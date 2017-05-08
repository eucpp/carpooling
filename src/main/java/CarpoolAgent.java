import jade.core.Agent;

import javax.swing.*;

public class CarpoolAgent extends Agent {

    private MapModel model;
    private CarpoolView view;

    protected void setup() {
        model = MapModel.generate();
        view = new CarpoolView(model);
        view.start();
    }
}