import java.util.*;
import java.util.ArrayList;

import jade.core.*;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.*;
import jade.domain.FIPAAgentManagement.*;

import jade.proto.ProposeInitiator;
import org.json.*;

import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;

public class PassengerAgent extends Agent implements Passenger {

    private final int id;
    private final Passenger.Intention intention;
    private Vehicle vehicle;
    private Set<AID> providers;

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
        System.out.println("Starting Passenger Agent " + getLocalName());

        SequentialBehaviour behaviour = new SequentialBehaviour(this);
        behaviour.addSubBehaviour(new NegotiationInitatorBehavior(this));
        behaviour.addSubBehaviour(new NegotiationBehavior(this));
        addBehaviour(behaviour);
    }

    private static class NegotiationInitatorBehavior extends ProposeInitiator {

        private PassengerAgent passenger;

        public NegotiationInitatorBehavior(PassengerAgent passenger) {
            super(passenger, createCFP(passenger));
            this.passenger = passenger;
        }

        private static ACLMessage createCFP(PassengerAgent sender)  {
            ACLMessage cfp = new ACLMessage(ACLMessage.PROPOSE);
            cfp.setProtocol(FIPANames.InteractionProtocol.FIPA_PROPOSE);
            cfp.setLanguage("json");
            return cfp;
        }

        @Override
        protected Vector<ACLMessage> prepareInitiations(ACLMessage propose) {
            System.out.printf(
                    "%s initiates negotiations ...\n",
                    getAgent().getLocalName()
            );

            try {
                passenger.providers = new HashSet<>();
                DFAgentDescription template = new DFAgentDescription();
                template.addServices(CarpoolAgent.VEHICLE_SERVICE);
                DFAgentDescription[] descriptions = DFService.search(getAgent(), template);
                for (DFAgentDescription description: descriptions) {
                    passenger.providers.add(description.getName());
                }
            } catch (Exception e) {
                System.out.println("Error: " + e);
                System.exit(1);
            }

            Vector<ACLMessage> cfps = new Vector<>();
            cfps.add((ACLMessage) propose.clone());
            for (AID vehicle: passenger.providers) {
                cfps.get(0).addReceiver(vehicle);
            }
            return cfps;
        }

        @Override
        protected void handleAllResponses(Vector responses) {
            for (Object obj : responses) {
                ACLMessage rsp = (ACLMessage) obj;
                AID sender = rsp.getSender();
                if (rsp.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                    System.out.printf(
                            "Passenger %s receives acceptance of negotiation from %s\n",
                            getAgent().getLocalName(),
                            sender.getLocalName()
                    );
                } else {
                    passenger.providers.remove(sender);
                }
            }
        }
    }

    private static class NegotiationBehavior extends ContractNetInitiator {

        PassengerAgent passenger;

        public NegotiationBehavior(PassengerAgent passenger) {
            super(passenger, createCFP(passenger));
            this.passenger = passenger;
        }

        private static ACLMessage createCFP(PassengerAgent sender)  {
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            Passenger.Intention intention = sender.getIntention();
            cfp.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
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
            System.out.printf(
                    "%s prepares cfps ...\n",
                    getAgent().getLocalName()
            );

            Vector<ACLMessage> cfps = new Vector<>();
            cfps.add((ACLMessage) cfp.clone());
            for (AID vehicle: passenger.providers) {
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

                if (rsp.getPerformative() == ACLMessage.PROPOSE) {
                    JSONObject content = new JSONObject(rsp.getContent());
                    AID sender = rsp.getSender();
                    double payment = content.getDouble("payment");
                    offers.add(new Offer(sender, payment));

                    System.out.printf(
                            "Passenger %s receives proposal from %s with payment=%f\n",
                            getAgent().getLocalName(),
                            sender.getLocalName(),
                            payment
                    );
                }
            }
            if (offers.isEmpty()) {
                return;
            }
            offers.sort((Offer a, Offer b) -> Double.compare(a.payment, b.payment));

            System.out.printf(
                    "Passenger %s have chosen proposal from vehicle %s\n",
                    getAgent().getLocalName(),
                    offers.get(0).aid.getLocalName()
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
