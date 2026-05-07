package com.guardian.hadoop.tuning;

import java.util.Collections;
import java.util.List;

public class ParameterOptimizationLlmOutcome {

    private final boolean llmUsed;
    private final String problemSummary;
    private final List<ParameterRecommendationRecord> recommendations;
    private final List<String> sourceEvidence;
    private final List<String> expectedBenefits;
    private final List<String> riskNotes;
    private final List<String> validationSteps;
    private final String llmModel;
    private final String analysisSource;

    public ParameterOptimizationLlmOutcome(boolean llmUsed,
                                           String problemSummary,
                                           List<ParameterRecommendationRecord> recommendations,
                                           List<String> sourceEvidence,
                                           List<String> expectedBenefits,
                                           List<String> riskNotes,
                                           List<String> validationSteps,
                                           String llmModel,
                                           String analysisSource) {
        this.llmUsed = llmUsed;
        this.problemSummary = problemSummary;
        this.recommendations = recommendations == null ? Collections.<ParameterRecommendationRecord>emptyList() : recommendations;
        this.sourceEvidence = sourceEvidence == null ? Collections.<String>emptyList() : sourceEvidence;
        this.expectedBenefits = expectedBenefits == null ? Collections.<String>emptyList() : expectedBenefits;
        this.riskNotes = riskNotes == null ? Collections.<String>emptyList() : riskNotes;
        this.validationSteps = validationSteps == null ? Collections.<String>emptyList() : validationSteps;
        this.llmModel = llmModel;
        this.analysisSource = analysisSource;
    }

    public boolean isLlmUsed() {
        return llmUsed;
    }

    public String getProblemSummary() {
        return problemSummary;
    }

    public List<ParameterRecommendationRecord> getRecommendations() {
        return recommendations;
    }

    public List<String> getSourceEvidence() {
        return sourceEvidence;
    }

    public List<String> getExpectedBenefits() {
        return expectedBenefits;
    }

    public List<String> getRiskNotes() {
        return riskNotes;
    }

    public List<String> getValidationSteps() {
        return validationSteps;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public String getAnalysisSource() {
        return analysisSource;
    }
}
