package com.guardian.hadoop.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentChunkRepository extends JpaRepository<KnowledgeDocumentChunkEntity, Long> {

    long countByDocumentKey(String documentKey);
}
