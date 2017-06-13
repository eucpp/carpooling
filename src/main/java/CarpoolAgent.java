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
    private ArrayList<VehicleAgent> vehicles;

    protected void setup() {
        try {
            map = MapModel.generate(8);

            passengers = generatePassengers(3, map);
            vehicles = generateVehicles(2, passengers, map);

            for (VehicleAgent vehicle : vehicles) {
                registerVehicle(getContainerController(), vehicle);
            }

            for (PassengerAgent passenger : passengers) {
                if (passenger.getVehicle() == null) {
                    registerPassenger(getContainerController(), passenger);
                }
            }

//            view = new CarpoolView(map);
//            view.drawPassengers(passengers);
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

    private static void registerVehicle(ContainerController container, VehicleAgent agent)
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
            PassengerAgent passenger = new PassengerAgent(new PassengerAgent.Intention(from, to));
            passengers.add(passenger);
        }
        return passengers;
    }

    private static ArrayList<VehicleAgent> generateVehicles(int n, ArrayList<? extends Passenger> passengers, MapModel map) {
        ArrayList<VehicleAgent> vehicles = new ArrayList<>(n);
        Set<Integer> pickedDrivers = new HashSet<>();
        Random rnd = new Random();
        for (int i = 0; i < n; ++i) {
            int r = rnd.nextInt(passengers.size());
            while (pickedDrivers.contains(r)) {
                r = rnd.nextInt(passengers.size());
            }
            pickedDrivers.add(r);
            Passenger driver = passengers.get(r);
            VehicleAgent vehicle = new VehicleAgent(driver, map);
            driver.setVehicle(vehicle);
            vehicles.add(vehicle);
        }
        return vehicles;
    }
}