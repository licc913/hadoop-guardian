$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptRoot "common.ps1")
Import-DeployConfig

if (-not $GuardianInitSchema -and -not $GuardianInitDemoData) {
    Write-Host "Database initialization is disabled in config."
    return
}

Ensure-PostgresRunning

$psql = Resolve-PsqlPath
$sqlDir = Join-Path (Get-BundleRoot) "sql"
$env:PGPASSWORD = $GuardianDbPassword
$env:PGCLIENTENCODING = "UTF8"

$adminArgs = @(
    "-h", $GuardianDbHost,
    "-p", [string]$GuardianDbPort,
    "-U", $GuardianDbUsername,
    "-v", "ON_ERROR_STOP=1"
)

$databaseExists = & $psql @adminArgs -d postgres -tAc "select 1 from pg_database where datname = '$GuardianDbName';"
if (-not $databaseExists) {
    & $psql @adminArgs -d postgres -c "create database `"$GuardianDbName`";"
}

$dbArgs = @(
    "-h", $GuardianDbHost,
    "-p", [string]$GuardianDbPort,
    "-U", $GuardianDbUsername,
    "-d", $GuardianDbName,
    "-v", "ON_ERROR_STOP=1"
)

if ($GuardianInitSchema) {
    foreach ($file in @(
        "001_init_schema.sql",
        "003_workflow_schema.sql",
        "005_datasource_schema.sql",
        "007_knowledge_base_schema.sql",
        "012_knowledge_document_chunk_schema.sql",
        "013_cluster_inspection_schema.sql"
    )) {
        & $psql @dbArgs -f (Join-Path $sqlDir $file)
    }
}

if ($GuardianInitDemoData) {
    $incidentCount = & $psql @dbArgs -tAc "select count(*) from incident_event;"
    if ([int]($incidentCount.Trim()) -eq 0) {
        & $psql @dbArgs -f (Join-Path $sqlDir "009_postgres_demo_data_zh.sql")
    }
}

Write-Host "Database initialization completed."
