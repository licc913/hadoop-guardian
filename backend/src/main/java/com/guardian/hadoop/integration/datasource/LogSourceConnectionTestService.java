package com.guardian.hadoop.integration.datasource;

import java.time.Instant;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
public class LogSourceConnectionTestService {

    private static final long LOG_SOURCE_ID = 1L;

    private final LogSourceSettingsRepository repository;

    public LogSourceConnectionTestService(LogSourceSettingsRepository repository) {
        this.repository = repository;
    }

    public IntegrationTestResponse testConnection() {
        LogSourceSettingsEntity settings = repository.findById(LOG_SOURCE_ID).orElseGet(this::newDefaultEntity);
        Instant now = Instant.now();
        if (!hasText(settings.getBaseUrl())) {
            return new IntegrationTestResponse(
                false,
                false,
                "日志平台配置不完整。",
                "请至少填写日志平台基础地址。",
                settings.getBaseUrl(),
                now
            );
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE + ", text/plain, */*"));
            applyAuthorization(settings, headers);

            RestTemplate restTemplate = createRestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                settings.getBaseUrl().trim(),
                HttpMethod.GET,
                new HttpEntity<String>(headers),
                String.class
            );

            return new IntegrationTestResponse(
                true,
                true,
                "日志平台连接成功。",
                "HTTP " + response.getStatusCodeValue() + "，基础地址可访问。",
                settings.getBaseUrl().trim(),
                now
            );
        } catch (RestClientResponseException ex) {
            return new IntegrationTestResponse(
                false,
                true,
                "日志平台请求失败。",
                "HTTP " + ex.getRawStatusCode() + " " + ex.getStatusText() + " | " + truncate(ex.getResponseBodyAsString(), 800),
                settings.getBaseUrl().trim(),
                now
            );
        } catch (Exception ex) {
            return new IntegrationTestResponse(
                false,
                true,
                "日志平台连接失败。",
                defaultIfBlank(ex.getMessage(), ex.getClass().getSimpleName()),
                settings.getBaseUrl().trim(),
                now
            );
        }
    }

    private void applyAuthorization(LogSourceSettingsEntity settings, HttpHeaders headers) {
        String authType = settings.getAuthType() == null ? "" : settings.getAuthType().trim().toUpperCase();
        String token = settings.getAuthToken();
        if (!hasText(token)) {
            return;
        }
        if ("BASIC".equals(authType)) {
            headers.set("Authorization", "Basic " + token.trim());
            return;
        }
        headers.set("Authorization", "Bearer " + token.trim());
    }

    private LogSourceSettingsEntity newDefaultEntity() {
        LogSourceSettingsEntity entity = new LogSourceSettingsEntity();
        entity.setId(LOG_SOURCE_ID);
        entity.setEnabled(false);
        entity.setProviderType("ELASTICSEARCH");
        entity.setAuthType("BASIC");
        entity.setDefaultTimeWindowMinutes(30);
        entity.setIndexPattern("hadoop-*");
        return entity;
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
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
