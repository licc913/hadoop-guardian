package com.guardian.hadoop.tuning;

import com.fasterxml.jackson.databind.JsonNode;
import com.guardian.hadoop.integration.cm.ClouderaManagerClient;
import com.guardian.hadoop.integration.cm.ClouderaManagerSettingsEntity;
import com.guardian.hadoop.integration.cm.ClouderaManagerSettingsService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class CmConfigCheckService {

    private final ClouderaManagerSettingsService settingsService;
    private final ClouderaManagerClient client;

    public CmConfigCheckService(ClouderaManagerSettingsService settingsService,
                                ClouderaManagerClient client) {
        this.settingsService = settingsService;
        this.client = client;
    }

    public CmConfigCheckResponse check(String serviceType) {
        String normalizedType = normalizeServiceType(serviceType);
        ClouderaManagerSettingsEntity settings = settingsService.getEffectiveSettings();
        List<CmConfigCheckStepRecord> steps = new ArrayList<CmConfigCheckStepRecord>();
        if (!settings.isEnabled()) {
            return new CmConfigCheckResponse(
                false,
                false,
                safe(settings.getClusterName()),
                normalizedType,
                "",
                "Cloudera Manager 未启用。",
                0,
                0,
                0,
                steps,
                Instant.now()
            );
        }

        String apiVersion = client.resolveApiVersion(settings);
        String clusterName = client.resolveClusterPathName(settings, apiVersion);
        JsonNode servicesNode = runJsonStep(
            steps,
            "读取集群服务列表",
            client.buildServicesEndpoint(settings, apiVersion, clusterName),
            () -> client.fetchCurrentServices(settings, apiVersion)
        );
        JsonNode matchedService = findService(servicesNode == null ? null : servicesNode.get("items"), normalizedType);
        if (matchedService == null) {
            return new CmConfigCheckResponse(
                true,
                false,
                clusterName,
                normalizedType,
                "",
                "服务列表读取成功，但没有找到目标组件服务。",
                0,
                0,
                0,
                steps,
                Instant.now()
            );
        }

        String serviceName = text(matchedService, "name");
        JsonNode serviceConfigNode = runJsonStep(
            steps,
            "读取服务级配置",
            client.buildServiceConfigEndpoint(settings, serviceName, apiVersion, clusterName),
            () -> client.fetchServiceConfig(settings, serviceName, apiVersion)
        );
        int serviceConfigCount = countItems(serviceConfigNode);

        JsonNode roleConfigGroupsNode = runJsonStep(
            steps,
            "读取角色配置组列表",
            client.buildRoleConfigGroupsEndpoint(settings, serviceName, apiVersion, clusterName),
            () -> client.fetchRoleConfigGroups(settings, serviceName, apiVersion)
        );
        JsonNode groups = roleConfigGroupsNode == null ? null : roleConfigGroupsNode.get("items");
        int groupCount = groups != null && groups.isArray() ? groups.size() : 0;
        int roleConfigCount = 0;
        if (groups != null && groups.isArray()) {
            for (JsonNode group : groups) {
                String groupName = firstNonBlank(text(group, "name"), text(group, "displayName"));
                if (!hasText(groupName)) {
                    continue;
                }
                JsonNode configNode = group.get("config");
                if (countItems(configNode) <= 0) {
                    configNode = runJsonStep(
                        steps,
                        "读取角色配置组配置: " + groupName,
                        client.buildRoleConfigGroupConfigEndpoint(settings, serviceName, groupName, apiVersion, clusterName),
                        () -> client.fetchRoleConfigGroupConfig(settings, serviceName, groupName, apiVersion)
                    );
                } else {
                    steps.add(new CmConfigCheckStepRecord(
                        "读取角色配置组配置: " + groupName,
                        true,
                        "roleConfigGroups?view=full 响应内已包含 config",
                        countItems(configNode),
                        0,
                        "已从角色配置组列表响应中读取配置项。"
                    ));
                }
                roleConfigCount += countItems(configNode);
            }
        }

        boolean success = steps.stream().allMatch(CmConfigCheckStepRecord::isSuccess)
            && serviceConfigCount + roleConfigCount > 0;
        String message = success
            ? "CM 配置采集链路正常。"
            : "CM 配置采集链路存在失败步骤，请查看下方明细。";
        return new CmConfigCheckResponse(
            true,
            success,
            clusterName,
            normalizedType,
            serviceName,
            message,
            serviceConfigCount,
            groupCount,
            roleConfigCount,
            steps,
            Instant.now()
        );
    }

    private JsonNode runJsonStep(List<CmConfigCheckStepRecord> steps,
                                 String stepName,
                                 String endpoint,
                                 JsonSupplier supplier) {
        long started = System.currentTimeMillis();
        try {
            JsonNode result = supplier.get();
            int itemCount = countItems(result);
            steps.add(new CmConfigCheckStepRecord(
                stepName,
                true,
                endpoint,
                itemCount,
                System.currentTimeMillis() - started,
                "成功，返回 " + itemCount + " 条。"
            ));
            return result;
        } catch (Exception exception) {
            steps.add(new CmConfigCheckStepRecord(
                stepName,
                false,
                endpoint,
                0,
                System.currentTimeMillis() - started,
                exception.getClass().getSimpleName() + ": " + defaultIfBlank(exception.getMessage(), "unknown error")
            ));
            return null;
        }
    }

    private JsonNode findService(JsonNode items, String serviceType) {
        if (items == null || !items.isArray()) {
            return null;
        }
        for (JsonNode item : items) {
            String type = normalizeServiceType(text(item, "type"));
            String name = normalizeServiceType(text(item, "name"));
            String displayName = normalizeServiceType(text(item, "displayName"));
            if (serviceType.equals(type) || serviceType.equals(name) || serviceType.equals(displayName)) {
                return item;
            }
        }
        return null;
    }

    private int countItems(JsonNode node) {
        JsonNode items = node == null ? null : node.get("items");
        if (items != null && items.isArray()) {
            return items.size();
        }
        return node == null || node.isNull() ? 0 : 1;
    }

    private String normalizeServiceType(String serviceType) {
        if (serviceType == null) {
            return "HDFS";
        }
        String normalized = serviceType.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("HIVE".equals(normalized) || "HIVE_ON_TEZ".equals(normalized) || "HIVEONTEZ".equals(normalized)) {
            return "HIVE_ON_TEZ";
        }
        return normalized;
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private interface JsonSupplier {
        JsonNode get();
    }
}
