package com.guardian.hadoop.knowledge;

import java.time.Instant;
import java.util.List;
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
        return repository.findAll().stream()
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
}
