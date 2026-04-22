package com.guardian.hadoop.inspection;

public class ClusterInspectionLlmResult {

    private final String overallRisk;
    private final String summary;
    private final String markdownContent;
    private final String model;

    public ClusterInspectionLlmResult(String overallRisk, String summary, String markdownContent, String model) {
        this.overallRisk = overallRisk;
        this.summary = summary;
        this.markdownContent = markdownContent;
        this.model = model;
    }

    public String getOverallRisk() {
        return overallRisk;
    }

    public String getSummary() {
        return summary;
    }

    public String getMarkdownContent() {
        return markdownContent;
    }

    public String getModel() {
        return model;
    }
}
