import java.util.*;

import jade.core.*;
import jade.wrapper.*;

import jade.domain.*;
import jade.domain.FIPAAgentManagement.*;

public class CarpoolAgent extends Agent {

    public static String PASSENGER_SERVICE_NAME = "PassengerService";
    public static String VEHICLE_SERVICE_NAME = "VehicleService";

    public static ServiceDescription PASSENGER_SERVICE;
    public static ServiceDescription VEHICLE_SERVICE;

    static {
        PASSENGER_SERVICE = new ServiceDescription();
        PASSENGER_SERVICE.setName(PASSENGER_SERVICE_NAME);
        PASSENGER_SERVICE.setType("carpooling-service");

        VEHICLE_SERVICE = new ServiceDescription();
        VEHICLE_SERVICE.setName(VEHICLE_SERVICE_NAME);
        VEHICLE_SERVICE.setType("carpooling-service");
    }

    private MapModel map;
    private CarpoolView view;

    private ArrayList<PassengerAgent> passengers;
    private ArrayList<DriverAgent> vehicles;

    protected void setup() {
        try {
            map = MapModel.generate(20);

            passengers = generatePassengers(8, map);
            vehicles = generateVehicles(4, map);

            for (DriverAgent vehicle : vehicles) {
                registerVehicle(getContainerController(), vehicle);
            }

            for (PassengerAgent passenger : passengers) {
                registerPassenger(getContainerController(), passenger);
            }

            view = new CarpoolView(map);
            view.drawPassengers(passengers);
        } catch (Exception e) {
            System.out.println("Error: " + e);
            e.printStackTrace(System.out);
            System.exit(1); // ???
        }
    }

    private static void registerPassenger(ContainerController container, PassengerAgent agent)
            throws StaleProxyException, FIPAException {
        AgentController ac = container.acceptNewAgent(agent.toString(), agent);
        ac.start();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(agent.getAID());
        dfd.addServices(PASSENGER_SERVICE);

        DFService.register(agent, dfd);
    }

    private static void registerVehicle(ContainerController container, DriverAgent agent)
            throws StaleProxyException, FIPAException {
        AgentController ac = container.acceptNewAgent(agent.toString(), agent);
        ac.start();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(agent.getAID());
        dfd.addServices(VEHICLE_SERVICE);
        DFService.register(agent, dfd);
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