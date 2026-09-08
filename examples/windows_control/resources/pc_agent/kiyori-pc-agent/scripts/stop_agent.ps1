$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
. (Join-Path $PSScriptRoot 'agent_process.ps1')
function Write-Log { param([string]$Level,[string]$Message) Write-Host "[$Level] $Message" }
$dataDir = Join-Path $projectRoot 'data'
$pidPath = Join-Path $dataDir 'agent.pid'
$runtimePath = Join-Path $dataDir 'runtime.json'
$pathHash = [System.Security.Cryptography.SHA256]::Create()
try { $id = ([BitConverter]::ToString($pathHash.ComputeHash([Text.Encoding]::UTF8.GetBytes($projectRoot.ToLowerInvariant())))).Replace('-', '') }
finally { $pathHash.Dispose() }
$mutex = New-Object System.Threading.Mutex($false, "Local\KiyoriPcAgentLauncher-$id")
$hasLock = $false
try {
    $hasLock = $mutex.WaitOne(0)
    if (-not $hasLock) { throw 'Launcher is busy; retry after it finishes.' }
    $recordedIds = @()
    if (Test-Path -LiteralPath $pidPath) {
        $value = (Get-Content -LiteralPath $pidPath -Raw).Trim()
        if ($value -notmatch '^\d+$') { throw 'Invalid agent.pid; inspect the existing Agent.' }
        $recordedIds += [int]$value
    }
    if (Test-Path -LiteralPath $runtimePath) {
        $recordedIds += [int](Get-Content -LiteralPath $runtimePath -Raw | ConvertFrom-Json).pid
    }
    foreach ($recordedId in ($recordedIds | Select-Object -Unique)) {
        if ($recordedId -gt 0 -and (Get-Process -Id $recordedId -ErrorAction SilentlyContinue)) {
            if (-not (Stop-OwnedAgentProcess -ProcessId $recordedId -RootPath $projectRoot)) {
                throw 'Unverified process preserved. Close the old Agent manually or use the same Windows permissions.'
            }
            Wait-Process -Id $recordedId -Timeout 5 -ErrorAction SilentlyContinue
        }
    }
    # Remove markers only after all recorded processes are stopped or absent; never mask a stop failure.
    foreach ($record in @($pidPath,$runtimePath)) {
        if (Test-Path -LiteralPath $record) { Remove-Item -LiteralPath $record -Force }
    }
    Write-Host '[OK] Recorded Agent processes stopped; configuration and logs preserved.'
} catch {
    Write-Host "[ERROR] $($_.Exception.Message)"
    exit 1
} finally {
    if ($hasLock) { $mutex.ReleaseMutex() | Out-Null }
    $mutex.Dispose()
}
