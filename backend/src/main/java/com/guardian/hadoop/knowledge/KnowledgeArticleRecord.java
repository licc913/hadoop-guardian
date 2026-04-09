package com.guardian.hadoop.knowledge;

import java.util.List;

public class KnowledgeArticleRecord {

    private final Long id;
    private final String domain;
    private final String scenarioKey;
    private final String title;
    private final String summary;
    private final String applicability;
    private final String riskLevel;
    private final boolean requiresApproval;
    private final String sourceName;
    private final String sourceUrl;
    private final List<String> symptoms;
    private final List<String> matchKeywords;
    private final List<String> steps;
    private final List<String> validationChecks;
    private final List<String> cautionItems;

    public KnowledgeArticleRecord(Long id, String domain, String scenarioKey, String title, String summary,
                                  String applicability, String riskLevel, boolean requiresApproval,
                                  String sourceName, String sourceUrl, List<String> symptoms,
                                  List<String> matchKeywords, List<String> steps,
                                  List<String> validationChecks, List<String> cautionItems) {
        this.id = id;
        this.domain = domain;
        this.scenarioKey = scenarioKey;
        this.title = title;
        this.summary = summary;
        this.applicability = applicability;
        this.riskLevel = riskLevel;
        this.requiresApproval = requiresApproval;
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
        this.symptoms = symptoms;
        this.matchKeywords = matchKeywords;
        this.steps = steps;
        this.validationChecks = validationChecks;
        this.cautionItems = cautionItems;
    }

    public static KnowledgeArticleRecord fromEntity(KnowledgeArticleEntity entity) {
        return new KnowledgeArticleRecord(
            entity.getId(),
            entity.getDomain(),
            entity.getScenarioKey(),
            entity.getTitle(),
            entity.getSummary(),
            entity.getApplicability(),
            entity.getRiskLevel(),
            entity.isRequiresApproval(),
            entity.getSourceName(),
            entity.getSourceUrl(),
            entity.getSymptoms(),
            entity.getMatchKeywords(),
            entity.getSteps(),
            entity.getValidationChecks(),
            entity.getCautionItems()
        );
    }

    public Long getId() { return id; }
    public String getDomain() { return domain; }
    public String getScenarioKey() { return scenarioKey; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getApplicability() { return applicability; }
    public String getRiskLevel() { return riskLevel; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public String getSourceName() { return sourceName; }
    public String getSourceUrl() { return sourceUrl; }
    public List<String> getSymptoms() { return symptoms; }
    public List<String> getMatchKeywords() { return matchKeywords; }
    public List<String> getSteps() { return steps; }
    public List<String> getValidationChecks() { return validationChecks; }
    public List<String> getCautionItems() { return cautionItems; }
}
