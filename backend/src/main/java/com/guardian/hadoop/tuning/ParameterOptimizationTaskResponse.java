package com.guardian.hadoop.tuning;

import java.time.Instant;

public class ParameterOptimizationTaskResponse {

    private final String taskId;
    private final ParameterOptimizationTaskStatus status;
    private final String message;
    private final Long resultId;
    private final ParameterOptimizationResult result;
    private final String errorMessage;
    private final Instant createdAt;
    private final Instant updatedAt;

    public ParameterOptimizationTaskResponse(String taskId,
                                             ParameterOptimizationTaskStatus status,
                                             String message,
                                             Long resultId,
                                             ParameterOptimizationResult result,
                                             String errorMessage,
                                             Instant createdAt,
                                             Instant updatedAt) {
        this.taskId = taskId;
        this.status = status;
        this.message = message;
        this.resultId = resultId;
        this.result = result;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public ParameterOptimizationTaskStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Long getResultId() {
        return resultId;
    }

    public ParameterOptimizationResult getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
