package com.guardian.hadoop.integration.datasource;

import com.guardian.hadoop.integration.cm.ClouderaManagerSettingsEntity;
import com.guardian.hadoop.integration.cm.ClouderaManagerSettingsRequest;
import com.guardian.hadoop.integration.cm.ClouderaManagerSettingsResponse;
import com.guardian.hadoop.integration.cm.ClouderaManagerSettingsService;
import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsPayload;
import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsService;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataSourceConfigService {

    private static final long LOG_SOURCE_ID = 1L;

    private final ClouderaManagerSettingsService clouderaManagerSettingsService;
    private final LogSourceSettingsRepository logSourceSettingsRepository;
    private final DiagnosisLlmSettingsService diagnosisLlmSettingsService;
    private final JmxEndpointRepository jmxEndpointRepository;
    private final DiagnosticScriptRepository diagnosticScriptRepository;

    public DataSourceConfigService(ClouderaManagerSettingsService clouderaManagerSettingsService,
                                   LogSourceSettingsRepository logSourceSettingsRepository,
                                   DiagnosisLlmSettingsService diagnosisLlmSettingsService,
                                   JmxEndpointRepository jmxEndpointRepository,
                                   DiagnosticScriptRepository diagnosticScriptRepository) {
        this.clouderaManagerSettingsService = clouderaManagerSettingsService;
        this.logSourceSettingsRepository = logSourceSettingsRepository;
        this.diagnosisLlmSettingsService = diagnosisLlmSettingsService;
        this.jmxEndpointRepository = jmxEndpointRepository;
        this.diagnosticScriptRepository = diagnosticScriptRepository;
    }

    public DataSourceConfigResponse getConfig() {
        return new DataSourceConfigResponse(
            clouderaManagerSettingsService.getSettings(),
            getLogSourceSettings(),
            diagnosisLlmSettingsService.getSettings(),
            jmxEndpointRepository.findAllByOrderByServiceTypeAscRoleTypeAscTargetHostAsc().stream()
                .map(JmxEndpointPayload::fromEntity)
                .collect(Collectors.toList()),
            diagnosticScriptRepository.findAllByOrderByServiceScopeAscScriptNameAsc().stream()
                .map(DiagnosticScriptPayload::fromEntity)
                .collect(Collectors.toList())
        );
    }

    @Transactional
    public DataSourceConfigResponse saveConfig(DataSourceConfigRequest request) {
        ClouderaManagerSettingsRequest cmRequest = request.getClouderaManager();
        ClouderaManagerSettingsResponse cmResponse = cmRequest == null
            ? clouderaManagerSettingsService.getSettings()
            : clouderaManagerSettingsService.saveSettings(cmRequest);

        LogSourceSettingsPayload logSource = saveLogSource(request.getLogSource());
        DiagnosisLlmSettingsPayload llm = diagnosisLlmSettingsService.saveSettings(request.getLlm());
        saveJmxEndpoints(request.getJmxEndpoints());
        saveDiagnosticScripts(request.getDiagnosticScripts());

        return new DataSourceConfigResponse(
            cmResponse,
            logSource,
            llm,
            jmxEndpointRepository.findAllByOrderByServiceTypeAscRoleTypeAscTargetHostAsc().stream()
                .map(JmxEndpointPayload::fromEntity)
                .collect(Collectors.toList()),
            diagnosticScriptRepository.findAllByOrderByServiceScopeAscScriptNameAsc().stream()
                .map(DiagnosticScriptPayload::fromEntity)
                .collect(Collectors.toList())
        );
    }

    private LogSourceSettingsPayload getLogSourceSettings() {
        LogSourceSettingsEntity entity = logSourceSettingsRepository.findById(LOG_SOURCE_ID).orElseGet(this::newLogSourceEntity);
        return LogSourceSettingsPayload.fromEntity(entity);
    }

    private LogSourceSettingsPayload saveLogSource(LogSourceSettingsPayload payload) {
        LogSourceSettingsEntity entity = logSourceSettingsRepository.findById(LOG_SOURCE_ID).orElseGet(this::newLogSourceEntity);
        if (payload != null) {
            entity.setEnabled(payload.isEnabled());
            entity.setProviderType(payload.getProviderType());
            entity.setBaseUrl(payload.getBaseUrl());
            entity.setAuthType(payload.getAuthType());
            if (hasText(payload.getAuthToken())) {
                entity.setAuthToken(payload.getAuthToken());
            }
            entity.setIndexPattern(payload.getIndexPattern());
            entity.setDefaultTimeWindowMinutes(payload.getDefaultTimeWindowMinutes());
        }
        logSourceSettingsRepository.save(entity);
        return LogSourceSettingsPayload.fromEntity(entity);
    }

    private void saveJmxEndpoints(List<JmxEndpointPayload> payloads) {
        if (payloads == null) {
            return;
        }
        Map<Long, JmxEndpointEntity> existingById = jmxEndpointRepository.findAll().stream()
            .collect(Collectors.toMap(JmxEndpointEntity::getId, entity -> entity));
        Set<Long> retainedIds = new HashSet<Long>();
        for (JmxEndpointPayload payload : payloads) {
            JmxEndpointEntity entity = payload.getId() == null ? null : existingById.get(payload.getId());
            if (entity == null) {
                entity = new JmxEndpointEntity();
            } else {
                retainedIds.add(entity.getId());
            }
            entity.setEnabled(payload.isEnabled());
            entity.setServiceType(payload.getServiceType());
            entity.setRoleType(payload.getRoleType());
            entity.setTargetHost(payload.getTargetHost());
            entity.setPort(payload.getPort());
            entity.setPath(payload.getPath());
            entity.setProtocol(payload.getProtocol());
            entity.setAuthType(payload.getAuthType());
            entity.setUsername(payload.getUsername());
            if (hasText(payload.getPassword())) {
                entity.setPassword(payload.getPassword());
            }
            entity.setMetricWhitelist(payload.getMetricWhitelist());
            JmxEndpointEntity saved = jmxEndpointRepository.save(entity);
            retainedIds.add(saved.getId());
        }

        for (JmxEndpointEntity entity : existingById.values()) {
            if (!retainedIds.contains(entity.getId())) {
                jmxEndpointRepository.delete(entity);
            }
        }
    }

    private void saveDiagnosticScripts(List<DiagnosticScriptPayload> payloads) {
        diagnosticScriptRepository.deleteAll();
        if (payloads == null) {
            return;
        }
        for (DiagnosticScriptPayload payload : payloads) {
            DiagnosticScriptEntity entity = new DiagnosticScriptEntity();
            entity.setEnabled(payload.isEnabled());
            entity.setScriptName(payload.getScriptName());
            entity.setCommandPath(payload.getCommandPath());
            entity.setAllowedArgs(payload.getAllowedArgs());
            entity.setTimeoutSeconds(payload.getTimeoutSeconds());
            entity.setRequiresApproval(payload.isRequiresApproval());
            entity.setHostScope(payload.getHostScope());
            entity.setServiceScope(payload.getServiceScope());
            entity.setDescription(payload.getDescription());
            diagnosticScriptRepository.save(entity);
        }
    }

    private LogSourceSettingsEntity newLogSourceEntity() {
        LogSourceSettingsEntity entity = new LogSourceSettingsEntity();
        entity.setId(LOG_SOURCE_ID);
        entity.setEnabled(false);
        entity.setProviderType("ELASTICSEARCH");
        entity.setAuthType("BASIC");
        entity.setDefaultTimeWindowMinutes(30);
        entity.setIndexPattern("hadoop-*");
        return entity;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
