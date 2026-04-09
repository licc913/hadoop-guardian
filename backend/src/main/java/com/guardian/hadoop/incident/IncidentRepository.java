package com.guardian.hadoop.incident;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<IncidentEntity, Long> {

    Optional<IncidentEntity> findByIncidentNo(String incidentNo);

    Optional<IncidentEntity> findBySourceId(String sourceId);
}
