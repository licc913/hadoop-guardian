package com.guardian.hadoop.action;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionRecommendationRepository extends JpaRepository<ActionRecommendationEntity, Long> {

    List<ActionRecommendationEntity> findByIncident_IdOrderByCreatedAtDesc(Long incidentId);
}
