package com.guardian.hadoop.integration.cm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ClouderaManagerSyncResponse {

    private final boolean success;
    private final boolean enabled;
    private final int fetchedCount;
    private final int importedCount;
    private final int skippedCount;
    private final String message;
    private final String details;
    private final Instant validatedAt;
    private final String endpoint;
    private final List<String> importedIncidents;

    public ClouderaManagerSyncResponse(boolean success,
                                       boolean enabled,
                                       int fetchedCount,
                                       int importedCount,
                                       int skippedCount,
                                       String message,
                                       String details,
                                       Instant validatedAt,
                                       String endpoint,
                                       List<String> importedIncidents) {
        this.success = success;
        this.enabled = enabled;
        this.fetchedCount = fetchedCount;
        this.importedCount = importedCount;
        this.skippedCount = skippedCount;
        this.message = message;
        this.details = details;
        this.validatedAt = validatedAt;
        this.endpoint = endpoint;
        this.importedIncidents = importedIncidents == null ? new ArrayList<String>() : importedIncidents;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getFetchedCount() {
        return fetchedCount;
    }

    public int getImportedCount() {
        return importedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public List<String> getImportedIncidents() {
        return importedIncidents;
    }
}
