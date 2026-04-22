package com.guardian.hadoop.inspection;

import com.guardian.hadoop.incident.IncidentEntity;
import com.guardian.hadoop.incident.IncidentRepository;
import com.guardian.hadoop.integration.cm.ClouderaManagerCurrentStatusService;
import com.guardian.hadoop.integration.cm.CmCurrentStatusResponse;
import com.guardian.hadoop.integration.cm.CmServiceLogSnapshotRecord;
import com.guardian.hadoop.integration.cm.CmServiceStatusRecord;
import com.guardian.hadoop.integration.datasource.JmxProbeResponse;
import com.guardian.hadoop.integration.datasource.JmxProbeResult;
import com.guardian.hadoop.integration.datasource.JmxProbeService;
import com.guardian.hadoop.knowledge.KnowledgeSuggestionRecord;
import com.guardian.hadoop.knowledge.KnowledgeSuggestionService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClusterInspectionService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final ClusterInspectionReportRepository repository;
    private final ClouderaManagerCurrentStatusService currentStatusService;
    private final ClusterInspectionLlmService llmService;
    private final KnowledgeSuggestionService knowledgeSuggestionService;
    private final IncidentRepository incidentRepository;
    private final ClusterInspectionDocxExporter docxExporter;
    private final JmxProbeService jmxProbeService;
    private final ClusterInspectionJobService jobService;

    public ClusterInspectionService(ClusterInspectionReportRepository repository,
                                    ClouderaManagerCurrentStatusService currentStatusService,
                                    ClusterInspectionLlmService llmService,
                                    KnowledgeSuggestionService knowledgeSuggestionService,
                                    IncidentRepository incidentRepository,
                                    ClusterInspectionDocxExporter docxExporter,
                                    JmxProbeService jmxProbeService,
                                    ClusterInspectionJobService jobService) {
        this.repository = repository;
        this.currentStatusService = currentStatusService;
        this.llmService = llmService;
        this.knowledgeSuggestionService = knowledgeSuggestionService;
        this.incidentRepository = incidentRepository;
        this.docxExporter = docxExporter;
        this.jmxProbeService = jmxProbeService;
        this.jobService = jobService;
    }

    public List<ClusterInspectionReport> listReports() {
        return repository.findTop50ByOrderByCreatedAtDescIdDesc().stream()
            .map(ClusterInspectionReport::fromEntity)
            .collect(Collectors.toList());
    }

    public ClusterInspectionReport getReport(long reportId) {
        return ClusterInspectionReport.fromEntity(findEntity(reportId));
    }

    @Transactional
    public ClusterInspectionReport createReport(String triggeredBy) {
        ClusterInspectionReportEntity entity = new ClusterInspectionReportEntity();
        entity.setClusterName("PENDING_CLUSTER");
        entity.setReportTitle("集群巡检报告生成中");
        entity.setOverallRisk("MEDIUM");
        entity.setStatus("PENDING");
        entity.setSummary("巡检报告已入队，正在后台聚合 CM 状态、服务日志、JMX 和知识库信息。");
        entity.setMarkdownContent("巡检报告生成中，请稍后刷新。");
        entity.setGeneratedBy(defaultIfBlank(triggeredBy, "frontend-operator"));
        entity.setCreatedAt(Instant.now());
        repository.saveAndFlush(entity);
        final Long reportId = entity.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                jobService.generateReportAsync(reportId);
            }
        });
        return ClusterInspectionReport.fromEntity(entity);
    }

    public void generateReportContent(ClusterInspectionReportEntity entity) {
        CmCurrentStatusResponse currentStatus = currentStatusService.fetchCurrentStatus();
        if (!currentStatus.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cloudera Manager 未启用，无法生成巡检报告");
        }
        if (!currentStatus.isSuccess()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, defaultIfBlank(currentStatus.getDetails(), currentStatus.getMessage()));
        }

        String clusterName = resolveClusterName(currentStatus);
        String inspectionContext = buildInspectionContext(clusterName, currentStatus);

        entity.setClusterName(clusterName);
        entity.setReportTitle(clusterName + " 集群巡检报告");
        entity.setSourceCollectedAt(currentStatus.getCollectedAt());
        repository.save(entity);

        ClusterInspectionLlmResult llmResult = llmService.generateReport(
            clusterName,
            inspectionContext,
            (completedSections, totalSections, sectionTitle, currentMarkdown) -> {
                entity.setStatus("RUNNING");
                entity.setSummary("宸℃鎶ュ憡姝ｅ湪鐢熸垚锛屽凡瀹屾垚 " + completedSections + "/" + totalSections + " 绔犺妭锛? " + sectionTitle);
                entity.setMarkdownContent(currentMarkdown);
                entity.setSourceCollectedAt(currentStatus.getCollectedAt());
                repository.save(entity);
            }
        );

        entity.setOverallRisk(llmResult.getOverallRisk());
        entity.setStatus("COMPLETED");
        entity.setSummary(llmResult.getSummary());
        entity.setMarkdownContent(llmResult.getMarkdownContent());
        entity.setLlmModel(llmResult.getModel());
        entity.setCompletedAt(Instant.now());
        entity.setErrorMessage(null);
        repository.save(entity);
    }

    public byte[] exportDocx(long reportId) {
        return docxExporter.export(getReport(reportId));
    }

    private ClusterInspectionReportEntity findEntity(long reportId) {
        return repository.findById(reportId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "巡检报告不存在"));
    }

    private String resolveClusterName(CmCurrentStatusResponse currentStatus) {
        for (CmServiceLogSnapshotRecord log : currentStatus.getRecentLogs()) {
            if (hasText(log.getClusterName())) {
                return log.getClusterName().trim();
            }
        }
        return "UNKNOWN_CLUSTER";
    }

    private String buildInspectionContext(String clusterName, CmCurrentStatusResponse currentStatus) {
        JmxProbeResponse jmxResponse = jmxProbeService.testAll();
        StringBuilder builder = new StringBuilder();
        builder.append("=== 巡检基础信息 ===\n");
        builder.append("巡检时间: ").append(formatInstant(currentStatus.getCollectedAt())).append('\n');
        builder.append("集群名称: ").append(clusterName).append('\n');
        builder.append("服务总数: ").append(currentStatus.getServiceCount()).append('\n');
        builder.append("异常服务数: ").append(currentStatus.getUnhealthyServiceCount()).append('\n');
        builder.append("采集接口: ").append(defaultIfBlank(currentStatus.getEndpoint(), "未记录")).append("\n\n");
        builder.append("JMX 端点总数: ").append(jmxResponse.getTotalCount())
            .append(" | 成功: ").append(jmxResponse.getSuccessCount())
            .append(" | 失败: ").append(jmxResponse.getFailureCount()).append("\n\n");

        builder.append("=== HDFS 专项巡检上下文 ===\n");
        appendServiceGroup(builder, currentStatus.getServices(), Arrays.asList("HDFS"), "当前未采集到 HDFS 服务状态。", jmxResponse.getResults());

        builder.append("\n=== YARN 专项巡检上下文 ===\n");
        appendServiceGroup(builder, currentStatus.getServices(), Arrays.asList("YARN"), "当前未采集到 YARN 服务状态。", jmxResponse.getResults());

        builder.append("\n=== Hive 与 Impala 专项巡检上下文 ===\n");
        appendServiceGroup(builder, currentStatus.getServices(), Arrays.asList("HIVE", "TEZ", "IMPALA"), "当前未采集到 Hive / Impala 相关服务状态。", jmxResponse.getResults());

        builder.append("\n=== 其他组件专项巡检上下文 ===\n");
        appendOtherServices(builder, currentStatus.getServices(), jmxResponse.getResults());

        builder.append("\n=== 当前未关闭事件 ===\n");
        appendOpenIncidents(builder, clusterName);

        builder.append("\n=== 容量风险专题证据 ===\n");
        appendRiskTopic(builder, currentStatus, "容量", "capacity", "disk", "space", "full", "quota", "replica", "under-replicated");

        builder.append("\n=== 性能风险专题证据 ===\n");
        appendRiskTopic(builder, currentStatus, "性能", "slow", "latency", "timeout", "rpc", "pending", "admission", "queue", "memory");

        builder.append("\n=== 稳定性风险专题证据 ===\n");
        appendRiskTopic(builder, currentStatus, "稳定性", "error", "warn", "bad", "aborted", "cancel", "exception", "restart", "stopped");

        builder.append("\n=== 安全与审计风险专题证据 ===\n");
        appendRiskTopic(builder, currentStatus, "安全与审计", "ranger", "audit", "auth", "kerberos", "permission", "denied", "ssl", "tls");

        builder.append("\n=== 最近服务日志快照 ===\n");
        appendRecentLogs(builder, currentStatus.getRecentLogs());
        return builder.toString();
    }

    private void appendServiceGroup(StringBuilder builder,
                                    List<CmServiceStatusRecord> services,
                                    List<String> serviceTokens,
                                    String emptyMessage,
                                    List<JmxProbeResult> jmxResults) {
        int count = 0;
        for (CmServiceStatusRecord service : services) {
            if (!matchesServiceGroup(service, serviceTokens)) {
                continue;
            }
            appendServiceSection(builder, service, jmxResults);
            count++;
        }
        if (count == 0) {
            builder.append("- ").append(emptyMessage).append('\n');
        }
    }

    private void appendOtherServices(StringBuilder builder,
                                     List<CmServiceStatusRecord> services,
                                     List<JmxProbeResult> jmxResults) {
        int count = 0;
        for (CmServiceStatusRecord service : services) {
            String type = normalize(service.getServiceType());
            if (containsAny(type, "HDFS", "YARN", "HIVE", "TEZ", "IMPALA")) {
                continue;
            }
            appendServiceSection(builder, service, jmxResults);
            count++;
        }
        if (count == 0) {
            builder.append("- 当前没有其他组件专项风险信号。\n");
        }
    }

    private void appendServiceSection(StringBuilder builder, CmServiceStatusRecord service, List<JmxProbeResult> jmxResults) {
        builder.append("\n[服务] ").append(defaultIfBlank(service.getServiceName(), service.getServiceType())).append('\n');
        builder.append("类型: ").append(defaultIfBlank(service.getServiceType(), "UNKNOWN")).append('\n');
        builder.append("状态: ").append(defaultIfBlank(service.getServiceState(), "UNKNOWN"))
            .append(" | 健康: ").append(defaultIfBlank(service.getHealthSummary(), "UNKNOWN"))
            .append(" | 异常角色: ").append(service.getUnhealthyRoleCount()).append('/').append(service.getRoleCount()).append('\n');
        appendItems(builder, "角色摘要", service.getRoleHighlights(), 8);
        appendItems(builder, "诊断用日志", service.getLogHighlights(), 8);
        appendItems(builder, "日志预览", service.getLogPreviewLines(), 4);
        appendJmxSummary(builder, service, jmxResults);
        appendKnowledge(builder, service);
    }

    private void appendJmxSummary(StringBuilder builder, CmServiceStatusRecord service, List<JmxProbeResult> jmxResults) {
        if (jmxResults == null || jmxResults.isEmpty()) {
            return;
        }
        List<JmxProbeResult> matched = new ArrayList<JmxProbeResult>();
        for (JmxProbeResult result : jmxResults) {
            if (!result.isSuccess()) {
                continue;
            }
            String normalizedService = normalize(service.getServiceType());
            String normalizedJmx = normalize(result.getServiceType());
            if (normalizedService.equals(normalizedJmx)
                || ("HIVE_ON_TEZ".equals(normalizedService) && containsAny(normalizedJmx, "HIVE", "TEZ"))
                || ("HDFS".equals(normalizedService) && containsAny(normalizedJmx, "HDFS"))
                || ("YARN".equals(normalizedService) && containsAny(normalizedJmx, "YARN"))
                || ("IMPALA".equals(normalizedService) && containsAny(normalizedJmx, "IMPALA"))) {
                matched.add(result);
            }
        }
        if (matched.isEmpty()) {
            return;
        }
        builder.append("巡检指标摘要:\n");
        int count = 0;
        for (JmxProbeResult result : matched) {
            builder.append("- ").append(defaultIfBlank(result.getRoleType(), "ROLE"))
                .append('@').append(defaultIfBlank(result.getTargetHost(), "unknown-host"))
                .append(" | bean数=").append(result.getBeanCount())
                .append(" | 指标=").append(renderMetrics(result))
                .append('\n');
            count++;
            if (count >= 3) {
                break;
            }
        }
    }

    private String renderMetrics(JmxProbeResult result) {
        List<String> metrics = result.getObservedMetrics();
        if (metrics == null || metrics.isEmpty()) {
            metrics = result.getSampleMetrics();
        }
        if (metrics == null || metrics.isEmpty()) {
            return "无";
        }
        return metrics.stream().limit(3).collect(Collectors.joining("；"));
    }

    private void appendKnowledge(StringBuilder builder, CmServiceStatusRecord service) {
        List<String> hints = new ArrayList<String>();
        addAll(hints, service.getRoleHighlights());
        addAll(hints, service.getLogHighlights());
        addAll(hints, service.getLogPreviewLines());
        String haystack = String.join(" | ", hints);
        List<KnowledgeSuggestionRecord> suggestions = knowledgeSuggestionService.search(service.getServiceType(), haystack, 2);
        if (suggestions.isEmpty()) {
            return;
        }
        builder.append("知识库建议:\n");
        for (KnowledgeSuggestionRecord suggestion : suggestions) {
            builder.append("- ").append(defaultIfBlank(suggestion.getTitle(), "未命名条目"));
            if (hasText(suggestion.getSummary())) {
                builder.append(" | ").append(suggestion.getSummary().trim());
            }
            builder.append('\n');
        }
    }

    private void appendOpenIncidents(StringBuilder builder, String clusterName) {
        List<IncidentEntity> incidents = new ArrayList<IncidentEntity>(incidentRepository.findAll());
        incidents.sort(Comparator.comparing(IncidentEntity::getOccurredAt, Comparator.nullsLast(Comparator.reverseOrder())));
        int count = 0;
        for (IncidentEntity incident : incidents) {
            if ("CLOSED".equalsIgnoreCase(incident.getStatus())) {
                continue;
            }
            if (hasText(clusterName) && hasText(incident.getClusterName())
                && !"UNKNOWN_CLUSTER".equals(clusterName)
                && !clusterName.equalsIgnoreCase(incident.getClusterName())) {
                continue;
            }
            builder.append("- ").append(defaultIfBlank(incident.getIncidentNo(), "NO-INCIDENT"))
                .append(" | 服务=").append(defaultIfBlank(incident.getServiceType(), "UNKNOWN"))
                .append(" | 等级=").append(defaultIfBlank(incident.getSeverity(), "UNKNOWN"))
                .append(" | 状态=").append(defaultIfBlank(incident.getStatus(), "UNKNOWN"))
                .append(" | 标题=").append(defaultIfBlank(incident.getTitle(), "无"))
                .append('\n');
            if (hasText(incident.getSummary())) {
                builder.append("  摘要: ").append(incident.getSummary().trim()).append('\n');
            }
            count++;
            if (count >= 20) {
                break;
            }
        }
        if (count == 0) {
            builder.append("- 当前没有未关闭事件。\n");
        }
    }

    private void appendRecentLogs(StringBuilder builder, List<CmServiceLogSnapshotRecord> recentLogs) {
        if (recentLogs == null || recentLogs.isEmpty()) {
            builder.append("- 当前没有最近服务日志快照。\n");
            return;
        }
        int count = 0;
        for (CmServiceLogSnapshotRecord log : recentLogs) {
            builder.append("- ").append(defaultIfBlank(log.getServiceType(), "UNKNOWN"))
                .append(" | ").append(defaultIfBlank(log.getLogType(), "UNKNOWN"))
                .append(" | ").append(defaultIfBlank(log.getLogText(), "无"))
                .append(" | 采集时间=").append(formatInstant(log.getCollectedAt()))
                .append('\n');
            count++;
            if (count >= 12) {
                break;
            }
        }
    }

    private void appendRiskTopic(StringBuilder builder,
                                 CmCurrentStatusResponse currentStatus,
                                 String topicName,
                                 String... keywords) {
        int count = 0;
        for (CmServiceStatusRecord service : currentStatus.getServices()) {
            List<String> signals = new ArrayList<String>();
            collectMatched(signals, service.getRoleHighlights(), keywords);
            collectMatched(signals, service.getLogHighlights(), keywords);
            collectMatched(signals, service.getLogPreviewLines(), keywords);
            if (signals.isEmpty()) {
                continue;
            }
            builder.append("- 服务=").append(defaultIfBlank(service.getServiceType(), "UNKNOWN"))
                .append(" | 状态=").append(defaultIfBlank(service.getHealthSummary(), "UNKNOWN"))
                .append(" | 命中证据数=").append(signals.size()).append('\n');
            for (int index = 0; index < signals.size() && index < 4; index++) {
                builder.append("  - ").append(signals.get(index)).append('\n');
            }
            count++;
        }
        if (count == 0) {
            builder.append("- 当前未发现明显的").append(topicName).append("专题风险证据。\n");
        }
    }

    private void appendItems(StringBuilder builder, String label, List<String> items, int limit) {
        if (items == null || items.isEmpty()) {
            return;
        }
        builder.append(label).append(":\n");
        int count = 0;
        for (String item : items) {
            if (!hasText(item)) {
                continue;
            }
            builder.append("- ").append(item.trim()).append('\n');
            count++;
            if (count >= limit) {
                break;
            }
        }
    }

    private void addAll(List<String> target, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        target.addAll(items);
    }

    private void collectMatched(List<String> target, List<String> items, String... keywords) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (String item : items) {
            if (!hasText(item)) {
                continue;
            }
            String normalized = normalize(item);
            if (containsAny(normalized, keywords)) {
                target.add(item.trim());
            }
        }
    }

    private boolean matchesServiceGroup(CmServiceStatusRecord service, List<String> serviceTokens) {
        String normalized = normalize(service.getServiceType()) + " " + normalize(service.getServiceName());
        for (String token : serviceTokens) {
            if (normalized.contains(token.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String formatInstant(Instant value) {
        return value == null ? "未记录" : DATE_TIME_FORMATTER.format(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }
}
