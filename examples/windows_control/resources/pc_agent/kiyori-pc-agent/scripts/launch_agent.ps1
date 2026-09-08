param([switch]$NoBrowser)
$ErrorActionPreference = "Stop"
$projectRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
Set-Location $projectRoot
. (Join-Path $PSScriptRoot 'agent_process.ps1')
$dataDir = Join-Path $projectRoot "data"
$logsDir = Join-Path $projectRoot "logs"
$runtimePath = Join-Path $dataDir "runtime.json"
$pidPath = Join-Path $dataDir "agent.pid"
$launcherLog = Join-Path $logsDir "launcher.log"
$outLog = Join-Path $logsDir "agent.out.log"
$errLog = Join-Path $logsDir "agent.err.log"

function Write-Log {
    param([string]$Level, [string]$Message)
    $line = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff') [$Level] $Message"
    Write-Host $line
    Add-Content -LiteralPath $launcherLog -Value $line -Encoding UTF8
}

function Test-ManagementReady {
    param([string]$Url, [int]$ExpectedPid)
    if ($Url -notmatch '^http://127\.0\.0\.1:\d+$') { return $false }
    # PowerShell 5.1 inherits the system proxy. Probe loopback directly and match this launch's PID.
    $response = $null
    try {
        $request = [System.Net.HttpWebRequest]::Create("$Url/api/health")
        $request.Proxy = $null
        $request.AllowAutoRedirect = $false
        $request.Timeout = 1500
        $request.ReadWriteTimeout = 1500
        $response = $request.GetResponse()
        $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
        try { $health = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        return ($health.ok -and [int]$health.pid -eq $ExpectedPid -and $health.mode -eq 'http-agent')
    } catch { return $false }
    finally { if ($response) { $response.Dispose() } }
}

function Ensure-Dependencies {
    param([string]$RootPath)

    $requiredPackages = @(
        @{ Name = "node-pty"; Path = (Join-Path $RootPath "node_modules\node-pty\package.json") },
        @{ Name = "@xterm/headless"; Path = (Join-Path $RootPath "node_modules\@xterm\headless\package.json") }
    )

    $missingBefore = @($requiredPackages | Where-Object { -not (Test-Path $_.Path) })
    if ($missingBefore.Count -eq 0) {
        Write-Log "INFO" "Dependency check passed: node-pty, @xterm/headless are present."
        return
    }

    Write-Log "INFO" "Missing dependencies: $((@($missingBefore | ForEach-Object { $_.Name }) -join ', '))"

    $npmCmd = Get-Command npm -ErrorAction SilentlyContinue
    $pnpmCmd = Get-Command pnpm -ErrorAction SilentlyContinue
    $npmLockFilePath = Join-Path $RootPath "package-lock.json"
    $pnpmLockFilePath = Join-Path $RootPath "pnpm-lock.yaml"

    $installerName = $null
    $primaryArgs = @()
    $fallbackArgs = $null

    if ((Test-Path $pnpmLockFilePath) -and $pnpmCmd) {
        $installerName = "pnpm"
        $primaryArgs = @("install", "--frozen-lockfile", "--prefer-offline")
        $fallbackArgs = @("install", "--prefer-offline")
    }
    elseif ((Test-Path $npmLockFilePath) -and $npmCmd) {
        $installerName = "npm"
        $primaryArgs = @("ci", "--no-audit", "--no-fund")
        $fallbackArgs = $null
    }
    elseif ($pnpmCmd) {
        $installerName = "pnpm"
        $primaryArgs = @("install", "--prefer-offline")
    }
    elseif ($npmCmd) {
        $installerName = "npm"
        $primaryArgs = @("install", "--no-audit", "--no-fund")
    }
    else {
        throw "Neither npm nor pnpm was found. Please install Node.js with npm or pnpm."
    }

    $commandName = if ($installerName -eq "pnpm") { "pnpm" } else { "npm" }

    Write-Log "INFO" "Installing dependencies: $installerName $($primaryArgs -join ' ')"
    & $commandName @primaryArgs
    $installExitCode = $LASTEXITCODE

    if ($installExitCode -ne 0 -and $fallbackArgs) {
        Write-Log "WARN" "Primary dependency install failed (exit $installExitCode). Retrying: $installerName $($fallbackArgs -join ' ')"
        & $commandName @fallbackArgs
        $installExitCode = $LASTEXITCODE
    }

    if ($installExitCode -ne 0) {
        throw "Dependency installation failed with exit code $installExitCode"
    }

    $missingAfter = @($requiredPackages | Where-Object { -not (Test-Path $_.Path) })
    if ($missingAfter.Count -gt 0) {
        throw "Dependencies are still missing after installation: $((@($missingAfter | ForEach-Object { $_.Name }) -join ', '))"
    }

    Write-Log "INFO" "Dependencies installed successfully via $installerName."
}

function Resolve-NodeRuntime {
    param([string]$RootPath)

    $systemNodeCmd = Get-Command node -ErrorAction SilentlyContinue
    if ($systemNodeCmd -and $systemNodeCmd.Source) {
        $systemNodePath = $systemNodeCmd.Source
        return [pscustomobject]@{
            NodePath = $systemNodePath
            NodeDir = Split-Path -Parent $systemNodePath
            Source = "system"
        }
    }

    $localCandidates = @(
        (Join-Path $RootPath "node\node.exe"),
        (Join-Path $RootPath "runtime\node\node.exe"),
        (Join-Path $RootPath "local\node\node.exe")
    )

    foreach ($candidate in $localCandidates) {
        if (Test-Path $candidate) {
            $resolved = (Resolve-Path $candidate).Path
            return [pscustomobject]@{
                NodePath = $resolved
                NodeDir = Split-Path -Parent $resolved
                Source = "local"
            }
        }
    }

    return $null
}

function Get-NodeArchToken {
    $rawArch = $env:PROCESSOR_ARCHITEW6432
    if ([string]::IsNullOrWhiteSpace($rawArch)) {
        $rawArch = $env:PROCESSOR_ARCHITECTURE
    }

    if ([string]::IsNullOrWhiteSpace($rawArch)) {
        return "x64"
    }

    switch ($rawArch.Trim().ToUpperInvariant()) {
        "ARM64" { return "arm64" }
        "X86" { return "x86" }
        "AMD64" { return "x64" }
        default { return "x64" }
    }
}

function Ensure-LocalNodeRuntime {
    param([string]$RootPath)

    $localNodeDir = Join-Path $RootPath "node"
    $localNodeExe = Join-Path $localNodeDir "node.exe"

    if (Test-Path $localNodeExe) {
        return $localNodeExe
    }

    $arch = Get-NodeArchToken
    $zipTag = "win-$arch-zip"
    $indexUrl = "https://nodejs.org/dist/index.json"

    Write-Log "INFO" "Node.js not found locally. Auto-downloading official runtime to .\node (arch=$arch)."

    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    }
    catch {
        # Keep going; modern PowerShell versions usually handle TLS defaults correctly.
    }

    $releases = Invoke-RestMethod -Uri $indexUrl -Method Get -TimeoutSec 30
    if (-not $releases) {
        throw "Failed to query Node.js release index: $indexUrl"
    }

    $targetRelease = $releases | Where-Object { $_.lts -and $_.files -contains $zipTag } | Select-Object -First 1
    if (-not $targetRelease) {
        throw "No LTS Node.js package found for $zipTag."
    }

    $version = [string]$targetRelease.version
    if ([string]::IsNullOrWhiteSpace($version)) {
        throw "Invalid Node.js version from release index."
    }

    $zipName = "node-$version-win-$arch.zip"
    $downloadUrl = "https://nodejs.org/dist/$version/$zipName"
    $tempRoot = Join-Path $RootPath "data\node_runtime_download"
    $zipPath = Join-Path $tempRoot $zipName
    $extractRoot = Join-Path $tempRoot "extract"

    try {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
        New-Item -ItemType Directory -Path $extractRoot -Force | Out-Null

        Write-Log "INFO" "Downloading Node.js package: $downloadUrl"
        Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath -TimeoutSec 120

        Write-Log "INFO" "Extracting Node.js package..."
        Expand-Archive -Path $zipPath -DestinationPath $extractRoot -Force

        $extractedFolder = Get-ChildItem -Path $extractRoot -Directory | Select-Object -First 1
        if (-not $extractedFolder) {
            throw "Node.js package extraction failed: no extracted directory found."
        }

        if (-not (Test-Path $localNodeDir)) {
            New-Item -ItemType Directory -Path $localNodeDir -Force | Out-Null
        }
        else {
            Get-ChildItem -LiteralPath $localNodeDir -Force | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
        }

        Copy-Item -Path (Join-Path $extractedFolder.FullName "*") -Destination $localNodeDir -Recurse -Force

        if (-not (Test-Path $localNodeExe)) {
            throw "Node.js download completed but node.exe is missing in .\node."
        }

        Write-Log "INFO" "Local Node.js runtime ready: $localNodeExe ($version)"
        return $localNodeExe
    }
    finally {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}


$pathHash = [System.Security.Cryptography.SHA256]::Create()
try { $id = ([BitConverter]::ToString($pathHash.ComputeHash([Text.Encoding]::UTF8.GetBytes($projectRoot.ToLowerInvariant())))).Replace('-', '') }
finally { $pathHash.Dispose() }
$mutex = New-Object System.Threading.Mutex($false, "Local\KiyoriPcAgentLauncher-$id")
$hasLock = $false
$originalPath = $env:Path
$originalNodeOptions = $env:NODE_OPTIONS
$originalBindOverride = $env:KIYORI_BIND_ADDRESS_OVERRIDE
try {
    $hasLock = $mutex.WaitOne(0)
    if (-not $hasLock) { Write-Host 'Another launcher for this directory is running.'; exit 1 }
    foreach ($directory in @($dataDir, $logsDir)) {
        if (-not (Test-Path -LiteralPath $directory)) { New-Item -ItemType Directory -Path $directory | Out-Null }
    }
    $runtime = Resolve-NodeRuntime -RootPath $projectRoot
    if (-not $runtime) {
        Ensure-LocalNodeRuntime -RootPath $projectRoot | Out-Null
        $runtime = Resolve-NodeRuntime -RootPath $projectRoot
    }
    if (-not $runtime) { throw 'Node.js runtime unavailable.' }
    $env:Path = "$($runtime.NodeDir);$env:Path"
    Ensure-Dependencies -RootPath $projectRoot
    # Stop only recorded processes owned by this directory. Never guess ownership from a port.
    $previousIds = @()
    if (Test-Path -LiteralPath $pidPath) {
        $rawPid = (Get-Content -LiteralPath $pidPath -Raw).Trim()
        if ($rawPid -match '^\d+$') { $previousIds += [int]$rawPid }
    }
    if (Test-Path -LiteralPath $runtimePath) {
        try { $previousIds += [int](Get-Content -LiteralPath $runtimePath -Raw | ConvertFrom-Json).pid }
        catch { throw 'Invalid runtime.json; inspect the existing Agent before restarting.' }
    }
    foreach ($previousId in ($previousIds | Select-Object -Unique)) {
        if ($previousId -gt 0 -and (Get-Process -Id $previousId -ErrorAction SilentlyContinue)) {
            if (-not (Stop-OwnedAgentProcess -ProcessId $previousId -RootPath $projectRoot)) {
                throw 'Cannot verify ownership of the recorded process. Close the old Agent manually or run with the same Windows permissions.'
            }
        }
    }
    $env:NODE_OPTIONS = ''
    Remove-Item Env:KIYORI_BIND_ADDRESS_OVERRIDE -ErrorAction SilentlyContinue
    $entry = Join-Path $projectRoot 'src\server.js'
    $child = Start-Process -FilePath $runtime.NodePath -ArgumentList ('"' + $entry + '"') -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru
    Set-Content -LiteralPath $pidPath -Value ([string]$child.Id) -Encoding ASCII
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    $managementUrl = ''
    while ([DateTime]::UtcNow -lt $deadline -and -not $child.HasExited) {
        if (Test-Path -LiteralPath $runtimePath) {
            try {
                $record = Get-Content -LiteralPath $runtimePath -Raw | ConvertFrom-Json
                if ([int]$record.pid -eq $child.Id -and (Test-ManagementReady -Url $record.managementUrl -ExpectedPid $child.Id)) {
                    $managementUrl = $record.managementUrl
                    break
                }
            } catch { Write-Log 'WARN' 'Waiting for complete runtime metadata.' }
        }
        Start-Sleep -Milliseconds 200
        $child.Refresh()
    }
    if (-not $managementUrl) {
        throw "Agent management endpoint not ready. Inspect $errLog and $launcherLog; no alternate listener was started."
    }
    Write-Log 'OK' "Agent management ready: $managementUrl"
    if (-not $NoBrowser) { Start-Process $managementUrl }
} catch {
    if (Test-Path -LiteralPath $logsDir) { Write-Log 'ERROR' $_.Exception.Message } else { Write-Error $_.Exception.Message }
    exit 1
} finally {
    $env:Path = $originalPath
    $env:NODE_OPTIONS = $originalNodeOptions
    $env:KIYORI_BIND_ADDRESS_OVERRIDE = $originalBindOverride
    if ($hasLock) { $mutex.ReleaseMutex() | Out-Null }
    $mutex.Dispose()
}
