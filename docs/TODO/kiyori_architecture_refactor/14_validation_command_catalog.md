---
status: accepted_design
plan_version: 3
baseline: 62464b054f6de00b70c5596295bc216eb8edf63d
last_reviewed: 2026-07-31
---

# 验证命令目录

## 使用边界

本文件列出正式实施时的命令。当前方案阶段不因文档出现命令而获得执行源码重构、设备、提交或推送权限。

命令从高信号、低成本到高成本串行执行。失败后停止，不跳过真实失败。

## 基线与仓库卫生

```powershell
git status --short --branch
git branch --show-current
git rev-parse HEAD
git rev-parse origin/main
git rev-list --left-right --count HEAD...origin/main
git submodule status --recursive
git diff --check
```

正式准备：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_formal_readiness.py `
  --repository . `
  --require-main
```

新鲜克隆门禁只在需要验证 clone/submodule 可复现时执行：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_fresh_clone.py `
  --repository .
```

## 文档

已提交 candidate 的 Markdown 链接：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_markdown_links.py `
  --base <base-commit> `
  --candidate <candidate-commit>
```

未提交方案文档使用等价的本地相对链接扫描，并在提交后重新运行正式脚本。

## G-00 架构与稳定合同门禁

G-00 当前命令：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --ownership config\architecture\package-ownership.toml `
  --require-main
```

对应单元测试：

```powershell
.\.venv\Scripts\python.exe -B -m unittest `
  ci.test.test_architecture_boundaries
```

G-00 当前基线必须通过；超范围文件、Manifest 多重集合、稳定合同计数、
AIDL/Room/ObjectBox 规范化文件哈希、terminal 变化和 M-01 非纯改名样例必须稳定失败。

## Application 与 App Shell

最窄编译：

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

定向测试示例：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.ai.assistance.operit.ui.main.shell.KiyoriShellStateTest" `
  --tests "com.ai.assistance.operit.ui.main.navigation.RouteBackGuardRegistryTest" `
  --tests "com.ai.assistance.operit.ui.main.MainActivityBrowserActionTest" `
  --no-daemon --console=plain
```

Application 第一里程碑应新增或确定以下特征覆盖：

- manifest Application class
- main/crash/repair/player process `onCreate`
- `initializeMainUiPrerequisites`
- `initializeMainApplication`
- WorkManager provider
- global JSON 与 ImageLoader
- startup timestamp 和日志

M-01 基线计数：

```text
14 个 Kotlin 文件
42 次 Kotlin 出现
1 次 Manifest 出现
6 次 Lint baseline 路径
16 个实现文件
49 次总出现
```

计数和允许文件必须与
[M-01 Application 原包改名精确影响清单](15_m01_application_rename_exact_manifest.md)
一致。

## Browser

基础定向测试组：

```text
BrowserAddressResolverTest
BrowserPresentationReleaseGateTest
BrowserBackgroundAnchorPolicyTest
WebSessionBrowserBackPolicyTest
WebSessionProfilePolicyTest
WebSessionHistoryPolicyTest
WebSessionBookmarkPolicyTest
BrowserDownloadPolicyTest
BrowserDownloadRuntimePolicyTest
UserscriptMatcherTest
UserscriptManagementPolicyTest
UserscriptStorageTransactionTest
UserscriptWebRequestEngineOwnershipTest
WebSessionBrowserChromeLayoutTest
```

执行形式：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "<fully-qualified-test-class>" `
  --no-daemon --console=plain
```

Browser 领域完成前还需：

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

## Player

定向测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.ai.assistance.operit.core.player.PlayerPolicyTest" `
  --tests "com.ai.assistance.operit.core.player.PlayerSurfaceLeasePolicyTest" `
  --tests "com.ai.assistance.operit.core.player.runtime.PlayerRuntimeProtocolPolicyTest" `
  --tests "com.ai.assistance.operit.ui.features.player.PlayerControlsPolicyTest" `
  --no-daemon --console=plain
```

native 与 instrumentation 编译：

```powershell
.\gradlew.bat :app:externalNativeBuildDebug --no-daemon --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --console=plain
```

最终 player packaging gate 由 Debug 构建触发，也可按项目任务单独核验。

## 完整 JVM 与 Debug 构建

高风险代码里程碑：

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

文档里程碑至少执行：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

## APK 审计

产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

先解析 Android Build Tools 的绝对路径。自动化中不依赖当前 shell 是否恰好把工具加入 PATH：

```powershell
$buildTools = (Get-Command aapt.exe -ErrorAction Stop).Source | Split-Path
$aapt = Join-Path $buildTools "aapt.exe"
$apksigner = Join-Path $buildTools "apksigner.bat"
$zipalign = Join-Path $buildTools "zipalign.exe"
```

基本信息：

```powershell
& $aapt dump badging app\build\outputs\apk\debug\app-debug.apk
```

签名：

```powershell
& $apksigner verify --verbose app\build\outputs\apk\debug\app-debug.apk
```

16 KB 对齐：

```powershell
& $zipalign -c -P 16 -v 4 app\build\outputs\apk\debug\app-debug.apk
```

SHA-256：

```powershell
Get-FileHash -Algorithm SHA256 app\build\outputs\apk\debug\app-debug.apk
```

必须检查实际输出，不只检查退出码。

## 静态合同反向搜索

示例：

```powershell
rg -n "OperitApplication|KiyoriApplication" app
rg -n "com\.ai\.assistance\.operit" app/src/main examples tools ci
rg -n "classDiscriminator|Class\.forName|Java\.type|Java\.com\.ai\.assistance\.operit" app examples
rg -n "preferencesDataStore|getSharedPreferences|databaseBuilder|enqueueUnique" app/src/main/java
rg -n "Java_com_ai_assistance_operit|System\.loadLibrary" app/src/main
```

搜索结果必须按兼容合同清单分类，不能把零匹配当作唯一目标。

## 未来设备验收目录

方案 v3 当前范围不使用手机、模拟器或 ADB。以下目录只为未来需要真实 Android 行为证据的
里程碑保留；G-00 与 M-01 均未执行这些设备操作。

### Application/Shell

- 冷启动与热启动
- crash/repair/player 进程
- 协议、权限引导和插件加载
- Kiyori Home、AI Home、抽屉、Back、旋转
- 外部 HTTP/HTTPS、分享、快捷方式

### Browser

- 普通和无痕 Profile
- 标签、历史、书签、下载、userscript
- APP_SHELL 与 background anchor
- 人工页面和 AI 工具共用同一窗口
- 退出、最小化、进程恢复

### Player

- 任意网络视频
- floating/fullscreen 往返
- Surface transfer
- 播放设置、Anime4K、倍速、音量
- `:player` 进程异常隔离

### 系统入口

- 默认助手
- 通知监听
- Widget
- 静态快捷方式
- DocumentsProvider
- Tasker
- External Chat
- Workflow schedule

### 数据

- 旧聊天、设置、模型配置、角色卡和工作区
- Browser history/bookmarks/userscripts/download tasks
- raw snapshot export/restore
- `Download/Kiyori`
- WorkManager 已排队任务

## 证据记录格式

每条命令记录：

```text
command:
cwd:
start time:
end time:
exit code:
task/test count:
failure count:
artifact:
artifact hash:
evidence level:
remaining verification:
```

未来设备任务的记录还需要：

```text
device model:
Android version:
WebView provider/version:
APK SHA-256:
pre-existing data present:
result:
```
