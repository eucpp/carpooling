import java.util.*;
import java.util.ArrayList;

import jade.core.*;
import jade.domain.*;
import jade.domain.FIPAAgentManagement.*;

import org.json.*;

import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;

public class PassengerAgent extends Agent implements Passenger {

    private final int id;
    private final MapModel.Intention intention;

    private static int next_id = 1;

    public PassengerAgent(MapModel.Intention intention) {
        this.id = next_id++;
        this.intention = intention;

        System.out.printf("%s intention: from %s to %s\n",
                toString(), intention.from.toString(), intention.to.toString());
    }

    @Override
    public int getID() {
        return this.id;
    }

    @Override
    public MapModel.Intention getIntention() {
        return this.intention;
    }

    @Override
    public String toString() {
        return "Passenger" + Integer.toString(id);
    }

    @Override
    protected void setup() {
        System.out.println("Starting Agent " + getLocalName());

        addBehaviour(
                new DriverSearchBehaviour(
                        this, intention, new DriverSearchBehaviour.AcceptDecisionMaker() {
                        }
                )
        );
    }
}
