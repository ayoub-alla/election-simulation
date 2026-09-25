package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import util.Config;
import util.GeminiClient;

import java.util.HashMap;
import java.util.Map;

/** Citizen agent: receives both programs, exchanges N defined 1 OR 2 .. messages with each party, then votes. */

public class CitizenAgent extends Agent {

    private String name; // name defined in Config
    private String persona; //  the "personality" of this citizen
    
    // map -> key , value it use like var.put( key , val ) , var.get(key)
    private final Map<String, String> programs = new HashMap<>();           // used to store Parties programes like <"pad" , "1- ..."> pjm : " 1 -... "
    private final Map<String, StringBuilder> discussion = new HashMap<>(); // stores the chat between citizen and party
    private final Map<String, Integer> sent = new HashMap<>();           // a count of sent messages
    private final Map<String, Integer> received = new HashMap<>();       // a count for received messages ex <"pad" , 1>
    
    // to trach vote status at end
    private boolean voted = false;
    

    // setup function of the agent
    
    @Override
    protected void setup() {
    	
    	// container.creatNewAgent( name , class , args )
    	
        name = getLocalName(); // name => citizen name
        Object[] args = getArguments(); // args  => we sent only persona
        
        // extract persona from args  , if not exist : default
        persona = (args != null && args.length > 0) ? args[0].toString() : "مواطن عادي مقتانع بالاقتصاد و التمنية .";
        
        
        // main action function
        //  
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
            	
            	// recies messages from parties
                ACLMessage msg = receive();
                
                if (msg == null) { block(); return; }
                
                String cid = msg.getConversationId();
                

                if (Config.CID_PROGRAM.equals(cid)) {
                	// if conversation id is Program from parties it handled by handleProgram function 
                    handleProgram(msg);
                    
                } else if (Config.CID_DISCUSS.equals(cid) && msg.getPerformative() == ACLMessage.INFORM) {
                	// performative a type of the message ACLMessage.INFORM, ACLMessage.REQUEST , ACLMessage.CFP
                	// if conversation id is the chat it handled by handleProgram function 
                    handleReply(msg);
                }
                
            }
        });
        
        
    }

    
    // function to see the party program 
    private void handleProgram(ACLMessage msg) {
    	
        String party = msg.getSender().getLocalName();
        
        programs.put(party, msg.getContent());
        
        // untill we recive all the programs from all parties then we start asking each party askQuestion()
        if (programs.size() == Config.PARTIES.length) {
            for (String party2 : Config.PARTIES) askQuestion(party2);
        }
        
    }

   
    private void askQuestion(String party) {

        int s = sent.getOrDefault(party, 0);

        if (s >= Config.ROUNDS_PER_PARTY) return;

        StringBuilder history = discussion.getOrDefault(party, new StringBuilder());
        String historyPart = history.length() == 0
                ? ""
                : "\nهذا هو الحوار اللي دار حتى لتوما بيناتكم:\n" + history
                        + "صيفط رد  جديد ماشي نفس القديم.\n";

        String prompt = "إنت مواطن سميتك "
                 + name +
                 " ف ورزازات، والشخصية ديالك: "
                 + persona +
                 " هذا هو البرنامج ديال الحزب "
                 + party + ":\n"
                 + programs.get(party)
                 + historyPart
                 + "\n قدم رد  واحد قصير  على هذا البرنامج (جملة واحدة). يمكن سؤال نقدي او يمكن تعليق او يمكن رأي اي شيئ عل حسب شخصيتك"
                 + Config.LANG_INSTRUCTION;

        String question;

        try {
            question = GeminiClient.ask(prompt);
        } catch (Exception e) {
            System.out.println("[" + name + "] Gemini call failed (" + e.getMessage() + "), using fallback question.");
            question = "اشنو هي مزانية المشروع ديالكم ؟";
        }

        System.out.println("[" + name + "] -> [" + party + "]  : "+ question);

        discussion.computeIfAbsent(party, k -> new StringBuilder()).append("سؤال ديالي : ").append(question).append("\n");

        ACLMessage m = new ACLMessage(ACLMessage.REQUEST);
        m.addReceiver(new AID(party, AID.ISLOCALNAME));
        m.setConversationId(Config.CID_DISCUSS);
        m.setContent(question);
        send(m);

        sent.put(party, s + 1);
    }

    
    // handle replies
    private void handleReply(ACLMessage msg) {
    	
        String party = msg.getSender().getLocalName();
        received.merge(party, 1, Integer::sum);
        
        //System.out.println("[" + party + "] -> [" + name + "]: " + msg.getContent());
        
        discussion.computeIfAbsent(party, k -> new StringBuilder())
                .append(party).append(" jawb: ").append(msg.getContent()).append("\n");

        askQuestion(party); // ask again if under the round limit

        boolean doneWithPjm = received.getOrDefault("PJM", 0) >= Config.ROUNDS_PER_PARTY;
        boolean doneWithPad = received.getOrDefault("PAD", 0) >= Config.ROUNDS_PER_PARTY;
        
        // votre if the citizen reach max msg to each party defined in config 
        if (doneWithPjm && doneWithPad && !voted) {
            voted = true;
            vote();
        }
    }

    
    // voting function
    private void vote() {
    	
    	String prompt = "إنت مواطن سميتك " + name 
    			+ " ف ورزازات، الشخصية ديالك: " + persona 
    			+ " هادو هما البرامج وهدا هو الحوار اللي دار بيناتكم:\n" + "== PJM ==\nالبرنامج: " + programs.get("PJM") 
    			+ "\nالحوار:\n" + discussion.getOrDefault("PJM", new StringBuilder()) 
    			+ "\n== PAD ==\nالبرنامج: " + programs.get("PAD") 
    			+ "\nالحوار:\n" + discussion.getOrDefault("PAD", new StringBuilder()) 
    			+ "\nبناءً على كلشي اللي لفوق، لمن غادي تصوت او عدم  تصويت ؟ جاوب بكلمة واحدة فقط: PJM أو PAD او NO-VOTE";
        
        String answer;
        
        try {
            answer = GeminiClient.ask(prompt);
        } catch (Exception e) {
            System.out.println("[" + name + "] Gemini call failed (" + e.getMessage() + "), fallback vote = NO-VOTE.");
            answer = "NO-VOTE";
        }
        
        String upper = answer.toUpperCase().trim();
        String choice;
        
        if (upper.contains("NO-VOTE") || upper.contains("NONE") || upper.contains("NEITHER") || upper.contains("BLANC")) {
            choice = "NO-VOTE";
        } else if (upper.contains("PAD")) {
            choice = "PAD";
        } else if (upper.contains("PJM")) {
            choice = "PJM";
        } else {
            choice = "NO-VOTE"; // Fallback if Gemini gives an unparseable response
        }
        
        System.out.println("[" + name + "] VOTES FOR : " + choice);

        ACLMessage v = new ACLMessage(ACLMessage.INFORM);
        v.setConversationId(Config.CID_VOTE);
        v.setContent(choice);
        for (String party : Config.PARTIES) v.addReceiver(new AID(party, AID.ISLOCALNAME));
        send(v);
    }

    @Override
    protected void takeDown() {
        System.out.println(name + " terminating.");
    }
}