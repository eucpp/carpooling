import jade.core.Agent;
import jdk.nashorn.internal.ir.annotations.Ignore;

import java.sql.DriverManager;

public class PassengerAgent extends Agent implements Passenger {

    private final int id;
    private final Passenger.Intention intention;
    private Vehicle vehicle;

    private static int next_id = 0;

    public PassengerAgent(Passenger.Intention intention) {
        this.id = next_id++;
        this.intention = intention;

        System.out.printf("Creating new passenger with id=%d that want to move from %s to %s\n",
                this.id, intention.from.toString(), intention.to.toString());
    }

    @Override
    public int getID() {
        return this.id;
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
