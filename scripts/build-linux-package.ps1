$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $projectRoot "frontend"
$backendDir = Join-Path $projectRoot "backend"
$deployDir = Join-Path $projectRoot "deploy"
$templateDir = Join-Path $deployDir "linux-package"
$releaseRoot = Join-Path $deployDir "release"
$releaseName = "hadoop-guardian-linux-" + (Get-Date -Format "yyyyMMdd-HHmmss")
$stagingDir = Join-Path $releaseRoot $releaseName
$tarPath = Join-Path $releaseRoot ($releaseName + ".tar.gz")
$zipPath = Join-Path $releaseRoot ($releaseName + ".zip")
$jarSource = Join-Path $backendDir "target\hadoop-guardian-backend-0.1.0-SNAPSHOT.jar"
$jarTarget = Join-Path $stagingDir "app\hadoop-guardian-backend.jar"
$postgresRuntimeSource = "C:\Users\17393\Downloads\percona-postgresql-14.22-ssl1.1-linux-x86_64.tar.gz"
$postgresRuntimeTarget = Join-Path $stagingDir "runtime\percona-postgresql-14.22-ssl1.1-linux-x86_64.tar.gz"

New-Item -ItemType Directory -Force -Path $releaseRoot | Out-Null
if (Test-Path $stagingDir) {
    Remove-Item -Recurse -Force $stagingDir
}
if (Test-Path $tarPath) {
    Remove-Item -Force $tarPath
}
if (Test-Path $zipPath) {
    Remove-Item -Force $zipPath
}

Push-Location $frontendDir
try {
    & "D:\nodejs\npm.cmd" run build
} finally {
    Pop-Location
}

Push-Location $backendDir
try {
    mvn -DskipTests package
} finally {
    Pop-Location
}

if (-not (Test-Path $jarSource)) {
    throw "Backend jar not found: $jarSource"
}

New-Item -ItemType Directory -Force -Path (Join-Path $stagingDir "app") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stagingDir "frontend") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stagingDir "logs") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stagingDir "data") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stagingDir "runtime") | Out-Null

Copy-Item -Recurse -Force (Join-Path $templateDir "*") $stagingDir
Copy-Item -Force $jarSource $jarTarget
Copy-Item -Recurse -Force (Join-Path $frontendDir "dist\*") (Join-Path $stagingDir "frontend")

if (-not (Test-Path $postgresRuntimeSource)) {
    throw "Embedded PostgreSQL runtime archive not found: $postgresRuntimeSource"
}

Copy-Item -Force $postgresRuntimeSource $postgresRuntimeTarget

$tar = Get-Command tar.exe -ErrorAction SilentlyContinue

New-Item -ItemType Directory -Force -Path (Join-Path $stagingDir "sql") | Out-Null
Get-ChildItem (Join-Path $projectRoot "sql") -Filter *.sql |
    Sort-Object Name |
    ForEach-Object {
        Copy-Item -Force $_.FullName (Join-Path $stagingDir ("sql\" + $_.Name))
    }

if ($tar) {
    & $tar.Source -czf $tarPath -C $releaseRoot $releaseName
    Write-Host ("Linux package created: {0}" -f $tarPath)
} else {
    Compress-Archive -Path (Join-Path $stagingDir "*") -DestinationPath $zipPath -Force
    Write-Host ("Linux package created without tar.exe, zip fallback: {0}" -f $zipPath)
}
