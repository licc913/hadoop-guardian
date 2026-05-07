package com.guardian.hadoop.integration.cm;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

@Component
public class ClouderaManagerClient {

    private static final int DEFAULT_ROLE_LOG_TAIL_BYTES = 32 * 1024;
    private static final int DEFAULT_ALERT_SYNC_TIMEOUT_MS = 120000;
    private static final int DEFAULT_ALERT_SYNC_LIMIT = 100;

    private final RestTemplate restTemplate;

    public ClouderaManagerClient(RestTemplate clouderaManagerRestTemplate) {
        this.restTemplate = clouderaManagerRestTemplate;
    }

    public ApiAlertsResponse fetchAlerts(ClouderaManagerSettingsEntity settings) {
        return fetchAlerts(settings, resolveApiVersion(settings));
    }

    public ApiAlertsResponse fetchAlerts(ClouderaManagerSettingsEntity settings, String apiVersion) {
        String path = buildAlertsEndpoint(settings, apiVersion);
        ResponseEntity<ApiAlertsResponse> response = customTimeoutRestTemplate(Integer.valueOf(DEFAULT_ALERT_SYNC_TIMEOUT_MS)).exchange(
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
        return fetchRoleFullLog(settings, serviceName, roleName, apiVersion, null);
    }

    public String fetchRoleFullLog(ClouderaManagerSettingsEntity settings,
                                   String serviceName,
                                   String roleName,
                                   String apiVersion,
                                   Integer readTimeoutMsOverride) {
        RestTemplate roleLogTemplate = roleLogRestTemplate(readTimeoutMsOverride);
        List<LogEndpointCandidate> candidates = buildRoleLogCandidates(settings, serviceName, roleName, apiVersion);
        HttpStatusCodeException lastHttpStatusException = null;
        Exception lastException = null;
        for (LogEndpointCandidate candidate : candidates) {
            try {
                ResponseEntity<String> response = roleLogTemplate.exchange(
                    candidate.path,
                    HttpMethod.GET,
                    candidate.tailBytes == null
                        ? buildPlainTextRequest(settings)
                        : buildPlainTextRequest(settings, candidate.tailBytes),
                    String.class
                );
                return response.getBody();
            } catch (HttpStatusCodeException exception) {
                lastHttpStatusException = exception;
                if (!isRetriableRoleLogStatus(exception)) {
                    throw exception;
                }
            } catch (Exception exception) {
                lastException = exception;
            }
        }
        if (lastHttpStatusException != null) {
            throw lastHttpStatusException;
        }
        if (lastException instanceof RuntimeException) {
            throw (RuntimeException) lastException;
        }
        if (lastException != null) {
            throw new IllegalStateException("Failed to fetch CM role log", lastException);
        }
        throw new IllegalStateException("No CM role log endpoint candidates available");
    }

    public JsonNode fetchServiceConfig(ClouderaManagerSettingsEntity settings, String serviceName) {
        return fetchServiceConfig(settings, serviceName, resolveApiVersion(settings));
    }

    public JsonNode fetchServiceConfig(ClouderaManagerSettingsEntity settings, String serviceName, String apiVersion) {
        String clusterName = resolveClusterPathName(settings, apiVersion);
        String path = buildServiceConfigEndpoint(settings, serviceName, apiVersion, clusterName);
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
                String fallbackPath = String.format("%s/api/%s/clusters/%s/services/%s/config",
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

    public JsonNode fetchRoleConfigGroups(ClouderaManagerSettingsEntity settings, String serviceName) {
        return fetchRoleConfigGroups(settings, serviceName, resolveApiVersion(settings));
    }

    public JsonNode fetchRoleConfigGroups(ClouderaManagerSettingsEntity settings, String serviceName, String apiVersion) {
        String clusterName = resolveClusterPathName(settings, apiVersion);
        String path = buildRoleConfigGroupsEndpoint(settings, serviceName, apiVersion, clusterName);
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
                String fallbackPath = String.format("%s/api/%s/clusters/%s/services/%s/roleConfigGroups",
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

    public JsonNode fetchRoleConfigGroupConfig(ClouderaManagerSettingsEntity settings, String serviceName, String roleConfigGroupName) {
        return fetchRoleConfigGroupConfig(settings, serviceName, roleConfigGroupName, resolveApiVersion(settings));
    }

    public JsonNode fetchRoleConfigGroupConfig(ClouderaManagerSettingsEntity settings,
                                               String serviceName,
                                               String roleConfigGroupName,
                                               String apiVersion) {
        String clusterName = resolveClusterPathName(settings, apiVersion);
        String path = buildRoleConfigGroupConfigEndpoint(settings, serviceName, roleConfigGroupName, apiVersion, clusterName);
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
                String fallbackPath = String.format("%s/api/%s/clusters/%s/services/%s/roleConfigGroups/%s/config",
                    trimTrailingSlash(settings.getBaseUrl()),
                    safe(apiVersion),
                    encodeSegment(clusterName),
                    encodeSegment(serviceName),
                    encodeSegment(roleConfigGroupName));
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
        return String.format("%s/api/%s/events?query=alert==true&limit=%d",
            trimTrailingSlash(settings.getBaseUrl()),
            safe(apiVersion),
            DEFAULT_ALERT_SYNC_LIMIT);
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

    public String buildServiceConfigEndpoint(ClouderaManagerSettingsEntity settings, String serviceName) {
        return buildServiceConfigEndpoint(settings, serviceName, resolveApiVersion(settings));
    }

    public String buildServiceConfigEndpoint(ClouderaManagerSettingsEntity settings, String serviceName, String apiVersion) {
        return buildServiceConfigEndpoint(settings, serviceName, apiVersion, resolveClusterPathName(settings, apiVersion));
    }

    public String buildServiceConfigEndpoint(ClouderaManagerSettingsEntity settings,
                                             String serviceName,
                                             String apiVersion,
                                             String clusterName) {
        return String.format("%s/api/%s/clusters/%s/services/%s/config?view=full",
            trimTrailingSlash(settings.getBaseUrl()),
            safe(apiVersion),
            encodeSegment(clusterName),
            encodeSegment(serviceName));
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

    public String buildRoleLogEndpoint(ClouderaManagerSettingsEntity settings,
                                       String serviceName,
                                       String roleName,
                                       String apiVersion,
                                       String clusterName,
                                       String logPath) {
        return String.format("%s/api/%s/clusters/%s/services/%s/roles/%s/logs/%s",
            trimTrailingSlash(settings.getBaseUrl()),
            safe(apiVersion),
            encodeSegment(clusterName),
            encodeSegment(serviceName),
            encodeSegment(roleName),
            encodeSegment(logPath));
    }

    public String buildRoleConfigGroupsEndpoint(ClouderaManagerSettingsEntity settings,
                                                String serviceName,
                                                String apiVersion,
                                                String clusterName) {
        return String.format("%s/api/%s/clusters/%s/services/%s/roleConfigGroups?view=full",
            trimTrailingSlash(settings.getBaseUrl()),
            safe(apiVersion),
            encodeSegment(clusterName),
            encodeSegment(serviceName));
    }

    public String buildRoleConfigGroupConfigEndpoint(ClouderaManagerSettingsEntity settings,
                                                     String serviceName,
                                                     String roleConfigGroupName,
                                                     String apiVersion,
                                                     String clusterName) {
        return String.format("%s/api/%s/clusters/%s/services/%s/roleConfigGroups/%s/config?view=full",
            trimTrailingSlash(settings.getBaseUrl()),
            safe(apiVersion),
            encodeSegment(clusterName),
            encodeSegment(serviceName),
            encodeSegment(roleConfigGroupName));
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
        return buildPlainTextRequest(settings, null);
    }

    private HttpEntity<Void> buildPlainTextRequest(ClouderaManagerSettingsEntity settings, Integer tailBytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.TEXT_PLAIN));
        headers.setBasicAuth(settings.getUsername(), settings.getPassword(), StandardCharsets.UTF_8);
        if (tailBytes != null && tailBytes.intValue() > 0) {
            headers.set(HttpHeaders.RANGE, "bytes=-" + tailBytes.intValue());
        }
        return new HttpEntity<Void>(headers);
    }

    private RestTemplate roleLogRestTemplate(Integer readTimeoutMsOverride) {
        if (readTimeoutMsOverride == null || readTimeoutMsOverride.intValue() <= 0) {
            return restTemplate;
        }
        return customTimeoutRestTemplate(readTimeoutMsOverride);
    }

    private RestTemplate customTimeoutRestTemplate(Integer timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs.intValue());
        factory.setReadTimeout(timeoutMs.intValue());
        return new RestTemplate(factory);
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

    private boolean isRetriableRoleLogStatus(HttpStatusCodeException exception) {
        HttpStatus status = exception.getStatusCode();
        return status == HttpStatus.BAD_REQUEST
            || status == HttpStatus.NOT_FOUND
            || status == HttpStatus.METHOD_NOT_ALLOWED
            || status == HttpStatus.INTERNAL_SERVER_ERROR
            || status == HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE;
    }

    private List<LogEndpointCandidate> buildRoleLogCandidates(ClouderaManagerSettingsEntity settings,
                                                              String serviceName,
                                                              String roleName,
                                                              String apiVersion) {
        String clusterName = resolveClusterPathName(settings, apiVersion);
        List<LogEndpointCandidate> candidates = new java.util.ArrayList<LogEndpointCandidate>();
        candidates.add(new LogEndpointCandidate(
            buildRoleFullLogEndpoint(settings, serviceName, roleName, apiVersion, clusterName),
            null
        ));
        candidates.add(new LogEndpointCandidate(
            buildRoleLogEndpoint(settings, serviceName, roleName, apiVersion, clusterName, "stderr"),
            null
        ));
        candidates.add(new LogEndpointCandidate(
            buildRoleLogEndpoint(settings, serviceName, roleName, apiVersion, clusterName, "stdout"),
            null
        ));
        candidates.add(new LogEndpointCandidate(
            buildRoleFullLogEndpoint(settings, serviceName, roleName, apiVersion, clusterName),
            Integer.valueOf(DEFAULT_ROLE_LOG_TAIL_BYTES)
        ));
        candidates.add(new LogEndpointCandidate(
            buildRoleLogEndpoint(settings, serviceName, roleName, apiVersion, clusterName, "stderr"),
            Integer.valueOf(DEFAULT_ROLE_LOG_TAIL_BYTES)
        ));
        candidates.add(new LogEndpointCandidate(
            buildRoleLogEndpoint(settings, serviceName, roleName, apiVersion, clusterName, "stdout"),
            Integer.valueOf(DEFAULT_ROLE_LOG_TAIL_BYTES)
        ));
        return candidates;
    }

    private static final class LogEndpointCandidate {
        private final String path;
        private final Integer tailBytes;

        private LogEndpointCandidate(String path, Integer tailBytes) {
            this.path = path;
            this.tailBytes = tailBytes;
        }
    }
}
