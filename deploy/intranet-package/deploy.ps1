$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$scriptsDir = Join-Path $scriptRoot "scripts"

& (Join-Path $scriptsDir "init-db.ps1")
& (Join-Path $scriptsDir "start.ps1")
