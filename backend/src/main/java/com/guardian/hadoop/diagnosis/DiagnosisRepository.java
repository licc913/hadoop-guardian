package com.guardian.hadoop.diagnosis;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisRepository extends JpaRepository<DiagnosisEntity, Long> {

    List<DiagnosisEntity> findByIncident_IdOrderByCreatedAtDesc(Long incidentId);

    DiagnosisEntity findTopByIncident_IdOrderByCreatedAtDesc(Long incidentId);
}
