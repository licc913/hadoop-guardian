package com.guardian.hadoop.integration.llm;

import com.guardian.hadoop.config.DiagnosisLlmProperties;
import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosisLlmSettingsService {

    private static final long SETTINGS_ID = 1L;

    private final DiagnosisLlmSettingsRepository repository;
    private final DiagnosisLlmProperties defaults;
    private final JdbcTemplate jdbcTemplate;

    public DiagnosisLlmSettingsService(DiagnosisLlmSettingsRepository repository,
                                      DiagnosisLlmProperties defaults,
                                      JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.defaults = defaults;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTableExists() {
        jdbcTemplate.execute(
            "create table if not exists diagnosis_llm_settings ("
                + "id bigint not null,"
                + "enabled boolean not null,"
                + "endpoint varchar(512),"
                + "api_key varchar(512),"
                + "model varchar(128),"
                + "connect_timeout_ms integer,"
                + "read_timeout_ms integer,"
                + "temperature double precision not null,"
                + "max_tokens integer,"
                + "primary key (id)"
                + ")"
        );
    }

    public DiagnosisLlmSettingsPayload getSettings() {
        DiagnosisLlmSettingsEntity entity = repository.findById(SETTINGS_ID).orElseGet(this::newDefaultEntity);
        return DiagnosisLlmSettingsPayload.fromEntity(entity);
    }

    public DiagnosisLlmSettingsEntity getEffectiveSettings() {
        return repository.findById(SETTINGS_ID).orElseGet(this::newDefaultEntity);
    }

    @Transactional
    public DiagnosisLlmSettingsPayload saveSettings(DiagnosisLlmSettingsPayload payload) {
        DiagnosisLlmSettingsEntity entity = repository.findById(SETTINGS_ID).orElseGet(this::newDefaultEntity);
        if (payload != null) {
            entity.setEnabled(payload.isEnabled());
            entity.setEndpoint(payload.getEndpoint());
            if (hasText(payload.getApiKey())) {
                entity.setApiKey(payload.getApiKey());
            }
            entity.setModel(payload.getModel());
            entity.setConnectTimeoutMs(payload.getConnectTimeoutMs());
            entity.setReadTimeoutMs(payload.getReadTimeoutMs());
            entity.setTemperature(payload.getTemperature());
            entity.setMaxTokens(payload.getMaxTokens());
        }
        repository.save(entity);
        return DiagnosisLlmSettingsPayload.fromEntity(entity);
    }

    private DiagnosisLlmSettingsEntity newDefaultEntity() {
        DiagnosisLlmSettingsEntity entity = new DiagnosisLlmSettingsEntity();
        entity.setId(SETTINGS_ID);
        entity.setEnabled(defaults.isEnabled());
        entity.setEndpoint(defaults.getEndpoint());
        entity.setApiKey(defaults.getApiKey());
        entity.setModel(defaults.getModel());
        entity.setConnectTimeoutMs(defaults.getConnectTimeoutMs());
        entity.setReadTimeoutMs(defaults.getReadTimeoutMs());
        entity.setTemperature(defaults.getTemperature());
        entity.setMaxTokens(defaults.getMaxTokens());
        return entity;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
