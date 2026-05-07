package com.guardian.hadoop.integration.cm;

import com.fasterxml.jackson.databind.JsonNode;
import com.guardian.hadoop.incident.IncidentEntity;
import com.guardian.hadoop.incident.IncidentRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

@Service
public class ClouderaManagerCurrentStatusService {

    private static final Logger log = LoggerFactory.getLogger(ClouderaManagerCurrentStatusService.class);

    private static final int MAX_ROLE_HIGHLIGHTS = 6;
    private static final int MAX_LOG_HIGHLIGHTS = 6;
    private static final int MAX_LOG_PREVIEWS = 3;
    private static final int MAX_PREVIEW_SERVICES = 4;
    private static final int MAX_LOG_COLLECTION_SERVICES_PER_RUN = 2;
    private static final int MAX_SUCCESSFUL_ROLE_LOG_ATTEMPTS_PER_SERVICE = 3;
    private static final int MAX_TOTAL_ROLE_LOG_FETCHES_PER_SERVICE = 16;
    private static final int LOG_CONTEXT_RADIUS = 3;
    private static final int LOG_LINE_MAX_LENGTH = 240;
    private static final long REALTIME_SIGNAL_LOOKBACK_SECONDS = 15 * 60L;

    private final ClouderaManagerClient client;
    private final ClouderaManagerSettingsService settingsService;
    private final IncidentRepository incidentRepository;
    private final CmServiceLogSnapshotService logSnapshotService;
    private final int roleLogTimeoutMs;

    public ClouderaManagerCurrentStatusService(ClouderaManagerClient client,
                                               ClouderaManagerSettingsService settingsService,
                                               IncidentRepository incidentRepository,
                                               CmServiceLogSnapshotService logSnapshotService,
                                               @Value("${guardian.cm-log.role-log-timeout-ms:20000}") int roleLogTimeoutMs) {
        this.client = client;
        this.settingsService = settingsService;
        this.incidentRepository = incidentRepository;
        this.logSnapshotService = logSnapshotService;
        this.roleLogTimeoutMs = Math.max(5000, roleLogTimeoutMs);
    }

    public CmCurrentStatusResponse fetchCurrentStatus() {
        return fetchCurrentStatus(false);
    }

    public CmCurrentStatusResponse fetchCurrentStatusForCollection() {
        return fetchCurrentStatus(true);
    }

