package com.guardian.hadoop.knowledge;

import com.guardian.hadoop.diagnosis.DiagnosisRepository;
import com.guardian.hadoop.incident.IncidentEntity;
import com.guardian.hadoop.incident.IncidentRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeSuggestionService {

    private final KnowledgeArticleRepository knowledgeArticleRepository;
    private final IncidentRepository incidentRepository;
    @SuppressWarnings("unused")
    private final DiagnosisRepository diagnosisRepository;

    public KnowledgeSuggestionService(KnowledgeArticleRepository knowledgeArticleRepository,
                                      IncidentRepository incidentRepository,
                                      DiagnosisRepository diagnosisRepository) {
        this.knowledgeArticleRepository = knowledgeArticleRepository;
        this.incidentRepository = incidentRepository;
        this.diagnosisRepository = diagnosisRepository;
    }

    public List<KnowledgeSuggestionRecord> getSuggestions(long incidentId) {
        IncidentEntity incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) {
            return Collections.emptyList();
        }
        String haystack = buildHaystack(incident).toLowerCase(Locale.ROOT);
        return matchArticles(incident.getServiceType(), incident.getSeverity(), haystack, 5);
    }

    public List<KnowledgeSuggestionRecord> search(String domainHint, String question, int limit) {
        String haystack = question == null ? "" : question.toLowerCase(Locale.ROOT);
        String normalizedDomain = domainHint == null ? "" : domainHint.trim().toUpperCase(Locale.ROOT);
        int safeLimit = limit <= 0 ? 3 : Math.min(limit, 8);
        return matchArticles(normalizedDomain, "MEDIUM", haystack, safeLimit);
    }

    private List<KnowledgeSuggestionRecord> matchArticles(String serviceType,
                                                          String severity,
                                                          String haystack,
                                                          int limit) {
        return knowledgeArticleRepository.findAll().stream()
            .map(article -> score(article, serviceType, severity, haystack))
            .filter(candidate -> candidate.score > 0)
            .sorted(Comparator.comparingInt((MatchCandidate candidate) -> candidate.score).reversed()
                .thenComparing(candidate -> candidate.article.getTitle()))
            .limit(limit)
            .map(MatchCandidate::toRecord)
            .collect(Collectors.toList());
    }

    private MatchCandidate score(KnowledgeArticleEntity article,
                                 String serviceType,
                                 String severity,
                                 String haystack) {
        int score = 0;
        List<String> reasons = new ArrayList<String>();
        Set<String> matchedKeywords = new LinkedHashSet<String>();

        String normalizedServiceType = safe(serviceType).toUpperCase(Locale.ROOT);
        boolean hasDomainHint = !normalizedServiceType.isEmpty();
        int domainScore = domainCompatibilityScore(article.getDomain(), normalizedServiceType);
        if (hasDomainHint && domainScore <= 0) {
            return new MatchCandidate(article, 0, Collections.<String>emptyList(), Collections.<String>emptyList());
        }

        for (String keyword : article.getMatchKeywords()) {
            String normalizedKeyword = safe(keyword).toLowerCase(Locale.ROOT);
            if (normalizedKeyword.length() >= 2 && haystack.contains(normalizedKeyword)) {
                matchedKeywords.add(keyword);
            }
        }

        if (matchedKeywords.isEmpty()) {
            return new MatchCandidate(article, 0, Collections.<String>emptyList(), Collections.<String>emptyList());
        }

        if (domainScore > 0) {
            score += domainScore;
            reasons.add("knowledge domain matches current service family");
        }
        score += matchedKeywords.size() * 6;
        reasons.add("matched log/event keywords: " + String.join(", ", matchedKeywords));

        if ("CRITICAL".equalsIgnoreCase(severity)) {
            score += 1;
            reasons.add("critical incident, prioritize conservative runbook");
        }

        return new MatchCandidate(article, score, new ArrayList<String>(matchedKeywords), reasons);
    }

    private int domainCompatibilityScore(String articleDomain, String serviceType) {
        String domain = safe(articleDomain).toUpperCase(Locale.ROOT);
        String service = safe(serviceType).toUpperCase(Locale.ROOT);
        if (domain.isEmpty() || service.isEmpty()) {
            return 0;
        }
        if (domain.equals(service)) {
            return 12;
        }
        if (containsAny(service, "HIVE", "TEZ") && containsAny(domain, "HIVE", "TEZ")) {
            return 8;
        }
        if (containsAny(service, "IMPALA", "IMPALAD", "CATALOGD", "STATESTORE")
            && containsAny(domain, "IMPALA", "IMPALAD", "CATALOGD", "STATESTORE")) {
            return 8;
        }
        return 0;
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String buildHaystack(IncidentEntity incident) {
        List<String> parts = new ArrayList<String>();
        parts.add(incident.getServiceType());
        parts.add(incident.getTitle());
        parts.add(incident.getSummary());
        parts.add(incident.getImpactScope());
        parts.addAll(incident.getEvidence());
        parts.addAll(incident.getAvoidedActions());
        return parts.stream()
            .filter(value -> value != null && !value.trim().isEmpty())
            .collect(Collectors.joining(" | "));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static class MatchCandidate {
        private final KnowledgeArticleEntity article;
        private final int score;
        private final List<String> matchedKeywords;
        private final List<String> reasons;

        private MatchCandidate(KnowledgeArticleEntity article,
                               int score,
                               List<String> matchedKeywords,
                               List<String> reasons) {
            this.article = article;
            this.score = score;
            this.matchedKeywords = matchedKeywords;
            this.reasons = reasons;
        }

        private KnowledgeSuggestionRecord toRecord() {
            return new KnowledgeSuggestionRecord(
                article.getId(),
                article.getDomain(),
                article.getScenarioKey(),
                article.getTitle(),
                article.getSummary(),
                article.getApplicability(),
                article.getRiskLevel(),
                article.isRequiresApproval(),
                article.getSourceName(),
                article.getSourceUrl(),
                score,
                matchedKeywords,
                reasons,
                article.getSteps(),
                article.getValidationChecks(),
                article.getCautionItems()
            );
        }
    }
}

