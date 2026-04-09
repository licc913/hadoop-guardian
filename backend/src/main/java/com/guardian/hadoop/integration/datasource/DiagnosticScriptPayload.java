package com.guardian.hadoop.integration.datasource;

public class DiagnosticScriptPayload {

    private Long id;
    private boolean enabled;
    private String scriptName;
    private String commandPath;
    private String allowedArgs;
    private int timeoutSeconds;
    private boolean requiresApproval;
    private String hostScope;
    private String serviceScope;
    private String description;

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

    public String getScriptName() {
        return scriptName;
    }

    public void setScriptName(String scriptName) {
        this.scriptName = scriptName;
    }

    public String getCommandPath() {
        return commandPath;
    }

    public void setCommandPath(String commandPath) {
        this.commandPath = commandPath;
    }

    public String getAllowedArgs() {
        return allowedArgs;
    }

    public void setAllowedArgs(String allowedArgs) {
        this.allowedArgs = allowedArgs;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public String getHostScope() {
        return hostScope;
    }

    public void setHostScope(String hostScope) {
        this.hostScope = hostScope;
    }

    public String getServiceScope() {
        return serviceScope;
    }

    public void setServiceScope(String serviceScope) {
        this.serviceScope = serviceScope;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public static DiagnosticScriptPayload fromEntity(DiagnosticScriptEntity entity) {
        DiagnosticScriptPayload payload = new DiagnosticScriptPayload();
        payload.setId(entity.getId());
        payload.setEnabled(entity.isEnabled());
        payload.setScriptName(entity.getScriptName());
        payload.setCommandPath(entity.getCommandPath());
        payload.setAllowedArgs(entity.getAllowedArgs());
        payload.setTimeoutSeconds(entity.getTimeoutSeconds());
        payload.setRequiresApproval(entity.isRequiresApproval());
        payload.setHostScope(entity.getHostScope());
        payload.setServiceScope(entity.getServiceScope());
        payload.setDescription(entity.getDescription());
        return payload;
    }
}
