package com.guardian.hadoop.knowledge;

import java.util.List;

public class AiGuidanceRecord {

    private final String incidentSynopsis;
    private final String probableScenario;
    private final double confidence;
    private final String confidenceLabel;
    private final String confidenceMethod;
    private final List<String> confidenceReasons;
    private final List<String> signalHighlights;
    private final List<String> missingSignals;
    private final List<String> evidenceHighlights;
    private final List<String> recommendedOrder;
    private final String planSummary;
    private final List<String> concretePlan;
    private final List<String> operatorNotes;
    private final List<Long> linkedKnowledgeIds;
    private final List<String> jmxInsights;

    public AiGuidanceRecord(String incidentSynopsis,
                            String probableScenario,
                            double confidence,
                            String confidenceLabel,
                            String confidenceMethod,
                            List<String> confidenceReasons,
                            List<String> signalHighlights,
                            List<String> missingSignals,
                            List<String> evidenceHighlights,
                            List<String> recommendedOrder,
                            String planSummary,
                            List<String> concretePlan,
                            List<String> operatorNotes,
                            List<Long> linkedKnowledgeIds,
                            List<String> jmxInsights) {
        this.incidentSynopsis = incidentSynopsis;
        this.probableScenario = probableScenario;
        this.confidence = confidence;
        this.confidenceLabel = confidenceLabel;
        this.confidenceMethod = confidenceMethod;
        this.confidenceReasons = confidenceReasons;
        this.signalHighlights = signalHighlights;
        this.missingSignals = missingSignals;
        this.evidenceHighlights = evidenceHighlights;
        this.recommendedOrder = recommendedOrder;
        this.planSummary = planSummary;
        this.concretePlan = concretePlan;
        this.operatorNotes = operatorNotes;
        this.linkedKnowledgeIds = linkedKnowledgeIds;
        this.jmxInsights = jmxInsights;
    }

    public String getIncidentSynopsis() { return incidentSynopsis; }
    public String getProbableScenario() { return probableScenario; }
    public double getConfidence() { return confidence; }
    public String getConfidenceLabel() { return confidenceLabel; }
    public String getConfidenceMethod() { return confidenceMethod; }
    public List<String> getConfidenceReasons() { return confidenceReasons; }
    public List<String> getSignalHighlights() { return signalHighlights; }
    public List<String> getMissingSignals() { return missingSignals; }
    public List<String> getEvidenceHighlights() { return evidenceHighlights; }
    public List<String> getRecommendedOrder() { return recommendedOrder; }
    public String getPlanSummary() { return planSummary; }
    public List<String> getConcretePlan() { return concretePlan; }
    public List<String> getOperatorNotes() { return operatorNotes; }
    public List<Long> getLinkedKnowledgeIds() { return linkedKnowledgeIds; }
    public List<String> getJmxInsights() { return jmxInsights; }
}
