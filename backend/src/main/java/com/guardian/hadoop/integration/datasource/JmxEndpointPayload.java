package com.guardian.hadoop.integration.datasource;

public class JmxEndpointPayload {

    private Long id;
    private boolean enabled;
    private String serviceType;
    private String roleType;
    private String targetHost;
    private int port;
    private String path;
    private String protocol;
    private String authType;
    private String username;
    private String password;
    private boolean passwordConfigured;
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

    public boolean isPasswordConfigured() {
        return passwordConfigured;
    }

    public void setPasswordConfigured(boolean passwordConfigured) {
        this.passwordConfigured = passwordConfigured;
    }

    public String getMetricWhitelist() {
        return metricWhitelist;
    }

    public void setMetricWhitelist(String metricWhitelist) {
        this.metricWhitelist = metricWhitelist;
    }

    public static JmxEndpointPayload fromEntity(JmxEndpointEntity entity) {
        JmxEndpointPayload payload = new JmxEndpointPayload();
        payload.setId(entity.getId());
        payload.setEnabled(entity.isEnabled());
        payload.setServiceType(entity.getServiceType());
        payload.setRoleType(entity.getRoleType());
        payload.setTargetHost(entity.getTargetHost());
        payload.setPort(entity.getPort());
        payload.setPath(entity.getPath());
        payload.setProtocol(entity.getProtocol());
        payload.setAuthType(entity.getAuthType());
        payload.setUsername(entity.getUsername());
        payload.setPassword("");
        payload.setPasswordConfigured(entity.getPassword() != null && !entity.getPassword().trim().isEmpty());
        payload.setMetricWhitelist(entity.getMetricWhitelist());
        return payload;
    }
}
