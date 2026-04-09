package com.guardian.hadoop.integration.datasource;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosticScriptRepository extends JpaRepository<DiagnosticScriptEntity, Long> {

    List<DiagnosticScriptEntity> findAllByOrderByServiceScopeAscScriptNameAsc();
}
