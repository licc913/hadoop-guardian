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

$listeningPid = $null
for ($attempt = 0; $attempt -lt 24; $attempt++) {
    Start-Sleep -Seconds 5
    $listeningPid = Get-ListeningPid $GuardianServerPort
    if ($listeningPid) {
        break
    }

    $runningProcess = Get-Process -Id $process.Id -ErrorAction SilentlyContinue
    if (-not $runningProcess) {
        break
    }
}

if (-not $listeningPid) {
    $stderrTail = ""
    if (Test-Path $stderr) {
        $stderrTail = (Get-Content -Path $stderr -Tail 20 -ErrorAction SilentlyContinue) -join [Environment]::NewLine
    }
    if ($stderrTail) {
        throw "Backend did not bind to port $GuardianServerPort. Recent stderr:`n$stderrTail"
    }
    throw "Backend did not bind to port $GuardianServerPort. Check logs."
}

Write-Host ("System started. URL=http://localhost:{0}" -f $GuardianServerPort)
