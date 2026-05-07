package com.guardian.hadoop.workflow;

public class ExecutionRecordCreateRequest {

    private Long actionRecommendationId;
    private String executionStatus;
    private String executor;
    private String executionSummary;

    public Long getActionRecommendationId() {
        return actionRecommendationId;
    }

    public void setActionRecommendationId(Long actionRecommendationId) {
        this.actionRecommendationId = actionRecommendationId;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public String getExecutionSummary() {
        return executionSummary;
    }

    public void setExecutionSummary(String executionSummary) {
        this.executionSummary = executionSummary;
    }
}
