---
status: accepted_design
plan_version: 3
baseline: 62464b054f6de00b70c5596295bc216eb8edf63d
last_reviewed: 2026-07-31
---

# 兼容合同与稳定标识清单

## 目的

目录和类名可以逐步变化，但以下合同决定现有开发数据、系统集成、插件生态和上游同步是否仍然成立。
本清单是每个里程碑的“不可意外变化集合”。

合同分为四类：

- `FROZEN`：值和语义都不能变
- `BRIDGED`：旧入口必须保留，真实实现可以迁移
- `MIGRATABLE`：可以迁移，但必须有独立迁移设计和验证
- `REVIEW`：当前证据不足，实施前逐项确认

## Android 身份

| 标识 | 当前值 | 类别 | 规则 |
| --- | --- | --- | --- |
| application ID | `com.kiyori` | `FROZEN` | 不改，保证开发安装、备份和 APK 识别连续 |
| Gradle namespace | `com.ai.assistance.operit` | `FROZEN` in phase 1 | 首阶段不改；未来单独 RFC |
| versionCode | `45` | `FROZEN` per milestone | 重构里程碑不调整 |
| versionName | `0.1.0` | `FROZEN` per milestone | 不把架构提交伪装成产品发布 |
| terminal gitlink | `8d5c2c224317c0176c14520facbb202a8be5993f` | `FROZEN` | 本轮不初始化、不修改、不进入提交 |
| `com.ai.assistance.operit.terminal` | terminal compatibility package | `FROZEN` | 本轮不纳入 |

## Room、ObjectBox 与备份

| 合同 | 当前值/位置 | 类别 | 验证 |
| --- | --- | --- | --- |
| Room database | `app_database` | `FROZEN` | 字面量 snapshot |
| Room schema | version `20` | `FROZEN` per naming refactor | `AppDatabase.kt` SHA-256 |
| Room tables | `chats`, `messages`, `message_variants` 等现有表 | `FROZEN` | database/entity source SHA-256 |
| ObjectBox model/UID | 当前 generated model | `FROZEN` | model 与备份 model SHA-256 |
| ObjectBox 目录 | `objectbox` / `objectbox_$profileId` | `FROZEN` | 字面量与 `ObjectBox.kt` SHA-256 |
| raw snapshot format | `formatVersion = 1` | `FROZEN` | 格式版本、entry prefix snapshot |
| raw snapshot prefix | `operit_raw_snapshot_` | `FROZEN` | path scan |
| snapshot package guard | `com.kiyori` | `FROZEN` | manifest read |
| backup directories | `Download/Kiyori/backup/*` | `FROZEN` | directory inventory |

raw snapshot 当前覆盖 `files`、`external_files`、`shared_prefs`、`datastore` 和 `databases`，
但默认排除部分缓存、模型索引和 terminal 数据。若未来单独执行应用数据迁移，
`Download/Kiyori` 必须作为独立范围处理，不能把 raw snapshot 误称为完整应用数据备份。
方案 v3 当前只保护仓库和本机开发状态，不执行该迁移。

## 启动通知权限

| 合同 | 类别 | 规则 |
| --- | --- | --- |
| Manifest `android.permission.POST_NOTIFICATIONS` | `FROZEN` | 继续恰好声明一次 |
| `com.kiyori.app.startup.KiyoriMainNotificationPermissionCoordinator` | `BRIDGED` | 保留一参数 `ComponentActivity` 构造器与 `checkAndRequest()` JVM 面 |
| `notification_permission_denied` / `notification_permission_rationale` | `FROZEN` per M-05D | 资源名、现有多语言值和 long Toast 语义不改 |
| API < 33 / granted / rationale / request 决策顺序 | `FROZEN` | platform 每次读取系统事实，不保存第二权限状态 |
| `android_permission_preferences` | `FROZEN` persistence | 用户首选执行模式，不作为系统 grant/rationale 事实 |

M-05D 只把启动请求的 Android API owner 迁入
`com.kiyori.platform.permission.KiyoriNotificationPermissionCapability`，并用
`com.kiyori.integration.operit.permission.OperitNotificationPermissionResources` 桥接原资源
ID。Browser download/userscript 通知能力检查、相机/定位/麦克风/WebSession 权限流均不是该
兼容桥的一部分。

