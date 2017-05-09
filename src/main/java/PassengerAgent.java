import jade.core.*;
import java.util.*;
import java.util.ArrayList;

import jade.util.leap.*;
import org.json.*;

import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;

public class PassengerAgent extends Agent implements Passenger {

    private final int id;
    private final Passenger.Intention intention;
    private Vehicle vehicle;

    private static int next_id = 0;

    public PassengerAgent(Passenger.Intention intention) {
        this.id = next_id++;
        this.intention = intention;

        System.out.printf("Creating new passenger with id=%d that want to move from %s to %s\n",
                this.id, intention.from.toString(), intention.to.toString());
    }

    @Override
    public int getID() {
        return this.id;
    }

    @Override
    public Passenger.Intention getIntention() {
        return this.intention;
    }

    @Override
    public Vehicle getVehicle() {
        return this.vehicle;
    }

    @Override
    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    @Override
    public String toString() {
        return "Passenger" + Integer.toString(id);
    }

    protected void setup() {
        System.out.println("Starting Passenger Agent " + getLocalName());
    }

    private static class InitatorBehavior extends ContractNetInitiator {

        private final int pid;

        InitatorBehavior(PassengerAgent agent, ArrayList<AID> vehicles) {
            super(agent, createCFP(agent, vehicles));
            pid = agent.getID();
        }

        private static ACLMessage createCFP(PassengerAgent sender, ArrayList<AID> recievers)  {
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            for (AID reciever : recievers) {
                cfp.addReceiver(reciever);
            }

            Passenger.Intention intention = sender.getIntention();

            cfp.setLanguage("json");
            String content = "{\"pid\": %d, \"from\": %d, \"to\": %d}";
            cfp.setContent(String.format(content, sender.getID(), intention.from, intention.to));

            return cfp;
        }

        private static class Offer {
            public final AID aid;
            public final double cost;

            Offer(AID aid, double cost) {
                this.aid = aid;
                this.cost = cost;
            }
        }

        @Override
        protected void handleAllResponses(Vector responses, Vector acceptances) {
            ArrayList<Offer> offers = new ArrayList<>();
            for (Object obj : responses) {
                ACLMessage rsp = (ACLMessage) obj;

                System.out.printf("Passenger%d receives message: %s\n", pid, rsp);

                if (rsp.getPerformative() == ACLMessage.PROPOSE) {
                    JSONObject content = new JSONObject(rsp.getContent());
                    Offer offer = new Offer(rsp.getSender(), content.getDouble("cost"));
                    offers.add(offer);
                }
            }
            if (offers.isEmpty()) {
                return;
            }
            offers.sort((Offer a, Offer b) -> Double.compare(a.cost, b.cost));

            ACLMessage acceptMsg = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
            acceptMsg.addReceiver(offers.get(0).aid);
            acceptances.add(acceptMsg);

            for (int i = 1; i < offers.size(); ++i) {
                ACLMessage rejectMsg = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                acceptMsg.addReceiver(offers.get(i).aid);
                acceptances.add(rejectMsg);
            }
        }
    }
}
