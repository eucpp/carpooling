import java.awt.*;

import javax.swing.*;

import org.jgrapht.*;
import org.jgrapht.ext.*;
import org.jgrapht.graph.*;

import com.mxgraph.layout.*;
import com.mxgraph.swing.*;

public class CarpoolView {

    private static final Dimension DEFAULT_SIZE = new Dimension(800, 600);

    private static class GraphApplet extends JApplet {
        //private static final long serialVersionUID = 2202072534703043194L;

        private JGraphXAdapter<MapModel.Node, MapModel.Edge> jgxAdapter;
        private mxGraphComponent mxGraph;

        GraphApplet(Graph<MapModel.Node, MapModel.Edge> g) {
            jgxAdapter = new JGraphXAdapter<>(g);
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
    }

    private JFrame frame;

    public CarpoolView(MapModel map) {
        JApplet applet = new GraphApplet(map.getGraph());
        applet.init();

        frame = new JFrame();
        frame.getContentPane().add(applet);
        frame.setTitle("Carpooling");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
    }

    public void start() {
        frame.setVisible(true);
    }
}
