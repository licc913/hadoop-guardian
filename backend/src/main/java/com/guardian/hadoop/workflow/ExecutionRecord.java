package com.guardian.hadoop.workflow;

import java.time.Instant;

public class ExecutionRecord {

    private final Long id;
    private final Long incidentId;
    private final Long actionRecommendationId;
    private final String executionStatus;
    private final String executor;
    private final String executionSummary;
    private final Instant startedAt;
    private final Instant finishedAt;

    public ExecutionRecord(Long id, Long incidentId, Long actionRecommendationId, String executionStatus,
                           String executor, String executionSummary, Instant startedAt, Instant finishedAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.actionRecommendationId = actionRecommendationId;
        this.executionStatus = executionStatus;
        this.executor = executor;
        this.executionSummary = executionSummary;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public static ExecutionRecord fromEntity(ExecutionRecordEntity entity) {
        return new ExecutionRecord(
            entity.getId(),
            entity.getIncident().getId(),
            entity.getActionRecommendation().getId(),
            entity.getExecutionStatus(),
            entity.getExecutor(),
            entity.getExecutionSummary(),
            entity.getStartedAt(),
            entity.getFinishedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public Long getActionRecommendationId() {
        return actionRecommendationId;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public String getExecutor() {
        return executor;
    }

    public String getExecutionSummary() {
        return executionSummary;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