    private CmCurrentStatusResponse fetchCurrentStatus(boolean collectRoleLogs) {
        ClouderaManagerSettingsEntity settings = settingsService.getEffectiveSettings();
        String apiVersion = client.resolveApiVersion(settings);
        String resolvedCluster = client.resolveClusterPathName(settings, apiVersion);
        String endpoint = client.buildServicesEndpoint(settings, apiVersion, resolvedCluster);
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
                Collections.<CmServiceStatusRecord>emptyList(),
                logSnapshotService.getLatestLogs()
            );
        }

        if (!isConfigured(settings)) {
            return new CmCurrentStatusResponse(
                false,
                true,
                "Cloudera Manager 配置不完整。",
                "请补齐基础地址、API 版本、用户名、密码和集群名称后再采集当前状态。",
                endpoint,
                now,
                0,
                0,
                Collections.<CmServiceStatusRecord>emptyList(),
                logSnapshotService.getLatestLogs()
            );
        }

        try {
            JsonNode response = client.fetchCurrentServices(settings, apiVersion);
            ServiceBuildResult buildResult = buildServiceStatuses(settings, apiVersion, response, collectRoleLogs);
            List<CmServiceStatusRecord> services = buildResult.services;
            Collections.sort(
                services,
                Comparator
                    .comparingInt((CmServiceStatusRecord item) -> hasServiceLogs(item) ? 0 : 1)
                    .thenComparingInt((CmServiceStatusRecord item) -> isServiceHealthy(item) ? 1 : 0)
                    .thenComparing(CmServiceStatusRecord::getServiceType, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(CmServiceStatusRecord::getServiceName, String.CASE_INSENSITIVE_ORDER)
            );
            int unhealthyCount = countUnhealthyServices(services);
            if (collectRoleLogs) {
                try {
                    logSnapshotService.appendSnapshots(
                        defaultIfBlank(settings.getClusterName(), resolvedCluster),
                        services,
                        now
                    );
                } catch (Exception exception) {
                    log.error("Failed to persist CM service log snapshots", exception);
                    buildResult.collectionWarnings.add(
                        "???????????????: " + defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName())
                    );
                }
                try {
                    persistCurrentIncidents(settings, endpoint, services);
                } catch (Exception exception) {
                    log.error("Failed to persist current incidents snapshot", exception);
                    buildResult.collectionWarnings.add(
                        "???????????????: " + defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName())
                    );
                }
            }

            String message = unhealthyCount == 0
                ? "当前服务快照采集成功，未发现明显异常服务。"
                : "当前服务快照采集成功，已发现异常服务并更新诊断队列。";
            String details = "共采集 " + services.size() + " 个服务，其中 "
                + unhealthyCount + " 个服务存在健康风险或角色日志异常。";

            if (!buildResult.collectionWarnings.isEmpty()) {
                StringBuilder detailBuilder = new StringBuilder(details);
                detailBuilder.append("\n瑙掕壊/鏃ュ織閲囬泦璀﹀憡:");
                for (String warning : buildResult.collectionWarnings) {
                    detailBuilder.append("\n- ").append(warning);
                }
                details = detailBuilder.toString();
            }

            return new CmCurrentStatusResponse(
                true,
                true,
                message,
                details,
                endpoint,
                Instant.now(),
                services.size(),
                unhealthyCount,
                services,
                logSnapshotService.getLatestLogs()
            );
        } catch (HttpStatusCodeException exception) {
            return new CmCurrentStatusResponse(
                false,
                true,
                "Cloudera Manager 当前状态采集失败。",
                "HTTP " + exception.getRawStatusCode() + " " + exception.getStatusText()
                    + buildResponseBodyDetails(exception.getResponseBodyAsString()),
                endpoint,
                now,
                0,
                0,
                Collections.<CmServiceStatusRecord>emptyList(),
                logSnapshotService.getLatestLogs()
            );
        } catch (RestClientException exception) {
            return new CmCurrentStatusResponse(
                false,
                true,
                "Cloudera Manager 当前状态采集失败。",
                defaultIfBlank(exception.getMessage(), "无法连接或解析 Cloudera Manager 当前状态响应。"),
                endpoint,
                now,
                0,
                0,
                Collections.<CmServiceStatusRecord>emptyList(),
                logSnapshotService.getLatestLogs()
            );
        } catch (Exception exception) {
            return new CmCurrentStatusResponse(
                false,
                true,
                "Cloudera Manager 当前状态采集失败。",
                defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName()),
                endpoint,
                now,
                0,
                0,
                Collections.<CmServiceStatusRecord>emptyList(),
                logSnapshotService.getLatestLogs()
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

    private ServiceBuildResult collectCurrentStatusInBatches(ClouderaManagerSettingsEntity settings,
                                                             String apiVersion,
                                                             String resolvedCluster,
                                                             String endpoint,
                                                             JsonNode response,
                                                             Instant now) {
        ServiceBuildResult baseResult = buildServiceStatuses(settings, apiVersion, response, false);
        List<CmServiceStatusRecord> baseServices = baseResult.services;
        List<String> collectionWarnings = new ArrayList<String>(baseResult.collectionWarnings);

        try {
            persistCurrentIncidents(settings, endpoint, baseServices);
        } catch (Exception exception) {
            log.error("Failed to persist fast current incidents snapshot", exception);
            collectionWarnings.add(
                "实时事件入队失败: " + defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName())
            );
        }

        List<CmServiceStatusRecord> logCollectedServices = collectServiceLogBatch(
            settings,
            apiVersion,
            baseServices,
            collectionWarnings
        );
        if (!logCollectedServices.isEmpty()) {
            try {
                logSnapshotService.appendSnapshots(
                    defaultIfBlank(settings.getClusterName(), resolvedCluster),
                    logCollectedServices,
                    now
                );
            } catch (Exception exception) {
                log.error("Failed to persist CM service log snapshots", exception);
                collectionWarnings.add(
                    "服务日志快照写库失败: " + defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName())
                );
            }
            try {
                persistCurrentIncidents(settings, endpoint, logCollectedServices);
            } catch (Exception exception) {
                log.error("Failed to enrich current incidents with collected service logs", exception);
                collectionWarnings.add(
                    "实时事件日志补充失败: " + defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName())
                );
            }
        }

        return new ServiceBuildResult(baseServices, collectionWarnings);
    }

    private ServiceBuildResult buildServiceStatuses(ClouderaManagerSettingsEntity settings,
                                                    String apiVersion,
                                                    JsonNode response,
                                                    boolean collectRoleLogs) {
        List<ServiceCandidate> candidates = new ArrayList<ServiceCandidate>();
        List<String> collectionWarnings = new ArrayList<String>();
        JsonNode items = response == null ? null : response.get("items");
        if (items == null || !items.isArray()) {
            return new ServiceBuildResult(Collections.<CmServiceStatusRecord>emptyList(), collectionWarnings);
        }

        for (JsonNode service : items) {
            String serviceName = text(service, "name");
            String serviceType = text(service, "type");
            String serviceState = firstText(service, "serviceState", "serviceStatus", "entityStatus");
            String healthSummary = firstText(service, "healthSummary", "healthStatus");
            String entityStatus = firstText(service, "entityStatus", "commissionState");

            JsonNode roleResponse = null;
            try {
                if (collectRoleLogs && !isBlank(serviceName)) {
                    roleResponse = client.fetchServiceRoles(settings, serviceName, apiVersion);
                }
            } catch (Exception exception) {
                String warning = "鏈嶅姟 " + defaultIfBlank(serviceName, "UNKNOWN_SERVICE")
                    + " 瑙掕壊鍒楄〃璇诲彇澶辫触: "
                    + defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName());
                collectionWarnings.add(warning);
                log.warn("Failed to fetch CM roles for service {}", serviceName, exception);
                roleResponse = null;
            }

            RoleSnapshot baseSnapshot = summarizeRoles(settings, apiVersion, serviceName, roleResponse, false);
            candidates.add(new ServiceCandidate(serviceName, serviceType, serviceState, healthSummary, entityStatus, roleResponse, baseSnapshot));
        }

        Collections.sort(
            candidates,
            Comparator
                .comparingInt((ServiceCandidate item) -> isServiceHealthy(item.healthSummary, item.entityStatus, item.baseSnapshot.alertRoleCount) ? 1 : 0)
                .thenComparing(ServiceCandidate::getServiceType, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ServiceCandidate::getServiceName, String.CASE_INSENSITIVE_ORDER)
        );

        List<CmServiceStatusRecord> services = new ArrayList<CmServiceStatusRecord>();
        for (int index = 0; index < candidates.size(); index++) {
            ServiceCandidate candidate = candidates.get(index);
            boolean collectPreviewForService = collectRoleLogs && index < MAX_PREVIEW_SERVICES;
            boolean serviceNeedsLogs = !isServiceHealthy(candidate.healthSummary, candidate.entityStatus, candidate.baseSnapshot.alertRoleCount);
            RoleSnapshot snapshot = serviceNeedsLogs || collectPreviewForService
                ? summarizeRoles(settings, apiVersion, candidate.serviceName, candidate.roleResponse, collectPreviewForService)
                : candidate.baseSnapshot;
            services.add(candidate.toRecord(snapshot));
        }
        return new ServiceBuildResult(services, collectionWarnings);
    }

    private List<CmServiceStatusRecord> collectServiceLogBatch(ClouderaManagerSettingsEntity settings,
                                                               String apiVersion,
                                                               List<CmServiceStatusRecord> baseServices,
                                                               List<String> collectionWarnings) {
        List<CmServiceStatusRecord> candidates = new ArrayList<CmServiceStatusRecord>();
        if (baseServices != null) {
            candidates.addAll(baseServices);
        }
        Collections.sort(
            candidates,
            Comparator
                .comparingInt((CmServiceStatusRecord item) -> isServiceHealthy(item) ? 1 : 0)
                .thenComparing(CmServiceStatusRecord::getServiceType, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(CmServiceStatusRecord::getServiceName, String.CASE_INSENSITIVE_ORDER)
        );

        List<CmServiceStatusRecord> collected = new ArrayList<CmServiceStatusRecord>();
        int processedServices = 0;
        for (CmServiceStatusRecord service : candidates) {
            if (service == null || isBlank(service.getServiceName())) {
                continue;
            }
            if (processedServices >= MAX_LOG_COLLECTION_SERVICES_PER_RUN) {
                break;
            }
            processedServices++;

            JsonNode roleResponse = null;
            try {
                roleResponse = client.fetchServiceRoles(settings, service.getServiceName(), apiVersion);
            } catch (Exception exception) {
                String warning = "服务 " + defaultIfBlank(service.getServiceName(), "UNKNOWN_SERVICE")
                    + " 角色列表读取失败: "
                    + defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName());
                collectionWarnings.add(warning);
                log.warn("Failed to fetch CM roles for service {}", service.getServiceName(), exception);
                continue;
            }

            RoleSnapshot snapshot = summarizeRoles(settings, apiVersion, service.getServiceName(), roleResponse, true);
            CmServiceStatusRecord enriched = new CmServiceStatusRecord(
                service.getServiceName(),
                service.getServiceType(),
                service.getServiceState(),
                service.getHealthSummary(),
                service.getEntityStatus(),
                snapshot.roleCount > 0 ? snapshot.roleCount : service.getRoleCount(),
                Math.max(service.getUnhealthyRoleCount(), snapshot.alertRoleCount),
                snapshot.roleHighlights.isEmpty() ? service.getRoleHighlights() : snapshot.roleHighlights,
                snapshot.logHighlights,
                snapshot.logPreviewLines
            );
            if (hasServiceLogs(enriched)) {
                collected.add(enriched);
            }
        }
        return collected;
    }

    private RoleSnapshot summarizeRoles(ClouderaManagerSettingsEntity settings,
                                        String apiVersion,
                                        String serviceName,
                                        JsonNode response,
                                        boolean collectPreviewForService) {
        RoleSnapshot snapshot = new RoleSnapshot();
        JsonNode items = response == null ? null : response.get("items");
        if (items == null || !items.isArray()) {
            return snapshot;
        }

        List<JsonNode> roles = new ArrayList<JsonNode>();
        for (JsonNode role : items) {
            roles.add(role);
        }
        Collections.sort(
            roles,
            Comparator
                .comparingInt((JsonNode role) -> roleLogPriority(serviceName, text(role, "type")))
                .thenComparing(role -> text(role, "type"), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(role -> text(role, "name"), String.CASE_INSENSITIVE_ORDER)
        );

        int successfulLogAttempts = 0;
        int totalLogFetches = 0;
        for (JsonNode role : roles) {
            snapshot.roleCount++;

            String roleName = text(role, "name");
            String roleType = text(role, "type");
            String hostName = text(role.get("hostRef"), "hostname");
            String healthSummary = firstText(role, "healthSummary", "healthStatus");
            String roleState = firstText(role, "roleState", "commissionState");

            boolean healthAbnormal = isRoleHealthAbnormal(healthSummary, roleState);
            boolean canFetchLogs = shouldFetchRoleLogs(serviceName, roleType);
            boolean collectPreview = collectPreviewForService
                && canFetchLogs
                && snapshot.logPreviewLines.isEmpty()
                && successfulLogAttempts < MAX_SUCCESSFUL_ROLE_LOG_ATTEMPTS_PER_SERVICE
                && totalLogFetches < MAX_TOTAL_ROLE_LOG_FETCHES_PER_SERVICE;
            boolean collectHighlight = healthAbnormal
                && canFetchLogs
                && snapshot.logHighlights.size() < MAX_LOG_HIGHLIGHTS
                && totalLogFetches < MAX_TOTAL_ROLE_LOG_FETCHES_PER_SERVICE;
            RoleLogEvidence evidence = (collectHighlight || collectPreview)
                ? fetchRoleLogEvidence(settings, apiVersion, serviceName, roleName, roleType, hostName)
                : RoleLogEvidence.empty();
            if (collectHighlight || collectPreview) {
                totalLogFetches++;
                if (!isBlank(evidence.preview) || !isBlank(evidence.highlight)) {
                    successfulLogAttempts++;
                }
            }
            boolean hasLogHighlight = !isBlank(evidence.highlight);
            if (!isBlank(evidence.error)) {
                snapshot.addRoleHighlight(evidence.error);
            }

            if (!healthAbnormal && !hasLogHighlight) {
                if (!isBlank(evidence.preview)) {
                    snapshot.addPreview(evidence.preview);
                }
                continue;
            }

            snapshot.alertRoleCount++;
            snapshot.addRoleHighlight(composeRoleHighlight(roleName, roleType, hostName, healthSummary, roleState));
            if (!isBlank(evidence.highlight)) {
                snapshot.addLogHighlight(evidence.highlight);
            }
            if (!isBlank(evidence.preview)) {
                snapshot.addPreview(evidence.preview);
            }
        }

        return snapshot;
    }

    private RoleLogEvidence fetchRoleLogEvidence(ClouderaManagerSettingsEntity settings,
                                                 String apiVersion,
                                                 String serviceName,
                                                 String roleName,
                                                 String roleType,
                                                 String hostName) {
        try {
            String logText = client.fetchRoleFullLog(
                settings,
                serviceName,
                roleName,
                apiVersion,
                Integer.valueOf(roleLogTimeoutMs)
            );
            if (isBlank(logText)) {
                return RoleLogEvidence.empty();
            }

            String[] rawLines = logText.split("\\r?\\n");
            List<String> lines = new ArrayList<String>();
            for (String rawLine : rawLines) {
                String normalized = normalizeLogLine(rawLine);
                if (!isBlank(normalized)) {
                    lines.add(normalized);
                }
            }
            if (lines.isEmpty()) {
                return RoleLogEvidence.empty();
            }

            String preview = composeLogPreview(roleType, roleName, hostName, lines.get(lines.size() - 1));
            for (int index = lines.size() - 1; index >= 0; index--) {
                String line = lines.get(index);
                String upper = line.toUpperCase(Locale.ROOT);
                if (!upper.contains("ERROR") && !upper.contains("WARN") && !upper.contains("WARNING")) {
                    continue;
                }
                int start = Math.max(0, index - LOG_CONTEXT_RADIUS);
                int end = Math.min(lines.size() - 1, index + LOG_CONTEXT_RADIUS);
                List<String> context = new ArrayList<String>();
                for (int lineIndex = start; lineIndex <= end; lineIndex++) {
                    context.add(lines.get(lineIndex));
                }
                return new RoleLogEvidence(composeLogHighlight(roleType, roleName, hostName, context), preview, null);
            }
            return new RoleLogEvidence(null, preview, null);
        } catch (HttpStatusCodeException exception) {
            log.warn(
                "Failed to fetch CM role log for service={}, role={} with status {} {}",
                serviceName,
                roleName,
                exception.getRawStatusCode(),
                exception.getStatusText()
            );
            return new RoleLogEvidence(
                null,
                null,
                roleLabel(roleType, roleName, hostName)
                    + " 日志读取失败: HTTP "
                    + exception.getRawStatusCode()
                    + " "
                    + exception.getStatusText()
            );
        } catch (Exception exception) {
            String message = defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName());
            if (message.length() > 120) {
                message = message.substring(0, 120);
            }
            log.warn("Failed to fetch CM role log for service={}, role={}: {}", serviceName, roleName, message, exception);
            return new RoleLogEvidence(
                null,
                null,
                defaultIfBlank(roleType, defaultIfBlank(roleName, "ROLE")) + " 日志读取失败: " + message
            );
        }
    }

    private void persistCurrentIncidents(ClouderaManagerSettingsEntity settings,
                                         String endpoint,
                                         List<CmServiceStatusRecord> services) {
        Instant now = Instant.now();
        Set<String> persistedSourceIds = new LinkedHashSet<String>();
        for (CmServiceStatusRecord service : services) {
            CmServiceStatusRecord incidentSignal = mergeRecentServiceLogs(
                settings,
                service,
                now.minusSeconds(REALTIME_SIGNAL_LOOKBACK_SECONDS)
            );
            if (!hasRealtimeIncidentSignal(incidentSignal)) {
                continue;
            }

            String sourceId = buildCurrentIncidentSourceId(settings, incidentSignal);
            upsertCurrentIncident(settings, endpoint, incidentSignal, now, sourceId);
            persistedSourceIds.add(sourceId);
        }
        supplementRealtimeIncidentsFromSnapshots(settings, endpoint, now, persistedSourceIds);
    }

    private void supplementRealtimeIncidentsFromSnapshots(ClouderaManagerSettingsEntity settings,
                                                          String endpoint,
                                                          Instant now,
                                                          Set<String> persistedSourceIds) {
        List<CmServiceLogSnapshotRecord> recentLogs = logSnapshotService.getRecentLogsForCluster(
            defaultIfBlank(settings.getClusterName(), "UNKNOWN_CLUSTER"),
            now.minusSeconds(REALTIME_SIGNAL_LOOKBACK_SECONDS),
            120
        );
        if (recentLogs.isEmpty()) {
            return;
        }

        Map<String, List<CmServiceLogSnapshotRecord>> grouped = new LinkedHashMap<String, List<CmServiceLogSnapshotRecord>>();
        for (CmServiceLogSnapshotRecord item : recentLogs) {
            if (item == null || isBlank(item.getServiceType())) {
                continue;
            }
            String key = defaultIfBlank(item.getServiceName(), item.getServiceType()) + "||" + item.getServiceType();
            List<CmServiceLogSnapshotRecord> bucket = grouped.get(key);
            if (bucket == null) {
                bucket = new ArrayList<CmServiceLogSnapshotRecord>();
                grouped.put(key, bucket);
            }
            bucket.add(item);
        }

        for (List<CmServiceLogSnapshotRecord> bucket : grouped.values()) {
            if (bucket == null || bucket.isEmpty()) {
                continue;
            }
            CmServiceLogSnapshotRecord first = bucket.get(0);
            CmServiceStatusRecord synthetic = buildSyntheticRealtimeSignal(first, bucket);
            String sourceId = buildCurrentIncidentSourceId(settings, synthetic);
            if (persistedSourceIds.contains(sourceId) || !hasRealtimeIncidentSignal(synthetic)) {
                continue;
            }
            upsertCurrentIncident(settings, endpoint, synthetic, now, sourceId);
        }
    }

    private CmServiceStatusRecord buildSyntheticRealtimeSignal(CmServiceLogSnapshotRecord first,
                                                               List<CmServiceLogSnapshotRecord> bucket) {
        List<String> highlights = new ArrayList<String>();
        List<String> previews = new ArrayList<String>();
        for (CmServiceLogSnapshotRecord item : bucket) {
            if (item == null || isBlank(item.getLogText())) {
                continue;
            }
            if ("WARN_ERROR".equalsIgnoreCase(item.getLogType())) {
                if (highlights.size() < MAX_LOG_HIGHLIGHTS) {
                    highlights.add(item.getLogText());
                }
            } else if (previews.size() < MAX_LOG_PREVIEWS) {
                previews.add(item.getLogText());
            }
        }
        return new CmServiceStatusRecord(
            defaultIfBlank(first.getServiceName(), first.getServiceType()),
            defaultIfBlank(first.getServiceType(), "UNKNOWN"),
            "UNKNOWN",
            "CONCERNING",
            "UNKNOWN",
            0,
            highlights.isEmpty() ? 0 : 1,
            Collections.<String>emptyList(),
            highlights,
            previews
        );
    }

    private void upsertCurrentIncident(ClouderaManagerSettingsEntity settings,
                                       String endpoint,
                                       CmServiceStatusRecord incidentSignal,
                                       Instant now,
                                       String sourceId) {
        IncidentEntity incident = incidentRepository
            .findBySourceTypeAndSourceId("CM_CURRENT", sourceId)
            .orElseGet(IncidentEntity::new);

        incident.setSourceType("CM_CURRENT");
        incident.setSourceId(sourceId);
        incident.setIncidentNo(buildCurrentIncidentNo(sourceId));
        incident.setClusterName(defaultIfBlank(settings.getClusterName(), "UNKNOWN_CLUSTER"));
        incident.setServiceType(defaultIfBlank(incidentSignal.getServiceType(), "CROSS_COMPONENT"));
        incident.setSeverity(mapSeverity(incidentSignal));
        if ("CLOSED".equalsIgnoreCase(incident.getStatus())) {
            incident.setStatus("OPEN");
            incident.setGovernanceStatus("ACTIVE");
            incident.setSuppressedUntil(null);
            incident.setGovernanceNote(buildRealtimeGovernanceNote("reopened after new realtime signal"));
        } else {
            incident.setStatus(defaultIfBlank(incident.getStatus(), "OPEN"));
        }
        incident.setGovernanceStatus(resolveGovernanceStatus(incident));
        incident.setEventFingerprint(sourceId);
        incident.setFirstSeenAt(incident.getFirstSeenAt() == null ? now : incident.getFirstSeenAt());
        incident.setLastSeenAt(now);
        incident.setOccurrenceCount(Math.max(defaultInteger(incident.getOccurrenceCount()), 0) + 1);
        incident.setTitle(truncate(buildCurrentIncidentTitle(incidentSignal), 240));
        incident.setSummary(resolveIncidentSummary(incident, incidentSignal));
        incident.setImpactScope(truncate(buildCurrentImpactScope(incidentSignal), 500));
        incident.setOwner(defaultIfBlank(incident.getOwner(), "cm-current"));
        incident.setOccurredAt(now);
        replaceListContents(incident.getEvidence(), mergeIncidentEvidence(incident, endpoint, incidentSignal));
        replaceListContents(incident.getAvoidedActions(), buildAvoidedActions());
        try {
            incidentRepository.save(incident);
            log.info(
                "Persisted CM current incident for service={} sourceId={} severity={} highlights={} previews={}",
                incidentSignal.getServiceName(),
                sourceId,
                incident.getSeverity(),
                safeSize(incidentSignal.getLogHighlights()),
                safeSize(incidentSignal.getLogPreviewLines())
            );
        } catch (Exception exception) {
            log.error(
                "Failed to persist CM current incident for service={} sourceId={}",
                incidentSignal.getServiceName(),
                sourceId,
                exception
            );
        }
    }

    private CmServiceStatusRecord mergeRecentServiceLogs(ClouderaManagerSettingsEntity settings,
                                                         CmServiceStatusRecord service,
                                                         Instant lookupStart) {
        if (service == null) {
            return null;
        }
        List<String> mergedHighlights = new ArrayList<String>(service.getLogHighlights());
        List<String> mergedPreviews = new ArrayList<String>(service.getLogPreviewLines());
        List<CmServiceLogSnapshotRecord> recentLogs = logSnapshotService.getRecentLogsForService(
            defaultIfBlank(settings.getClusterName(), "UNKNOWN_CLUSTER"),
            defaultIfBlank(service.getServiceName(), service.getServiceType()),
            service.getServiceType(),
            lookupStart,
            12
        );
        Set<String> highlightSet = new LinkedHashSet<String>(mergedHighlights);
        Set<String> previewSet = new LinkedHashSet<String>(mergedPreviews);
        for (CmServiceLogSnapshotRecord item : recentLogs) {
            if (item == null || isBlank(item.getLogText())) {
                continue;
            }
            if ("WARN_ERROR".equalsIgnoreCase(item.getLogType())) {
                if (highlightSet.add(item.getLogText())) {
                    mergedHighlights.add(item.getLogText());
                }
                continue;
            }
            if (previewSet.add(item.getLogText())) {
                mergedPreviews.add(item.getLogText());
            }
        }
        return new CmServiceStatusRecord(
            service.getServiceName(),
            service.getServiceType(),
            service.getServiceState(),
            service.getHealthSummary(),
            service.getEntityStatus(),
            service.getRoleCount(),
            service.getUnhealthyRoleCount(),
            new ArrayList<String>(service.getRoleHighlights()),
            mergedHighlights,
            mergedPreviews
        );
    }

    private String resolveIncidentSummary(IncidentEntity incident, CmServiceStatusRecord service) {
        return buildCurrentIncidentSummary(service);
    }

    private List<String> mergeIncidentEvidence(IncidentEntity incident, String endpoint, CmServiceStatusRecord service) {
        return buildCurrentEvidence(endpoint, service);
    }

    private String buildCurrentIncidentSourceId(ClouderaManagerSettingsEntity settings, CmServiceStatusRecord service) {
        return String.join("|",
            defaultIfBlank(settings.getClusterName(), "UNKNOWN_CLUSTER").toUpperCase(Locale.ROOT),
            defaultIfBlank(service.getServiceType(), "CROSS_COMPONENT").toUpperCase(Locale.ROOT),
            defaultIfBlank(service.getServiceName(), service.getServiceType()).toUpperCase(Locale.ROOT)
        );
    }

    private String buildCurrentIncidentNo(String sourceId) {
        return "RTC-" + fingerprint(sourceId).substring(0, 20).toUpperCase(Locale.ROOT);
    }

    private String fingerprint(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(defaultIfBlank(value, "").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", current));
            }
            return builder.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            return Integer.toHexString(Math.abs(defaultIfBlank(value, "").hashCode())).toUpperCase(Locale.ROOT);
        }
    }

    private String resolveGovernanceStatus(IncidentEntity incident) {
        if (incident != null && "SUPPRESSED".equalsIgnoreCase(incident.getGovernanceStatus())) {
            Instant suppressedUntil = incident.getSuppressedUntil();
            if (suppressedUntil != null && suppressedUntil.isAfter(Instant.now())) {
                return "SUPPRESSED";
            }
        }
        return "ACTIVE";
    }

    private String buildCurrentIncidentTitle(CmServiceStatusRecord service) {
        return defaultIfBlank(service.getServiceType(), "SERVICE").toLowerCase(Locale.ROOT)
            + " · "
            + defaultIfBlank(service.getServiceName(), service.getServiceType())
            + " 实时异常";
    }

    private String buildCurrentIncidentSummary(CmServiceStatusRecord service) {
        if (!service.getLogHighlights().isEmpty()) {
            return service.getLogHighlights().get(0);
        }
        if (!service.getLogPreviewLines().isEmpty()) {
            return service.getLogPreviewLines().get(0);
        }
        if (!service.getRoleHighlights().isEmpty()) {
            return service.getRoleHighlights().get(0);
        }
        return defaultIfBlank(service.getHealthSummary(), "服务处于异常状态。");
    }

    private String buildCurrentImpactScope(CmServiceStatusRecord service) {
        List<String> parts = new ArrayList<String>();
        parts.add("服务: " + defaultIfBlank(service.getServiceName(), service.getServiceType()));
        parts.add("类型: " + defaultIfBlank(service.getServiceType(), "UNKNOWN"));
        parts.add("异常角色: " + service.getUnhealthyRoleCount() + "/" + service.getRoleCount());
        return join(parts, " | ");
    }

    private String truncate(String value, int maxLength) {
        if (isBlank(value) || value.length() <= maxLength) {
            return defaultIfBlank(value, "");
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private List<String> buildCurrentEvidence(String endpoint, CmServiceStatusRecord service) {
        List<String> evidence = new ArrayList<String>();
        evidence.add("来源：Cloudera Manager 当前状态采集");
        evidence.add("接口：" + endpoint);
        evidence.add("服务：" + defaultIfBlank(service.getServiceName(), service.getServiceType()));
        evidence.add("健康：" + defaultIfBlank(service.getHealthSummary(), "UNKNOWN"));
        evidence.add("状态：" + defaultIfBlank(service.getServiceState(), service.getEntityStatus()));
        evidence.add("异常角色：" + service.getUnhealthyRoleCount() + "/" + service.getRoleCount());
        evidence.addAll(service.getRoleHighlights());
        evidence.addAll(service.getLogHighlights());
        evidence.addAll(service.getLogPreviewLines());
        return evidence;
    }

    private List<String> buildAvoidedActions() {
        List<String> actions = new ArrayList<String>();
        actions.add("未确认根因前不要直接重启整项服务。");
        actions.add("未审批前不要批量改配置或扩大影响范围。");
        return actions;
    }

    private String buildRealtimeGovernanceNote(String action) {
        return "cm-current @ " + Instant.now() + " - " + defaultIfBlank(action, "updated governance state");
    }

    private String mapSeverity(CmServiceStatusRecord service) {
        String health = safe(service.getHealthSummary()).toUpperCase(Locale.ROOT);
        if (health.contains("BAD")) {
            return "CRITICAL";
        }
        if (health.contains("CONCERN") || service.getUnhealthyRoleCount() > 0 || !service.getLogHighlights().isEmpty()) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private int countUnhealthyServices(List<CmServiceStatusRecord> services) {
        int count = 0;
        for (CmServiceStatusRecord service : services) {
            if (hasRealtimeIncidentSignal(service)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasServiceLogs(CmServiceStatusRecord service) {
        return service != null
            && ((!service.getLogHighlights().isEmpty()) || (!service.getLogPreviewLines().isEmpty()));
    }

    private boolean isServiceHealthy(CmServiceStatusRecord service) {
        return isServiceHealthy(service.getHealthSummary(), service.getEntityStatus(), service.getUnhealthyRoleCount());
    }

    private boolean isServiceHealthy(String healthSummary, String entityStatus, int unhealthyRoleCount) {
        return !isRoleHealthAbnormal(healthSummary, entityStatus)
            && unhealthyRoleCount <= 0;
    }

    private boolean hasRealtimeIncidentSignal(CmServiceStatusRecord service) {
        return service != null
            && (!isServiceHealthy(service) || !service.getLogHighlights().isEmpty());
    }

    private void replaceListContents(List<String> target, List<String> values) {
        if (target == null) {
            return;
        }
        target.clear();
        if (values != null && !values.isEmpty()) {
            target.addAll(values);
        }
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value.intValue();
    }

    private int safeSize(List<String> items) {
        return items == null ? 0 : items.size();
    }

    private boolean shouldFetchRoleLogs(String serviceName, String roleType) {
        String normalizedRoleType = safe(roleType).toUpperCase(Locale.ROOT);
        if (normalizedRoleType.isEmpty()) {
            return false;
        }
        if (normalizedRoleType.contains("GATEWAY")
            || normalizedRoleType.contains("CLIENT")
            || normalizedRoleType.contains("AUX")
            || normalizedRoleType.contains("HTTPFS")) {
            return false;
        }
        return true;
    }

    private int roleLogPriority(String serviceName, String roleType) {
        String normalizedService = safe(serviceName).toLowerCase(Locale.ROOT);
        String normalizedRole = safe(roleType).toUpperCase(Locale.ROOT);
        if (!shouldFetchRoleLogs(serviceName, roleType)) {
            return 100;
        }
        if ("yarn".equals(normalizedService)) {
            if (normalizedRole.contains("RESOURCEMANAGER")) {
                return 0;
            }
            if (normalizedRole.contains("JOBHISTORY")) {
                return 1;
            }
            if (normalizedRole.contains("TIMELINE")) {
                return 2;
            }
            if (normalizedRole.contains("NODEMANAGER")) {
                return 10;
            }
        }
        if ("hdfs".equals(normalizedService)) {
            if (normalizedRole.contains("NAMENODE")) {
                return 0;
            }
            if (normalizedRole.contains("JOURNALNODE")) {
                return 1;
            }
            if (normalizedRole.contains("FAILOVERCONTROLLER") || normalizedRole.contains("ZKFC")) {
                return 2;
            }
            if (normalizedRole.contains("SECONDARY")) {
                return 3;
            }
            if (normalizedRole.contains("DATANODE")) {
                return 10;
            }
        }
        if ("hive".equals(normalizedService)) {
            if (normalizedRole.contains("HIVEMETASTORE")) {
                return 0;
            }
            if (normalizedRole.contains("HIVESERVER2")) {
                return 1;
            }
            if (normalizedRole.contains("WEBHCAT")) {
                return 2;
            }
            if (normalizedRole.contains("TEZ")) {
                return 5;
            }
        }
        if ("impala".equals(normalizedService)) {
            if (normalizedRole.contains("CATALOG")) {
                return 0;
            }
            if (normalizedRole.contains("STATESTORE")) {
                return 1;
            }
            if (normalizedRole.contains("IMPALAD")) {
                return 5;
            }
        }
        if ("hbase".equals(normalizedService)) {
            if (normalizedRole.contains("MASTER")) {
                return 0;
            }
            if (normalizedRole.contains("REGIONSERVER")) {
                return 5;
            }
        }
        return 20;
    }

    private boolean isRoleHealthAbnormal(String healthSummary, String roleState) {
        String health = safe(healthSummary).toUpperCase(Locale.ROOT);
        String state = safe(roleState).toUpperCase(Locale.ROOT);
        return health.contains("BAD")
            || health.contains("CONCERN")
            || health.contains("UNKNOWN")
            || state.contains("STOPPED")
            || state.contains("BAD");
    }

    private String composeRoleHighlight(String roleName,
                                        String roleType,
                                        String hostName,
                                        String healthSummary,
                                        String roleState) {
        List<String> parts = new ArrayList<String>();
        if (!isBlank(roleType)) {
            parts.add(roleType);
        }
        if (!isBlank(roleName)) {
            parts.add(roleName);
        }
        if (!isBlank(hostName)) {
            parts.add("host=" + hostName);
        }
        if (!isBlank(healthSummary)) {
            parts.add("健康=" + healthSummary);
        }
        if (!isBlank(roleState)) {
            parts.add("状态=" + roleState);
        }
        return parts.isEmpty() ? "存在异常角色。" : join(parts, " | ");
    }

    private String composeLogPreview(String roleType, String roleName, String hostName, String line) {
        String roleLabel = roleLabel(roleType, roleName, hostName);
        return roleLabel + " 日志预览: " + truncate(line, LOG_LINE_MAX_LENGTH);
    }

    private String composeLogHighlight(String roleType, String roleName, String hostName, List<String> contextLines) {
        String roleLabel = roleLabel(roleType, roleName, hostName);
        StringBuilder builder = new StringBuilder();
        builder.append(roleLabel).append(" WARN/ERROR: ");
        for (int index = 0; index < contextLines.size(); index++) {
            if (index > 0) {
                builder.append(" || ");
            }
            builder.append(truncate(contextLines.get(index), LOG_LINE_MAX_LENGTH));
        }
        return builder.toString();
    }

    private String roleLabel(String roleType, String roleName, String hostName) {
        StringBuilder builder = new StringBuilder(defaultIfBlank(roleType, defaultIfBlank(roleName, "ROLE")));
        if (!isBlank(roleName) && !safe(roleName).equalsIgnoreCase(safe(roleType))) {
            builder.append(" ").append(roleName);
        }
        if (!isBlank(hostName)) {
            builder.append(" @").append(hostName);
        }
        return builder.toString();
    }

    private boolean matchesServiceType(String requestedServiceType, CmServiceStatusRecord record) {
        String normalizedRequested = safe(requestedServiceType).toUpperCase(Locale.ROOT);
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

    private String findPreservedLogLine(String summary, List<String> evidence) {
        if (looksLikeLogEvidence(summary)) {
            return summary;
        }
        if (evidence == null) {
            return null;
        }
        for (String item : evidence) {
            if (looksLikeLogEvidence(item)) {
                return item;
            }
        }
        return null;
    }

    private boolean looksLikeLogEvidence(String value) {
        String upper = safe(value).toUpperCase(Locale.ROOT);
        return upper.contains("日志")
            || upper.contains("WARN")
            || upper.contains("WARNING")
            || upper.contains("ERROR");
    }

    private String normalizeLogLine(String line) {
        return safe(line).replaceAll("\\s+", " ").trim();
    }

    private String join(List<String> values, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(delimiter);
            }
            builder.append(values.get(index));
        }
        return builder.toString();
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

    private String buildResponseBodyDetails(String responseBody) {
        String normalized = safe(responseBody).replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "";
        }
        return " | " + truncate(normalized, 240);
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isConfigured(ClouderaManagerSettingsEntity settings) {
        return settings != null
            && hasText(settings.getBaseUrl())
            && hasText(settings.getApiVersion())
            && hasText(settings.getUsername())
            && hasText(settings.getPassword())
            && hasText(settings.getClusterName());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class ServiceBuildResult {
        private final List<CmServiceStatusRecord> services;
        private final List<String> collectionWarnings;

        private ServiceBuildResult(List<CmServiceStatusRecord> services, List<String> collectionWarnings) {
            this.services = services == null ? Collections.<CmServiceStatusRecord>emptyList() : services;
            this.collectionWarnings = collectionWarnings == null ? Collections.<String>emptyList() : collectionWarnings;
        }
    }

    private static final class RoleSnapshot {
        private int roleCount;
        private int alertRoleCount;
        private final List<String> roleHighlights = new ArrayList<String>();
        private final List<String> logHighlights = new ArrayList<String>();
        private final List<String> logPreviewLines = new ArrayList<String>();
        private final Set<String> dedupe = new LinkedHashSet<String>();

        private void addRoleHighlight(String value) {
            if (roleHighlights.size() >= MAX_ROLE_HIGHLIGHTS || !dedupe.add("ROLE:" + value)) {
                return;
            }
            roleHighlights.add(value);
        }

        private void addLogHighlight(String value) {
            if (logHighlights.size() >= MAX_LOG_HIGHLIGHTS || !dedupe.add("LOG:" + value)) {
                return;
            }
            logHighlights.add(value);
        }

        private void addPreview(String value) {
            if (logPreviewLines.size() >= MAX_LOG_PREVIEWS || !dedupe.add("PREVIEW:" + value)) {
                return;
            }
            logPreviewLines.add(value);
        }
    }

    private static final class RoleLogEvidence {
        private final String highlight;
        private final String preview;
        private final String error;

        private RoleLogEvidence(String highlight, String preview, String error) {
            this.highlight = highlight;
            this.preview = preview;
            this.error = error;
        }

        private static RoleLogEvidence empty() {
            return new RoleLogEvidence(null, null, null);
        }
    }

    private static final class ServiceCandidate {
        private final String serviceName;
        private final String serviceType;
        private final String serviceState;
        private final String healthSummary;
        private final String entityStatus;
        private final JsonNode roleResponse;
        private final RoleSnapshot baseSnapshot;

        private ServiceCandidate(String serviceName,
                                 String serviceType,
                                 String serviceState,
                                 String healthSummary,
                                 String entityStatus,
                                 JsonNode roleResponse,
                                 RoleSnapshot baseSnapshot) {
            this.serviceName = serviceName;
            this.serviceType = serviceType;
            this.serviceState = serviceState;
            this.healthSummary = healthSummary;
            this.entityStatus = entityStatus;
            this.roleResponse = roleResponse;
            this.baseSnapshot = baseSnapshot;
        }

        private String getServiceName() {
            return serviceName == null ? "" : serviceName;
        }

        private String getServiceType() {
            return serviceType == null ? "" : serviceType;
        }

        private CmServiceStatusRecord toRecord(RoleSnapshot snapshot) {
            return new CmServiceStatusRecord(
                serviceName,
                serviceType,
                serviceState,
                healthSummary,
                entityStatus,
                snapshot.roleCount,
                snapshot.alertRoleCount,
                snapshot.roleHighlights,
                snapshot.logHighlights,
                snapshot.logPreviewLines
            );
        }
    }
}
