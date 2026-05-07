package com.guardian.hadoop.tuning;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParameterOptimizationRepository extends JpaRepository<ParameterOptimizationEntity, Long> {

    List<ParameterOptimizationEntity> findTop50ByOrderByCreatedAtDesc();
}
