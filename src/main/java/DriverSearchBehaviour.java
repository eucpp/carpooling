import jade.core.*;
import jade.domain.*;
import jade.core.behaviours.*;

import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;
import jade.domain.FIPAAgentManagement.DFAgentDescription;

import org.json.JSONObject;

import java.util.*;

public class DriverSearchBehaviour extends FSMBehaviour {

    public interface AcceptDecisionMaker {
        boolean accept(double payment);
    }

    public DriverSearchBehaviour(Agent agent, MapModel.Intention intention, AcceptDecisionMaker decisionMaker) {
        registerFirstState(new NegotiationBehavior(agent, decisionMaker, intention), "Negotiation");
        registerLastState(new DummyBehaviour(), "Dummy");

        registerTransition("Negotiation", "Dummy", ACLMessage.INFORM);
        registerTransition("Negotiation", "Dummy", ACLMessage.CANCEL);
        registerTransition("Negotiation", "Negotiation", ACLMessage.FAILURE, new String[]{"Negotiation"});
    }

    private static class DummyBehaviour extends OneShotBehaviour {
        @Override
        public void action() {}
    }

    private static class NegotiationBehavior extends ContractNetInitiator {

        private int result = -1;
        MapModel.Intention intention;
        AcceptDecisionMaker decisionMaker;

        public NegotiationBehavior(Agent agent, AcceptDecisionMaker decisionMaker, MapModel.Intention intention) {
            super(agent, createCFP(intention));
            this.intention = intention;
            this.decisionMaker = decisionMaker;
        }

        private static ACLMessage createCFP(MapModel.Intention intention)  {
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
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
        protected Vector<ACLMessage> prepareCfps(ACLMessage __) {
            System.out.printf(
                    "%s prepares cfps ...\n",
                    getAgent().getLocalName()
            );

            Vector<ACLMessage> cfps = new Vector<>();
            cfps.add(createCFP(intention));
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
                    ACLMessage cfp = createCFP(intention);
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
            Offer best = offers.get(0);

            for (int i = 1; i < offers.size(); ++i) {
                ACLMessage msg = offers.get(i).msg;
                ACLMessage rejectMsg = msg.createReply();
                rejectMsg.setPerformative(ACLMessage.REJECT_PROPOSAL);
                acceptances.add(rejectMsg);
            }

            ACLMessage chosen = best.msg;
            ACLMessage reply = chosen.createReply();

            if (decisionMaker.accept(best.payment)) {
                System.out.printf(
                        "%s have chosen proposal from %s\n",
                        getAgent().getLocalName(),
                        chosen.getSender().getLocalName()
                );

                reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
            } else {
                result = ACLMessage.CANCEL;
                reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
            }

            acceptances.add(reply);
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
                result = ACLMessage.INFORM;
            } catch (Exception e) {
                System.out.println("Error: " + e);
                System.exit(1);
            }
        }

        @Override
        protected void handleFailure(ACLMessage failure) {
            result = ACLMessage.FAILURE;
        }

        @Override
        public int onEnd() {
            return result;
        }
    }
}
