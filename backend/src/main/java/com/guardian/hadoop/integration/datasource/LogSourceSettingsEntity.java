package com.guardian.hadoop.integration.datasource;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "log_source_settings")
public class LogSourceSettingsEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "provider_type", length = 64)
    private String providerType;

    @Column(name = "base_url", length = 256)
    private String baseUrl;

    @Column(name = "auth_type", length = 64)
    private String authType;

    @Column(name = "auth_token", length = 512)
    private String authToken;

    @Column(name = "index_pattern", length = 256)
    private String indexPattern;

    @Column(name = "default_time_window_minutes")
    private Integer defaultTimeWindowMinutes;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
}
