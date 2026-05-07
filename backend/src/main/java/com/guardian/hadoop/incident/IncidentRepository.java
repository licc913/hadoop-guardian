package com.guardian.hadoop.incident;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<IncidentEntity, Long> {

    Optional<IncidentEntity> findByIncidentNo(String incidentNo);

    Optional<IncidentEntity> findBySourceId(String sourceId);

    Optional<IncidentEntity> findBySourceTypeAndSourceId(String sourceType, String sourceId);

    List<IncidentEntity> findTop200ByClusterNameOrderByOccurredAtDesc(String clusterName);

    @Query("select count(i) from IncidentEntity i where i.governanceStatus = :status")
    long countByGovernanceStatus(@Param("status") String status);
}
