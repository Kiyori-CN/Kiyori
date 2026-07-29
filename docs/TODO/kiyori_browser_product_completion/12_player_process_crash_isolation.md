---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: planned
date: 2026-07-28
---

# 播放器进程崩溃隔离与诊断页

## 任务目标

当前 `PlayerSession`、`MpvPlayerEngine`、浏览器悬浮播放器和全屏播放器都位于 Kiyori 主进程。
Java 或 Kotlin 未捕获异常可以进入现有 `CrashReportActivity`，但 mpv、FFmpeg、驱动或
RenderThread 的 native fatal signal 会直接终止承载它的进程。播放器一旦触发该路径，浏览器、
软件首页、AI 和播放器 UI 会一起退出，主进程也没有机会启动崩溃报告页。

本阶段必须建立以下结果：

1. 播放器 native runtime 终止时，Kiyori 主进程、Browser Runtime、AI 状态和当前 WebView 继续存活
2. 前台播放期间立即展示现有崩溃诊断页，并明确标记为播放器运行时崩溃
3. 诊断页可以复制和导出结构化报告、播放器事件记录及系统允许读取的进程退出证据
4. 用户可以返回 Kiyori，或明确请求重新创建播放器进程
5. 重新创建播放器只能由用户操作触发，不自动重试、不建立第二播放器、不切换其他播放内核
6. 现有 `BROWSER_ONLY`、`FLOATING_PLAYER`、`FULLSCREEN_PLAYER`、媒体 request 和 Surface lease
   不变量继续由唯一 `PlayerSession` 持有

Kiyori 尚未发布。本阶段是内部运行时结构替换，不保留旧的进程内 engine 路径、兼容开关或双实现。

## 非目标

- 不修复某一个尚未复现或没有新证据的 mpv、FFmpeg、Mali、HWUI 或 WebView native 根因
- 不更换当前固定 mpv、FFmpegKit、Clang 21 C++ runtime 或 Anime4K 依赖
- 不改变视频候选、原始 URL、headers、Cookie scope、下载 owner 或 WebSession owner
- 不新增 ExoPlayer 播放路径、本地代理、URL 解析器、第二 `PlayerSession` 或第二下载数据库
- 不在本阶段增加后台媒体前台服务、媒体通知、投屏或音乐播放
- 不把 Debug 构建、JVM 测试或模拟信号描述为目标真机已经通过

## 已确认事实

- `CrashReportActivity` 已声明在 `:crash` 进程，但当前入口依赖
  `GlobalExceptionHandler.startActivity()` 成功执行
- `GlobalExceptionHandler` 只能进入 Java/Kotlin 未捕获异常路径，不能在 native fatal signal
  已终止进程后继续运行
- `PlayerActivity` 没有独立进程声明；浏览器悬浮播放器直接消费主进程 `PlayerSession`
- `PlayerSession` 当前直接持有 `MpvPlayerEngine`、`PlayerMediaResolver`、`Surface` 和进度轮询
- `PlayerDebugLogBuffer` 只有内存中的最多 600 行，进程终止后无法恢复
- 2026-07-28 的 vivo Android 16 现场已出现 RenderThread `fdsan` SIGABRT；最近一次
  mpv 初始化和 Surface 时序修正仍处于设备复测待验证状态

Android 能力边界以官方资料为准：

