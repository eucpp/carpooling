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

    private static final String PASSENGER_NODE_STYLE = "PassengerNode";
    private static final String VEHICLE_NODE_STYLE = "VehicleNode";
    private static final String INTENTION_EDGE_STYLE = "IntensionEdge";
    private static final String PATH_EDGE_STYLE = "PathEdge";

    private static class GraphApplet extends JApplet {
        private JGraphXAdapter<MapModel.Node, MapModel.Edge> jgxAdapter;
        private mxGraphComponent mxGraph;

        GraphApplet(Graph<MapModel.Node, MapModel.Edge> g) {
            mxStylesheet stylesheet = new mxStylesheet();

            Map<String, Object> defaultEdge = stylesheet.getDefaultEdgeStyle();
            defaultEdge.put(mxConstants.STYLE_NOLABEL, true);
            defaultEdge.put(mxConstants.STYLE_ENDARROW, mxConstants.NONE);

            Map<String, Object> passengerNodeStyle = new HashMap<>();
            passengerNodeStyle.put(mxConstants.STYLE_FILLCOLOR, "red");
            passengerNodeStyle.put(mxConstants.STYLE_ROUNDED, true);
            passengerNodeStyle.put(mxConstants.STYLE_ARCSIZE, "70");

            Map<String, Object> vehicleNodeStyle = new HashMap<>();
            vehicleNodeStyle.put(mxConstants.STYLE_FILLCOLOR, "red");

            Map<String, Object> intensionEdgeStyle = new HashMap<>();
            intensionEdgeStyle.put(mxConstants.STYLE_STROKECOLOR, "red");

            Map<String, Object> pathEdgeStyle = new HashMap<>();
            pathEdgeStyle.put(mxConstants.STYLE_STROKECOLOR, "green");

            stylesheet.putCellStyle(PASSENGER_NODE_STYLE, passengerNodeStyle);
            stylesheet.putCellStyle(VEHICLE_NODE_STYLE, vehicleNodeStyle);
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
//            resize(DEFAULT_SIZE);
            setPreferredSize(new Dimension(400, 500));
            mxIGraphLayout layout = new mxCircleLayout(jgxAdapter);
            layout.execute(jgxAdapter.getDefaultParent());
        }

        public void applyStyle(String style, MapModel.Node node) {
            Map<MapModel.Node, mxICell> nodeToCell = jgxAdapter.getVertexToCellMap();
            jgxAdapter.getModel().beginUpdate();
            try {
                mxICell[] cell = { nodeToCell.get(node) };
                mxGraph.getGraph().setCellStyle(style, cell);
            } finally {
                jgxAdapter.getModel().endUpdate();
            }
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
    private GraphApplet rightApplet;

    public CarpoolView(MapModel map) {
        this.map = map;

        leftApplet = new GraphApplet(this.map.getGraph());
        rightApplet = new GraphApplet(this.map.getGraph());
        leftApplet.init();
        rightApplet.init();


        frame = new JFrame();
        frame.getContentPane().setLayout(new GridLayout(1, 2));
        frame.getContentPane().add(leftApplet);
        frame.getContentPane().add(rightApplet);
        frame.setTitle("Carpooling");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setPreferredSize(DEFAULT_SIZE);
        frame.pack();
        frame.setVisible(true);
    }

    public void drawPassengers(ArrayList<? extends Passenger> passengers) {
        for (Passenger passenger: passengers) {
            PassengerAgent.Intention intention = passenger.getIntention();
            GraphPath<MapModel.Node, MapModel.Edge> path =
                DijkstraShortestPath.findPathBetween(map.getGraph(), intention.from, intention.to);

            String nodeStyle = passenger.getVehicle() != null ? VEHICLE_NODE_STYLE : PASSENGER_NODE_STYLE;
            leftApplet.applyStyle(nodeStyle, intention.from);
            leftApplet.drawPath(INTENTION_EDGE_STYLE, path);
        }
    }
}
