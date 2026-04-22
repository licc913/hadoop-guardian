package com.guardian.hadoop.integration.cm;

import java.time.Instant;

public class CmServiceLogSnapshotRecord {

    private final String clusterName;
    private final String serviceName;
    private final String serviceType;
    private final String logType;
    private final String logText;
    private final Instant collectedAt;

    public CmServiceLogSnapshotRecord(String clusterName,
                                      String serviceName,
                                      String serviceType,
                                      String logType,
                                      String logText,
                                      Instant collectedAt) {
        this.clusterName = clusterName;
        this.serviceName = serviceName;
        this.serviceType = serviceType;
        this.logType = logType;
        this.logText = logText;
        this.collectedAt = collectedAt;
    }

    public static CmServiceLogSnapshotRecord fromEntity(CmServiceLogSnapshotEntity entity) {
        return new CmServiceLogSnapshotRecord(
            entity.getClusterName(),
            entity.getServiceName(),
            entity.getServiceType(),
            entity.getLogType(),
            entity.getLogText(),
            entity.getCollectedAt()
        );
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getLogType() {
        return logType;
    }

    public String getLogText() {
        return logText;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }
}
