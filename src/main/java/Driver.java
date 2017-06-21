import java.util.ArrayList;

public interface Driver {

    double getInitialRouteCost();

    double getCurrentIncome();

    void acceptCurrentRoute();
    void quitDriving();

    void rememberCurrentRoute();
    boolean hasCurrentRouteChanged();
}
