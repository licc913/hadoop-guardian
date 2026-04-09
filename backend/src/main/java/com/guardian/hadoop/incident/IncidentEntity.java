package com.guardian.hadoop.incident;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OrderColumn;
import javax.persistence.Table;

@Entity
@Table(name = "incident_event")
public class IncidentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_no", nullable = false, unique = true, length = 64)
    private String incidentNo;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 128)
    private String sourceId;

    @Column(name = "cluster_name", nullable = false, length = 128)
    private String clusterName;

    @Column(name = "service_type", nullable = false, length = 64)
    private String serviceType;

    @Column(nullable = false, length = 32)
    private String severity;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "impact_scope", length = 512)
    private String impactScope;

    @Column(length = 128)
    private String owner;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "incident_evidence_item", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "evidence_text", nullable = false, columnDefinition = "TEXT")
    @OrderColumn(name = "order_no")
    private List<String> evidence = new ArrayList<String>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "incident_avoided_action", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "action_text", nullable = false, columnDefinition = "TEXT")
    @OrderColumn(name = "order_no")
    private List<String> avoidedActions = new ArrayList<String>();

    public IncidentEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIncidentNo() {
        return incidentNo;
    }

    public void setIncidentNo(String incidentNo) {
        this.incidentNo = incidentNo;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getImpactScope() {
        return impactScope;
    }

    public void setImpactScope(String impactScope) {
        this.impactScope = impactScope;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public List<String> getEvidence() {
        return evidence;
    }

    public void setEvidence(List<String> evidence) {
        this.evidence = evidence;
    }

    public List<String> getAvoidedActions() {
        return avoidedActions;
    }

    public void setAvoidedActions(List<String> avoidedActions) {
        this.avoidedActions = avoidedActions;
    }
}
