import jade.core.Agent;
import java.util.ArrayList;
import java.util.Random;

public class VehicleAgent extends Agent implements Vehicle {

    private final Passenger driver;
    private ArrayList<Passenger> passengers;
    private final int capacity;

//    private static final Random rnd = new Random();

    public VehicleAgent(Passenger driver) {
        this.driver = driver;
        this.passengers = new ArrayList<>();
        this.capacity = 3;
//        double x = rnd.nextDouble();
//        if (x < 0.1) {
//            this.capacity = 2;
//        } else if (x < 0.5) {
//            this.capacity = 4;
//        } else {
//            this.capacity = 5;
//        }
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
        return this.capacity;
    }

    @Override
    public String toString() {
        return "Driver" + driver.getID();
    }

    protected void setup() {
        System.out.println("Starting Vehicle Agent " + getLocalName());
    }
}
