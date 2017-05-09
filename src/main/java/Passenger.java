
public interface Passenger {

    class Intention {
        public final MapModel.Node from;
        public final MapModel.Node to;

        public Intention(MapModel.Node from, MapModel.Node to) {
            this.from = from;
            this.to = to;
        }
    }

    Passenger.Intention getIntention();

    Vehicle getVehicle();
    void setVehicle(Vehicle vehicle);
}
