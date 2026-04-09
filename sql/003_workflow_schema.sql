CREATE TABLE IF NOT EXISTS approval_record (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incident_event(id),
    action_recommendation_id BIGINT NOT NULL REFERENCES action_recommendation(id),
    approval_status VARCHAR(32) NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    approver VARCHAR(128),
    comment TEXT,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS execution_record (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incident_event(id),
    action_recommendation_id BIGINT NOT NULL REFERENCES action_recommendation(id),
    execution_status VARCHAR(32) NOT NULL,
    executor VARCHAR(128) NOT NULL,
    execution_summary TEXT NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS postmortem_record (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL UNIQUE REFERENCES incident_event(id),
    summary TEXT NOT NULL,
    root_cause TEXT NOT NULL,
    impact_statement TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS postmortem_timeline_item (
    postmortem_id BIGINT NOT NULL REFERENCES postmortem_record(id),
    order_no INTEGER NOT NULL,
    timeline_text TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS postmortem_prevention_item (
    postmortem_id BIGINT NOT NULL REFERENCES postmortem_record(id),
    order_no INTEGER NOT NULL,
    prevention_text TEXT NOT NULL
);
