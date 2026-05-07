package com.guardian.hadoop.shared;

import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PlatformSchemaMigrationService {

    private final JdbcTemplate jdbcTemplate;

    public PlatformSchemaMigrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensurePlatformSchema() {
        ensureIncidentGovernanceColumns();
        ensureOptimizationColumns();
    }

    private void ensureIncidentGovernanceColumns() {
        jdbcTemplate.execute("alter table incident_event alter column title type text");
        jdbcTemplate.execute("alter table incident_event alter column impact_scope type text");
        jdbcTemplate.execute("alter table incident_event alter column owner type varchar(256)");
        jdbcTemplate.execute("alter table incident_event add column if not exists governance_status varchar(32) default 'ACTIVE'");
        jdbcTemplate.execute("alter table incident_event add column if not exists event_fingerprint varchar(256)");
        jdbcTemplate.execute("alter table incident_event alter column event_fingerprint type varchar(1024)");
        jdbcTemplate.execute("alter table incident_event add column if not exists first_seen_at timestamp null");
        jdbcTemplate.execute("alter table incident_event add column if not exists last_seen_at timestamp null");
        jdbcTemplate.execute("alter table incident_event add column if not exists occurrence_count integer default 1");
        jdbcTemplate.execute("alter table incident_event add column if not exists suppressed_until timestamp null");
        jdbcTemplate.execute("alter table incident_event add column if not exists governance_note text null");
        jdbcTemplate.execute("update incident_event set governance_status = 'ACTIVE' where governance_status is null");
        jdbcTemplate.execute("update incident_event set occurrence_count = 1 where occurrence_count is null");
        jdbcTemplate.execute("update incident_event set first_seen_at = occurred_at where first_seen_at is null");
        jdbcTemplate.execute("update incident_event set last_seen_at = occurred_at where last_seen_at is null");
        jdbcTemplate.execute("create index if not exists idx_incident_event_fingerprint on incident_event(event_fingerprint)");
        jdbcTemplate.execute("create index if not exists idx_incident_event_governance on incident_event(governance_status, suppressed_until)");
    }

    private void ensureOptimizationColumns() {
        jdbcTemplate.execute("alter table if exists diagnosis_result alter column root_cause type text");
        jdbcTemplate.execute("alter table if exists parameter_optimization_record alter column optimization_goal type text");
        jdbcTemplate.execute("alter table if exists parameter_optimization_record alter column component_version type text");
        jdbcTemplate.execute("alter table if exists parameter_optimization_record alter column llm_model type text");
        jdbcTemplate.execute("alter table if exists sql_optimization_record alter column optimization_goal type text");
        jdbcTemplate.execute("alter table if exists sql_optimization_record alter column llm_model type text");
    }
}
