package com.guardian.hadoop.integration.cm;

import com.guardian.hadoop.shared.PlatformRuntimeStatusService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CmServiceLogCollectionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CmServiceLogCollectionScheduler.class);
    private final AtomicBoolean collecting = new AtomicBoolean(false);

    private final ClouderaManagerCurrentStatusService currentStatusService;
    private final ClouderaManagerSettingsService settingsService;
    private final PlatformRuntimeStatusService runtimeStatusService;

    public CmServiceLogCollectionScheduler(ClouderaManagerCurrentStatusService currentStatusService,
                                           ClouderaManagerSettingsService settingsService,
                                           PlatformRuntimeStatusService runtimeStatusService) {
        this.currentStatusService = currentStatusService;
        this.settingsService = settingsService;
        this.runtimeStatusService = runtimeStatusService;
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
        try {
        ClouderaManagerSettingsEntity settings = settingsService.getEffectiveSettings();
        if (settings == null || !settings.isEnabled() || !isConfigured(settings)) {
            logger.debug("Skip CM log collection because settings are disabled or incomplete.");
            runtimeStatusService.reportCmCollection(false, "CM settings disabled or incomplete", 0);
            return;
        }
        CmCurrentStatusResponse response = currentStatusService.fetchCurrentStatusForCollection();
        if (response.isSuccess()) {
            int recentLogs = response.getRecentLogs() == null ? 0 : response.getRecentLogs().size();
            runtimeStatusService.reportCmCollection(true, response.getMessage(), recentLogs);
            logger.info(
                "CM log collection finished. trigger={}, services={}, unhealthyServices={}, recentLogs={}",
                manualTrigger ? "manual" : "scheduled",
                response.getServiceCount(),
                response.getUnhealthyServiceCount(),
                recentLogs
            );
        } else {
            runtimeStatusService.reportCmCollection(false, response.getDetails(), 0);
            logger.warn("CM log collection failed: {}", response.getDetails());
        }
        } finally {
            collecting.set(false);
        }
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
}
