# API Contract

## Summary

- `GET /api/dashboard/summary`
- `GET /api/incidents`
- `GET /api/incidents/{incidentId}`
- `GET /api/incidents/{incidentId}/diagnoses`
- `POST /api/incidents/{incidentId}/diagnosis-tasks`
- `POST /api/integrations/cloudera-manager/sync-alerts`

## POST Payload

```json
{
  "triggerBy": "platform-oncall",
  "triggerReason": "Manual triage from incident detail page"
}
```

## Response Notes

- Current data is stored via Spring Data JPA.
- Default local runtime uses H2 file storage; PostgreSQL is supported through the `postgres` profile.
- The UI should consume only backend data and should not fall back to mock incidents.
- Production implementation should enrich synchronized Cloudera Manager alerts with evidence collection and real diagnosis logic.

## Cloudera Manager Sync

`POST /api/integrations/cloudera-manager/sync-alerts`

Response example:

```json
{
  "enabled": true,
  "importedCount": 3,
  "message": "Cloudera Manager 告警同步完成。"
}
```
