import java.util.*;

import jade.domain.FIPANames;
import org.json.*;

import jade.core.*;
import jade.lang.acl.*;

import jade.proto.ContractNetResponder;


public class VehicleAgent extends Agent implements Vehicle {

    private final Passenger driver;
    private ArrayList<Passenger> passengers;
    private MapModel map;

    private static final int CAPACITY = 3;

    public VehicleAgent(Passenger driver, MapModel map) {
        this.driver = driver;
        this.passengers = new ArrayList<>();
        this.map = map;
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

        addBehaviour(new ResponderBehavior(this, map));
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

        public double getCost() {
            if (route.getLength() == Double.MAX_VALUE) {
                return 0;
            }
            return payment / route.getLength();
        }
    }

    private static class ResponderBehavior extends ContractNetResponder {
        private Plan currentPlan;
        private Plan newPlan;
        private Passenger.Intention driverIntention;
        private MapModel map;

        ResponderBehavior(VehicleAgent agent, MapModel map) {
            super(agent, createMessageTemplate(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET));

            driverIntention = agent.driver.getIntention();

            Set<Destination> destinations = new HashSet<>();
            destinations.add(new Destination(agent.getAID(), Destination.Tag.SOURCE, driverIntention.from));
            destinations.add(new Destination(agent.getAID(), Destination.Tag.SINK, driverIntention.to));

            MapModel.Route route = map.getRoute(driverIntention.from, driverIntention.to);

            this.currentPlan = new Plan(route, destinations, 0);
            this.newPlan = null;
            this.map = map;
        }

        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) {
            JSONObject content = new JSONObject(cfp.getContent());
            MapModel.Node from = MapModel.Node.getNodeByID(content.getInt("from"));
            MapModel.Node to = MapModel.Node.getNodeByID(content.getInt("to"));
            int pricePerKm = content.getInt("price");
            AID sender = cfp.getSender();

            System.out.printf(
                    "Vehicle %s receive cfp from %s: from=%d; to=%d; $/km=%f\n",
                    this.getAgent().getAID().toString(),
                    sender.toString(),
                    from.id, to.id, pricePerKm
            );

            HashSet<Destination> newDestinations = new HashSet<>(currentPlan.destinations);
            newDestinations.add(new Destination(sender, Destination.Tag.SOURCE, from));
            newDestinations.add(new Destination(sender, Destination.Tag.SINK, to));

            Map<AID, MapModel.Route> routes = new HashMap<>();
            for (Destination dst: newDestinations) {
                if (dst.tag == Destination.Tag.SOURCE) {
                    routes.put(dst.aid, map.emptyRoute());
                }
            }

            MapModel.Route vehicleRoute = computeRoute(
                    driverIntention,
                    (Set<Destination>) newDestinations.clone(),
                    routes
            );

            double passengerPayment = pricePerKm * routes.get(sender).getLength();
            double totalPayment = currentPlan.payment + passengerPayment;
            Plan newPlan = new Plan(vehicleRoute, newDestinations, totalPayment);

            System.out.printf(
                    "Vehicle %s builds a route: %s; payment=%f\n",
                    this.getAgent().getAID().toString(),
                    vehicleRoute.toString(),
                    totalPayment
            );

            System.out.printf(
                    "Vehicle %s - route for %s: %s; payment=%f\n",
                    this.getAgent().getAID().toString(),
                    sender.toString(),
                    vehicleRoute.toString(),
                    passengerPayment
            );

            if (newPlan.getCost() > currentPlan.getCost()) {
                System.out.println(String.format(
                        "Vehicle %s - proposes route for %s",
                        this.getAgent().getAID().toString(),
                        sender.toString()
                ));

                ACLMessage propose = new ACLMessage(ACLMessage.PROPOSE);
                propose.addReceiver(cfp.getSender());
                propose.setLanguage("json");
                propose.setContent(new JSONObject()
                        .put("payment", passengerPayment)
//                        .put("route", new JSONArray(routes.get(sender)))
                        .toString()
                );

                this.newPlan = newPlan;

                return propose;
            } else {
                System.out.println(String.format(
                        "Vehicle %s - refuses proposal from %s",
                        this.getAgent().getAID().toString(),
                        sender.toString()
                ));

                ACLMessage refuse = new ACLMessage(ACLMessage.REFUSE);
                refuse.addReceiver(cfp.getSender());
                refuse.setLanguage("json");
                return refuse;
            }
        }

        @Override protected ACLMessage handleAcceptProposal(
                ACLMessage cfp, ACLMessage propose, ACLMessage accept
        ) {
            System.out.println(String.format(
                    "Vehicle %s - passenger %s accepts proposal",
                    this.getAgent().getAID().toString(),
                    accept.getSender().toString()
            ));

            currentPlan = newPlan;
            newPlan = null;

            ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
            inform.addReceiver(accept.getSender());
            return inform;
        }

        private MapModel.Route computeRoute(
                Passenger.Intention driverIntention,
                Set<Destination> destinations,
                Map<AID, MapModel.Route> routes
        ) {
            Set<AID> onBoard = new HashSet<>();

            MapModel.Node curr = driverIntention.from;
            MapModel.Route vehicleRoute = map.emptyRoute();

            while (!destinations.isEmpty()) {
                MapModel.Route minRoute = map.INFINITE_ROUTE;
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
                    routes.put(nextDestination.aid, map.emptyRoute());
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
