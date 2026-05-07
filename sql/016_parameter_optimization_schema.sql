CREATE TABLE IF NOT EXISTS parameter_optimization_record (
    id BIGSERIAL PRIMARY KEY,
    cluster_name VARCHAR(128) NOT NULL,
    service_name VARCHAR(128) NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    component_version VARCHAR(256),
    current_symptoms TEXT,
    optimization_goal TEXT,
    config_snapshot_text TEXT NOT NULL,
    source_code_hints TEXT,
    problem_summary TEXT NOT NULL,
    recommended_parameters TEXT NOT NULL,
    source_evidence TEXT NOT NULL,
    expected_benefits TEXT NOT NULL,
    risk_notes TEXT NOT NULL,
    validation_steps TEXT NOT NULL,
    rule_findings TEXT NOT NULL,
    llm_model VARCHAR(128),
    analysis_source VARCHAR(32) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_parameter_optimization_record_created_at
    ON parameter_optimization_record (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_parameter_optimization_record_service
    ON parameter_optimization_record (service_type, created_at DESC);
