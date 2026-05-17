package com.guardian.hadoop.integration.cm;

import javax.validation.Valid;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/cloudera-manager")
public class ClouderaManagerSettingsController {

    private final ClouderaManagerSettingsService settingsService;
    private final ClouderaManagerCurrentStatusService currentStatusService;
    private final CmServiceLogSnapshotService logSnapshotService;
    private final CmServiceLogCollectionScheduler logCollectionScheduler;

    public ClouderaManagerSettingsController(ClouderaManagerSettingsService settingsService,
                                             ClouderaManagerCurrentStatusService currentStatusService,
                                             CmServiceLogSnapshotService logSnapshotService,
                                             CmServiceLogCollectionScheduler logCollectionScheduler) {
        this.settingsService = settingsService;
        this.currentStatusService = currentStatusService;
        this.logSnapshotService = logSnapshotService;
        this.logCollectionScheduler = logCollectionScheduler;
    }

    @GetMapping("/settings")
    public ClouderaManagerSettingsResponse getSettings() {
        return settingsService.getSettings();
    }

    @PutMapping("/settings")
    public ClouderaManagerSettingsResponse saveSettings(@Valid @RequestBody ClouderaManagerSettingsRequest request) {
        return settingsService.saveSettings(request);
    }

    @PostMapping("/current-status")
    public CmCurrentStatusResponse getCurrentStatus() {
        try {
            CmCurrentStatusResponse response = currentStatusService.fetchCurrentStatus();
            if (response.isEnabled()) {
                logCollectionScheduler.triggerCollectionAsync();
            }
            return response;
        } catch (Exception exception) {
            return new CmCurrentStatusResponse(
                false,
                true,
                "Cloudera Manager 当前状态采集失败。",
                exception.getClass().getSimpleName() + ": " + defaultIfBlank(exception.getMessage(), "unknown error"),
                "",
                Instant.now(),
                0,
                0,
                Collections.<CmServiceStatusRecord>emptyList(),
                Collections.<CmServiceLogSnapshotRecord>emptyList()
            );
        }
    }

    @GetMapping("/current-logs")
    public List<CmServiceLogSnapshotRecord> getCurrentLogs() {
        return logSnapshotService.getLatestLogs();
    }

    @GetMapping("/log-collection-status")
    public CmLogCollectionStatusResponse getLogCollectionStatus() {
        return logCollectionScheduler.getStatus();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
