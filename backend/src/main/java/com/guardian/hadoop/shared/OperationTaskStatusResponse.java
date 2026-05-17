package com.guardian.hadoop.shared;

import java.time.Instant;
import java.util.List;

public class OperationTaskStatusResponse {

    private final Instant generatedAt;
    private final int runningCount;
    private final int failedCount;
    private final int recentCount;
    private final List<OperationTaskStatusItem> recentTasks;

    public OperationTaskStatusResponse(Instant generatedAt,
                                       int runningCount,
                                       int failedCount,
                                       int recentCount,
                                       List<OperationTaskStatusItem> recentTasks) {
        this.generatedAt = generatedAt;
        this.runningCount = runningCount;
        this.failedCount = failedCount;
        this.recentCount = recentCount;
        this.recentTasks = recentTasks;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public int getRunningCount() {
        return runningCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public int getRecentCount() {
        return recentCount;
    }

    public List<OperationTaskStatusItem> getRecentTasks() {
        return recentTasks;
    }
}
