package com.guardian.hadoop.knowledge;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticleEntity, Long> {

    Optional<KnowledgeArticleEntity> findByScenarioKey(String scenarioKey);
}
