package com.guardian.hadoop.sql;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "sql_optimization_record")
public class SqlOptimizationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "engine_type", nullable = false, length = 32)
    private String engineType;

    @Column(name = "original_sql", nullable = false, columnDefinition = "TEXT")
    private String originalSql;

    @Column(name = "table_schema_note", columnDefinition = "TEXT")
    private String tableSchemaNote;

    @Column(name = "partition_info", columnDefinition = "TEXT")
    private String partitionInfo;

    @Column(name = "explain_text", columnDefinition = "TEXT")
    private String explainText;

    @Column(name = "error_text", columnDefinition = "TEXT")
    private String errorText;

    @Column(name = "optimization_goal", columnDefinition = "TEXT")
    private String optimizationGoal;

    @Column(name = "problem_summary", nullable = false, columnDefinition = "TEXT")
    private String problemSummary;

    @Column(name = "optimized_sql", nullable = false, columnDefinition = "TEXT")
    private String optimizedSql;

    @Column(name = "optimization_points", nullable = false, columnDefinition = "TEXT")
    private String optimizationPoints;

    @Column(name = "risk_notes", nullable = false, columnDefinition = "TEXT")
    private String riskNotes;

    @Column(name = "validation_steps", nullable = false, columnDefinition = "TEXT")
    private String validationSteps;

    @Column(name = "rule_findings", nullable = false, columnDefinition = "TEXT")
    private String ruleFindings;

    @Column(name = "llm_model", length = 128)
    private String llmModel;

    @Column(name = "analysis_source", nullable = false, length = 32)
    private String analysisSource;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public String getEngineType() { return engineType; }
    public void setEngineType(String engineType) { this.engineType = engineType; }
    public String getOriginalSql() { return originalSql; }
    public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
    public String getTableSchemaNote() { return tableSchemaNote; }
    public void setTableSchemaNote(String tableSchemaNote) { this.tableSchemaNote = tableSchemaNote; }
    public String getPartitionInfo() { return partitionInfo; }
    public void setPartitionInfo(String partitionInfo) { this.partitionInfo = partitionInfo; }
    public String getExplainText() { return explainText; }
    public void setExplainText(String explainText) { this.explainText = explainText; }
    public String getErrorText() { return errorText; }
    public void setErrorText(String errorText) { this.errorText = errorText; }
    public String getOptimizationGoal() { return optimizationGoal; }
    public void setOptimizationGoal(String optimizationGoal) { this.optimizationGoal = optimizationGoal; }
    public String getProblemSummary() { return problemSummary; }
    public void setProblemSummary(String problemSummary) { this.problemSummary = problemSummary; }
    public String getOptimizedSql() { return optimizedSql; }
    public void setOptimizedSql(String optimizedSql) { this.optimizedSql = optimizedSql; }
    public String getOptimizationPoints() { return optimizationPoints; }
    public void setOptimizationPoints(String optimizationPoints) { this.optimizationPoints = optimizationPoints; }
    public String getRiskNotes() { return riskNotes; }
    public void setRiskNotes(String riskNotes) { this.riskNotes = riskNotes; }
    public String getValidationSteps() { return validationSteps; }
    public void setValidationSteps(String validationSteps) { this.validationSteps = validationSteps; }
    public String getRuleFindings() { return ruleFindings; }
    public void setRuleFindings(String ruleFindings) { this.ruleFindings = ruleFindings; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
    public String getAnalysisSource() { return analysisSource; }
    public void setAnalysisSource(String analysisSource) { this.analysisSource = analysisSource; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
