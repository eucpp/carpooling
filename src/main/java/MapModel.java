import java.util.*;
import org.jgrapht.*;
import org.jgrapht.alg.shortestpath.*;
import org.jgrapht.graph.*;
import org.jgrapht.generate.*;

import org.jgrapht.alg.ConnectivityInspector;

public class MapModel {

    public static class Node {
        public final int id;

        private static int next_id = 0;
        private static HashMap<Integer, Node> nodes;

        public static Node getNodeByID(int id) {
            return nodes.get(id);
        }

        Node() {
            id = next_id++;
            nodes.put(id, this);
        }

        @Override
        public String toString() {
            return Integer.toString(id);
        }
    }

    public static class Edge { }

    public static class Route {
        private List<Edge> path;
        private double length;

        public static final Route INFINITE_ROUTE = new Route(null, Double.MAX_VALUE);

        public static Route emptyRoute() {
            return new Route(new ArrayList<>());
        }

        public Route(List<Edge> path) {
            this(path, path.size());
        }

        List<Edge> getPath() {
            return path;
        }

        public double getLength() {
            return length;
        }

        public void join(Route route) {
            path.addAll(route.path);
            length += route.length;
        }

        private Route(List<Edge> path, double cost) {
            this.path = path;
            this.length = cost;
        }
    }

    private UndirectedGraph<Node, Edge> graph;
    private DijkstraShortestPath<MapModel.Node, MapModel.Edge> dijkstra;

    public static MapModel generate(int n) {
        MapModel model = new MapModel();

        double p = 0.5;
        long seed = 42;
        GraphGenerator<Node, Edge, Node> generator = new GnpRandomGraphGenerator<>(n, p, seed);
        generator.generateGraph(model.graph, () -> new Node(), null);

        ListenableUndirectedGraph<Node, Edge> g = new ListenableUndirectedGraph<>(model.graph);
        ConnectivityInspector<Node, Edge> inspector = new ConnectivityInspector<>(g);
        g.addGraphListener(inspector);
        while (!inspector.isGraphConnected()) {
            List<Set<Node>> components = inspector.connectedSets();
            Set<Node> c1 = components.get(0);
            Set<Node> c2 = components.get(1);
            g.addEdge(c1.iterator().next(), c2.iterator().next());
        }

        return model;
    }

    public UndirectedGraph<Node, Edge> getGraph() {
        return graph;
    }

    public Route getRoute(Node source, Node sink) {
        GraphPath<Node, Edge> path = dijkstra.getPath(source, sink);
        return new Route(path.getEdgeList());
    }

    private MapModel() {
        graph = new SimpleGraph<Node, Edge>(Edge.class);
        dijkstra = new DijkstraShortestPath<>(graph);
    }
}
