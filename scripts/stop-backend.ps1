$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path (Join-Path $projectRoot "logs") "backend.pid"

$stopped = $false

if (Test-Path $pidFile) {
    $pidValue = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($pidValue) {
        $process = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $process.Id -Force
            $stopped = $true
        }
    }
    Remove-Item $pidFile -ErrorAction SilentlyContinue
}

$listening = netstat -ano | Select-String ':8080' | ForEach-Object {
    ($_ -split '\s+')[-1]
} | Select-Object -First 1

if ($listening) {
    Stop-Process -Id $listening -Force
    $stopped = $true
}

if ($stopped) {
    Write-Host "Backend stopped."
} else {
    Write-Host "Backend is not running."
}
