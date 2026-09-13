---
status: accepted_design
plan_version: 3
baseline: 62464b054f6de00b70c5596295bc216eb8edf63d
last_reviewed: 2026-08-01
---

# 当前架构与命名分类账

## 产品与实现现状

Kiyori 已经不是 Operit 的简单换皮：

- Kiyori App Shell 是顶层导航 owner
- 浏览器、播放器、下载、文件、设置和后续内容能力属于 Kiyori 产品域
- Operit AI 是内嵌 AI 子系统，继续拥有对话、模型、工具、记忆、工作流和插件生态
- 人工页面与 AI 工具必须操作同一份领域状态

源码结构仍主要延续 Operit：

- Application 为 `com.kiyori.app.KiyoriApplication`；Operit AI 已通过 `com.kiyori.platform`
  的 context、Json、startup time 和主初始化合同与具体类解耦
- 根 Composable 为 `ui/main/OperitApp.kt`
- `MainActivity` 同时承担启动门禁、Intent、权限、显示策略和 UI 装配
- Browser Runtime 大量位于 `core/tools/defaultTool/websession/`
- Kiyori App Shell 位于旧 `ui/main/` 下
- Kiyori 主题与 Operit AI 主题仍共享旧 `ui/theme/` 入口

问题不是旧名称本身，而是名称、目录与真实所有权不一致。

## 复杂度评估

| 领域 | 难度 | 主要原因 |
| --- | --- | --- |
| Application 根类 | 中 | M-02 已清除 13 个 concrete 消费者；M-03 已完成 Manifest、Lint、多进程与纯包迁移审计 |
| 根 Composable 与 App Shell | 高 | 路由、CompositionLocal、AI 稳定宿主、Pager、抽屉和外部入口汇合 |
| 主题与通用 UI | 高 | Kiyori 固定主题与 Operit AI 局部个性化共用调用面，且是上游冲突热点 |
| Browser Runtime | 很高 | 运行时埋在 AI tool 路径，人工 UI 与 AI 共用 WebView、下载、脚本和 presentation |
| Player Runtime | 很高 | 主进程状态、`:player` 进程、AIDL、native mpv、Surface lease 与浏览器交接 |
| 数据与备份 | 很高 | Room、ObjectBox、DataStore、SharedPreferences、外部目录和 raw snapshot |
| Operit AI 核心 | 高但不应整体迁移 | 上游持续演进，存在插件 Java bridge、序列化类型和协议依赖 |
| Gradle namespace | 很高 | 会改变 R/BuildConfig 包、Manifest 相对类名和大量上游 import |
| terminal | 不评估 | 用户明确暂不纳入 |

## 命名分类规则

所有 `Operit*` 标识必须先归类，禁止根据字符串批量替换。

### K1：Kiyori 产品根，可直接迁移

这些符号表达当前 Kiyori 应用所有权，且本身不是数据或外部协议：

| 当前标识 | 目标标识 | 处理 |
| --- | --- | --- |
| `KiyoriApplication` | `com.kiyori.app.KiyoriApplication` | M-01 原包内改名、M-02 全局访问隔离和 M-03 包迁移已完成 |
| `OperitApp` | `KiyoriApp` | Application 稳定后单独迁移 |
| `OperitTheme` | `KiyoriTheme` | 完成主题 owner 审计后迁移 |
| `OperitUtilityTheme` | `KiyoriUtilityTheme` 或并入统一设计系统 | 不与视觉调整混做 |
| `OperitApp:*` wake lock / log tag | `KiyoriApp:*` | 与对应 owner 同批修改 |

文件移动与符号改名必须保持函数体、默认值、资源选择和运行顺序不变。

### K2：名称可改，但必须先分离职责

| 当前标识 | 当前问题 | 目标 |
| --- | --- | --- |
| `OperitPaths` | M-05E 前混合 AI、浏览器、备份和工具调用；公开 JVM API 需要兼容 | 唯一 owner 已迁为 `KiyoriPaths`；旧对象保留纯委派，路径字符串不变 |
| `OperitBackupDirs` | 实际根目录是 `Download/Kiyori/backup`，名称与所有权不符 | `KiyoriBackupPaths` 已成为无状态领域投影；旧对象保留纯委派，备份布局不变 |
| `MainActivity` | 名称无品牌问题，但职责过多且 FQCN 被系统资源引用 | 保留稳定入口或抽出 `KiyoriActivityHost` |
| `ui/main/` | 混合 Kiyori Shell 与 Operit AI 路由 | 按文件所有权拆到 `app/` 与 `integration/operit/` |
| `core/tools/defaultTool/websession/` | Browser Runtime 被目录表达为 AI 默认工具 | Browser owner 迁入 `feature/browser`，旧工具只留适配入口 |

