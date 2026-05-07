package com.guardian.hadoop.incident;

import java.time.Instant;

public class IncidentGovernanceResponse {

    private final boolean success;
    private final String message;
    private final IncidentRecord incident;
    private final Instant effectiveAt;

    public IncidentGovernanceResponse(boolean success, String message, IncidentRecord incident, Instant effectiveAt) {
        this.success = success;
        this.message = message;
        this.incident = incident;
        this.effectiveAt = effectiveAt;
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

    public Instant getEffectiveAt() {
        return effectiveAt;
    }
}
