param(
    [switch]$Foreground,
    [switch]$Rebuild
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot "backend"
$targetDir = Join-Path $backendDir "target"
$jarPath = Join-Path $targetDir "hadoop-guardian-backend-0.1.0-SNAPSHOT.jar"
$logDir = Join-Path $projectRoot "logs"
$pidFile = Join-Path $logDir "backend.pid"

function Resolve-JavaPath {
    $candidates = @(
        "D:\Java\java1.8\bin\java.exe",
        "C:\Program Files\Common Files\Oracle\Java\javapath\java.exe"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    throw "Java runtime not found."
}

function Stop-ExistingBackend {
    $existing = netstat -ano | Select-String ':8080' | ForEach-Object {
        ($_ -split '\s+')[-1]
    } | Select-Object -First 1
    if ($existing) {
        Stop-Process -Id $existing -Force
        Start-Sleep -Seconds 2
    }
}

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

if ($Rebuild -or -not (Test-Path $jarPath)) {
    Push-Location $backendDir
    try {
        mvn -DskipTests package
    } finally {
        Pop-Location
    }
}

Stop-ExistingBackend

$javaExe = Resolve-JavaPath
$arguments = @(
    "-jar",
    $jarPath,
    "--spring.profiles.active=postgres"
)

if ($Foreground) {
    Push-Location $backendDir
    try {
        & $javaExe @arguments
    } finally {
        Pop-Location
    }
    return
}

$stdout = Join-Path $logDir "backend-stdout.log"
$stderr = Join-Path $logDir "backend-stderr.log"
$process = Start-Process -FilePath $javaExe `
    -ArgumentList $arguments `
    -WorkingDirectory $backendDir `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr `
    -PassThru

$process.Id | Set-Content -Path $pidFile -Encoding ASCII
Start-Sleep -Seconds 12

$listening = netstat -ano | Select-String ':8080' | Select-Object -First 1

if (-not $listening) {
    Write-Error "Backend did not bind to port 8080. Check logs/backend-stdout.log and logs/backend-stderr.log."
}

Write-Host ("Backend started. PID={0} URL=http://localhost:8080" -f $process.Id)
