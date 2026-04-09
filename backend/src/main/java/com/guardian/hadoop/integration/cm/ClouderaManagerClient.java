package com.guardian.hadoop.integration.cm;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ClouderaManagerClient {

    private final RestTemplate restTemplate;

    public ClouderaManagerClient(RestTemplate clouderaManagerRestTemplate) {
        this.restTemplate = clouderaManagerRestTemplate;
    }

    public ApiAlertsResponse fetchAlerts(ClouderaManagerSettingsEntity settings) {
        String path = buildAlertsEndpoint(settings);
        ResponseEntity<ApiAlertsResponse> response = restTemplate.exchange(
            path,
            HttpMethod.GET,
            buildRequest(settings),
            ApiAlertsResponse.class
        );
        ApiAlertsResponse body = response.getBody();
        return body == null ? new ApiAlertsResponse() : body;
    }

    public JsonNode fetchCurrentServices(ClouderaManagerSettingsEntity settings) {
        String path = buildServicesEndpoint(settings);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
            path,
            HttpMethod.GET,
            buildRequest(settings),
            JsonNode.class
        );
        return response.getBody();
    }

    public JsonNode fetchServiceRoles(ClouderaManagerSettingsEntity settings, String serviceName) {
        String path = buildRolesEndpoint(settings, serviceName);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
            path,
            HttpMethod.GET,
            buildRequest(settings),
            JsonNode.class
        );
        return response.getBody();
    }

    public String buildAlertsEndpoint(ClouderaManagerSettingsEntity settings) {
        return String.format("%s/api/%s/events?query=alert==true",
            trimTrailingSlash(settings.getBaseUrl()),
            safe(settings.getApiVersion()));
    }

    public String buildServicesEndpoint(ClouderaManagerSettingsEntity settings) {
        return String.format("%s/api/%s/clusters/%s/services?view=full",
            trimTrailingSlash(settings.getBaseUrl()),
            safe(settings.getApiVersion()),
            safe(settings.getClusterName()));
    }

    public String buildRolesEndpoint(ClouderaManagerSettingsEntity settings, String serviceName) {
        return String.format("%s/api/%s/clusters/%s/services/%s/roles?view=full",
            trimTrailingSlash(settings.getBaseUrl()),
            safe(settings.getApiVersion()),
            safe(settings.getClusterName()),
            safe(serviceName));
    }

    private HttpEntity<Void> buildRequest(ClouderaManagerSettingsEntity settings) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(settings.getUsername(), settings.getPassword(), StandardCharsets.UTF_8);
        return new HttpEntity<Void>(headers);
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
