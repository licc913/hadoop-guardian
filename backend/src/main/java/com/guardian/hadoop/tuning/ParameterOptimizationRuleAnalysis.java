package com.guardian.hadoop.tuning;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ParameterOptimizationRuleAnalysis {

    private final String riskLevel;
    private final List<String> findings;
    private final List<String> focusParameters;
    private final Map<String, String> proposedValues;

    public ParameterOptimizationRuleAnalysis(String riskLevel,
                                            List<String> findings,
                                            List<String> focusParameters,
                                            Map<String, String> proposedValues) {
        this.riskLevel = riskLevel;
        this.findings = findings == null ? Collections.<String>emptyList() : findings;
        this.focusParameters = focusParameters == null ? Collections.<String>emptyList() : focusParameters;
        this.proposedValues = proposedValues == null ? Collections.<String, String>emptyMap() : proposedValues;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public List<String> getFindings() {
        return findings;
    }

    public List<String> getFocusParameters() {
        return focusParameters;
    }

    public Map<String, String> getProposedValues() {
        return proposedValues;
    }
}