## DataStore 与 SharedPreferences

### DataStore 文件名

以下名称从源码提取，重构只允许改 owner，不允许改文件名：

```text
tool_permissions
current_chat_id
ui_preferences
wake_word_preferences
waifu_settings
user_preferences
speech_services_preferences
prompt_tags
persona_card_chat_history
model_configs
api_settings
github_auth_preferences
functional_configs
external_http_api_preferences
display_preferences
custom_emoji_settings
character_groups
character_cards
android_permission_preferences
database_backup_settings
url_config
web_session_browser_store
```

同一 DataStore 文件可能被多个 manager 共享，例如 `api_settings`。迁移前必须确认：

- 文件名仍相同
- key 名仍相同
- serializer/default 值仍相同
- 同一个文件只有一个写入 owner
- UI 只是调用 owner，不复制状态

### SharedPreferences 文件名

已确认的重要名称包括：

```text
toolpkg_desktop_widget_host
avatar_preferences
user_profile_document
com.ai.assistance.operit.data.preferences.SkillVisibilityPreferences
agreement_preferences
free_usage_preferences
env_preferences
com.ai.assistance.operit.core.tools.PackageManager
mcp_local_server_prefs
kiyori_player_settings
floating_chat_prefs
browser_download_settings
web_session_browser_settings
market_resubmit_cooldowns
shell_executor_prefs
cloud_embedding_settings_$profileId
memory_search_settings_$profileId
${type.wireValue}_publish_draft
```

其中包含旧包名的 SharedPreferences 名称必须保留。类移动不能自动触发偏好迁移或复制。
上述 DataStore 和 SharedPreferences 文件名已全部进入
`config/architecture/persistence-names.txt`。同一文件名在多个调用点出现时保存精确计数，
防止移动过程中静默复制第二个 owner。`persistence-api-calls.txt` 还保存每个 DataStore、
SharedPreferences、Room 和 WorkManager unique-work 调用的源码路径、API、合同参数与
重复次数；新增调用点、删除调用点、改名或把调用迁移到另一文件都会触发 ARCH009，
而排版和注释变化不会触发。`DataStoreFactory`、`PreferenceDataStoreFactory`、默认
SharedPreferences、原生 SQLite 等尚无当前调用面的持久化创建 API 会直接失败；必须先
补充提取规则、快照、数据兼容设计和负向测试，不能借另一套 API 绕过门禁。

同一 snapshot 还固定：

```text
payload/
payload/shared_prefs/
payload/datastore/
payload/databases/
room_db_backup_
room_db_manual_backup_
room_db_daily_backup
room_db_manual_backup
workflow_
workflow_id
```

这些值分别保护 raw snapshot entry、Room 备份文件、unique work name 和 worker input key。

## Web、Player 与产品公共路径

| 路径/名称 | 类别 | 说明 |
| --- | --- | --- |
| `Download/Kiyori` | `FROZEN` | Kiyori 新建公共数据根 |
| `Download/Operit` | `FROZEN` legacy boundary | 不自动扫描、合并、删除 |
| `websession/userscripts` | `FROZEN` | 用户脚本公共路径 |
| `browser/downloads` | `FROZEN` | 浏览器下载目录 |
| `Download/Kiyori/exports` | `FROZEN` | 播放器/诊断/导出结果 |
| `operit_raw_snapshot_*` | `FROZEN` | 旧备份文件格式 |
| `.operit/config.json` | `FROZEN` | Operit ecosystem identifier |
| `/data/data/com.kiyori` | `FROZEN` | 当前 app sandbox |

M-05E 已建立 `com.kiyori.platform.storage.KiyoriPaths` 作为唯一真实路径 owner，并以
`KiyoriBackupPaths` 提供无状态备份领域投影。旧 `OperitPaths` 与 `OperitBackupDirs`
保留完整 JVM API 作为纯委派兼容入口；任何入口都不得改变目录、大小写、相对层级、
创建语义、plugin ID 算法、raw snapshot 排除集合或备份布局。

## Serialization 与 reflection

