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
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
public class LlmConnectionTestService {

    private final DiagnosisLlmSettingsService settingsService;

    public LlmConnectionTestService(DiagnosisLlmSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public IntegrationTestResponse testConnection() {
        DiagnosisLlmSettingsEntity settings = settingsService.getEffectiveSettings();
        Instant now = Instant.now();
        boolean configured = hasText(settings.getEndpoint()) && hasText(settings.getApiKey()) && hasText(settings.getModel());
        if (!configured) {
            return new IntegrationTestResponse(
                false,
                false,
                "大模型配置不完整。",
                "请检查接口地址、API Key 和模型名称是否都已填写。",
                settings.getEndpoint(),
                now
            );
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
                payload.put("max_tokens", Math.min(settings.getMaxTokens(), 128));
            }
            payload.put("messages", buildMessages());

            ResponseEntity<Map> response = createRestTemplate(settings).postForEntity(
                settings.getEndpoint().trim(),
                new HttpEntity<Map<String, Object> >(payload, headers),
                Map.class
            );

            if (!settings.isEnabled()) {
                return new IntegrationTestResponse(
                    false,
                    true,
                    "大模型接口可达，但诊断链路尚未启用。",
                    "HTTP " + response.getStatusCodeValue()
                        + "，接口已返回内容，但“启用大模型诊断”开关当前未勾选。若希望事件详情页直接调用模型，请先启用该开关后重新保存。",
                    settings.getEndpoint().trim(),
                    now
                );
            }

            return new IntegrationTestResponse(
                true,
                true,
                "大模型接口连接成功。",
                "HTTP " + response.getStatusCodeValue() + "，模型已返回响应内容，且诊断链路处于启用状态。",
                settings.getEndpoint().trim(),
                now
            );
        } catch (RestClientResponseException ex) {
            return new IntegrationTestResponse(
                false,
                true,
                "大模型接口请求失败。",
                "HTTP " + ex.getRawStatusCode() + " " + ex.getStatusText() + " | " + truncate(ex.getResponseBodyAsString(), 800),
                settings.getEndpoint().trim(),
                now
            );
        } catch (Exception ex) {
            return new IntegrationTestResponse(
                false,
                true,
                "大模型接口连接失败。",
                defaultIfBlank(ex.getMessage(), ex.getClass().getSimpleName()),
                settings.getEndpoint().trim(),
                now
            );
        }
    }

    private List<Map<String, String> > buildMessages() {
        List<Map<String, String> > messages = new ArrayList<Map<String, String> >();
        messages.add(message("system", "你是连接测试助手。请直接返回一句简短中文。"));
        messages.add(message("user", "请回复：连接测试成功"));
        return messages;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<String, String>();
        item.put("role", role);
        item.put("content", content);
        return item;
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
