package com.guardian.hadoop.integration.cm;

import java.time.Instant;

public class CmLogCollectionStatusResponse {

    private final boolean running;
    private final boolean lastSuccess;
    private final String lastMessage;
    private final int lastRecentLogCount;
    private final Instant lastStartedAt;
    private final Instant lastFinishedAt;
    private final long lastDurationMs;
    private final long collectionFixedDelayMs;

    public CmLogCollectionStatusResponse(boolean running,
                                         boolean lastSuccess,
                                         String lastMessage,
                                         int lastRecentLogCount,
                                         Instant lastStartedAt,
                                         Instant lastFinishedAt,
                                         long lastDurationMs,
                                         long collectionFixedDelayMs) {
        this.running = running;
        this.lastSuccess = lastSuccess;
        this.lastMessage = lastMessage;
        this.lastRecentLogCount = lastRecentLogCount;
        this.lastStartedAt = lastStartedAt;
        this.lastFinishedAt = lastFinishedAt;
        this.lastDurationMs = lastDurationMs;
        this.collectionFixedDelayMs = collectionFixedDelayMs;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isLastSuccess() {
        return lastSuccess;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public int getLastRecentLogCount() {
        return lastRecentLogCount;
    }

    public Instant getLastStartedAt() {
        return lastStartedAt;
    }

    public Instant getLastFinishedAt() {
        return lastFinishedAt;
    }

    public long getLastDurationMs() {
        return lastDurationMs;
    }

    public long getCollectionFixedDelayMs() {
        return collectionFixedDelayMs;
    }
}
