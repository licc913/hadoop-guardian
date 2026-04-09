package com.guardian.hadoop.integration.datasource;

import java.time.Instant;

public class IntegrationTestResponse {

    private final boolean success;
    private final boolean configured;
    private final String message;
    private final String details;
    private final String endpoint;
    private final Instant validatedAt;

    public IntegrationTestResponse(boolean success,
                                   boolean configured,
                                   String message,
                                   String details,
                                   String endpoint,
                                   Instant validatedAt) {
        this.success = success;
        this.configured = configured;
        this.message = message;
        this.details = details;
        this.endpoint = endpoint;
        this.validatedAt = validatedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isConfigured() {
        return configured;
    }

    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }
}
