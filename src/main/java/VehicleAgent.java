import java.util.*;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.json.*;

import jade.core.*;
import jade.lang.acl.*;

import jade.domain.FIPANames;
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

    private static class ResponderBehavior extends ContractNetResponder {

        private MapModel.Route currentRoute;
        private MapModel map;

        ResponderBehavior(VehicleAgent agent) {
            super(agent, createMessageTemplate());
        }

//        private static class

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

        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) {
            JSONObject content = new JSONObject(cfp.getContent());
            MapModel.Node from = MapModel.Node.getNodeByID(content.getInt("from"));
            MapModel.Node to = MapModel.Node.getNodeByID(content.getInt("to"));


        }

        private MapModel.Route computeRoute(
                Passenger.Intention driverIntention,
                Set<Destination> destinations,
                Map<AID, MapModel.Route> routes) {
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
                        if (route.getCost() < minRoute.getCost()) {
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

        private static MessageTemplate createMessageTemplate() {
            return MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.CFP),
                MessageTemplate.MatchLanguage("json")
            );
        }


    }
}
