import java.util.*;
import java.util.stream.Collectors;

import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPANames;
import org.json.*;

import jade.core.*;
import jade.lang.acl.*;

import jade.proto.ContractNetResponder;

public class DriverAgent extends Agent implements Driver {

    private static class Destination {
        public enum Tag {
            SOURCE, SINK
        }

        public final AID aid;
        public final Tag tag;
        public final MapModel.Node node;

        public Destination(AID aid, Tag tag, MapModel.Node node) {
            this.aid = aid;
            this.tag = tag;
            this.node = node;
        }
    }

    private static class Plan {
        private final MapModel.Route route;
        private final Set<Destination> destinations;
        private final double payment;

        public Plan(MapModel.Route route, Set<Destination> destinations, double payment) {
            this.route = route;
            this.destinations = destinations;
            this.payment = payment;
        }

        public double getIncome() {
            return payment - route.getCost();
        }

        public Set<AID> getPassengers() {
            return destinations.stream()
                    .filter(dst -> dst.tag == Destination.Tag.SOURCE)
                    .map(dst -> dst.aid)
                    .collect(Collectors.toSet());
        }
    }

    private static final double DRIVER_PREMIUM = 1;

    private final int id;
    private MapModel map;
    private Plan newPlan;
    private Plan prevPlan;
    private Plan currPlan;
    private MapModel.Intention intention;
    private CheckProfitBehaviour checkBehaviour;

    private static final int CAPACITY = 3;
    private static final long CHECK_PROFIT_PERIOD_MS = 5 * 1000;

    private static int next_id = 0;

    public DriverAgent(MapModel.Intention intention, MapModel map) {
        Set<Destination> destinations = new HashSet<>();
        MapModel.Route route = map.getRoute(intention.from, intention.to);
        this.id = next_id++;
        this.intention = intention;
        this.map = map;
        this.newPlan = null;
        this.prevPlan = null;
        this.currPlan = new Plan(route, destinations, 0);
        this.checkBehaviour = new CheckProfitBehaviour();

        System.out.printf("%s - initial route: %s\n", toString(), route.toString());
    }

    @Override
    public String toString() {
        return "Driver" + Integer.toString(id);
    }

    protected void setup() {
        System.out.printf(
                "Starting Agent %s\n",
                getLocalName()
        );

        register();
        addBehaviour(new NegotiationBehavior(this));
        addBehaviour(checkBehaviour);
    }