必须保持：

- Kotlin serialization discriminator `__type`
- Workflow JSON 中的 `com.ai.assistance.operit.data.model.*` FQCN
- `Class.forName` 可加载的公开 ToolPkg 类
- `Java.type("com.ai.assistance.operit.*")`
- `Java.com.ai.assistance.operit.*`
- `OperitChatArchive`、`OperitArchivedChat`、`OperitArchivedMessage` 等备份模型
- `OperitTavernExtension`、character card payload 和 external tag payload

可以改名的纯内部类必须先证明没有：

- `@SerialName` 或 class discriminator 依赖
- examples/assets Java bridge 依赖
- JSON fixture FQCN
- ProGuard/R8 keep 依赖
- WorkManager/Android reflection 依赖

## WorkManager

现有已持久化 worker 的旧类名必须可实例化：

```text
com.ai.assistance.operit.core.workflow.WorkflowWorker
com.ai.assistance.operit.data.backup.RoomDatabaseBackupWorker
```

如果真实实现迁移，旧类只作为稳定 worker entrypoint，构造参数、input/output key、unique work name
和结果语义不变。两个 worker、对应 scheduler、raw snapshot 与 Room 备份/恢复实现已进入
`critical-file-hashes.txt`；迁移前仍必须在已有任务存在的设备上验证重启、执行、取消和重新调度。

## Manifest、资源和外部组件

以下内容全部进入 Manifest snapshot 对比：

- Application class
- MainActivity、PlayerActivity、CrashReportActivity、DataRecoveryActivity
- `OperitAssistActivity`
- `OperitVoiceInteractionService`
- `OperitVoiceInteractionSessionService`
- `OperitNotificationListenerService`
- `PlayerRuntimeService`
- Browser download services/receivers
- Script/ToolPkg debug receivers
- ExternalChatReceiver
- Tasker activities/receivers
- Widget receivers/config activity
- DocumentsProvider
- ScreenCapture service/activity
- Shizuku/FileProvider authorities

稳定 action 包括：

```text
operit://github-oauth-callback
com.ai.assistance.operit.action.OPEN_DATA_RECOVERY
com.ai.assistance.operit.EXECUTE_JS
com.ai.assistance.operit.DEBUG_INSTALL_TOOLPKG
com.ai.assistance.operit.DEBUG_REFRESH_PACKAGES
com.ai.assistance.operit.DUMP_COMPOSE_DSL_UI
com.ai.assistance.operit.EXTERNAL_CHAT
com.ai.assistance.operit.action.SHOWER_BINDER_READY
com.ai.assistance.operit.TRIGGER_WORKFLOW
com.ai.assistance.operit.action.*
```

稳定 authority 包括：

```text
${applicationId}.shizuku
${applicationId}.fileprovider
${applicationId}.documents.workspace
${applicationId}.documents.memory
${applicationId}.documents.data
${applicationId}.androidx-startup
```

`applicationId` 保持 `com.kiyori`，所以 authority 解析值继续稳定。
`manifest-components.txt` 已机器化保存组件、action、category、authority、scheme/host、
MIME type、process 与组件 permission 的精确多重集合；同名 action 或 process 的重复次数
也不能在未批准里程碑中变化。`manifest-structure-hashes.txt` 同时保存各迁移阶段完整
Manifest 语义树哈希，忽略 XML 格式、属性顺序和同级元素顺序，但保留节点归属和全部
属性值；权限、导出状态、进程、launch mode、备份配置与 intent-filter 归属都不能静默漂移。

### 当前 Manifest 组件入口

以下是当前 Manifest 中必须逐项对比的组件集合。相对类名展开时以当前 namespace 为基准；
未来任何包移动都必须改为明确的绝对类名或保留兼容入口。

