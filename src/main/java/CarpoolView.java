import java.awt.*;
import java.util.*;

import javax.swing.*;

import com.mxgraph.layout.orthogonal.mxOrthogonalLayout;
import org.jgrapht.*;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.ext.*;

import com.mxgraph.util.*;
import com.mxgraph.view.*;
import com.mxgraph.model.*;
import com.mxgraph.swing.*;
import com.mxgraph.layout.*;

public class CarpoolView {

    private static final Dimension DEFAULT_SIZE = new Dimension(400, 400);

    private static final String PASSENGER_NODE_STYLE = "PassengerNode";
    private static final String VEHICLE_NODE_STYLE = "VehicleNode";
    private static final String INTENTION_EDGE_STYLE = "IntensionEdge";
    private static final String PATH_EDGE_STYLE = "PathEdge";

    private static final String[] COLORS = new String[] {
        "FF0000", "00FF00", "0000FF", "FFFF00", "FF00FF", "00FFFF", "000000",
        "800000", "008000", "000080", "808000", "800080", "008080", "808080",
        "C00000", "00C000", "0000C0", "C0C000", "C000C0", "00C0C0", "C0C0C0",
        "400000", "004000", "000040", "404000", "400040", "004040", "404040",
        "200000", "002000", "000020", "202000", "200020", "002020", "202020",
        "600000", "006000", "000060", "606000", "600060", "006060", "606060",
        "A00000", "00A000", "0000A0", "A0A000", "A000A0", "00A0A0", "A0A0A0",
        "E00000", "00E000", "0000E0", "E0E000", "E000E0", "00E0E0", "E0E0E0",
    };

    private static class GraphApplet extends JApplet {
        private JGraphXAdapter<MapModel.Node, MapModel.Edge> jgxAdapter;
        private mxGraphComponent mxGraph;

        GraphApplet(Graph<MapModel.Node, MapModel.Edge> g) {
            mxStylesheet stylesheet = new mxStylesheet();

            Map<String, Object> defaultEdge = stylesheet.getDefaultEdgeStyle();
            defaultEdge.put(mxConstants.STYLE_NOLABEL, true);
            defaultEdge.put(mxConstants.STYLE_ENDARROW, mxConstants.NONE);

            Map<String, Object> passengerNodeStyle = new HashMap<>();
//            passengerNodeStyle.put(mxConstants.STYLE_FILLCOLOR, "red");
            passengerNodeStyle.put(mxConstants.STYLE_ROUNDED, true);
            passengerNodeStyle.put(mxConstants.STYLE_ARCSIZE, "100");

            Map<String, Object> vehicleNodeStyle = new HashMap<>();
//            vehicleNodeStyle.put(mxConstants.STYLE_FILLCOLOR, "red");

            Map<String, Object> intensionEdgeStyle = new HashMap<>();
//            intensionEdgeStyle.put(mxConstants.STYLE_STROKECOLOR, "red");

            Map<String, Object> pathEdgeStyle = new HashMap<>();
//            pathEdgeStyle.put(mxConstants.STYLE_STROKECOLOR, "green");

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
//            setPreferredSize(new Dimension(400, 500));
            mxIGraphLayout layout = new mxFastOrganicLayout(jgxAdapter);
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
//        frame.setPreferredSize(DEFAULT_SIZE);
        frame.pack();
//        frame.setEnabled(false);
        frame.setVisible(true);
    }

    public void drawPassengers(ArrayList<? extends Passenger> passengers) {
        for (Passenger passenger: passengers) {
            PassengerAgent.Intention intention = passenger.getIntention();
            GraphPath<MapModel.Node, MapModel.Edge> path =
                DijkstraShortestPath.findPathBetween(map.getGraph(), intention.from, intention.to);

            String nodeStyle = passenger.getVehicle() != null ? VEHICLE_NODE_STYLE : PASSENGER_NODE_STYLE;
            nodeStyle += ";fillColor=" + COLORS[passenger.getID()];

            String edgeStyle = INTENTION_EDGE_STYLE;
            edgeStyle += ";strokeColor=" + COLORS[passenger.getID()];

            leftApplet.applyStyle(nodeStyle, intention.from);
            leftApplet.drawPath(edgeStyle, path);
        }
    }
}
