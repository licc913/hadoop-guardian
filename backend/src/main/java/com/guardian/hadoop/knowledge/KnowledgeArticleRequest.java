package com.guardian.hadoop.knowledge;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class KnowledgeArticleRequest {

    @NotBlank
    @Size(max = 64)
    private String domain;

    @NotBlank
    @Size(max = 96)
    private String scenarioKey;

    @NotBlank
    @Size(max = 256)
    private String title;

    @NotBlank
    private String summary;

    @NotBlank
    private String applicability;

    @NotBlank
    @Size(max = 32)
    private String riskLevel;

    @NotNull
    private Boolean requiresApproval;

    @NotBlank
    @Size(max = 128)
    private String sourceName;

    @NotBlank
    @Size(max = 512)
    private String sourceUrl;

    @NotEmpty
    private List<String> symptoms = new ArrayList<String>();

    @NotEmpty
    private List<String> matchKeywords = new ArrayList<String>();

    @NotEmpty
    private List<String> steps = new ArrayList<String>();

    private List<String> validationChecks = new ArrayList<String>();

    private List<String> cautionItems = new ArrayList<String>();

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getScenarioKey() {
        return scenarioKey;
    }

    public void setScenarioKey(String scenarioKey) {
        this.scenarioKey = scenarioKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getApplicability() {
        return applicability;
    }

    public void setApplicability(String applicability) {
        this.applicability = applicability;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Boolean getRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(Boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public List<String> getSymptoms() {
        return symptoms;
    }

    public void setSymptoms(List<String> symptoms) {
        this.symptoms = symptoms;
    }

    public List<String> getMatchKeywords() {
        return matchKeywords;
    }

    public void setMatchKeywords(List<String> matchKeywords) {
        this.matchKeywords = matchKeywords;
    }

    public List<String> getSteps() {
        return steps;
    }

    public void setSteps(List<String> steps) {
        this.steps = steps;
    }

    public List<String> getValidationChecks() {
        return validationChecks;
    }

    public void setValidationChecks(List<String> validationChecks) {
        this.validationChecks = validationChecks;
    }

    public List<String> getCautionItems() {
        return cautionItems;
    }

    public void setCautionItems(List<String> cautionItems) {
        this.cautionItems = cautionItems;
    }
}
