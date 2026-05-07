package com.guardian.hadoop.sql;

import java.util.List;

public class SqlOptimizationLlmOutcome {

    private final boolean success;
    private final String problemSummary;
    private final String optimizedSql;
    private final List<String> optimizationPoints;
    private final List<String> riskNotes;
    private final List<String> validationSteps;
    private final String llmModel;
    private final String analysisSource;

    public SqlOptimizationLlmOutcome(boolean success,
                                     String problemSummary,
                                     String optimizedSql,
                                     List<String> optimizationPoints,
                                     List<String> riskNotes,
                                     List<String> validationSteps,
                                     String llmModel,
                                     String analysisSource) {
        this.success = success;
        this.problemSummary = problemSummary;
        this.optimizedSql = optimizedSql;
        this.optimizationPoints = optimizationPoints;
        this.riskNotes = riskNotes;
        this.validationSteps = validationSteps;
        this.llmModel = llmModel;
        this.analysisSource = analysisSource;
    }

    public boolean isSuccess() { return success; }
    public String getProblemSummary() { return problemSummary; }
    public String getOptimizedSql() { return optimizedSql; }
    public List<String> getOptimizationPoints() { return optimizationPoints; }
    public List<String> getRiskNotes() { return riskNotes; }
    public List<String> getValidationSteps() { return validationSteps; }
    public String getLlmModel() { return llmModel; }
    public String getAnalysisSource() { return analysisSource; }
}
