function Test-OwnedAgentProcess {
    param([object]$ProcessInfo, [string]$RootPath)
    if (-not $ProcessInfo -or $ProcessInfo.Name -ne 'node.exe' -or -not $ProcessInfo.CommandLine) { return $false }
    $expectedEntry = [System.IO.Path]::GetFullPath((Join-Path $RootPath 'src\server.js'))
    $arguments = [regex]::Matches([string]$ProcessInfo.CommandLine, '"[^"]*"|\S+')
    # The launcher uses node.exe <absolute entry>; -e text or later script arguments are not the entry.
    return ($arguments.Count -ge 2 -and [string]::Equals($arguments[1].Value.Trim('"'), $expectedEntry, [System.StringComparison]::OrdinalIgnoreCase))
}

function Stop-OwnedAgentProcess {
    param([int]$ProcessId, [string]$RootPath)
    if ($ProcessId -le 0) { return $false }
    $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
    if (-not (Test-OwnedAgentProcess -ProcessInfo $processInfo -RootPath $RootPath)) {
        Write-Log 'WARN' "Skipped unverified process PID $ProcessId. Close a legacy agent manually if needed."
        return $false
    }
    # PIDs and ports can be reused. Stop only node with this directory's exact absolute script entry.
    Stop-Process -Id $ProcessId -Force -ErrorAction Stop
    Wait-Process -Id $ProcessId -Timeout 5 -ErrorAction SilentlyContinue
    Write-Log 'INFO' "Stopped verified PC Agent PID $ProcessId"
    return $true
}
