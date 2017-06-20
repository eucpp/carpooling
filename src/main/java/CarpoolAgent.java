import java.util.*;
import java.util.stream.Collectors;

import jade.core.*;
import jade.domain.*;
import jade.wrapper.*;
import jade.lang.acl.*;
import jade.core.behaviours.*;
import jade.domain.FIPAAgentManagement.*;

import org.json.JSONArray;
import org.json.JSONObject;

public class CarpoolAgent extends Agent {

    public static String LOGGING_SERVICE_NAME = "LoggingService";
    public static String PASSENGER_SERVICE_NAME = "PassengerService";
    public static String DRIVER_SERVICE_NAME = "DriverService";

    public static ServiceDescription LOGGING_SERVICE;
    public static ServiceDescription PASSENGER_SERVICE;
    public static ServiceDescription DRIVER_SERVICE;

    static {
        LOGGING_SERVICE = new ServiceDescription();
        LOGGING_SERVICE.setName(LOGGING_SERVICE_NAME);
        LOGGING_SERVICE.setType("carpooling-services");

        PASSENGER_SERVICE = new ServiceDescription();
        PASSENGER_SERVICE.setName(PASSENGER_SERVICE_NAME);
        PASSENGER_SERVICE.setType("carpooling-services");

        DRIVER_SERVICE = new ServiceDescription();
        DRIVER_SERVICE.setName(DRIVER_SERVICE_NAME);
        DRIVER_SERVICE.setType("carpooling-services");
    }

    private MapModel map;
    private CarpoolView view;

    private ArrayList<PassengerAgent> passengers;
    private ArrayList<DriverAgent> drivers;
    private Set<AID> agents;

    protected void setup() {
        try {
            map = MapModel.generate(20);

            passengers = generatePassengers(8, map);
            drivers = generateVehicles(4, map);

            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            dfd.addServices(LOGGING_SERVICE);
            DFService.register(this, dfd);

            ContainerController cc = getContainerController();
            for (DriverAgent driver : drivers) {
                cc.acceptNewAgent(driver.toString(), driver).start();
            }

            for (PassengerAgent passenger : passengers) {
                cc.acceptNewAgent(passenger.toString(), passenger).start();
            }

            agents = new HashSet<>();
            agents.addAll(passengers.stream().map(Agent::getAID).collect(Collectors.toSet()));
            agents.addAll(drivers.stream().map(Agent::getAID).collect(Collectors.toSet()));

//            view = new CarpoolView(map);
//            view.drawPassengers(passengers);

            addBehaviour(new Behaviour() {
                private Map<AID, JSONObject> ready = new HashMap<>();

                @Override
                public void action() {
                    if (allReady()) {
                        return;
                    }

                    ACLMessage msg = getAgent().blockingReceive(
                            MessageTemplate.MatchPerformative(ACLMessage.INFORM)
                    );
                    AID sender = msg.getSender();
                    JSONObject content = new JSONObject(msg.getContent());

                    assert !ready.containsKey(sender);

                    System.out.printf("Carpool - receive inform from %s\n", sender.getLocalName());

                    ready.put(sender, content);
                }

                @Override
                public boolean done() {
                    boolean isDone = allReady();
                    if (isDone) {
                        printStats();
                    }
                    return isDone;
                }

                private boolean allReady() {
                    return ready.keySet().containsAll(agents);
                }

                private void printStats() {
                    for (Map.Entry<AID, JSONObject> entry: ready.entrySet()) {
                        AID driver = entry.getKey();
                        JSONObject content = entry.getValue();
                        String senderType = content.getString("sender-type");

                        if (senderType.equals("driver")) {
                            String route = getRoute(content.getJSONArray("route"));
                            String passengers = getPassengers(content.getJSONArray("passengers"));

                            System.out.printf(
                                    "%s ready to drive!\nroute: %s\n passengers:\n%s",
                                    driver.getLocalName(), route, passengers
                            );
                        } else if (senderType.equals("passenger")) {
                            System.out.printf(
                                    "%s ready to go!\npayment: ?\n",
                                    driver.getLocalName()
                            );
                        }
                    }
                }

                private String getRoute(JSONArray array) {
                    ArrayList<MapModel.Node> nodes = new ArrayList<>();
                    for (int i = 0; i < array.length(); ++i) {
                        nodes.add(MapModel.Node.getNodeByID(array.getInt(i)));
                    }
                    return map.new Route(nodes).toString();
                }

                private String getPassengers(JSONArray array) {
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < array.length(); ++i) {
                        JSONObject obj = array.getJSONObject(i);
                        builder.append(String.format(
                            "%s: %d ---> %d\n",
                            obj.getString("name"),
                            obj.getInt("from"), obj.getInt("to")
                        ));
                    }
                    return builder.toString();
                }
            });
        } catch (Exception e) {
            System.out.println("Error: " + e);
            e.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static ArrayList<PassengerAgent> generatePassengers(int n, MapModel map) {
        ArrayList<PassengerAgent> passengers = new ArrayList<>(n);
        ArrayList<MapModel.Node> nodes = new ArrayList<>(map.getGraph().vertexSet());
        Random rnd = new Random();
        for (int i = 0; i < n; ++i) {
            MapModel.Node from = nodes.get(rnd.nextInt(nodes.size()));
            MapModel.Node to = nodes.get(rnd.nextInt(nodes.size()));
            while (to == from) {
                to = nodes.get(rnd.nextInt(nodes.size()));
            }
            PassengerAgent passenger = new PassengerAgent(new MapModel.Intention(from, to));
            passengers.add(passenger);
        }
        return passengers;
    }

    private static ArrayList<DriverAgent> generateVehicles(int n, MapModel map) {
        ArrayList<DriverAgent> vehicles = new ArrayList<>(n);
        ArrayList<MapModel.Node> nodes = new ArrayList<>(map.getGraph().vertexSet());
        Random rnd = new Random();
        for (int i = 0; i < n; ++i) {
            MapModel.Node from = nodes.get(rnd.nextInt(nodes.size()));
            MapModel.Node to = nodes.get(rnd.nextInt(nodes.size()));
            while (to == from) {
                to = nodes.get(rnd.nextInt(nodes.size()));
            }
            DriverAgent vehicle = new DriverAgent(new MapModel.Intention(from, to), map);
            vehicles.add(vehicle);
        }
        return vehicles;
    }
}