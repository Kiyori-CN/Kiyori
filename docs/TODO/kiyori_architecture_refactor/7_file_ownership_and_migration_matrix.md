---
status: accepted_design
plan_version: 3
baseline: 62464b054f6de00b70c5596295bc216eb8edf63d
last_reviewed: 2026-07-31
---

# 源码所有权与文件迁移矩阵

## 使用方法

本表是迁移前的路径级决策，不是“把整个目录搬走”的授权。

每个文件在进入源码变更前必须拥有以下字段：

- 当前路径
- 当前真实 owner
- 目标路径或保留路径
- 迁移动作
- 上游同步等级
- 稳定合同影响
- 前置里程碑
- 必须验证的行为

没有出现在矩阵中的文件不得因为包名相似而批量移动。

## 动作定义

| 动作 | 含义 |
| --- | --- |
| `KEEP-OPERIT` | 留在 Operit 兼容岛，保持路径和公开类型可被上游识别 |
| `KEEP-CONTRACT` | 作为稳定外部入口保留旧 FQCN，内部实现可委派到 Kiyori |
| `MOVE-KIYORI` | 所有权已经明确，按纯移动里程碑迁入 `com.kiyori` |
| `SPLIT` | 当前文件混合两个 owner，先拆职责，再移动其中一侧 |
| `ADAPTER` | 旧入口只保留单一委派，真实状态和实现位于唯一新 owner |
| `REVIEW` | 需要逐文件确认，不能由目录规则自动决定 |
| `EXCLUDE` | 明确不在本次重构范围，例如 terminal |

## 非 Kotlin/Java 资产矩阵

包路径移动不能只检查 `app/src/main/java`。以下路径必须和源码迁移一起审计，但不能因为
出现 `Operit` 字符串就批量改名：

| 路径/资产 | 首选动作 | 保护重点 |
| --- | --- | --- |
| `app/src/main/AndroidManifest.xml` | `KEEP-CONTRACT` / `REVIEW` | application、导出组件、进程、权限、intent-filter、authority 和相对类名 |
| `app/src/main/aidl/` | `KEEP-CONTRACT` | AIDL package、Parcelable 字段、callback 顺序和 transaction 语义 |
| `app/src/main/cpp/`、`CMakeLists.txt`、`*.cpp`、`*.h` | `KEEP-CONTRACT` / `REVIEW` | JNI 导出符号、注册表、ABI、库名、编译定义和 native 资源 |
| `app/src/main/res/xml/` | `KEEP-CONTRACT` / `REVIEW` | `provider_paths`、快捷方式、备份规则、文件选择器和组件引用 |
| `app/src/main/res/values*/` | `REVIEW` | 用户可见品牌文案与协议/系统识别字符串分离；不把资源 key 当作显示文案批量改名 |
| `app/src/main/res/drawable*/`、`mipmap*/`、`font/` | `REVIEW` | 资源 ID、主题引用、启动图标和 UI 视觉零变化；纯目录移动不得夹入设计改版 |
| `app/proguard-rules.pro`、`consumer-rules.pro` | `KEEP-CONTRACT` / `REVIEW` | FQCN keep、反射、JNI、WorkManager、ToolPkg bridge 和序列化类型 |
| `app/build.gradle.kts`、`gradle/`、`buildSrc/` | `REVIEW` | namespace、applicationId、source set、任务、native packaging 和上游同步热点 |
| `app/src/test/`、`app/src/androidTest/` | `MOVE-KIYORI` / `KEEP-CONTRACT` | 测试包镜像真实 owner；测试迁移不能改变断言语义或掩盖旧入口缺失 |
| `examples/`、`assets/`、ToolPkg/Skill 脚本 | `KEEP-CONTRACT` / `REVIEW` | `Java.type`、`Class.forName`、JSON FQCN、协议样例和公开脚本兼容性 |
| `ci/`、`tools/`、项目文档 | `REVIEW` | 检查规则、构建入口、路径说明和协作者工作流同步；不把临时日志写入正式文档 |
| `app/build/`、`.gradle/`、`.idea/`、`local.properties`、签名/密钥 | `EXCLUDE` | 生成物、机器状态和私密配置不得进入迁移提交 |
| `terminal` gitlink 与其未初始化内容 | `EXCLUDE` | 本轮不初始化、不改动、不备份为源码副本、不进入提交 |

