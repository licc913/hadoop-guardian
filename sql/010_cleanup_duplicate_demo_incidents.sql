BEGIN;

CREATE TEMP TABLE tmp_duplicate_incident_victims AS
SELECT older.id
FROM incident_event older
WHERE older.source_id LIKE 'manual-%'
  AND EXISTS (
    SELECT 1
    FROM incident_event newer
    WHERE newer.id <> older.id
      AND newer.service_type = older.service_type
      AND newer.title = older.title
      AND newer.occurred_at > older.occurred_at
  );

CREATE TEMP TABLE tmp_duplicate_diagnosis_victims AS
SELECT id
FROM diagnosis_result
WHERE incident_id IN (SELECT id FROM tmp_duplicate_incident_victims);

CREATE TEMP TABLE tmp_duplicate_action_victims AS
SELECT id
FROM action_recommendation
WHERE incident_id IN (SELECT id FROM tmp_duplicate_incident_victims);

CREATE TEMP TABLE tmp_duplicate_postmortem_victims AS
SELECT id
FROM postmortem_record
WHERE incident_id IN (SELECT id FROM tmp_duplicate_incident_victims);

DELETE FROM approval_record
WHERE incident_id IN (SELECT id FROM tmp_duplicate_incident_victims)
   OR action_recommendation_id IN (SELECT id FROM tmp_duplicate_action_victims);

DELETE FROM execution_record
WHERE incident_id IN (SELECT id FROM tmp_duplicate_incident_victims)
   OR action_recommendation_id IN (SELECT id FROM tmp_duplicate_action_victims);

DELETE FROM diagnosis_task
WHERE incident_id IN (SELECT id FROM tmp_duplicate_incident_victims);

DELETE FROM action_recommendation
WHERE id IN (SELECT id FROM tmp_duplicate_action_victims);

DELETE FROM diagnosis_recommendation_item
WHERE diagnosis_id IN (SELECT id FROM tmp_duplicate_diagnosis_victims);

DELETE FROM diagnosis_followup_item
WHERE diagnosis_id IN (SELECT id FROM tmp_duplicate_diagnosis_victims);

DELETE FROM diagnosis_result
WHERE id IN (SELECT id FROM tmp_duplicate_diagnosis_victims);

DELETE FROM postmortem_timeline_item
WHERE postmortem_id IN (SELECT id FROM tmp_duplicate_postmortem_victims);

DELETE FROM postmortem_prevention_item
WHERE postmortem_id IN (SELECT id FROM tmp_duplicate_postmortem_victims);

DELETE FROM postmortem_record
WHERE id IN (SELECT id FROM tmp_duplicate_postmortem_victims);

DELETE FROM incident_evidence_item
WHERE incident_id IN (SELECT id FROM tmp_duplicate_incident_victims);

DELETE FROM incident_avoided_action
WHERE incident_id IN (SELECT id FROM tmp_duplicate_incident_victims);

DELETE FROM incident_event
WHERE id IN (SELECT id FROM tmp_duplicate_incident_victims);

COMMIT;
