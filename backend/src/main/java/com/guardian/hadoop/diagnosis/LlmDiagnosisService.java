package com.guardian.hadoop.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardian.hadoop.incident.IncidentEntity;
import com.guardian.hadoop.integration.cm.CmServiceLogSnapshotRecord;
import com.guardian.hadoop.integration.cm.CmServiceLogSnapshotService;
import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsEntity;
import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsService;
import com.guardian.hadoop.integration.llm.LlmCallRecordService;
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
    private final LlmCallRecordService llmCallRecordService;

    public LlmDiagnosisService(DiagnosisLlmSettingsService settingsService,
                               ObjectMapper objectMapper,
                               KnowledgeSuggestionService knowledgeSuggestionService,
                               CmServiceLogSnapshotService logSnapshotService,
                               LlmCallRecordService llmCallRecordService) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.knowledgeSuggestionService = knowledgeSuggestionService;
        this.logSnapshotService = logSnapshotService;
        this.llmCallRecordService = llmCallRecordService;
    }

    public LlmDiagnosisResult generate(IncidentEntity incident, String triggerReason, String triggerBy) {
        DiagnosisLlmSettingsEntity settings = settingsService.getEffectiveSettings();
        boolean configured = hasText(settings.getEndpoint()) && hasText(settings.getApiKey()) && hasText(settings.getModel());
        if (!settings.isEnabled() && !configured) {
            return LlmDiagnosisResult.failure("AI diagnosis is disabled.", "Please enable and save LLM settings first.");
        }
        if (!configured) {
            return LlmDiagnosisResult.failure("AI diagnosis settings are incomplete.", "Please check endpoint, apiKey and model.");
        }

        List<CmServiceLogSnapshotRecord> serviceLogs = logSnapshotService.getLogsForIncident(incident);
        List<KnowledgeSuggestionRecord> knowledgeSuggestions = knowledgeSuggestionService.search(
            incident.getServiceType(),
            buildKnowledgeQuery(incident, serviceLogs),
            3
        );
        String prompt = buildIncidentPrompt(incident, triggerReason, triggerBy, knowledgeSuggestions, serviceLogs);
        Long callRecordId = llmCallRecordService.start("DIAGNOSIS", settings.getModel(), prompt);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(settings.getApiKey().trim());

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("model", settings.getModel().trim());
            payload.put("stream", Boolean.FALSE);
            payload.put("temperature", settings.getTemperature());
            payload.put("messages", buildMessages(prompt));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = createRestTemplate(settings, prompt).postForObject(
                settings.getEndpoint().trim(),
                new HttpEntity<Map<String, Object> >(payload, headers),
                Map.class
            );

            String content = extractContent(response);
            if (!hasText(content)) {
                llmCallRecordService.fail(callRecordId, summarizeMissingContentResponse(response));
                return LlmDiagnosisResult.failure(
                    "AI returned a response without final diagnosis content.",
                    summarizeMissingContentResponse(response)
                );
            }
            llmCallRecordService.finish(callRecordId, content);

            try {
                return LlmDiagnosisResult.success(parseBlueprint(content, incident.getServiceType()));
            } catch (IOException parseException) {
                if (looksLikeReasoningOnly(content)) {
                    return LlmDiagnosisResult.failure(
                        "AI returned reasoning text but no final diagnosis JSON.",
                        "Please increase max output tokens or use a chat model that returns final content."
                    );
                }
                logger.warn("Failed to parse LLM diagnosis JSON, falling back to plain text blueprint", parseException);
                return LlmDiagnosisResult.success(buildPlainTextBlueprint(content));
            }
        } catch (RestClientResponseException exception) {
            logger.warn("External LLM diagnosis request failed", exception);
            llmCallRecordService.fail(callRecordId, buildHttpErrorDetails(exception));
            return LlmDiagnosisResult.failure("AI request failed.", buildHttpErrorDetails(exception));
        } catch (ResourceAccessException exception) {
            logger.warn("External LLM diagnosis timed out or was unreachable", exception);
            llmCallRecordService.fail(callRecordId, defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName()));
            Throwable root = exception.getMostSpecificCause();
            if (root instanceof SocketTimeoutException) {
                return LlmDiagnosisResult.failure(
                    "AI diagnosis timed out.",
                    "Current read timeout=" + effectiveReadTimeout(settings, prompt) + "ms. Increase timeout or reduce prompt size."
                );
            }
            return LlmDiagnosisResult.failure(
                "AI request failed.",
                defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName())
            );
        } catch (Exception exception) {
            logger.warn("Failed to generate diagnosis from external LLM", exception);
            llmCallRecordService.fail(callRecordId, defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName()));
            return LlmDiagnosisResult.failure(
                "AI response could not be parsed.",
                defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName())
            );
        }
    }

    private String buildKnowledgeQuery(IncidentEntity incident, List<CmServiceLogSnapshotRecord> serviceLogs) {
        StringBuilder builder = new StringBuilder();
        builder.append(safe(incident.getTitle())).append('\n');
        builder.append(safe(incident.getSummary())).append('\n');
        builder.append(safe(incident.getImpactScope())).append('\n');
        builder.append(String.join("\n", incident.getEvidence())).append('\n');
        if (serviceLogs != null) {
            for (CmServiceLogSnapshotRecord log : serviceLogs) {
                builder.append(defaultIfBlank(log.getLogText(), "")).append('\n');
            }
        }
        return builder.toString();
    }
    private List<Map<String, String> > buildMessages(String prompt) {
        List<Map<String, String> > messages = new ArrayList<Map<String, String> >();
        messages.add(message(
            "system",
            "You are a senior CDP/Hadoop operations diagnosis expert. Return exactly one JSON object and no Markdown. "
                + "The JSON field names must be: rootCause, confidence, impactLevel, crossComponentPath, recommendations, followUps, "
                + "actionName, actionType, actionRiskLevel, actionRequiresApproval, actionRecommendationText. "
                + "All natural-language values must be Simplified Chinese. "
                + "Base the diagnosis primarily on the current WARN/ERROR service logs and incident detail evidence. Do not invent symptoms. "
                + "rootCause must be detailed and structured for operators, including symptom, exact evidence, direct cause, impacted role/host, impact scope, reasoning, and uncertainty. "
                + "recommendations must contain at least 6 actionable steps; each step must include purpose, exact check/action, validation, and risk. "
                + "followUps must contain at least 4 evidence or verification items. "
                + "actionRecommendationText must be a complete SOP, not a one-line summary. "
                + "Ignore knowledge-base entries that are not clearly relevant to the current service and log keywords."
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
        builder.append("Analyze the incident below and return exactly one JSON object. All natural-language JSON values must be Simplified Chinese.\n");
        builder.append("Output depth: provide a detailed operations diagnosis, not a short conclusion. Expand rootCause, recommendations, followUps and actionRecommendationText fully.\n");
        builder.append("Incident full metadata:\n");
        builder.append("id: ").append(incident.getId()).append("\n");
        builder.append("incidentNo: ").append(safe(incident.getIncidentNo())).append("\n");
        builder.append("sourceType: ").append(safe(incident.getSourceType())).append("\n");
        builder.append("sourceId: ").append(safe(incident.getSourceId())).append("\n");
        builder.append("clusterName: ").append(safe(incident.getClusterName())).append("\n");
        builder.append("serviceType: ").append(safe(incident.getServiceType())).append("\n");
        builder.append("severity: ").append(safe(incident.getSeverity())).append("\n");
        builder.append("status: ").append(safe(incident.getStatus())).append("\n");
        builder.append("governanceStatus: ").append(safe(incident.getGovernanceStatus())).append("\n");
        builder.append("title: ").append(safe(incident.getTitle())).append("\n");
        builder.append("summary: ").append(safe(incident.getSummary())).append("\n");
        builder.append("impactScope: ").append(safe(incident.getImpactScope())).append("\n");
        builder.append("owner: ").append(defaultIfBlank(incident.getOwner(), "UNKNOWN")).append("\n");
        builder.append("eventFingerprint: ").append(safe(incident.getEventFingerprint())).append("\n");
        builder.append("occurredAt: ").append(incident.getOccurredAt()).append("\n");
        builder.append("firstSeenAt: ").append(incident.getFirstSeenAt()).append("\n");
        builder.append("lastSeenAt: ").append(incident.getLastSeenAt()).append("\n");
        builder.append("occurrenceCount: ").append(incident.getOccurrenceCount()).append("\n");
        builder.append("suppressedUntil: ").append(incident.getSuppressedUntil()).append("\n");
        builder.append("governanceNote: ").append(safe(incident.getGovernanceNote())).append("\n");
        builder.append("triggerBy: ").append(defaultIfBlank(triggerBy, "UNKNOWN")).append("\n");
        builder.append("triggerReason: ").append(defaultIfBlank(triggerReason, "manual diagnosis from incident detail page")).append("\n");
        builder.append("\nCurrent WARN/ERROR service logs (highest priority, same as frontend diagnosis input):\n");
        appendServiceLogs(builder, serviceLogs);
        builder.append("\nEvidence from incident detail page:\n");
        appendList(builder, incident.getEvidence());
        builder.append("\nAvoided actions from incident detail page:\n");
        appendList(builder, incident.getAvoidedActions());
        if (knowledgeSuggestions != null && !knowledgeSuggestions.isEmpty()) {
            builder.append("\nKnowledge matches (already filtered by service domain and log/event keywords; still ignore any item that conflicts with logs):\n");
            for (KnowledgeSuggestionRecord suggestion : knowledgeSuggestions) {
                builder.append("- title: ").append(defaultIfBlank(suggestion.getTitle(), "Untitled knowledge article"))
                    .append(" | domain: ").append(defaultIfBlank(suggestion.getDomain(), "UNKNOWN"))
                    .append(" | matchedKeywords: ").append(String.join(", ", suggestion.getMatchedKeywords()))
                    .append(" | summary: ").append(defaultIfBlank(suggestion.getSummary(), "N/A"))
                    .append("\n");
            }
        } else {
            builder.append("\nKnowledge matches: none. Do not force a knowledge-base solution.\n");
        }
        builder.append("\nOperational answer contract:\n");
        builder.append("- Use only WARN/ERROR service logs as the primary diagnosis evidence. If no WARN/ERROR log exists, say evidence is insufficient and list what to collect next.\n");
        builder.append("- Do not use a knowledge article unless it is clearly related to the current service and log keywords.\n");
        builder.append("- rootCause must include: symptom, exact log evidence, likely direct cause, impacted role/host, impact scope, reasoning, uncertainty, and actions not to take yet.\n");
        builder.append("- recommendations must include at least 6 ordered operator steps. Each step must state purpose, concrete check/action, expected result, next decision, and risk/rollback note.\n");
        builder.append("- followUps must include at least 4 missing evidence or validation items, not duplicates of recommendations.\n");
        builder.append("- actionRecommendationText must be a complete SOP: pre-checks, read-only verification, low-risk remediation, post-action validation, rollback/escalation criteria.\n");
        builder.append("- Do not perform cross-component speculation. crossComponentPath should be an empty string unless the current service logs explicitly prove it.\n");
        return builder.toString();
    }

    private void appendServiceLogs(StringBuilder builder,
                                   List<CmServiceLogSnapshotRecord> serviceLogs) {
        if (serviceLogs == null || serviceLogs.isEmpty()) {
            builder.append("- none\n");
            return;
        }
        for (CmServiceLogSnapshotRecord value : serviceLogs) {
            builder.append("- ")
                .append(renderServiceLogForPrompt(value))
                .append("\n");
        }
    }

    private String renderServiceLogForPrompt(CmServiceLogSnapshotRecord value) {
        return "["
            + defaultIfBlank(value.getLogType(), "LOG")
            + "] "
            + "cluster=" + defaultIfBlank(value.getClusterName(), "UNKNOWN_CLUSTER")
            + " | serviceName=" + defaultIfBlank(value.getServiceName(), "UNKNOWN_SERVICE")
            + " | serviceType=" + defaultIfBlank(value.getServiceType(), "UNKNOWN")
            + " | collectedAt=" + value.getCollectedAt()
            + " | log="
            + defaultIfBlank(value.getLogText(), "empty");
    }

    private void appendList(StringBuilder builder, List<String> values) {
        if (values == null || values.isEmpty()) {
            builder.append("- none\n");
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
        String rootCause = readText(root, "rootCause", safe(serviceType) + " shows abnormal behavior and needs more evidence.");
        double confidence = clamp(readDouble(root, "confidence", 0.66d), 0.1d, 0.95d);
        String impactLevel = normalizeOneOf(readText(root, "impactLevel", "SEV2"), Arrays.asList("SEV1", "SEV2", "SEV3"), "SEV2");
        String crossComponentPath = truncate(readText(root, "crossComponentPath", ""), 128);
        List<String> recommendations = readTextList(root.get("recommendations"), "Validate current evidence before expanding remediation scope.");
        List<String> followUps = readTextList(root.get("followUps"), "Collect more evidence and refresh the diagnosis.");
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

        String rootCause = normalized;
        List<String> recommendations = new ArrayList<String>();
        for (int index = 0; index < lines.size(); index++) {
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
            return "Response contains reasoning_content but choices[0].message.content is empty. Increase max output tokens or use a non-reasoning chat model for diagnosis JSON.";
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

    private List<String> readTextList(JsonNode node, String fallback) {
        List<String> values = new ArrayList<String>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && hasText(item.asText())) {
                    values.add(item.asText().trim());
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



