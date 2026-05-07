package com.guardian.hadoop.integration.cm;

import com.guardian.hadoop.incident.IncidentEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CmServiceLogSnapshotService {
    private static final Pattern SIGNAL_TOKEN_PATTERN = Pattern.compile("[A-Z][A-Z0-9_-]{3,}");

    private final CmServiceLogSnapshotRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final long retentionHours;
    private final long dedupeWindowMinutes;

    public CmServiceLogSnapshotService(CmServiceLogSnapshotRepository repository,
                                       JdbcTemplate jdbcTemplate,
                                       @Value("${guardian.cm-log.retention-hours:24}") long retentionHours,
                                       @Value("${guardian.cm-log.dedupe-window-minutes:10}") long dedupeWindowMinutes) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.retentionHours = retentionHours;
        this.dedupeWindowMinutes = dedupeWindowMinutes;
    }

    @PostConstruct
    public void ensureTableExists() {
        jdbcTemplate.execute(
            "create table if not exists cm_service_log_snapshot ("
                + "id bigserial primary key,"
                + "cluster_name varchar(128) not null,"
                + "service_name varchar(128) not null,"
                + "service_type varchar(64) not null,"
                + "log_type varchar(32) not null,"
                + "log_text text not null,"
                + "collected_at timestamp not null"
                + ")"
        );
        jdbcTemplate.execute(
            "create index if not exists idx_cm_service_log_snapshot_collected_at "
                + "on cm_service_log_snapshot(collected_at desc)"
        );
        jdbcTemplate.execute(
            "create index if not exists idx_cm_service_log_snapshot_service_type "
                + "on cm_service_log_snapshot(service_type)"
        );
        jdbcTemplate.execute(
            "alter table cm_service_log_snapshot add column if not exists log_hash varchar(64)"
        );
        jdbcTemplate.execute(
            "create index if not exists idx_cm_service_log_snapshot_lookup "
                + "on cm_service_log_snapshot(cluster_name, service_type, collected_at desc)"
        );
        jdbcTemplate.execute(
            "create index if not exists idx_cm_service_log_snapshot_hash "
                + "on cm_service_log_snapshot(cluster_name, service_type, log_type, log_hash, collected_at desc)"
        );
    }

    @Transactional
    public void appendSnapshots(String clusterName, List<CmServiceStatusRecord> services, Instant collectedAt) {
        String safeClusterName = defaultIfBlank(clusterName, "UNKNOWN_CLUSTER");
        List<CmServiceLogSnapshotEntity> snapshots = new ArrayList<CmServiceLogSnapshotEntity>();
        if (services != null) {
            for (CmServiceStatusRecord service : services) {
                if (service == null) {
                    continue;
                }
                snapshots.addAll(buildSnapshots(safeClusterName, service, collectedAt));
            }
        }

        List<CmServiceLogSnapshotEntity> filtered = dedupeSnapshots(snapshots, collectedAt);
        if (!filtered.isEmpty()) {
            repository.saveAll(filtered);
        }
        pruneExpiredSnapshots();
    }

    public List<CmServiceLogSnapshotRecord> getLatestLogs() {
        return repository.findTop20ByOrderByCollectedAtDescIdDesc().stream()
            .map(CmServiceLogSnapshotRecord::fromEntity)
            .collect(Collectors.toList());
    }

    public List<CmServiceLogSnapshotRecord> getLatestLogsForService(String clusterName, String serviceType, int limit) {
        String normalizedCluster = defaultIfBlank(clusterName, "UNKNOWN_CLUSTER");
        String normalizedServiceType = safe(serviceType).toUpperCase(Locale.ROOT);
        if (normalizedServiceType.isEmpty()) {
            return Collections.emptyList();
        }

        List<CmServiceLogSnapshotRecord> logs = new ArrayList<CmServiceLogSnapshotRecord>();
        Set<String> delivered = new LinkedHashSet<String>();
        int safeLimit = Math.max(1, Math.min(limit, 20));
        List<CmServiceLogSnapshotEntity> entities = repository.findLatestByClusterAndServiceTypeIgnoreCase(
            normalizedCluster,
            normalizedServiceType,
            PageRequest.of(0, 50)
        );
        if (entities.isEmpty()) {
            entities = repository.findLatestByServiceTypeIgnoreCase(
                normalizedServiceType,
                PageRequest.of(0, 50)
            );
        }
        for (CmServiceLogSnapshotEntity entity : entities) {
            if (!isPersistableLogText(entity.getLogText())) {
                continue;
            }
            String key = defaultIfBlank(entity.getLogHash(), defaultIfBlank(entity.getLogText(), ""));
            if (!delivered.add(key)) {
                continue;
            }
            logs.add(CmServiceLogSnapshotRecord.fromEntity(entity));
            if (logs.size() >= safeLimit) {
                break;
            }
        }
        return logs;
    }

    public List<CmServiceLogSnapshotRecord> getRecentLogsForService(String clusterName,
                                                                    String serviceName,
                                                                    String serviceType,
                                                                    Instant lookupStart,
                                                                    int limit) {
        String normalizedCluster = defaultIfBlank(clusterName, "UNKNOWN_CLUSTER");
        String normalizedServiceName = defaultIfBlank(serviceName, serviceType);
        String normalizedServiceType = safe(serviceType).toUpperCase(Locale.ROOT);
        if (normalizedServiceType.isEmpty()) {
            return Collections.emptyList();
        }

        int safeLimit = Math.max(1, Math.min(limit, 20));
        PageRequest page = PageRequest.of(0, 60);
        List<CmServiceLogSnapshotEntity> entities = repository.findLatestByClusterAndServiceNameAndServiceTypeAfterIgnoreCase(
            normalizedCluster,
            normalizedServiceName,
            normalizedServiceType,
            lookupStart == null ? Instant.now().minusSeconds(15 * 60L) : lookupStart,
            page
        );
        if (entities.isEmpty()) {
            entities = repository.findLatestByClusterAndServiceNameAndServiceTypeIgnoreCase(
                normalizedCluster,
                normalizedServiceName,
                normalizedServiceType,
                page
            );
        }

        List<CmServiceLogSnapshotRecord> logs = new ArrayList<CmServiceLogSnapshotRecord>();
        Set<String> delivered = new LinkedHashSet<String>();
        for (CmServiceLogSnapshotEntity entity : entities) {
            if (!isPersistableLogText(entity.getLogText())) {
                continue;
            }
            String key = defaultIfBlank(entity.getLogHash(), defaultIfBlank(entity.getLogText(), ""));
            if (!delivered.add(key)) {
                continue;
            }
            logs.add(CmServiceLogSnapshotRecord.fromEntity(entity));
            if (logs.size() >= safeLimit) {
                break;
            }
        }
        return logs;
    }

    public List<CmServiceLogSnapshotRecord> getRecentLogsForCluster(String clusterName,
                                                                    Instant lookupStart,
                                                                    int limit) {
        String normalizedCluster = defaultIfBlank(clusterName, "UNKNOWN_CLUSTER");
        int safeLimit = Math.max(1, Math.min(limit, 200));
        PageRequest page = PageRequest.of(0, Math.max(safeLimit * 2, 50));
        List<CmServiceLogSnapshotEntity> entities = repository.findLatestByClusterAfterIgnoreCase(
            normalizedCluster,
            lookupStart == null ? Instant.now().minusSeconds(15 * 60L) : lookupStart,
            page
        );
        if (entities.isEmpty()) {
            entities = repository.findLatestByClusterIgnoreCase(normalizedCluster, page);
        }

        List<CmServiceLogSnapshotRecord> logs = new ArrayList<CmServiceLogSnapshotRecord>();
        Set<String> delivered = new LinkedHashSet<String>();
        for (CmServiceLogSnapshotEntity entity : entities) {
            if (!isPersistableLogText(entity.getLogText())) {
                continue;
            }
            String key = defaultIfBlank(entity.getLogHash(), defaultIfBlank(entity.getLogText(), ""));
            if (!delivered.add(key)) {
                continue;
            }
            logs.add(CmServiceLogSnapshotRecord.fromEntity(entity));
            if (logs.size() >= safeLimit) {
                break;
            }
        }
        return logs;
    }

    public List<CmServiceLogSnapshotRecord> getLogsForIncident(IncidentEntity incident) {
        if (incident == null) {
            return Collections.emptyList();
        }
        String requestedServiceType = safe(incident.getServiceType()).toUpperCase(Locale.ROOT);
        if (requestedServiceType.isEmpty()) {
            return Collections.emptyList();
        }

        String clusterName = defaultIfBlank(incident.getClusterName(), "UNKNOWN_CLUSTER");
        List<String> serviceTypes = resolveServiceTypes(requestedServiceType);
        Instant lookupStart = incident.getOccurredAt() == null
            ? Instant.now().minusSeconds(6 * 60 * 60)
            : incident.getOccurredAt().minusSeconds(6 * 60 * 60);

        List<String> normalizedServiceTypes = normalizeServiceTypes(serviceTypes);
        List<CmServiceLogSnapshotEntity> serviceLogs = loadIncidentServiceLogs(clusterName, normalizedServiceTypes, lookupStart);
        serviceLogs.removeIf(entity -> !isPersistableLogText(entity.getLogText()));
        if (serviceLogs.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> signalTokens = extractIncidentSignalTokens(incident, requestedServiceType);
        List<CmServiceLogSnapshotEntity> prioritized = new ArrayList<CmServiceLogSnapshotEntity>();
        if (!signalTokens.isEmpty()) {
            for (CmServiceLogSnapshotEntity entity : serviceLogs) {
                if (matchesSignalTokens(entity.getLogText(), signalTokens)) {
                    prioritized.add(entity);
                }
            }
        }
        if (prioritized.isEmpty()) {
            prioritized = serviceLogs;
        }

        prioritized.sort(
            Comparator
                .comparingInt((CmServiceLogSnapshotEntity entity) -> "WARN_ERROR".equalsIgnoreCase(entity.getLogType()) ? 0 : 1)
                .thenComparing(CmServiceLogSnapshotEntity::getCollectedAt, Comparator.reverseOrder())
                .thenComparing(CmServiceLogSnapshotEntity::getId, Comparator.reverseOrder())
        );

        List<CmServiceLogSnapshotRecord> logs = new ArrayList<CmServiceLogSnapshotRecord>();
        Set<String> delivered = new LinkedHashSet<String>();
        for (CmServiceLogSnapshotEntity entity : prioritized) {
            String key = defaultIfBlank(entity.getLogHash(), defaultIfBlank(entity.getLogText(), ""));
            if (!delivered.add(key)) {
                continue;
            }
            logs.add(CmServiceLogSnapshotRecord.fromEntity(entity));
            if (logs.size() >= 12) {
                break;
            }
        }
        return logs;
    }

    private List<CmServiceLogSnapshotEntity> loadIncidentServiceLogs(String clusterName,
                                                                     List<String> serviceTypes,
                                                                     Instant lookupStart) {
        PageRequest recentPage = PageRequest.of(0, 300);
        List<CmServiceLogSnapshotEntity> serviceLogs = new ArrayList<CmServiceLogSnapshotEntity>(
            repository.findLatestByClusterAndServiceTypesAfterIgnoreCase(
                clusterName,
                serviceTypes,
                lookupStart,
                recentPage
            )
        );
        if (!serviceLogs.isEmpty()) {
            return serviceLogs;
        }

        serviceLogs.addAll(repository.findLatestByClusterAndServiceTypesIgnoreCase(clusterName, serviceTypes, recentPage));
        if (!serviceLogs.isEmpty()) {
            return serviceLogs;
        }

        serviceLogs.addAll(repository.findLatestByServiceTypesAfterIgnoreCase(serviceTypes, lookupStart, recentPage));
        if (!serviceLogs.isEmpty()) {
            return serviceLogs;
        }

        serviceLogs.addAll(repository.findLatestByServiceTypesIgnoreCase(serviceTypes, recentPage));
        if (!serviceLogs.isEmpty()) {
            return serviceLogs;
        }

        serviceLogs.addAll(repository.findLatestByClusterAfterIgnoreCase(clusterName, lookupStart, recentPage));
        if (!serviceLogs.isEmpty()) {
            return serviceLogs;
        }

        serviceLogs.addAll(repository.findLatestByClusterIgnoreCase(clusterName, recentPage));
        return serviceLogs;
    }

    private List<CmServiceLogSnapshotEntity> dedupeSnapshots(List<CmServiceLogSnapshotEntity> snapshots, Instant collectedAt) {
        if (snapshots == null || snapshots.isEmpty()) {
            return Collections.emptyList();
        }
        Instant dedupeCutoff = (collectedAt == null ? Instant.now() : collectedAt).minusSeconds(Math.max(1L, dedupeWindowMinutes) * 60L);
        List<CmServiceLogSnapshotEntity> filtered = new ArrayList<CmServiceLogSnapshotEntity>();
        Set<String> batchKeys = new LinkedHashSet<String>();
        for (CmServiceLogSnapshotEntity entity : snapshots) {
            if (entity == null || !isPersistableLogText(entity.getLogText())) {
                continue;
            }
            String batchKey = entity.getClusterName() + "||"
                + entity.getServiceType() + "||"
                + entity.getLogType() + "||"
                + defaultIfBlank(entity.getLogHash(), "");
            if (!batchKeys.add(batchKey)) {
                continue;
            }
            boolean exists = repository.existsByClusterNameAndServiceTypeAndLogTypeAndLogHashAndCollectedAtAfter(
                entity.getClusterName(),
                entity.getServiceType(),
                entity.getLogType(),
                entity.getLogHash(),
                dedupeCutoff
            );
            if (!exists) {
                filtered.add(entity);
            }
        }
        return filtered;
    }

    private List<CmServiceLogSnapshotEntity> buildSnapshots(String clusterName,
                                                            CmServiceStatusRecord service,
                                                            Instant collectedAt) {
        List<CmServiceLogSnapshotEntity> snapshots = new ArrayList<CmServiceLogSnapshotEntity>();
        for (String item : service.getLogHighlights()) {
            if (isPersistableLogText(item)) {
                snapshots.add(buildSnapshot(clusterName, service, "WARN_ERROR", item, collectedAt));
            }
        }
        for (String item : service.getLogPreviewLines()) {
            if (isPersistableLogText(item)) {
                snapshots.add(buildSnapshot(clusterName, service, "PREVIEW", item, collectedAt));
            }
        }
        return snapshots;
    }

    private CmServiceLogSnapshotEntity buildSnapshot(String clusterName,
                                                     CmServiceStatusRecord service,
                                                     String logType,
                                                     String logText,
                                                     Instant collectedAt) {
        CmServiceLogSnapshotEntity entity = new CmServiceLogSnapshotEntity();
        entity.setClusterName(clusterName);
        entity.setServiceName(defaultIfBlank(service.getServiceName(), service.getServiceType()));
        entity.setServiceType(defaultIfBlank(service.getServiceType(), "UNKNOWN"));
        entity.setLogType(logType);
        entity.setLogText(defaultIfBlank(logText, "empty"));
        entity.setLogHash(hashLogText(logText));
        entity.setCollectedAt(collectedAt == null ? Instant.now() : collectedAt);
        return entity;
    }

    private List<String> resolveServiceTypes(String requestedServiceType) {
        if ("HIVE_METASTORE".equals(requestedServiceType)) {
            return Arrays.asList("HIVE_METASTORE", "HIVE", "TEZ");
        }
        if ("HIVE_ON_TEZ".equals(requestedServiceType)) {
            return Arrays.asList("HIVE_ON_TEZ", "HIVE", "TEZ");
        }
        if ("HIVE".equals(requestedServiceType)) {
            return Arrays.asList("HIVE", "HIVE_ON_TEZ", "HIVE_METASTORE", "TEZ");
        }
        if ("IMPALA".equals(requestedServiceType)) {
            return Arrays.asList("IMPALA", "IMPALAD", "CATALOGD", "STATESTORE");
        }
        return Collections.singletonList(requestedServiceType);
    }

    private List<String> normalizeServiceTypes(List<String> serviceTypes) {
        Set<String> normalized = new LinkedHashSet<String>();
        if (serviceTypes != null) {
            for (String serviceType : serviceTypes) {
                String upper = safe(serviceType).toUpperCase(Locale.ROOT);
                if (!upper.isEmpty()) {
                    normalized.add(upper);
                }
            }
        }
        return new ArrayList<String>(normalized);
    }

    private String hashLogText(String logText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(defaultIfBlank(logText, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception exception) {
            return Integer.toHexString(defaultIfBlank(logText, "").hashCode());
        }
    }

    private boolean matchesServiceType(String requestedServiceType, String actualServiceType) {
        String normalizedActual = safe(actualServiceType).toUpperCase(Locale.ROOT);
        if (requestedServiceType.equals(normalizedActual)) {
            return true;
        }
        if ("HIVE_ON_TEZ".equals(requestedServiceType)) {
            return normalizedActual.contains("HIVE") || normalizedActual.contains("TEZ");
        }
        if ("IMPALA".equals(requestedServiceType)) {
            return normalizedActual.contains("IMPALA");
        }
        if ("YARN".equals(requestedServiceType)) {
            return normalizedActual.contains("YARN");
        }
        if ("HDFS".equals(requestedServiceType)) {
            return normalizedActual.contains("HDFS");
        }
        return false;
    }

    private List<String> extractIncidentSignalTokens(IncidentEntity incident, String requestedServiceType) {
        Set<String> tokens = new LinkedHashSet<String>();
        collectSignalTokens(tokens, incident.getTitle(), requestedServiceType);
        collectSignalTokens(tokens, incident.getSummary(), requestedServiceType);
        collectSignalTokens(tokens, incident.getImpactScope(), requestedServiceType);
        if (incident.getEvidence() != null) {
            for (String item : incident.getEvidence()) {
                collectSignalTokens(tokens, item, requestedServiceType);
            }
        }
        return new ArrayList<String>(tokens);
    }

    private void collectSignalTokens(Set<String> tokens, String text, String requestedServiceType) {
        String upper = safe(text).toUpperCase(Locale.ROOT);
        Matcher matcher = SIGNAL_TOKEN_PATTERN.matcher(upper);
        while (matcher.find()) {
            String token = matcher.group();
            if (shouldIgnoreSignalToken(token, requestedServiceType)) {
                continue;
            }
            tokens.add(token);
        }
    }

    private boolean shouldIgnoreSignalToken(String token, String requestedServiceType) {
        if (token.equals(requestedServiceType)) {
            return true;
        }
        return "WARN".equals(token)
            || "WARNING".equals(token)
            || "ERROR".equals(token)
            || "INFO".equals(token)
            || "DEBUG".equals(token)
            || "TRACE".equals(token)
            || "SERVICE".equals(token)
            || "CURRENT".equals(token)
            || "REALTIME".equals(token)
            || "HEALTH".equals(token)
            || "STARTED".equals(token)
            || "STOPPED".equals(token)
            || "CONCERNING".equals(token)
            || "UNKNOWN".equals(token)
            || "HDFS".equals(token)
            || "IMPALA".equals(token)
            || "YARN".equals(token)
            || "HIVE".equals(token)
            || "TEZ".equals(token)
            || "SPARK".equals(token)
            || "KAFKA".equals(token)
            || "HBASE".equals(token)
            || "RANGER".equals(token)
            || "ZOOKEEPER".equals(token);
    }

    private boolean matchesSignalTokens(String logText, List<String> signalTokens) {
        String upper = safe(logText).toUpperCase(Locale.ROOT);
        for (String token : signalTokens) {
            if (upper.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPersistableLogText(String logText) {
        String normalized = safe(logText);
        if (normalized.isEmpty()) {
            return false;
        }
        return !normalized.contains("日志读取失败");
    }

    private String defaultIfBlank(String value, String fallback) {
        String safeValue = safe(value);
        return safeValue.isEmpty() ? fallback : safeValue;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void pruneExpiredSnapshots() {
        if (retentionHours <= 0L) {
            return;
        }
        repository.deleteByCollectedAtBefore(Instant.now().minusSeconds(retentionHours * 3600L));
    }
}
