package com.guardian.hadoop.shared;

public class SystemStatusResponse {

    private final boolean backendUp;
    private final boolean clouderaManagerEnabled;
    private final String databaseMode;
    private final long incidentCount;

    public SystemStatusResponse(boolean backendUp, boolean clouderaManagerEnabled, String databaseMode, long incidentCount) {
        this.backendUp = backendUp;
        this.clouderaManagerEnabled = clouderaManagerEnabled;
        this.databaseMode = databaseMode;
        this.incidentCount = incidentCount;
    }

    public boolean isBackendUp() {
        return backendUp;
    }

    public boolean isClouderaManagerEnabled() {
        return clouderaManagerEnabled;
    }

    public String getDatabaseMode() {
        return databaseMode;
    }

    public long getIncidentCount() {
        return incidentCount;
    }
}
