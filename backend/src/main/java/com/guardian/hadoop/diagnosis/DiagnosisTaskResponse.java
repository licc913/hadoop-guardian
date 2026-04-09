package com.guardian.hadoop.diagnosis;

import java.util.Collections;
import java.util.List;

public class DiagnosisTaskResponse {

    private final boolean createdNewDiagnosis;
    private final String message;
    private final List<String> reasonCodes;
    private final DiagnosisRecord diagnosis;
    private final String diagnosisSource;
    private final String requestedMode;
    private final boolean usedFallback;
    private final String details;

    public DiagnosisTaskResponse(boolean createdNewDiagnosis,
                                 String message,
                                 List<String> reasonCodes,
                                 DiagnosisRecord diagnosis,
                                 String diagnosisSource,
                                 String requestedMode,
                                 boolean usedFallback,
                                 String details) {
        this.createdNewDiagnosis = createdNewDiagnosis;
        this.message = message;
        this.reasonCodes = reasonCodes == null ? Collections.<String>emptyList() : reasonCodes;
        this.diagnosis = diagnosis;
        this.diagnosisSource = diagnosisSource;
        this.requestedMode = requestedMode;
        this.usedFallback = usedFallback;
        this.details = details;
    }

    public boolean isCreatedNewDiagnosis() {
        return createdNewDiagnosis;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getReasonCodes() {
        return reasonCodes;
    }

    public DiagnosisRecord getDiagnosis() {
        return diagnosis;
    }

    public String getDiagnosisSource() {
        return diagnosisSource;
    }

    public String getRequestedMode() {
        return requestedMode;
    }

    public boolean isUsedFallback() {
        return usedFallback;
    }

    public String getDetails() {
        return details;
    }
}
