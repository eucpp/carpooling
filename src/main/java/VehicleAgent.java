import jade.core.Agent;
import java.util.ArrayList;
import java.util.Random;

public class VehicleAgent extends Agent {

    private final PassengerAgent driver;
    private ArrayList<PassengerAgent> passengers;
    private final int capacity;

    private static final Random rnd = new Random();

    public VehicleAgent(PassengerAgent driver) {
        this.driver = driver;
        this.passengers = new ArrayList<>();

        double x = rnd.nextDouble();
        if (x < 0.1) {
            this.capacity = 2;
        } else if (x < 0.5) {
            this.capacity = 4;
        } else {
            this.capacity = 5;
        }
    }

    protected void setup() {
        System.out.println("Starting Driver Agent " + getLocalName());
    }
}
