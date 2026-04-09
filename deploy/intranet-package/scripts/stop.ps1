$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptRoot "common.ps1")

$backendStopped = Stop-BackendProcess
$databaseStopped = Stop-PostgresProcess

if ($backendStopped -or $databaseStopped) {
    Write-Host "System stopped."
} else {
    Write-Host "System is not running."
}
