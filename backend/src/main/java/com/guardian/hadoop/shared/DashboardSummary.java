package com.guardian.hadoop.shared;

public class DashboardSummary {

    private final int openIncidents;
    private final int diagnosingIncidents;
    private final int criticalIncidents;
    private final int actionRequiredIncidents;

    public DashboardSummary(int openIncidents, int diagnosingIncidents, int criticalIncidents, int actionRequiredIncidents) {
        this.openIncidents = openIncidents;
        this.diagnosingIncidents = diagnosingIncidents;
        this.criticalIncidents = criticalIncidents;
        this.actionRequiredIncidents = actionRequiredIncidents;
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
}
