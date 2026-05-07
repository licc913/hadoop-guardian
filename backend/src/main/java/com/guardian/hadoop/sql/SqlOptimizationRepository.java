package com.guardian.hadoop.sql;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SqlOptimizationRepository extends JpaRepository<SqlOptimizationEntity, Long> {

    List<SqlOptimizationEntity> findTop50ByOrderByCreatedAtDesc();
}
