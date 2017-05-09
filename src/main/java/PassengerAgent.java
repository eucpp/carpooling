import jade.core.Agent;

import java.sql.DriverManager;

public class PassengerAgent extends Agent implements Passenger {

    private final Passenger.Intention intention;
    private Vehicle vehicle;

    public PassengerAgent(Passenger.Intention intention) {
        this.intention = intention;

        System.out.printf("Creating new passenger that want to move from %s to %s\n",
                intention.from.toString(), intention.to.toString());
    }

    @Override
    public Passenger.Intention getIntention() {
        return this.intention;
    }

    @Override
    public Vehicle getVehicle() {
        return this.vehicle;
    }

    @Override
    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    protected void setup() {
        System.out.println("Starting Passenger Agent " + getLocalName());
    }

}
