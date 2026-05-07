package com.guardian.hadoop.shared;

import java.time.Instant;

public class SystemStatusResponse {

    private final boolean backendUp;
    private final boolean clouderaManagerEnabled;
    private final String databaseMode;
    private final long incidentCount;
    private final long suppressedIncidentCount;
    private final Instant lastCmCollectionAt;
    private final Boolean lastCmCollectionSuccess;
    private final String lastCmCollectionMessage;
    private final int lastCmRecentLogCount;
    private final long inspectionRunningCount;
    private final long inspectionFailedCount;
    private final Instant lastInspectionStartedAt;
    private final Instant lastInspectionCompletedAt;
    private final String lastInspectionStatus;
    private final String lastInspectionMessage;

    public SystemStatusResponse(boolean backendUp,
                                boolean clouderaManagerEnabled,
                                String databaseMode,
                                long incidentCount,
                                long suppressedIncidentCount,
                                Instant lastCmCollectionAt,
                                Boolean lastCmCollectionSuccess,
                                String lastCmCollectionMessage,
                                int lastCmRecentLogCount,
                                long inspectionRunningCount,
                                long inspectionFailedCount,
                                Instant lastInspectionStartedAt,
                                Instant lastInspectionCompletedAt,
                                String lastInspectionStatus,
                                String lastInspectionMessage) {
        this.backendUp = backendUp;
        this.clouderaManagerEnabled = clouderaManagerEnabled;
        this.databaseMode = databaseMode;
        this.incidentCount = incidentCount;
        this.suppressedIncidentCount = suppressedIncidentCount;
        this.lastCmCollectionAt = lastCmCollectionAt;
        this.lastCmCollectionSuccess = lastCmCollectionSuccess;
        this.lastCmCollectionMessage = lastCmCollectionMessage;
        this.lastCmRecentLogCount = lastCmRecentLogCount;
        this.inspectionRunningCount = inspectionRunningCount;
        this.inspectionFailedCount = inspectionFailedCount;
        this.lastInspectionStartedAt = lastInspectionStartedAt;
        this.lastInspectionCompletedAt = lastInspectionCompletedAt;
        this.lastInspectionStatus = lastInspectionStatus;
        this.lastInspectionMessage = lastInspectionMessage;
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

    public long getSuppressedIncidentCount() {
        return suppressedIncidentCount;
    }

    public Instant getLastCmCollectionAt() {
        return lastCmCollectionAt;
    }

    public Boolean getLastCmCollectionSuccess() {
        return lastCmCollectionSuccess;
    }

    public String getLastCmCollectionMessage() {
        return lastCmCollectionMessage;
    }

    public int getLastCmRecentLogCount() {
        return lastCmRecentLogCount;
    }

    public long getInspectionRunningCount() {
        return inspectionRunningCount;
    }

    public long getInspectionFailedCount() {
        return inspectionFailedCount;
    }

    public Instant getLastInspectionStartedAt() {
        return lastInspectionStartedAt;
    }

    public Instant getLastInspectionCompletedAt() {
        return lastInspectionCompletedAt;
    }

    public String getLastInspectionStatus() {
        return lastInspectionStatus;
    }

    public String getLastInspectionMessage() {
        return lastInspectionMessage;
    }
}
