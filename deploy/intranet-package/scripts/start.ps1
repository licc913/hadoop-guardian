param(
    [switch]$Foreground
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptRoot "common.ps1")
Import-DeployConfig
Ensure-PostgresRunning
Set-GuardianEnvironment

$bundleRoot = Get-BundleRoot
$jarPath = Join-Path $bundleRoot "app\hadoop-guardian-backend.jar"
$frontendDir = Join-Path $bundleRoot "frontend"
$logsDir = Join-Path $bundleRoot "logs"
$pidFile = Get-BackendPidFile

if (-not (Test-Path $jarPath)) {
    throw "Backend jar not found: $jarPath"
}

if (-not (Test-Path (Join-Path $frontendDir "index.html"))) {
    throw "Frontend static files not found: $frontendDir"
}

New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
[void](Stop-BackendProcess)

$javaExe = Resolve-JavaPath
$arguments = @(
    "-jar",
    $jarPath,
    "--server.port=$GuardianServerPort",
    "--spring.profiles.active=postgres",
    "--spring.web.resources.static-locations=file:./frontend/,classpath:/static/"
)

if ($Foreground) {
    Push-Location $bundleRoot
    try {
        & $javaExe @arguments
    } finally {
        Pop-Location
    }
    return
}

$stdout = Join-Path $logsDir "backend-stdout.log"
$stderr = Join-Path $logsDir "backend-stderr.log"
$process = Start-Process -FilePath $javaExe `
    -ArgumentList $arguments `
    -WorkingDirectory $bundleRoot `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr `
    -PassThru

$process.Id | Set-Content -Path $pidFile -Encoding ASCII
Start-Sleep -Seconds 12

$listeningPid = Get-ListeningPid $GuardianServerPort
if (-not $listeningPid) {
    throw "Backend did not bind to port $GuardianServerPort. Check logs."
}

Write-Host ("System started. URL=http://localhost:{0}" -f $GuardianServerPort)
