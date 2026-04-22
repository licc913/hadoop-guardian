CREATE TABLE IF NOT EXISTS cluster_inspection_report (
    id BIGSERIAL PRIMARY KEY,
    cluster_name VARCHAR(128) NOT NULL,
    report_title VARCHAR(256) NOT NULL,
    overall_risk VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    summary TEXT NOT NULL,
    markdown_content TEXT NOT NULL,
    generated_by VARCHAR(128) NOT NULL,
    llm_model VARCHAR(128),
    source_collected_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    error_message TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE cluster_inspection_report ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE cluster_inspection_report ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP NULL;
ALTER TABLE cluster_inspection_report ADD COLUMN IF NOT EXISTS error_message TEXT NULL;
