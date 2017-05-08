import jade.core.Agent;

import java.sql.DriverManager;

public class PassengerAgent extends Agent {

    public static class Intention {
        public final MapModel.Node from;
        public final MapModel.Node to;

        public Intention(MapModel.Node from, MapModel.Node to) {
            this.from = from;
            this.to = to;
        }
    }

    private final Intention intention;
    private VehicleAgent vehicle;

    public PassengerAgent(Intention intention) {
        this.intention = intention;
    }

    public Intention getIntention() {
        return this.intention;
    }

    public VehicleAgent getVehicle() {
        return this.vehicle;
    }

    public void setVehicle(VehicleAgent vehicle) {
        this.vehicle = vehicle;
    }

    protected void setup() {
        System.out.println("Starting Passenger Agent " + getLocalName());
    }

}
