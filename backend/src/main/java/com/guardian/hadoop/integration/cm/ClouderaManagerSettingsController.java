package com.guardian.hadoop.integration.cm;

import javax.validation.Valid;
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

    public ClouderaManagerSettingsController(ClouderaManagerSettingsService settingsService,
                                             ClouderaManagerCurrentStatusService currentStatusService) {
        this.settingsService = settingsService;
        this.currentStatusService = currentStatusService;
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
        return currentStatusService.fetchCurrentStatus();
    }
}
