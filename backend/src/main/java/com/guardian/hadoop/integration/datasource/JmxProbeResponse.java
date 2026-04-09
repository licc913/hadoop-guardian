package com.guardian.hadoop.integration.datasource;

import java.time.Instant;
import java.util.List;

public class JmxProbeResponse {

    private final int totalCount;
    private final int successCount;
    private final int failureCount;
    private final List<JmxProbeResult> results;
    private final Instant validatedAt;

    public JmxProbeResponse(int totalCount,
                            int successCount,
                            int failureCount,
                            List<JmxProbeResult> results,
                            Instant validatedAt) {
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.results = results;
        this.validatedAt = validatedAt;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public List<JmxProbeResult> getResults() {
        return results;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }
}
