package com.guardian.hadoop.integration.datasource;

import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsEntity;
import com.guardian.hadoop.integration.llm.DiagnosisLlmSettingsService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
public class LlmPromptService {

    private final DiagnosisLlmSettingsService settingsService;

    public LlmPromptService(DiagnosisLlmSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public LlmPromptResponse ask(String question) {
        DiagnosisLlmSettingsEntity settings = settingsService.getEffectiveSettings();
        Instant now = Instant.now();
        if (!hasText(settings.getEndpoint()) || !hasText(settings.getApiKey()) || !hasText(settings.getModel())) {
            return new LlmPromptResponse(false, "大模型配置不完整。", "", settings.getModel(), now);
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
            payload.put("messages", buildMessages(question));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = createRestTemplate(settings).postForObject(
                settings.getEndpoint().trim(),
                new HttpEntity<Map<String, Object> >(payload, headers),
                Map.class
            );

            String answer = extractContent(response);
            if (!hasText(answer)) {
                return new LlmPromptResponse(false, "模型已响应，但没有返回可展示内容。", "", settings.getModel(), now);
            }
            return new LlmPromptResponse(true, "问答完成。", answer.trim(), settings.getModel(), now);
        } catch (RestClientResponseException ex) {
            return new LlmPromptResponse(
                false,
                "模型问答失败：HTTP " + ex.getRawStatusCode() + " " + ex.getStatusText(),
                truncate(ex.getResponseBodyAsString(), 1000),
                settings.getModel(),
                now
            );
        } catch (Exception ex) {
            return new LlmPromptResponse(false, "模型问答失败。", defaultIfBlank(ex.getMessage(), ex.getClass().getSimpleName()), settings.getModel(), now);
        }
    }

    private List<Map<String, String> > buildMessages(String question) {
        List<Map<String, String> > messages = new ArrayList<Map<String, String> >();
        messages.add(message("system", "你是 Hadoop 平台运维助手。回答要直接、简洁、面向中文运维人员。"));
        messages.add(message("user", question));
        return messages;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<String, String>();
        item.put("role", role);
        item.put("content", content);
        return item;
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

    private String truncate(String value, int maxLength) {
        String safeValue = value == null ? "" : value.trim();
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }
}
