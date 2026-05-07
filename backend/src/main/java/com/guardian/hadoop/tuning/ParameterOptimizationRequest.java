package com.guardian.hadoop.tuning;

public class ParameterOptimizationRequest {

    private String serviceType;
    private String currentSymptoms;
    private String optimizationGoal;
    private String sourceCodeHints;
    private String manualConfigNote;
    private String createdBy;
    private boolean useCurrentClusterConfig = true;

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getCurrentSymptoms() {
        return currentSymptoms;
    }

    public void setCurrentSymptoms(String currentSymptoms) {
        this.currentSymptoms = currentSymptoms;
    }

    public String getOptimizationGoal() {
        return optimizationGoal;
    }

    public void setOptimizationGoal(String optimizationGoal) {
        this.optimizationGoal = optimizationGoal;
    }

    public String getSourceCodeHints() {
        return sourceCodeHints;
    }

    public void setSourceCodeHints(String sourceCodeHints) {
        this.sourceCodeHints = sourceCodeHints;
    }

    public String getManualConfigNote() {
        return manualConfigNote;
    }

    public void setManualConfigNote(String manualConfigNote) {
        this.manualConfigNote = manualConfigNote;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public boolean isUseCurrentClusterConfig() {
        return useCurrentClusterConfig;
    }

    public void setUseCurrentClusterConfig(boolean useCurrentClusterConfig) {
        this.useCurrentClusterConfig = useCurrentClusterConfig;
    }
}
