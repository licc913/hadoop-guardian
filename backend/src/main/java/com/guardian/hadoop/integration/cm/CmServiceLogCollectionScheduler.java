package com.guardian.hadoop.integration.cm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CmServiceLogCollectionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CmServiceLogCollectionScheduler.class);

    private final ClouderaManagerCurrentStatusService currentStatusService;
    private final ClouderaManagerSettingsService settingsService;

    public CmServiceLogCollectionScheduler(ClouderaManagerCurrentStatusService currentStatusService,
                                           ClouderaManagerSettingsService settingsService) {
        this.currentStatusService = currentStatusService;
        this.settingsService = settingsService;
    }

    @Scheduled(
        initialDelayString = "${guardian.cm-log.collection-initial-delay-ms:10000}",
        fixedDelayString = "${guardian.cm-log.collection-fixed-delay-ms:60000}"
    )
    public void collectCurrentStatusLogs() {
        ClouderaManagerSettingsResponse settings = settingsService.getSettings();
        if (settings == null || !settings.isEnabled() || !settings.isConfigured()) {
            logger.debug("Skip CM log collection because settings are disabled or incomplete.");
            return;
        }
        CmCurrentStatusResponse response = currentStatusService.fetchCurrentStatus();
        if (response.isSuccess()) {
            logger.info(
                "CM log collection finished. services={}, unhealthyServices={}, recentLogs={}",
                response.getServiceCount(),
                response.getUnhealthyServiceCount(),
                response.getRecentLogs() == null ? 0 : response.getRecentLogs().size()
            );
        } else {
            logger.warn("CM log collection failed: {}", response.getDetails());
        }
    }
}
