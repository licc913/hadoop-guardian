package com.guardian.hadoop.integration.datasource;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "diagnostic_script_registry")
public class DiagnosticScriptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "script_name", nullable = false, length = 128)
    private String scriptName;

    @Column(name = "command_path", nullable = false, length = 256)
    private String commandPath;

    @Column(name = "allowed_args", columnDefinition = "TEXT")
    private String allowedArgs;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(name = "host_scope", length = 128)
    private String hostScope;

    @Column(name = "service_scope", length = 64)
    private String serviceScope;

    @Column(columnDefinition = "TEXT")
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
}
