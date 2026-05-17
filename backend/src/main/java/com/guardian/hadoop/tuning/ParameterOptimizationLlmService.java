package com.guardian.hadoop.tuning;

import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsEntity;
import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsService;
import com.guardian.hadoop.integration.llm.LlmCallRecordService;
import com.guardian.hadoop.knowledge.KnowledgeSuggestionRecord;
import com.guardian.hadoop.knowledge.KnowledgeSuggestionService;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
public class ParameterOptimizationLlmService {

    private static final Logger logger = LoggerFactory.getLogger(ParameterOptimizationLlmService.class);
    private static final Pattern SECTION_PATTERN = Pattern.compile("^【([^】]+)】\\s*$", Pattern.MULTILINE);
    private static final int MAX_CONFIG_PROMPT_CHARS = 14000;
    private static final int MAX_SIGNAL_COUNT = 10;
    private static final int MAX_SIGNAL_CHARS = 300;
    private static final int MAX_KNOWLEDGE_SUMMARY = 220;

    private final DiagnosisLlmSettingsService settingsService;
    private final KnowledgeSuggestionService knowledgeSuggestionService;
    private final LlmCallRecordService llmCallRecordService;

    public ParameterOptimizationLlmService(DiagnosisLlmSettingsService settingsService,
                                           KnowledgeSuggestionService knowledgeSuggestionService,
                                           LlmCallRecordService llmCallRecordService) {
        this.settingsService = settingsService;
        this.knowledgeSuggestionService = knowledgeSuggestionService;
        this.llmCallRecordService = llmCallRecordService;
    }

