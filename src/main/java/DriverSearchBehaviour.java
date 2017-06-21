import jade.core.*;
import jade.domain.*;
import jade.core.behaviours.*;

import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetInitiator;
import jade.domain.FIPAAgentManagement.DFAgentDescription;

import org.json.JSONObject;

import java.util.*;

public class DriverSearchBehaviour extends ContractNetInitiator {

    private final Driver driver;
    private final MapModel.Intention intention;
    private final WaitConfirmBehaviour waitConfirmBehaviour;

    public DriverSearchBehaviour(DriverAgent agent, MapModel.Intention intention) {
        super(agent, createCFP(intention));
        this.driver = agent;
        this.intention = intention;
        this.waitConfirmBehaviour = new WaitConfirmBehaviour();

        registerHandleInform(waitConfirmBehaviour);
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

    private static class Offer {
        public final ACLMessage msg;
        public final double payment;

        Offer(ACLMessage msg, double payment) {
            this.msg = msg;
            this.payment = payment;
        }
    }

    @Override
    protected Vector<ACLMessage> prepareCfps(ACLMessage __) {
        System.out.printf(
                "%s - prepares cfps ...\n",
                getAgent().getLocalName()
        );

        Vector<ACLMessage> cfps = new Vector<>();
        cfps.add(createCFP(intention));
        int receiversCnt = 0;
        try {
            DFAgentDescription template = new DFAgentDescription();
            template.addServices(CarpoolAgent.DRIVER_SERVICE);
            DFAgentDescription[] descriptions = DFService.search(getAgent(), template);
            for (DFAgentDescription description: descriptions) {
                if (description.getName().equals(getAgent().getAID())
                        || driver.inBlackList(description.getName())) {
                    continue;
                }

                System.out.printf(
                        "%s - sends cfp to %s\n",
                        getAgent().getLocalName(),
                        description.getName().getLocalName()
                );

                cfps.get(0).addReceiver(description.getName());
                receiversCnt++;
            }
        } catch (Exception e) {
            System.out.println("Error: " + e);
            System.exit(1);
        }
        if (receiversCnt == 0) {
            driver.acceptCurrentRoute();
        }
        // We want to receive a reply in 10 secs
        cfps.get(0).setReplyByDate(new Date(System.currentTimeMillis() + 10 * 1000));
        return cfps;
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
                offers.add(new Offer(rsp, payment));

                System.out.printf(
                        "%s - receives proposal from %s with payment=%f\n",
                        getAgent().getLocalName(),
                        sender.getLocalName(),
                        payment
                );
            }
        }

        if (offers.isEmpty()) {
            driver.incAttemptCount();
            return;
        }

        offers.sort((Offer a, Offer b) -> Double.compare(a.payment, b.payment));
        Offer best = offers.get(0);

        for (int i = 1; i < offers.size(); ++i) {
            ACLMessage msg = offers.get(i).msg;
            ACLMessage rejectMsg = msg.createReply();
            rejectMsg.setPerformative(ACLMessage.REJECT_PROPOSAL);
            acceptances.add(rejectMsg);

//            if (offers.get(i).payment > driver.getInitialRouteCost()) {
//                driver.addToBlackList(msg.getSender());
//            }
        }

        ACLMessage chosen = best.msg;
        ACLMessage reply = chosen.createReply();

        if (accept(best.payment)) {
            System.out.printf(
                    "%s - have chosen proposal from %s\n",
                    getAgent().getLocalName(),
                    chosen.getSender().getLocalName()
            );

            reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
        } else {
            reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
        }

        acceptances.add(reply);
    }

    private boolean accept(double payment) {
        if (driver.getCurrentIncome() > -payment) {
            driver.acceptCurrentRoute();
            return false;
        } else if (!driver.hasCurrentRouteChanged()) {
            return true;
        }
        driver.rememberCurrentRoute();
        return false;
    }

    @Override
    protected void handleFailure(ACLMessage failure) {
        System.out.printf(
                "%s - receives failure message from %s\n",
                getAgent().getLocalName(),
                failure.getSender().getLocalName()
        );

        reset();
    }

    @Override
    public void reset() {
        super.reset();
        waitConfirmBehaviour.reset();
    }

    private class WaitConfirmBehaviour extends OneShotBehaviour {

        @Override
        public void action() {
            ACLMessage inform = (ACLMessage) getDataStore().get(DriverSearchBehaviour.this.REPLY_KEY);

            System.out.printf(
                    "%s - receives inform message from %s\n",
                    getAgent().getLocalName(),
                    inform.getSender().getLocalName()
            );

            MessageTemplate tpl = MessageTemplate.and(
                MessageTemplate.or(
                        MessageTemplate.MatchPerformative(ACLMessage.CONFIRM),
                        MessageTemplate.MatchPerformative(ACLMessage.DISCONFIRM)
                ),
                MessageTemplate.MatchSender(inform.getSender())
            );

            ACLMessage msg = getAgent().blockingReceive(tpl);
            if (msg.getPerformative() == ACLMessage.CONFIRM) {
                System.out.printf("%s - receives confirm from %s\n",
                        getAgent().getLocalName(), msg.getSender().getLocalName());
                handleConfirm();
            } else if (msg.getPerformative() == ACLMessage.DISCONFIRM) {
                System.out.printf("%s - receives disconfirm from %s\n",
                        getAgent().getLocalName(), msg.getSender().getLocalName());
                handleDisconfirm();
            }
        }

        private void handleConfirm() {
            DriverSearchBehaviour.this.driver.quitDriving();
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

        private void handleDisconfirm() {
            DriverSearchBehaviour.this.reset();
        }
    }
}
