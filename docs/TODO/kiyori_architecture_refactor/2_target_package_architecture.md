---
status: accepted_design
plan_version: 3
last_reviewed: 2026-07-31
---

# 目标包结构与依赖规则

## 目标包树

以下是逻辑目标，不要求一次性创建所有空目录。目录只在有真实 owner 和文件迁入时建立。

```text
app/src/main/java/
├── com/kiyori/
│   ├── app/
│   │   ├── KiyoriApplication.kt
│   │   ├── KiyoriApp.kt
│   │   ├── MainActivity.kt
│   │   ├── navigation/
│   │   ├── shell/
│   │   └── startup/
│   ├── capability/
│   │   ├── browser/
│   │   ├── player/
│   │   ├── files/
│   │   ├── downloads/
│   │   └── miniapp/
│   ├── design/
│   │   ├── component/
│   │   ├── icon/
│   │   └── theme/
│   ├── feature/
│   │   ├── home/
│   │   ├── browser/
│   │   │   ├── domain/
│   │   │   ├── data/
│   │   │   ├── runtime/
│   │   │   ├── presentation/
│   │   │   └── ui/
│   │   ├── player/
│   │   │   ├── domain/
│   │   │   ├── runtime/
│   │   │   ├── presentation/
│   │   │   └── ui/
│   │   ├── files/
│   │   ├── miniapp/
│   │   ├── settings/
│   │   ├── backup/
│   │   └── recovery/
│   ├── integration/
│   │   ├── operit/
│   │   │   ├── ai/
│   │   │   ├── browser/
│   │   │   ├── navigation/
│   │   │   └── player/
│   │   ├── shower/
│   │   ├── shizuku/
│   │   └── tasker/
│   └── platform/
│       ├── android/
│       ├── lifecycle/
│       ├── logging/
│       ├── permissions/
│       └── storage/
└── com/ai/assistance/operit/
    ├── api/
    ├── core/
    ├── data/
    ├── integrations/
    ├── plugins/
    ├── services/
    ├── ui/
    ├── util/
    ├── widget/
    └── provider/
```

## 各层职责

### `app`

只负责进程入口、根组合、顶层导航、启动阶段编排和实现装配。

允许依赖 feature、capability、design、platform 与 integration。禁止在此实现浏览器下载、
播放器状态、AI 工具逻辑或数据库操作。

`KiyoriApplication` 最终只保留 Android Application 必需职责与启动协调器，不直接成为所有子系统的
全局服务定位器。第一次只在旧包内完成类名和文件名迁移；随后把 context、JSON 和主进程初始化访问
收口到稳定 platform 合同，确认 Operit AI 不再直接依赖 app 层后，才把 Application 移入本目录。

### `capability`

定义人工 UI 与 AI 调用共同使用的稳定合同：

- 可观察状态
- 稳定资源 ID
- 命令、结果与错误
- 权限与确认要求
- 生命周期和取消语义

capability 不依赖 Compose、Activity、ViewModel、具体数据库、WebView 或 mpv。

### `feature`

每个产品域拥有自己的模型、数据、runtime、presentation 与 UI。子目录按实际复杂度创建，
简单功能不强制五层结构。

跨 feature 协作必须通过 capability 或 app 编排，不直接访问另一 feature 的 ViewModel、
Composable、数据库实体或内部 manager。

### `design`

保存 Kiyori 产品设计系统、共享组件、图标语义和主题。它不拥有业务状态，也不读取偏好。

AI 对话的背景、气泡、头像、局部字体等个性化仍由 Operit AI 页面负责；Kiyori 设计系统只提供
宿主色彩与通用组件合同。

### `platform`

保存跨 feature 的 Android 平台能力，例如日志、生命周期、权限和存储路径。
这里不放无法明确归属的通用工具。每个类名必须说明具体职责。

`OperitPaths` 未来迁入这里并重命名时，所有目录字符串保持原值。

### `integration`

连接外部系统和 Operit AI。`integration.operit` 可以同时看到 Kiyori capability 与 Operit AI，
但不得拥有浏览器、播放器、下载或设置的第二份状态。

Operit AI 原始代码不应依赖 Kiyori UI。需要调用产品能力时，由 app 注册的 integration adapter
把调用转为 capability 命令。

### `com.ai.assistance.operit`

保留以下内容：

