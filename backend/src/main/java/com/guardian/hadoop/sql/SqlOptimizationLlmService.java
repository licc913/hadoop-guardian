package com.guardian.hadoop.sql;

import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsEntity;
import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsService;
import com.guardian.hadoop.knowledge.KnowledgeSuggestionRecord;
import com.guardian.hadoop.knowledge.KnowledgeSuggestionService;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
public class SqlOptimizationLlmService {

    private static final Logger logger = LoggerFactory.getLogger(SqlOptimizationLlmService.class);
    private static final int SQL_TEXT_LIMIT = 4200;
    private static final int SCHEMA_NOTE_LIMIT = 1200;
    private static final int PARTITION_INFO_LIMIT = 360;
    private static final int EXPLAIN_TEXT_LIMIT = 1800;
    private static final int ERROR_TEXT_LIMIT = 900;
    private static final int KNOWLEDGE_SUMMARY_LIMIT = 140;
    private static final Pattern SECTION_PATTERN = Pattern.compile(
        "^【(问题总结|优化后SQL|优化点|风险提示|验证步骤)】\\s*$",
        Pattern.MULTILINE
    );

    private final DiagnosisLlmSettingsService settingsService;
    private final KnowledgeSuggestionService knowledgeSuggestionService;

    public SqlOptimizationLlmService(DiagnosisLlmSettingsService settingsService,
                                     KnowledgeSuggestionService knowledgeSuggestionService) {
        this.settingsService = settingsService;
        this.knowledgeSuggestionService = knowledgeSuggestionService;
    }

