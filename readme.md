# Ouarzazate Agents Election Simulation by JADE and Gemini API

N JADE agents in 1 container: **PJM**, **PAD** (parties) and **Salma, Ayoub, ...** (N citizens).

Flow: parties generate a program via Gemini api -> send it to citizens -> citizens
debate with each party over N rounds (Gemini) -> citizens vote -> parties tally
and print the winner.

## Requirements
- JDK 8+
- JADE jar 
- `json-20240303.jar` (org.json) — for parsing Gemini's response

## Setup in Eclipse
1. Import/open the project.
2. Right-click project -> **Build Path -> Configure Build Path -> Libraries**
   -> Add External JARs -> add `jade.jar` and `json-20240303.jar` under
   **Classpath** (not Modulepath).
3. Right-click `Launcher.java` -> **Run As -> Run Configurations**
   -> **Environment** tab -> New:
   - `GEMINI_API_KEY` = your key
   - `GEMINI_MODEL` = `gemini-3.1-flash-lite` (optional, overrides the default)
4. Run -> Run.

## Where things are
- `agents/PartyAgent.java` — generates program, debates, tallies votes
- `agents/CitizenAgent.java` — receives programs, debates, votes
- `util/GeminiClient.java` — Gemini API call, model fallback, rate throttle
- `util/Config.java` — citizen/party names, persona , round count, language instruction
- `Launcher.java` — creates the container and the N agents