这类迁移先建立特征测试和唯一 owner，再进行符号移动。

### O1：Operit AI 子系统名称，继续保留

以下标识表达 Operit AI runtime、插件生态或上游来源：

- `OperitPlugin`
- `OperitQuickJsEngine`
- `OperitComposeDslRuntime`
- `OperitForge`
- `OperitPackageMarket`
- `OperitScriptMarket`
- ToolPkg Java bridge 中公开的 `com.ai.assistance.operit.*` 类型
- Operit AI 对话、模型、工具、记忆、工作流和市场兼容版本

这些名称不应为了品牌整齐而改写。Kiyori UI 可以使用产品语言，底层生态名保持准确。

### C1：数据、协议或序列化合同，默认冻结

以下标识必须保持稳定，除非另立迁移协议：

- `operit://`
- `com.ai.assistance.operit.*` Intent action 与 extra
- `com.operit.*` ToolPkg ID
- `OPERIT_MARKET_COMPAT_VERSION`
- `.operit/config.json`
- `app_database`
- DataStore 和 SharedPreferences 文件名
- `operit_raw_snapshot_` 文件前缀和 raw snapshot `formatVersion = 1`
- Workflow JSON 的 `__type` FQCN
- `OperitChatArchive`、`OperitArchivedChat`、`OperitArchivedMessage` 等备份模型
- `OperitTavernExtension`、`OperitCharacterCardPayload` 和附加标签模型
- AIDL 包名
- JNI 导出函数和 `liboperit_ripgrep.so`

Kiyori 未发布不代表这些数据可以丢弃；用户明确要求保留现有开发数据。

### E1：Android 外部组件入口

以下类型可能被系统设置、快捷方式、Widget、通知监听、默认助手或其他应用保存为组件名：

- `MainActivity`
- `OperitAssistActivity`
- `OperitVoiceInteractionService`
- `OperitVoiceInteractionSessionService`
- `OperitNotificationListenerService`
- Widget receiver 与 config activity
- WorkManager 的 `WorkflowWorker` 与 `RoomDatabaseBackupWorker`
- Manifest 中导出的 receiver、service、provider 和 activity

推荐做法是保留旧 FQCN 作为极小的稳定入口，把真实实现迁入 Kiyori 包。稳定入口不得持有第二份状态，
不得实现另一套行为，只负责进入唯一实现。

## 持久化与动态入口证据

### 数据库和偏好

- Room 数据库名为 `app_database`
- Room schema version 为 `20`
- raw snapshot 包含 `files`、`external_files`、`shared_prefs`、`datastore` 和 `databases`
- raw snapshot 只接受 `packageName == com.kiyori`
- Browser history 使用 `web_session_browser_store`
- UI、工具权限、聊天 ID、角色、模型、语音、GitHub、下载和播放器均有稳定偏好文件名

目录重构不能改动这些字符串。

### WorkManager

WorkManager 持久化 worker 类名。当前至少存在：

- `com.ai.assistance.operit.core.workflow.WorkflowWorker`
- `com.ai.assistance.operit.data.backup.RoomDatabaseBackupWorker`

移动这两个类会使已有任务无法实例化。它们保持旧入口，或在独立迁移中完成明确的取消、重建和设备验收。

### 序列化与反射

- `WorkflowRepository` 和多个工具 JSON 使用 `classDiscriminator = "__type"`
- 示例 workflow 直接保存 `com.ai.assistance.operit.data.model.TriggerNode` 等 FQCN
- ToolPkg 和 examples 通过 `Java.type` 或 `Java.com.ai.assistance.operit` 访问宿主类型
- JS bridge 使用 `Class.forName`

`data.model`、公开 bridge 类型与 hooks 默认留在 Operit 兼容岛。

### JNI 与 AIDL

当前 native 代码导出多个
`Java_com_ai_assistance_operit_*` 函数，覆盖 Markdown、XML 和 ToolPkg WASM。
播放器与无障碍 provider 的 AIDL 也使用旧包名。

它们不进入普通 Kotlin 文件移动批次。需要迁移时必须作为 native/IPC 专项完成。

## 当前最重要的反模式

- `core` 依赖 `ui`，说明底层包含 presentation 或宿主回调
- `data` 依赖 `core` 与 `ui`，说明数据层混入业务或界面类型
- 浏览器 runtime 位于 AI tool 路径，目录无法表达产品 owner
- 根 Activity 与 Application 直接初始化大量子系统
- `util` 同时包含路径、序列化、媒体池、网络、文档、日志和 native wrapper
- `ui/features` 同时包含 Operit AI 页面与 Kiyori 产品功能

重构必须修复所有权和依赖方向，不能只把这些目录整体换名。
