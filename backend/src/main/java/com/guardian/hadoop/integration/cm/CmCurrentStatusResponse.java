package com.guardian.hadoop.integration.cm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CmCurrentStatusResponse {

    private final boolean success;
    private final boolean enabled;
    private final String message;
    private final String details;
    private final String endpoint;
    private final Instant collectedAt;
    private final int serviceCount;
    private final int unhealthyServiceCount;
    private final List<CmServiceStatusRecord> services;
    private final List<CmServiceLogSnapshotRecord> recentLogs;

    public CmCurrentStatusResponse(boolean success,
                                   boolean enabled,
                                   String message,
                                   String details,
                                   String endpoint,
                                   Instant collectedAt,
                                   int serviceCount,
                                   int unhealthyServiceCount,
                                   List<CmServiceStatusRecord> services,
                                   List<CmServiceLogSnapshotRecord> recentLogs) {
        this.success = success;
        this.enabled = enabled;
        this.message = message;
        this.details = details;
        this.endpoint = endpoint;
        this.collectedAt = collectedAt;
        this.serviceCount = serviceCount;
        this.unhealthyServiceCount = unhealthyServiceCount;
        this.services = services == null ? new ArrayList<CmServiceStatusRecord>() : services;
        this.recentLogs = recentLogs == null ? new ArrayList<CmServiceLogSnapshotRecord>() : recentLogs;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public int getServiceCount() {
        return serviceCount;
    }

    public int getUnhealthyServiceCount() {
        return unhealthyServiceCount;
    }

    public List<CmServiceStatusRecord> getServices() {
        return services;
    }

    public List<CmServiceLogSnapshotRecord> getRecentLogs() {
        return recentLogs;
    }
}
