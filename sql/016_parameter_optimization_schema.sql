CREATE TABLE IF NOT EXISTS parameter_optimization_record (
    id BIGSERIAL PRIMARY KEY,
    cluster_name VARCHAR(128) NOT NULL,
    service_name VARCHAR(128) NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    component_version TEXT,
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
    llm_model TEXT,
    analysis_source VARCHAR(32) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_parameter_optimization_record_created_at
    ON parameter_optimization_record (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_parameter_optimization_record_service
    ON parameter_optimization_record (service_type, created_at DESC);

CREATE TABLE IF NOT EXISTS parameter_optimization_task (
    task_id VARCHAR(64) PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    message TEXT,
    result_id BIGINT,
    error_message TEXT,
    request_summary TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_parameter_optimization_task_updated_at
    ON parameter_optimization_task (updated_at DESC);

CREATE TABLE IF NOT EXISTS llm_call_record (
    id BIGSERIAL PRIMARY KEY,
    feature VARCHAR(64) NOT NULL,
    model VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    prompt_chars INTEGER NOT NULL,
    response_chars INTEGER NOT NULL,
    duration_ms BIGINT,
    error_message TEXT,
    prompt_preview TEXT,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_llm_call_record_created_at
    ON llm_call_record (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_llm_call_record_feature
    ON llm_call_record (feature, created_at DESC);

ALTER TABLE parameter_optimization_record
    ALTER COLUMN component_version TYPE TEXT;

ALTER TABLE parameter_optimization_record
    ALTER COLUMN optimization_goal TYPE TEXT;

ALTER TABLE parameter_optimization_record
    ALTER COLUMN llm_model TYPE TEXT;
