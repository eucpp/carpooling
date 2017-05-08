import java.awt.*;
import java.util.*;

import javax.swing.*;

import org.jgrapht.*;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.ext.*;

import com.mxgraph.util.*;
import com.mxgraph.view.*;
import com.mxgraph.model.*;
import com.mxgraph.swing.*;
import com.mxgraph.layout.*;

public class CarpoolView {

    private static final Dimension DEFAULT_SIZE = new Dimension(800, 600);

    private static final String INTENTION_EDGE_STYLE = "IntensionEdge";
    private static final String PATH_EDGE_STYLE = "PathEdge";

    private static class GraphApplet extends JApplet {
        private JGraphXAdapter<MapModel.Node, MapModel.Edge> jgxAdapter;
        private mxGraphComponent mxGraph;

        GraphApplet(Graph<MapModel.Node, MapModel.Edge> g) {
            mxStylesheet stylesheet = new mxStylesheet();

            Map<String, Object> defaultEdge = stylesheet.getDefaultEdgeStyle();
            defaultEdge.put(mxConstants.STYLE_NOLABEL, 1);

            Map<String, Object> intensionEdgeStyle = new HashMap<>();
            intensionEdgeStyle.put(mxConstants.STYLE_STROKECOLOR, "red");

            Map<String, Object> pathEdgeStyle = new HashMap<>();
            pathEdgeStyle.put(mxConstants.STYLE_FILLCOLOR, Color.GREEN);

            stylesheet.putCellStyle(INTENTION_EDGE_STYLE, intensionEdgeStyle);
            stylesheet.putCellStyle(PATH_EDGE_STYLE, pathEdgeStyle);

            jgxAdapter = new JGraphXAdapter<>(g);
            jgxAdapter.setStylesheet(stylesheet);
            mxGraph = new mxGraphComponent(jgxAdapter);
        }

        @Override
        public void init() {
            mxGraph.setEnabled(false);
            getContentPane().add(new mxGraphComponent(jgxAdapter));
            resize(DEFAULT_SIZE);
            mxIGraphLayout layout = new mxCircleLayout(jgxAdapter);
            layout.execute(jgxAdapter.getDefaultParent());
        }

        public void drawPath(String style, GraphPath<MapModel.Node, MapModel.Edge> path) {
            Map<MapModel.Edge, mxICell> edgeToCell = jgxAdapter.getEdgeToCellMap();
            jgxAdapter.getModel().beginUpdate();
            try {
                for (MapModel.Edge edge : path.getEdgeList()) {
                    mxICell[] cell = { edgeToCell.get(edge) };
                    mxGraph.getGraph().setCellStyle(style, cell);
                }
            } finally {
                jgxAdapter.getModel().endUpdate();
            }
        }
    }

    private MapModel map;
    private JFrame frame;
    private GraphApplet leftApplet;

    public CarpoolView(MapModel map) {
        this.map = map;

        leftApplet = new GraphApplet(this.map.getGraph());
        leftApplet.init();

        frame = new JFrame();
        frame.getContentPane().add(leftApplet);
        frame.setTitle("Carpooling");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
    }

    public void start(ArrayList<PassengerAgent> passengers) {
        for (PassengerAgent passenger: passengers) {
            PassengerAgent.Intention intention = passenger.getIntention();
            GraphPath<MapModel.Node, MapModel.Edge> path =
                DijkstraShortestPath.findPathBetween(map.getGraph(), intention.from, intention.to);
            leftApplet.drawPath(INTENTION_EDGE_STYLE, path);
        }

        frame.setVisible(true);
    }
}
