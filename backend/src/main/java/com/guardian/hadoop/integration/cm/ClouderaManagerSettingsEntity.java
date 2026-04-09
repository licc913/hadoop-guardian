package com.guardian.hadoop.integration.cm;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "cloudera_manager_settings")
public class ClouderaManagerSettingsEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "base_url", length = 256)
    private String baseUrl;

    @Column(name = "api_version", length = 32)
    private String apiVersion;

    @Column(name = "username", length = 128)
    private String username;

    @Column(name = "password", length = 256)
    private String password;

    @Column(name = "cluster_name", length = 128)
    private String clusterName;

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

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }
}
