package com.guardian.hadoop.integration.datasource;

import java.util.ArrayList;
import java.util.List;

public class JmxProbeResult {

    private Long endpointId;
    private String serviceType;
    private String roleType;
    private String targetHost;
    private boolean success;
    private String message;
    private int beanCount;
    private List<String> sampleMetrics = new ArrayList<String>();
    private List<String> observedMetrics = new ArrayList<String>();

    public Long getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(Long endpointId) {
        this.endpointId = endpointId;
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

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getBeanCount() {
        return beanCount;
    }

    public void setBeanCount(int beanCount) {
        this.beanCount = beanCount;
    }

    public List<String> getSampleMetrics() {
        return sampleMetrics;
    }

    public void setSampleMetrics(List<String> sampleMetrics) {
        this.sampleMetrics = sampleMetrics;
    }

    public List<String> getObservedMetrics() {
        return observedMetrics;
    }

    public void setObservedMetrics(List<String> observedMetrics) {
        this.observedMetrics = observedMetrics;
    }
}
