package com.guardian.hadoop.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardian.hadoop.incident.IncidentEntity;
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

    public LlmDiagnosisService(DiagnosisLlmSettingsService settingsService,
                               ObjectMapper objectMapper,
                               KnowledgeSuggestionService knowledgeSuggestionService) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.knowledgeSuggestionService = knowledgeSuggestionService;
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
        String prompt = buildIncidentPrompt(incident, triggerReason, triggerBy, knowledgeSuggestions);

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
                    "AI returned no diagnosis content.",
                    summarizeResponse(response)
                );
            }

            try {
                return LlmDiagnosisResult.success(parseBlueprint(content, incident.getServiceType()));
            } catch (IOException parseException) {
                logger.warn("Failed to parse LLM diagnosis JSON, falling back to plain text blueprint", parseException);
                return LlmDiagnosisResult.success(buildPlainTextBlueprint(content, incident.getServiceType()));
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
            "You are a senior CDP and Hadoop platform diagnosis expert. "
                + "You specialize in Cloudera Manager, role logs, JMX, HDFS, YARN, Hive, Impala, Spark, Kafka, HBase, Ranger, and ZooKeeper. "
                + "You must return a single JSON object only. "
                + "Do not output markdown, explanations, or code fences. "
                + "Required fields: rootCause, confidence, impactLevel, crossComponentPath, recommendations, followUps, "
                + "actionName, actionType, actionRiskLevel, actionRequiresApproval, actionRecommendationText. "
                + "Use current evidence first. Do not treat historical incidents as current facts."
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
                                       List<KnowledgeSuggestionRecord> knowledgeSuggestions) {
        StringBuilder builder = new StringBuilder();
        builder.append("Analyze the incident below and return JSON only.\n");
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
        builder.append("Evidence:\n");
        appendList(builder, incident.getEvidence(), 6, 240);
        builder.append("Avoided actions:\n");
        appendList(builder, incident.getAvoidedActions(), 3, 180);
        if (knowledgeSuggestions != null && !knowledgeSuggestions.isEmpty()) {
            builder.append("Knowledge matches:\n");
            for (KnowledgeSuggestionRecord suggestion : knowledgeSuggestions) {
                builder.append("- title: ").append(defaultIfBlank(suggestion.getTitle(), "Untitled knowledge article"))
                    .append(" | domain: ").append(defaultIfBlank(suggestion.getDomain(), "UNKNOWN"))
                    .append(" | summary: ").append(truncate(defaultIfBlank(suggestion.getSummary(), "N/A"), 200))
                    .append("\n");
            }
        }
        builder.append("Requirements:\n");
        builder.append("- Base the diagnosis on current evidence only.\n");
        builder.append("- Prefer role logs, JMX, service state, SQL execution context, and dependencies.\n");
        builder.append("- If evidence is insufficient, say that in followUps instead of inventing facts.\n");
        return builder.toString();
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
        String rootCause = truncate(
            readText(root, "rootCause", safe(serviceType) + " shows abnormal behavior and needs more evidence."),
            256
        );
        double confidence = clamp(readDouble(root, "confidence", 0.66d), 0.1d, 0.95d);
        String impactLevel = normalizeOneOf(readText(root, "impactLevel", "SEV2"), Arrays.asList("SEV1", "SEV2", "SEV3"), "SEV2");
        String crossComponentPath = truncate(readText(root, "crossComponentPath", safe(serviceType)), 128);
        List<String> recommendations = readTextList(root.get("recommendations"), 3, "Validate current evidence before expanding remediation scope.");
        List<String> followUps = readTextList(root.get("followUps"), 2, "Collect more evidence and refresh the diagnosis.");
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

    private DiagnosisBlueprint buildPlainTextBlueprint(String content, String serviceType) {
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

        String rootCause = truncate(lines.isEmpty() ? normalized : lines.get(0), 256);
        List<String> recommendations = new ArrayList<String>();
        for (int index = 1; index < lines.size() && recommendations.size() < 3; index++) {
            recommendations.add(lines.get(index));
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Validate current evidence before expanding remediation scope.");
        }

        return new DiagnosisBlueprint(
            rootCause,
            0.72d,
            "SEV2",
            safe(serviceType),
            recommendations,
            Arrays.asList("Collect more runtime evidence.", "If the action is risky, route it through approval."),
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

    private String summarizeResponse(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return "Empty response body.";
        }
        try {
            return truncate(objectMapper.writeValueAsString(response), 500);
        } catch (Exception ignored) {
            return "Response body could not be serialized.";
        }
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
