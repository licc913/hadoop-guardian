package com.guardian.hadoop.integration.cm;

import com.guardian.hadoop.incident.IncidentEntity;
import com.guardian.hadoop.incident.IncidentRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

@Service
public class ClouderaManagerSyncService {

    private static final long OPEN_DUPLICATE_WINDOW_HOURS = 12L;
    private static final long CLOSED_DUPLICATE_WINDOW_MINUTES = 30L;

    private final ClouderaManagerClient client;
    private final IncidentRepository incidentRepository;
    private final ClouderaManagerSettingsService settingsService;

    public ClouderaManagerSyncService(ClouderaManagerClient client,
                                      IncidentRepository incidentRepository,
                                      ClouderaManagerSettingsService settingsService) {
        this.client = client;
        this.incidentRepository = incidentRepository;
        this.settingsService = settingsService;
    }

    @Transactional
    public ClouderaManagerSyncResponse syncAlerts() {
        ClouderaManagerSettingsEntity settings = settingsService.getEffectiveSettings();
        String endpoint = client.buildAlertsEndpoint(settings);
        Instant now = Instant.now();

        if (!settings.isEnabled()) {
            return new ClouderaManagerSyncResponse(false, false, 0, 0, 0,
                "Cloudera Manager 告警同步未启用。",
                "请先在设置页启用并保存 Cloudera Manager 配置。",
                now, endpoint, new ArrayList<String>());
        }
        if (!settingsService.getSettings().isConfigured()) {
            return new ClouderaManagerSyncResponse(false, true, 0, 0, 0,
                "Cloudera Manager 接入信息不完整。",
                "请补齐基础地址、API 版本、用户名、密码和集群名称后再执行同步。",
                now, endpoint, new ArrayList<String>());
        }

        try {
            ApiAlertsResponse response = client.fetchAlerts(settings);
            List<ApiAlertItem> items = response.getItems() == null ? new ArrayList<ApiAlertItem>() : response.getItems();
            List<IncidentEntity> existingIncidents = incidentRepository.findAll();
            List<String> importedIncidents = new ArrayList<String>();
            int importedCount = 0;
            int skippedCount = 0;

            for (ApiAlertItem item : items) {
                if (!item.isAlert() || isBlank(item.getId())) {
                    skippedCount++;
                    continue;
                }

                Instant occurredAt = parseInstant(item.getEventTime());
                if (incidentRepository.findBySourceId(item.getId()).isPresent() || isDuplicateAlert(existingIncidents, item, occurredAt)) {
                    skippedCount++;
                    continue;
                }

                IncidentEntity incident = new IncidentEntity();
                incident.setIncidentNo("CM-" + item.getId());
                incident.setSourceType("CM_ALERT");
                incident.setSourceId(item.getId());
                incident.setClusterName(settings.getClusterName());
                incident.setServiceType(mapServiceType(item));
                incident.setSeverity(mapSeverity(item.getSeverity()));
                incident.setStatus("OPEN");
                incident.setTitle(buildTitle(item, incident.getServiceType()));
                incident.setSummary(buildSummary(item));
                incident.setImpactScope(buildImpactScope(item));
                incident.setOwner("cm-sync");
                incident.setOccurredAt(occurredAt);
                incident.setEvidence(buildEvidence(item, endpoint));
                incident.setAvoidedActions(Arrays.asList(
                    "在未确认根因前不要直接重启集群服务。",
                    "未审批前不要直接下发配置变更或扩大操作范围。"
                ));

                IncidentEntity saved = incidentRepository.save(incident);
                existingIncidents.add(saved);
                importedCount++;

                if (importedIncidents.size() < 5) {
                    importedIncidents.add(saved.getIncidentNo() + " | " + saved.getTitle());
                }
            }

            return new ClouderaManagerSyncResponse(
                true,
                true,
                items.size(),
                importedCount,
                skippedCount,
                importedCount > 0 ? "Cloudera Manager 告警同步完成。" : "Cloudera Manager 告警同步完成，本次没有新增事件。",
                String.format(Locale.ROOT,
                    "从 %s 拉取 %d 条告警，导入 %d 条，跳过 %d 条重复或无效告警。",
                    endpoint, items.size(), importedCount, skippedCount),
                Instant.now(),
                endpoint,
                importedIncidents
            );
        } catch (HttpStatusCodeException exception) {
            return new ClouderaManagerSyncResponse(
                false, true, 0, 0, 0,
                "Cloudera Manager 告警同步失败。",
                "HTTP " + exception.getRawStatusCode() + " " + exception.getStatusText(),
                Instant.now(), endpoint, new ArrayList<String>());
        } catch (RestClientException exception) {
            return new ClouderaManagerSyncResponse(
                false, true, 0, 0, 0,
                "Cloudera Manager 告警同步失败。",
                defaultIfBlank(exception.getMessage(), "无法连接或解析 Cloudera Manager 告警响应。"),
                Instant.now(), endpoint, new ArrayList<String>());
        }
    }

