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

        return knowledgeArticleRepository.findAll().stream()
            .map(article -> score(article, incident, haystack))
            .filter(candidate -> candidate.score > 0)
            .sorted(Comparator.comparingInt((MatchCandidate candidate) -> candidate.score).reversed()
                .thenComparing(candidate -> candidate.article.getTitle()))
            .limit(5)
            .map(MatchCandidate::toRecord)
            .collect(Collectors.toList());
    }

    private MatchCandidate score(KnowledgeArticleEntity article, IncidentEntity incident, String haystack) {
        int score = 0;
        List<String> reasons = new ArrayList<String>();
        Set<String> matchedKeywords = new LinkedHashSet<String>();

        if (article.getDomain().equalsIgnoreCase(incident.getServiceType())) {
            score += 12;
            reasons.add("知识条目所属领域与当前事件服务类型一致。");
        } else if ("HIVE_ON_TEZ".equals(incident.getServiceType()) && "YARN".equals(article.getDomain())) {
            score += 5;
            reasons.add("Hive on Tez 事件通常需要联动检查 YARN 资源层。");
        } else if ("CROSS_COMPONENT".equals(incident.getServiceType())) {
            score += 3;
            reasons.add("跨组件事件会联动匹配多个处置方案。");
        }

        for (String keyword : article.getMatchKeywords()) {
            String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
            if (normalizedKeyword.length() >= 2 && haystack.contains(normalizedKeyword)) {
                matchedKeywords.add(keyword);
            }
        }

        if (!matchedKeywords.isEmpty()) {
            score += matchedKeywords.size() * 4;
            reasons.add("命中关键信号：" + String.join("、", matchedKeywords));
        }

        if ("CRITICAL".equals(incident.getSeverity())) {
            score += 1;
            reasons.add("事件等级较高，优先返回更保守的处置方案。");
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

    private static class MatchCandidate {
        private final KnowledgeArticleEntity article;
        private final int score;
        private final List<String> matchedKeywords;
        private final List<String> reasons;

        private MatchCandidate(KnowledgeArticleEntity article, int score, List<String> matchedKeywords, List<String> reasons) {
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
