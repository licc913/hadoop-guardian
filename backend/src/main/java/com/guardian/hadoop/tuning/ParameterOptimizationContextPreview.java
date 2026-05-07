package com.guardian.hadoop.tuning;

import java.util.List;
import java.util.Map;

public class ParameterOptimizationContextPreview {

    private final boolean configured;
    private final boolean available;
    private final String message;
    private final String clusterName;
    private final String serviceName;
    private final String serviceType;
    private final String componentVersion;
    private final String serviceState;
    private final String healthSummary;
    private final Map<String, String> configEntries;
    private final List<ParameterConfigEntryRecord> scopedConfigEntries;
    private final List<String> recentSignals;

    public ParameterOptimizationContextPreview(boolean configured,
                                               boolean available,
                                               String message,
                                               String clusterName,
                                               String serviceName,
                                               String serviceType,
                                               String componentVersion,
                                               String serviceState,
                                               String healthSummary,
                                               Map<String, String> configEntries,
                                               List<ParameterConfigEntryRecord> scopedConfigEntries,
                                               List<String> recentSignals) {
        this.configured = configured;
        this.available = available;
        this.message = message;
        this.clusterName = clusterName;
        this.serviceName = serviceName;
        this.serviceType = serviceType;
        this.componentVersion = componentVersion;
        this.serviceState = serviceState;
        this.healthSummary = healthSummary;
        this.configEntries = configEntries;
        this.scopedConfigEntries = scopedConfigEntries;
        this.recentSignals = recentSignals;
    }

    public boolean isConfigured() {
        return configured;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getMessage() {
        return message;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getComponentVersion() {
        return componentVersion;
    }

    public String getServiceState() {
        return serviceState;
    }

    public String getHealthSummary() {
        return healthSummary;
    }

    public Map<String, String> getConfigEntries() {
        return configEntries;
    }

    public List<ParameterConfigEntryRecord> getScopedConfigEntries() {
        return scopedConfigEntries;
    }

    public List<String> getRecentSignals() {
        return recentSignals;
    }
}
