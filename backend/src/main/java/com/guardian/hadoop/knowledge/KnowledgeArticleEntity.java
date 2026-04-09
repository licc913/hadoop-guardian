package com.guardian.hadoop.knowledge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OrderColumn;
import javax.persistence.Table;

@Entity
@Table(name = "knowledge_article")
public class KnowledgeArticleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String domain;

    @Column(name = "scenario_key", nullable = false, unique = true, length = 96)
    private String scenarioKey;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String applicability;

    @Column(name = "risk_level", nullable = false, length = 32)
    private String riskLevel;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(name = "source_name", nullable = false, length = 128)
    private String sourceName;

    @Column(name = "source_url", nullable = false, length = 512)
    private String sourceUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "knowledge_symptom_item", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "symptom_text", nullable = false, columnDefinition = "TEXT")
    @OrderColumn(name = "order_no")
    private List<String> symptoms = new ArrayList<String>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "knowledge_keyword_item", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "keyword_text", nullable = false, length = 128)
    @OrderColumn(name = "order_no")
    private List<String> matchKeywords = new ArrayList<String>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "knowledge_step_item", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "step_text", nullable = false, columnDefinition = "TEXT")
    @OrderColumn(name = "order_no")
    private List<String> steps = new ArrayList<String>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "knowledge_validation_item", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "validation_text", nullable = false, columnDefinition = "TEXT")
    @OrderColumn(name = "order_no")
    private List<String> validationChecks = new ArrayList<String>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "knowledge_caution_item", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "caution_text", nullable = false, columnDefinition = "TEXT")
    @OrderColumn(name = "order_no")
    private List<String> cautionItems = new ArrayList<String>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

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

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(boolean requiresApproval) {
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
