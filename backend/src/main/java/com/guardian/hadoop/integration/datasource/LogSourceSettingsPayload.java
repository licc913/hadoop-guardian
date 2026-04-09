package com.guardian.hadoop.integration.datasource;

public class LogSourceSettingsPayload {

    private boolean enabled;
    private String providerType;
    private String baseUrl;
    private String authType;
    private String authToken;
    private boolean authTokenConfigured;
    private String indexPattern;
    private Integer defaultTimeWindowMinutes;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public boolean isAuthTokenConfigured() {
        return authTokenConfigured;
    }

    public void setAuthTokenConfigured(boolean authTokenConfigured) {
        this.authTokenConfigured = authTokenConfigured;
    }

    public String getIndexPattern() {
        return indexPattern;
    }

    public void setIndexPattern(String indexPattern) {
        this.indexPattern = indexPattern;
    }

    public Integer getDefaultTimeWindowMinutes() {
        return defaultTimeWindowMinutes;
    }

    public void setDefaultTimeWindowMinutes(Integer defaultTimeWindowMinutes) {
        this.defaultTimeWindowMinutes = defaultTimeWindowMinutes;
    }

    public static LogSourceSettingsPayload fromEntity(LogSourceSettingsEntity entity) {
        LogSourceSettingsPayload payload = new LogSourceSettingsPayload();
        payload.setEnabled(entity.isEnabled());
        payload.setProviderType(entity.getProviderType());
        payload.setBaseUrl(entity.getBaseUrl());
        payload.setAuthType(entity.getAuthType());
        payload.setAuthToken("");
        payload.setAuthTokenConfigured(entity.getAuthToken() != null && !entity.getAuthToken().trim().isEmpty());
        payload.setIndexPattern(entity.getIndexPattern());
        payload.setDefaultTimeWindowMinutes(entity.getDefaultTimeWindowMinutes());
        return payload;
    }
}
