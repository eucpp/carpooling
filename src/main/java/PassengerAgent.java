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
    private final MapModel.Intention intention;

    private static int next_id = 0;

    public PassengerAgent(MapModel.Intention intention) {
        this.id = next_id++;
        this.intention = intention;

        System.out.printf("%s intention: from %s to %s\n",
                toString(), intention.from.toString(), intention.to.toString());
    }

    @Override
    public int getID() {
        return this.id;
    }

    @Override
    public MapModel.Intention getIntention() {
        return this.intention;
    }

    @Override
    public String toString() {
        return "Passenger" + Integer.toString(id);
    }

    @Override
    protected void setup() {
        System.out.println("Starting Agent " + getLocalName());

        addBehaviour(new NegotiationBehavior(this));
    }

    private static class NegotiationBehavior extends ContractNetInitiator {

        public NegotiationBehavior(PassengerAgent passenger) {
            super(passenger, createCFP(passenger));
        }

        private static ACLMessage createCFP(PassengerAgent sender)  {
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            MapModel.Intention intention = sender.getIntention();
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
            try {
                DFAgentDescription template = new DFAgentDescription();
                template.addServices(CarpoolAgent.DRIVER_SERVICE);
                DFAgentDescription[] descriptions = DFService.search(getAgent(), template);
                for (DFAgentDescription description: descriptions) {
                    cfps.get(0).addReceiver(description.getName());
                }
            } catch (Exception e) {
                System.out.println("Error: " + e);
                System.exit(1);
            }
            // We want to receive a reply in 10 secs
            cfps.get(0).setReplyByDate(new Date(System.currentTimeMillis() + 10 * 1000));
            return cfps;
        }

        private static class Offer {
            public final ACLMessage msg;
            public final double payment;

            Offer(ACLMessage msg, double payment) {
                this.msg = msg;
                this.payment = payment;
            }
        }

        @Override
        protected void handleAllResponses(Vector responses, Vector acceptances) {
            ArrayList<Offer> offers = new ArrayList<>();
            ArrayList<AID> busy = new ArrayList<>();
            for (Object obj : responses) {
                ACLMessage rsp = (ACLMessage) obj;

                if (rsp.getPerformative() == ACLMessage.PROPOSE) {
                    JSONObject content = new JSONObject(rsp.getContent());
                    AID sender = rsp.getSender();
                    double payment = content.getDouble("payment");
                    offers.add(new Offer(rsp, payment));

                    System.out.printf(
                            "%s receives proposal from %s with payment=%f\n",
                            getAgent().getLocalName(),
                            sender.getLocalName(),
                            payment
                    );
                } else if (rsp.getPerformative() == ACLMessage.REFUSE) {
                    JSONObject content = new JSONObject(rsp.getContent());
                    if (content.getString("reason").equals("busy")) {
                        busy.add(rsp.getSender());
                    }
                }
            }
            if (offers.isEmpty()) {
                if (!busy.isEmpty()) {
                    ACLMessage cfp = createCFP((PassengerAgent) getAgent());
                    for (AID receiver: busy) {
                        cfp.addReceiver(receiver);
                    }
                    Vector vec = new Vector();
                    vec.add(cfp);
                    newIteration(vec);
                    return;
                }
            }

            offers.sort((Offer a, Offer b) -> Double.compare(a.payment, b.payment));

            ACLMessage chosen = offers.get(0).msg;

            System.out.printf(
                    "%s have chosen proposal from %s\n",
                    getAgent().getLocalName(),
                    chosen.getSender().getLocalName()
            );

            ACLMessage acceptMsg = chosen.createReply();
            acceptMsg.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
            acceptances.add(acceptMsg);

            for (int i = 1; i < offers.size(); ++i) {
                ACLMessage msg = offers.get(i).msg;
                ACLMessage rejectMsg = msg.createReply();
                rejectMsg.setPerformative(ACLMessage.REJECT_PROPOSAL);
                acceptances.add(rejectMsg);
            }
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            try {
                ACLMessage notification = new ACLMessage(ACLMessage.INFORM);
                notification.setContent(new JSONObject()
                        .put("sender-type", "passenger")
                        .toString()
                );

                DFAgentDescription template = new DFAgentDescription();
                template.addServices(CarpoolAgent.LOGGING_SERVICE);
                DFAgentDescription[] descriptions = DFService.search(getAgent(), template);
                for (DFAgentDescription description: descriptions) {
                    notification.addReceiver(description.getName());
                }

                getAgent().send(notification);
            } catch (Exception e) {
                System.out.println("Error: " + e);
                System.exit(1);
            }
        }
    }
}
