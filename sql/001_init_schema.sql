CREATE TABLE IF NOT EXISTS incident_event (
    id BIGSERIAL PRIMARY KEY,
    incident_no VARCHAR(64) NOT NULL UNIQUE,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    cluster_name VARCHAR(128) NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    title VARCHAR(256) NOT NULL,
    summary TEXT NOT NULL,
    impact_scope VARCHAR(512),
    owner VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS diagnosis_task (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incident_event(id),
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    trigger_by VARCHAR(64) NOT NULL,
    trigger_reason VARCHAR(256),
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    error_message TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS diagnosis_result (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incident_event(id),
    task_id BIGINT NULL REFERENCES diagnosis_task(id),
    subsystem VARCHAR(64) NOT NULL,
    root_cause VARCHAR(256) NOT NULL,
    confidence NUMERIC(5, 2) NOT NULL,
    impact_level VARCHAR(32) NOT NULL,
    cross_component_path VARCHAR(128),
    diagnosis_json JSONB NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS action_recommendation (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incident_event(id),
    diagnosis_result_id BIGINT NOT NULL REFERENCES diagnosis_result(id),
    action_name VARCHAR(128) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    requires_approval BOOLEAN NOT NULL,
    recommendation_text TEXT NOT NULL,
    execution_payload JSONB NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS incident_evidence_item (
    incident_id BIGINT NOT NULL REFERENCES incident_event(id),
    order_no INTEGER NOT NULL,
    evidence_text TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS incident_avoided_action (
    incident_id BIGINT NOT NULL REFERENCES incident_event(id),
    order_no INTEGER NOT NULL,
    action_text TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS diagnosis_recommendation_item (
    diagnosis_id BIGINT NOT NULL REFERENCES diagnosis_result(id),
    order_no INTEGER NOT NULL,
    recommendation_text TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS diagnosis_followup_item (
    diagnosis_id BIGINT NOT NULL REFERENCES diagnosis_result(id),
    order_no INTEGER NOT NULL,
    followup_text TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS cloudera_manager_settings (
    id BIGINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL,
    base_url VARCHAR(256),
    api_version VARCHAR(32),
    username VARCHAR(128),
    password VARCHAR(256),
    cluster_name VARCHAR(128)
);
