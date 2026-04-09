package com.guardian.hadoop.integration.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class JmxProbeService {

    private final JmxEndpointRepository jmxEndpointRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public JmxProbeService(JmxEndpointRepository jmxEndpointRepository,
                           RestTemplate clouderaManagerRestTemplate,
                           ObjectMapper objectMapper) {
        this.jmxEndpointRepository = jmxEndpointRepository;
        this.restTemplate = clouderaManagerRestTemplate;
        this.objectMapper = objectMapper;
    }

    public JmxProbeResponse testAll() {
        List<JmxProbeResult> results = new ArrayList<JmxProbeResult>();
        int successCount = 0;

        for (JmxEndpointEntity endpoint : jmxEndpointRepository.findAllByOrderByServiceTypeAscRoleTypeAscTargetHostAsc()) {
            if (!endpoint.isEnabled()) {
                continue;
            }
            JmxProbeResult result = testEndpoint(endpoint);
            results.add(result);
            if (result.isSuccess()) {
                successCount++;
            }
        }

        return new JmxProbeResponse(results.size(), successCount, results.size() - successCount, results, Instant.now());
    }

    private JmxProbeResult testEndpoint(JmxEndpointEntity endpoint) {
        JmxProbeResult result = new JmxProbeResult();
        result.setEndpointId(endpoint.getId());
        result.setServiceType(endpoint.getServiceType());
        result.setRoleType(endpoint.getRoleType());
        result.setTargetHost(endpoint.getTargetHost());

        String url = buildUrl(endpoint);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            if ("BASIC".equalsIgnoreCase(endpoint.getAuthType())
                && hasText(endpoint.getUsername())
                && hasText(endpoint.getPassword())) {
                headers.setBasicAuth(endpoint.getUsername(), endpoint.getPassword(), StandardCharsets.UTF_8);
            }

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<String>(headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode beans = root.path("beans");
            if (!beans.isArray()) {
                result.setSuccess(false);
                result.setMessage("JMX 返回内容缺少 beans 数组。");
                return result;
            }

            result.setBeanCount(beans.size());
            result.setSampleMetrics(extractSamples(beans, endpoint.getMetricWhitelist()));
            result.setSuccess(true);
            result.setMessage("JMX 指标抓取成功。");
            return result;
        } catch (Exception exception) {
            result.setSuccess(false);
            result.setMessage(trimMessage(exception.getMessage()));
            return result;
        }
    }

    private List<String> extractSamples(JsonNode beans, String whitelist) {
        List<String> samples = new ArrayList<String>();
        List<String> tokens = splitWhitelist(whitelist);

        for (JsonNode bean : beans) {
            String name = bean.path("name").asText();
            if (!tokens.isEmpty() && !matchesAny(name, tokens)) {
                continue;
            }
            if (hasText(name)) {
                samples.add(name);
            }
            if (samples.size() >= 5) {
                break;
            }
        }

        if (samples.isEmpty()) {
            for (int index = 0; index < beans.size() && index < 3; index++) {
                String name = beans.get(index).path("name").asText();
                if (hasText(name)) {
                    samples.add(name);
                }
            }
        }

        return samples;
    }

    private List<String> splitWhitelist(String whitelist) {
        List<String> values = new ArrayList<String>();
        if (!hasText(whitelist)) {
            return values;
        }
        for (String token : whitelist.split(",")) {
            if (hasText(token)) {
                values.add(token.trim().toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    private boolean matchesAny(String beanName, List<String> tokens) {
        String normalized = beanName == null ? "" : beanName.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String trimMessage(String value) {
        if (!hasText(value)) {
            return "无法连接 JMX 端点。";
        }
        return value.trim();
    }

    private String buildUrl(JmxEndpointEntity endpoint) {
        EndpointAddress address = normalizeAddress(endpoint);
        return address.protocol + "://" + address.host + ":" + address.port + normalizePath(endpoint.getPath());
    }

    private EndpointAddress normalizeAddress(JmxEndpointEntity endpoint) {
        String fallbackProtocol = hasText(endpoint.getProtocol()) ? endpoint.getProtocol().trim().toLowerCase(Locale.ROOT) : "http";
        String rawTarget = endpoint.getTargetHost() == null ? "" : endpoint.getTargetHost().trim();
        int fallbackPort = endpoint.getPort() > 0 ? endpoint.getPort() : 80;

        if (!hasText(rawTarget)) {
            return new EndpointAddress(fallbackProtocol, "localhost", fallbackPort);
        }

        try {
            String candidate = rawTarget.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")
                ? rawTarget
                : fallbackProtocol + "://" + rawTarget;
            URI uri = URI.create(candidate);
            String protocol = hasText(uri.getScheme()) ? uri.getScheme().toLowerCase(Locale.ROOT) : fallbackProtocol;
            String host = hasText(uri.getHost()) ? uri.getHost() : extractHost(rawTarget);
            int port = uri.getPort() > 0 ? uri.getPort() : fallbackPort;
            return new EndpointAddress(protocol, host, port);
        } catch (Exception ignored) {
            return new EndpointAddress(fallbackProtocol, extractHost(rawTarget), extractPort(rawTarget, fallbackPort));
        }
    }

    private String normalizePath(String path) {
        if (!hasText(path)) {
            return "/jmx";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String extractHost(String value) {
        String normalized = value.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
        int slashIndex = normalized.indexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(0, slashIndex);
        }
        int colonIndex = normalized.lastIndexOf(':');
        if (colonIndex > 0) {
            return normalized.substring(0, colonIndex);
        }
        return normalized;
    }

    private int extractPort(String value, int fallbackPort) {
        String normalized = value.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
        int slashIndex = normalized.indexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(0, slashIndex);
        }
        int colonIndex = normalized.lastIndexOf(':');
        if (colonIndex > 0 && colonIndex < normalized.length() - 1) {
            try {
                return Integer.parseInt(normalized.substring(colonIndex + 1));
            } catch (NumberFormatException ignored) {
                return fallbackPort;
            }
        }
        return fallbackPort;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static class EndpointAddress {
        private final String protocol;
        private final String host;
        private final int port;

        private EndpointAddress(String protocol, String host, int port) {
            this.protocol = protocol;
            this.host = host;
            this.port = port;
        }
    }
}
