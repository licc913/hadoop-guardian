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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

@Component
public class ClouderaManagerClient {

    private final RestTemplate restTemplate;

    public ClouderaManagerClient(RestTemplate clouderaManagerRestTemplate) {
        this.restTemplate = clouderaManagerRestTemplate;
    }

    public ApiAlertsResponse fetchAlerts(ClouderaManagerSettingsEntity settings) {
        return fetchAlerts(settings, resolveApiVersion(settings));
    }

    public ApiAlertsResponse fetchAlerts(ClouderaManagerSettingsEntity settings, String apiVersion) {
        String path = buildAlertsEndpoint(settings, apiVersion);
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
        return fetchCurrentServices(settings, resolveApiVersion(settings));
    }

    public JsonNode fetchCurrentServices(ClouderaManagerSettingsEntity settings, String apiVersion) {
        String clusterName = resolveClusterPathName(settings, apiVersion);
        String path = buildServicesEndpoint(settings, apiVersion, clusterName);
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                path,
                HttpMethod.GET,
                buildRequest(settings),
                JsonNode.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException exception) {
            if (exception.getRawStatusCode() == 404 || exception.getRawStatusCode() == 500) {
                String fallbackPath = String.format("%s/api/%s/clusters/%s/services",
                    trimTrailingSlash(settings.getBaseUrl()),
                    safe(apiVersion),
                    encodeSegment(clusterName));
                ResponseEntity<JsonNode> fallbackResponse = restTemplate.exchange(
                    fallbackPath,
                    HttpMethod.GET,
                    buildRequest(settings),
                    JsonNode.class
                );
                return fallbackResponse.getBody();
            }
            throw exception;
        }
    }

    public JsonNode fetchServiceRoles(ClouderaManagerSettingsEntity settings, String serviceName) {
        return fetchServiceRoles(settings, serviceName, resolveApiVersion(settings));
    }

    public JsonNode fetchServiceRoles(ClouderaManagerSettingsEntity settings, String serviceName, String apiVersion) {
        String clusterName = resolveClusterPathName(settings, apiVersion);
        String path = buildRolesEndpoint(settings, serviceName, apiVersion, clusterName);
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                path,
                HttpMethod.GET,
                buildRequest(settings),
                JsonNode.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException exception) {
            if (exception.getRawStatusCode() == 404 || exception.getRawStatusCode() == 500) {
                String fallbackPath = String.format("%s/api/%s/clusters/%s/services/%s/roles",
                    trimTrailingSlash(settings.getBaseUrl()),
                    safe(apiVersion),
                    encodeSegment(clusterName),
                    encodeSegment(serviceName));
                ResponseEntity<JsonNode> fallbackResponse = restTemplate.exchange(
                    fallbackPath,
                    HttpMethod.GET,
                    buildRequest(settings),
                    JsonNode.class
                );
                return fallbackResponse.getBody();
            }
            throw exception;
        }
    }

    public String fetchRoleFullLog(ClouderaManagerSettingsEntity settings, String serviceName, String roleName) {
        return fetchRoleFullLog(settings, serviceName, roleName, resolveApiVersion(settings));
    }

    public String fetchRoleFullLog(ClouderaManagerSettingsEntity settings, String serviceName, String roleName, String apiVersion) {
        String path = buildRoleFullLogEndpoint(settings, serviceName, roleName, apiVersion);
        ResponseEntity<String> response = restTemplate.exchange(
            path,
            HttpMethod.GET,
            buildPlainTextRequest(settings),
            String.class
        );
        return response.getBody();
    }

    public JsonNode fetchClusters(ClouderaManagerSettingsEntity settings, String apiVersion) {
        String path = String.format("%s/api/%s/clusters",
            trimTrailingSlash(settings.getBaseUrl()),
            safe(apiVersion));
        ResponseEntity<JsonNode> response = restTemplate.exchange(
            path,
            HttpMethod.GET,
            buildRequest(settings),
            JsonNode.class
        );
        return response.getBody();
    }

    public String buildAlertsEndpoint(ClouderaManagerSettingsEntity settings) {
        return buildAlertsEndpoint(settings, resolveApiVersion(settings));
    }

    public String buildAlertsEndpoint(ClouderaManagerSettingsEntity settings, String apiVersion) {
        return String.format("%s/api/%s/events?query=alert==true",
            trimTrailingSlash(settings.getBaseUrl()),
            safe(apiVersion));
    }

    public String buildServicesEndpoint(ClouderaManagerSettingsEntity settings) {
        return buildServicesEndpoint(settings, resolveApiVersion(settings));
    }

    public String buildServicesEndpoint(ClouderaManagerSettingsEntity settings, String apiVersion) {
        return buildServicesEndpoint(settings, apiVersion, resolveClusterPathName(settings, apiVersion));
    }

    public String buildServicesEndpoint(ClouderaManagerSettingsEntity settings, String apiVersion, String clusterName) {
        return String.format("%s/api/%s/clusters/%s/services?view=full",
            trimTrailingSlash(settings.getBaseUrl()),
            safe(apiVersion),
            encodeSegment(clusterName));
    }

    public String buildRolesEndpoint(ClouderaManagerSettingsEntity settings, String serviceName) {
        return buildRolesEndpoint(settings, serviceName, resolveApiVersion(settings));
    }

    public String buildRolesEndpoint(ClouderaManagerSettingsEntity settings, String serviceName, String apiVersion) {
        return buildRolesEndpoint(settings, serviceName, apiVersion, resolveClusterPathName(settings, apiVersion));
    }

    public String buildRolesEndpoint(ClouderaManagerSettingsEntity settings, String serviceName, String apiVersion, String clusterName) {
        return String.format("%s/api/%s/clusters/%s/services/%s/roles?view=full",
            trimTrailingSlash(settings.getBaseUrl()),
            safe(apiVersion),
            encodeSegment(clusterName),
            encodeSegment(serviceName));
    }

    public String buildRoleFullLogEndpoint(ClouderaManagerSettingsEntity settings, String serviceName, String roleName) {
        return buildRoleFullLogEndpoint(settings, serviceName, roleName, resolveApiVersion(settings));
    }

    public String buildRoleFullLogEndpoint(ClouderaManagerSettingsEntity settings, String serviceName, String roleName, String apiVersion) {
        return buildRoleFullLogEndpoint(settings, serviceName, roleName, apiVersion, resolveClusterPathName(settings, apiVersion));
    }

    public String buildRoleFullLogEndpoint(ClouderaManagerSettingsEntity settings,
                                           String serviceName,
                                           String roleName,
                                           String apiVersion,
                                           String clusterName) {
        return String.format("%s/api/%s/clusters/%s/services/%s/roles/%s/logs/full",
            trimTrailingSlash(settings.getBaseUrl()),
            safe(apiVersion),
            encodeSegment(clusterName),
            encodeSegment(serviceName),
            encodeSegment(roleName));
    }

    public String resolveApiVersion(ClouderaManagerSettingsEntity settings) {
        String discoveredVersion = discoverApiVersion(settings);
        if (!isBlank(discoveredVersion)) {
            return discoveredVersion;
        }
        return safe(settings.getApiVersion());
    }

    public String resolveClusterPathName(ClouderaManagerSettingsEntity settings, String apiVersion) {
        String configured = safe(settings.getClusterName());
        try {
            JsonNode response = fetchClusters(settings, apiVersion);
            JsonNode items = response == null ? null : response.get("items");
            if (items == null || !items.isArray()) {
                return configured;
            }

            if (items.size() == 1) {
                String singleName = text(items.get(0), "name");
                if (hasText(singleName)) {
                    return singleName;
                }
            }

            if (isBlank(configured)) {
                return configured;
            }

            for (JsonNode item : items) {
                String name = text(item, "name");
                String displayName = text(item, "displayName");
                if (matchesClusterName(configured, name) || matchesClusterName(configured, displayName)) {
                    return hasText(name) ? name : configured;
                }
            }

            for (JsonNode item : items) {
                String name = text(item, "name");
                String displayName = text(item, "displayName");
                if (containsClusterName(name, configured) || containsClusterName(displayName, configured)) {
                    return hasText(name) ? name : configured;
                }
            }

            if (items.size() > 0) {
                String firstName = text(items.get(0), "name");
                if (hasText(firstName)) {
                    return firstName;
                }
            }
        } catch (Exception ignored) {
            return configured;
        }
        return configured;
    }

    private String discoverApiVersion(ClouderaManagerSettingsEntity settings) {
        try {
            String path = String.format("%s/api/version", trimTrailingSlash(settings.getBaseUrl()));
            ResponseEntity<String> response = restTemplate.exchange(
                path,
                HttpMethod.GET,
                buildPlainTextRequest(settings),
                String.class
            );
            return safe(response.getBody());
        } catch (Exception ignored) {
            return safe(settings.getApiVersion());
        }
    }

    private HttpEntity<Void> buildRequest(ClouderaManagerSettingsEntity settings) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(settings.getUsername(), settings.getPassword(), StandardCharsets.UTF_8);
        return new HttpEntity<Void>(headers);
    }

    private HttpEntity<Void> buildPlainTextRequest(ClouderaManagerSettingsEntity settings) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.TEXT_PLAIN));
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean hasText(String value) {
        return !isBlank(value);
    }

    private String encodeSegment(String value) {
        return UriUtils.encodePathSegment(safe(value), StandardCharsets.UTF_8);
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private boolean containsIgnoreCase(String left, String right) {
        return hasText(left) && hasText(right) && left.toLowerCase().contains(right.toLowerCase());
    }

    private boolean matchesClusterName(String left, String right) {
        if (!hasText(left) || !hasText(right)) {
            return false;
        }
        String normalizedLeft = normalizeClusterName(left);
        String normalizedRight = normalizeClusterName(right);
        return hasText(normalizedLeft) && normalizedLeft.equals(normalizedRight);
    }

    private boolean containsClusterName(String left, String right) {
        if (!hasText(left) || !hasText(right)) {
            return false;
        }
        String normalizedLeft = normalizeClusterName(left);
        String normalizedRight = normalizeClusterName(right);
        return hasText(normalizedLeft)
            && hasText(normalizedRight)
            && normalizedLeft.contains(normalizedRight);
    }

    private String normalizeClusterName(String value) {
        return safe(value)
            .toLowerCase()
            .replaceAll("[\\s_\\-]", "");
    }
}
