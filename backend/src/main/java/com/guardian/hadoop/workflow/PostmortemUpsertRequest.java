package com.guardian.hadoop.workflow;

import java.util.List;

public class PostmortemUpsertRequest {

    private String summary;
    private String rootCause;
    private String impactStatement;
    private List<String> timeline;
    private List<String> preventionItems;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getImpactStatement() {
        return impactStatement;
    }

    public void setImpactStatement(String impactStatement) {
        this.impactStatement = impactStatement;
    }

    public List<String> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<String> timeline) {
        this.timeline = timeline;
    }

    public List<String> getPreventionItems() {
        return preventionItems;
    }

    public void setPreventionItems(List<String> preventionItems) {
        this.preventionItems = preventionItems;
    }
}
