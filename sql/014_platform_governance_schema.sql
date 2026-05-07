ALTER TABLE incident_event
    ADD COLUMN IF NOT EXISTS governance_status VARCHAR(32) DEFAULT 'ACTIVE';

ALTER TABLE incident_event
    ADD COLUMN IF NOT EXISTS event_fingerprint VARCHAR(256);

ALTER TABLE incident_event
    ALTER COLUMN event_fingerprint TYPE VARCHAR(1024);

ALTER TABLE incident_event
    ADD COLUMN IF NOT EXISTS first_seen_at TIMESTAMP NULL;

ALTER TABLE incident_event
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP NULL;

ALTER TABLE incident_event
    ADD COLUMN IF NOT EXISTS occurrence_count INTEGER DEFAULT 1;

ALTER TABLE incident_event
    ADD COLUMN IF NOT EXISTS suppressed_until TIMESTAMP NULL;

ALTER TABLE incident_event
    ADD COLUMN IF NOT EXISTS governance_note TEXT NULL;

CREATE INDEX IF NOT EXISTS idx_incident_event_fingerprint
    ON incident_event(event_fingerprint);

CREATE INDEX IF NOT EXISTS idx_incident_event_governance
    ON incident_event(governance_status, suppressed_until);
