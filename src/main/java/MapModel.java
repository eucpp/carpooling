import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import org.jgrapht.*;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.ext.ExportException;
import org.jgrapht.ext.GraphExporter;
import org.jgrapht.graph.*;
import org.jgrapht.generate.*;
import org.jgrapht.alg.shortestpath.*;

import org.jgrapht.alg.ConnectivityInspector;

public class MapModel {

    public static class Node {
        public final int id;

        private static int next_id = 1;
        private static HashMap<Integer, Node> nodes = new HashMap<>();

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

    public static class Intention {
        public final MapModel.Node from;
        public final MapModel.Node to;

        public Intention(MapModel.Node from, MapModel.Node to) {
            this.from = from;
            this.to = to;
        }
    }

    private static final double PRICE_PER_KM = 1.0;

    public class Route {
        private List<Node> path;
        private double length;

        public double getLength() {
            return length;
        }

        public double getCost() {
            return PRICE_PER_KM * getLength();
        }

        public Node getFirst() {
            return path.get(0);
        }

        public Node getLast() {
            return path.get(path.size() - 1);
        }

        public List<Node> getNodes() {
            return path;
        }

        public void join(Route route) {
            assert getLast().id == route.getFirst().id;

            path.addAll(route.path.subList(1, route.path.size()));
            length += route.length;
        }

        @Override
        public String toString() {
            if (path == null) {
                return "INFINITE";
            }
            if (path.isEmpty()) {
                return "";
            }

            return String.join(" -> ",
                    path.stream().map(Node::toString).collect(Collectors.toList()));
        }

        public Route(ArrayList<Node> nodes) {
            this.path = nodes;
            this.length = nodes.size() - 1;
        }

        private Route(Node start) {
            this.path = new ArrayList<>();
            this.path.add(start);
            this.length = 0;
        }

        private Route(GraphPath<Node, Edge> path) {
            this(path, path.getLength());
        }

        private Route(GraphPath<Node, Edge> path, double length) {
            this.path = (path == null) ? null : path.getVertexList();
            this.length = length;
        }
    }

    private UndirectedGraph<Node, Edge> graph;
    private DijkstraShortestPath<MapModel.Node, MapModel.Edge> dijkstra;

    public static MapModel generate(int n) {
        MapModel model = new MapModel();

//        double p = 0.5;
        long seed = 42;
        GraphGenerator<Node, Edge, Node> generator = new ScaleFreeGraphGenerator<>(n, seed);
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
        return new Route(path);
    }

    public final Route INFINITE_ROUTE = new Route(null, Double.MAX_VALUE);

    public Route initRoute(Node start) {
        return new Route(start);
    }

    public Route emptyRoute() {
        return new Route(new GraphWalk<Node, Edge>(graph, new ArrayList<>(), 0));
    }

    public void exportToDot()
        throws ExportException {
        GraphExporter<Node, Edge> exporter = new DOTExporter<>(
                (Node node) -> Integer.toString(node.id),
                null, null
        );
        exporter.exportGraph(graph, new File("graph.dot"));
    }

    private MapModel() {
        graph = new SimpleGraph<Node, Edge>(Edge.class);
        dijkstra = new DijkstraShortestPath<>(graph);
    }
}
