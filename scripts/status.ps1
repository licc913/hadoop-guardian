$projectRoot = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $projectRoot "logs"
$pidFile = Join-Path $logDir "backend.pid"

function Get-ListeningPid([int]$Port) {
    $line = netstat -ano | Select-String (":{0}" -f $Port) | Select-Object -First 1
    if (-not $line) {
        return $null
    }
    return ($line -split '\s+')[-1]
}

$backendPid = Get-ListeningPid 8080
$frontendPid = Get-ListeningPid 5173
$postgresPid = Get-ListeningPid 5432

Write-Host "Hadoop Guardian local status"
Write-Host "---------------------------"

if ($backendPid) {
    Write-Host ("Backend: running (PID={0}) http://localhost:8080" -f $backendPid)
} else {
    Write-Host "Backend: stopped"
}

if ($frontendPid) {
    Write-Host ("Frontend: running (PID={0}) http://127.0.0.1:5173" -f $frontendPid)
} else {
    Write-Host "Frontend: stopped"
}

if ($postgresPid) {
    Write-Host ("PostgreSQL: running (PID={0}) localhost:5432" -f $postgresPid)
} else {
    Write-Host "PostgreSQL: stopped"
}

if (Test-Path $pidFile) {
    Write-Host ("PID file: {0}" -f (Get-Content $pidFile | Select-Object -First 1))
}
