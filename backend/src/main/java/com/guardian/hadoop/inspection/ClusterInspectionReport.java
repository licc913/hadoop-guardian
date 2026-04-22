package com.guardian.hadoop.inspection;

import java.time.Instant;

public class ClusterInspectionReport {

    private final Long id;
    private final String clusterName;
    private final String reportTitle;
    private final String overallRisk;
    private final String status;
    private final String summary;
    private final String markdownContent;
    private final String generatedBy;
    private final String llmModel;
    private final Instant sourceCollectedAt;
    private final Instant completedAt;
    private final String errorMessage;
    private final Instant createdAt;

    public ClusterInspectionReport(Long id,
                                   String clusterName,
                                   String reportTitle,
                                   String overallRisk,
                                   String status,
                                   String summary,
                                   String markdownContent,
                                   String generatedBy,
                                   String llmModel,
                                   Instant sourceCollectedAt,
                                   Instant completedAt,
                                   String errorMessage,
                                   Instant createdAt) {
        this.id = id;
        this.clusterName = clusterName;
        this.reportTitle = reportTitle;
        this.overallRisk = overallRisk;
        this.status = status;
        this.summary = summary;
        this.markdownContent = markdownContent;
        this.generatedBy = generatedBy;
        this.llmModel = llmModel;
        this.sourceCollectedAt = sourceCollectedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
    }

    public static ClusterInspectionReport fromEntity(ClusterInspectionReportEntity entity) {
        return new ClusterInspectionReport(
            entity.getId(),
            entity.getClusterName(),
            entity.getReportTitle(),
            entity.getOverallRisk(),
            entity.getStatus(),
            entity.getSummary(),
            entity.getMarkdownContent(),
            entity.getGeneratedBy(),
            entity.getLlmModel(),
            entity.getSourceCollectedAt(),
            entity.getCompletedAt(),
            entity.getErrorMessage(),
            entity.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getReportTitle() {
        return reportTitle;
    }

    public String getOverallRisk() {
        return overallRisk;
    }

    public String getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public String getMarkdownContent() {
        return markdownContent;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public Instant getSourceCollectedAt() {
        return sourceCollectedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
