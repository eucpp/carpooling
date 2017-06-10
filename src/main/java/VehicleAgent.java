import java.util.*;

import org.json.*;

import jade.core.*;
import jade.lang.acl.*;

import jade.proto.ContractNetResponder;


public class VehicleAgent extends Agent implements Vehicle {

    private final Passenger driver;
    private ArrayList<Passenger> passengers;

    private static final int CAPACITY = 3;

    public VehicleAgent(Passenger driver) {
        this.driver = driver;
        this.passengers = new ArrayList<>();
    }

    @Override
    public Passenger getDriver() {
        return this.driver;
    }

    @Override
    public ArrayList<Passenger> getPassengers() {
        return this.passengers;
    }

    @Override
    public int getCapacity() {
        return CAPACITY;
    }

    @Override
    public String toString() {
        return "Driver" + driver.getID();
    }

    protected void setup() {
        System.out.println("Starting Vehicle Agent " + getLocalName());
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

    private static class Plan {
        private final MapModel.Route route;
        private final Set<Destination> destinations;
        private final double payment;

        public Plan(MapModel.Route route, Set<Destination> destinations, double payment) {
            this.route = route;
            this.destinations = destinations;
            this.payment = payment;
        }
    }

    private static class ResponderBehavior extends ContractNetResponder {
        private Plan currentPlan;
        private double currentCost;
        private Map<AID, MapModel.Route> passengerRoutes;
        private Passenger.Intention driverIntention;
        private MapModel map;

        ResponderBehavior(VehicleAgent agent) {
            super(agent, createMessageTemplate());
        }

        private static MessageTemplate createMessageTemplate() {
            return MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.CFP),
                    MessageTemplate.MatchLanguage("json")
            );
        }

        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) {
            JSONObject content = new JSONObject(cfp.getContent());
            MapModel.Node from = MapModel.Node.getNodeByID(content.getInt("from"));
            MapModel.Node to = MapModel.Node.getNodeByID(content.getInt("to"));
            int pricePerKm = content.getInt("price");

            HashSet<Destination> newDestinations = new HashSet<>(currentPlan.destinations);
            newDestinations.add(new Destination(cfp.getSender(), Destination.Tag.SOURCE, from));
            newDestinations.add(new Destination(cfp.getSender(), Destination.Tag.SINK, to));

            Map<AID, MapModel.Route> routes = new HashMap<>();
            for (AID aid: passengerRoutes.keySet()) {
                routes.put(aid, MapModel.Route.emptyRoute());
            }

            MapModel.Route vehicleRoute = computeRoute(
                    driverIntention,
                    (Set<Destination>) newDestinations.clone(),
                    routes
            );

            double payment = pricePerKm * vehicleRoute.getLength();
            double totalPayment = currentPlan.payment + payment;

            double newCost = totalPayment / vehicleRoute.getLength();
            if (newCost < currentCost) {
                currentCost = newCost;
                currentPlan = new Plan(vehicleRoute, newDestinations, totalPayment);
                passengerRoutes = routes;

                ACLMessage propose = new ACLMessage(ACLMessage.PROPOSE);
                propose.addReceiver(cfp.getSender());
                propose.setLanguage("json");
                propose.setContent(String.format("{ \"payment\": %f}", payment));

                return propose;
            } else {
                ACLMessage refuse = new ACLMessage(ACLMessage.REFUSE);
                refuse.addReceiver(cfp.getSender());
                refuse.setLanguage("json");
                return refuse;
            }
        }

        @Override protected ACLMessage handleAcceptProposal(
                ACLMessage cfp, ACLMessage propose, ACLMessage accept
        ) {

        }

        private MapModel.Route computeRoute(
                Passenger.Intention driverIntention,
                Set<Destination> destinations,
                Map<AID, MapModel.Route> routes
        ) {
            Set<AID> onBoard = new HashSet<>();

            MapModel.Node curr = driverIntention.from;
            MapModel.Route vehicleRoute = MapModel.Route.emptyRoute();

            while (!destinations.isEmpty()) {
                MapModel.Route minRoute = MapModel.Route.INFINITE_ROUTE;
                Destination nextDestination = null;

                for (Destination destination : destinations) {
                    if ((destination.tag == Destination.Tag.SOURCE && onBoard.size() < CAPACITY) ||
                            (destination.tag == Destination.Tag.SINK && onBoard.contains(destination.aid))) {
                        MapModel.Route route = map.getRoute(curr, destination.node);
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
                    routes.put(nextDestination.aid, MapModel.Route.emptyRoute());
                    onBoard.add(nextDestination.aid);
                } else if (nextDestination.tag == Destination.Tag.SINK) {
                    onBoard.remove(nextDestination.aid);
                }

                curr = nextDestination.node;
            }

            return vehicleRoute;
        }
    }
}
