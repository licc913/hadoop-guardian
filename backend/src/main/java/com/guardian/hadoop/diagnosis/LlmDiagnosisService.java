package com.guardian.hadoop.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardian.hadoop.incident.IncidentEntity;
import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsEntity;
import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
public class LlmDiagnosisService {

    private static final Logger logger = LoggerFactory.getLogger(LlmDiagnosisService.class);

    private final DiagnosisLlmSettingsService settingsService;
    private final ObjectMapper objectMapper;

    public LlmDiagnosisService(DiagnosisLlmSettingsService settingsService, ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    public LlmDiagnosisResult generate(IncidentEntity incident, String triggerReason, String triggerBy) {
        DiagnosisLlmSettingsEntity settings = settingsService.getEffectiveSettings();
        boolean configured = hasText(settings.getEndpoint()) && hasText(settings.getApiKey()) && hasText(settings.getModel());
        if (!settings.isEnabled() && !configured) {
            return LlmDiagnosisResult.failure("AI 大模型诊断未启用。", "请先在设置页启用或补齐大模型配置。");
        }
        if (!configured) {
            return LlmDiagnosisResult.failure("AI 大模型配置不完整。", "请检查接口地址、API Key 和模型名称是否都已正确保存。");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(settings.getApiKey().trim());

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("model", settings.getModel().trim());
            payload.put("stream", Boolean.FALSE);
            payload.put("temperature", settings.getTemperature());
            if (settings.getMaxTokens() > 0) {
                payload.put("max_tokens", settings.getMaxTokens());
            }
            payload.put("messages", buildMessages(incident, triggerReason, triggerBy));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = createRestTemplate(settings).postForObject(
                settings.getEndpoint().trim(),
                new HttpEntity<Map<String, Object> >(payload, headers),
                Map.class
            );

            String content = extractContent(response);
            if (!hasText(content)) {
                return LlmDiagnosisResult.failure(
                    "AI 大模型接口已响应，但没有返回可解析的诊断内容。",
                    summarizeResponse(response)
                );
            }

            try {
                return LlmDiagnosisResult.success(parseBlueprint(content, incident.getServiceType()));
            } catch (IOException parseException) {
                logger.warn("Failed to parse JSON diagnosis result, falling back to plain text blueprint", parseException);
                return LlmDiagnosisResult.success(buildPlainTextBlueprint(content, incident.getServiceType()));
            }
        } catch (RestClientResponseException exception) {
            logger.warn("External LLM request failed", exception);
            return LlmDiagnosisResult.failure("AI 大模型请求失败。", buildHttpErrorDetails(exception));
        } catch (Exception exception) {
            logger.warn("Failed to generate diagnosis from external LLM", exception);
            return LlmDiagnosisResult.failure(
                "AI 大模型返回结果无法解析。",
                defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName())
            );
        }
    }