    private boolean isDuplicateAlert(List<IncidentEntity> existingIncidents, ApiAlertItem item, Instant occurredAt) {
        String incomingFingerprint = buildIncomingFingerprint(item);
        for (IncidentEntity existing : existingIncidents) {
            if (!"CM_ALERT".equalsIgnoreCase(existing.getSourceType())) {
                continue;
            }
            if (!incomingFingerprint.equals(buildExistingFingerprint(existing))) {
                continue;
            }

            long limitMinutes = "CLOSED".equalsIgnoreCase(existing.getStatus())
                ? CLOSED_DUPLICATE_WINDOW_MINUTES
                : Duration.ofHours(OPEN_DUPLICATE_WINDOW_HOURS).toMinutes();
            Duration delta = Duration.between(existing.getOccurredAt(), occurredAt).abs();
            if (delta.toMinutes() <= limitMinutes) {
                return true;
            }
        }
        return false;
    }

    private String buildExistingFingerprint(IncidentEntity incident) {
        return String.join("|",
            safe(incident.getClusterName()).toUpperCase(Locale.ROOT),
            safe(incident.getServiceType()).toUpperCase(Locale.ROOT),
            safe(incident.getTitle()).replaceAll("\\s+", " ").replaceAll("[0-9]+(?:\\.[0-9]+)?", "#").toUpperCase(Locale.ROOT),
            safe(incident.getSummary()).replaceAll("\\s+", " ").replaceAll("[0-9]+(?:\\.[0-9]+)?", "#").toUpperCase(Locale.ROOT)
        );
    }

    private String buildIncomingFingerprint(ApiAlertItem item) {
        String serviceType = mapServiceType(item);
        return String.join("|",
            safe(settingsService.getEffectiveSettings().getClusterName()).toUpperCase(Locale.ROOT),
            serviceType.toUpperCase(Locale.ROOT),
            buildTitle(item, serviceType).replaceAll("\\s+", " ").replaceAll("[0-9]+(?:\\.[0-9]+)?", "#").toUpperCase(Locale.ROOT),
            buildSummary(item).replaceAll("\\s+", " ").replaceAll("[0-9]+(?:\\.[0-9]+)?", "#").toUpperCase(Locale.ROOT)
        );
    }

