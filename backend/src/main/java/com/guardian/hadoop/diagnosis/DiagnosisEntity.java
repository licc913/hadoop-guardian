package com.guardian.hadoop.diagnosis;

import com.guardian.hadoop.incident.IncidentEntity;
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
import javax.persistence.ManyToOne;
import javax.persistence.OrderColumn;
import javax.persistence.Table;

@Entity
@Table(name = "diagnosis_result")
public class DiagnosisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "incident_id")
    private IncidentEntity incident;

    @Column(nullable = false, length = 64)
    private String subsystem;

    @Column(name = "root_cause", nullable = false, length = 256)
    private String rootCause;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "impact_level", nullable = false, length = 32)
    private String impactLevel;

    @Column(name = "cross_component_path", length = 128)
    private String crossComponentPath;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "diagnosis_recommendation_item", joinColumns = @JoinColumn(name = "diagnosis_id"))
    @Column(name = "recommendation_text", nullable = false, columnDefinition = "TEXT")
    @OrderColumn(name = "order_no")
    private List<String> recommendations = new ArrayList<String>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "diagnosis_followup_item", joinColumns = @JoinColumn(name = "diagnosis_id"))
    @Column(name = "followup_text", nullable = false, columnDefinition = "TEXT")
    @OrderColumn(name = "order_no")
    private List<String> followUps = new ArrayList<String>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DiagnosisEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public IncidentEntity getIncident() {
        return incident;
    }

    public void setIncident(IncidentEntity incident) {
        this.incident = incident;
    }

    public String getSubsystem() {
        return subsystem;
    }

    public void setSubsystem(String subsystem) {
        this.subsystem = subsystem;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getImpactLevel() {
        return impactLevel;
    }

    public void setImpactLevel(String impactLevel) {
        this.impactLevel = impactLevel;
    }

    public String getCrossComponentPath() {
        return crossComponentPath;
    }

    public void setCrossComponentPath(String crossComponentPath) {
        this.crossComponentPath = crossComponentPath;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations;
    }

    public List<String> getFollowUps() {
        return followUps;
    }

    public void setFollowUps(List<String> followUps) {
        this.followUps = followUps;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