    public SqlOptimizationLlmOutcome optimize(SqlOptimizationRequest request, SqlOptimizationRuleAnalysis ruleAnalysis) {
        DiagnosisLlmSettingsEntity settings = settingsService.getEffectiveSettings();
        boolean configured = settings.isEnabled()
            && hasText(settings.getEndpoint())
            && hasText(settings.getApiKey())
            && hasText(settings.getModel());
        if (!configured) {
            return fallback(ruleAnalysis, request, "RULE_ONLY", null, "大模型未启用或配置不完整，已回退到规则分析结果。");
        }

        String prompt = buildPrompt(request, ruleAnalysis);
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
                new HttpEntity<Map<String, Object> >(payload, headers),
                Map.class
            );
            String content = extractContent(response);
            if (!hasText(content)) {
                return fallback(ruleAnalysis, request, "RULE_FALLBACK", settings.getModel(), "大模型调用成功，但没有返回可解析内容。");
            }
            return parseContent(content, settings.getModel(), ruleAnalysis, request);
        } catch (RestClientResponseException exception) {
            logger.warn("SQL optimization request failed", exception);
            return fallback(
                ruleAnalysis,
                request,
                "RULE_FALLBACK",
                settings.getModel(),
                "大模型优化请求失败，HTTP " + exception.getRawStatusCode() + " " + exception.getStatusText() + "。"
            );
        } catch (ResourceAccessException exception) {
            logger.warn("SQL optimization request timed out or was unreachable", exception);
            Throwable root = exception.getMostSpecificCause();
            if (root instanceof SocketTimeoutException) {
                return fallback(ruleAnalysis, request, "RULE_FALLBACK", settings.getModel(), "大模型优化超时，已回退到规则分析结果。");
            }
            return fallback(
                ruleAnalysis,
                request,
                "RULE_FALLBACK",
                settings.getModel(),
                defaultIfBlank(exception.getMessage(), "大模型优化调用失败。")
            );
        } catch (Exception exception) {
            logger.warn("Failed to generate SQL optimization", exception);
            return fallback(
                ruleAnalysis,
                request,
                "RULE_FALLBACK",
                settings.getModel(),
                defaultIfBlank(exception.getMessage(), "大模型优化结果解析失败。")
            );
        }
    }

    private SqlOptimizationLlmOutcome parseContent(String content,
                                                   String llmModel,
                                                   SqlOptimizationRuleAnalysis ruleAnalysis,
                                                   SqlOptimizationRequest request) {
        try {
            String normalized = sanitizeStructuredText(content);
            Map<String, String> sections = parseSections(normalized);
            String optimizedSql = defaultIfBlank(sections.get("优化后SQL"), ruleAnalysis.getNormalizedSql());
            return new SqlOptimizationLlmOutcome(
                true,
                defaultIfBlank(sections.get("问题总结"), buildFallbackSummary(ruleAnalysis, request, null)),
                optimizedSql,
                parseBullets(sections.get("优化点"), 8, ruleAnalysis.getRewriteHints()),
                parseBullets(
                    sections.get("风险提示"),
                    6,
                    Collections.singletonList("上线前请在测试环境核对结果集、执行计划和资源开销，确认优化未改变业务语义。")
                ),
                parseBullets(
                    sections.get("验证步骤"),
                    6,
                    Arrays.asList(
                        "执行 EXPLAIN，对比优化前后的扫描范围、Join 顺序和聚合代价。",
                        "在测试环境校验优化前后结果集一致性，再观察执行时长和资源使用。"
                    )
                ),
                llmModel,
                "LLM"
            );
        } catch (Exception parseError) {
            logger.warn("Failed to parse SQL optimization structured text, falling back to rule analysis", parseError);
            return fallback(ruleAnalysis, request, "RULE_FALLBACK", llmModel, "大模型返回格式不符合约束，已回退到规则分析结果。");
        }
    }

    private SqlOptimizationLlmOutcome fallback(SqlOptimizationRuleAnalysis ruleAnalysis,
                                               SqlOptimizationRequest request,
                                               String analysisSource,
                                               String llmModel,
                                               String reason) {
        List<String> optimizationPoints = new ArrayList<String>(ruleAnalysis.getRewriteHints());
        if (hasText(reason)) {
            optimizationPoints.add(reason);
        }
        List<String> riskNotes = new ArrayList<String>();
        riskNotes.add("当前结果主要基于规则扫描生成，尚未引入完整执行计划推演。");
        if (hasText(request.getErrorText())) {
            riskNotes.add("优化前后仍需结合报错原文，复核数据类型、权限或资源限制问题。");
        }
        List<String> validationSteps = new ArrayList<String>();
        validationSteps.add("先执行 EXPLAIN，对比优化前后的扫描范围、Join 顺序和聚合代价。");
        validationSteps.add("在测试环境验证结果集一致性，再评估是否替换生产 SQL。");
        return new SqlOptimizationLlmOutcome(
            false,
            buildFallbackSummary(ruleAnalysis, request, reason),
            ruleAnalysis.getNormalizedSql(),
            optimizationPoints,
            riskNotes,
            validationSteps,
            llmModel,
            analysisSource
        );
    }

    private String buildFallbackSummary(SqlOptimizationRuleAnalysis ruleAnalysis,
                                        SqlOptimizationRequest request,
                                        String reason) {
        StringBuilder builder = new StringBuilder();
        builder.append("已完成 ").append(normalizeEngineType(request.getEngineType())).append(" SQL 规则扫描，");
        builder.append("当前主要风险等级为 ").append(ruleAnalysis.getRiskLevel()).append("。");
        if (!ruleAnalysis.getFindings().isEmpty()) {
            builder.append("核心问题包括：")
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
        return "你是 CDP 平台上的资深 SQL 优化专家，专门分析 Impala SQL 和 Hive SQL。"
            + "你必须使用简体中文回答，不要返回 JSON，不要返回 Markdown 代码块，不要解释你的推理过程。"
            + "你必须严格按照下面 5 个章节输出，并保持标题完全一致："
            + "【问题总结】、【优化后SQL】、【优化点】、【风险提示】、【验证步骤】。"
            + "优化时必须保证业务语义不变，优先依据输入中的规则分析、表结构、分区字段、EXPLAIN、报错信息和知识库建议。"
            + "分析时必须覆盖这些优化角度：扫描范围与分区裁剪、Join 顺序与 Join 策略、聚合与去重代价、排序与窗口函数、"
            + "谓词下推与列裁剪、数据倾斜与 Shuffle、引擎特性与资源开销、结果语义一致性。"
            + "如果是 Impala，请重点关注分区裁剪、广播 Join / Shuffle Join、统计信息、Admission Control、内存与并发开销。"
            + "如果是 Hive，请重点关注分区裁剪、MapJoin / Common Join、Reduce 数量、数据倾斜、小文件、Tez 或 MapReduce 代价。"
            + "【优化后SQL】必须给出完整 SQL；如果上下文不足以安全改写，也要保留原 SQL 并说明原因。"
            + "【优化点】、【风险提示】、【验证步骤】必须逐条输出，每条单独一行。";
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<String, String>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String buildPrompt(SqlOptimizationRequest request, SqlOptimizationRuleAnalysis ruleAnalysis) {
        String engineType = normalizeEngineType(request.getEngineType());
        String knowledgeDomain = engineType + "_SQL";
        String originalSql = limitedInput(request.getOriginalSql(), SQL_TEXT_LIMIT);
        String tableSchemaNote = limitedInput(request.getTableSchemaNote(), SCHEMA_NOTE_LIMIT);
        String partitionInfo = limitedInput(request.getPartitionInfo(), PARTITION_INFO_LIMIT);
        String explainText = limitedInput(request.getExplainText(), EXPLAIN_TEXT_LIMIT);
        String errorText = limitedInput(request.getErrorText(), ERROR_TEXT_LIMIT);
        List<KnowledgeSuggestionRecord> suggestions = knowledgeSuggestionService.search(
            knowledgeDomain,
            originalSql + "\n" + tableSchemaNote + "\n" + errorText,
            3
        );

        StringBuilder builder = new StringBuilder();
        builder.append("请分析并优化以下 SQL。\n");
        builder.append("执行引擎: ").append(engineType).append("\n");
        builder.append("优化目标: ").append(defaultIfBlank(request.getOptimizationGoal(), "提升执行效率，同时保持结果语义不变。")).append("\n\n");

        builder.append("【原始SQL】\n");
        builder.append(defaultIfBlank(originalSql, "none")).append("\n\n");

        builder.append("【规则扫描结论】\n");
        builder.append("风险等级: ").append(ruleAnalysis.getRiskLevel()).append("\n");
        builder.append("已发现问题:\n");
        appendList(builder, ruleAnalysis.getFindings(), 10, 260);
        builder.append("规则建议:\n");
        appendList(builder, ruleAnalysis.getRewriteHints(), 10, 260);
        builder.append("\n");

        builder.append("【补充上下文】\n");
        builder.append("表结构说明:\n").append(defaultIfBlank(tableSchemaNote, "none")).append("\n");
        builder.append("分区信息:\n").append(defaultIfBlank(partitionInfo, "none")).append("\n");
        builder.append("EXPLAIN / PROFILE:\n").append(defaultIfBlank(explainText, "none")).append("\n");
        builder.append("报错信息:\n").append(defaultIfBlank(errorText, "none")).append("\n\n");

        builder.append("【分析要求】\n");
        appendEngineSpecificStrategies(builder, engineType);
        appendEnterpriseBestPractices(builder, engineType);

        if (!suggestions.isEmpty()) {
            builder.append("\n【知识库命中】\n");
            for (KnowledgeSuggestionRecord suggestion : suggestions) {
                builder.append("- 标题: ").append(defaultIfBlank(suggestion.getTitle(), "Untitled"))
                    .append(" | 领域: ").append(defaultIfBlank(suggestion.getDomain(), "UNKNOWN"))
                    .append(" | 摘要: ").append(truncate(defaultIfBlank(suggestion.getSummary(), "none"), KNOWLEDGE_SUMMARY_LIMIT))
                    .append("\n");
            }
        }

        builder.append("\n【输出约束】\n");
        builder.append("1. 不要返回 JSON。\n");
        builder.append("2. 必须严格按 5 个章节输出：问题总结、优化后SQL、优化点、风险提示、验证步骤。\n");
        builder.append("3. 优化后 SQL 必须完整可读，不要省略核心子句。\n");
        builder.append("4. 优化点必须解释当前 SQL 存在哪些问题，以及具体如何修改。\n");
        builder.append("5. 风险提示必须指出潜在副作用、兼容性风险或语义风险。\n");
        builder.append("6. 验证步骤必须包含 EXPLAIN、结果集一致性或资源开销核验。\n");
        return builder.toString();
    }

    private void appendEngineSpecificStrategies(StringBuilder builder, String engineType) {
        builder.append("- 先判断当前 SQL 的主要瓶颈属于扫描、Join、聚合、排序、倾斜、资源争抢还是语义问题。\n");
        builder.append("- 指出当前 SQL 存在的问题，不要只给泛化建议。\n");
        builder.append("- 所有建议都必须落到可执行改写，而不是空泛原则。\n");
        if ("HIVE".equals(engineType)) {
            builder.append("- 重点检查是否缺少分区过滤、是否存在过多 Reduce、是否需要 MapJoin、是否存在数据倾斜或小文件问题。\n");
            builder.append("- 重点判断 Group By / Distinct / Order By 是否会放大 Shuffle，是否需要先聚合、先过滤或改写子查询。\n");
            builder.append("- 如果 SQL 适合 Tez 执行优化，请明确说明从减少 Stage、降低 Shuffle、控制并发角度如何改写。\n");
        } else {
            builder.append("- 重点检查是否缺少分区裁剪、是否存在大表 Join 顺序问题、是否可能触发 Broadcast 失控或 Shuffle 过大。\n");
            builder.append("- 重点判断 Admission Control、内存占用、Count Distinct、全局排序、谓词下推和列裁剪是否存在明显优化空间。\n");
            builder.append("- 如果 SQL 适合先过滤再 Join、先聚合后 Join 或拆分中间层，请明确给出改写策略和理由。\n");
        }
    }

    private void appendEnterpriseBestPractices(StringBuilder builder, String engineType) {
        builder.append("\n【平台内优化经验】\n");
        if ("HIVE".equals(engineType)) {
            builder.append("- 先判断任务中每张表及每个分区的数据量，避免只给 SQL 级建议而忽略数据量级。\n");
            builder.append("- 重点分析关联键、分组字段的数据分布，判断是否存在倾斜、高基数或空值问题。\n");
            builder.append("- 必须关注分区过滤是否完整，尽量减少扫描分区数。\n");
            builder.append("- Select 子句只保留业务必需字段，减少扫描列数。\n");
            builder.append("- 如需落中间结果或临时表，优先建议使用 parquet，包括 create table as select 的临时表。\n");
            builder.append("- 非必要不要使用 row_number() over(partition by ... order by ...)，除非业务语义确实需要。\n");
            builder.append("- 重点检查关联键的数据质量，包括空值、重复、异常编码等。\n");
            builder.append("- 关联键尽量不要做函数处理，如 cast、substr、concat、nvl 后再 join。\n");
            builder.append("- 避免多重函数嵌套、复杂 case when 嵌套，这类写法会明显增加 CPU 开销。\n");
            builder.append("- 一个 SQL 中尽量减少同一张表的重复扫描；如存在 union all 后再重复 join，同步评估是否能先 union all 再统一 join。\n");
        } else {
            builder.append("- 查询尽可能添加分区过滤，减少扫描分区数。\n");
            builder.append("- Select 子句只取业务需要字段，减少扫描列数。\n");
            builder.append("- Join 顺序优先保证大表在前、小表在后，例如大表 left join 小表，或大表 right join 小表。\n");
            builder.append("- 如不确定表大小，left/right join 后优先评估是否显式加 [shuffle]，避免大表 broadcast 导致单节点内存峰值过高。\n");
            builder.append("- Inner join 中 [shuffle] 可能不生效，必要时评估在 select 后增加 [straight_join] 来固定 Join 顺序。\n");
            builder.append("- 插入分区表时优先使用静态分区；如必须动态分区插入，评估 insert 子句使用 [noclustered] 以避免额外排序。\n");
        }
    }

    private void appendList(StringBuilder builder, List<String> values, int limit, int maxItemLength) {
        if (values == null || values.isEmpty()) {
            builder.append("- none\n");
            return;
        }
        int count = 0;
        for (String value : values) {
            builder.append("- ").append(truncate(value, maxItemLength)).append("\n");
            count++;
            if (count >= limit) {
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
        content = extractTextValue(message.get("reasoning"));
        if (hasText(content)) {
            return content;
        }
        return extractTextValue(message.get("text"));
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
        factory.setReadTimeout(Math.max(settings.getReadTimeoutMs(), 240000));
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
        String sanitized = value == null ? "" : value.trim();
        sanitized = sanitized.replace("```sql", "").replace("```SQL", "").replace("```", "");
        return sanitized.trim();
    }

    private Map<String, String> parseSections(String content) {
        Map<String, String> sections = new LinkedHashMap<String, String>();
        Matcher matcher = SECTION_PATTERN.matcher(content);
        List<Integer> starts = new ArrayList<Integer>();
        List<String> titles = new ArrayList<String>();
        while (matcher.find()) {
            starts.add(matcher.start());
            titles.add(matcher.group(1));
        }
        if (starts.isEmpty()) {
            throw new IllegalArgumentException("No structured sections found in SQL optimization response");
        }
        for (int index = 0; index < starts.size(); index++) {
            int start = starts.get(index);
            int contentStart = content.indexOf('\n', start);
            if (contentStart < 0) {
                contentStart = content.length();
            } else {
                contentStart += 1;
            }
            int end = index + 1 < starts.size() ? starts.get(index + 1) : content.length();
            String body = content.substring(contentStart, end).trim();
            sections.put(titles.get(index), body);
        }
        return sections;
    }

    private List<String> parseBullets(String sectionText, int limit, List<String> fallback) {
        List<String> values = new ArrayList<String>();
        if (hasText(sectionText)) {
            String[] lines = sectionText.split("\\R+");
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }
                line = line.replaceFirst("^[\\-•\\d+\\.、\\s]+", "").trim();
                if (!line.isEmpty()) {
                    values.add(line);
                }
                if (values.size() >= limit) {
                    break;
                }
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private String normalizeEngineType(String engineType) {
        if (engineType == null) {
            return "IMPALA";
        }
        String normalized = engineType.trim().toUpperCase();
        return "HIVE".equals(normalized) ? "HIVE" : "IMPALA";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String limitedInput(String value, int limit) {
        String normalized = safe(value);
        if (!hasText(normalized)) {
            return "";
        }
        return truncate(normalized, limit);
    }

    private String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
