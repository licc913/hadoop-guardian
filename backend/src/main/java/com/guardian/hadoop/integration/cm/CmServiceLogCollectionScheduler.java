package com.guardian.hadoop.integration.cm;

import com.guardian.hadoop.shared.PlatformRuntimeStatusService;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CmServiceLogCollectionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CmServiceLogCollectionScheduler.class);
    private final AtomicBoolean collecting = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastStartedAt = new AtomicReference<Instant>();
    private final AtomicReference<Instant> lastFinishedAt = new AtomicReference<Instant>();
    private final AtomicReference<String> lastMessage = new AtomicReference<String>("");
    private final AtomicBoolean lastSuccess = new AtomicBoolean(false);
    private final AtomicLong lastDurationMs = new AtomicLong(0L);
    private final AtomicLong lastRecentLogCount = new AtomicLong(0L);

    private final ClouderaManagerCurrentStatusService currentStatusService;
    private final ClouderaManagerSettingsService settingsService;
    private final PlatformRuntimeStatusService runtimeStatusService;
    private final long collectionFixedDelayMs;

    public CmServiceLogCollectionScheduler(ClouderaManagerCurrentStatusService currentStatusService,
                                           ClouderaManagerSettingsService settingsService,
                                           PlatformRuntimeStatusService runtimeStatusService,
                                           @Value("${guardian.cm-log.collection-fixed-delay-ms:60000}") long collectionFixedDelayMs) {
        this.currentStatusService = currentStatusService;
        this.settingsService = settingsService;
        this.runtimeStatusService = runtimeStatusService;
        this.collectionFixedDelayMs = collectionFixedDelayMs;
    }

    @Scheduled(
        initialDelayString = "${guardian.cm-log.collection-initial-delay-ms:10000}",
        fixedDelayString = "${guardian.cm-log.collection-fixed-delay-ms:60000}"
    )
    public void collectCurrentStatusLogs() {
        collectCurrentStatusLogsInternal(false);
    }

    public void triggerCollectionAsync() {
        CompletableFuture.runAsync(() -> collectCurrentStatusLogsInternal(true));
    }

    private void collectCurrentStatusLogsInternal(boolean manualTrigger) {
        if (!collecting.compareAndSet(false, true)) {
            logger.debug("Skip CM log collection because a previous collection is still running.");
            return;
        }
        Instant startedAt = Instant.now();
        lastStartedAt.set(startedAt);
        try {
            ClouderaManagerSettingsEntity settings = settingsService.getEffectiveSettings();
            if (settings == null || !settings.isEnabled() || !isConfigured(settings)) {
                logger.debug("Skip CM log collection because settings are disabled or incomplete.");
                markFinished(false, "CM settings disabled or incomplete", 0, startedAt);
                return;
            }
            CmCurrentStatusResponse response = currentStatusService.fetchCurrentStatusForCollection();
            if (response.isSuccess()) {
                int recentLogs = response.getRecentLogs() == null ? 0 : response.getRecentLogs().size();
                markFinished(true, response.getMessage(), recentLogs, startedAt);
                logger.info(
                    "CM log collection finished. trigger={}, services={}, unhealthyServices={}, recentLogs={}",
                    manualTrigger ? "manual" : "scheduled",
                    response.getServiceCount(),
                    response.getUnhealthyServiceCount(),
                    recentLogs
                );
            } else {
                markFinished(false, response.getDetails(), 0, startedAt);
                logger.warn("CM log collection failed: {}", response.getDetails());
            }
        } catch (Exception exception) {
            markFinished(
                false,
                exception.getClass().getSimpleName() + ": " + defaultIfBlank(exception.getMessage(), "unknown error"),
                0,
                startedAt
            );
            logger.warn("CM log collection failed.", exception);
        } finally {
            collecting.set(false);
        }
    }

    public CmLogCollectionStatusResponse getStatus() {
        return new CmLogCollectionStatusResponse(
            collecting.get(),
            lastSuccess.get(),
            lastMessage.get(),
            (int) lastRecentLogCount.get(),
            lastStartedAt.get(),
            lastFinishedAt.get(),
            lastDurationMs.get(),
            collectionFixedDelayMs
        );
    }

    private void markFinished(boolean success, String message, int recentLogCount, Instant startedAt) {
        Instant finishedAt = Instant.now();
        lastSuccess.set(success);
        lastMessage.set(defaultIfBlank(message, ""));
        lastRecentLogCount.set(recentLogCount);
        lastFinishedAt.set(finishedAt);
        lastDurationMs.set(Duration.between(startedAt, finishedAt).toMillis());
        runtimeStatusService.reportCmCollection(success, defaultIfBlank(message, ""), recentLogCount);
    }

    private boolean isConfigured(ClouderaManagerSettingsEntity settings) {
        return hasText(settings.getBaseUrl())
            && hasText(settings.getApiVersion())
            && hasText(settings.getUsername())
            && hasText(settings.getPassword())
            && hasText(settings.getClusterName());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
