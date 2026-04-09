package com.guardian.hadoop.integration.llm;

public class DiagnosisLlmSettingsPayload {

    private boolean enabled;
    private String endpoint;
    private String apiKey;
    private boolean apiKeyConfigured;
    private String model;
    private int connectTimeoutMs;
    private int readTimeoutMs;
    private double temperature;
    private int maxTokens;
    private boolean configured;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isApiKeyConfigured() {
        return apiKeyConfigured;
    }

    public void setApiKeyConfigured(boolean apiKeyConfigured) {
        this.apiKeyConfigured = apiKeyConfigured;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public static DiagnosisLlmSettingsPayload fromEntity(DiagnosisLlmSettingsEntity entity) {
        DiagnosisLlmSettingsPayload payload = new DiagnosisLlmSettingsPayload();
        payload.setEnabled(entity.isEnabled());
        payload.setEndpoint(entity.getEndpoint());
        payload.setApiKey("");
        payload.setApiKeyConfigured(hasText(entity.getApiKey()));
        payload.setModel(entity.getModel());
        payload.setConnectTimeoutMs(entity.getConnectTimeoutMs());
        payload.setReadTimeoutMs(entity.getReadTimeoutMs());
        payload.setTemperature(entity.getTemperature());
        payload.setMaxTokens(entity.getMaxTokens());
        payload.setConfigured(hasText(entity.getEndpoint()) && hasText(entity.getApiKey()) && hasText(entity.getModel()));
        return payload;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
