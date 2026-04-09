package com.guardian.hadoop.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRecordRepository extends JpaRepository<ExecutionRecordEntity, Long> {

    List<ExecutionRecordEntity> findByIncident_IdOrderByStartedAtAsc(Long incidentId);
}
