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
            .filter(result -> incident.getServiceType().equalsIgnoreCase(result.getServiceType()))
            .collect(Collectors.toList());

        List<String> signalHighlights = new ArrayList<String>();
        List<String> missingSignals = new ArrayList<String>();
        List<String> jmxInsights = buildJmxInsights(serviceJmx, missingSignals);

        if (cmSettings.isEnabled() && cmSettings.isConfigured()) {
            signalHighlights.add("Cloudera Manager 已接入，可为当前事件提供告警上下文。");
        } else {
            missingSignals.add("Cloudera Manager 尚未完整配置，缺少实时告警上下文。");
        }

        if (logSettings != null && logSettings.isEnabled() && hasText(logSettings.getBaseUrl())) {
            signalHighlights.add("日志采集已配置：" + safe(logSettings.getProviderType()) + " / " + safe(logSettings.getIndexPattern()));
        } else {
            missingSignals.add("日志采集尚未就绪，无法自动拉取角色日志和错误片段。");
        }

        if (serviceJmx.isEmpty()) {
            missingSignals.add("当前服务域没有登记对应的 JMX 端点。");
        } else {
            long successCount = serviceJmx.stream().filter(JmxProbeResult::isSuccess).count();
            signalHighlights.add("共匹配到 " + serviceJmx.size() + " 个 JMX 端点，其中 " + successCount + " 个返回成功。");
            if (successCount == 0) {
                missingSignals.add("已配置 JMX 端点，但全部探测失败。请检查网络、认证或路径设置。");
            }
        }

        String synopsis = incident.getTitle() + "。当前服务域：" + incident.getServiceType() + "。事件状态：" + incident.getStatus() + "。";
        if (primary != null) {
            synopsis += " 当前首条关联知识为：" + primary.getTitle() + "。";
        } else {
            synopsis += " 当前还没有高置信度知识条目命中。";
        }

        String probableScenario = primary != null
            ? primary.getTitle() + "。命中关键词：" + String.join("、", primary.getMatchedKeywords())
            : "当前还没有明确命中的场景，请先补充更多证据后再继续诊断。";

        List<String> confidenceReasons = new ArrayList<String>();
        int supportScore = 0;
        if (primary != null) {
            supportScore += 2;
            confidenceReasons.add("已命中知识条目：" + primary.getTitle());
            if (!primary.getMatchedKeywords().isEmpty()) {
                supportScore += 1;
                confidenceReasons.add("命中关键词：" + String.join("、", primary.getMatchedKeywords()));
            }
        }
        if (!diagnoses.isEmpty()) {
            supportScore += 2;
            confidenceReasons.add("已存在人工诊断结果，可作为补充支撑。");
        }
        if (cmSettings.isEnabled() && cmSettings.isConfigured()) {
            supportScore += 1;
            confidenceReasons.add("Cloudera Manager 信号上下文可用。");
        }
        if (logSettings != null && logSettings.isEnabled() && hasText(logSettings.getBaseUrl())) {
            supportScore += 1;
            confidenceReasons.add("日志采集链路已配置。");
        }
        if (!serviceJmx.isEmpty() && serviceJmx.stream().anyMatch(JmxProbeResult::isSuccess)) {
            supportScore += 1;
            confidenceReasons.add("运行态 JMX 信号可用。");
        }
        if (!missingSignals.isEmpty()) {
            confidenceReasons.add("仍存在缺失信号：" + String.join("；", missingSignals));
        }

        double confidence;
        String confidenceLabel;
        if (supportScore >= 6) {
            confidence = 0.8d;
            confidenceLabel = "高";
        } else if (supportScore >= 4) {
            confidence = 0.6d;
            confidenceLabel = "中";
        } else if (supportScore >= 2) {
            confidence = 0.4d;
            confidenceLabel = "低";
        } else {
            confidence = 0.2d;
            confidenceLabel = "很低";
        }

        List<String> evidenceHighlights = new ArrayList<String>();
        evidenceHighlights.addAll(incident.getEvidence().stream().limit(4).collect(Collectors.toList()));
        if (!diagnoses.isEmpty()) {
            evidenceHighlights.add("最近诊断：" + diagnoses.get(0).getRootCause());
        }
        evidenceHighlights.addAll(jmxInsights.stream().limit(2).collect(Collectors.toList()));

        List<String> recommendedOrder = primary != null
            ? primary.getSteps().stream().limit(3).collect(Collectors.toList())
            : Collections.singletonList("优先补采 Cloudera Manager 告警上下文、JMX 指标和服务日志。");

        List<String> concretePlan = buildConcretePlan(incident, diagnoses, primary, cmSettings, logSettings, serviceJmx);
        String planSummary = diagnoses.isEmpty()
            ? "当前 guidance 主要基于事件记录、知识命中和采集链路就绪情况生成，建议先补证据再发起人工诊断。"
            : "当前 guidance 已结合人工诊断结果、知识命中和实时采集状态。";

        List<String> operatorNotes = new ArrayList<String>();
        if (primary != null) {
            operatorNotes.addAll(primary.getCautionItems());
            if (primary.isRequiresApproval()) {
                operatorNotes.add("该路径包含受控动作，执行前必须完成审批。");
            }
        } else {
            operatorNotes.add("当前 AI guidance 仍缺少足够上下文，请先补证据后再评估。");
        }
        operatorNotes.addAll(
            missingSignals.stream()
                .map(value -> "待补项：" + value)
                .collect(Collectors.toList())
        );

        List<Long> linkedKnowledgeIds = suggestions.stream()
            .map(KnowledgeSuggestionRecord::getId)
            .collect(Collectors.toList());

        return new AiGuidanceRecord(
            synopsis,
            probableScenario,
            confidence,
            confidenceLabel,
            "这是规则式置信度分值，不是统计概率；它由知识命中、人工诊断上下文，以及 CM、日志、JMX 信号完整度共同决定。",
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
            steps.add("先查看最新的 Cloudera Manager 告警、角色健康状态和事件时间线，确认异常是否仍在持续。");
        } else {
            steps.add("优先完成 Cloudera Manager 接入，否则无法获取实时告警和角色健康上下文。");
        }

        JmxProbeResult successfulJmx = serviceJmx.stream().filter(JmxProbeResult::isSuccess).findFirst().orElse(null);
        if (successfulJmx != null) {
            steps.add("检查 " + successfulJmx.getRoleType() + "@" + successfulJmx.getTargetHost()
                + " 的 JMX 返回结果，重点关注异常波动的核心指标。");
        } else {
            steps.add("当前还没有有效的 JMX 返回，请先修复 JMX 连通性或认证问题。");
        }

        if (logSettings != null && logSettings.isEnabled() && hasText(logSettings.getBaseUrl())) {
            steps.add("从已配置的日志平台拉取 " + incident.getServiceType() + " 最近时间窗口内的关键日志片段，用于增强根因证据。");
        } else {
            steps.add("日志源尚未就绪，当前应优先恢复角色日志采集链路。");
        }

        if (primary != null) {
            steps.addAll(primary.getSteps().stream().limit(2).collect(Collectors.toList()));
            if (primary.isRequiresApproval()) {
                steps.add("该路径涉及受控动作，执行前必须完成审批确认。");
            }
        }

        if (!diagnoses.isEmpty()) {
            steps.add("已存在人工诊断结果，执行动作前请重新核对最新诊断、影响范围和禁做动作。");
        } else {
            steps.add("当前还没有人工诊断结果，如果证据仍不足，请先发起人工诊断。");
        }

        return steps.stream().distinct().limit(6).collect(Collectors.toList());
    }

    private List<String> buildJmxInsights(List<JmxProbeResult> serviceJmx, List<String> missingSignals) {
        List<String> insights = new ArrayList<String>();
        if (serviceJmx.isEmpty()) {
            return insights;
        }
        for (JmxProbeResult result : serviceJmx) {
            if (!result.isSuccess()) {
                insights.add(result.getRoleType() + "@" + result.getTargetHost() + " 抓取失败：" + result.getMessage());
                continue;
            }
            String samples = result.getSampleMetrics() == null || result.getSampleMetrics().isEmpty()
                ? "未返回样例 Bean"
                : String.join("；", result.getSampleMetrics().stream().limit(3).collect(Collectors.toList()));
            insights.add(result.getRoleType() + "@" + result.getTargetHost()
                + " Bean 数：" + result.getBeanCount()
                + "，样例指标：" + samples);
        }
        return insights;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return hasText(value) ? value : "未知";
    }
}
