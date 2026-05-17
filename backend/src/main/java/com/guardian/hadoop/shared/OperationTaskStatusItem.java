package com.guardian.hadoop.shared;

import java.time.Instant;

public class OperationTaskStatusItem {

    private final String taskType;
    private final String taskId;
    private final String status;
    private final String title;
    private final String message;
    private final Instant startedAt;
    private final Instant updatedAt;
    private final Long durationMs;

    public OperationTaskStatusItem(String taskType,
                                   String taskId,
                                   String status,
                                   String title,
                                   String message,
                                   Instant startedAt,
                                   Instant updatedAt,
                                   Long durationMs) {
        this.taskType = taskType;
        this.taskId = taskId;
        this.status = status;
        this.title = title;
        this.message = message;
        this.startedAt = startedAt;
        this.updatedAt = updatedAt;
        this.durationMs = durationMs;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }
}
