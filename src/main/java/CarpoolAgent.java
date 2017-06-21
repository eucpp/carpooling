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
    public static String DRIVER_SERVICE_NAME = "DriverService";

    public static ServiceDescription LOGGING_SERVICE;
    public static ServiceDescription DRIVER_SERVICE;

    static {
        LOGGING_SERVICE = new ServiceDescription();
        LOGGING_SERVICE.setName(LOGGING_SERVICE_NAME);
        LOGGING_SERVICE.setType("carpooling-services");

        DRIVER_SERVICE = new ServiceDescription();
        DRIVER_SERVICE.setName(DRIVER_SERVICE_NAME);
        DRIVER_SERVICE.setType("carpooling-services");
    }

    private MapModel map;
    private Set<AID> agents;
    private double initialCost;

    protected void setup() {
        try {
            map = MapModel.generate(100);
            map.exportToDot();

            ArrayList<DriverAgent> drivers = generateDrivers(30, map);

            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            dfd.addServices(LOGGING_SERVICE);
            DFService.register(this, dfd);

            initialCost = 0;
            ContainerController cc = getContainerController();
            for (DriverAgent driver : drivers) {
                cc.acceptNewAgent(driver.toString(), driver).start();
                initialCost += driver.getInitialRouteCost();
            }

            agents = new HashSet<>();
            agents.addAll(drivers.stream().map(Agent::getAID).collect(Collectors.toSet()));

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
                    double totalCost = 0;
                    for (Map.Entry<AID, JSONObject> entry: ready.entrySet()) {
                        AID driver = entry.getKey();
                        JSONObject content = entry.getValue();
                        String senderType = content.getString("sender-type");

                        if (senderType.equals("driver")) {
                            String route = getRoute(content.getJSONArray("route"));
                            String passengers = getPassengers(content.getJSONArray("passengers"));
                            double routeCost = content.getDouble("cost");
                            double income = content.getDouble("income");
                            totalCost += routeCost;

                            System.out.printf(
                                    "------------------------------\n" +
                                    "%s ready to drive!\n" +
                                    "    route: %s\n" +
                                    "    route cost: %f\n" +
                                    "    income: %f\n" +
                                    "    passengers: \n%s\n",
                                    driver.getLocalName(), route, routeCost, income, passengers
                            );
                        }
                    }

                    System.out.printf("------------------------------\n");
                    System.out.printf("Baseline route cost: %f\n", initialCost);
                    System.out.printf("Resulting route cost: %f\n", totalCost);
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
                                "        %s - route: %d ---> %d; payment: %f\n",
                                obj.getString("name"),
                                obj.getInt("from"), obj.getInt("to"),
                                obj.getDouble("payment")
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

    private static ArrayList<DriverAgent> generateDrivers(int n, MapModel map) {
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