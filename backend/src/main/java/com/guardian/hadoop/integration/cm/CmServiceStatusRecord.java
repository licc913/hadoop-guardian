package com.guardian.hadoop.integration.cm;

import java.util.ArrayList;
import java.util.List;

public class CmServiceStatusRecord {

    private final String serviceName;
    private final String serviceType;
    private final String serviceState;
    private final String healthSummary;
    private final String entityStatus;
    private final int roleCount;
    private final int unhealthyRoleCount;
    private final List<String> roleHighlights;

    public CmServiceStatusRecord(String serviceName,
                                 String serviceType,
                                 String serviceState,
                                 String healthSummary,
                                 String entityStatus,
                                 int roleCount,
                                 int unhealthyRoleCount,
                                 List<String> roleHighlights) {
        this.serviceName = serviceName;
        this.serviceType = serviceType;
        this.serviceState = serviceState;
        this.healthSummary = healthSummary;
        this.entityStatus = entityStatus;
        this.roleCount = roleCount;
        this.unhealthyRoleCount = unhealthyRoleCount;
        this.roleHighlights = roleHighlights == null ? new ArrayList<String>() : roleHighlights;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getServiceState() {
        return serviceState;
    }

    public String getHealthSummary() {
        return healthSummary;
    }

    public String getEntityStatus() {
        return entityStatus;
    }

    public int getRoleCount() {
        return roleCount;
    }

    public int getUnhealthyRoleCount() {
        return unhealthyRoleCount;
    }

    public List<String> getRoleHighlights() {
        return roleHighlights;
    }
}
