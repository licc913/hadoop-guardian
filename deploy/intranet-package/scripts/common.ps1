$ErrorActionPreference = "Stop"

$script:ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$script:BundleRoot = Split-Path -Parent $script:ScriptDir
$script:ConfigPath = Join-Path $script:BundleRoot "config\deploy-env.ps1"
$script:ConfigLoaded = $false

function Import-DeployConfig {
    if ($script:ConfigLoaded) {
        return
    }

    if (-not (Test-Path $script:ConfigPath)) {
        throw "Deploy config not found: $script:ConfigPath"
    }

    . $script:ConfigPath
    foreach ($name in @(
        "GuardianJavaHome",
        "GuardianUseEmbeddedPostgres",
        "GuardianEmbeddedPostgresHome",
        "GuardianEmbeddedPostgresDataDir",
        "GuardianPsqlPath",
        "GuardianDbHost",
        "GuardianDbPort",
        "GuardianDbName",
        "GuardianDbUsername",
        "GuardianDbPassword",
        "GuardianServerPort",
        "GuardianSecurityEnabled",
        "GuardianSecuritySigningSecret",
        "GuardianAdminUsername",
        "GuardianAdminPassword",
        "GuardianAdminDisplayName",
        "GuardianOperatorUsername",
        "GuardianOperatorPassword",
        "GuardianOperatorDisplayName",
        "GuardianCmEnabled",
        "GuardianCmBaseUrl",
        "GuardianCmApiVersion",
        "GuardianCmUsername",
        "GuardianCmPassword",
        "GuardianCmClusterName",
        "GuardianLlmEnabled",
        "GuardianLlmEndpoint",
        "GuardianLlmApiKey",
        "GuardianLlmModel",
        "GuardianLlmConnectTimeoutMs",
        "GuardianLlmReadTimeoutMs",
        "GuardianLlmTemperature",
        "GuardianLlmMaxTokens",
        "GuardianInitSchema",
        "GuardianInitDemoData"
    )) {
        $variable = Get-Variable -Name $name -Scope Local -ErrorAction SilentlyContinue
        if ($null -ne $variable) {
            Set-Variable -Name $name -Scope Script -Value $variable.Value
        }
    }
    $script:ConfigLoaded = $true
}

function Get-BundleRoot {
    return $script:BundleRoot
}

function Resolve-BundlePath([string]$PathValue) {
    if ($null -eq $PathValue) {
        return $null
    }

    $trimmedValue = $PathValue.Trim()
    if ($trimmedValue.Length -eq 0) {
        return $null
    }

    if ([System.IO.Path]::IsPathRooted($trimmedValue)) {
        return $trimmedValue
    }

    return Join-Path $script:BundleRoot $trimmedValue
}

function Get-LogsDir {
    $logsDir = Join-Path $script:BundleRoot "logs"
    New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
    return $logsDir
}

function Resolve-JavaPath {
    Import-DeployConfig

    $candidates = @()
    if ($GuardianJavaHome) {
        $candidates += (Join-Path (Resolve-BundlePath $GuardianJavaHome) "bin\java.exe")
    }
    $candidates += (Join-Path $script:BundleRoot "runtime\java\bin\java.exe")

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return $candidate
        }
    }

    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    throw "Java runtime not found. Set `$GuardianJavaHome in config\deploy-env.ps1."
}

function Resolve-PostgresBinaryPath([string]$BinaryName) {
    Import-DeployConfig

    if ($GuardianUseEmbeddedPostgres) {
        $embeddedHome = Resolve-BundlePath $GuardianEmbeddedPostgresHome
        $embeddedBinary = Join-Path $embeddedHome "bin\$BinaryName"
        if (Test-Path $embeddedBinary) {
            return $embeddedBinary
        }
        throw "Embedded PostgreSQL binary not found: $embeddedBinary"
    }

    if ($GuardianPsqlPath) {
        $resolvedPsqlPath = Resolve-BundlePath $GuardianPsqlPath
        if ($BinaryName -ieq "psql.exe" -and (Test-Path $resolvedPsqlPath)) {
            return $resolvedPsqlPath
        }

        $sibling = Join-Path (Split-Path -Parent $resolvedPsqlPath) $BinaryName
        if (Test-Path $sibling) {
            return $sibling
        }
    }

    $command = Get-Command $BinaryName -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    throw "$BinaryName not found. Check config\deploy-env.ps1."
}

