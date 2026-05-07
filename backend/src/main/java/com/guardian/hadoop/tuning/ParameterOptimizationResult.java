package com.guardian.hadoop.tuning;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ParameterOptimizationResult {

    private final Long id;
    private final String clusterName;
    private final String serviceName;
    private final String serviceType;
    private final String componentVersion;
    private final String currentSymptoms;
    private final String optimizationGoal;
    private final String configSnapshotText;
    private final String sourceCodeHints;
    private final String problemSummary;
    private final List<ParameterRecommendationRecord> recommendations;
    private final List<String> sourceEvidence;
    private final List<String> expectedBenefits;
    private final List<String> riskNotes;
    private final List<String> validationSteps;
    private final List<String> ruleFindings;
    private final String llmModel;
    private final String analysisSource;
    private final String createdBy;
    private final Instant createdAt;

    public ParameterOptimizationResult(Long id,
                                       String clusterName,
                                       String serviceName,
                                       String serviceType,
                                       String componentVersion,
                                       String currentSymptoms,
                                       String optimizationGoal,
                                       String configSnapshotText,
                                       String sourceCodeHints,
                                       String problemSummary,
                                       List<ParameterRecommendationRecord> recommendations,
                                       List<String> sourceEvidence,
                                       List<String> expectedBenefits,
                                       List<String> riskNotes,
                                       List<String> validationSteps,
                                       List<String> ruleFindings,
                                       String llmModel,
                                       String analysisSource,
                                       String createdBy,
                                       Instant createdAt) {
        this.id = id;
        this.clusterName = clusterName;
        this.serviceName = serviceName;
        this.serviceType = serviceType;
        this.componentVersion = componentVersion;
        this.currentSymptoms = currentSymptoms;
        this.optimizationGoal = optimizationGoal;
        this.configSnapshotText = configSnapshotText;
        this.sourceCodeHints = sourceCodeHints;
        this.problemSummary = problemSummary;
        this.recommendations = recommendations;
        this.sourceEvidence = sourceEvidence;
        this.expectedBenefits = expectedBenefits;
        this.riskNotes = riskNotes;
        this.validationSteps = validationSteps;
        this.ruleFindings = ruleFindings;
        this.llmModel = llmModel;
        this.analysisSource = analysisSource;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public static ParameterOptimizationResult fromEntity(ParameterOptimizationEntity entity) {
        return new ParameterOptimizationResult(
            entity.getId(),
            entity.getClusterName(),
            entity.getServiceName(),
            entity.getServiceType(),
            entity.getComponentVersion(),
            entity.getCurrentSymptoms(),
            entity.getOptimizationGoal(),
            entity.getConfigSnapshotText(),
            entity.getSourceCodeHints(),
            entity.getProblemSummary(),
            splitRecommendations(entity.getRecommendedParameters()),
            splitLines(entity.getSourceEvidence()),
            splitLines(entity.getExpectedBenefits()),
            splitLines(entity.getRiskNotes()),
            splitLines(entity.getValidationSteps()),
            splitLines(entity.getRuleFindings()),
            entity.getLlmModel(),
            entity.getAnalysisSource(),
            entity.getCreatedBy(),
            entity.getCreatedAt()
        );
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split("\\R+"))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .collect(Collectors.toList());
    }

    private static List<ParameterRecommendationRecord> splitRecommendations(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split("\\R+"))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .map(ParameterOptimizationResult::toRecommendation)
            .collect(Collectors.toList());
    }

    private static ParameterRecommendationRecord toRecommendation(String line) {
        String[] parts = line.split("\\|", 4);
        String configKey = parts.length > 0 ? parts[0].trim() : "";
        String currentValue = parts.length > 1 ? parts[1].trim() : "";
        String recommendedValue = parts.length > 2 ? parts[2].trim() : "";
        String reason = parts.length > 3 ? parts[3].trim() : "";
        return new ParameterRecommendationRecord(configKey, currentValue, recommendedValue, reason);
    }

    public Long getId() {
        return id;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getComponentVersion() {
        return componentVersion;
    }

    public String getCurrentSymptoms() {
        return currentSymptoms;
    }

    public String getOptimizationGoal() {
        return optimizationGoal;
    }

    public String getConfigSnapshotText() {
        return configSnapshotText;
    }

    public String getSourceCodeHints() {
        return sourceCodeHints;
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

    public List<String> getRuleFindings() {
        return ruleFindings;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public String getAnalysisSource() {
        return analysisSource;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
