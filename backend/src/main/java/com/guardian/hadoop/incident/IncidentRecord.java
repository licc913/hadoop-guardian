package com.guardian.hadoop.incident;

import java.time.Instant;
import java.util.List;

public class IncidentRecord {

    private final Long id;
    private final String incidentNo;
    private final String clusterName;
    private final String serviceType;
    private final String severity;
    private final String status;
    private final String title;
    private final String summary;
    private final String impactScope;
    private final String owner;
    private final String governanceStatus;
    private final String eventFingerprint;
    private final Instant occurredAt;
    private final Instant firstSeenAt;
    private final Instant lastSeenAt;
    private final int occurrenceCount;
    private final Instant suppressedUntil;
    private final String governanceNote;
    private final List<String> evidence;
    private final List<String> avoidedActions;

    public IncidentRecord(Long id, String incidentNo, String clusterName, String serviceType, String severity,
                          String status, String title, String summary, String impactScope, String owner,
                          String governanceStatus, String eventFingerprint, Instant occurredAt,
                          Instant firstSeenAt, Instant lastSeenAt, int occurrenceCount,
                          Instant suppressedUntil, String governanceNote,
                          List<String> evidence, List<String> avoidedActions) {
        this.id = id;
        this.incidentNo = incidentNo;
        this.clusterName = clusterName;
        this.serviceType = serviceType;
        this.severity = severity;
        this.status = status;
        this.title = title;
        this.summary = summary;
        this.impactScope = impactScope;
        this.owner = owner;
        this.governanceStatus = governanceStatus;
        this.eventFingerprint = eventFingerprint;
        this.occurredAt = occurredAt;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.occurrenceCount = occurrenceCount;
        this.suppressedUntil = suppressedUntil;
        this.governanceNote = governanceNote;
        this.evidence = evidence;
        this.avoidedActions = avoidedActions;
    }

    public static IncidentRecord fromEntity(IncidentEntity entity) {
        return new IncidentRecord(
            entity.getId(),
            entity.getIncidentNo(),
            entity.getClusterName(),
            entity.getServiceType(),
            entity.getSeverity(),
            entity.getStatus(),
            entity.getTitle(),
            entity.getSummary(),
            entity.getImpactScope(),
            entity.getOwner(),
            entity.getGovernanceStatus(),
            entity.getEventFingerprint(),
            entity.getOccurredAt(),
            entity.getFirstSeenAt(),
            entity.getLastSeenAt(),
            entity.getOccurrenceCount() == null ? 0 : entity.getOccurrenceCount(),
            entity.getSuppressedUntil(),
            entity.getGovernanceNote(),
            entity.getEvidence(),
            entity.getAvoidedActions()
        );
    }

    public Long getId() {
        return id;
    }

    public String getIncidentNo() {
        return incidentNo;
    }

    public String getClusterName() {
        return clusterName;
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

    public String getSummary() {
        return summary;
    }

    public String getImpactScope() {
        return impactScope;
    }

    public String getOwner() {
        return owner;
    }

    public String getGovernanceStatus() {
        return governanceStatus;
    }

    public String getEventFingerprint() {
        return eventFingerprint;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public int getOccurrenceCount() {
        return occurrenceCount;
    }

    public Instant getSuppressedUntil() {
        return suppressedUntil;
    }

    public String getGovernanceNote() {
        return governanceNote;
    }

    public List<String> getEvidence() {
        return evidence;
    }

    public List<String> getAvoidedActions() {
        return avoidedActions;
    }
}
