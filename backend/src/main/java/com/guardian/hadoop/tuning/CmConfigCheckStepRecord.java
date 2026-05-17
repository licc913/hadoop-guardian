package com.guardian.hadoop.tuning;

public class CmConfigCheckStepRecord {

    private final String step;
    private final boolean success;
    private final String endpoint;
    private final int itemCount;
    private final long durationMs;
    private final String message;

    public CmConfigCheckStepRecord(String step,
                                   boolean success,
                                   String endpoint,
                                   int itemCount,
                                   long durationMs,
                                   String message) {
        this.step = step;
        this.success = success;
        this.endpoint = endpoint;
        this.itemCount = itemCount;
        this.durationMs = durationMs;
        this.message = message;
    }

    public String getStep() {
        return step;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public int getItemCount() {
        return itemCount;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getMessage() {
        return message;
    }
}
