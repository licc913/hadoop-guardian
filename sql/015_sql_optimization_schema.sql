CREATE TABLE IF NOT EXISTS sql_optimization_record (
    id BIGSERIAL PRIMARY KEY,
    engine_type VARCHAR(32) NOT NULL,
    original_sql TEXT NOT NULL,
    table_schema_note TEXT NULL,
    partition_info TEXT NULL,
    explain_text TEXT NULL,
    error_text TEXT NULL,
    optimization_goal TEXT NULL,
    problem_summary TEXT NOT NULL,
    optimized_sql TEXT NOT NULL,
    optimization_points TEXT NOT NULL,
    risk_notes TEXT NOT NULL,
    validation_steps TEXT NOT NULL,
    rule_findings TEXT NOT NULL,
    llm_model VARCHAR(128) NULL,
    analysis_source VARCHAR(32) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sql_optimization_record_created_at
    ON sql_optimization_record(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sql_optimization_record_engine
    ON sql_optimization_record(engine_type, created_at DESC);
