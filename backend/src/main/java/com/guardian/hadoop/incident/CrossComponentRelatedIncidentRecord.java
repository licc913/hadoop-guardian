package com.guardian.hadoop.incident;

import java.time.Instant;
import java.util.List;

public class CrossComponentRelatedIncidentRecord {

    private final long incidentId;
    private final String incidentNo;
    private final String serviceType;
    private final String severity;
    private final String status;
    private final String title;
    private final int correlationScore;
    private final String relationSummary;
    private final List<String> matchedSignals;
    private final List<String> sharedNodes;
    private final Instant occurredAt;

    public CrossComponentRelatedIncidentRecord(long incidentId,
                                               String incidentNo,
                                               String serviceType,
                                               String severity,
                                               String status,
                                               String title,
                                               int correlationScore,
                                               String relationSummary,
                                               List<String> matchedSignals,
                                               List<String> sharedNodes,
                                               Instant occurredAt) {
        this.incidentId = incidentId;
        this.incidentNo = incidentNo;
        this.serviceType = serviceType;
        this.severity = severity;
        this.status = status;
        this.title = title;
        this.correlationScore = correlationScore;
        this.relationSummary = relationSummary;
        this.matchedSignals = matchedSignals;
        this.sharedNodes = sharedNodes;
        this.occurredAt = occurredAt;
    }

    public long getIncidentId() {
        return incidentId;
    }

    public String getIncidentNo() {
        return incidentNo;
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getSeverity() {
        return severity;
    }

    public String getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public int getCorrelationScore() {
        return correlationScore;
    }

    public String getRelationSummary() {
        return relationSummary;
    }

    public List<String> getMatchedSignals() {
        return matchedSignals;
    }

    public List<String> getSharedNodes() {
        return sharedNodes;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