| 类型 | 当前入口 | 迁移策略 |
| --- | --- | --- |
| application | `com.kiyori.app.KiyoriApplication` | M-03 已完成绝对 FQCN 迁移；ARCH008/ARCH018 锁定唯一入口和 Manifest 语义 |
| activity | `.core.tools.defaultTool.websession.browser.WebSessionPermissionRequestActivity` | Browser capability 迁移后保持入口 |
| activity | `.core.tools.defaultTool.websession.browser.WebSessionDirectoryPickerActivity` | Browser capability 迁移后保持入口 |
| activity | `.core.tools.defaultTool.websession.userscript.install.UserscriptImportPickerActivity` | userscript 兼容入口 |
| provider | `.provider.WorkspaceDocumentsProvider` | authority 不变 |
| provider | `.provider.MemoryDocumentsProvider` | authority 不变 |
| provider | `.provider.OperitDataDocumentsProvider` | 旧 FQCN 或稳定 provider adapter |
| activity | `.ui.features.player.PlayerActivity` | player 外部入口单独迁移 |
| activity | `.ui.main.MainActivity` | 稳定宿主入口，内部实现可迁移 |
| activity | `.services.assistant.OperitAssistActivity` | 默认助手兼容入口 |
| activity | `.ui.error.CrashReportActivity` | crash process 入口 |
| service | `.core.player.runtime.PlayerRuntimeService` | `:player` AIDL/native 兼容入口 |
| activity | `.ui.recovery.DataRecoveryActivity` | recovery action 兼容入口 |
| activity | `.integrations.tasker.ActivityConfigAIAgentAction` | Tasker 外部入口 |
| activity | `.core.tools.system.ScreenCaptureActivity` | projection 入口 |
| service | `.services.FloatingChatService` | floating lifecycle/action 稳定入口 |
| service | `.core.tools.defaultTool.websession.browser.BrowserDownloadForegroundService` | download owner 入口 |
| service | `.core.tools.defaultTool.websession.browser.BrowserDownloadJobService` | job scheduler 入口 |
| receiver | `.core.tools.defaultTool.websession.browser.BrowserDownloadRuntimeActionReceiver` | download action 入口 |
| receiver | `.core.tools.defaultTool.websession.browser.BrowserDownloadBootReceiver` | boot action 入口 |
| receiver | `.core.tools.defaultTool.websession.browser.BrowserDownloadInstallResultReceiver` | package result 入口 |
| service | `.core.tools.system.ScreenCaptureService` | projection service 入口 |
| service | `.services.notification.OperitNotificationListenerService` | 系统通知监听兼容入口 |
| service | `.api.chat.AIForegroundService` | AI runtime service |
| receiver | `.core.tools.javascript.ScriptExecutionReceiver` | ToolPkg/脚本 action 入口 |
| receiver | `.core.tools.packTool.ToolPkgDebugInstallReceiver` | ToolPkg debug action 入口 |
| receiver | `.core.tools.packTool.PackageDebugRefreshReceiver` | package refresh action 入口 |
| receiver | `.core.tools.packTool.ToolPkgComposeDslDebugDumpReceiver` | Compose DSL debug 入口 |
| receiver | `.integrations.intent.ExternalChatReceiver` | external chat action 入口 |
| service | `.services.UIDebuggerService` | debug overlay 入口 |
| receiver | `.widget.VoiceAssistantWidgetReceiver` | widget host 入口 |
| receiver | `.widget.ToolPkgDesktopWidgetReceiver` | ToolPkg widget 入口 |
| activity | `.widget.ToolPkgDesktopWidgetConfigActivity` | widget config 入口 |
| receiver | `.core.tools.agent.ShowerBinderReceiver` | Shower Binder 入口 |
| activity | `.integrations.tasker.WorkflowTaskerActivityConfig` | Tasker workflow 入口 |
| receiver | `.integrations.tasker.WorkflowTaskerReceiver` | Tasker workflow action 入口 |
| receiver | `.integrations.tasker.WorkflowBootReceiver` | workflow boot 入口 |
| service | `.services.assistant.OperitVoiceInteractionService` | 默认语音助手兼容入口 |
| service | `.services.assistant.OperitVoiceInteractionSessionService` | 默认语音助手 session 入口 |

### 当前外部 action 集合

除 `operit://` 外，源码和 Manifest 已确认的稳定 action 包括：