    private void register() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            dfd.addServices(CarpoolAgent.DRIVER_SERVICE);
            DFService.register(this, dfd);
        } catch (Exception e) {
            System.out.println("Error: " + e);
            e.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private void deregister() {
        try {
            DFService.deregister(this);
        } catch (Exception e) {
            System.out.println("Error: " + e);
            e.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private void acceptPlan() {
        System.out.printf(
                "%s - accepts plan and sends confirm notifications to waiting passengers\n",
                getLocalName()
        );

        deregister();
        removeBehaviour(checkBehaviour);

        Set<AID> passengers = currPlan.getPassengers();
        Set<String> passengerNames = passengers.stream().map(AID::getLocalName).collect(Collectors.toSet());

        for (AID aid: passengers) {
            ACLMessage confirm = new ACLMessage(ACLMessage.CONFIRM);
            confirm.addReceiver(aid);
            send(confirm);
        }

        try {
            ACLMessage notification = new ACLMessage(ACLMessage.INFORM);
            List<String> route = currPlan.route.getNodes().stream()
                    .map(MapModel.Node::toString)
                    .collect(Collectors.toList());
            notification.setContent(new JSONObject()
                    .put("sender-type", "driver")
                    .put("income", currPlan.getIncome())
                    .put("route", route)
                    .put("passengers", passengerNames)
                    .toString()
            );

            DFAgentDescription template = new DFAgentDescription();
            template.addServices(CarpoolAgent.LOGGING_SERVICE);
            DFAgentDescription[] descriptions = DFService.search(this, template);
            for (DFAgentDescription description: descriptions) {
                notification.addReceiver(description.getName());
            }

            send(notification);
        } catch (Exception e) {
            System.out.println("Error: " + e);
            System.exit(1);
        }
    }

    private void quitDriving() {
        System.out.printf("%s - quits driving and becomes a passenger", getLocalName());

        removeBehaviour(checkBehaviour);

        for (AID aid: currPlan.getPassengers()) {
            ACLMessage disconfirm = new ACLMessage(ACLMessage.DISCONFIRM);
            disconfirm.addReceiver(aid);
            send(disconfirm);
        }
    }

    private class CheckProfitBehaviour extends TickerBehaviour {

        private boolean running = false;

        CheckProfitBehaviour() {
            super(DriverAgent.this, CHECK_PROFIT_PERIOD_MS + new Random().nextInt(1000));
        }

        @Override
        protected void onTick() {
            if (running) {
                return;
            }
            running = true;

            addBehaviour(new DriverSearchBehaviour(getAgent(), intention,
                    new DriverSearchBehaviour.AcceptDecisionMaker() {
                        @Override
                        public boolean accept(double payment) {
                            if (currPlan.getIncome() > -payment) {
                                acceptPlan();
                                return false;
                            } else if (currPlan == prevPlan) {
                                return true;
                            }
                            prevPlan = currPlan;
                            return false;
                        }

                        @Override
                        public void noProposals() {
                            acceptPlan();
                        }

                        @Override
                        public void onConfirm() {
                            quitDriving();
                        }

                        @Override
                        public void onEnd() {
                            running = false;
                        }
                    })
            );
        }
    }

    private static class NegotiationBehavior extends ContractNetResponder {

        NegotiationBehavior(DriverAgent agent) {
            super(agent, createMessageTemplate(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET));
        }

        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) {
            AID sender = cfp.getSender();
            DriverAgent agent = (DriverAgent) getAgent();

            if (agent.newPlan != null) {
                System.out.printf(
                        "%s - receive cfp from %s but it is busy now\n",
                        getAgent().getLocalName(),
                        sender.getLocalName()
                );

                ACLMessage refuse = cfp.createReply();
                refuse.setPerformative(ACLMessage.REFUSE);
                refuse.setContent(new JSONObject()
                        .put("reason", "busy")
                        .toString()
                );
                return refuse;
            }

            JSONObject content = new JSONObject(cfp.getContent());
            MapModel.Node from = MapModel.Node.getNodeByID(content.getInt("from"));
            MapModel.Node to = MapModel.Node.getNodeByID(content.getInt("to"));

            System.out.printf(
                    "%s - receive cfp from %s: from=%d; to=%d\n",
                    getAgent().getLocalName(),
                    sender.getLocalName(),
                    from.id, to.id
            );

            HashSet<Destination> newDestinations = new HashSet<>(agent.currPlan.destinations);
            newDestinations.add(new Destination(sender, Destination.Tag.SOURCE, from));
            newDestinations.add(new Destination(sender, Destination.Tag.SINK, to));

            Map<AID, MapModel.Route> routes = new HashMap<>();
            for (Destination dst: newDestinations) {
                if (dst.tag == Destination.Tag.SOURCE) {
                    routes.put(dst.aid, agent.map.initRoute(dst.node));
                }
            }

            MapModel.Route vehicleRoute = computeRoute(
                    (Set<Destination>) newDestinations.clone(),
                    routes
            );

            MapModel.Route passengerRoute = routes.get(sender);
            double passengerPayment = Math.max(
                    Math.abs(agent.currPlan.route.getCost() - vehicleRoute.getCost()) + DRIVER_PREMIUM,
                    passengerRoute.getCost()
            );
            double totalPayment = agent.currPlan.payment + passengerPayment;
            Plan newPlan = new Plan(vehicleRoute, newDestinations, totalPayment);

            System.out.printf(
                    "%s builds a route: %s; income=%f;\n",
                    getAgent().getLocalName(),
                    vehicleRoute.toString(),
                    newPlan.getIncome()
            );

            System.out.printf(
                    "%s - route for %s: %s; payment=%f\n",
                    getAgent().getLocalName(),
                    sender.getLocalName(),
                    passengerRoute.toString(),
                    passengerPayment
            );

            if (newPlan.getIncome() > agent.currPlan.getIncome()) {
                System.out.println(String.format(
                        "%s - proposes route for %s",
                        getAgent().getLocalName(),
                        sender.getLocalName()
                ));

                ACLMessage propose = cfp.createReply();
                propose.setPerformative(ACLMessage.PROPOSE);
                propose.setContent(new JSONObject()
                        .put("payment", passengerPayment)
                        .toString()
                );
                agent.newPlan = newPlan;
                return propose;
            } else {
                System.out.println(String.format(
                        "%s - refuses proposal from %s",
                        getAgent().getLocalName(),
                        sender.getLocalName()
                ));

                ACLMessage refuse = cfp.createReply();
                refuse.setPerformative(ACLMessage.REFUSE);
                refuse.setContent(new JSONObject()
                        .put("reason", "unprofitable")
                        .toString()
                );
                return refuse;
            }
        }

        @Override
        protected ACLMessage handleAcceptProposal(
                ACLMessage cfp, ACLMessage propose, ACLMessage accept
        ) {
            System.out.printf(
                    "%s receives accept from %s\n",
                    getAgent().getLocalName(),
                    accept.getSender().getLocalName()
            );

            DriverAgent agent = (DriverAgent) getAgent();
            agent.currPlan = agent.newPlan;
            agent.newPlan = null;

            ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
            inform.addReceiver(accept.getSender());
            return inform;
        }

        @Override
        protected void handleRejectProposal(
                ACLMessage cfp, ACLMessage propose, ACLMessage accept
        ) {
            System.out.printf(
                    "%s receives reject from %s\n",
                    getAgent().getLocalName(),
                    accept.getSender().getLocalName()
            );

            DriverAgent agent = (DriverAgent) getAgent();
            agent.newPlan = null;
        }

        private MapModel.Route computeRoute(
                Set<Destination> destinations,
                Map<AID, MapModel.Route> routes
        ) {
            Set<AID> onBoard = new HashSet<>();

            DriverAgent agent = (DriverAgent) getAgent();
            MapModel.Node curr = agent.intention.from;
            MapModel.Route vehicleRoute = agent.map.initRoute(curr);

            while (!destinations.isEmpty()) {
                MapModel.Route minRoute = agent.map.INFINITE_ROUTE;
                Destination nextDestination = null;

                for (Destination destination : destinations) {
                    if ((destination.tag == Destination.Tag.SOURCE && onBoard.size() < CAPACITY) ||
                            (destination.tag == Destination.Tag.SINK && onBoard.contains(destination.aid))) {
                        MapModel.Route route = agent.map.getRoute(curr, destination.node);
                        if (route.getLength() < minRoute.getLength()) {
                            minRoute = route;
                            nextDestination = destination;
                        }
                    }
                }

                destinations.remove(nextDestination);

                for (AID aid : onBoard) {
                    routes.get(aid).join(minRoute);
                }
                vehicleRoute.join(minRoute);

                if (nextDestination.tag == Destination.Tag.SOURCE) {
                    routes.put(nextDestination.aid, agent.map.initRoute(nextDestination.node));
                    onBoard.add(nextDestination.aid);
                } else if (nextDestination.tag == Destination.Tag.SINK) {
                    onBoard.remove(nextDestination.aid);
                }

                curr = nextDestination.node;
            }

            MapModel.Route lastRoute = agent.map.getRoute(curr, agent.intention.to);
            for (AID aid : onBoard) {
                routes.get(aid).join(lastRoute);
            }
            vehicleRoute.join(lastRoute);

            return vehicleRoute;
        }
    }
}
