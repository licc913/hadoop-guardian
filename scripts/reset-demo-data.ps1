$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$sqlDir = Join-Path $projectRoot "sql"
$psqlPath = "C:\Program Files\PostgreSQL\15\bin\psql.exe"

if (-not (Test-Path $psqlPath)) {
    throw "psql not found. Install PostgreSQL 15 first."
}

$env:PGPASSWORD = "guardian"
$env:PGCLIENTENCODING = "UTF8"

& $psqlPath -h localhost -p 5432 -U guardian -d guardian -c `
    "TRUNCATE TABLE postmortem_prevention_item, postmortem_timeline_item, postmortem_record, execution_record, approval_record, action_recommendation, diagnosis_followup_item, diagnosis_recommendation_item, diagnosis_result, incident_avoided_action, incident_evidence_item, diagnosis_task, incident_event, jmx_endpoint_registry, diagnostic_script_registry, log_source_settings RESTART IDENTITY CASCADE;"

foreach ($file in @("002_test_data.sql", "004_workflow_test_data.sql", "006_datasource_test_data.sql")) {
    & $psqlPath -h localhost -p 5432 -U guardian -d guardian -f (Join-Path $sqlDir $file)
}

Write-Host "Demo data reset complete."
