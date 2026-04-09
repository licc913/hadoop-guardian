package com.guardian.hadoop.integration.cm;

import com.guardian.hadoop.config.ClouderaManagerProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClouderaManagerSettingsService {

    private static final long SETTINGS_ID = 1L;

    private final ClouderaManagerSettingsRepository repository;
    private final ClouderaManagerProperties defaults;

    public ClouderaManagerSettingsService(ClouderaManagerSettingsRepository repository, ClouderaManagerProperties defaults) {
        this.repository = repository;
        this.defaults = defaults;
    }

    public ClouderaManagerSettingsResponse getSettings() {
        ClouderaManagerSettingsEntity entity = repository.findById(SETTINGS_ID).orElseGet(this::newDefaultEntity);
        return toResponse(entity);
    }

    @Transactional
    public ClouderaManagerSettingsResponse saveSettings(ClouderaManagerSettingsRequest request) {
        ClouderaManagerSettingsEntity entity = repository.findById(SETTINGS_ID).orElseGet(this::newDefaultEntity);
        entity.setEnabled(request.isEnabled());
        entity.setBaseUrl(request.getBaseUrl());
        entity.setApiVersion(request.getApiVersion());
        entity.setUsername(request.getUsername());
        if (hasText(request.getPassword())) {
            entity.setPassword(request.getPassword());
        }
        entity.setClusterName(request.getClusterName());
        repository.save(entity);
        return toResponse(entity);
    }

    public ClouderaManagerSettingsEntity getEffectiveSettings() {
        return repository.findById(SETTINGS_ID).orElseGet(this::newDefaultEntity);
    }

    private ClouderaManagerSettingsEntity newDefaultEntity() {
        ClouderaManagerSettingsEntity entity = new ClouderaManagerSettingsEntity();
        entity.setId(SETTINGS_ID);
        entity.setEnabled(defaults.isEnabled());
        entity.setBaseUrl(defaults.getBaseUrl());
        entity.setApiVersion(defaults.getApiVersion());
        entity.setUsername(defaults.getUsername());
        entity.setPassword(defaults.getPassword());
        entity.setClusterName(defaults.getClusterName());
        return entity;
    }

    private ClouderaManagerSettingsResponse toResponse(ClouderaManagerSettingsEntity entity) {
        return new ClouderaManagerSettingsResponse(
            entity.isEnabled(),
            entity.getBaseUrl(),
            entity.getApiVersion(),
            entity.getUsername(),
            "",
            hasText(entity.getPassword()),
            entity.getClusterName(),
            isConfigured(entity)
        );
    }

    private boolean isConfigured(ClouderaManagerSettingsEntity entity) {
        return hasText(entity.getBaseUrl())
            && hasText(entity.getApiVersion())
            && hasText(entity.getUsername())
            && hasText(entity.getPassword())
            && hasText(entity.getClusterName());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
