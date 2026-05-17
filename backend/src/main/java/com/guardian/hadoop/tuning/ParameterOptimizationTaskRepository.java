package com.guardian.hadoop.tuning;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ParameterOptimizationTaskRepository extends JpaRepository<ParameterOptimizationTaskEntity, String> {

    long countByStatus(String status);

    List<ParameterOptimizationTaskEntity> findTop10ByOrderByUpdatedAtDesc();
}
