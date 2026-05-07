package com.guardian.hadoop.sql;

public class SqlOptimizationRequest {

    private String engineType;
    private String originalSql;
    private String tableSchemaNote;
    private String partitionInfo;
    private String explainText;
    private String errorText;
    private String optimizationGoal;
    private String createdBy;

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
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
