import org.jgrapht.*;
import org.jgrapht.graph.*;

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

    public static MapModel generate() {
        MapModel model = new MapModel();

        Node v1 = new Node();
        Node v2 = new Node();
        Node v3 = new Node();
        Node v4 = new Node();

        model.graph.addVertex(v1);
        model.graph.addVertex(v2);
        model.graph.addVertex(v3);
        model.graph.addVertex(v4);

        model.graph.addEdge(v1, v2);
        model.graph.addEdge(v2, v3);
        model.graph.addEdge(v3, v1);
        model.graph.addEdge(v4, v3);

        return model;
    }

    public UndirectedGraph<Node, Edge> getGraph() {
        return graph;
    }

    private MapModel() {
        graph = new SimpleGraph<Node, Edge>(Edge.class);
    }
}
