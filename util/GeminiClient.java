package util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Tiny wrapper around the Gemini generateContent REST endpoint. */
public class GeminiClient {

    // Tried in order. If a model is overloaded (503), we fall back to the next one.
    // First entry can be overridden with GEMINI_MODEL if you want to force one.
    private static final String[] MODELS = buildModelList();

    private static String[] buildModelList() {
        String forced = System.getenv("GEMINI_MODEL");
        String[] fallbacks = {
                "gemini-3.1-flash-lite", // best RPM/RPD on free tier, but sometimes 503 when overloaded
                "gemini-3.5-flash-lite", // close performance, separate quota/capacity
                "gemini-2.5-flash-lite"  // older but usually available
        };
        if (forced != null && !forced.isBlank()) {
            // put the forced model first, keep the others as fallback
            String[] withForced = new String[fallbacks.length + 1];
            withForced[0] = forced;
            System.arraycopy(fallbacks, 0, withForced, 1, fallbacks.length);
            return withForced;
        }
        return fallbacks;
    }

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    // Global throttle shared by ALL agents/threads, so RPM is respected across the whole app.
    private static final Object LOCK = new Object();
    private static long lastCallMillis = 0;
    // interval to not exceed RPM (60000 / your RPM limit)
    private static final long MIN_INTERVAL_MS = 15000;

    private static void throttle() throws InterruptedException {
        synchronized (LOCK) {
            long wait = lastCallMillis + MIN_INTERVAL_MS - System.currentTimeMillis();
            if (wait > 0) Thread.sleep(wait);
            lastCallMillis = System.currentTimeMillis();
        }
    }

    public static String ask(String prompt) throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY env var not set");
        }

        Exception lastError = null;
        for (String model : MODELS) {
            throttle(); // shared clock respected before every attempt, whichever model it is
            try {
                return callModel(model, apiKey, prompt);
            } catch (OverloadedException e) {
                lastError = e;
                // loop continues to the next model in MODELS
            }
        }
        throw new RuntimeException("All Gemini models unavailable (last error: " + lastError + ")");
    }

    private static String callModel(String model, String apiKey, String prompt) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        JSONObject part = new JSONObject().put("text", prompt);
        JSONObject content = new JSONObject().put("parts", new JSONArray().put(part));
        JSONObject body = new JSONObject().put("contents", new JSONArray().put(content));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 503) {
            throw new OverloadedException(model + " returned 503");
        }
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Gemini error " + resp.statusCode() + ": " + resp.body());
        }
        JSONObject json = new JSONObject(resp.body());
        return json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim();
    }

    /** Internal marker so ask() knows a 503 means "try the next model" and not a hard failure. */
    private static class OverloadedException extends Exception {
        OverloadedException(String msg) { super(msg); }
    }
}
