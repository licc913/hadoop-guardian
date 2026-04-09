package com.guardian.hadoop.workflow;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostmortemRecordRepository extends JpaRepository<PostmortemRecordEntity, Long> {

    Optional<PostmortemRecordEntity> findByIncident_Id(Long incidentId);
}
