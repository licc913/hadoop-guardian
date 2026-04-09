package com.guardian.hadoop.integration.cm;

public class ClouderaManagerSettingsResponse {

    private final boolean enabled;
    private final String baseUrl;
    private final String apiVersion;
    private final String username;
    private final String password;
    private final boolean passwordConfigured;
    private final String clusterName;
    private final boolean configured;

    public ClouderaManagerSettingsResponse(boolean enabled, String baseUrl, String apiVersion, String username,
                                           String password, boolean passwordConfigured, String clusterName, boolean configured) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.apiVersion = apiVersion;
        this.username = username;
        this.password = password;
        this.passwordConfigured = passwordConfigured;
        this.clusterName = clusterName;
        this.configured = configured;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isPasswordConfigured() {
        return passwordConfigured;
    }

    public String getClusterName() {
        return clusterName;
    }

    public boolean isConfigured() {
        return configured;
    }
}
