package com.guardian.hadoop.integration.datasource;

import java.time.Instant;

public class LlmPromptResponse {

    private final boolean success;
    private final String message;
    private final String answer;
    private final String model;
    private final Instant respondedAt;

    public LlmPromptResponse(boolean success, String message, String answer, String model, Instant respondedAt) {
        this.success = success;
        this.message = message;
        this.answer = answer;
        this.model = model;
        this.respondedAt = respondedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getAnswer() {
        return answer;
    }

    public String getModel() {
        return model;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
