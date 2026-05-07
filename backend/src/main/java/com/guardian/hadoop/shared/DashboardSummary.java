package com.guardian.hadoop.shared;

public class DashboardSummary {

    private final int openIncidents;
    private final int diagnosingIncidents;
    private final int criticalIncidents;
    private final int actionRequiredIncidents;
    private final int suppressedIncidents;

    public DashboardSummary(int openIncidents,
                            int diagnosingIncidents,
                            int criticalIncidents,
                            int actionRequiredIncidents,
                            int suppressedIncidents) {
        this.openIncidents = openIncidents;
        this.diagnosingIncidents = diagnosingIncidents;
        this.criticalIncidents = criticalIncidents;
        this.actionRequiredIncidents = actionRequiredIncidents;
        this.suppressedIncidents = suppressedIncidents;
    }

    public int getOpenIncidents() {
        return openIncidents;
    }

    public int getDiagnosingIncidents() {
        return diagnosingIncidents;
    }

    public int getCriticalIncidents() {
        return criticalIncidents;
    }

    public int getActionRequiredIncidents() {
        return actionRequiredIncidents;
    }

    public int getSuppressedIncidents() {
        return suppressedIncidents;
    }
}
