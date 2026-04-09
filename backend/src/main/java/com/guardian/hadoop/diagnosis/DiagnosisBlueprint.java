package com.guardian.hadoop.diagnosis;

import java.util.List;

public class DiagnosisBlueprint {

    private final String rootCause;
    private final double confidence;
    private final String impactLevel;
    private final String crossComponentPath;
    private final List<String> recommendations;
    private final List<String> followUps;
    private final String actionName;
    private final String actionType;
    private final String actionRiskLevel;
    private final boolean actionRequiresApproval;
    private final String actionRecommendationText;
    private final String generationSource;

    public DiagnosisBlueprint(String rootCause,
                              double confidence,
                              String impactLevel,
                              String crossComponentPath,
                              List<String> recommendations,
                              List<String> followUps,
                              String actionName,
                              String actionType,
                              String actionRiskLevel,
                              boolean actionRequiresApproval,
                              String actionRecommendationText,
                              String generationSource) {
        this.rootCause = rootCause;
        this.confidence = confidence;
        this.impactLevel = impactLevel;
        this.crossComponentPath = crossComponentPath;
        this.recommendations = recommendations;
        this.followUps = followUps;
        this.actionName = actionName;
        this.actionType = actionType;
        this.actionRiskLevel = actionRiskLevel;
        this.actionRequiresApproval = actionRequiresApproval;
        this.actionRecommendationText = actionRecommendationText;
        this.generationSource = generationSource;
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

    public String getActionName() {
        return actionName;
    }

    public String getActionType() {
        return actionType;
    }

    public String getActionRiskLevel() {
        return actionRiskLevel;
    }

    public boolean isActionRequiresApproval() {
        return actionRequiresApproval;
    }

    public String getActionRecommendationText() {
        return actionRecommendationText;
    }

    public String getGenerationSource() {
        return generationSource;
    }
}
