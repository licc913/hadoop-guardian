package com.guardian.hadoop.action;

import java.time.Instant;

public class ActionRecommendationRecord {

    private final Long id;
    private final Long incidentId;
    private final Long diagnosisId;
    private final String actionName;
    private final String actionType;
    private final String riskLevel;
    private final boolean requiresApproval;
    private final String recommendationText;
    private final String status;
    private final Instant createdAt;

    public ActionRecommendationRecord(Long id, Long incidentId, Long diagnosisId, String actionName, String actionType,
                                      String riskLevel, boolean requiresApproval, String recommendationText,
                                      String status, Instant createdAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.diagnosisId = diagnosisId;
        this.actionName = actionName;
        this.actionType = actionType;
        this.riskLevel = riskLevel;
        this.requiresApproval = requiresApproval;
        this.recommendationText = recommendationText;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static ActionRecommendationRecord fromEntity(ActionRecommendationEntity entity) {
        return new ActionRecommendationRecord(
            entity.getId(),
            entity.getIncident().getId(),
            entity.getDiagnosis().getId(),
            entity.getActionName(),
            entity.getActionType(),
            entity.getRiskLevel(),
            entity.isRequiresApproval(),
            entity.getRecommendationText(),
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public Long getDiagnosisId() {
        return diagnosisId;
    }

    public String getActionName() {
        return actionName;
    }

    public String getActionType() {
        return actionType;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public String getRecommendationText() {
        return recommendationText;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
