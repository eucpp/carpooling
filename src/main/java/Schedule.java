import java.util.*;

import jade.core.AID;

public class Schedule {

    public static class Route {
        public final ArrayList<MapModel.Edge> path;
        public final double cost;

        private Route(ArrayList<MapModel.Edge> path, double cost) {
            this.path = path;
            this.cost = cost;
        }
    }

    private HashMap<PassengerAgent, Route> routes;

    public Schedule(ArrayList<PassengerAgent> passegers, MapModel map) {
        routes = new HashMap<>();
        for (PassengerAgent passenger: passegers) {
            Route route = new Route(new ArrayList<>(), Double.MAX_VALUE);
            routes.put(passenger, route);
        }
    }

    public void changeRoute(PassengerAgent passenger, ArrayList<MapModel.Edge> path, double cost) {
        routes.computeIfPresent(passenger, (__, ___) -> new Route(path, cost));
    }

    public Set<Map.Entry<PassengerAgent, Route>> getRoutes() {
        return routes.entrySet();
    }
}
