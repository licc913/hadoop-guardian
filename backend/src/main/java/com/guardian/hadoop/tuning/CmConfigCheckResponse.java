package com.guardian.hadoop.tuning;

import java.time.Instant;
import java.util.List;

public class CmConfigCheckResponse {

    private final boolean configured;
    private final boolean success;
    private final String clusterName;
    private final String serviceType;
    private final String serviceName;
    private final String message;
    private final int serviceConfigCount;
    private final int roleConfigGroupCount;
    private final int roleConfigCount;
    private final List<CmConfigCheckStepRecord> steps;
    private final Instant checkedAt;

    public CmConfigCheckResponse(boolean configured,
                                 boolean success,
                                 String clusterName,
                                 String serviceType,
                                 String serviceName,
                                 String message,
                                 int serviceConfigCount,
                                 int roleConfigGroupCount,
                                 int roleConfigCount,
                                 List<CmConfigCheckStepRecord> steps,
                                 Instant checkedAt) {
        this.configured = configured;
        this.success = success;
        this.clusterName = clusterName;
        this.serviceType = serviceType;
        this.serviceName = serviceName;
        this.message = message;
        this.serviceConfigCount = serviceConfigCount;
        this.roleConfigGroupCount = roleConfigGroupCount;
        this.roleConfigCount = roleConfigCount;
        this.steps = steps;
        this.checkedAt = checkedAt;
    }

    public boolean isConfigured() {
        return configured;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getMessage() {
        return message;
    }

    public int getServiceConfigCount() {
        return serviceConfigCount;
    }

    public int getRoleConfigGroupCount() {
        return roleConfigGroupCount;
    }

    public int getRoleConfigCount() {
        return roleConfigCount;
    }

    public List<CmConfigCheckStepRecord> getSteps() {
        return steps;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }
}
