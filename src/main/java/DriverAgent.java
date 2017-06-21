import java.util.*;
import java.util.stream.Collectors;

import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.WrapperBehaviour;
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

    private static class PassengerPlan {
        public final AID aid;
        public final MapModel.Node from;
        public final MapModel.Node to;
        public final double payment;

        public PassengerPlan(AID aid, MapModel.Node from, MapModel.Node to, double payment) {
            this.aid = aid;
            this.from = from;
            this.to = to;
            this.payment = payment;
        }

        public JSONObject toJSON() {
            return new JSONObject()
                    .put("name", aid.getLocalName())
                    .put("from", from.id)
                    .put("to", to.id)
                    .put("payment", payment);
        }
    }

    private static class Plan {
        private final MapModel.Route route;
        private final Set<Destination> destinations;
        private final Map<AID, Double> payments;
        private final double totalPayment;

        public Plan(
                MapModel.Route route,
                Set<Destination> destinations,
                Map<AID, Double> payments,
                double payment
        ) {
            this.route = route;
            this.destinations = destinations;
            this.payments = payments;
            this.totalPayment = payment;
        }

        public double getIncome() {
            return totalPayment - route.getCost();
        }

        public Set<AID> getPassengers() {
            return destinations.stream()
                    .filter(dst -> dst.tag == Destination.Tag.SOURCE)
                    .map(dst -> dst.aid)
                    .collect(Collectors.toSet());
        }

        public List<PassengerPlan> getPassengerPlans() {
            ArrayList<PassengerPlan> plans = new ArrayList<>();
            for (AID aid: getPassengers()) {
                MapModel.Node from = destinations.stream()
                        .filter(dst -> dst.aid == aid && dst.tag == Destination.Tag.SOURCE)
                        .map(dst -> dst.node)
                        .collect(Collectors.toList())
                        .get(0);
                MapModel.Node to = destinations.stream()
                        .filter(dst -> dst.aid == aid && dst.tag == Destination.Tag.SINK)
                        .map(dst -> dst.node)
                        .collect(Collectors.toList())
                        .get(0);
                double payment = payments.get(aid);
                plans.add(new PassengerPlan(aid, from, to, payment));
            }
            return plans;
        }
    }

    private static final double DRIVER_PREMIUM = 0;

    private final int id;
    private MapModel map;
    private Plan newPlan;
    private Plan prevPlan;
    private Plan currPlan;
    private MapModel.Intention intention;
    private NegotiationBehavior negotiationBehavior;
    private CheckProfitBehaviour checkBehaviour;
    private MapModel.Route initialRoute;
    private Set<AID> blackList;
    private boolean checkRunning;
    private int attemptCnt;
    private boolean isDone;

    private static final int CAPACITY = 3;
    private static final int MAX_ATTEMPT_COUNT = 3;
    private static final long CHECK_PROFIT_PERIOD_MS = 3 * 1000;

    private static int next_id = 1;

    public DriverAgent(MapModel.Intention intention, MapModel map) {
        Set<Destination> destinations = new HashSet<>();
        Map<AID, Double> payments = new HashMap<>();
        this.initialRoute = map.getRoute(intention.from, intention.to);
        this.id = next_id++;
        this.intention = intention;
        this.map = map;
        this.newPlan = null;
        this.prevPlan = null;
        this.currPlan = new Plan(initialRoute, destinations, payments, 0);
        this.negotiationBehavior = new NegotiationBehavior(this);
        this.checkBehaviour = new CheckProfitBehaviour();
        this.blackList = new HashSet<>();
        this.checkRunning = false;
        this.attemptCnt = 0;
        this.isDone = false;

        System.out.printf(
                "%s - initial route: %s; cost = %f\n",
                toString(),
                initialRoute.toString(),
                initialRoute.getCost()
        );
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
        addBehaviour(negotiationBehavior);
        addBehaviour(checkBehaviour);
    }

    @Override
    public double getInitialRouteCost() {
        return initialRoute.getCost();
    }

    @Override
    public double getCurrentIncome() {
        return currPlan.getIncome();
    }

    @Override
    public void incAttemptCount() {
        ++attemptCnt;
    }

    @Override
    public boolean inBlackList(AID aid) {
        return blackList.contains(aid);
    }

    @Override
    public void acceptCurrentRoute() {
        System.out.printf(
                "%s - accepts plan and sends confirm notifications to waiting passengers\n",
                getLocalName()
        );

        deregister();
        isDone = true;
        removeBehaviour(checkBehaviour);

        Set<AID> passengers = currPlan.getPassengers();

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
            List<JSONObject> passengerPlans = currPlan.getPassengerPlans().stream()
                    .map(PassengerPlan::toJSON)
                    .collect(Collectors.toList());
            notification.setContent(new JSONObject()
                    .put("sender-type", "driver")
                    .put("cost", currPlan.route.getCost())
                    .put("income", currPlan.getIncome())
                    .put("route", route)
                    .put("passengers", passengerPlans)
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

    @Override
    public void quitDriving() {
        System.out.printf("%s - quits driving and becomes a passenger\n", getLocalName());

        deregister();
        isDone = true;
        removeBehaviour(checkBehaviour);

        for (AID aid: currPlan.getPassengers()) {
            ACLMessage disconfirm = new ACLMessage(ACLMessage.DISCONFIRM);
            disconfirm.addReceiver(aid);
            send(disconfirm);
        }
    }

    @Override
    public void rememberCurrentRoute() {
        prevPlan = currPlan;
    }

    @Override
    public boolean hasCurrentRouteChanged() {
        return prevPlan == currPlan;
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

    private class CheckProfitBehaviour extends TickerBehaviour {

        CheckProfitBehaviour() {
            super(DriverAgent.this, CHECK_PROFIT_PERIOD_MS + new Random().nextInt(10 * 1000));
        }

        @Override
        protected void onTick() {
            if (attemptCnt == MAX_ATTEMPT_COUNT) {
                acceptCurrentRoute();
                return;
            }
            if (checkRunning) {
                return;
            }
            checkRunning = true;

            addBehaviour(new WrapperBehaviour(new DriverSearchBehaviour(DriverAgent.this, intention)) {
                @Override
                public int onEnd() {
                    checkRunning = false;
                    return getWrappedBehaviour().onEnd();
                }
            });
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

            if (agent.isDone) {
                System.out.printf(
                        "%s - receive cfp from %s but it is no longer provides service\n",
                        getAgent().getLocalName(),
                        sender.getLocalName()
                );

                ACLMessage refuse = cfp.createReply();
                refuse.setPerformative(ACLMessage.REFUSE);
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
//            double passengerPayment = Math.max(
//                    Math.abs(agent.currPlan.route.getCost() - vehicleRoute.getCost()) + DRIVER_PREMIUM,
//                    passengerRoute.getCost()
//            );
            double passengerPayment =
                    Math.abs(agent.currPlan.route.getCost() - vehicleRoute.getCost()) + DRIVER_PREMIUM;
            Map<AID, Double> newPayments = new HashMap<>(agent.currPlan.payments);
            newPayments.put(sender, passengerPayment);
            double totalPayment = agent.currPlan.totalPayment + passengerPayment;

            Plan newPlan = new Plan(vehicleRoute, newDestinations, newPayments, totalPayment);

            System.out.printf(
                    "%s - builds a route for %s\n" +
                    "    full route: %s\n" +
                    "    passenger route: %s\n" +
                    "    driver income = %f\n" +
                    "    passenger payment = %f\n",
                    getAgent().getLocalName(),
                    sender.getLocalName(),
                    vehicleRoute.toString(),
                    passengerRoute.toString(),
                    newPlan.getIncome(), passengerPayment
            );

            if (newPlan.getIncome() >= agent.currPlan.getIncome()) {
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

                agent.blackList.add(cfp.getSender());

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
            DriverAgent agent = (DriverAgent) getAgent();

            if (agent.isDone) {
                System.out.printf(
                        "%s - receives accept from %s and sends failure\n",
                        getAgent().getLocalName(),
                        accept.getSender().getLocalName()
                );

                ACLMessage failure = accept.createReply();
                failure.setPerformative(ACLMessage.FAILURE);
                return failure;
            }

            System.out.printf(
                    "%s - receives accept from %s and sends inform\n",
                    getAgent().getLocalName(),
                    accept.getSender().getLocalName()
            );

            agent.currPlan = agent.newPlan;
            agent.newPlan = null;

            ACLMessage inform = accept.createReply();
            inform.setPerformative(ACLMessage.INFORM);
            return inform;
        }

        @Override
        protected void handleRejectProposal(
                ACLMessage cfp, ACLMessage propose, ACLMessage reject
        ) {
            System.out.printf(
                    "%s - receives reject from %s\n",
                    getAgent().getLocalName(),
                    reject.getSender().getLocalName()
            );

            DriverAgent agent = (DriverAgent) getAgent();
            agent.newPlan = null;
            agent.blackList.remove(reject.getSender());
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
