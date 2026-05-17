package com.guardian.hadoop.tuning;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "parameter_optimization_record")
public class ParameterOptimizationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_name", nullable = false, length = 128)
    private String clusterName;

    @Column(name = "service_name", nullable = false, length = 128)
    private String serviceName;

    @Column(name = "service_type", nullable = false, length = 64)
    private String serviceType;

    @Column(name = "component_version", columnDefinition = "text")
    private String componentVersion;

    @Column(name = "current_symptoms", columnDefinition = "text")
    private String currentSymptoms;

    @Column(name = "optimization_goal", columnDefinition = "text")
    private String optimizationGoal;

    @Column(name = "config_snapshot_text", columnDefinition = "text", nullable = false)
    private String configSnapshotText;

    @Column(name = "source_code_hints", columnDefinition = "text")
    private String sourceCodeHints;

    @Column(name = "problem_summary", columnDefinition = "text", nullable = false)
    private String problemSummary;

    @Column(name = "recommended_parameters", columnDefinition = "text", nullable = false)
    private String recommendedParameters;

    @Column(name = "source_evidence", columnDefinition = "text", nullable = false)
    private String sourceEvidence;

    @Column(name = "expected_benefits", columnDefinition = "text", nullable = false)
    private String expectedBenefits;

    @Column(name = "risk_notes", columnDefinition = "text", nullable = false)
    private String riskNotes;

    @Column(name = "validation_steps", columnDefinition = "text", nullable = false)
    private String validationSteps;

    @Column(name = "rule_findings", columnDefinition = "text", nullable = false)
    private String ruleFindings;

    @Column(name = "llm_model", columnDefinition = "text")
    private String llmModel;

    @Column(name = "analysis_source", nullable = false, length = 32)
    private String analysisSource;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getComponentVersion() {
        return componentVersion;
    }

    public void setComponentVersion(String componentVersion) {
        this.componentVersion = componentVersion;
    }

    public String getCurrentSymptoms() {
        return currentSymptoms;
    }

    public void setCurrentSymptoms(String currentSymptoms) {
        this.currentSymptoms = currentSymptoms;
    }

    public String getOptimizationGoal() {
        return optimizationGoal;
    }

    public void setOptimizationGoal(String optimizationGoal) {
        this.optimizationGoal = optimizationGoal;
    }

    public String getConfigSnapshotText() {
        return configSnapshotText;
    }

    public void setConfigSnapshotText(String configSnapshotText) {
        this.configSnapshotText = configSnapshotText;
    }

    public String getSourceCodeHints() {
        return sourceCodeHints;
    }

    public void setSourceCodeHints(String sourceCodeHints) {
        this.sourceCodeHints = sourceCodeHints;
    }

    public String getProblemSummary() {
        return problemSummary;
    }

    public void setProblemSummary(String problemSummary) {
        this.problemSummary = problemSummary;
    }

    public String getRecommendedParameters() {
        return recommendedParameters;
    }

    public void setRecommendedParameters(String recommendedParameters) {
        this.recommendedParameters = recommendedParameters;
    }

    public String getSourceEvidence() {
        return sourceEvidence;
    }

    public void setSourceEvidence(String sourceEvidence) {
        this.sourceEvidence = sourceEvidence;
    }

    public String getExpectedBenefits() {
        return expectedBenefits;
    }

    public void setExpectedBenefits(String expectedBenefits) {
        this.expectedBenefits = expectedBenefits;
    }

    public String getRiskNotes() {
        return riskNotes;
    }

    public void setRiskNotes(String riskNotes) {
        this.riskNotes = riskNotes;
    }

    public String getValidationSteps() {
        return validationSteps;
    }

    public void setValidationSteps(String validationSteps) {
        this.validationSteps = validationSteps;
    }

    public String getRuleFindings() {
        return ruleFindings;
    }

    public void setRuleFindings(String ruleFindings) {
        this.ruleFindings = ruleFindings;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public String getAnalysisSource() {
        return analysisSource;
    }

    public void setAnalysisSource(String analysisSource) {
        this.analysisSource = analysisSource;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
