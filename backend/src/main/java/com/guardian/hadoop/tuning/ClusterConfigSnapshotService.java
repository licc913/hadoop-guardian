package com.guardian.hadoop.tuning;

import com.fasterxml.jackson.databind.JsonNode;
import com.guardian.hadoop.integration.cm.ClouderaManagerClient;
import com.guardian.hadoop.integration.cm.ClouderaManagerSettingsEntity;
import com.guardian.hadoop.integration.cm.ClouderaManagerSettingsService;
import com.guardian.hadoop.integration.cm.CmServiceLogSnapshotRecord;
import com.guardian.hadoop.integration.cm.CmServiceLogSnapshotService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ClusterConfigSnapshotService {

    private final ClouderaManagerSettingsService settingsService;
    private final ClouderaManagerClient client;
    private final CmServiceLogSnapshotService serviceLogSnapshotService;

    public ClusterConfigSnapshotService(ClouderaManagerSettingsService settingsService,
                                        ClouderaManagerClient client,
                                        CmServiceLogSnapshotService serviceLogSnapshotService) {
        this.settingsService = settingsService;
        this.client = client;
        this.serviceLogSnapshotService = serviceLogSnapshotService;
    }

    public ParameterOptimizationContextPreview collect(String serviceType) {
        String normalizedType = normalizeServiceType(serviceType);
        ClouderaManagerSettingsEntity settings = settingsService.getEffectiveSettings();
        if (!settings.isEnabled()) {
            return new ParameterOptimizationContextPreview(
                false,
                false,
                "Cloudera Manager 未启用，无法采集当前组件配置。",
                safe(settings.getClusterName()),
                "",
                normalizedType,
                "",
                "",
                "",
                Collections.<String, String>emptyMap(),
                Collections.<ParameterConfigEntryRecord>emptyList(),
                Collections.<String>emptyList()
            );
        }

        try {
            JsonNode servicesNode = client.fetchCurrentServices(settings);
            JsonNode services = servicesNode == null ? null : servicesNode.get("items");
            JsonNode matchedService = findService(services, normalizedType);
            if (matchedService == null) {
                return new ParameterOptimizationContextPreview(
                    true,
                    false,
                    "当前集群中未找到对应组件服务，请确认服务类型与 Cloudera Manager 中的 service name/type 是否一致。",
                    safe(settings.getClusterName()),
                    "",
                    normalizedType,
                    "",
                    "",
                    "",
                    Collections.<String, String>emptyMap(),
                    Collections.<ParameterConfigEntryRecord>emptyList(),
                    Collections.<String>emptyList()
                );
            }

            String serviceName = text(matchedService, "name");
            JsonNode serviceConfigNode = client.fetchServiceConfig(settings, serviceName);
            JsonNode roleConfigGroupsNode = client.fetchRoleConfigGroups(settings, serviceName);
            List<ParameterConfigEntryRecord> scopedConfigEntries =
                collectScopedConfigEntries(settings, serviceName, serviceConfigNode, roleConfigGroupsNode);
            Map<String, String> configEntries = buildEffectiveConfigMap(scopedConfigEntries);
            List<String> recentSignals = serviceLogSnapshotService
                .getLatestLogsForService(settings.getClusterName(), normalizedType, 8)
                .stream()
                .map(CmServiceLogSnapshotRecord::getLogText)
                .map(this::cleanSignal)
                .filter(this::hasText)
                .collect(Collectors.toList());

            String message = scopedConfigEntries.isEmpty()
                ? "已连接 Cloudera Manager，但当前服务没有返回可展示的非敏感配置参数。"
                : "已采集当前组件在 Cloudera Manager 中的服务级与角色配置组参数，以及最近服务日志信号。";

            return new ParameterOptimizationContextPreview(
                true,
                true,
                message,
                safe(settings.getClusterName()),
                serviceName,
                normalizedType,
                firstNonBlank(
                    text(matchedService, "version"),
                    text(matchedService, "serviceVersion"),
                    text(matchedService, "fullVersion"),
                    text(matchedService, "buildVersion"),
                    text(matchedService, "productVersion")
                ),
                text(matchedService, "serviceState"),
                text(matchedService, "healthSummary"),
                configEntries,
                scopedConfigEntries,
                recentSignals
            );
        } catch (Exception exception) {
            return new ParameterOptimizationContextPreview(
                true,
                false,
                "配置采集失败: " + defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName()),
                safe(settings.getClusterName()),
                "",
                normalizedType,
                "",
                "",
                "",
                Collections.<String, String>emptyMap(),
                Collections.<ParameterConfigEntryRecord>emptyList(),
                Collections.<String>emptyList()
            );
        }
    }

    private JsonNode findService(JsonNode items, String serviceType) {
        if (items == null || !items.isArray()) {
            return null;
        }
        String expected = normalizeServiceType(serviceType);
        for (JsonNode item : items) {
            String type = normalizeServiceType(text(item, "type"));
            String name = normalizeServiceType(text(item, "name"));
            if (expected.equals(type) || expected.equals(name)) {
                return item;
            }
        }
        return null;
    }

    private List<ParameterConfigEntryRecord> collectScopedConfigEntries(ClouderaManagerSettingsEntity settings,
                                                                        String serviceName,
                                                                        JsonNode serviceConfigNode,
                                                                        JsonNode roleConfigGroupsNode) {
        List<ParameterConfigEntryRecord> result = new ArrayList<ParameterConfigEntryRecord>();
        result.addAll(extractScopedEntries(serviceConfigNode, "SERVICE", serviceName, ""));

        JsonNode groups = roleConfigGroupsNode == null ? null : roleConfigGroupsNode.get("items");
        if (groups != null && groups.isArray()) {
            Set<String> seenGroups = new LinkedHashSet<String>();
            for (JsonNode groupNode : groups) {
                String groupName = firstNonBlank(text(groupNode, "name"), text(groupNode, "displayName"));
                if (!hasText(groupName) || !seenGroups.add(groupName)) {
                    continue;
                }
                String roleType = firstNonBlank(text(groupNode, "roleType"), text(groupNode, "base"));
                JsonNode groupConfigNode;
                try {
                    groupConfigNode = client.fetchRoleConfigGroupConfig(settings, serviceName, groupName);
                } catch (Exception exception) {
                    groupConfigNode = groupNode.get("config");
                }
                result.addAll(extractScopedEntries(groupConfigNode, "ROLE_CONFIG_GROUP", groupName, roleType));
            }
        }

        return result.stream()
            .sorted(Comparator
                .comparing(ParameterConfigEntryRecord::getScopeType, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ParameterConfigEntryRecord::getRoleType, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ParameterConfigEntryRecord::getScopeName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ParameterConfigEntryRecord::getConfigKey, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    }

    private List<ParameterConfigEntryRecord> extractScopedEntries(JsonNode configNode,
                                                                  String scopeType,
                                                                  String scopeName,
                                                                  String roleType) {
        if (configNode == null) {
            return Collections.emptyList();
        }
        JsonNode items = configNode.get("items");
        if (items == null || !items.isArray()) {
            return Collections.emptyList();
        }

        List<ParameterConfigEntryRecord> result = new ArrayList<ParameterConfigEntryRecord>();
        for (JsonNode item : items) {
            String key = firstNonBlank(text(item, "name"), text(item, "key"));
            if (!hasText(key) || isSensitiveKey(key)) {
                continue;
            }
            ConfigValueCandidate candidate = extractConfigValue(item);
            result.add(new ParameterConfigEntryRecord(
                scopeType,
                defaultIfBlank(scopeName, scopeType),
                defaultIfBlank(roleType, ""),
                key,
                hasText(candidate.value) ? candidate.value : "未显式设置",
                hasText(candidate.source) ? candidate.source : "UNSET"
            ));
        }
        return result;
    }

    private Map<String, String> buildEffectiveConfigMap(List<ParameterConfigEntryRecord> scopedEntries) {
        if (scopedEntries == null || scopedEntries.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        Map<String, String> sourceMap = new LinkedHashMap<String, String>();
        for (ParameterConfigEntryRecord entry : scopedEntries) {
            String key = entry.getConfigKey();
            if (!result.containsKey(key)) {
                result.put(key, entry.getConfigValue());
                sourceMap.put(key, entry.getValueSource());
                continue;
            }
            String currentSource = sourceMap.get(key);
            if (shouldReplace(currentSource, entry.getValueSource())) {
                result.put(key, entry.getConfigValue());
                sourceMap.put(key, entry.getValueSource());
            }
        }
        return result;
    }

    private boolean shouldReplace(String currentSource, String nextSource) {
        if (!hasText(currentSource)) {
            return true;
        }
        if (!hasText(nextSource)) {
            return false;
        }
        return "DEFAULT".equalsIgnoreCase(currentSource) && !"DEFAULT".equalsIgnoreCase(nextSource);
    }

    private ConfigValueCandidate extractConfigValue(JsonNode item) {
        String value = text(item, "value");
        if (hasText(value)) {
            return new ConfigValueCandidate(value, "EXPLICIT");
        }
        JsonNode valueNode = item.get("value");
        if (valueNode != null && !valueNode.isNull() && !valueNode.isMissingNode()) {
            if (valueNode.isArray()) {
                StringBuilder builder = new StringBuilder();
                for (JsonNode child : valueNode) {
                    if (child == null || child.isNull()) {
                        continue;
                    }
                    if (builder.length() > 0) {
                        builder.append(',');
                    }
                    builder.append(child.asText(""));
                }
                if (hasText(builder.toString())) {
                    return new ConfigValueCandidate(builder.toString(), "EXPLICIT");
                }
            } else {
                String rendered = valueNode.isValueNode() ? valueNode.asText("") : valueNode.toString();
                return new ConfigValueCandidate(rendered, "EXPLICIT");
            }
        }

        String fallback = firstNonBlank(
            text(item, "default"),
            text(item, "defaultValue"),
            text(item, "relatedValue"),
            text(item, "displayValue")
        );
        if (hasText(fallback)) {
            return new ConfigValueCandidate(fallback, "DEFAULT");
        }
        return new ConfigValueCandidate("", "");
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
            || normalized.contains("secret")
            || normalized.contains("token")
            || normalized.contains("credential")
            || normalized.contains("principal")
            || normalized.contains("keytab");
    }

    private String cleanSignal(String value) {
        return truncate(defaultIfBlank(value, "").replaceAll("\\s+", " ").trim(), 260);
    }

    private String normalizeServiceType(String serviceType) {
        if (serviceType == null) {
            return "HDFS";
        }
        String normalized = serviceType.trim().toUpperCase(Locale.ROOT);
        if ("HIVE".equals(normalized) || "HIVE_ON_TEZ".equals(normalized)) {
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String truncate(String value, int maxLength) {
        if (!hasText(value) || value.length() <= maxLength) {
            return defaultIfBlank(value, "");
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
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

    private static final class ConfigValueCandidate {
        private final String value;
        private final String source;

        private ConfigValueCandidate(String value, String source) {
            this.value = value;
            this.source = source;
        }
    }
}
