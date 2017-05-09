import java.util.ArrayList;

public interface Vehicle {

    int getCapacity();

    Passenger getDriver();

    ArrayList<? extends Passenger> getPassengers();
}
