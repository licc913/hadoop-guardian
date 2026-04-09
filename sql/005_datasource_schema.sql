CREATE TABLE IF NOT EXISTS log_source_settings (
    id BIGINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL,
    provider_type VARCHAR(64),
    base_url VARCHAR(256),
    auth_type VARCHAR(64),
    auth_token VARCHAR(512),
    index_pattern VARCHAR(256),
    default_time_window_minutes INTEGER
);

CREATE TABLE IF NOT EXISTS jmx_endpoint_registry (
    id BIGSERIAL PRIMARY KEY,
    enabled BOOLEAN NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    role_type VARCHAR(64) NOT NULL,
    target_host VARCHAR(128) NOT NULL,
    port INTEGER NOT NULL,
    path VARCHAR(128) NOT NULL,
    protocol VARCHAR(16) NOT NULL,
    auth_type VARCHAR(32),
    username VARCHAR(128),
    password VARCHAR(256),
    metric_whitelist TEXT
);

CREATE TABLE IF NOT EXISTS diagnostic_script_registry (
    id BIGSERIAL PRIMARY KEY,
    enabled BOOLEAN NOT NULL,
    script_name VARCHAR(128) NOT NULL,
    command_path VARCHAR(256) NOT NULL,
    allowed_args TEXT,
    timeout_seconds INTEGER NOT NULL,
    requires_approval BOOLEAN NOT NULL,
    host_scope VARCHAR(128),
    service_scope VARCHAR(64),
    description TEXT
);
