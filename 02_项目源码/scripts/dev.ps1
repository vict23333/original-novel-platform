param(
    [ValidateSet('Start','Stop','Status')][string]$Action = 'Status',
    [string]$NodeExe,
    [string]$MySqlBin
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $projectRoot '.runtime'
$settingsPath = Join-Path $runtime 'settings.local.json'
$recordPath = Join-Path $runtime 'processes.json'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$records = @{}
if (Test-Path -LiteralPath $recordPath) {
    (Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $records[$_.Name] = $_.Value }
}
function Get-OwnedProcess([string]$Name) {
    $entry = $records[$Name]
    if (-not $entry) { return $null }
    $current = Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.id)" -ErrorAction SilentlyContinue
    if ($current -and $current.CreationDate.ToUniversalTime().Ticks -eq ([datetime]$entry.created).ToUniversalTime().Ticks -and $current.CommandLine -eq $entry.command) { return $current }
    return $null
}
function Start-OwnedProcess([string]$Name, [int]$Port, [string]$Exe, [string[]]$Arguments, [string]$Directory) {
    if (Get-OwnedProcess $Name) { Write-Output "$Name already running."; return }
    if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { throw "Port $Port is occupied. No existing process was stopped." }
    $process = Start-Process -FilePath $Exe -ArgumentList $Arguments -WorkingDirectory $Directory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime "$Name-out.log") -RedirectStandardError (Join-Path $runtime "$Name-err.log")
    $current = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)"
    if (-not $current) { throw "$Name exited; inspect .runtime logs." }
    $records[$Name] = @{ id=$current.ProcessId; created=$current.CreationDate.ToUniversalTime().ToString('o'); command=$current.CommandLine }
    $records | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $recordPath -Encoding UTF8
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        if (-not (Get-OwnedProcess $Name)) { throw "$Name exited; inspect .runtime logs." }
        if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { Write-Output "$Name ready on 127.0.0.1:$Port"; return }
        Start-Sleep -Seconds 1
    }
    throw "$Name startup timed out; inspect logs and run Status before retrying."
}
function New-Secret {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes); return [Convert]::ToBase64String($bytes) } finally { $rng.Dispose() }
}
if ($Action -eq 'Status') {
    foreach ($name in @('mysql','backend','frontend')) { $process=Get-OwnedProcess $name; if ($process) { "$name RUNNING (PID $($process.ProcessId))" } else { "$name STOPPED / NOT TRACKED" } }
    return
}
if ($Action -eq 'Stop') {
    if (-not (Test-Path -LiteralPath $settingsPath)) { Write-Output 'Nothing has been initialized.'; return }
    $settings = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
    foreach ($name in @('frontend','backend','mysql')) {
        $process=Get-OwnedProcess $name
        if (-not $process) { Write-Output "$name not running as an owned process."; continue }
        $stoppingProcess = Get-Process -Id $process.ProcessId
        if ($name -eq 'mysql') {
            Push-Location $projectRoot
            try {
                & (Join-Path $settings.mysqlBin 'mysqladmin.exe') '--defaults-file=.runtime/mysql-admin.cnf' shutdown
                if ($LASTEXITCODE -ne 0) { throw 'Graceful database shutdown failed; process was not killed.' }
            } finally { Pop-Location }
        } else { Stop-Process -Id $process.ProcessId }
        # mysqladmin 接受关闭请求不代表 mysqld 已退出，等待完成后才能立即再次 Start。
        if (-not $stoppingProcess.WaitForExit(30000)) { throw "$name is still shutting down; wait before starting again." }
        Write-Output "$name stopped."
    }
    return
}
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'backend/target/novel-platform-0.9.0.jar'))) { throw 'Build the backend first: cd backend; mvn test package' }
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'frontend/node_modules/vite/bin/vite.js'))) { throw 'Install frontend dependencies first: cd frontend; npm ci' }
if (Test-Path -LiteralPath $settingsPath) {
    $settings = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
} else {
    if (-not $NodeExe) { $NodeExe = (Get-Command node.exe).Source }
    if (-not $MySqlBin) { $MySqlBin = Join-Path $env:ProgramFiles 'MySQL/MySQL Server 8.0/bin' }
    $nodeVersion = (& $NodeExe --version).TrimStart('v')
    $parsedNodeVersion = [version]$nodeVersion
    if (-not (($parsedNodeVersion.Major -eq 20 -and $parsedNodeVersion -ge [version]'20.19.0') -or $parsedNodeVersion -ge [version]'22.12.0')) { throw 'Node 20.19+ or 22.12+ is required; pass -NodeExe with a supported executable.' }
    if (-not (Test-Path -LiteralPath (Join-Path $MySqlBin 'mysqld.exe'))) { throw 'MySQL 8 is required; pass -MySqlBin with its bin directory.' }
    $settings = [pscustomobject]@{ nodeExe=$NodeExe; mysqlBin=$MySqlBin; dbPassword=(New-Secret); rootPassword=(New-Secret); jwtSecret=(New-Secret); adminUsername='platform_admin'; adminPassword=(New-Secret) }
    $settings | ConvertTo-Json | Set-Content -LiteralPath $settingsPath -Encoding UTF8
}
Push-Location $projectRoot
$priorEnvironment = @{}
try {
    if (-not (Test-Path -LiteralPath '.runtime/mysql-data')) {
        & (Join-Path $settings.mysqlBin 'mysqld.exe') --no-defaults --initialize-insecure '--datadir=./.runtime/mysql-data' '--log-error=./initialize.log'
        if ($LASTEXITCODE -ne 0) { throw 'Database initialization failed. Existing data will not be deleted automatically.' }
    }
    $initializing = -not (Test-Path -LiteralPath '.runtime/database.ready')
    $mysqlArgs = @('--no-defaults', '--datadir=./.runtime/mysql-data', '--port=13406', '--bind-address=127.0.0.1', '--mysqlx=OFF', '--skip-name-resolve', '--console')
    if ($initializing) {
        $sql = @"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$($settings.rootPassword)';
CREATE DATABASE IF NOT EXISTS novel_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'novel_app'@'127.0.0.1' IDENTIFIED BY '$($settings.dbPassword)';
GRANT ALL PRIVILEGES ON novel_platform.* TO 'novel_app'@'127.0.0.1';
CREATE USER IF NOT EXISTS 'dev_admin'@'127.0.0.1' IDENTIFIED BY '$($settings.rootPassword)';
GRANT SHUTDOWN ON *.* TO 'dev_admin'@'127.0.0.1';
"@
        [IO.File]::WriteAllText((Join-Path $runtime 'mysql-init.sql'), $sql, [Text.UTF8Encoding]::new($false))
        # mysqld 在数据目录解析 init-file 的相对路径。
        $mysqlArgs += '--init-file=../mysql-init.sql'
    }
    Start-OwnedProcess 'mysql' 13406 (Join-Path $settings.mysqlBin 'mysqld.exe') $mysqlArgs $projectRoot
    $adminCnf = "[client]`nhost=127.0.0.1`nport=13406`nprotocol=TCP`nuser=dev_admin`npassword=$($settings.rootPassword)`n"
    [IO.File]::WriteAllText((Join-Path $runtime 'mysql-admin.cnf'), $adminCnf, [Text.UTF8Encoding]::new($false))
    $databaseReady = $false
    for ($attempt = 0; $attempt -lt 15; $attempt++) {
        & (Join-Path $settings.mysqlBin 'mysqladmin.exe') '--defaults-file=.runtime/mysql-admin.cnf' ping 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $databaseReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $databaseReady) { throw 'Database readiness check failed.' }
    if ($initializing) {
        'Initialized by local development script.' | Set-Content -LiteralPath '.runtime/database.ready'
        Remove-Item -LiteralPath (Join-Path $runtime 'mysql-init.sql')
    }
    $variables = @{ DB_PASSWORD=$settings.dbPassword; JWT_SECRET=$settings.jwtSecret; ADMIN_USERNAME=$settings.adminUsername; ADMIN_PASSWORD=$settings.adminPassword }
    # AI 配置仅留本机；环境变量优先于本地配置。只传给后端，不传给 Vite。
    $aiNames = @{ AI_ENABLED='enabled'; AI_API_URL='apiUrl'; AI_API_KEY='apiKey'; AI_MODEL='model'; AI_TIMEOUT_SECONDS='timeoutSeconds' }
    $aiPath = Join-Path $runtime 'ai.local.json'
    if (Test-Path -LiteralPath $aiPath) {
        $ai = Get-Content -LiteralPath $aiPath -Raw | ConvertFrom-Json
        foreach ($name in $aiNames.Keys) {
            if (-not [Environment]::GetEnvironmentVariable($name,'Process') -and $null -ne $ai.($aiNames[$name])) {
                $variables[$name] = [string]$ai.($aiNames[$name])
            }
        }
    }
    foreach ($name in $aiNames.Keys) { $priorEnvironment[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
    foreach ($name in $variables.Keys) { $priorEnvironment[$name] = [Environment]::GetEnvironmentVariable($name,'Process'); [Environment]::SetEnvironmentVariable($name,$variables[$name],'Process') }
    Start-OwnedProcess 'backend' 8081 (Get-Command java.exe).Source @('-Djdk.net.unixdomain.tmpdir=./.runtime/nonexistent-socket-dir', '-jar', 'backend/target/novel-platform-0.9.0.jar') $projectRoot
    foreach ($name in $aiNames.Keys) { [Environment]::SetEnvironmentVariable($name,$null,'Process') }
    Start-OwnedProcess 'frontend' 5173 $settings.nodeExe @('node_modules/vite/bin/vite.js','--host','127.0.0.1') (Join-Path $projectRoot 'frontend')
    Write-Output 'Open http://127.0.0.1:5173 ; register a reader or author account in the browser.'
    Write-Output 'Administrator credentials are stored only in .runtime/settings.local.json. Do not publish this file.'
} finally {
    foreach ($name in $priorEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name,$priorEnvironment[$name],'Process') }
    Pop-Location
}
