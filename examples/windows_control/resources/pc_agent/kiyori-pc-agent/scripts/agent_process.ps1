function Test-OwnedAgentProcess {
    param([object]$ProcessInfo, [string]$RootPath)
    if (-not $ProcessInfo -or $ProcessInfo.Name -ne 'node.exe' -or -not $ProcessInfo.CommandLine) { return $false }
    $expectedEntry = [System.IO.Path]::GetFullPath((Join-Path $RootPath 'src\server.js'))
    $arguments = [regex]::Matches([string]$ProcessInfo.CommandLine, '"[^"]*"|\S+')
    foreach ($argument in $arguments) {
        if ([string]::Equals($argument.Value.Trim('"'), $expectedEntry, [System.StringComparison]::OrdinalIgnoreCase)) { return $true }
    }
    return $false
}

function Stop-OwnedAgentProcess {
    param([int]$ProcessId, [string]$RootPath)
    if ($ProcessId -le 0) { return $false }
    $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
    if (-not (Test-OwnedAgentProcess -ProcessInfo $processInfo -RootPath $RootPath)) {
        Write-Log 'WARN' "Skipped unverified process PID $ProcessId. Close a legacy agent manually if needed."
        return $false
    }
    # PID 与端口都可能被复用；只有命令行指向本目录绝对入口的 node 进程才能停止。
    Stop-Process -Id $ProcessId -Force -ErrorAction Stop
    Write-Log 'INFO' "Stopped verified PC Agent PID $ProcessId"
    return $true
}
