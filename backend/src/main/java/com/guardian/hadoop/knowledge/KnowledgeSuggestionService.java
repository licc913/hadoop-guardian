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
        if (article.getDomain().equalsIgnoreCase(normalizedServiceType)) {
            score += 12;
            reasons.add("知识条目领域与当前服务类型一致。");
        } else if ("HIVE_ON_TEZ".equals(normalizedServiceType) && "YARN".equalsIgnoreCase(article.getDomain())) {
            score += 5;
            reasons.add("Hive on Tez 场景通常需要同时检查 YARN 资源层。");
        } else if ("CROSS_COMPONENT".equals(normalizedServiceType)) {
            score += 3;
            reasons.add("跨组件问题需要联动匹配多个处置方案。");
        }

        for (String keyword : article.getMatchKeywords()) {
            String normalizedKeyword = safe(keyword).toLowerCase(Locale.ROOT);
            if (normalizedKeyword.length() >= 2 && haystack.contains(normalizedKeyword)) {
                matchedKeywords.add(keyword);
            }
        }

        if (!matchedKeywords.isEmpty()) {
            score += matchedKeywords.size() * 4;
            reasons.add("命中关键词：" + String.join("、", matchedKeywords));
        }

        if ("CRITICAL".equalsIgnoreCase(severity)) {
            score += 1;
            reasons.add("当前事件等级较高，优先返回更保守的处置方案。");
        }

        return new MatchCandidate(article, score, new ArrayList<String>(matchedKeywords), reasons);
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
