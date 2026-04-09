package com.guardian.hadoop.integration.cm;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

@Service
public class ClouderaManagerCurrentStatusService {

    private final ClouderaManagerClient client;
    private final ClouderaManagerSettingsService settingsService;

    public ClouderaManagerCurrentStatusService(ClouderaManagerClient client,
                                               ClouderaManagerSettingsService settingsService) {
        this.client = client;
        this.settingsService = settingsService;
    }

    public CmCurrentStatusResponse fetchCurrentStatus() {
        ClouderaManagerSettingsEntity settings = settingsService.getEffectiveSettings();
        String endpoint = client.buildServicesEndpoint(settings);
        Instant now = Instant.now();

        if (!settings.isEnabled()) {
            return new CmCurrentStatusResponse(
                false,
                false,
                "Cloudera Manager 集成未启用。",
                "请先在设置页启用并保存 Cloudera Manager 配置。",
                endpoint,
                now,
                0,
                0,
                Collections.<CmServiceStatusRecord>emptyList()
            );
        }
        if (!settingsService.getSettings().isConfigured()) {
            return new CmCurrentStatusResponse(
                false,
                true,
                "Cloudera Manager 配置不完整。",
                "请补齐基础地址、API 版本、用户名、密码和集群名称后再采集当前状态。",
                endpoint,
                now,
                0,
                0,
                Collections.<CmServiceStatusRecord>emptyList()
            );
        }

        try {
            JsonNode response = client.fetchCurrentServices(settings);
            List<CmServiceStatusRecord> services = buildServiceStatuses(settings, response);
            int unhealthyCount = 0;
            for (CmServiceStatusRecord service : services) {
                if (!isHealthy(service.getHealthSummary(), service.getEntityStatus(), service.getUnhealthyRoleCount())) {
                    unhealthyCount++;
                }
            }

            services.sort(Comparator
                .comparingInt((CmServiceStatusRecord item) -> isHealthy(item.getHealthSummary(), item.getEntityStatus(), item.getUnhealthyRoleCount()) ? 1 : 0)
                .thenComparing(CmServiceStatusRecord::getServiceName));

            return new CmCurrentStatusResponse(
                true,
                true,
                unhealthyCount == 0 ? "当前服务快照采集成功，未发现异常服务。" : "当前服务快照采集成功，发现存在异常或待关注服务。",
                String.format(Locale.ROOT, "共采集 %d 个服务，其中 %d 个服务存在健康风险或角色异常。", services.size(), unhealthyCount),
                endpoint,
                Instant.now(),
                services.size(),
                unhealthyCount,
                services
            );
        } catch (HttpStatusCodeException exception) {
            return new CmCurrentStatusResponse(
                false,
                true,
                "Cloudera Manager 当前状态采集失败。",
                "HTTP " + exception.getRawStatusCode() + " " + exception.getStatusText(),
                endpoint,
                Instant.now(),
                0,
                0,
                Collections.<CmServiceStatusRecord>emptyList()
            );
        } catch (RestClientException exception) {
            return new CmCurrentStatusResponse(
                false,
                true,
                "Cloudera Manager 当前状态采集失败。",
                defaultIfBlank(exception.getMessage(), "无法连接或解析 Cloudera Manager 当前状态响应。"),
                endpoint,
                Instant.now(),
                0,
                0,
                Collections.<CmServiceStatusRecord>emptyList()
            );
        }
    }

    public CmServiceStatusRecord findServiceStatus(String serviceType) {
        if (isBlank(serviceType)) {
            return null;
        }
        CmCurrentStatusResponse response = fetchCurrentStatus();
        if (!response.isSuccess()) {
            return null;
        }
        for (CmServiceStatusRecord record : response.getServices()) {
            if (matchesServiceType(serviceType, record)) {
                return record;
            }
        }
        return null;
    }

