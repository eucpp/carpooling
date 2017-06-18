import java.io.SequenceInputStream;
import java.util.*;

import jade.core.behaviours.SequentialBehaviour;
import jade.domain.FIPANames;
import jade.proto.ProposeResponder;
import org.json.*;

import jade.core.*;
import jade.lang.acl.*;

import jade.proto.ContractNetResponder;


public class VehicleAgent extends Agent implements Vehicle {

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

        public double getCost() {
            if (route.getLength() == Double.MAX_VALUE) {
                return 0;
            }
            return payment / route.getLength();
        }
    }

    private final Passenger driver;
    private MapModel map;
    private Plan currentPlan;
    private Plan newPlan;
    boolean inNegotiation;

    private static final int CAPACITY = 3;

    public VehicleAgent(Passenger driver, MapModel map) {
        Set<Destination> destinations = new HashSet<>();
        MapModel.Route route = map.getRoute(driver.getIntention().from, driver.getIntention().to);
        this.currentPlan = new Plan(route, destinations, 0);
        this.newPlan = null;
        this.driver = driver;
        this.map = map;
        this.inNegotiation = false;

        System.out.printf(
                "Vehicle %s initial route: %s\n",
                getLocalName(),
                route.toString()
        );
    }

    @Override
    public Passenger getDriver() {
        return this.driver;
    }

    @Override
    public int getCapacity() {
        return CAPACITY;
    }

    @Override
    public String toString() {
        return "Vehicle" + driver.getID();
    }

    protected void setup() {
        System.out.printf(
                "Starting Vehicle Agent %s\n",
                getLocalName()
        );

        addBehaviour(new NegotiationBehavior(this));
    }

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

    private static class NegotiationBehavior extends ContractNetResponder {

        NegotiationBehavior(VehicleAgent agent) {
            super(agent, createMessageTemplate(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET));
        }

        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) {
            AID sender = cfp.getSender();
            VehicleAgent agent = (VehicleAgent) getAgent();

            if (agent.newPlan != null) {
                System.out.printf(
                        "Vehicle %s receive cfp from %s but it is busy now\n",
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
                    "Vehicle %s receive cfp from %s: from=%d; to=%d\n",
                    getAgent().getLocalName(),
                    sender.getLocalName(),
                    from.id, to.id
            );

            HashSet<Destination> newDestinations = new HashSet<>(agent.currentPlan.destinations);
            newDestinations.add(new Destination(sender, Destination.Tag.SOURCE, from));
            newDestinations.add(new Destination(sender, Destination.Tag.SINK, to));

            Map<AID, MapModel.Route> routes = new HashMap<>();
            for (Destination dst: newDestinations) {
                if (dst.tag == Destination.Tag.SOURCE) {
                    routes.put(dst.aid, agent.map.initRoute(dst.node));
                }
            }

            MapModel.Route vehicleRoute = computeRoute(
                    agent.driver.getIntention(),
                    (Set<Destination>) newDestinations.clone(),
                    routes
            );

            MapModel.Route passengerRoute = routes.get(sender);
            double passengerPayment = Math.max(
                    Math.abs(agent.currentPlan.route.getCost() - vehicleRoute.getCost()),
                    passengerRoute.getCost()
            );
            double totalPayment = agent.currentPlan.payment + passengerPayment;
            Plan newPlan = new Plan(vehicleRoute, newDestinations, totalPayment);

            System.out.printf(
                    "Vehicle %s builds a route: %s; income=%f;\n",
                    getAgent().getLocalName(),
                    vehicleRoute.toString(),
                    newPlan.getIncome()
            );

            System.out.printf(
                    "Vehicle %s - route for %s: %s; payment=%f\n",
                    getAgent().getLocalName(),
                    sender.getLocalName(),
                    passengerRoute.toString(),
                    passengerPayment
            );

            if (newPlan.getIncome() >= agent.currentPlan.getIncome()) {
                System.out.println(String.format(
                        "Vehicle %s - proposes route for %s",
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
                        "Vehicle %s - refuses proposal from %s",
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
            System.out.println(String.format(
                    "Vehicle %s - passenger %s accepts proposal",
                    getAgent().getLocalName(),
                    accept.getSender().getLocalName()
            ));

            VehicleAgent agent = (VehicleAgent) getAgent();
            agent.currentPlan = agent.newPlan;
            agent.newPlan = null;

            ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
            inform.addReceiver(accept.getSender());
            return inform;
        }

        @Override
        protected void handleRejectProposal(
                ACLMessage cfp, ACLMessage propose, ACLMessage accept
        ) {
            System.out.println(String.format(
                    "Vehicle %s - passenger %s rejects proposal",
                    getAgent().getLocalName(),
                    accept.getSender().getLocalName()
            ));

            VehicleAgent agent = (VehicleAgent) getAgent();
            agent.newPlan = null;
        }

        private MapModel.Route computeRoute(
                Passenger.Intention driverIntention,
                Set<Destination> destinations,
                Map<AID, MapModel.Route> routes
        ) {
            Set<AID> onBoard = new HashSet<>();

            VehicleAgent agent = (VehicleAgent) getAgent();
            MapModel.Node curr = driverIntention.from;
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

            MapModel.Route lastRoute = agent.map.getRoute(curr, driverIntention.to);
            for (AID aid : onBoard) {
                routes.get(aid).join(lastRoute);
            }
            vehicleRoute.join(lastRoute);

            return vehicleRoute;
        }
    }
}
