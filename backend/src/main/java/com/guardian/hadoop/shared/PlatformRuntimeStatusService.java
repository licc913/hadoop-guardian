package com.guardian.hadoop.shared;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class PlatformRuntimeStatusService {

    private final AtomicReference<Instant> lastCmCollectionAt = new AtomicReference<Instant>();
    private final AtomicReference<Boolean> lastCmCollectionSuccess = new AtomicReference<Boolean>();
    private final AtomicReference<String> lastCmCollectionMessage = new AtomicReference<String>();
    private final AtomicInteger lastCmRecentLogCount = new AtomicInteger();
    private final AtomicReference<Instant> lastInspectionStartedAt = new AtomicReference<Instant>();
    private final AtomicReference<Instant> lastInspectionCompletedAt = new AtomicReference<Instant>();
    private final AtomicReference<String> lastInspectionStatus = new AtomicReference<String>();
    private final AtomicReference<String> lastInspectionMessage = new AtomicReference<String>();

    public void reportCmCollection(boolean success, String message, int recentLogCount) {
        lastCmCollectionAt.set(Instant.now());
        lastCmCollectionSuccess.set(Boolean.valueOf(success));
        lastCmCollectionMessage.set(message);
        lastCmRecentLogCount.set(recentLogCount);
    }

    public void markInspectionStarted(String message) {
        lastInspectionStartedAt.set(Instant.now());
        lastInspectionStatus.set("RUNNING");
        lastInspectionMessage.set(message);
    }

    public void markInspectionFinished(String status, String message) {
        lastInspectionCompletedAt.set(Instant.now());
        lastInspectionStatus.set(status);
        lastInspectionMessage.set(message);
    }

    public Instant getLastCmCollectionAt() {
        return lastCmCollectionAt.get();
    }

    public Boolean getLastCmCollectionSuccess() {
        return lastCmCollectionSuccess.get();
    }

    public String getLastCmCollectionMessage() {
        return lastCmCollectionMessage.get();
    }

    public int getLastCmRecentLogCount() {
        return lastCmRecentLogCount.get();
    }

    public Instant getLastInspectionStartedAt() {
        return lastInspectionStartedAt.get();
    }

    public Instant getLastInspectionCompletedAt() {
        return lastInspectionCompletedAt.get();
    }

    public String getLastInspectionStatus() {
        return lastInspectionStatus.get();
    }

    public String getLastInspectionMessage() {
        return lastInspectionMessage.get();
    }
}
