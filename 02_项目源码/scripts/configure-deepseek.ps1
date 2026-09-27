param([string]$Model = 'deepseek-flash')
$ErrorActionPreference = 'Stop'
if ($Model -notmatch '^[a-zA-Z0-9._:/-]{1,100}$') { throw 'Invalid model identifier.' }
$runtime = Join-Path (Split-Path -Parent $PSScriptRoot) '.runtime'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$configPath = Join-Path $runtime 'ai.local.json'
$secureKey = Read-Host 'Enter your DeepSeek API key (hidden, local only)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $key = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer).Trim()
    if (-not $key -or $key.Contains("`n") -or $key.Contains("`r")) { throw 'API key must be a non-empty single line.' }
    $config = @{ enabled=$true; apiUrl='https://api.deepseek.com/chat/completions'; apiKey=$key; model=$Model; timeoutSeconds=60 }
    [IO.File]::WriteAllText($configPath, ($config | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    Write-Output 'DeepSeek configuration saved locally. Restart with dev.ps1 Stop then Start.'
    Write-Output 'No external request has been made. Test a short passage in the chapter editor after restart.'
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $key=$null; $config=$null; $secureKey.Dispose()
}
