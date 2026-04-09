package com.guardian.hadoop.integration.datasource;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "jmx_endpoint_registry")
public class JmxEndpointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "service_type", nullable = false, length = 64)
    private String serviceType;

    @Column(name = "role_type", nullable = false, length = 64)
    private String roleType;

    @Column(name = "target_host", nullable = false, length = 128)
    private String targetHost;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false, length = 128)
    private String path;

    @Column(nullable = false, length = 16)
    private String protocol;

    @Column(name = "auth_type", length = 32)
    private String authType;

    @Column(length = 128)
    private String username;

    @Column(length = 256)
    private String password;

    @Column(name = "metric_whitelist", columnDefinition = "TEXT")
    private String metricWhitelist;

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

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getRoleType() {
        return roleType;
    }

    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public void setTargetHost(String targetHost) {
        this.targetHost = targetHost;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
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

    public String getMetricWhitelist() {
        return metricWhitelist;
    }

    public void setMetricWhitelist(String metricWhitelist) {
        this.metricWhitelist = metricWhitelist;
    }
}