每个非 Kotlin/Java 资产都必须能追溯到对应源码里程碑、稳定合同、验证命令和回滚点。
Manifest、AIDL、native、资源或 CI 的变化如果不是纯路径引用修正，必须从普通文件移动提交中
拆成独立风险批次。

## 一级目录矩阵

| 当前路径 | 规模 | 当前真实内容 | 首选动作 | 目标或保留位置 | 前置条件 |
| --- | ---: | --- | --- | --- | --- |
| `api/` | 76 | AI provider、语音、外部 HTTP 和聊天服务 | `KEEP-OPERIT` / `SPLIT` | Operit AI runtime；Kiyori 外部入口由 integration 适配 | 先区分 AI API 与产品平台 API |
| `core/application/` | 3 | Application、生命周期和前台服务辅助 | `SPLIT` | `com.kiyori.app` 与 `com.kiyori.platform.lifecycle` | 先收口全局 Application 访问 |
| `core/avatar/` | 35 | AI 角色与渲染实现 | `KEEP-OPERIT` | Operit AI UI/runtime | 不与 Kiyori design system 混合 |
| `core/browser/` | 2 | Browser address/presentation policy | `MOVE-KIYORI` | `feature/browser/domain`、`presentation` | capability 和状态 owner 先冻结 |
| `core/chat/` | 6 | AI 消息与 hooks | `KEEP-OPERIT` | Operit AI runtime | 保护 hooks FQCN 和 ToolPkg 入口 |
| `core/config/` | 4 | AI prompt/config | `KEEP-OPERIT` | Operit AI runtime | 不把 Kiyori 设置页误当 owner |
| `core/player/` | 16 | PlayerSession、Surface lease、mpv runtime | `SPLIT` | Kiyori player domain/runtime；AIDL/service 保留兼容入口 | 先完成跨进程和 native contract inventory |
| `core/subpack/` | 5 | APK/EXE/export 工具 | `KEEP-OPERIT` / `REVIEW` | Operit 工具 runtime 或独立 export feature | 保护 ToolPkg/脚本 Java bridge |
| `core/tools/` | 191 | AI tool registry、system tools、browser adapters、JS/WASM | `KEEP-OPERIT` / `SPLIT` | AI tool runtime；Kiyori capability adapter | 不能把整个目录改成 product feature |
| `core/workflow/` | 4 | Workflow worker/scheduler/executor | `KEEP-CONTRACT` | Operit workflow；worker FQCN 稳定入口 | 先完成 WorkManager 恢复验证 |
| `data/` | 130 | AI 数据、偏好、数据库、备份、市场和浏览器存储 | `REVIEW` | 按 owner 分到 Operit data、Kiyori feature/data 或 platform/storage | 先依据合同清单逐文件审查 |
| `integrations/` | 17 | HTTP、Tasker、外部 Intent 等 | `SPLIT` | `integration/operit`、`integration/tasker`、`integration/http` | action、extra 和外部调用保持稳定 |
| `plugins/` | 14 | PluginRegistry、ToolPkg lifecycle | `KEEP-OPERIT` | Operit plugin ecosystem | Kiyori 只通过 integration 调用 |
| `provider/` | 3 | DocumentsProvider 与外部文件入口 | `KEEP-CONTRACT` | 旧 FQCN 或明确 provider adapter | authority 和 URI 不变 |
| `services/` | 23 | AI、浮窗、通知、语音和服务入口 | `SPLIT` / `KEEP-CONTRACT` | Kiyori platform/service 或 Operit AI service | 先确认导出状态和系统保存关系 |
| `ui/main/` | 30 | Kiyori Shell、MainActivity、AI route 和 root composition | `SPLIT` | `com.kiyori.app`、`integration/operit/navigation` | 不可整体移动 |
| `ui/features/chat/` | 164 | Operit AI 对话和输入 | `KEEP-OPERIT` | Operit AI UI | 保持 AI 会话和渲染合同 |
| `ui/features/websession/` | 27 | Browser chrome、drawer、userscript UI | `MOVE-KIYORI` | `feature/browser/ui` | UI 只观察同一 Browser owner |
| `ui/features/player/` | 7 | Player Activity、controls、fullscreen | `MOVE-KIYORI` / `KEEP-CONTRACT` | `feature/player/ui`；Activity FQCN 单独审查 | 保持 Surface lease 和 Activity entry |
| `ui/features/settings/` | 53 | AI 设置、Kiyori 设置、市场和主题设置 | `SPLIT` | `feature/settings` 与 Operit AI UI | 以 persisted owner 而非页面标题分类 |
| `ui/features/toolbox/` | 47 | AI 工具箱和各类工具页 | `KEEP-OPERIT` / `SPLIT` | Operit AI tools；Kiyori product pages only when owner exists | 不把空页面接入错误领域 |
| `ui/theme/` | 11 | 共享主题、Kiyori semantic theme、utility theme | `SPLIT` | `design/theme` 与 Operit AI local theme | 先保证视觉快照和偏好语义不变 |
| `ui/common/` | 38 | Markdown、Compose DSL、通用显示和组件 | `SPLIT` / `REVIEW` | AI renderer、design component、platform display | 特殊渲染域不能被普通主题覆盖 |
| `ui/floating/` | 29 | 浮动聊天、语音、屏幕识别、窗口 | `KEEP-CONTRACT` / `SPLIT` | Kiyori platform presentation 与 Operit AI UI | 保持 overlay、权限和生命周期 |
| `util/` | 76 | 路径、序列化、网络、缓存、native、日志和文本 | `SPLIT` | platform、feature data、Operit compatibility | 禁止整体迁入 `com.kiyori.util` |
| `widget/` | 7 | Android widget 与动态 route | `KEEP-CONTRACT` / `ADAPTER` | 旧组件入口 + Kiyori route adapter | 保持 widget host 偏好和组件 FQCN |

