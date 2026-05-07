package com.guardian.hadoop.incident;

import com.guardian.hadoop.diagnosis.DiagnosisEntity;
import com.guardian.hadoop.diagnosis.DiagnosisRepository;
import com.guardian.hadoop.integration.cm.CmServiceLogSnapshotRecord;
import com.guardian.hadoop.integration.cm.CmServiceLogSnapshotService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CrossComponentAnalysisService {

    private static final Pattern IP_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Z][A-Z0-9_-]{3,}");
    private static final long LOOKBACK_HOURS = 6L;

    private static final Map<String, List<String>> DEPENDENCY_MAP = buildDependencyMap();

    private final IncidentRepository incidentRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final CmServiceLogSnapshotService logSnapshotService;

    public CrossComponentAnalysisService(IncidentRepository incidentRepository,
                                         DiagnosisRepository diagnosisRepository,
                                         CmServiceLogSnapshotService logSnapshotService) {
        this.incidentRepository = incidentRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.logSnapshotService = logSnapshotService;
    }

    public CrossComponentAnalysisRecord analyze(long incidentId) {
        IncidentEntity incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) {
            return null;
        }

        String primaryService = normalizeServiceType(incident.getServiceType());
        List<CmServiceLogSnapshotRecord> primaryLogs = logSnapshotService.getLogsForIncident(incident);
        Set<String> primaryNodes = extractNodes(incident, primaryLogs);
        Set<String> primarySignals = extractSignals(incident, primaryLogs);
        DiagnosisEntity latestDiagnosis = diagnosisRepository.findTopByIncident_IdOrderByCreatedAtDesc(incidentId);

        Instant incidentTime = incident.getLastSeenAt() != null ? incident.getLastSeenAt() : incident.getOccurredAt();
        Instant lowerBound = (incidentTime == null ? Instant.now() : incidentTime).minusSeconds(LOOKBACK_HOURS * 3600L);

        List<IncidentEntity> candidates = incidentRepository.findTop200ByClusterNameOrderByOccurredAtDesc(defaultIfBlank(incident.getClusterName(), "UNKNOWN_CLUSTER"));
        List<CorrelatedIncidentCandidate> correlated = new ArrayList<CorrelatedIncidentCandidate>();
        for (IncidentEntity candidate : candidates) {
            if (candidate == null || candidate.getId() == null || candidate.getId().longValue() == incidentId) {
                continue;
            }
            if (candidate.getOccurredAt() != null && candidate.getOccurredAt().isBefore(lowerBound)) {
                continue;
            }
            CorrelatedIncidentCandidate relation = correlate(incident, candidate, primaryService, primaryNodes, primarySignals, latestDiagnosis);
            if (relation != null) {
                correlated.add(relation);
            }
        }

        correlated.sort(
            Comparator.comparingInt(CorrelatedIncidentCandidate::getScore).reversed()
                .thenComparing(CorrelatedIncidentCandidate::getOccurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
        );
        if (correlated.size() > 5) {
            correlated = new ArrayList<CorrelatedIncidentCandidate>(correlated.subList(0, 5));
        }

        List<CrossComponentRelatedIncidentRecord> relatedRecords = correlated.stream()
            .map(CorrelatedIncidentCandidate::toRecord)
            .collect(Collectors.toList());
        List<String> correlatedServices = correlated.stream()
            .map(CorrelatedIncidentCandidate::getServiceType)
            .distinct()
            .collect(Collectors.toList());
        List<String> sharedNodes = correlated.stream()
            .flatMap(item -> item.getSharedNodes().stream())
            .distinct()
            .limit(6)
            .collect(Collectors.toList());
        List<String> signalHighlights = correlated.stream()
            .flatMap(item -> item.getMatchedSignals().stream())
            .distinct()
            .limit(8)
            .collect(Collectors.toList());

        String probablePath = buildProbablePath(primaryService, correlatedServices, latestDiagnosis);
        double confidence = correlated.isEmpty()
            ? 0.28d
            : Math.min(0.92d, 0.42d + correlated.get(0).getScore() / 100.0d);
        String confidenceLabel = confidence >= 0.75d ? "HIGH" : confidence >= 0.5d ? "MEDIUM" : "LOW";
        String summary = correlated.isEmpty()
            ? "未发现明确的跨组件传播证据，当前更像单服务异常，建议继续围绕当前服务日志和节点做诊断。"
            : "发现 " + correlated.size() + " 个相关组件事件，当前异常很可能不是单点问题，已经出现跨组件关联信号。";
        String impactAssessment = correlated.isEmpty()
            ? "当前影响范围主要集中在 " + primaryService + "，尚未发现同时间窗内其他关键组件的明显联动。"
            : "当前异常已和 " + join(correlatedServices, "、") + " 出现时间窗重叠、共享节点或依赖链路关联，建议按传播路径确认影响范围。";
        List<String> recommendedChecks = buildRecommendedChecks(primaryService, correlated, primaryNodes);

        return new CrossComponentAnalysisRecord(
            primaryService,
            probablePath,
            confidence,
            confidenceLabel,
            summary,
            impactAssessment,
            correlatedServices,
            sharedNodes,
            signalHighlights,
            recommendedChecks,
            relatedRecords
        );
    }

    private CorrelatedIncidentCandidate correlate(IncidentEntity primaryIncident,
                                                  IncidentEntity candidate,
                                                  String primaryService,
                                                  Set<String> primaryNodes,
                                                  Set<String> primarySignals,
                                                  DiagnosisEntity latestDiagnosis) {
        String candidateService = normalizeServiceType(candidate.getServiceType());
        if (candidateService.equals(primaryService)) {
            return null;
        }

        List<String> reasons = new ArrayList<String>();
        int score = 0;

        if (isDependencyLinked(primaryService, candidateService)) {
            score += 30;
            reasons.add(primaryService + " 与 " + candidateService + " 存在依赖链路");
        }

        long minutesApart = minutesApart(primaryIncident, candidate);
        if (minutesApart <= 30L) {
            score += 20;
            reasons.add("两起事件在 30 分钟内连续出现");
        } else if (minutesApart <= 120L) {
            score += 10;
            reasons.add("两起事件处于同一排障时间窗");
        }

        List<CmServiceLogSnapshotRecord> candidateLogs = logSnapshotService.getLogsForIncident(candidate);
        Set<String> candidateNodes = extractNodes(candidate, candidateLogs);
        Set<String> sharedNodes = intersect(primaryNodes, candidateNodes);
        if (!sharedNodes.isEmpty()) {
            score += 35;
            reasons.add("共享节点: " + join(sharedNodes.stream().limit(3).collect(Collectors.toList()), "、"));
        }

        Set<String> candidateSignals = extractSignals(candidate, candidateLogs);
        Set<String> sharedSignals = intersect(primarySignals, candidateSignals);
        if (!sharedSignals.isEmpty()) {
            score += 15;
            reasons.add("共享日志/事件信号: " + join(sharedSignals.stream().limit(3).collect(Collectors.toList()), "、"));
        }

        if (latestDiagnosis != null && referencesService(latestDiagnosis.getCrossComponentPath(), candidateService)) {
            score += 15;
            reasons.add("最近一次诊断已经指向 " + candidateService + " 依赖路径");
        }

        if (score < 30) {
            return null;
        }

        String relationSummary = reasons.isEmpty()
            ? "与当前事件存在跨组件关联。"
            : join(reasons, "；");
        return new CorrelatedIncidentCandidate(
            candidate.getId().longValue(),
            candidate.getIncidentNo(),
            candidateService,
            defaultIfBlank(candidate.getSeverity(), "UNKNOWN"),
            defaultIfBlank(candidate.getStatus(), "OPEN"),
            defaultIfBlank(candidate.getTitle(), candidateService + " related incident"),
            score,
            relationSummary,
            new ArrayList<String>(sharedSignals),
            new ArrayList<String>(sharedNodes),
            candidate.getOccurredAt()
        );
    }

    private String buildProbablePath(String primaryService, List<String> correlatedServices, DiagnosisEntity latestDiagnosis) {
        if (latestDiagnosis != null && hasText(latestDiagnosis.getCrossComponentPath())) {
            return latestDiagnosis.getCrossComponentPath();
        }
        if (correlatedServices.isEmpty()) {
            return primaryService;
        }
        return primaryService + " -> " + join(correlatedServices, " -> ");
    }

    private List<String> buildRecommendedChecks(String primaryService,
                                                List<CorrelatedIncidentCandidate> correlated,
                                                Set<String> primaryNodes) {
        List<String> checks = new ArrayList<String>();
        checks.add("先确认 " + primaryService + " 当前异常是否集中在同一节点、同一角色或同一时间窗。");
        if (!primaryNodes.isEmpty()) {
            checks.add("优先核查节点 " + join(primaryNodes.stream().limit(4).collect(Collectors.toList()), "、") + " 的角色状态和日志。");
        }
        if (!correlated.isEmpty()) {
            checks.add("按传播路径优先检查 " + correlated.get(0).getServiceType() + " 与当前服务之间的依赖链路是否同时异常。");
            checks.add("把当前事件与关联事件的 WARN/ERROR 日志按时间排序，确认是上游扩散还是下游受影响。");
        } else {
            checks.add("当前未发现明显跨组件扩散，建议继续围绕当前服务的节点、队列、RPC 和存储链路排查。");
        }
        return checks.stream().distinct().limit(5).collect(Collectors.toList());
    }

    private boolean isDependencyLinked(String primaryService, String candidateService) {
        List<String> outbound = DEPENDENCY_MAP.getOrDefault(primaryService, Collections.<String>emptyList());
        List<String> reverse = DEPENDENCY_MAP.getOrDefault(candidateService, Collections.<String>emptyList());
        return outbound.contains(candidateService) || reverse.contains(primaryService);
    }

    private boolean referencesService(String text, String serviceType) {
        String normalized = safe(text).toUpperCase(Locale.ROOT);
        return normalized.contains(serviceType);
    }

    private long minutesApart(IncidentEntity primaryIncident, IncidentEntity candidate) {
        Instant primaryTime = primaryIncident.getLastSeenAt() != null ? primaryIncident.getLastSeenAt() : primaryIncident.getOccurredAt();
        Instant candidateTime = candidate.getLastSeenAt() != null ? candidate.getLastSeenAt() : candidate.getOccurredAt();
        if (primaryTime == null || candidateTime == null) {
            return Long.MAX_VALUE;
        }
        return Math.abs(Duration.between(primaryTime, candidateTime).toMinutes());
    }

    private Set<String> extractNodes(IncidentEntity incident, List<CmServiceLogSnapshotRecord> logs) {
        Set<String> nodes = new LinkedHashSet<String>();
        collectIpMatches(nodes, incident.getSummary());
        collectIpMatches(nodes, incident.getImpactScope());
        if (incident.getEvidence() != null) {
            for (String item : incident.getEvidence()) {
                collectIpMatches(nodes, item);
            }
        }
        if (logs != null) {
            for (CmServiceLogSnapshotRecord log : logs) {
                collectIpMatches(nodes, log.getLogText());
            }
        }
        return nodes;
    }

    private Set<String> extractSignals(IncidentEntity incident, List<CmServiceLogSnapshotRecord> logs) {
        Set<String> signals = new LinkedHashSet<String>();
        collectSignalTokens(signals, incident.getTitle());
        collectSignalTokens(signals, incident.getSummary());
        collectSignalTokens(signals, incident.getImpactScope());
        if (incident.getEvidence() != null) {
            for (String item : incident.getEvidence()) {
                collectSignalTokens(signals, item);
            }
        }
        if (logs != null) {
            for (CmServiceLogSnapshotRecord log : logs) {
                collectSignalTokens(signals, log.getLogText());
            }
        }
        signals.remove(normalizeServiceType(incident.getServiceType()));
        return signals;
    }

    private void collectIpMatches(Set<String> nodes, String text) {
        Matcher matcher = IP_PATTERN.matcher(safe(text));
        while (matcher.find()) {
            nodes.add(matcher.group());
        }
    }

    private void collectSignalTokens(Set<String> signals, String text) {
        String normalized = safe(text).toUpperCase(Locale.ROOT);
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 4 && token.length() <= 64) {
                signals.add(token);
            }
        }
    }

    private Set<String> intersect(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<String>();
        for (String item : left) {
            if (right.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private String normalizeServiceType(String serviceType) {
        String normalized = safe(serviceType).toUpperCase(Locale.ROOT);
        if (normalized.contains("HIVE_METASTORE")) {
            return "HIVE_METASTORE";
        }
        if (normalized.contains("HIVE")) {
            return "HIVE_ON_TEZ";
        }
        return normalized.isEmpty() ? "CROSS_COMPONENT" : normalized;
    }

    private static Map<String, List<String>> buildDependencyMap() {
        Map<String, List<String>> map = new LinkedHashMap<String, List<String>>();
        map.put("HDFS", Arrays.asList("YARN", "HIVE_ON_TEZ", "IMPALA", "HBASE"));
        map.put("YARN", Arrays.asList("HDFS", "HIVE_ON_TEZ", "SPARK", "IMPALA"));
        map.put("HIVE_ON_TEZ", Arrays.asList("YARN", "HDFS", "HIVE_METASTORE", "IMPALA"));
        map.put("HIVE_METASTORE", Arrays.asList("HIVE_ON_TEZ", "IMPALA"));
        map.put("IMPALA", Arrays.asList("HDFS", "YARN", "HIVE_METASTORE", "RANGER"));
        map.put("HBASE", Arrays.asList("HDFS", "RANGER", "ZOOKEEPER"));
        map.put("RANGER", Arrays.asList("HDFS", "IMPALA", "HIVE_ON_TEZ", "HBASE", "KAFKA"));
        map.put("KAFKA", Arrays.asList("ZOOKEEPER", "RANGER", "HDFS"));
        map.put("CROSS_COMPONENT", Arrays.asList("HDFS", "YARN", "HIVE_ON_TEZ", "IMPALA", "HBASE", "RANGER", "KAFKA"));
        return map;
    }

    private String join(List<String> items, String delimiter) {
        return items.stream().filter(this::hasText).collect(Collectors.joining(delimiter));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static final class CorrelatedIncidentCandidate {
        private final long incidentId;
        private final String incidentNo;
        private final String serviceType;
        private final String severity;
        private final String status;
        private final String title;
        private final int score;
        private final String relationSummary;
        private final List<String> matchedSignals;
        private final List<String> sharedNodes;
        private final Instant occurredAt;

        private CorrelatedIncidentCandidate(long incidentId,
                                            String incidentNo,
                                            String serviceType,
                                            String severity,
                                            String status,
                                            String title,
                                            int score,
                                            String relationSummary,
                                            List<String> matchedSignals,
                                            List<String> sharedNodes,
                                            Instant occurredAt) {
            this.incidentId = incidentId;
            this.incidentNo = incidentNo;
            this.serviceType = serviceType;
            this.severity = severity;
            this.status = status;
            this.title = title;
            this.score = score;
            this.relationSummary = relationSummary;
            this.matchedSignals = matchedSignals;
            this.sharedNodes = sharedNodes;
            this.occurredAt = occurredAt;
        }

        private CrossComponentRelatedIncidentRecord toRecord() {
            return new CrossComponentRelatedIncidentRecord(
                incidentId,
                incidentNo,
                serviceType,
                severity,
                status,
                title,
                score,
                relationSummary,
                matchedSignals,
                sharedNodes,
                occurredAt
            );
        }

        private int getScore() {
            return score;
        }

        private Instant getOccurredAt() {
            return occurredAt;
        }

        private String getServiceType() {
            return serviceType;
        }

        private List<String> getMatchedSignals() {
            return matchedSignals;
        }

        private List<String> getSharedNodes() {
            return sharedNodes;
        }
    }
}
