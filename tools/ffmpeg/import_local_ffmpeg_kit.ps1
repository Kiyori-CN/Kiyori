[CmdletBinding()]
param(
    [string]$AarPath,
    [string]$Distro = "FedoraLinux-43"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$appLibsDir = Join-Path $repoRoot "app\libs"
$jniLibsDir = Join-Path $repoRoot "app\src\main\jniLibs"
$targetAar = Join-Path $appLibsDir "ffmpeg-kit-local.aar"

if (-not (Test-Path $appLibsDir)) {
    throw "app/libs 不存在: $appLibsDir"
}

if ([string]::IsNullOrWhiteSpace($AarPath)) {
    $findCommand = @'
if [ -d ~/build/ffmpeg-kit/prebuilt/bundle-android-aar ]; then
  find ~/build/ffmpeg-kit/prebuilt/bundle-android-aar -maxdepth 1 -name "*.aar" | head -n 1
else
  find ~/build/ffmpeg-kit/android/ffmpeg-kit-android-lib/build/outputs/aar -maxdepth 1 -name "*.aar" | head -n 1
fi
'@
    $detectedAarPath = wsl.exe -d $Distro -- bash -lc $findCommand
    if ($LASTEXITCODE -ne 0) {
        throw "无法从 WSL 查找 ffmpeg-kit AAR: $Distro"
    }
    $AarPath = ([string]$detectedAarPath).Trim()
}

if ([string]::IsNullOrWhiteSpace($AarPath)) {
    throw "未找到 AAR。请传入 -AarPath，或确认 WSL 构建产物位于 ~/build/ffmpeg-kit/prebuilt/bundle-android-aar 或 ~/build/ffmpeg-kit/android/ffmpeg-kit-android-lib/build/outputs/aar"
}

$resolvedSource = $null
if (Test-Path $AarPath) {
    $resolvedSource = (Resolve-Path $AarPath).Path
} else {
    $linuxPath = $AarPath.Replace('\', '/')
    $targetWslPath = wsl.exe -d $Distro -- wslpath -a $targetAar
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($targetWslPath)) {
        throw "无法将目标路径转换为 WSL 路径: $targetAar"
    }
    $targetWslPath = ([string]$targetWslPath).Trim()
    wsl.exe -d $Distro -- bash -lc 'cp -f -- "$1" "$2"' bash $linuxPath $targetWslPath
    if ($LASTEXITCODE -ne 0) {
        throw "从 WSL 复制 ffmpeg-kit AAR 失败: $linuxPath"
    }
}

if ($resolvedSource) {
    Copy-Item -LiteralPath $resolvedSource -Destination $targetAar -Force
}

if (-not (Test-Path $targetAar)) {
    throw "AAR 复制失败: $targetAar"
}

Get-ChildItem -Path $appLibsDir -Filter "ffmpeg-kit-*.aar" -File |
Where-Object { $_.FullName -ne $targetAar } |
Remove-Item -Force

$legacyJar = Join-Path $appLibsDir "ffmpegkit.jar"
if (Test-Path $legacyJar) {
    Remove-Item -LiteralPath $legacyJar -Force
}

$ffmpegPatterns = @(
    "libavcodec*.so",
    "libavdevice*.so",
    "libavfilter*.so",
    "libavformat*.so",
    "libavutil*.so",
    "libswresample*.so",
    "libswscale*.so",
    "libffmpegkit*.so"
)

foreach ($pattern in $ffmpegPatterns) {
    Get-ChildItem -Path $jniLibsDir -Recurse -File -Filter $pattern -ErrorAction SilentlyContinue |
    Remove-Item -Force
}

Write-Host "Imported ffmpeg-kit AAR:" $targetAar
Write-Host "Legacy ffmpegkit.jar removed if present."
Write-Host "Legacy ffmpeg *.so files removed from:" $jniLibsDir
