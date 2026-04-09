package com.guardian.hadoop.diagnosis;

import java.time.Instant;
import java.util.List;

public class DiagnosisRecord {

    private final Long id;
    private final Long incidentId;
    private final String subsystem;
    private final String rootCause;
    private final double confidence;
    private final String impactLevel;
    private final String crossComponentPath;
    private final List<String> recommendations;
    private final List<String> followUps;
    private final Instant createdAt;

    public DiagnosisRecord(Long id, Long incidentId, String subsystem, String rootCause, double confidence,
                           String impactLevel, String crossComponentPath, List<String> recommendations,
                           List<String> followUps, Instant createdAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.subsystem = subsystem;
        this.rootCause = rootCause;
        this.confidence = confidence;
        this.impactLevel = impactLevel;
        this.crossComponentPath = crossComponentPath;
        this.recommendations = recommendations;
        this.followUps = followUps;
        this.createdAt = createdAt;
    }

    public static DiagnosisRecord fromEntity(DiagnosisEntity entity) {
        return new DiagnosisRecord(
            entity.getId(),
            entity.getIncident().getId(),
            entity.getSubsystem(),
            entity.getRootCause(),
            entity.getConfidence(),
            entity.getImpactLevel(),
            entity.getCrossComponentPath(),
            entity.getRecommendations(),
            entity.getFollowUps(),
            entity.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getSubsystem() {
        return subsystem;
    }

    public String getRootCause() {
        return rootCause;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getImpactLevel() {
        return impactLevel;
    }

    public String getCrossComponentPath() {
        return crossComponentPath;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    public List<String> getFollowUps() {
        return followUps;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
