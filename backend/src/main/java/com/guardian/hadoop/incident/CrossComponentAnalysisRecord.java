package com.guardian.hadoop.incident;

import java.util.List;

public class CrossComponentAnalysisRecord {

    private final String primaryService;
    private final String probablePath;
    private final double confidence;
    private final String confidenceLabel;
    private final String summary;
    private final String impactAssessment;
    private final List<String> correlatedServices;
    private final List<String> sharedNodes;
    private final List<String> signalHighlights;
    private final List<String> recommendedChecks;
    private final List<CrossComponentRelatedIncidentRecord> relatedIncidents;

    public CrossComponentAnalysisRecord(String primaryService,
                                        String probablePath,
                                        double confidence,
                                        String confidenceLabel,
                                        String summary,
                                        String impactAssessment,
                                        List<String> correlatedServices,
                                        List<String> sharedNodes,
                                        List<String> signalHighlights,
                                        List<String> recommendedChecks,
                                        List<CrossComponentRelatedIncidentRecord> relatedIncidents) {
        this.primaryService = primaryService;
        this.probablePath = probablePath;
        this.confidence = confidence;
        this.confidenceLabel = confidenceLabel;
        this.summary = summary;
        this.impactAssessment = impactAssessment;
        this.correlatedServices = correlatedServices;
        this.sharedNodes = sharedNodes;
        this.signalHighlights = signalHighlights;
        this.recommendedChecks = recommendedChecks;
        this.relatedIncidents = relatedIncidents;
    }

    public String getPrimaryService() {
        return primaryService;
    }

    public String getProbablePath() {
        return probablePath;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getConfidenceLabel() {
        return confidenceLabel;
    }

    public String getSummary() {
        return summary;
    }

    public String getImpactAssessment() {
        return impactAssessment;
    }

    public List<String> getCorrelatedServices() {
        return correlatedServices;
    }

    public List<String> getSharedNodes() {
        return sharedNodes;
    }

    public List<String> getSignalHighlights() {
        return signalHighlights;
    }

    public List<String> getRecommendedChecks() {
        return recommendedChecks;
    }

    public List<CrossComponentRelatedIncidentRecord> getRelatedIncidents() {
        return relatedIncidents;
    }
}
