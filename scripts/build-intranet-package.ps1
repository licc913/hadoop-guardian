$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $projectRoot "frontend"
$backendDir = Join-Path $projectRoot "backend"
$deployDir = Join-Path $projectRoot "deploy"
$templateDir = Join-Path $deployDir "intranet-package"
$releaseRoot = Join-Path $deployDir "release"
$releaseName = "hadoop-guardian-intranet-" + (Get-Date -Format "yyyyMMdd-HHmmss")
$stagingDir = Join-Path $releaseRoot $releaseName
$zipPath = Join-Path $releaseRoot ($releaseName + ".zip")
$jarSource = Join-Path $backendDir "target\hadoop-guardian-backend-0.1.0-SNAPSHOT.jar"
$jarTarget = Join-Path $stagingDir "app\hadoop-guardian-backend.jar"
$javaRuntimeSource = "D:\Java\java1.8\jre"
$postgresRoot = "C:\Program Files\PostgreSQL\15"
$postgresDirs = @("bin", "lib", "share")

New-Item -ItemType Directory -Force -Path $releaseRoot | Out-Null
if (Test-Path $stagingDir) {
    Remove-Item -Recurse -Force $stagingDir
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

if (-not (Test-Path (Join-Path $javaRuntimeSource "bin\java.exe"))) {
    throw "Java runtime not found: $javaRuntimeSource"
}

foreach ($dir in $postgresDirs) {
    if (-not (Test-Path (Join-Path $postgresRoot $dir))) {
        throw "PostgreSQL runtime directory not found: $(Join-Path $postgresRoot $dir)"
    }
}

New-Item -ItemType Directory -Force -Path (Join-Path $stagingDir "app") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stagingDir "frontend") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stagingDir "logs") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stagingDir "runtime") | Out-Null

Copy-Item -Recurse -Force (Join-Path $templateDir "*") $stagingDir
Copy-Item -Force $jarSource $jarTarget
Copy-Item -Recurse -Force (Join-Path $frontendDir "dist\*") (Join-Path $stagingDir "frontend")

$javaTarget = Join-Path $stagingDir "runtime\java"
New-Item -ItemType Directory -Force -Path $javaTarget | Out-Null
Copy-Item -Recurse -Force (Join-Path $javaRuntimeSource "*") $javaTarget

$postgresTarget = Join-Path $stagingDir "runtime\postgresql"
New-Item -ItemType Directory -Force -Path $postgresTarget | Out-Null
foreach ($dir in $postgresDirs) {
    New-Item -ItemType Directory -Force -Path (Join-Path $postgresTarget $dir) | Out-Null
    Copy-Item -Recurse -Force (Join-Path $postgresRoot "$dir\*") (Join-Path $postgresTarget $dir)
}
foreach ($file in @("server_license.txt", "pg_env.bat")) {
    $source = Join-Path $postgresRoot $file
    if (Test-Path $source) {
        Copy-Item -Force $source (Join-Path $postgresTarget $file)
    }
}

New-Item -ItemType Directory -Force -Path (Join-Path $stagingDir "sql") | Out-Null
foreach ($file in @(
    "001_init_schema.sql",
    "003_workflow_schema.sql",
    "005_datasource_schema.sql",
    "007_knowledge_base_schema.sql",
    "009_postgres_demo_data_zh.sql",
    "010_cleanup_duplicate_demo_incidents.sql",
    "011_cleanup_demo_seed_data.sql",
    "012_knowledge_document_chunk_schema.sql"
)) {
    Copy-Item -Force (Join-Path $projectRoot "sql\$file") (Join-Path $stagingDir "sql\$file")
}

Compress-Archive -Path (Join-Path $stagingDir "*") -DestinationPath $zipPath -Force
Write-Host ("Intranet package created: {0}" -f $zipPath)
