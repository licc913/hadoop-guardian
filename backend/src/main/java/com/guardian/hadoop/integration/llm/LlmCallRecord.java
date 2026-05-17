package com.guardian.hadoop.integration.llm;

import java.time.Instant;

public class LlmCallRecord {

    private final Long id;
    private final String feature;
    private final String model;
    private final String status;
    private final int promptChars;
    private final int responseChars;
    private final Long durationMs;
    private final String errorMessage;
    private final String promptPreview;
    private final Instant createdAt;
    private final Instant completedAt;

    public LlmCallRecord(Long id,
                         String feature,
                         String model,
                         String status,
                         int promptChars,
                         int responseChars,
                         Long durationMs,
                         String errorMessage,
                         String promptPreview,
                         Instant createdAt,
                         Instant completedAt) {
        this.id = id;
        this.feature = feature;
        this.model = model;
        this.status = status;
        this.promptChars = promptChars;
        this.responseChars = responseChars;
        this.durationMs = durationMs;
        this.errorMessage = errorMessage;
        this.promptPreview = promptPreview;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public Long getId() {
        return id;
    }

    public String getFeature() {
        return feature;
    }

    public String getModel() {
        return model;
    }

    public String getStatus() {
        return status;
    }

    public int getPromptChars() {
        return promptChars;
    }

    public int getResponseChars() {
        return responseChars;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getPromptPreview() {
        return promptPreview;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