## 重点文件组

### Application 与 Shell

| 文件组 | 第一阶段 | 后续目标 |
| --- | --- | --- |
| `core/application/KiyoriApplication.kt` | M-01 已完成原包内纯符号改名 | 全局访问收口后移入 `com.kiyori.app` |
| `ui/main/OperitApp.kt` | 不与 Application 同批 | `com.kiyori.app.KiyoriApp` |
| `ui/main/MainActivity.kt` | 保持 Android 入口稳定 | 抽出 `KiyoriActivityHost` 和 Intent/startup coordinator |
| `ui/main/shell/Kiyori*.kt` | 保持行为不变 | `com.kiyori.app.shell` |
| `ui/main/navigation/*` | 逐文件分类 | Kiyori navigation 或 `integration.operit.navigation` |
| `ui/main/screens/OperitScreens.kt` | 保留 | Operit AI screen contract |

### Browser

| 当前文件组 | 目标 owner | 不变量 |
| --- | --- | --- |
| `core/browser/*` | Browser domain/presentation | 不创建第二 session |
| `core/tools/defaultTool/websession/browser/*` | Browser runtime/data 或 Operit adapter | 一个 WebView、一个 registry、一个 download owner |
| `core/tools/defaultTool/websession/userscript/*` | Browser userscript runtime/data | 一个 repository、一个 execution world owner |
| `ui/features/websession/browser/*` | Browser UI | 只投影 runtime，不建立事实状态 |
| Browser tests | 与实现同步移动 | 测试包名必须反映新 owner |

### Player

| 当前文件组 | 目标 owner | 不变量 |
| --- | --- | --- |
| `core/player/PlayerSession*` | Kiyori player domain | 单一 session 和 request |
| `core/player/runtime/*` | Kiyori runtime + stable IPC adapter | AIDL/native 名称先不变 |
| `ui/features/player/*` | Kiyori player UI | floating/fullscreen 只转移 Surface |
| `app/src/main/aidl/*player/runtime*` | `KEEP-CONTRACT` | package、parcelable 和 callback 顺序不变 |
| `PlayerRuntimeService` | `KEEP-CONTRACT` | `:player` 进程与 manifest 入口稳定 |

## 文件级审查规则

一个文件同时满足以下任一条件时必须进入 `SPLIT`：

- 同时 import Kiyori Shell 与 Operit AI route
- 同时读写两个领域的持久化状态
- 同时实现 UI、runtime 和 external Intent
- 同时被上游近期修改且由 Kiyori 变更过
- 同时拥有用户可见名称和兼容协议常量

`SPLIT` 完成前不进行目录重命名。拆分提交必须先建立行为等价测试，再移动各自实现。

## 完成判断

本矩阵只有在以下条件满足后才能从“准备”进入“执行中”：

- 所有 `REVIEW` 文件已经逐文件登记
- 所有 `KEEP-CONTRACT` 类型都有稳定入口说明
- 每个 `MOVE-KIYORI` 组都有 capability owner 和测试组
- 每个 `ADAPTER` 只有一个委派目标
- CI 能根据矩阵检查新文件是否越过边界