    private List<CmServiceStatusRecord> buildServiceStatuses(ClouderaManagerSettingsEntity settings, JsonNode response) {
        List<CmServiceStatusRecord> services = new ArrayList<CmServiceStatusRecord>();
        JsonNode items = response == null ? null : response.get("items");
        if (items == null || !items.isArray()) {
            return services;
        }

        for (JsonNode service : items) {
            String serviceName = text(service, "name");
            String serviceType = text(service, "type");
            String serviceState = firstText(service, "serviceState", "serviceStatus", "entityStatus");
            String healthSummary = firstText(service, "healthSummary", "healthStatus");
            String entityStatus = firstText(service, "entityStatus", "commissionState");

            JsonNode roleResponse = null;
            try {
                if (!isBlank(serviceName)) {
                    roleResponse = client.fetchServiceRoles(settings, serviceName);
                }
            } catch (Exception ignored) {
                roleResponse = null;
            }

            RoleSnapshot snapshot = summarizeRoles(roleResponse);
            services.add(new CmServiceStatusRecord(
                serviceName,
                serviceType,
                serviceState,
                healthSummary,
                entityStatus,
                snapshot.roleCount,
                snapshot.unhealthyRoleCount,
                snapshot.highlights
            ));
        }
        return services;
    }

    private RoleSnapshot summarizeRoles(JsonNode response) {
        RoleSnapshot snapshot = new RoleSnapshot();
        JsonNode items = response == null ? null : response.get("items");
        if (items == null || !items.isArray()) {
            return snapshot;
        }

        for (JsonNode role : items) {
            snapshot.roleCount++;
            String roleName = text(role, "name");
            String roleType = text(role, "type");
            String healthSummary = firstText(role, "healthSummary", "healthStatus");
            String roleState = firstText(role, "roleState", "commissionState");
            if (isHealthy(healthSummary, roleState, 0)) {
                continue;
            }
            snapshot.unhealthyRoleCount++;
            if (snapshot.highlights.size() < 4) {
                snapshot.highlights.add(composeRoleHighlight(roleName, roleType, healthSummary, roleState));
            }
        }
        return snapshot;
    }

    private String composeRoleHighlight(String roleName, String roleType, String healthSummary, String roleState) {
        List<String> parts = new ArrayList<String>();
        if (!isBlank(roleType)) {
            parts.add(roleType);
        }
        if (!isBlank(roleName)) {
            parts.add(roleName);
        }
        if (!isBlank(healthSummary)) {
            parts.add("健康=" + healthSummary);
        }
        if (!isBlank(roleState)) {
            parts.add("状态=" + roleState);
        }
        return parts.isEmpty() ? "存在异常角色" : String.join(" | ", parts);
    }

    private boolean matchesServiceType(String requestedServiceType, CmServiceStatusRecord record) {
        String normalizedRequested = requestedServiceType.toUpperCase(Locale.ROOT);
        String normalizedServiceType = safe(record.getServiceType()).toUpperCase(Locale.ROOT);
        if (normalizedRequested.equals(normalizedServiceType)) {
            return true;
        }
        if ("HIVE_ON_TEZ".equals(normalizedRequested)) {
            return normalizedServiceType.contains("HIVE") || normalizedServiceType.contains("TEZ");
        }
        if ("IMPALA".equals(normalizedRequested)) {
            return normalizedServiceType.contains("IMPALA");
        }
        if ("YARN".equals(normalizedRequested)) {
            return normalizedServiceType.contains("YARN");
        }
        if ("HDFS".equals(normalizedRequested)) {
            return normalizedServiceType.contains("HDFS");
        }
        return false;
    }

    private boolean isHealthy(String healthSummary, String entityStatus, int unhealthyRoleCount) {
        String summary = safe(healthSummary).toUpperCase(Locale.ROOT);
        String state = safe(entityStatus).toUpperCase(Locale.ROOT);
        if (unhealthyRoleCount > 0) {
            return false;
        }
        if (summary.contains("BAD") || summary.contains("CONCERNING") || summary.contains("UNKNOWN")
            || state.contains("STOPPED") || state.contains("BAD")) {
            return false;
        }
        return true;
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("");
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class RoleSnapshot {
        private int roleCount;
        private int unhealthyRoleCount;
        private final List<String> highlights = new ArrayList<String>();
    }
}
