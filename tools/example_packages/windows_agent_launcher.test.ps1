$ErrorActionPreference = 'Stop'
$agentRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../examples/windows_control/resources/pc_agent/kiyori-pc-agent'))
$scriptsRoot = Join-Path $agentRoot 'scripts'
foreach ($file in (Get-ChildItem -LiteralPath $scriptsRoot -Filter '*.ps1')) {
    $parseErrors = $null
    $parseTokens = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($file.FullName, [ref]$parseTokens, [ref]$parseErrors)
    if ($parseErrors.Count -gt 0) { throw "PowerShell parse failure: $($file.Name): $parseErrors" }
}
. (Join-Path $scriptsRoot 'agent_process.ps1')
$entry = Join-Path $agentRoot 'src\server.js'
$owned = [pscustomobject]@{ Name = 'node.exe'; CommandLine = 'node.exe "' + $entry + '"' }
if (-not (Test-OwnedAgentProcess $owned $agentRoot)) { throw 'Absolute owned entry rejected' }
foreach ($candidate in @(
    [pscustomobject]@{ Name = 'node.exe'; CommandLine = 'node.exe src/server.js' },
    [pscustomobject]@{ Name = 'node.exe'; CommandLine = 'node.exe "C:\another-agent\src\server.js"' },
    [pscustomobject]@{ Name = 'other.exe'; CommandLine = 'other.exe "' + $entry + '"' },
    [pscustomobject]@{ Name = 'node.exe'; CommandLine = 'node.exe "' + $entry + '.backup"' },
    [pscustomobject]@{ Name = 'node.exe'; CommandLine = 'node.exe -e "' + $entry + '"' },
    [pscustomobject]@{ Name = 'node.exe'; CommandLine = 'node.exe other.js "' + $entry + '"' }
)) {
    if (Test-OwnedAgentProcess $candidate $agentRoot) { throw 'Unowned process accepted' }
}
Write-Output 'PASS: all launcher scripts parse; 7 ownership cases passed; no processes stopped.'
