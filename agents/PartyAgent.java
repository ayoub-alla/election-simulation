package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import util.Config;
import util.GeminiClient;

import java.util.HashMap;
import java.util.Map;

/** Party agent (PJM or PAD): builds a program, debates with citizens, counts the votes. */
public class PartyAgent extends Agent {

    private String partyName; // party name
    private String persona; // persona or vision of the party 
    
    // map -> key , value it use like var.put( key , val ) , var.get(key)
    private final Map<String, Integer> repliesSent = new HashMap<>(); // a count of replies that sent per party like <"pad" , 1 >
    private final Map<String, Integer> votes = new HashMap<>(); // votes count

    @Override
    protected void setup() {
    	
    	
        Object[] args = getArguments(); // args  name , persona/vision
        
        // defined name , or localname if not exist
        partyName = (args != null && args.length > 0) ? args[0].toString() : getLocalName();
        
        // person or : default
        persona = (args != null && args.length > 1) ? args[1].toString()  : "حزب وسط كيهتم بالتمنية و الاقتصاد الرقمي";
        
        //register in dfservice 
        register();
        
        

        // the start of the flow creating a program of the party
        addBehaviour(new GenerateProgram());
        
        // chat with citizens answer inqueiries
        addBehaviour(new DebateWithCitizens());
        
        // count votes democracy
        addBehaviour(new CountVotes());
        
    }

    private class GenerateProgram extends OneShotBehaviour {
        @Override
        public void action() {
        	
        	System.out.println("The party "+partyName+" is now generating the Party program \n" );
        	
        	String prompt = "إنت حزب سياسي سميتك " + partyName 
        			+ "، والتوجه/الطابع ديالك هو هذا: " + persona + 
        			" اقترح من 6 حتى لـ 8 ديال الأهداف/الوعود مختلفة بزاف على بعضياتها، اللي غادي تديرهم باش تنمي مدينة ورزازات في المغرب. جاوب غير بليستة مرقمة (رقم + جملة واحدة لكل هدف)، بلا مقدمة ولا خاتمة." 
        			+ Config.LANG_INSTRUCTION;
            
            String program;
            try {
                program = GeminiClient.ask(prompt);
            } catch (Exception e) {
            	program = "1. توسيع محطة الطاقة الشمسية\n2. ترميم المدينة القديمة\n3. شبكة ماء جديدة\n" + "4. مركز تدريب فالسياحة\n5. مركب رياضي للشباب\n6. إصلاح الطرقات";
                System.out.println("[" + partyName + "] Gemini call failed (" + e.getMessage() + "), using fallback program.");
            }
            
            System.out.println("[" + partyName + "] PROGRAM :\n" + program);
            
            
        	System.out.println(" "+partyName+" is now sending the program to all citizens \n" );
            
            // for each citizen the party send his program ! no df search here
            for (String citizen : Config.CITIZENS) {
            	
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID(citizen, AID.ISLOCALNAME));
                msg.setConversationId(Config.CID_PROGRAM);
                msg.setContent(partyName + " PROGRAM:\n" + program);
                send(msg);
                
            }
        }
    }

    /** Answers the citizens' questions, up to ROUNDS_PER_PARTY replies per citizen. */
    private class DebateWithCitizens extends CyclicBehaviour {
    	
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchConversationId(Config.CID_DISCUSS));
            ACLMessage msg = receive(mt);
            if (msg == null) { block(); return; }

            String citizen = msg.getSender().getLocalName();
            int sent = repliesSent.getOrDefault(citizen, 0);
            
            // If we already replied to this citizen, drop duplicate buffered messages
            if (sent >= Config.ROUNDS_PER_PARTY) {
                return;
            }

            repliesSent.put(citizen, sent + 1);

            String prompt = "أنت حزب " + partyName +
            		" (" + persona + ") ،  كاتدافع على البرنامج ديالك على ورزازات. واحد المواطن قال ليك: " 
            		+ msg.getContent() 
            		+ ". جاوب بجملة ولا جملتين فقط."
            		+ Config.LANG_INSTRUCTION;
            
            String reply;
            try {
                reply = GeminiClient.ask(prompt);
            } catch (Exception e) {
                reply = "شكرا ليك على هاد السؤال غادي نجاوبوك عليه الى صوتي علينا .";
            }
            
            System.out.println("[" + partyName + "] -> [" + citizen + "] : "+ reply);

            ACLMessage r = msg.createReply();
            r.setPerformative(ACLMessage.INFORM);
            r.setConversationId(Config.CID_DISCUSS);
            r.setContent(reply);
            send(r);
            
            // DRAIN BACKLOG: Clear out extra messages from this same citizen that arrived during the throttle wait time
            ACLMessage duplicate;
            while ((duplicate = receive(mt)) != null) {
                if (!duplicate.getSender().getLocalName().equals(citizen)) {
                    putBack(duplicate); // keep message if it's from another citizen
                    break;
                }
            }
        }
    }

    private class CountVotes extends CyclicBehaviour {
    	
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchConversationId(Config.CID_VOTE));
            ACLMessage msg = receive(mt);
            if (msg == null) { block(); return; }

            String choice = msg.getContent();
            votes.merge(choice, 1, Integer::sum);

            int total = votes.values().stream().mapToInt(Integer::intValue).sum();
            if (total == Config.CITIZENS.length) {
            	int pjm = votes.getOrDefault("PJM", 0);
                int pad = votes.getOrDefault("PAD", 0);
                int noVote = votes.getOrDefault("NO-VOTE", 0);
                
                String winner;
                if (pjm == pad) {
                    winner = "TIE";
                } else {
                    winner = (pjm > pad) ? "PJM" : "PAD";
                }

                System.out.println("PJM: " + pjm + " votes | PAD: " + pad + " votes | NO-VOTE: " + noVote + " votes | Winner: " + winner);
                System.out.println("Election Simulation Ended....\n");
            }
        }
    }

    private void register() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("party");
        sd.setName(partyName);
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) { }
        System.out.println(partyName + " terminating.");
    }
}