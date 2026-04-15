package com.guardian.hadoop.knowledge;

import com.guardian.hadoop.diagnosis.DiagnosisEntity;
import com.guardian.hadoop.diagnosis.DiagnosisRepository;
import com.guardian.hadoop.incident.IncidentEntity;
import com.guardian.hadoop.incident.IncidentRepository;
import com.guardian.hadoop.integration.cm.ClouderaManagerSettingsResponse;
import com.guardian.hadoop.integration.cm.ClouderaManagerSettingsService;
import com.guardian.hadoop.integration.datasource.JmxProbeResponse;
import com.guardian.hadoop.integration.datasource.JmxProbeResult;
import com.guardian.hadoop.integration.datasource.JmxProbeService;
import com.guardian.hadoop.integration.datasource.LogSourceSettingsEntity;
import com.guardian.hadoop.integration.datasource.LogSourceSettingsRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AiGuidanceService {

    private static final long LOG_SOURCE_ID = 1L;

    private final IncidentRepository incidentRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final KnowledgeSuggestionService knowledgeSuggestionService;
    private final ClouderaManagerSettingsService clouderaManagerSettingsService;
    private final LogSourceSettingsRepository logSourceSettingsRepository;
    private final JmxProbeService jmxProbeService;

    public AiGuidanceService(IncidentRepository incidentRepository,
                             DiagnosisRepository diagnosisRepository,
                             KnowledgeSuggestionService knowledgeSuggestionService,
                             ClouderaManagerSettingsService clouderaManagerSettingsService,
                             LogSourceSettingsRepository logSourceSettingsRepository,
                             JmxProbeService jmxProbeService) {
        this.incidentRepository = incidentRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.knowledgeSuggestionService = knowledgeSuggestionService;
        this.clouderaManagerSettingsService = clouderaManagerSettingsService;
        this.logSourceSettingsRepository = logSourceSettingsRepository;
        this.jmxProbeService = jmxProbeService;
    }

    public AiGuidanceRecord build(long incidentId) {
        IncidentEntity incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) {
            return null;
        }

        List<DiagnosisEntity> diagnoses = diagnosisRepository.findByIncident_IdOrderByCreatedAtDesc(incidentId);
        List<KnowledgeSuggestionRecord> suggestions = knowledgeSuggestionService.getSuggestions(incidentId);
        KnowledgeSuggestionRecord primary = suggestions.isEmpty() ? null : suggestions.get(0);

        ClouderaManagerSettingsResponse cmSettings = clouderaManagerSettingsService.getSettings();
        LogSourceSettingsEntity logSettings = logSourceSettingsRepository.findById(LOG_SOURCE_ID).orElse(null);
        JmxProbeResponse jmxResponse = jmxProbeService.testAll();
        List<JmxProbeResult> serviceJmx = jmxResponse.getResults().stream()
            .filter(result -> matchesService(incident.getServiceType(), result.getServiceType()))
            .collect(Collectors.toList());

        List<String> signalHighlights = new ArrayList<String>();
        List<String> missingSignals = new ArrayList<String>();
        List<String> jmxInsights = buildJmxInsights(serviceJmx);

        if (cmSettings.isEnabled() && cmSettings.isConfigured()) {
            signalHighlights.add("Cloudera Manager 已接入，可提供当前服务与角色状态。");
        } else {
            missingSignals.add("Cloudera Manager 未完整配置，缺少当前服务与角色状态。");
        }

        if (logSettings != null && logSettings.isEnabled() && hasText(logSettings.getBaseUrl())) {
            signalHighlights.add("日志平台已接入：" + safe(logSettings.getProviderType(), "UNKNOWN")
                + " / " + safe(logSettings.getIndexPattern(), "未设置索引"));
        } else {
            missingSignals.add("日志平台未就绪，无法补充角色日志和错误片段。");
        }

        if (serviceJmx.isEmpty()) {
            missingSignals.add("当前服务域没有登记对应的 JMX 端点。");
        } else {
            long successCount = serviceJmx.stream().filter(JmxProbeResult::isSuccess).count();
            signalHighlights.add("已匹配 " + serviceJmx.size() + " 个 JMX 端点，其中 " + successCount + " 个返回成功。");
            if (successCount == 0) {
                missingSignals.add("JMX 端点已配置，但本次没有成功返回。");
            }
        }

        if (!jmxInsights.isEmpty()) {
            signalHighlights.addAll(jmxInsights.stream().limit(2).collect(Collectors.toList()));
        }

        String synopsis = incident.getTitle() + "。当前服务域：" + safe(incident.getServiceType(), "UNKNOWN")
            + "。事件状态：" + safe(incident.getStatus(), "OPEN") + "。";
        if (primary != null) {
            synopsis += " 当前最高相关知识条目为《" + primary.getTitle() + "》。";
        }

        String probableScenario = primary != null
            ? primary.getTitle() + "。命中关键词：" + join(primary.getMatchedKeywords(), "、")
            : "当前还没有高置信度知识条目命中，请结合实时状态、角色日志和 JMX 指标继续判断。";

        List<String> confidenceReasons = new ArrayList<String>();
        int supportScore = 0;
        if (primary != null) {
            supportScore += 2;
            confidenceReasons.add("已命中知识条目：《" + primary.getTitle() + "》");
        }
        if (!diagnoses.isEmpty()) {
            supportScore += 2;
            confidenceReasons.add("存在历史诊断结果可对照。");
        }
        if (cmSettings.isEnabled() && cmSettings.isConfigured()) {
            supportScore += 1;
            confidenceReasons.add("Cloudera Manager 当前状态链路可用。");
        }
        if (!jmxInsights.isEmpty()) {
            supportScore += 1;
            confidenceReasons.add("JMX 指标链路可用。");
        }
        if (logSettings != null && logSettings.isEnabled() && hasText(logSettings.getBaseUrl())) {
            supportScore += 1;
            confidenceReasons.add("日志平台链路可用。");
        }
        if (!missingSignals.isEmpty()) {
            confidenceReasons.add("当前仍有缺失信号：" + join(missingSignals, "；"));
        }

        double confidence;
        String confidenceLabel;
        if (supportScore >= 6) {
            confidence = 0.80d;
            confidenceLabel = "高";
        } else if (supportScore >= 4) {
            confidence = 0.60d;
            confidenceLabel = "中";
        } else if (supportScore >= 2) {
            confidence = 0.40d;
            confidenceLabel = "低";
        } else {
            confidence = 0.20d;
            confidenceLabel = "很低";
        }

        List<String> evidenceHighlights = new ArrayList<String>();
        evidenceHighlights.addAll(limit(incident.getEvidence(), 4));
        evidenceHighlights.addAll(limit(jmxInsights, 2));
        if (!diagnoses.isEmpty() && hasText(diagnoses.get(0).getRootCause())) {
            evidenceHighlights.add("最近一次诊断结论：" + diagnoses.get(0).getRootCause());
        }

        List<String> recommendedOrder = primary != null
            ? limit(primary.getSteps(), 3)
            : Collections.singletonList("先确认当前角色状态、最近 ERROR/WARN 角色日志和已抓取的 JMX 指标。");

        List<String> concretePlan = buildConcretePlan(incident, diagnoses, primary, cmSettings, logSettings, serviceJmx);
        List<String> operatorNotes = new ArrayList<String>();
        if (primary != null) {
            operatorNotes.addAll(primary.getCautionItems());
            if (primary.isRequiresApproval()) {
                operatorNotes.add("当前知识路径包含受控动作，执行前需要审批。");
            }
        }
        operatorNotes.addAll(
            missingSignals.stream()
                .map(item -> "待补项：" + item)
                .collect(Collectors.toList())
        );

        List<Long> linkedKnowledgeIds = suggestions.stream()
            .map(KnowledgeSuggestionRecord::getId)
            .collect(Collectors.toList());

        String planSummary = diagnoses.isEmpty()
            ? "当前 guidance 主要基于事件上下文、知识命中和实时信号链路生成。"
            : "当前 guidance 已结合历史诊断、知识命中和实时信号链路。";

        return new AiGuidanceRecord(
            synopsis,
            probableScenario,
            confidence,
            confidenceLabel,
            "这是基于知识命中、历史诊断、CM/JMX/日志链路可用性计算的规则式置信度，不是统计概率。",
            confidenceReasons,
            signalHighlights,
            missingSignals,
            evidenceHighlights,
            recommendedOrder,
            planSummary,
            concretePlan,
            operatorNotes,
            linkedKnowledgeIds,
            jmxInsights
        );
    }

    private List<String> buildConcretePlan(IncidentEntity incident,
                                           List<DiagnosisEntity> diagnoses,
                                           KnowledgeSuggestionRecord primary,
                                           ClouderaManagerSettingsResponse cmSettings,
                                           LogSourceSettingsEntity logSettings,
                                           List<JmxProbeResult> serviceJmx) {
        List<String> steps = new ArrayList<String>();

        if (cmSettings.isEnabled() && cmSettings.isConfigured()) {
            steps.add("先确认 Cloudera Manager 中该服务的当前状态、异常角色和最新角色日志。");
        } else {
            steps.add("先补齐 Cloudera Manager 配置，否则缺少当前服务状态。");
        }

        JmxProbeResult successfulJmx = serviceJmx.stream()
            .filter(JmxProbeResult::isSuccess)
            .findFirst()
            .orElse(null);
        if (successfulJmx != null) {
            String metricSummary = !successfulJmx.getObservedMetrics().isEmpty()
                ? join(limit(successfulJmx.getObservedMetrics(), 3), "；")
                : join(limit(successfulJmx.getSampleMetrics(), 3), "；");
            steps.add("检查 " + successfulJmx.getRoleType() + "@" + successfulJmx.getTargetHost()
                + " 的 JMX 指标：" + safe(metricSummary, "未提取到具体指标值"));
        } else {
            steps.add("当前没有成功的 JMX 返回，先检查 JMX 连通性、端口和白名单。");
        }

        if (logSettings != null && logSettings.isEnabled() && hasText(logSettings.getBaseUrl())) {
            steps.add("结合日志平台检索最近时间窗口内的 ERROR/WARN，与角色日志和 JMX 指标交叉验证。");
        } else {
            steps.add("日志平台链路未就绪，需要结合角色日志或人工补充关键错误片段。");
        }

        if (primary != null) {
            steps.addAll(limit(primary.getSteps(), 2));
        } else if (hasText(incident.getServiceType())) {
            steps.add("优先围绕 " + incident.getServiceType() + " 的当前异常角色、资源瓶颈和依赖链路做排查。");
        }

        if (!diagnoses.isEmpty()) {
            steps.add("执行动作前重新核对最近诊断结论与当前实时证据是否一致。");
        } else {
            steps.add("如果证据仍不足，再发起一次人工诊断任务补全结构化结论。");
        }

        return steps.stream().distinct().limit(6).collect(Collectors.toList());
    }

    private List<String> buildJmxInsights(List<JmxProbeResult> serviceJmx) {
        List<String> insights = new ArrayList<String>();
        for (JmxProbeResult result : serviceJmx) {
            if (!result.isSuccess()) {
                insights.add(result.getRoleType() + "@" + result.getTargetHost() + " JMX 抓取失败：" + result.getMessage());
                continue;
            }
            List<String> metrics = !result.getObservedMetrics().isEmpty()
                ? limit(result.getObservedMetrics(), 4)
                : limit(result.getSampleMetrics(), 3);
            String suffix = metrics.isEmpty()
                ? "未返回可解释的指标值"
                : join(metrics, "；");
            insights.add(result.getRoleType() + "@" + result.getTargetHost()
                + " Bean 数=" + result.getBeanCount()
                + "，指标摘要：" + suffix);
        }
        return insights;
    }

    private boolean matchesService(String incidentServiceType, String jmxServiceType) {
        String left = safe(incidentServiceType, "").toUpperCase(Locale.ROOT);
        String right = safe(jmxServiceType, "").toUpperCase(Locale.ROOT);
        if (left.equals(right)) {
            return true;
        }
        if ("HIVE_ON_TEZ".equals(left)) {
            return right.contains("HIVE") || right.contains("TEZ");
        }
        return false;
    }

    private List<String> limit(List<String> values, int maxSize) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream()
            .filter(this::hasText)
            .limit(maxSize)
            .collect(Collectors.toList());
    }

    private String join(List<String> values, String delimiter) {
        List<String> clean = limit(values, Integer.MAX_VALUE);
        return clean.isEmpty() ? "" : String.join(delimiter, clean);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }
}
