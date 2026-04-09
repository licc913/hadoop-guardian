package com.guardian.hadoop.incident;

import java.time.Instant;

public class IncidentCloseResponse {

    private final boolean success;
    private final String message;
    private final IncidentRecord incident;
    private final Instant closedAt;

    public IncidentCloseResponse(boolean success, String message, IncidentRecord incident, Instant closedAt) {
        this.success = success;
        this.message = message;
        this.incident = incident;
        this.closedAt = closedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public IncidentRecord getIncident() {
        return incident;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
