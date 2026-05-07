package com.guardian.hadoop.inspection;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClusterInspectionReportRepository extends JpaRepository<ClusterInspectionReportEntity, Long> {

    List<ClusterInspectionReportEntity> findTop50ByOrderByCreatedAtDescIdDesc();

    long countByStatus(String status);
}
