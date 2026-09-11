param([string]$NodeExe)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$processFile = Join-Path $projectRoot '.runtime/processes.json'
if (Test-Path -LiteralPath $processFile) {
    $processRecords = Get-Content -LiteralPath $processFile -Raw | ConvertFrom-Json
    foreach ($name in @('frontend','backend')) {
        $entry = $processRecords.$name
        if ($entry) {
            $running = Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.id)" -ErrorAction SilentlyContinue
            if ($running -and $running.CommandLine -eq $entry.command) { throw 'Stop the development services before building: ./scripts/dev.ps1 -Action Stop' }
        }
    }
}
$settingsFile = Join-Path $projectRoot '.runtime/settings.local.json'
if (-not $NodeExe -and (Test-Path -LiteralPath $settingsFile)) { $NodeExe = (Get-Content -LiteralPath $settingsFile -Raw | ConvertFrom-Json).nodeExe }
if (-not $NodeExe) { $NodeExe = (Get-Command node.exe).Source }
$nodeVersion = [version]((& $NodeExe --version).TrimStart('v'))
if (-not (($nodeVersion.Major -eq 20 -and $nodeVersion -ge [version]'20.19.0') -or $nodeVersion -ge [version]'22.12.0')) { throw 'Use Node 20.19+ or 22.12+; an alternate executable can be passed with -NodeExe.' }
$npmDirectory = Split-Path -Parent (Get-Command npm.cmd).Source
$npmCli = Join-Path $npmDirectory 'node_modules/npm/bin/npm-cli.js'
if (-not (Test-Path -LiteralPath $npmCli)) { throw 'npm CLI was not found next to npm.cmd. Install Node with npm.' }
$previousPath = $env:PATH
try {
    $env:PATH = (Split-Path -Parent $NodeExe) + ';' + $env:PATH
    Push-Location (Join-Path $projectRoot 'backend')
    try { & mvn.cmd -B test package; if ($LASTEXITCODE -ne 0) { throw 'Backend build or tests failed.' } } finally { Pop-Location }
    Push-Location (Join-Path $projectRoot 'frontend')
    try {
        & $NodeExe $npmCli ci --no-fund
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
        & $NodeExe 'node_modules/vite/bin/vite.js' build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    } finally { Pop-Location }
} finally { $env:PATH = $previousPath }