    private List<Map<String, String> > buildMessages(IncidentEntity incident, String triggerReason, String triggerBy) {
        List<Map<String, String> > messages = new ArrayList<Map<String, String> >();
        messages.add(message(
            "system",
            "你是 Hadoop 平台故障诊断助手。请基于事件信息输出一个 JSON 对象，不要输出 markdown，不要输出多余说明。"
                + " 字段必须包含 rootCause、confidence、impactLevel、crossComponentPath、recommendations、followUps、"
                + "actionName、actionType、actionRiskLevel、actionRequiresApproval、actionRecommendationText。"
                + " actionType 只能是 EVIDENCE_COLLECTION、DIAGNOSTIC_COLLECTION、GUARDED_SCRIPT 之一。"
                + " actionRiskLevel 只能是 LOW、MEDIUM、HIGH、CRITICAL 之一。"
                + " impactLevel 只能是 SEV1、SEV2、SEV3 之一。"
                + " recommendations 最多 3 条，followUps 最多 2 条。"
        ));
        messages.add(message("user", buildIncidentPrompt(incident, triggerReason, triggerBy)));
        return messages;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<String, String>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String buildIncidentPrompt(IncidentEntity incident, String triggerReason, String triggerBy) {
        StringBuilder builder = new StringBuilder();
        builder.append("请根据下面事件生成 JSON。\n");
        builder.append("JSON schema:\n");
        builder.append("{");
        builder.append("\"rootCause\":string,");
        builder.append("\"confidence\":number,");
        builder.append("\"impactLevel\":string,");
        builder.append("\"crossComponentPath\":string,");
        builder.append("\"recommendations\":string[],");
        builder.append("\"followUps\":string[],");
        builder.append("\"actionName\":string,");
        builder.append("\"actionType\":string,");
        builder.append("\"actionRiskLevel\":string,");
        builder.append("\"actionRequiresApproval\":boolean,");
        builder.append("\"actionRecommendationText\":string");
        builder.append("}\n");
        builder.append("事件编号：").append(safe(incident.getIncidentNo())).append("\n");
        builder.append("服务类型：").append(safe(incident.getServiceType())).append("\n");
        builder.append("严重级别：").append(safe(incident.getSeverity())).append("\n");
        builder.append("事件状态：").append(safe(incident.getStatus())).append("\n");
        builder.append("标题：").append(safe(incident.getTitle())).append("\n");
        builder.append("摘要：").append(safe(incident.getSummary())).append("\n");
        builder.append("影响范围：").append(safe(incident.getImpactScope())).append("\n");
        builder.append("责任人：").append(safe(incident.getOwner())).append("\n");
        builder.append("触发人：").append(defaultIfBlank(triggerBy, "未知")).append("\n");
        builder.append("触发原因：").append(defaultIfBlank(triggerReason, "人工发起诊断")).append("\n");
        builder.append("证据：\n");
        appendList(builder, incident.getEvidence());
        builder.append("避免动作：\n");
        appendList(builder, incident.getAvoidedActions());
        builder.append("要求：结论必须简洁、可执行、保守，建议动作不要超出当前证据太多。");
        return builder.toString();
    }

    private void appendList(StringBuilder builder, List<String> values) {
        if (values == null || values.isEmpty()) {
            builder.append("- 无\n");
            return;
        }
        for (String value : values) {
            builder.append("- ").append(safe(value)).append("\n");
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
        Object contentObject = ((Map<?, ?>) messageObject).get("content");
        if (contentObject instanceof String) {
            return (String) contentObject;
        }
        if (contentObject instanceof List) {
            StringBuilder builder = new StringBuilder();
            for (Object item : (List<?>) contentObject) {
                if (item instanceof Map) {
                    Object text = ((Map<?, ?>) item).get("text");
                    if (text instanceof String) {
                        builder.append((String) text);
                    }
                }
            }
            return builder.toString();
        }
        return null;
    }

    private DiagnosisBlueprint parseBlueprint(String content, String serviceType) throws IOException {
        String sanitized = sanitizeJsonBlock(content);
        JsonNode root = objectMapper.readTree(sanitized);

        String rootCause = truncate(readText(root, "rootCause",
            safe(serviceType) + " 方向存在异常，需要结合更多证据确认最终根因。"), 256);
        double confidence = clamp(readDouble(root, "confidence", 0.66d), 0.1d, 0.95d);
        String impactLevel = normalizeOneOf(readText(root, "impactLevel", "SEV2"), Arrays.asList("SEV1", "SEV2", "SEV3"), "SEV2");
        String crossComponentPath = truncate(readText(root, "crossComponentPath", safe(serviceType)), 128);
        List<String> recommendations = readTextList(root.get("recommendations"), 3, "先复核现有证据，再决定是否扩展处置范围。");
        List<String> followUps = readTextList(root.get("followUps"), 2, "补充更多证据后刷新诊断结论。");
        String actionName = truncate(readText(root, "actionName", "生成诊断建议"), 128);
        String actionType = normalizeOneOf(readText(root, "actionType", "EVIDENCE_COLLECTION"),
            Arrays.asList("EVIDENCE_COLLECTION", "DIAGNOSTIC_COLLECTION", "GUARDED_SCRIPT"), "EVIDENCE_COLLECTION");
        String actionRiskLevel = normalizeOneOf(readText(root, "actionRiskLevel", "LOW"),
            Arrays.asList("LOW", "MEDIUM", "HIGH", "CRITICAL"), "LOW");
        boolean actionRequiresApproval = readBoolean(root, "actionRequiresApproval", false);
        String actionRecommendationText = readText(root, "actionRecommendationText", "请先补采更多证据，再决定是否执行受控动作。");

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

    private DiagnosisBlueprint buildPlainTextBlueprint(String content, String serviceType) {
        String normalized = defaultIfBlank(content, "模型未返回有效内容")
            .replace("\r", "\n")
            .replaceAll("\n{2,}", "\n")
            .trim();
        List<String> lines = Arrays.stream(normalized.split("\n"))
            .map(String::trim)
            .filter(this::hasText)
            .collect(Collectors.toList());

        String rootCause = truncate(lines.isEmpty() ? normalized : lines.get(0), 256);
        List<String> recommendations = new ArrayList<String>();
        for (int index = 1; index < lines.size() && recommendations.size() < 3; index++) {
            recommendations.add(lines.get(index));
        }
        if (recommendations.isEmpty()) {
            recommendations.add("先复核模型输出与当前事件证据是否一致，再决定是否扩展处置范围。");
        }

        return new DiagnosisBlueprint(
            rootCause,
            0.72d,
            "SEV2",
            safe(serviceType),
            recommendations,
            Arrays.asList("回填更多运行指标后复核诊断结论。", "如涉及风险动作，请走审批流程。"),
            "复核 AI 诊断结论",
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

    private String summarizeResponse(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return "响应体为空。";
        }
        try {
            return truncate(objectMapper.writeValueAsString(response), 500);
        } catch (Exception ignored) {
            return "响应体无法序列化。";
        }
    }

    private String buildHttpErrorDetails(RestClientResponseException exception) {
        return "HTTP " + exception.getRawStatusCode() + " " + exception.getStatusText()
            + " | " + truncate(defaultIfBlank(exception.getResponseBodyAsString(), "无响应体"), 800);
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
        String normalized = safe(value).toUpperCase(Locale.ROOT);
        for (String allowedValue : allowedValues) {
            if (allowedValue.equalsIgnoreCase(normalized)) {
                return allowedValue;
            }
        }
        return fallback;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String truncate(String value, int maxLength) {
        String safeValue = safe(value);
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }

    private RestTemplate createRestTemplate(DiagnosisLlmSettingsEntity settings) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(settings.getConnectTimeoutMs());
        factory.setReadTimeout(settings.getReadTimeoutMs());
        return new RestTemplate(factory);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
