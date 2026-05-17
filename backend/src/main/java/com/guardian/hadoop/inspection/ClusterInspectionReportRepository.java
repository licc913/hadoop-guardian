package com.guardian.hadoop.inspection;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClusterInspectionReportRepository extends JpaRepository<ClusterInspectionReportEntity, Long> {

    List<ClusterInspectionReportEntity> findTop50ByOrderByCreatedAtDescIdDesc();

    List<ClusterInspectionReportEntity> findTop10ByOrderByCreatedAtDescIdDesc();

    long countByStatus(String status);
}
