package com.guardian.hadoop.diagnosis;

public class LlmDiagnosisResult {

    private final DiagnosisBlueprint blueprint;
    private final String failureMessage;
    private final String failureDetails;

    private LlmDiagnosisResult(DiagnosisBlueprint blueprint, String failureMessage, String failureDetails) {
        this.blueprint = blueprint;
        this.failureMessage = failureMessage;
        this.failureDetails = failureDetails;
    }

    public static LlmDiagnosisResult success(DiagnosisBlueprint blueprint) {
        return new LlmDiagnosisResult(blueprint, null, null);
    }

    public static LlmDiagnosisResult failure(String failureMessage, String failureDetails) {
        return new LlmDiagnosisResult(null, failureMessage, failureDetails);
    }

    public DiagnosisBlueprint getBlueprint() {
        return blueprint;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public String getFailureDetails() {
        return failureDetails;
    }

    public boolean hasBlueprint() {
        return blueprint != null;
    }
}
