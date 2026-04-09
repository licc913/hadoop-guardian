package com.guardian.hadoop.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecordEntity, Long> {

    List<ApprovalRecordEntity> findByIncident_IdOrderByRequestedAtAsc(Long incidentId);
}