    public ParameterOptimizationLlmOutcome analyze(ParameterOptimizationRequest request,
                                                   ParameterOptimizationContextPreview context,
                                                   ParameterOptimizationRuleAnalysis ruleAnalysis) {
        DiagnosisLlmSettingsEntity settings = settingsService.getEffectiveSettings();
        boolean configured = settings.isEnabled()
            && hasText(settings.getEndpoint())
            && hasText(settings.getApiKey())
            && hasText(settings.getModel());
        if (!configured) {
            return fallback(ruleAnalysis, context, request, "RULE_ONLY", null, "大模型未启用或配置不完整，已回退到规则分析结果。");
        }

        String prompt = buildPrompt(request, context, ruleAnalysis);
        Long callRecordId = llmCallRecordService.start("PARAMETER_OPTIMIZATION", settings.getModel(), prompt);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(settings.getApiKey().trim());

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("model", settings.getModel().trim());
            payload.put("stream", Boolean.FALSE);
            payload.put("temperature", settings.getTemperature());
            int maxTokens = effectiveMaxTokens(settings);
            if (maxTokens > 0) {
                payload.put("max_tokens", maxTokens);
            }
            payload.put("messages", buildMessages(prompt));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = createRestTemplate(settings).postForObject(
                settings.getEndpoint().trim(),
                new HttpEntity<Map<String, Object>>(payload, headers),
                Map.class
            );
            String content = extractContent(response);
            if (!hasText(content)) {
                llmCallRecordService.fail(callRecordId, "empty model response");
                return fallback(ruleAnalysis, context, request, "RULE_FALLBACK", settings.getModel(), "大模型调用成功，但没有返回可解析内容。");
            }
            llmCallRecordService.finish(callRecordId, content);
            return parseContent(content, settings.getModel(), ruleAnalysis, context, request);
        } catch (RestClientResponseException exception) {
            logger.warn("Parameter optimization request failed", exception);
            llmCallRecordService.fail(callRecordId, "HTTP " + exception.getRawStatusCode() + " " + exception.getStatusText());
            return fallback(
                ruleAnalysis,
                context,
                request,
                "RULE_FALLBACK",
                settings.getModel(),
                "大模型参数优化请求失败，HTTP " + exception.getRawStatusCode() + " " + exception.getStatusText() + "。"
            );
        } catch (ResourceAccessException exception) {
            logger.warn("Parameter optimization request timed out or was unreachable", exception);
            llmCallRecordService.fail(callRecordId, defaultIfBlank(exception.getMessage(), "resource access error"));
            Throwable root = exception.getMostSpecificCause();
            String reason = root instanceof SocketTimeoutException
                ? "大模型参数优化超时，已回退到规则分析结果。"
                : defaultIfBlank(exception.getMessage(), "大模型参数优化调用失败。");
            return fallback(ruleAnalysis, context, request, "RULE_FALLBACK", settings.getModel(), reason);
        } catch (Exception exception) {
            logger.warn("Failed to generate parameter optimization", exception);
            llmCallRecordService.fail(callRecordId, exception.getClass().getSimpleName() + ": " + defaultIfBlank(exception.getMessage(), "unknown error"));
            return fallback(
                ruleAnalysis,
                context,
                request,
                "RULE_FALLBACK",
                settings.getModel(),
                defaultIfBlank(exception.getMessage(), "大模型参数优化结果解析失败。")
            );
        }
    }

    private ParameterOptimizationLlmOutcome parseContent(String content,
                                                         String llmModel,
                                                         ParameterOptimizationRuleAnalysis ruleAnalysis,
                                                         ParameterOptimizationContextPreview context,
                                                         ParameterOptimizationRequest request) {
        try {
            Map<String, String> sections = parseSections(sanitizeStructuredText(content));
            return new ParameterOptimizationLlmOutcome(
                true,
                defaultIfBlank(sections.get("参数问题总结"), buildFallbackSummary(ruleAnalysis, context, request, null)),
                parseRecommendations(sections.get("建议调整项"), context, ruleAnalysis),
                parseBullets(sections.get("为什么这样优化"), 10, fallbackEvidence(context)),
                parseBullets(sections.get("预期收益"), 8, fallbackBenefits(ruleAnalysis)),
                parseBullets(sections.get("风险提示"), 8, fallbackRisks(request)),
                parseBullets(sections.get("验证步骤"), 8, fallbackValidation()),
                llmModel,
                "LLM"
            );
        } catch (Exception exception) {
            logger.warn("Failed to parse parameter optimization structured text, falling back to rule analysis", exception);
            return fallback(ruleAnalysis, context, request, "RULE_FALLBACK", llmModel, "大模型返回格式不符合约束，已回退到规则分析结果。");
        }
    }

    private ParameterOptimizationLlmOutcome fallback(ParameterOptimizationRuleAnalysis ruleAnalysis,
                                                     ParameterOptimizationContextPreview context,
                                                     ParameterOptimizationRequest request,
                                                     String analysisSource,
                                                     String llmModel,
                                                     String reason) {
        List<ParameterRecommendationRecord> recommendations = new ArrayList<ParameterRecommendationRecord>();
        Map<String, String> currentConfigs = context == null ? Collections.<String, String>emptyMap() : context.getConfigEntries();
        for (String parameter : ruleAnalysis.getFocusParameters()) {
            recommendations.add(new ParameterRecommendationRecord(
                parameter,
                defaultIfBlank(currentConfigs.get(parameter), "未采集"),
                defaultIfBlank(ruleAnalysis.getProposedValues().get(parameter), "建议结合当前版本源码与日志进一步评估"),
                "当前先基于规则分析给出保守建议，建议结合版本源码、现网症状和测试验证后再调整。"
            ));
            if (recommendations.size() >= 6) {
                break;
            }
        }
        if (recommendations.isEmpty()) {
            recommendations.add(new ParameterRecommendationRecord(
                "请先补充更多配置与症状",
                "未知",
                "补充当前配置、源码线索或明确症状",
                "当前证据不足，无法安全给出具体参数值。"
            ));
        }
        List<String> riskNotes = new ArrayList<String>(fallbackRisks(request));
        if (hasText(reason)) {
            riskNotes.add(reason);
        }
        return new ParameterOptimizationLlmOutcome(
            false,
            buildFallbackSummary(ruleAnalysis, context, request, reason),
            recommendations,
            fallbackEvidence(context),
            fallbackBenefits(ruleAnalysis),
            riskNotes,
            fallbackValidation(),
            llmModel,
            analysisSource
        );
    }

    private String buildPrompt(ParameterOptimizationRequest request,
                               ParameterOptimizationContextPreview context,
                               ParameterOptimizationRuleAnalysis ruleAnalysis) {
        String serviceType = normalizeServiceType(request.getServiceType());
        List<KnowledgeSuggestionRecord> suggestions = collectKnowledge(serviceType, request, context);
        StringBuilder builder = new StringBuilder();
        builder.append("请针对以下 Hadoop/CDP 组件参数做优化分析。不要返回 JSON，不要返回 Markdown 代码块。\n");
        builder.append("集群名称: ").append(defaultIfBlank(context == null ? null : context.getClusterName(), "未知")).append('\n');
        builder.append("服务类型: ").append(serviceType).append('\n');
        builder.append("服务名称: ").append(defaultIfBlank(context == null ? null : context.getServiceName(), "未知")).append('\n');
        builder.append("组件版本: ").append(defaultIfBlank(context == null ? null : context.getComponentVersion(), "未知")).append('\n');
        builder.append("当前服务状态: ")
            .append(defaultIfBlank(context == null ? null : context.getServiceState(), "未知"))
            .append(" / ")
            .append(defaultIfBlank(context == null ? null : context.getHealthSummary(), "未知"))
            .append('\n');
        builder.append("优化目标: ").append(defaultIfBlank(request.getOptimizationGoal(), "提升组件稳定性、资源利用率与故障恢复能力")).append('\n');
        builder.append("当前症状: ").append(defaultIfBlank(request.getCurrentSymptoms(), "未补充")).append("\n\n");

        builder.append("【当前组件配置参数】\n");
        appendConfigEntries(builder, context == null ? null : context.getScopedConfigEntries());

        builder.append("\n【最近服务日志信号】\n");
        appendList(builder, context == null ? null : context.getRecentSignals(), MAX_SIGNAL_COUNT, MAX_SIGNAL_CHARS);

        builder.append("\n【规则预分析】\n");
        builder.append("风险等级: ").append(defaultIfBlank(ruleAnalysis.getRiskLevel(), "UNKNOWN")).append('\n');
        builder.append("当前发现:\n");
        appendList(builder, ruleAnalysis.getFindings(), 12, 220);
        builder.append("重点关注参数:\n");
        appendList(builder, ruleAnalysis.getFocusParameters(), 12, 140);

        builder.append("\n【管理员补充的源码线索】\n");
        builder.append(defaultIfBlank(request.getSourceCodeHints(), "无")).append('\n');
        builder.append("\n【管理员补充的运维说明】\n");
        builder.append(defaultIfBlank(request.getManualConfigNote(), "无")).append('\n');

        if (!suggestions.isEmpty()) {
            builder.append("\n【知识库命中】\n");
            for (KnowledgeSuggestionRecord suggestion : suggestions) {
                builder.append("- 标题: ").append(defaultIfBlank(suggestion.getTitle(), "未命名"))
                    .append(" | 领域: ").append(defaultIfBlank(suggestion.getDomain(), "UNKNOWN"))
                    .append(" | 摘要: ").append(truncate(defaultIfBlank(suggestion.getSummary(), "无"), MAX_KNOWLEDGE_SUMMARY))
                    .append('\n');
            }
        }

        builder.append("\n【分析策略】\n");
        appendServiceSpecificStrategies(builder, serviceType);
        builder.append("- 必须先判断当前现象是否真的由参数导致；如果更像网络、SQL、数据质量、容量规划或上下游组件问题，要明确指出，不要强行给参数建议。\n");
        builder.append("- 建议调整项必须写明参数名、当前值、建议值或建议方向，以及为什么这样调整。\n");
        builder.append("- 调参依据必须尽量引用当前配置、当前日志、管理员提供的源码线索和知识库内容。\n");
        builder.append("- 如果当前证据不足以给出具体值，应写成“建议方向/建议范围”，不要编造确定值。\n");
        builder.append("- 输出必须使用简体中文，不能返回 JSON，不能返回 Markdown 代码块。\n");
        builder.append("- 必须严格按以下 6 个章节输出：\n");
        builder.append("【参数问题总结】\n【建议调整项】\n【为什么这样优化】\n【预期收益】\n【风险提示】\n【验证步骤】\n");
        builder.append("- 【建议调整项】章节每一行必须使用固定格式：参数名 | 当前值 | 建议值 | 调整原因\n");
        return builder.toString();
    }

    private void appendServiceSpecificStrategies(StringBuilder builder, String serviceType) {
        if ("HDFS".equals(serviceType)) {
            builder.append("- 重点从 NameNode handler、DataNode 传输线程、客户端 socket timeout、副本链路和 block 处理线程角度分析。\n");
            builder.append("- 如果日志体现 PacketResponder、Slow BlockReceiver、mirror timeout、version mismatch，先判断是否链路或协议异常，再评估参数上限是否偏小。\n");
        } else if ("YARN".equals(serviceType)) {
            builder.append("- 重点从 scheduler、queue、allocation、NodeManager 总资源、AM 资源占比和 pending application 积压角度分析。\n");
            builder.append("- 先区分是参数上限不协调，还是资源池规划、队列模型或作业申请方式本身有问题。\n");
        } else if ("HIVE_ON_TEZ".equals(serviceType)) {
            builder.append("- 重点从 Tez 容器大小、并行度、reducer 划分、auto convert join 和向量化参数角度分析。\n");
            builder.append("- 如果更适合先优化 SQL、文件布局或数据倾斜处理，要明确指出，不要只给参数建议。\n");
        } else if ("IMPALA".equals(serviceType)) {
            builder.append("- 重点从 admission control、查询超时、mem_limit、scratch 使用、buffer/thread 和并发资源边界角度分析。\n");
            builder.append("- 如果日志明显指向 SQL 模式或 HDFS/YARN 上下游问题，要说明参数优化不是唯一解。\n");
        } else {
            builder.append("- 围绕当前组件最关键的线程、队列、内存、超时和并发参数给出建议。\n");
        }
    }

    private List<KnowledgeSuggestionRecord> collectKnowledge(String serviceType,
                                                             ParameterOptimizationRequest request,
                                                             ParameterOptimizationContextPreview context) {
        List<KnowledgeSuggestionRecord> all = new ArrayList<KnowledgeSuggestionRecord>();
        String question = defaultIfBlank(request.getCurrentSymptoms(), "")
            + "\n" + defaultIfBlank(request.getManualConfigNote(), "")
            + "\n" + defaultIfBlank(request.getSourceCodeHints(), "")
            + "\n" + flattenScopedConfig(context == null ? null : context.getScopedConfigEntries());
        for (String domain : knowledgeDomains(serviceType)) {
            all.addAll(knowledgeSuggestionService.search(domain, question, 3));
        }
        return all;
    }

    private List<String> knowledgeDomains(String serviceType) {
        if ("HDFS".equals(serviceType)) {
            return Arrays.asList("HDFS_PARAM", "HDFS_SOURCE");
        }
        if ("YARN".equals(serviceType)) {
            return Arrays.asList("YARN_PARAM", "YARN_SOURCE");
        }
        if ("HIVE_ON_TEZ".equals(serviceType)) {
            return Arrays.asList("HIVE_PARAM", "HIVE_SOURCE");
        }
        if ("IMPALA".equals(serviceType)) {
            return Arrays.asList("IMPALA_PARAM", "IMPALA_SOURCE");
        }
        return Arrays.asList(serviceType + "_PARAM", serviceType + "_SOURCE");
    }

    private Map<String, String> parseSections(String content) {
        Map<String, String> sections = new LinkedHashMap<String, String>();
        Matcher matcher = SECTION_PATTERN.matcher(content);
        List<Integer> starts = new ArrayList<Integer>();
        List<String> titles = new ArrayList<String>();
        while (matcher.find()) {
            starts.add(matcher.start());
            titles.add(matcher.group(1).trim());
        }
        if (starts.isEmpty()) {
            throw new IllegalArgumentException("No structured sections found in parameter optimization response");
        }
        for (int index = 0; index < starts.size(); index++) {
            int start = starts.get(index);
            int contentStart = content.indexOf('\n', start);
            contentStart = contentStart < 0 ? content.length() : contentStart + 1;
            int end = index + 1 < starts.size() ? starts.get(index + 1) : content.length();
            sections.put(titles.get(index), content.substring(contentStart, end).trim());
        }
        return sections;
    }

    private List<ParameterRecommendationRecord> parseRecommendations(String sectionText,
                                                                     ParameterOptimizationContextPreview context,
                                                                     ParameterOptimizationRuleAnalysis ruleAnalysis) {
        List<ParameterRecommendationRecord> result = new ArrayList<ParameterRecommendationRecord>();
        if (hasText(sectionText)) {
            for (String rawLine : sectionText.split("\\R+")) {
                String line = normalizeBullet(rawLine);
                if (!hasText(line)) {
                    continue;
                }
                String[] parts = line.split("\\|", 4);
                if (parts.length < 4) {
                    continue;
                }
                result.add(new ParameterRecommendationRecord(parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim()));
                if (result.size() >= 10) {
                    break;
                }
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        Map<String, String> currentConfigs = context == null ? Collections.<String, String>emptyMap() : context.getConfigEntries();
        for (String focusParameter : ruleAnalysis.getFocusParameters()) {
            result.add(new ParameterRecommendationRecord(
                focusParameter,
                defaultIfBlank(currentConfigs.get(focusParameter), "未采集"),
                defaultIfBlank(ruleAnalysis.getProposedValues().get(focusParameter), "建议进一步评估"),
                "大模型未返回标准化建议，先保留规则分析给出的重点参数。"
            ));
            if (result.size() >= 6) {
                break;
            }
        }
        return result;
    }

    private List<String> parseBullets(String sectionText, int limit, List<String> fallback) {
        List<String> values = new ArrayList<String>();
        if (hasText(sectionText)) {
            for (String rawLine : sectionText.split("\\R+")) {
                String line = normalizeBullet(rawLine);
                if (hasText(line)) {
                    values.add(line);
                }
                if (values.size() >= limit) {
                    break;
                }
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private String normalizeBullet(String rawLine) {
        if (!hasText(rawLine)) {
            return "";
        }
        return rawLine.trim().replaceFirst("^[\\-•·\\d\\.\\s]+", "").trim();
    }

    private List<String> fallbackEvidence(ParameterOptimizationContextPreview context) {
        List<String> evidence = new ArrayList<String>();
        if (context != null && hasText(context.getComponentVersion())) {
            evidence.add("当前组件版本: " + context.getComponentVersion());
        }
        if (context != null && context.getScopedConfigEntries() != null && !context.getScopedConfigEntries().isEmpty()) {
            int count = 0;
            for (ParameterConfigEntryRecord entry : context.getScopedConfigEntries()) {
                evidence.add(renderScope(entry) + " | " + entry.getConfigKey() + "=" + entry.getConfigValue());
                if (++count >= 5) {
                    break;
                }
            }
        }
        if (context != null && context.getRecentSignals() != null) {
            for (String signal : context.getRecentSignals()) {
                evidence.add(signal);
                if (evidence.size() >= 8) {
                    break;
                }
            }
        }
        if (evidence.isEmpty()) {
            evidence.add("当前仅基于规则分析与手工输入做参数建议。");
        }
        return evidence;
    }

    private List<String> fallbackBenefits(ParameterOptimizationRuleAnalysis ruleAnalysis) {
        List<String> benefits = new ArrayList<String>();
        benefits.add("先把分析范围收敛到当前版本、当前日志和当前配置真正相关的参数，避免无关调参。");
        if (!ruleAnalysis.getFocusParameters().isEmpty()) {
            List<String> focus = ruleAnalysis.getFocusParameters().subList(0, Math.min(3, ruleAnalysis.getFocusParameters().size()));
            benefits.add("优先聚焦 " + String.join("、", focus) + " 等重点参数。");
        }
        return benefits;
    }

    private List<String> fallbackRisks(ParameterOptimizationRequest request) {
        List<String> risks = new ArrayList<String>();
        risks.add("参数调整只给建议，不应直接在生产集群执行，必须先在测试或灰度范围验证。");
        if (!hasText(request.getSourceCodeHints())) {
            risks.add("当前没有补充明确的源码片段，建议后续补充版本实现依据，避免对默认值或生效路径误判。");
        }
        return risks;
    }

    private List<String> fallbackValidation() {
        return Arrays.asList(
            "先记录当前参数值、服务状态和关键症状，确保存在明确回滚点。",
            "灰度调整后持续观察服务日志、JMX 指标和业务现象是否改善。",
            "确认没有引入新的超时、内存或并发副作用后，再考虑扩大调整范围。"
        );
    }

    private String buildFallbackSummary(ParameterOptimizationRuleAnalysis ruleAnalysis,
                                        ParameterOptimizationContextPreview context,
                                        ParameterOptimizationRequest request,
                                        String reason) {
        StringBuilder builder = new StringBuilder();
        builder.append("已完成 ").append(normalizeServiceType(request.getServiceType())).append(" 参数规则分析。");
        builder.append("当前风险等级为 ").append(defaultIfBlank(ruleAnalysis.getRiskLevel(), "UNKNOWN")).append("。");
        if (context != null && hasText(context.getComponentVersion())) {
            builder.append("已采集组件版本 ").append(context.getComponentVersion()).append("。");
        }
        if (!ruleAnalysis.getFindings().isEmpty()) {
            builder.append("当前重点问题包括: ")
                .append(String.join("；", ruleAnalysis.getFindings().subList(0, Math.min(2, ruleAnalysis.getFindings().size()))))
                .append("。");
        }
        if (hasText(reason)) {
            builder.append(reason);
        }
        return builder.toString();
    }

    private List<Map<String, String>> buildMessages(String prompt) {
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        messages.add(message("system", buildSystemPrompt()));
        messages.add(message("user", prompt));
        return messages;
    }

    private String buildSystemPrompt() {
        return "你是 CDP / Hadoop 平台的资深参数优化专家，专门分析 HDFS、YARN、Hive on Tez 和 Impala 的参数调优问题。"
            + "你必须使用简体中文回答，不要返回 JSON，不要返回 Markdown 代码块，也不要暴露推理过程。"
            + "你必须优先依据输入中的当前版本、当前配置、当前服务日志、管理员补充的源码线索和知识库依据来判断，不允许臆测未提供的实现细节。"
            + "如果更可能是网络、SQL、数据质量、资源规划或上下游组件问题，而不是参数本身问题，也必须明确指出。"
            + "你必须严格按以下 6 个章节输出: 【参数问题总结】【建议调整项】【为什么这样优化】【预期收益】【风险提示】【验证步骤】。"
            + "【建议调整项】章节每一行必须使用固定格式: 参数名 | 当前值 | 建议值 | 调整原因。";
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<String, String>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String flattenScopedConfig(List<ParameterConfigEntryRecord> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ParameterConfigEntryRecord entry : entries) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(renderScope(entry))
                .append(" | ")
                .append(entry.getConfigKey())
                .append('=')
                .append(entry.getConfigValue());
        }
        return builder.toString();
    }

    private void appendConfigEntries(StringBuilder builder, List<ParameterConfigEntryRecord> entries) {
        if (entries == null || entries.isEmpty()) {
            builder.append("- 当前没有可用的 Cloudera Manager 组件配置快照，请结合手工说明和源码线索分析。\n");
            return;
        }
        int total = entries.size();
        int count = 0;
        int startLength = builder.length();
        for (ParameterConfigEntryRecord entry : entries) {
            String line = "- " + renderScope(entry)
                + " | " + entry.getConfigKey()
                + " = " + truncate(entry.getConfigValue(), 260)
                + " | 来源=" + defaultIfBlank(entry.getValueSource(), "UNKNOWN")
                + "\n";
            if (builder.length() - startLength + line.length() > MAX_CONFIG_PROMPT_CHARS) {
                break;
            }
            builder.append(line);
            count++;
        }
        if (count < total) {
            builder.append("- 其余 ").append(total - count).append(" 个配置项已省略，页面中仍可完整展示。\n");
        }
    }

    private String renderScope(ParameterConfigEntryRecord entry) {
        if (entry == null) {
            return "未知作用域";
        }
        if ("ROLE_CONFIG_GROUP".equalsIgnoreCase(entry.getScopeType())) {
            return defaultIfBlank(entry.getRoleType(), "ROLE") + "@" + defaultIfBlank(entry.getScopeName(), "未知配置组");
        }
        return "SERVICE@" + defaultIfBlank(entry.getScopeName(), "未知服务");
    }

    private void appendList(StringBuilder builder, List<String> values, int limit, int maxItemLength) {
        if (values == null || values.isEmpty()) {
            builder.append("- 无\n");
            return;
        }
        int count = 0;
        for (String value : values) {
            builder.append("- ").append(truncate(value, maxItemLength)).append('\n');
            if (++count >= limit) {
                break;
            }
        }
    }

    private String extractContent(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object choicesObject = response.get("choices");
        if (!(choicesObject instanceof List) || ((List<?>) choicesObject).isEmpty()) {
            return null;
        }
        Object firstChoice = ((List<?>) choicesObject).get(0);
        if (!(firstChoice instanceof Map)) {
            return null;
        }
        Object messageObject = ((Map<?, ?>) firstChoice).get("message");
        if (!(messageObject instanceof Map)) {
            return null;
        }
        Map<?, ?> message = (Map<?, ?>) messageObject;
        String content = extractTextValue(message.get("content"));
        if (hasText(content)) {
            return content;
        }
        content = extractTextValue(message.get("reasoning_content"));
        if (hasText(content)) {
            return content;
        }
        content = extractTextValue(message.get("text"));
        if (hasText(content)) {
            return content;
        }
        return extractTextValue(message.get("output_text"));
    }

    private String extractTextValue(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof List) {
            StringBuilder builder = new StringBuilder();
            for (Object item : (List<?>) value) {
                String text = extractTextValue(item);
                if (hasText(text)) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append(text.trim());
                }
            }
            return builder.toString();
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            String text = extractTextValue(map.get("text"));
            if (hasText(text)) {
                return text;
            }
            text = extractTextValue(map.get("content"));
            if (hasText(text)) {
                return text;
            }
            return extractTextValue(map.get("output_text"));
        }
        return null;
    }

    private RestTemplate createRestTemplate(DiagnosisLlmSettingsEntity settings) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(settings.getConnectTimeoutMs(), 10000));
        factory.setReadTimeout(Math.max(settings.getReadTimeoutMs(), 180000));
        return new RestTemplate(factory);
    }

    private int effectiveMaxTokens(DiagnosisLlmSettingsEntity settings) {
        int maxTokens = settings.getMaxTokens();
        if (maxTokens <= 0 || maxTokens == 2048) {
            return 0;
        }
        return maxTokens;
    }

    private String sanitizeStructuredText(String value) {
        return value == null ? "" : value.replace("```", "").trim();
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

    private String truncate(String value, int maxLength) {
        if (!hasText(value) || value.length() <= maxLength) {
            return defaultIfBlank(value, "");
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
