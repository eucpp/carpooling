import java.util.*;
import java.util.ArrayList;

import jade.core.*;
import jade.domain.*;
import jade.domain.FIPAAgentManagement.*;

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

    @Override
    protected void setup() {
        try {
            System.out.println("Starting Passenger Agent " + getLocalName());

            ArrayList<AID> vehicles = new ArrayList<>();
            DFAgentDescription template = new DFAgentDescription();
            template.addServices(CarpoolAgent.PASSENGER_SERVICE);
            for (DFAgentDescription description: DFService.search(this, template)) {
                vehicles.add(description.getName());
            }
            addBehaviour(new InitatorBehavior(this, vehicles));
        } catch (Exception e) {
            System.out.println("Error: " + e);
            System.exit(1); // ???
        }
    }

    private static class InitatorBehavior extends ContractNetInitiator {

        ArrayList<AID> vehicles;

        InitatorBehavior(PassengerAgent agent, ArrayList<AID> vehicles) {
            super(agent, createCFP(agent));
            this.vehicles = vehicles;
        }

        private static ACLMessage createCFP(PassengerAgent sender)  {
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            Passenger.Intention intention = sender.getIntention();
            cfp.setLanguage("json");
            cfp.setContent(new JSONObject()
                .put("from", intention.from)
                .put("to", intention.to)
                .toString()
            );
            return cfp;
        }

        @Override
        protected Vector<ACLMessage> prepareCfps(ACLMessage cfp) {
            Vector<ACLMessage> cfps = new Vector<>();
            cfps.add((ACLMessage) cfp.clone());
            for (AID vehicle: vehicles) {
                cfps.get(0).addReceiver(vehicle);
            }
            return cfps;
        }

        private static class Offer {
            public final AID aid;
            public final double payment;

            Offer(AID aid, double payment) {
                this.aid = aid;
                this.payment = payment;
            }
        }

        @Override
        protected void handleAllResponses(Vector responses, Vector acceptances) {
            ArrayList<Offer> offers = new ArrayList<>();
            for (Object obj : responses) {
                ACLMessage rsp = (ACLMessage) obj;

                System.out.printf(
                        "Passenger %s receives proposal: %s\n",
                        this.getAgent().getAID().toString(),
                        rsp
                );

                if (rsp.getPerformative() == ACLMessage.PROPOSE) {
                    JSONObject content = new JSONObject(rsp.getContent());
                    Offer offer = new Offer(rsp.getSender(), content.getDouble("payment"));
                    offers.add(offer);
                }
            }
            if (offers.isEmpty()) {
                return;
            }
            offers.sort((Offer a, Offer b) -> Double.compare(a.payment, b.payment));

            System.out.printf(
                    "Passenger %s have chosen proposal from vehicle %s\n",
                    this.getAgent().getAID().toString(),
                    offers.get(0).aid.toString()
            );

            ACLMessage acceptMsg = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
            acceptMsg.addReceiver(offers.get(0).aid);
            acceptances.add(acceptMsg);

            for (int i = 1; i < offers.size(); ++i) {
                ACLMessage rejectMsg = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                acceptMsg.addReceiver(offers.get(i).aid);
                acceptances.add(rejectMsg);
            }
        }

        @Override
        protected void handleInform(ACLMessage inform) {

        }
    }
}
