package com.guardian.hadoop.diagnosis;

import javax.validation.constraints.NotBlank;

public class DiagnosisTaskRequest {

    @NotBlank
    private String triggerBy;

    @NotBlank
    private String triggerReason;

    private String diagnosisMode;

    public String getTriggerBy() {
        return triggerBy;
    }

    public void setTriggerBy(String triggerBy) {
        this.triggerBy = triggerBy;
    }

    public String getTriggerReason() {
        return triggerReason;
    }

    public void setTriggerReason(String triggerReason) {
        this.triggerReason = triggerReason;
    }

    public String getDiagnosisMode() {
        return diagnosisMode;
    }

    public void setDiagnosisMode(String diagnosisMode) {
        this.diagnosisMode = diagnosisMode;
    }
}
