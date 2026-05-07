package com.guardian.hadoop.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardian.hadoop.incident.IncidentEntity;
import com.guardian.hadoop.integration.cm.CmServiceLogSnapshotRecord;
import com.guardian.hadoop.integration.cm.CmServiceLogSnapshotService;
import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsEntity;
import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsService;
import com.guardian.hadoop.knowledge.KnowledgeSuggestionRecord;
import com.guardian.hadoop.knowledge.KnowledgeSuggestionService;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
public class LlmDiagnosisService {

    private static final Logger logger = LoggerFactory.getLogger(LlmDiagnosisService.class);

    private final DiagnosisLlmSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final KnowledgeSuggestionService knowledgeSuggestionService;
    private final CmServiceLogSnapshotService logSnapshotService;

    public LlmDiagnosisService(DiagnosisLlmSettingsService settingsService,
                               ObjectMapper objectMapper,
                               KnowledgeSuggestionService knowledgeSuggestionService,
                               CmServiceLogSnapshotService logSnapshotService) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.knowledgeSuggestionService = knowledgeSuggestionService;
        this.logSnapshotService = logSnapshotService;
    }

    public LlmDiagnosisResult generate(IncidentEntity incident, String triggerReason, String triggerBy) {
        DiagnosisLlmSettingsEntity settings = settingsService.getEffectiveSettings();
        boolean configured = hasText(settings.getEndpoint()) && hasText(settings.getApiKey()) && hasText(settings.getModel());
        if (!settings.isEnabled() && !configured) {
            return LlmDiagnosisResult.failure("AI diagnosis is disabled.", "Enable and save the LLM settings first.");
        }
        if (!configured) {
            return LlmDiagnosisResult.failure("AI diagnosis settings are incomplete.", "Check endpoint, API key, and model.");
        }

        List<KnowledgeSuggestionRecord> knowledgeSuggestions = knowledgeSuggestionService.search(
            incident.getServiceType(),
            safe(incident.getTitle()) + "\n" + safe(incident.getSummary()) + "\n" + String.join("\n", incident.getEvidence()),
            3
        );
        List<CmServiceLogSnapshotRecord> serviceLogs = logSnapshotService.getLogsForIncident(incident);
        String prompt = buildIncidentPrompt(incident, triggerReason, triggerBy, knowledgeSuggestions, serviceLogs);

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
            Map<String, Object> response = createRestTemplate(settings, prompt).postForObject(
                settings.getEndpoint().trim(),
                new HttpEntity<Map<String, Object> >(payload, headers),
                Map.class
            );

            String content = extractContent(response);
            if (!hasText(content)) {
                return LlmDiagnosisResult.failure(
                    "AI model responded but did not return a final diagnosis body.",
                    summarizeMissingContentResponse(response)
                );
            }

            try {
                return LlmDiagnosisResult.success(parseBlueprint(content, incident.getServiceType()));
            } catch (IOException parseException) {
                if (looksLikeReasoningOnly(content)) {
                    return LlmDiagnosisResult.failure(
                        "AI model returned reasoning text but no final diagnosis JSON.",
                        "Increase max output tokens, or use a non-reasoning chat model for the diagnosis task."
                    );
                }
                logger.warn("Failed to parse LLM diagnosis JSON, falling back to plain text blueprint", parseException);
                return LlmDiagnosisResult.success(buildPlainTextBlueprint(content));
            }
        } catch (RestClientResponseException exception) {
            logger.warn("External LLM diagnosis request failed", exception);
            return LlmDiagnosisResult.failure("AI request failed.", buildHttpErrorDetails(exception));
        } catch (ResourceAccessException exception) {
            logger.warn("External LLM diagnosis timed out or was unreachable", exception);
            Throwable root = exception.getMostSpecificCause();
            if (root instanceof SocketTimeoutException) {
                return LlmDiagnosisResult.failure(
                    "AI diagnosis timed out.",
                    "Current read timeout=" + effectiveReadTimeout(settings, prompt)
                        + "ms. Reduce prompt size or increase the timeout."
                );
            }
            return LlmDiagnosisResult.failure(
                "AI request failed.",
                defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName())
            );
        } catch (Exception exception) {
            logger.warn("Failed to generate diagnosis from external LLM", exception);
            return LlmDiagnosisResult.failure(
                "AI response could not be parsed.",
                defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName())
            );
        }
    }

    private List<Map<String, String> > buildMessages(String prompt) {
        List<Map<String, String> > messages = new ArrayList<Map<String, String> >();
        messages.add(message(
            "system",
            "你是资深 CDP/Hadoop 平台故障诊断专家，擅长基于 Cloudera Manager 服务状态、角色日志和运维知识库做根因分析。"
                + "你必须只返回一个 JSON 对象，不要输出 Markdown、解释说明、代码块或额外文本。"
                + "JSON 字段名必须固定为：rootCause, confidence, impactLevel, crossComponentPath, recommendations, followUps, "
                + "actionName, actionType, actionRiskLevel, actionRequiresApproval, actionRecommendationText。"
                + "所有自然语言字段值必须使用简体中文。"
                + "诊断必须优先基于当前服务日志，不能编造日志中不存在的现象。"
                + "rootCause 必须写得充分，至少覆盖：现象、直接原因、关键日志证据、受影响角色或节点。"
                + "recommendations 必须给出可执行步骤，而不是空泛建议。"
                + "followUps 必须写待补充证据、风险提示或验证步骤。"
        ));
        messages.add(message("user", prompt));
        return messages;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<String, String>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String buildIncidentPrompt(IncidentEntity incident,
                                       String triggerReason,
                                       String triggerBy,
                                       List<KnowledgeSuggestionRecord> knowledgeSuggestions,
                                       List<CmServiceLogSnapshotRecord> serviceLogs) {
        StringBuilder builder = new StringBuilder();
        builder.append("请分析以下诊断事件，并且只返回 JSON 对象。所有自然语言字段值必须使用简体中文。\n");
        builder.append("incidentNo: ").append(safe(incident.getIncidentNo())).append("\n");
        builder.append("serviceType: ").append(safe(incident.getServiceType())).append("\n");
        builder.append("severity: ").append(safe(incident.getSeverity())).append("\n");
        builder.append("status: ").append(safe(incident.getStatus())).append("\n");
        builder.append("title: ").append(truncate(safe(incident.getTitle()), 220)).append("\n");
        builder.append("summary: ").append(truncate(safe(incident.getSummary()), 300)).append("\n");
        builder.append("impactScope: ").append(truncate(safe(incident.getImpactScope()), 220)).append("\n");
        builder.append("owner: ").append(defaultIfBlank(incident.getOwner(), "UNKNOWN")).append("\n");
        builder.append("triggerBy: ").append(defaultIfBlank(triggerBy, "UNKNOWN")).append("\n");
        builder.append("triggerReason: ").append(defaultIfBlank(triggerReason, "manual diagnosis")).append("\n");
        builder.append("Current service logs (highest priority, same as frontend display):\n");
        appendServiceLogs(builder, serviceLogs, 12, 520);
        builder.append("Evidence:\n");
        appendList(builder, incident.getEvidence(), 10, 320);
        builder.append("Avoided actions:\n");
        appendList(builder, incident.getAvoidedActions(), 3, 180);
        if (knowledgeSuggestions != null && !knowledgeSuggestions.isEmpty()) {
            builder.append("Knowledge matches:\n");
            for (KnowledgeSuggestionRecord suggestion : knowledgeSuggestions) {
                builder.append("- title: ").append(defaultIfBlank(suggestion.getTitle(), "Untitled knowledge article"))
                    .append(" | domain: ").append(defaultIfBlank(suggestion.getDomain(), "UNKNOWN"))
                    .append(" | summary: ").append(truncate(defaultIfBlank(suggestion.getSummary(), "N/A"), 240))
                    .append("\n");
            }
        }
        builder.append("Requirements:\n");
        builder.append("- 只能基于当前事件证据和当前服务日志判断，不得编造事实。\n");
        builder.append("- 最高优先级是上面的服务日志，尤其是 WARN/ERROR 及其上下文。\n");
        builder.append("- rootCause 不能只写一句空泛结论，必须说明：发生了什么、直接原因是什么、日志里哪几条证据支持这个判断、影响到哪个节点或角色。\n");
        builder.append("- recommendations 至少给出 3 条具体做法，按先后顺序描述，说明先做什么、再做什么、怎么验证。\n");
        builder.append("- followUps 写待补充证据、风险提示或验证步骤，不要重复 recommendations。\n");
        builder.append("- 只围绕当前服务日志诊断，不做跨组件推断；crossComponentPath 默认返回空字符串即可。\n");
        builder.append("- 如果证据不足，明确指出缺什么，而不是猜测。\n");
        return builder.toString();
    }

    private void appendServiceLogs(StringBuilder builder,
                                   List<CmServiceLogSnapshotRecord> serviceLogs,
                                   int limit,
                                   int maxItemLength) {
        if (serviceLogs == null || serviceLogs.isEmpty()) {
            builder.append("- none\n");
            return;
        }
        int count = 0;
        for (CmServiceLogSnapshotRecord value : serviceLogs) {
            builder.append("- ")
                .append(renderServiceLogForPrompt(value, maxItemLength))
                .append("\n");
            count++;
            if (count >= limit) {
                break;
            }
        }
    }

    private String renderServiceLogForPrompt(CmServiceLogSnapshotRecord value, int maxItemLength) {
        String line = "["
            + defaultIfBlank(value.getLogType(), "LOG")
            + "] "
            + defaultIfBlank(value.getLogText(), "empty");
        return truncate(line, maxItemLength);
    }

    private void appendList(StringBuilder builder, List<String> values, int limit, int maxItemLength) {
        if (values == null || values.isEmpty()) {
            builder.append("- none\n");
            return;
        }
        int count = 0;
        for (String value : values) {
            builder.append("- ").append(truncate(safe(value), maxItemLength)).append("\n");
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

    private DiagnosisBlueprint parseBlueprint(String content, String serviceType) throws IOException {
        String sanitized = sanitizeJsonBlock(content);
        JsonNode root = objectMapper.readTree(sanitized);
        String rootCause = truncate(
            readText(root, "rootCause", safe(serviceType) + " shows abnormal behavior and needs more evidence."),
            4000
        );
        double confidence = clamp(readDouble(root, "confidence", 0.66d), 0.1d, 0.95d);
        String impactLevel = normalizeOneOf(readText(root, "impactLevel", "SEV2"), Arrays.asList("SEV1", "SEV2", "SEV3"), "SEV2");
        String crossComponentPath = truncate(readText(root, "crossComponentPath", ""), 128);
        List<String> recommendations = readTextList(root.get("recommendations"), 5, "Validate current evidence before expanding remediation scope.");
        List<String> followUps = readTextList(root.get("followUps"), 4, "Collect more evidence and refresh the diagnosis.");
        String actionName = truncate(readText(root, "actionName", "Generate diagnosis recommendation"), 128);
        String actionType = normalizeOneOf(
            readText(root, "actionType", "EVIDENCE_COLLECTION"),
            Arrays.asList("EVIDENCE_COLLECTION", "DIAGNOSTIC_COLLECTION", "GUARDED_SCRIPT"),
            "EVIDENCE_COLLECTION"
        );
        String actionRiskLevel = normalizeOneOf(
            readText(root, "actionRiskLevel", "LOW"),
            Arrays.asList("LOW", "MEDIUM", "HIGH", "CRITICAL"),
            "LOW"
        );
        boolean actionRequiresApproval = readBoolean(root, "actionRequiresApproval", false);
        String actionRecommendationText = readText(root, "actionRecommendationText", recommendations.get(0));

        return new DiagnosisBlueprint(
            rootCause,
            confidence,
            impactLevel,
            crossComponentPath,
            recommendations,
            followUps,
            actionName,
            actionType,
            actionRiskLevel,
            actionRequiresApproval,
            actionRecommendationText,
            "EXTERNAL_LLM"
        );
    }

    private DiagnosisBlueprint buildPlainTextBlueprint(String content) {
        String normalized = defaultIfBlank(content, "Model returned no valid content.")
            .replace("\r", "\n")
            .replaceAll("\n{2,}", "\n")
            .trim();
        String[] rawLines = normalized.split("\n");
        List<String> lines = new ArrayList<String>();
        for (String rawLine : rawLines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (hasText(line)) {
                lines.add(line);
            }
        }

        String rootCause = truncate(normalized, 4000);
        List<String> recommendations = new ArrayList<String>();
        for (int index = 0; index < lines.size() && recommendations.size() < 5; index++) {
            recommendations.add(lines.get(index));
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Validate current evidence before expanding remediation scope.");
        }

        return new DiagnosisBlueprint(
            rootCause,
            0.72d,
            "SEV2",
            "",
            recommendations,
            Arrays.asList("Collect more runtime evidence.", "Verify the proposed handling steps in a controlled window.", "If the action is risky, route it through approval."),
            "Review AI diagnosis result",
            "DIAGNOSTIC_COLLECTION",
            "LOW",
            false,
            recommendations.get(0),
            "EXTERNAL_LLM"
        );
    }

    private String sanitizeJsonBlock(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    private String summarizeMissingContentResponse(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return "Empty response body.";
        }
        Object choicesObject = response.get("choices");
        if (!(choicesObject instanceof List) || ((List<?>) choicesObject).isEmpty()) {
            return "Response does not contain choices[].";
        }
        Object firstChoice = ((List<?>) choicesObject).get(0);
        if (!(firstChoice instanceof Map)) {
            return "Response choices[0] is not an object.";
        }
        Object messageObject = ((Map<?, ?>) firstChoice).get("message");
        if (!(messageObject instanceof Map)) {
            return "Response choices[0].message is missing.";
        }
        Map<?, ?> message = (Map<?, ?>) messageObject;
        boolean hasReasoning = hasText(extractTextValue(message.get("reasoning_content")))
            || hasText(extractTextValue(message.get("reasoning")));
        if (hasReasoning) {
            return "Response contains reasoning_content but choices[0].message.content is empty. "
                + "Increase max output tokens or use a non-reasoning chat model for diagnosis JSON.";
        }
        return "Response choices[0].message.content is empty.";
    }

    private boolean looksLikeReasoningOnly(String content) {
        String normalized = safe(content).toLowerCase();
        return normalized.contains("json")
            && normalized.contains("rootcause")
            && (normalized.contains("requirements") || normalized.contains("recommendations"));
    }

    private String buildHttpErrorDetails(RestClientResponseException exception) {
        return "HTTP "
            + exception.getRawStatusCode()
            + " "
            + exception.getStatusText()
            + " | "
            + truncate(defaultIfBlank(exception.getResponseBodyAsString(), "empty response body"), 800);
    }

    private String readText(JsonNode root, String fieldName, String fallback) {
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return fallback;
        }
        String value = node.asText();
        return hasText(value) ? value.trim() : fallback;
    }

    private double readDouble(JsonNode root, String fieldName, double fallback) {
        JsonNode node = root.get(fieldName);
        return node == null || !node.isNumber() ? fallback : node.asDouble();
    }

    private boolean readBoolean(JsonNode root, String fieldName, boolean fallback) {
        JsonNode node = root.get(fieldName);
        return node == null || !node.isBoolean() ? fallback : node.asBoolean();
    }

    private List<String> readTextList(JsonNode node, int maxItems, String fallback) {
        List<String> values = new ArrayList<String>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && hasText(item.asText())) {
                    values.add(item.asText().trim());
                }
                if (values.size() >= maxItems) {
                    break;
                }
            }
        }
        if (values.isEmpty()) {
            values.add(fallback);
        }
        return values;
    }

    private String normalizeOneOf(String value, List<String> allowedValues, String fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        String candidate = value.trim().toUpperCase();
        for (String allowedValue : allowedValues) {
            if (allowedValue.equalsIgnoreCase(candidate)) {
                return allowedValue;
            }
        }
        return fallback;
    }

    private RestTemplate createRestTemplate(DiagnosisLlmSettingsEntity settings, String prompt) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(settings.getConnectTimeoutMs(), 10000));
        factory.setReadTimeout(effectiveReadTimeout(settings, prompt));
        return new RestTemplate(factory);
    }

    private int effectiveReadTimeout(DiagnosisLlmSettingsEntity settings, String prompt) {
        int configured = Math.max(settings.getReadTimeoutMs(), 60000);
        if (prompt != null && prompt.length() > 2500) {
            return Math.max(configured, 240000);
        }
        if (prompt != null && prompt.length() > 1200) {
            return Math.max(configured, 180000);
        }
        return Math.max(configured, 120000);
    }

    private int effectiveMaxTokens(DiagnosisLlmSettingsEntity settings) {
        int configured = settings == null ? 0 : settings.getMaxTokens();
        if (configured <= 0) {
            return 0;
        }
        if (configured == 2048) {
            return 0;
        }
        return configured;
    }

    private double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String truncate(String value, int maxLength) {
        String safeValue = safe(value);
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
