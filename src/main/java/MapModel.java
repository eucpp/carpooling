import java.util.*;
import org.jgrapht.*;
import org.jgrapht.alg.*;
import org.jgrapht.graph.*;
import org.jgrapht.generate.*;

public class MapModel {

    public static class Node {
        public final int id;

        private static int next_id = 0;

        Node() {
            id = next_id++;
        }

        @Override
        public String toString() {
            return Integer.toString(id);
        }
    }

    public static class Edge { }

    private UndirectedGraph<Node, Edge> graph;

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

    private MapModel() {
        graph = new SimpleGraph<Node, Edge>(Edge.class);
    }
}
