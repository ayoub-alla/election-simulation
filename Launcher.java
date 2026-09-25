import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.AgentContainer;

/** Starts one JADE container with the 2 party agents and the N citizen agents. */

public class Launcher {

    public static void main(String[] args) throws Exception {
    	
    	
    	
    	
    	// create 1 container (mainContaine)
        Runtime runtime = Runtime.instance();
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.GUI, "false"); 
        AgentContainer mainContainer = runtime.createMainContainer(profile);
        //mainContainer.start();
        
        

        //  Party agents each one with personllised vision or persona  -> mainContainer 
        // container.createNewAgent( name , agent class (where setup action ..) , Args )
        
        AgentController pjm = mainContainer.createNewAgent(
        		
        		"PJM",  // name
        		"agents.PartyAgent", // class
                new Object[]{"PJM", "حزب محافظ، كيهتم بالسياحة التقليدية والدين والتراث والبيئة."} // args
        		
        		);
        
        AgentController pad = mainContainer.createNewAgent(
        		
        		"PAD",
        		"agents.PartyAgent",
                new Object[]{"PAD", "حزب الشباب، كايحتم بالتكنولوجيا، الشركات الناشئة والخدمة ديال الشباب."}
        		
        		);
        
        // start party agents
        pjm.start();
        pad.start();

        
        
        
        // create an agent for each citizen -> mainContainer
       // container.createNewAgent( name , agent class (where setup action ..) , Args )

        
        for (int i = 0; i < util.Config.CITIZENS.length; i++) {
        	
            AgentController citizen = mainContainer.createNewAgent(
            		
                    util.Config.CITIZENS[i], // name
                    "agents.CitizenAgent", // class
                    new Object[]{util.Config.citizenPersonas[i]} // args persona
                    
                    );
            
            // start citizen agent
            citizen.start();
        }
    }
    
}
