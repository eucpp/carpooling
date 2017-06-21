import jade.core.AID;

import java.util.ArrayList;

public interface Driver {

    double getInitialRouteCost();

    double getCurrentIncome();

    void acceptCurrentRoute();
    void quitDriving();

    void rememberCurrentRoute();
    boolean hasCurrentRouteChanged();

    void incAttemptCount();

    boolean inBlackList(AID agent);
//    void addToBlackList(AID agent);
//    void removeFromBlackList(AID agent);
}