```text
com.ai.assistance.operit.action.CANCEL_CURRENT_OPERATION
com.ai.assistance.operit.action.ENSURE_MICROPHONE_FOREGROUND
com.ai.assistance.operit.action.EXIT_APP
com.ai.assistance.operit.action.FLOATING_CHAT_SERVICE_STARTED
com.ai.assistance.operit.action.FLOATING_CHAT_SERVICE_STOPPED
com.ai.assistance.operit.action.FLOATING_CHAT_WINDOW_SHOW_FAILED
com.ai.assistance.operit.action.FLOATING_CHAT_WINDOW_SHOWN
com.ai.assistance.operit.action.OPEN_DATA_RECOVERY
com.ai.assistance.operit.action.OPEN_SETTINGS_SHORTCUT
com.ai.assistance.operit.action.OPEN_VOICE_FLOATING_WINDOW
com.ai.assistance.operit.action.PREPARE_WAKE_HANDOFF
com.ai.assistance.operit.action.SCREEN_CAPTURE_FGS_START
com.ai.assistance.operit.action.SET_WAKE_LISTENING_SUSPENDED_FOR_FLOATING_FULLSCREEN
com.ai.assistance.operit.action.SET_WAKE_LISTENING_SUSPENDED_FOR_IME
com.ai.assistance.operit.action.SHOWER_BINDER_READY
com.ai.assistance.operit.action.START_OR_REFRESH_EXTERNAL_HTTP
com.ai.assistance.operit.action.STOP_EXTERNAL_HTTP
com.ai.assistance.operit.action.TOGGLE_WAKE_LISTENING
com.ai.assistance.operit.DEBUG_INSTALL_TOOLPKG
com.ai.assistance.operit.DEBUG_REFRESH_PACKAGES
com.ai.assistance.operit.DUMP_COMPOSE_DSL_UI
com.ai.assistance.operit.EXECUTE_JS
com.ai.assistance.operit.EXTERNAL_CHAT
com.ai.assistance.operit.TRIGGER_WORKFLOW
```

新增 action 必须说明调用者、receiver、权限、是否跨进程、版本和测试，不得因包移动改写旧值。

## AIDL、JNI 与 native

### AIDL

播放器 runtime AIDL 当前位于：

```text
com.ai.assistance.operit.core.player.runtime
```

无障碍 provider AIDL 当前位于：

```text
com.ai.assistance.operit.provider
```

包名、Parcelable 字段、callback 顺序、transaction 语义和 `:player` service 不能混入普通 Kotlin
包移动。两组共 8 个 AIDL 文件已进入 `critical-file-hashes.txt`，因此只保持 package
字面量不变但修改字段、方法或顺序同样会触发门禁。

### JNI

以下导出函数含 `Java_com_ai_assistance_operit_*`：

- `NativeMarkdownSplitter`
- `NativeXmlSplitter`
- `ToolPkgWasmNative`

8 个完整 JNI 导出符号，以及 `sherpa-ncnn-jni`、`sherpa-mnn-jni`、`streamnative`、
`toolpkgwasm`、`operit_ripgrep` 的全部 8 个 `System.loadLibrary` 调用都已按精确出现次数
进入 `native-ipc-identifiers.txt`；仅保持
`Java_com_ai_assistance_operit_` 前缀数量不变但替换类名或方法名同样会失败。

`NativeRipgrep` 使用 `liboperit_ripgrep.so`。除非单独迁移到显式 `RegisterNatives` 并完成 APK/native
审计，否则 Java 包名、导出符号和库文件名保持不变。

## Operit ecosystem

以下名称继续保留：

- `operit://`
- `OperitForge`
- Operit market compatibility version
- `com.operit.*` ToolPkg IDs
- `remote_operit`
- `operit-pc-agent`
- `OPERIT_*` environment variables
- source attribution、license history 和 upstream URLs

这些不是 Kiyori UI 品牌残留，而是生态合同或历史归属。

## 合同变更流程

任何 `FROZEN` 或 `BRIDGED` 合同要改变，必须创建独立决策记录，包含：

1. 现有消费者
2. 数据和协议版本
3. 迁移输入、输出和失败语义
4. 并行运行/安装边界
5. 用户可见提示
6. 回滚路径
7. 适用任务所需的旧数据与 Android 运行环境验收

目录整理不能暗中完成合同迁移。