    private String mapServiceType(ApiAlertItem item) {
        String serviceName = defaultIfBlank(item.getServiceName(), "").toUpperCase(Locale.ROOT);
        String category = defaultIfBlank(item.getCategory(), "").toUpperCase(Locale.ROOT);
        String content = defaultIfBlank(item.getContent(), "").toUpperCase(Locale.ROOT);
        String roleName = defaultIfBlank(item.getRoleName(), "").toUpperCase(Locale.ROOT);
        String entityName = defaultIfBlank(item.getEntityName(), "").toUpperCase(Locale.ROOT);
        String combined = serviceName + " " + category + " " + content + " " + roleName + " " + entityName;

        if (combined.contains("HDFS") || combined.contains("NAMENODE") || combined.contains("DATANODE")) {
            return "HDFS";
        }
        if (combined.contains("IMPALA") || combined.contains("CATALOGD") || combined.contains("STATESTORE")) {
            return "IMPALA";
        }
        if (combined.contains("YARN") || combined.contains("RESOURCEMANAGER") || combined.contains("NODEMANAGER")) {
            return "YARN";
        }
        if (combined.contains("HIVE") || combined.contains("TEZ") || combined.contains("HIVESERVER2") || combined.contains("METASTORE")) {
            return "HIVE_ON_TEZ";
        }
        return "CROSS_COMPONENT";
    }

    private String mapSeverity(String value) {
        if (isBlank(value)) {
            return "HIGH";
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        if (normalized.contains("CRITICAL") || normalized.contains("BAD")) {
            return "CRITICAL";
        }
        if (normalized.contains("WARN") || normalized.contains("CONCERN")) {
            return "MEDIUM";
        }
        if (normalized.contains("INFO") || normalized.contains("GOOD")) {
            return "LOW";
        }
        return "HIGH";
    }

    private String buildTitle(ApiAlertItem item, String serviceType) {
        String serviceLabel = firstNonBlank(item.getServiceName(), serviceType);
        String subject = firstNonBlank(item.getRoleName(), item.getEntityName(), item.getCategory(), "运行告警");
        return serviceLabel + " • " + subject;
    }

    private String buildSummary(ApiAlertItem item) {
        String content = defaultIfBlank(item.getContent(), "Cloudera Manager 返回的事件内容为空。");
        List<String> fragments = new ArrayList<String>();
        appendFragment(fragments, "主机", item.getHostName());
        appendFragment(fragments, "角色", item.getRoleName());
        appendFragment(fragments, "对象", item.getEntityName());
        if (fragments.isEmpty()) {
            return content;
        }
        return String.join("，", fragments) + " 出现异常：" + content;
    }

    private String buildImpactScope(ApiAlertItem item) {
        List<String> parts = new ArrayList<String>();
        appendFragment(parts, "服务", item.getServiceName());
        appendFragment(parts, "角色", item.getRoleName());
        appendFragment(parts, "主机", item.getHostName());
        appendFragment(parts, "对象", item.getEntityName());
        return parts.isEmpty() ? "待确认影响范围" : String.join(" | ", parts);
    }

    private List<String> buildEvidence(ApiAlertItem item, String endpoint) {
        List<String> evidence = new ArrayList<String>();
        evidence.add("来源：Cloudera Manager 历史告警流");
        evidence.add("接口：" + endpoint);
        appendEvidence(evidence, "类别", item.getCategory());
        appendEvidence(evidence, "严重级别", item.getSeverity());
        appendEvidence(evidence, "事件时间", item.getEventTime());
        appendEvidence(evidence, "服务", item.getServiceName());
        appendEvidence(evidence, "角色", item.getRoleName());
        appendEvidence(evidence, "主机", item.getHostName());
        appendEvidence(evidence, "对象", item.getEntityName());
        for (String highlight : item.getAttributeHighlights()) {
            evidence.add("属性：" + highlight);
        }
        evidence.add("原始内容：" + defaultIfBlank(item.getContent(), "无原始事件内容"));
        return evidence;
    }

    private Instant parseInstant(String value) {
        if (isBlank(value)) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return Instant.now();
        }
    }

    private void appendEvidence(List<String> evidence, String label, String value) {
        if (!isBlank(value)) {
            evidence.add(label + "：" + value.trim());
        }
    }

    private void appendFragment(List<String> fragments, String label, String value) {
        if (!isBlank(value)) {
            fragments.add(label + "：" + value.trim());
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
