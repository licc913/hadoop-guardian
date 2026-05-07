package com.guardian.hadoop.sql;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SqlOptimizationResult {

    private final Long id;
    private final String engineType;
    private final String originalSql;
    private final String tableSchemaNote;
    private final String partitionInfo;
    private final String explainText;
    private final String errorText;
    private final String optimizationGoal;
    private final String problemSummary;
    private final String optimizedSql;
    private final List<String> optimizationPoints;
    private final List<String> riskNotes;
    private final List<String> validationSteps;
    private final List<String> ruleFindings;
    private final String llmModel;
    private final String analysisSource;
    private final String createdBy;
    private final Instant createdAt;

    public SqlOptimizationResult(Long id,
                                 String engineType,
                                 String originalSql,
                                 String tableSchemaNote,
                                 String partitionInfo,
                                 String explainText,
                                 String errorText,
                                 String optimizationGoal,
                                 String problemSummary,
                                 String optimizedSql,
                                 List<String> optimizationPoints,
                                 List<String> riskNotes,
                                 List<String> validationSteps,
                                 List<String> ruleFindings,
                                 String llmModel,
                                 String analysisSource,
                                 String createdBy,
                                 Instant createdAt) {
        this.id = id;
        this.engineType = engineType;
        this.originalSql = originalSql;
        this.tableSchemaNote = tableSchemaNote;
        this.partitionInfo = partitionInfo;
        this.explainText = explainText;
        this.errorText = errorText;
        this.optimizationGoal = optimizationGoal;
        this.problemSummary = problemSummary;
        this.optimizedSql = optimizedSql;
        this.optimizationPoints = optimizationPoints;
        this.riskNotes = riskNotes;
        this.validationSteps = validationSteps;
        this.ruleFindings = ruleFindings;
        this.llmModel = llmModel;
        this.analysisSource = analysisSource;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public static SqlOptimizationResult fromEntity(SqlOptimizationEntity entity) {
        return new SqlOptimizationResult(
            entity.getId(),
            entity.getEngineType(),
            entity.getOriginalSql(),
            entity.getTableSchemaNote(),
            entity.getPartitionInfo(),
            entity.getExplainText(),
            entity.getErrorText(),
            entity.getOptimizationGoal(),
            entity.getProblemSummary(),
            entity.getOptimizedSql(),
            splitLines(entity.getOptimizationPoints()),
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

    public Long getId() { return id; }
    public String getEngineType() { return engineType; }
    public String getOriginalSql() { return originalSql; }
    public String getTableSchemaNote() { return tableSchemaNote; }
    public String getPartitionInfo() { return partitionInfo; }
    public String getExplainText() { return explainText; }
    public String getErrorText() { return errorText; }
    public String getOptimizationGoal() { return optimizationGoal; }
    public String getProblemSummary() { return problemSummary; }
    public String getOptimizedSql() { return optimizedSql; }
    public List<String> getOptimizationPoints() { return optimizationPoints; }
    public List<String> getRiskNotes() { return riskNotes; }
    public List<String> getValidationSteps() { return validationSteps; }
    public List<String> getRuleFindings() { return ruleFindings; }
    public String getLlmModel() { return llmModel; }
    public String getAnalysisSource() { return analysisSource; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