- 上游 Operit AI runtime
- AI 对话、模型、记忆、工作流和工具
- ToolPkg、Skill、MCP、市场和 Java bridge
- 序列化 FQCN 与备份模型
- AIDL、JNI 和 native wrapper
- Android 稳定组件入口
- 仍需保持上游文件可识别的适配热点

旧包根不是垃圾区。每个保留文件必须有 Operit AI、兼容或上游同步理由。

## 依赖方向

目标方向：

```text
app
├── feature
├── integration
├── capability
├── design
└── platform

feature
├── capability
├── design
└── platform

integration.operit
├── capability
├── platform
└── com.ai.assistance.operit

com.ai.assistance.operit
├── 可依赖稳定的 com.kiyori.capability 与 com.kiyori.platform 合同
└── 不依赖 com.kiyori.app 或 com.kiyori.feature.*.ui
```

禁止方向：

- capability -> feature
- platform -> feature
- design -> feature
- Operit AI -> Kiyori Composable、Activity 或 ViewModel
- 一个 feature -> 另一个 feature 的内部实现
- 任何 UI -> 另一个领域的数据库实体作为写入入口

## Browser 目标映射

当前 Browser Runtime 被分散在：

- `core/browser/`
- `core/tools/defaultTool/websession/browser/`
- `core/tools/defaultTool/websession/userscript/`
- `ui/features/browser/appshell/`
- `ui/features/websession/browser/`

目标为：

```text
com/kiyori/
├── capability/browser/
├── feature/browser/
│   ├── domain/
│   ├── data/
│   ├── runtime/
│   ├── presentation/
│   └── ui/
└── integration/operit/browser/
```

迁移后仍只有一个 WebSession registry、一个活动 WebView、一个下载 owner、一个历史/书签 owner、
一个 userscript repository 和一个 presentation coordinator。

旧 AI 工具路径只保留与上游或 ToolPkg 兼容所需的入口，并把命令交给同一 Browser capability。

## Player 目标映射

Kiyori player 的 domain、UI 和 presentation 迁入 `feature/player`。现有 AIDL 与 `:player`
service 包名先保持不变，通过 `integration.operit.player` 或稳定 runtime adapter 连接。

在播放器设备验收完成前，不迁移 AIDL 包名、不调整 native 库名、不改变 Surface lease、
媒体 request 或设置文件名。

## App Shell 目标映射

当前 `ui/main/` 不能整体移动：

- `Kiyori*` shell、首页、设置宿主和产品导航迁入 `com.kiyori.app`
- `OperitScreens`、AI 动态路由和 ToolPkg screen registry 继续属于 Operit AI
- 两者之间的 route 转换迁入 `integration.operit.navigation`
- `MainActivity` 的 Android 外部入口与内部内容宿主分开审计

## 命名规范

- 包名使用小写单数职责名：`feature`、`capability`、`integration`、`platform`
- 文件名与主要公开类型一致
- 避免新增无范围的 `Common`、`Utils`、`Manager`、`Helper` 和 `Impl`
- 状态 owner 使用领域名，例如 `BrowserSessionRegistry`、`PlayerSession`
- 纯 UI 投影使用 `UiState`，持久化模型和协议模型不得复用 UI 类型
- adapter 名称必须指出两端，例如 `OperitBrowserCapabilityAdapter`
- 兼容入口名称保留原 FQCN，并在注释中说明稳定原因和唯一委派目标

## 测试目录

测试包镜像生产包：

```text
app/src/test/java/
├── com/kiyori/
│   ├── app/
│   ├── capability/
│   ├── feature/
│   └── integration/
└── com/ai/assistance/operit/
```

纯移动时同步移动对应测试。测试文件不继续留在旧目录导入新实现，以免掩盖所有权错误。

## Gradle 模块策略

首轮只建立包边界和自动依赖门禁，不新增 feature module。

只有满足以下条件后才评估模块化：

1. `core -> ui`、`data -> ui` 等依赖环已经归零。
2. capability 不依赖 Android UI 与具体实现。
3. Browser、Player 等 feature 可以仅通过公开合同被 app 装配。
4. 资源、Manifest、native 制品和依赖已有唯一 owner。
5. 完整构建与设备验证稳定至少两个里程碑。

Gradle `namespace` 也不是包重构完成条件。它保持 `com.ai.assistance.operit`，直到单独设计
R/BuildConfig、Manifest 相对类名和上游 import 的迁移成本。
