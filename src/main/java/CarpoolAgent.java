import java.util.*;
import jade.core.*;

public class CarpoolAgent extends Agent {

    private MapModel map;
    private CarpoolView view;

    private ArrayList<PassengerAgent> passengers;
    private ArrayList<VehicleAgent> vehicles;

    protected void setup() {
        map = MapModel.generate(8);
        passengers = generatePassengers(2, map);
        vehicles = generateVehicles(1, passengers, map);

        view = new CarpoolView(map);

        view.drawPassengers(passengers);
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
                r = rnd.nextInt();
            }
            pickedDrivers.add(r);
            Passenger driver = passengers.get(r);
            VehicleAgent vehicle = new VehicleAgent(driver);
            driver.setVehicle(vehicle);
            vehicles.add(vehicle);
        }
        return vehicles;
    }
}