- [进程与线程](https://developer.android.com/guide/components/processes-and-threads)
- [AIDL 跨进程服务](https://developer.android.com/develop/background-work/services/aidl)
- [Binder 死亡通知](https://developer.android.com/reference/android/os/IBinder.DeathRecipient)
- [可跨 Binder 传递的 Surface](https://developer.android.com/reference/android/view/Surface)
- [进程退出信息](https://developer.android.com/reference/android/app/ApplicationExitInfo)
- [进程状态摘要](https://developer.android.com/reference/android/app/ActivityManager#setProcessStateSummary(byte[]))

## 方案选择

### 采用：主进程会话 + 独立播放器运行时服务

只把 native engine 边界移入 `com.kiyori:player`：

```text
com.kiyori
	PlayerSession
	PlayerRuntimeConnection
	PlayerCrashCoordinator
	Browser Runtime
	PlayerActivity
	PlayerSurfaceView

com.kiyori:player
	PlayerRuntimeService
	MpvPlayerEngine
	PlayerMediaResolver
	唯一 MPV core

com.kiyori:crash
	CrashReportActivity
```

主进程 `PlayerSession` 继续是唯一产品状态 owner。`:player` 只有一个命令执行器和一个
`MpvPlayerEngine`，不保存第二份 presentation、浏览器状态或持久化设置。

### 不采用：只增加异常捕获

`try/catch`、`Thread.UncaughtExceptionHandler` 和 `LinkageError` 转换只能覆盖仍能执行 Java 代码的路径，
不能保证 native signal 后主进程存活。

### 不采用：保留主进程 engine 并增加独立 watchdog

该结构仍会让 Browser Runtime、AI 和播放器一起死亡。watchdog 只能尝试重新打开页面，不能满足
“软件不完全退出”的要求，也无法保留主进程内 WebView 和会话状态。

### 不采用：把 PlayerActivity 和 UI 一起移入播放器进程

全屏 Activity 随 native runtime 一起终止后，主进程可能失去前台 Activity；浏览器悬浮 Surface
也仍然需要跨进程协调。保留 UI 和 `PlayerSession` 在主进程，才能让报告页、Browser Runtime 和
用户返回路径保持稳定。

## 固定所有权

| 状态或资源 | 唯一 owner |
| --- | --- |
| presentation、request、播放位置、暂停、倍速、轨道、错误 | 主进程 `PlayerSession` |
| Surface role、owner token、generation、交接和 Activity request | 主进程 Surface lease reducer |
| 用户持久化播放器设置 | 主进程 `PlayerSettingsStore` |
| mpv core、MPV JNI、remote Surface、媒体文件描述符 | `:player` 的 `PlayerRuntimeService` |
| WebView、标签、候选、headers 和 Cookie scope | 现有 WebSession owner |
| 崩溃报告、附件和消费状态 | 跨进程原子文件 `CrashReportStore` |
| 崩溃报告 UI | 现有 `:crash` `CrashReportActivity` |

`:player` 不读取 `PlayerSettingsStore` 的 SharedPreferences。主进程在建立 runtime 和设置改变时发送
不可变配置快照，避免跨进程偏好缓存形成第二状态。

## IPC 合同

新增非导出的绑定服务：

```xml
<service
    android:name=".core.player.runtime.PlayerRuntimeService"
    android:exported="false"
    android:process=":player" />
```

新增 AIDL：

```text
app/src/main/aidl/com/ai/assistance/operit/core/player/runtime/
	IPlayerRuntime.aidl
	IPlayerRuntimeCallback.aidl
	PlayerRuntimeConfig.aidl
	PlayerRuntimeLoadRequest.aidl
	PlayerRuntimeTrackSnapshot.aidl
```

所有会触达 mpv 的 AIDL 命令使用 `oneway`。Binder 线程只校验 runtime generation、command ID 和参数，
随后把命令投递到 `PlayerRuntimeThread`。`MPVLib.create`、初始化、属性、command、Surface 和 destroy
全部在这一条 `HandlerThread` 上串行执行。

回调同样携带 runtime generation、单调 event sequence 和相关 command ID。主进程只在三者匹配当前
连接时消费事件，旧进程的迟到回调不能修改新会话。

首期 IPC 命令分为：

- runtime：register callback、initialize、close
- media：load、pause、seek、speed、track、subtitle、fit、shader、settings
- Surface：attach、update、detach
- utility：screenshot

首期 callback 分为：

- runtime ready、command completed、command failed
- Surface attached、Surface detached
- pause、buffering、position、duration、speed、network speed
- file loaded、track snapshot、natural end、runtime error
- screenshot completed、诊断事件

主线程不得执行同步 Binder 查询。进度和轨道由远端事件推送；`:player` 以不高于当前 250 ms
刷新频率发送合并进度快照。

## runtime 连接状态

`PlayerSession` 增加显式 runtime 状态：

```text
STOPPED
	BINDING
	READY
	ACTIVE
	DEAD
	CLOSING
```

规则：

1. `open()` 生成新的 runtime generation，并进入 `BINDING`
2. Service 连接、callback 注册和 remote initialization ACK 全部完成后进入 `READY`
3. Surface attach ACK 与媒体 load 命令建立后进入 `ACTIVE`
4. bind 失败、Binder 死亡或远端 fatal report 进入 `DEAD`
5. `DEAD` 不自动 bind、不自动 load、不修改网页
6. 用户在崩溃页请求重新启动时生成新的 runtime generation，旧 callback 全部失效
7. 正常 `close()` 进入 `CLOSING`，等待 remote close ACK 或明确的连接结束，再回到 `STOPPED`

`ServiceConnection.onServiceDisconnected()` 与 `IBinder.DeathRecipient` 进入同一个幂等死亡处理入口，
同一 runtime generation 只生成一份报告。

## Surface 两阶段 ACK

现有 Surface lease 继续决定哪个 Android `Surface` 有资格连接。跨进程后，native attach 和 detach
不再被视为同步完成：

1. `surfaceCreated` 注册 role、owner token 和 lease generation
2. 主进程写入 `ATTACH_SENT` 诊断事件，再发送带 generation 的 remote attach
3. `:player` 验证 Surface 有效并完成 `MPVLib.attachSurface`
4. remote callback 返回 `SURFACE_ATTACHED`
5. 主进程收到匹配 ACK 后才设置 `nativeSurfaceAttached=true`
6. pending `loadfile` 只能在该 ACK 后发送
7. `surfaceDestroyed` 先把 lease 标记为等待 remote detach，再发送 detach
8. 只有匹配的 `SURFACE_DETACHED` ACK 才能完成当前 lease、启动全屏 Activity 或允许下一 Surface attach

Binder 死亡代表远端进程及其 native Surface 引用已经消失。主进程必须：

- 使当前 runtime generation 失效
- 丢弃 pending Surface parcel 和迟到 callback
- 把 lease 收敛到无 native attach 的确定状态
- 发出一次全屏 Activity finish 请求
- 移除浏览器悬浮播放器 presentation
- 保留 Browser Runtime 和 WebView，不执行 reload、重新嗅探或网页媒体 JavaScript

remote `Surface` wrapper 只在 attach 到 detach 期间持有；detach、stale command、close 和 Service
销毁都必须释放 remote wrapper。主进程不释放 `SurfaceView` 持有的原始 Surface。

## 媒体与设置跨进程

`PlayerMediaRequest` 仍由主进程保存。remote load DTO 只承载本次 mpv 调用需要的内存数据：

- runtime generation 与 request ID
- 原始 URI
- headers
- 当前设置快照
- Anime4K shader 绝对路径
- 初始倍速

headers、Cookie 和完整 URI 只存在于内存 IPC，不进入崩溃记录、普通日志或页面文本。

`PlayerMediaResolver` 移入 `:player`，由同一应用 UID 的 `ContentResolver` 打开 `content://`。
它继续持有 `ParcelFileDescriptor` 到 remote media close；Instrumentation 必须覆盖外部授予的
`content://` 读取。网络和本地路径继续保持原样，不复制到聊天目录。

截图命令让 remote mpv 写入应用私有 cache 文件；主进程收到完成回调后继续使用现有 MediaStore
流程保存。`:player` 不直接写公共媒体库。

## 诊断记录

### PlayerCrashJournal

主进程新增有界、脱敏的原子诊断记录。只在关键状态变化时写入，不记录每个 250 ms 进度：

- runtime bind、ready、death、close
- initialize、attach、detach、load 的 SENT 与 ACK
- presentation 与 Surface generation
- decoder profile、GPU Next、Vulkan、Anime4K mode
- file loaded、natural end、可见 runtime error
- 用户发出的 pause、seek、speed、track、screenshot

记录只保存 request ID 的短哈希、媒体来源类型、URI scheme 和 host 哈希。禁止保存 query、headers、
Cookie、标题、完整 URL、文件内容或用户私人路径。

### 进程状态摘要

`:player` 只在关键 native 阶段调用 `ActivityManager.setProcessStateSummary()`：

- `init`
- `attach:<generation>`
- `load:<request-hash>`
- `detach:<generation>`
- `destroy`

摘要保持短小且无敏感数据。Android 11 及以上的 `ApplicationExitInfo` 可在进程终止后提供该摘要。

### CrashReportStore

用 `AtomicFile` 建立版本化结构报告，替换当前只有一个 boolean 的 `CrashRecoveryState`：

- report ID、schema version、时间
- `APP_FATAL` 或 `PLAYER_RUNTIME_FATAL`
- process name、PID、runtime generation
- Java throwable 文本
- `ApplicationExitInfo` reason、status、description、importance、timestamp
- process state summary
- 脱敏 PlayerCrashJournal
- 应用日志或 native trace 附件路径
- displayed、resolved 状态

报告页按 report ID 读取，不再通过 Intent 传递大段 stack trace。打开报告只标记 displayed，不立即删除；
用户返回或重启后标记 resolved。存储保留最近五份报告，并限制单个附件大小。

Android 11 及以上在 Binder death 后按 PID 和时间窗口读取 `ApplicationExitInfo`。Android 12 及以上
存在 native trace stream 时保存为有界二进制附件；页面显示退出原因、signal/status 和状态摘要，
导出操作附带原始 trace，不把 protobuf 二进制直接当正文显示。

Android 8 至 Android 10 使用 Binder death、PID、runtime generation 和 PlayerCrashJournal 形成报告。

## 进程感知的异常处理

`OperitApplication` 在 `onCreate()` 识别进程角色：

- 主进程：写入 `APP_FATAL` 报告，启动 `:crash` 页面后终止主进程
- `:player`：写入 `PLAYER_RUNTIME_FATAL` Java 报告并终止；主进程 Binder death 负责读取并展示同一报告
- `:crash`：不得再次启动 `CrashReportActivity`，避免报告进程形成递归崩溃页
- 其他既有进程：保持其当前职责，不初始化播放器 runtime

`:player` 不写主进程 `AppLogger` 的共享 append 文件。播放器诊断通过 callback 进入主进程
`PlayerCrashJournal`；fatal handler 只原子写入自己的结构报告。`:crash` 展示事件同样不追加主进程
日志文件，保证 `operit.log` 只有一个写入进程。

## 崩溃页行为

`CrashReportActivity` 支持两种明确模式：

### APP_FATAL

- 保留当前“重启应用”
- 展示 Java stack、应用日志和结构化报告
- 主进程已死亡，重启使用现有明确启动流程

### PLAYER_RUNTIME_FATAL

- 标题和正文明确说明“视频播放器运行时已停止，Kiyori 仍在运行”
- 主操作为“返回 Kiyori”
- 第二操作为“重新启动播放器”
- 保留复制、导出和导出应用日志
- 展示播放器进程、退出原因、最后 native 阶段、decoder/render 设置和脱敏事件
- 系统 Back 与“返回 Kiyori”只关闭报告页，不杀死主进程、不清除 Browser Runtime

`:crash` 进程不能直接调用 `PlayerSession.getInstance()`。重新启动播放器通过
`MainActivity.ACTION_RESTART_PLAYER_AFTER_CRASH` 和 report ID 发送到现有 `singleTask MainActivity`；
MainActivity 在主进程消费一次性 action，再调用唯一 `PlayerSession`。

主进程有 resumed Activity 时立即打开报告页。主进程位于后台时保存 pending report；下一次
Activity resume 在展示其他播放器 UI 前打开该报告。后台态不强行拉起新任务。

## runtime 死亡后的会话状态

新增纯逻辑 reducer `resolvePlayerRuntimeDeath()`，一次性完成：

- runtime 状态进入 `DEAD`
- 清除 active/pending remote Surface 与 callback sequence
- Surface lease 标记为 remote detach 已完成
- 取消未发出的 fullscreen launch，发出必要的 fullscreen finish
- active presentation 收敛为 `BROWSER_ONLY`
- 当前 request 从 active state 移入 `lastFailedRequest`
- 保存最后已知位置、倍速和 request ID，供用户明确重启时使用
- `loadGeneration` 不在死亡处理时增加

用户点击“重新启动播放器”后创建新 runtime generation，并按明确操作重新 load。
这次 load 必须增加 `loadGeneration`，诊断记录标记为 user requested restart，不描述为无缝续播。

## 分阶段实施

每个里程碑完成后串行执行定向验证和独立 Debug APK 构建，成功后才进入下一阶段。

### 里程碑 12.1：[DONE] 结构化崩溃报告基础

- 新增 `CrashReportRecord`、`CrashReportStore`、附件和消费状态
- 把 `GlobalExceptionHandler` 改为按 report ID 启动崩溃页
- `CrashReportActivity` 支持 `APP_FATAL` 与 `PLAYER_RUNTIME_FATAL`
- 删除 `CrashRecoveryState` boolean owner 及对应旧测试
- 新增报告序列化、原子写入、限额、消费和 UI action 纯逻辑测试
- 构建 Debug APK

完成信号：现有 Java 崩溃页能力不退化，播放器模式可用构造报告独立展示。

本地实施状态，2026-07-28：

- `CrashReportRecord`、`CrashReportStore`、report ID 启动契约与
  `APP_FATAL`/`PLAYER_RUNTIME_FATAL` 双模式页面已实现
- 报告写入使用进程内串行锁、跨进程文件锁和每报告一个 `AtomicFile`，消费状态单调推进，
  只保留最新五份
- `CrashRecoveryState` 及四份旧 instrumentation 测试已删除，新增序列化、保留策略、
  UI 主操作、状态迁移和存储并发测试
- `CrashReportModelsTest`、`compileDebugAndroidTestKotlin`、formal readiness 和
  `git diff --check` 通过
- `:app:assembleDebug` 与 `verifyDebugPlayerRuntimePackaging` 通过；APK 位于
  `app/build/outputs/apk/debug/app-debug.apk`，大小 `463724939` 字节，SHA-256 为
  `8261A8CFBD5180A5C4A7DE7DA133E3C4F85D72901AD6D7D5A051A060B2B1961D`

本里程碑没有安装 APK、操作设备或注入真实崩溃，设备状态保持 `verification_pending`。

### 里程碑 12.2：[DONE] AIDL runtime 与单线程 mpv owner

- 新增 AIDL、DTO、`PlayerRuntimeService`、`PlayerRuntimeConnection`
- 把 `MpvPlayerEngine`、`PlayerMediaResolver` 和 remote progress/track 读取移入 runtime package
- `PlayerSession` 删除直接 engine、resolver、current remote Surface 和同步 progress poll
- 所有 mpv 命令进入单一 HandlerThread，所有 callback 带 generation 和 sequence
- 更新 Surface lease 为 attach/detach ACK 模型
- 保持 `MpvPlayerEngine` 为唯一 MPV binding import owner
- 构建 Debug APK并重新执行 native packaging 审计

完成信号：正常播放链只经过 `:player`，主进程不存在第二 engine 路径，presentation 转换不增加媒体 load。

本地实施状态，2026-07-28：

- Manifest 已注册唯一非导出 `:player` service；AIDL 命令与 callback 都是 `oneway`
- `MpvPlayerEngine`、`PlayerMediaResolver`、媒体描述符、remote Surface 和不高于 250 ms 的
  progress/track 读取均由 `PlayerRuntimeService` 的单一 `HandlerThread` 持有
- 主进程 `PlayerSession` 只保存产品状态、runtime generation、PID、命令编号和 Surface lease，
  不再 import MPV、构造 engine/resolver 或执行同步 Binder 查询
- Surface reducer 使用 `DETACHED`、`ATTACHING`、`ATTACHED`、`DETACHING` 四态，只有匹配
  generation 与 command ID 的 remote ACK 才完成租约
- 全量 `testDebugUnitTest`、`compileDebugAndroidTestKotlin`、formal readiness、
  `git diff --check` 和 `ci.test.test_player_assets` 六项静态测试通过
- `:app:assembleDebug` 与更新后的 `verifyDebugPlayerRuntimePackaging` 通过；APK 位于
  `app/build/outputs/apk/debug/app-debug.apk`，大小 `463724939` 字节，SHA-256 为
  `D418E31199550108870A613824F9A22CE4C384499CBD2A69207A16EAE8341969`

本里程碑没有安装 APK 或运行 instrumentation；跨进程 PID、Surface parcel 和真实播放仍为
`verification_pending`。

### 里程碑 12.3：[DONE] 死亡检测与诊断闭环

- 接入 `DeathRecipient`、幂等 death reducer 和 `PlayerCrashCoordinator`
- 新增 `PlayerCrashJournal`、process state summary 与 `ApplicationExitInfo` 收集
- Binder death 生成或合并唯一播放器 fatal report
- 前台立即展示崩溃页，后台在下一 resume 展示
- `MainActivity.ACTION_RESTART_PLAYER_AFTER_CRASH` 进入用户明确重启路径
- 构建 Debug APK

完成信号：终止 `:player` 后主进程 PID 和 WebSession 不变，报告页出现，且没有自动 runtime 重建。

本地实施状态，2026-07-28：

- `DeathRecipient` 与 Service disconnect 共用幂等入口，session 进入 `DEAD` 后不自动 bind 或 load
- bounded journal、process-state summary 和 Android 11+ `ApplicationExitInfo` 已接入
- 前台立即展示 PENDING 报告，后台由下一次 Activity resume 消费；Java fatal 与 Binder death
  按 PID 和时间窗合并
- 报告页提供“重新启动播放器”和“返回 Kiyori”，MainActivity 仅消费匹配 runtime generation
  的一次性重启动作
- 定向 JVM、AndroidTest 源码编译、formal readiness、差异检查、隐私扫描和独立 Debug APK
  构建通过；APK 大小 `463725699` 字节，SHA-256 为
  `D8EFB42C9AF4E4B6803F5582BDD7C02B659AC0153ADD7EA2638D8B0618527952`

真实进程终止和主进程/WebSession 保留仍为 `verification_pending`。

### 里程碑 12.4：[LOCAL DONE] 清理、门禁与设备验收

- 删除旧进程内 engine 创建、内存-only 日志 owner 和过时文档契约
- 更新 `CONTEXT.md`、`README.md`、`PLAYER_ARCHITECTURE.md`、阶段 8/9/11/12 和总 index
- 增加源码门禁，检查唯一 engine owner、唯一 `:player` service、无同步 Binder、无敏感诊断字段
- 执行定向测试、Android instrumentation 编译、formal readiness、`git diff --check`
- 构建并核验最终 Debug APK、Manifest、DEX、native owner、符号、签名和 16 KB 对齐
- 在 Android 8+ ARM64 与原 vivo Android 16 完成真实进程死亡验收

完成信号：自动检查和 Debug APK 全部通过，目标设备报告页、进程存活、浏览器状态和用户重启路径通过。

## 预计影响文件

新增：

```text
app/src/main/aidl/com/ai/assistance/operit/core/player/runtime/
app/src/main/java/com/ai/assistance/operit/core/player/runtime/
	PlayerRuntimeService.kt
	PlayerRuntimeConnection.kt
	PlayerRuntimeModels.kt
	PlayerRuntimeProcessState.kt
app/src/main/java/com/ai/assistance/operit/util/crash/
	CrashReportModels.kt
	CrashReportStore.kt
	PlayerCrashJournal.kt
	PlayerCrashCoordinator.kt
	PlayerExitInfoCollector.kt
```

主要修改：

```text
app/src/main/AndroidManifest.xml
app/src/main/java/com/ai/assistance/operit/core/application/OperitApplication.kt
app/src/main/java/com/ai/assistance/operit/core/player/PlayerSession.kt
app/src/main/java/com/ai/assistance/operit/core/player/PlayerModels.kt
app/src/main/java/com/ai/assistance/operit/core/player/PlayerSurfaceLeasePolicy.kt
app/src/main/java/com/ai/assistance/operit/core/player/MpvPlayerEngine.kt
app/src/main/java/com/ai/assistance/operit/core/player/PlayerMediaResolver.kt
app/src/main/java/com/ai/assistance/operit/ui/error/CrashReportActivity.kt
app/src/main/java/com/ai/assistance/operit/ui/main/MainActivity.kt
app/src/main/java/com/ai/assistance/operit/ui/features/player/PlayerActivity.kt
app/src/main/java/com/ai/assistance/operit/ui/features/player/PlayerScreen.kt
app/src/main/java/com/ai/assistance/operit/util/GlobalExceptionHandler.kt
```

删除：

```text
app/src/main/java/com/ai/assistance/operit/util/CrashRecoveryState.kt
旧 CrashRecoveryState instrumentation tests
旧 PlayerSession 直接 engine、resolver 与同步 progress poll 路径
```

具体文件名可在实现时按现有包组织收敛，但所有权与边界不能变化。

## 自动测试矩阵

### JVM

- runtime connection：bind、ready、stale generation、callback sequence、close、death 幂等
- Surface lease：attach ACK、detach ACK、提前 Surface、过期 token、双向转挂、runtime death
- session death reducer：全屏、悬浮、binding、loading、closing 各状态收敛
- crash store：原子写入、版本、限额、displayed、resolved、附件
- journal：有界、脱敏、URL/header/Cookie/path 禁止项
- report policy：APP_FATAL、PLAYER_RUNTIME_FATAL、前台和后台 launch
- restart action：只消费一次、生成新 runtime generation、明确增加 load generation

### Android instrumentation

- `PlayerRuntimeService` PID 与主进程 PID 不同
- AIDL callback、generation 和 Binder death 可观察
- 外部授予的 `content://` 可由 `:player` 打开并保持到 close
- `Surface` parcel attach/detach 与 stale Surface 释放
- `CrashReportActivity` 在 `:crash` 读取结构报告
- `MainActivity` restart action 回到主进程唯一 `PlayerSession`

### 静态门禁

- `MPVLib` 和 `MPVNode` import 仍只有一个源文件
- `MpvPlayerEngine` 构造只出现在 `PlayerRuntimeService`
- `PlayerActivity`、浏览器悬浮层和 AI 不持有 runtime binder
- Manifest 只有一个非导出的 `:player` service
- 主线程没有同步 remote query
- 诊断持久化零 headers、Cookie、完整 URL 和私人路径
- runtime death 路径零自动 bind、零自动 load、零 WebView reload

## 本地验证命令

每个实现里程碑至少运行：

```powershell
.\.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main
git diff --check
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

定向测试应先于全量任务。最终 APK 继续核验：

- `com.kiyori`、版本、min/target SDK
- Debug v2 签名
- `:player` 与 `:crash` Manifest 进程
- MPV binding DEX
- 唯一 arm64 native owner、FFmpeg/C++ 符号闭包
- 16 KB ZIP 与全部 arm64 ELF `PT_LOAD`

## 设备验收

在目标设备记录主进程 PID、`:player` PID、系统版本和 APK SHA-256：

1. 浏览器打开同一网页并开始人工全屏播放
2. 终止 `:player`，确认主进程 PID、Browser Runtime、标签和 WebView 仍存在
3. 确认 `CrashReportActivity` 出现，报告类型为播放器运行时，时间和 PID 匹配
4. 确认报告包含退出 reason/status、最后 native 阶段和脱敏事件
5. 检查导出内容不含 Cookie、headers、完整媒体 URL 或私人路径
6. 返回 Kiyori，确认浏览器页面未刷新、未重新嗅探、无残留黑色 Surface
7. 点击“重新启动播放器”，确认创建新的 `:player` PID，且只有这次用户操作触发新 load
8. 在浏览器悬浮播放、悬浮转全屏、全屏返回和 Surface replacement 中分别终止 runtime
9. 验证 Java 未捕获异常和 native SIGABRT 两类报告
10. 在 Android 8 至 Android 10 验证无 `ApplicationExitInfo` 时的 Binder death 报告
11. 在 Android 11 及以上验证 exit reason 和 process state summary
12. 在 Android 12 及以上验证 native trace 附件导出

原 vivo Android 16 必须使用先前出现 `fdsan` 的相同网页、视频和交接步骤复测。进程隔离证明主应用
存活，不等于 native 根因已经消失；两个结论分开记录。

## 风险与约束

### Surface ACK 时序

跨进程 attach/detach 从同步调用变为异步确认，最容易破坏现有双向交接。必须先扩展纯 reducer，
再接入真实 Binder，不能在 UI 回调里直接修改 native attached 状态。

### content URI

临时 URI 授权必须在 `:player` 实测。读取失败是明确播放器错误，不能复制文件、改写 URI 或调用
其他播放器。

### 多进程日志

现有 `AppLogger` append 文件没有跨进程写入合同。`:player` 和 `:crash` 不并发写该文件；诊断使用
独立原子报告和主进程单写 journal。

### runtime 重启竞态

旧 Binder callback、旧 Surface generation 和旧 screenshot 结果必须由 runtime generation 拒绝。
用户重启前不会创建新 service。

### 后台行为

本阶段保持当前 `PAUSE` 或 `CONTINUE` 语义，不新增媒体前台服务。主进程在后台时不强制弹出 Activity，
报告在下一次前台 resume 展示。

### Application 初始化

`:player` 和 `:crash` 只执行其所需的最小 `Application.onCreate()` 路径，不能初始化 Browser Runtime、
AI、数据库、ToolPkg 或主界面前置状态。

## 回滚点

每个里程碑以成功 Debug APK 为边界。12.2 切换到 remote runtime 时，旧的
`PlayerSession -> MpvPlayerEngine` 直接路径必须在同一里程碑完整删除。该里程碑未通过时，恢复到
12.1 的已构建状态，不在工作树中保留可切换的双 runtime。

本计划默认不创建提交、不推送。真正实施时继续在唯一 `main` 工作树上保存用户现有修改，并按每个
里程碑的差异和 APK 证据决定是否进入下一阶段。

## 完成定义

只有同时满足以下条件才能把阶段 12 标记为完成：

- 主进程只持有一个 `PlayerSession`，`:player` 只持有一个 MPV core
- 播放器进程 Java fatal 与 native fatal 都能形成唯一结构报告
- 前台播放器死亡后 Kiyori 主进程和 Browser Runtime 保持存活
- 崩溃页可以返回 Kiyori，并由用户明确重新启动播放器
- 无自动重试、第二 engine、第二 PlayerSession、URL 改写或网页刷新
- JVM、instrumentation 编译、formal readiness、差异检查和 Debug APK 构建通过
- APK Manifest、DEX、native、签名和 16 KB 审计通过
- 目标 Android 设备完成全屏、悬浮、转场和 native SIGABRT 验收

当前本地状态为 `[LOCAL DONE]`。里程碑 12.1、12.2、12.3 及 12.4 的源码、权威文档、
自动检查和 Debug APK 已完成；设备安装、崩溃注入和真机验收未授权，因此阶段最终状态保持
`verification_pending`，不能宣称真实设备问题已根治。