function Resolve-PsqlPath {
    return Resolve-PostgresBinaryPath "psql.exe"
}

function Resolve-PgCtlPath {
    return Resolve-PostgresBinaryPath "pg_ctl.exe"
}

function Resolve-InitDbPath {
    return Resolve-PostgresBinaryPath "initdb.exe"
}

function Resolve-PgIsReadyPath {
    return Resolve-PostgresBinaryPath "pg_isready.exe"
}

function Get-BackendPidFile {
    return Join-Path (Get-LogsDir) "backend.pid"
}

function Get-EmbeddedPostgresDataDir {
    Import-DeployConfig
    return Resolve-BundlePath $GuardianEmbeddedPostgresDataDir
}

function Get-EmbeddedPostgresLogFile {
    return Join-Path (Get-LogsDir) "postgresql.log"
}

function Get-ListeningPid([int]$Port) {
    $matches = @(netstat -ano | Select-String (":{0}" -f $Port))
    if ($matches.Count -eq 0) {
        return $null
    }
    return (($matches[0].ToString()) -split '\s+')[-1]
}

function Test-PostgresReady {
    Import-DeployConfig

    try {
        $pgIsReady = Resolve-PgIsReadyPath
        & $pgIsReady -h $GuardianDbHost -p ([string]$GuardianDbPort) -U $GuardianDbUsername -d postgres | Out-Null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Ensure-EmbeddedPostgresInitialized {
    Import-DeployConfig

    $dataDir = Get-EmbeddedPostgresDataDir
    if (Test-Path (Join-Path $dataDir "PG_VERSION")) {
        return
    }

    $parentDir = Split-Path -Parent $dataDir
    if ($parentDir) {
        New-Item -ItemType Directory -Force -Path $parentDir | Out-Null
    }
    New-Item -ItemType Directory -Force -Path (Get-LogsDir) | Out-Null

    $passwordFile = Join-Path (Get-LogsDir) "postgres-password.txt"
    Set-Content -Path $passwordFile -Value $GuardianDbPassword -Encoding ASCII

    try {
        $initDb = Resolve-InitDbPath
        & $initDb `
            "-D" $dataDir `
            "-U" $GuardianDbUsername `
            "-A" "scram-sha-256" `
            "--pwfile=$passwordFile" `
            "--encoding=UTF8"

        if ($LASTEXITCODE -ne 0) {
            throw "initdb failed with exit code $LASTEXITCODE."
        }
    } finally {
        Remove-Item -Path $passwordFile -ErrorAction SilentlyContinue
    }

    Add-Content -Path (Join-Path $dataDir "postgresql.auto.conf") -Value @(
        "",
        "listen_addresses = '127.0.0.1'",
        "port = $GuardianDbPort",
        "max_connections = 100"
    )

    Add-Content -Path (Join-Path $dataDir "pg_hba.conf") -Value @(
        "",
        "host all all 127.0.0.1/32 scram-sha-256",
        "host all all ::1/128 scram-sha-256"
    )
}

function Ensure-PostgresRunning {
    Import-DeployConfig

    if (-not $GuardianUseEmbeddedPostgres) {
        return
    }

    Ensure-EmbeddedPostgresInitialized

    if (Test-PostgresReady) {
        return
    }

    $pgCtl = Resolve-PgCtlPath
    $dataDir = Get-EmbeddedPostgresDataDir
    $logFile = Get-EmbeddedPostgresLogFile
    $serverOptions = "-p $GuardianDbPort"

    & $pgCtl start -D $dataDir -l $logFile -w -t 60 -o $serverOptions
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL did not start successfully. Check $logFile"
    }
}

function Stop-BackendProcess {
    Import-DeployConfig

    $pidFile = Get-BackendPidFile
    $stopped = $false

    if (Test-Path $pidFile) {
        $pidLines = @(Get-Content $pidFile -ErrorAction SilentlyContinue)
        $pidValue = $null
        if ($pidLines.Count -gt 0) {
            $pidValue = $pidLines[0]
        }
        if ($pidValue) {
            $process = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
            if ($process) {
                Stop-Process -Id $process.Id -Force
                $stopped = $true
            }
        }
        Remove-Item $pidFile -ErrorAction SilentlyContinue
    }

    $listeningPid = Get-ListeningPid $GuardianServerPort
    if ($listeningPid) {
        Stop-Process -Id ([int]$listeningPid) -Force
        $stopped = $true
    }

    return $stopped
}

function Stop-PostgresProcess {
    Import-DeployConfig

    if (-not $GuardianUseEmbeddedPostgres) {
        return $false
    }

    $dataDir = Get-EmbeddedPostgresDataDir
    if (-not (Test-Path (Join-Path $dataDir "postmaster.pid"))) {
        return $false
    }

    $pgCtl = Resolve-PgCtlPath
    & $pgCtl stop -D $dataDir -m fast -w -t 60 | Out-Null
    return $LASTEXITCODE -eq 0
}

function Set-GuardianEnvironment {
    Import-DeployConfig

    $env:SPRING_PROFILES_ACTIVE = "postgres"
    $env:GUARDIAN_DB_URL = "jdbc:postgresql://$GuardianDbHost`:$GuardianDbPort/$GuardianDbName"
    $env:GUARDIAN_DB_USERNAME = $GuardianDbUsername
    $env:GUARDIAN_DB_PASSWORD = $GuardianDbPassword
    $env:GUARDIAN_SECURITY_ENABLED = $GuardianSecurityEnabled.ToString().ToLowerInvariant()
    $env:GUARDIAN_SECURITY_SIGNING_SECRET = $GuardianSecuritySigningSecret
    $env:GUARDIAN_ADMIN_USERNAME = $GuardianAdminUsername
    $env:GUARDIAN_ADMIN_PASSWORD = $GuardianAdminPassword
    $env:GUARDIAN_ADMIN_DISPLAY_NAME = $GuardianAdminDisplayName
    $env:GUARDIAN_OPERATOR_USERNAME = $GuardianOperatorUsername
    $env:GUARDIAN_OPERATOR_PASSWORD = $GuardianOperatorPassword
    $env:GUARDIAN_OPERATOR_DISPLAY_NAME = $GuardianOperatorDisplayName

    $env:GUARDIAN_CM_ENABLED = $GuardianCmEnabled.ToString().ToLowerInvariant()
    $env:GUARDIAN_CM_BASE_URL = $GuardianCmBaseUrl
    $env:GUARDIAN_CM_API_VERSION = $GuardianCmApiVersion
    $env:GUARDIAN_CM_USERNAME = $GuardianCmUsername
    $env:GUARDIAN_CM_PASSWORD = $GuardianCmPassword
    $env:GUARDIAN_CM_CLUSTER_NAME = $GuardianCmClusterName

    $env:GUARDIAN_LLM_ENABLED = $GuardianLlmEnabled.ToString().ToLowerInvariant()
    $env:GUARDIAN_LLM_ENDPOINT = $GuardianLlmEndpoint
    $env:GUARDIAN_LLM_API_KEY = $GuardianLlmApiKey
    $env:GUARDIAN_LLM_MODEL = $GuardianLlmModel
    $env:GUARDIAN_LLM_CONNECT_TIMEOUT_MS = [string]$GuardianLlmConnectTimeoutMs
    $env:GUARDIAN_LLM_READ_TIMEOUT_MS = [string]$GuardianLlmReadTimeoutMs
    $env:GUARDIAN_LLM_TEMPERATURE = [string]$GuardianLlmTemperature
    $env:GUARDIAN_LLM_MAX_TOKENS = [string]$GuardianLlmMaxTokens
}
