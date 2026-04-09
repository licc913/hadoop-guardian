$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptRoot "common.ps1")
Import-DeployConfig

$backendPid = Get-ListeningPid $GuardianServerPort
$postgresReady = Test-PostgresReady

Write-Host "Hadoop Guardian package status"
Write-Host "-----------------------------"

if ($postgresReady) {
    Write-Host ("Database: running postgresql://{0}:{1}/{2}" -f $GuardianDbHost, $GuardianDbPort, $GuardianDbName)
} else {
    Write-Host "Database: stopped"
}

if ($backendPid) {
    Write-Host ("Backend: running (PID={0}) http://localhost:{1}" -f $backendPid, $GuardianServerPort)
} else {
    Write-Host "Backend: stopped"
}

$pidFile = Get-BackendPidFile
if (Test-Path $pidFile) {
    $pidLines = @(Get-Content $pidFile)
    if ($pidLines.Count -gt 0) {
        Write-Host ("PID file: {0}" -f $pidLines[0])
    }
}
