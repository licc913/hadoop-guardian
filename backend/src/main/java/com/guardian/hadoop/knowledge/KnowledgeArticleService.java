package com.guardian.hadoop.knowledge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeArticleService {

    private final KnowledgeArticleRepository repository;

    public KnowledgeArticleService(KnowledgeArticleRepository repository) {
        this.repository = repository;
    }

    public List<KnowledgeArticleRecord> listArticles() {
        return listArticles(null);
    }

    public List<KnowledgeArticleRecord> listArticles(List<String> domains) {
        Set<String> domainSet = domains == null
            ? Collections.emptySet()
            : domains.stream()
                .filter(value -> value != null && !value.trim().isEmpty())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return repository.findAll().stream()
            .filter(entity -> domainSet.isEmpty() || domainSet.contains(safe(entity.getDomain()).toUpperCase(Locale.ROOT)))
            .sorted((left, right) -> {
                Instant rightInstant = right.getUpdatedAt() != null ? right.getUpdatedAt() : right.getCreatedAt();
                Instant leftInstant = left.getUpdatedAt() != null ? left.getUpdatedAt() : left.getCreatedAt();
                if (rightInstant == null && leftInstant == null) {
                    return 0;
                }
                if (rightInstant == null) {
                    return -1;
                }
                if (leftInstant == null) {
                    return 1;
                }
                return rightInstant.compareTo(leftInstant);
            })
            .map(KnowledgeArticleRecord::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional
    public KnowledgeArticleRecord upsert(KnowledgeArticleRequest request) {
        KnowledgeArticleEntity entity = repository.findByScenarioKey(request.getScenarioKey()).orElseGet(KnowledgeArticleEntity::new);
        entity.setDomain(request.getDomain());
        entity.setScenarioKey(request.getScenarioKey());
        entity.setTitle(request.getTitle());
        entity.setSummary(request.getSummary());
        entity.setApplicability(request.getApplicability());
        entity.setRiskLevel(request.getRiskLevel());
        entity.setRequiresApproval(Boolean.TRUE.equals(request.getRequiresApproval()));
        entity.setSourceName(request.getSourceName());
        entity.setSourceUrl(request.getSourceUrl());
        entity.setSymptoms(request.getSymptoms());
        entity.setMatchKeywords(request.getMatchKeywords());
        entity.setSteps(request.getSteps());
        entity.setValidationChecks(request.getValidationChecks());
        entity.setCautionItems(request.getCautionItems());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }
        entity.setUpdatedAt(Instant.now());
        return KnowledgeArticleRecord.fromEntity(repository.save(entity));
    }

    @Transactional
    public KnowledgeArticleRecord createQuickEntry(KnowledgeQuickEntryRequest request) {
        String domain = normalizeDomain(request.getDomain());
        String content = normalizeContent(request.getContent());
        String title = buildTitle(domain, content);

        KnowledgeArticleRequest fullRequest = new KnowledgeArticleRequest();
        fullRequest.setDomain(domain);
        fullRequest.setScenarioKey(domain.toLowerCase(Locale.ROOT) + "-note-" + Instant.now().toEpochMilli());
        fullRequest.setTitle(title);
        fullRequest.setSummary(buildSummary(content));
        fullRequest.setApplicability(content);
        fullRequest.setRiskLevel("MEDIUM");
        fullRequest.setRequiresApproval(Boolean.FALSE);
        fullRequest.setSourceName("自建知识库");
        fullRequest.setSourceUrl("internal://knowledge");
        fullRequest.setSymptoms(extractParagraphs(content, 4));
        fullRequest.setMatchKeywords(extractKeywords(domain, title, content));
        fullRequest.setSteps(extractSteps(content));
        fullRequest.setValidationChecks(Collections.<String>emptyList());
        fullRequest.setCautionItems(Collections.<String>emptyList());
        return upsert(fullRequest);
    }

    private String normalizeDomain(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeContent(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private String buildTitle(String domain, String content) {
        List<String> segments = splitContent(content);
        if (!segments.isEmpty()) {
            String first = segments.get(0);
            return first.length() <= 48 ? first : first.substring(0, 48);
        }
        return domain + " 自建知识条目";
    }

    private String buildSummary(String content) {
        if (content.length() <= 180) {
            return content;
        }
        return content.substring(0, 180) + "...";
    }

    private List<String> extractParagraphs(String content, int limit) {
        List<String> segments = splitContent(content);
        if (segments.isEmpty()) {
            return Collections.singletonList(content);
        }
        return segments.stream().limit(limit).collect(Collectors.toList());
    }

    private List<String> extractSteps(String content) {
        List<String> lines = splitContent(content);
        if (lines.isEmpty()) {
            return Collections.singletonList("先核对当前服务状态与关键异常现象。");
        }
        return lines.stream().limit(6).collect(Collectors.toList());
    }

    private List<String> extractKeywords(String domain, String title, String content) {
        Set<String> keywords = new LinkedHashSet<String>();
        keywords.add(domain);
        for (String candidate : splitContent(title + "\n" + content)) {
            String normalized = candidate.trim();
            if (normalized.length() < 2) {
                continue;
            }
            if (normalized.length() > 32) {
                normalized = normalized.substring(0, 32);
            }
            keywords.add(normalized);
            if (keywords.size() >= 8) {
                break;
            }
        }
        return new ArrayList<String>(keywords);
    }

    private List<String> splitContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = content.split("[\\n。；;!！?？]+");
        List<String> lines = new ArrayList<String>();
        for (String part : parts) {
            String normalized = part.trim();
            if (!normalized.isEmpty()) {
                lines.add(normalized);
            }
        }
        return lines;
    }
}
