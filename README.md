<div align="center">
  <img src="app/src/main/assets/logo.svg" width="128" height="128" alt="Kiyori 标志">
  <h1>Kiyori</h1>
  <p><strong>面向 Android 的 AGI 协作系统</strong></p>
  <p>让人与 AI 在同一个工作环境中理解信息、使用工具、完成任务。</p>
  <p>AI 对话 · 终端 · 浏览器 · 播放器 · 文件管理 · 可扩展工作流</p>
  <p>
    <a href="#开始使用">开始使用</a> ·
    <a href="#核心能力">核心能力</a> ·
    <a href="#从源码构建">源码构建</a> ·
    <a href="docs/README.md">文档中心</a> ·
    <a href="https://github.com/Kiyori-CN/Kiyori/issues">问题反馈</a> ·
    <a href="README.en.md">English</a>
  </p>
  <p>
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0 及以上">
    <img src="https://img.shields.io/badge/ABI-arm64--v8a-1E88E5" alt="ARM64 架构">
    <img src="https://img.shields.io/badge/status-in_development-D97706" alt="开发中，尚未公开发行">
    <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0--or--later-555555" alt="GPL v3 或更高版本"></a>
  </p>
</div>

**Kiyori 致力于打造全球最强的 Android AGI 系统，让 AI 对话、终端、浏览器、播放器、文件管理器与扩展工具逐步形成全面的人机协作环境。**

我们希望你能够在手机上提出目标，与 AI 共同研究和制定方案，使用真实网页、终端和文件完成工作，再检查过程与结果。可以把这种目标体验理解为“手机上的 Codex”：围绕任务协作，把讨论、操作和产物连接起来，并针对 Android 的触控、权限、存储与生命周期重新设计。

这里的 **AGI 是长期研发方向，“全球最强”是项目追求的目标**。当前 Kiyori 是基于 Operit 演进的独立 Android 应用，已经具备 AI、浏览器、终端、文件与媒体等能力底座，正在持续完善跨模块协作与设备体验。

> [!IMPORTANT]
> **项目尚未公开发行。** 当前通过本仓库构建开发版，没有公开的官方 APK 下载渠道。功能实现、自动检查、真机体验和正式发行分别验收；各专项的实际状态见 [开发任务索引](docs/TODO/README.md)。

## 目录

- [项目愿景](#项目愿景)
- [人与 AI 如何协作](#人与-ai-如何协作)
- [能力总览](#能力总览)
- [核心能力](#核心能力)
- [当前阶段](#当前阶段)
- [开始使用](#开始使用)
- [模型与扩展配置](#模型与扩展配置)
- [从源码构建](#从源码构建)
- [架构与仓库导览](#架构与仓库导览)
- [数据、隐私与权限](#数据隐私与权限)
- [常见问题](#常见问题)
- [文档与贡献](#文档与贡献)
- [上游、致谢与许可证](#上游致谢与许可证)

## 项目愿景

手机已经承载了阅读、沟通、创作与文件处理的大量工作。Kiyori 希望进一步把这些工作连接成可协作的任务：人负责目标、判断与重要决策，AI 参与理解、推理和执行，应用提供可观察、可操作的真实环境。

项目围绕五个方向持续演进：

1. **从对话走向任务。** 把模型回答与网页操作、代码执行、文件处理、媒体工具连接起来，让结果能够落到实际产物上。
2. **让人与 AI 使用同一环境。** 以共享网页会话、可见终端、明确工作目录和统一工具入口减少上下文割裂，支持人参与检查和继续操作。
3. **保留过程与控制权。** 让工具调用、输出、异常和执行终态可查看，按操作风险处理授权，让重要动作有明确边界。
4. **开放模型与能力接入。** 支持云模型、自部署服务、端侧推理，以及脚本、ToolPkg、Skill、MCP 和工作流，允许不同任务选择合适的能力。
5. **面向真实 Android 使用。** 把触控体验、系统权限、应用生命周期、网络条件、文件访问与资源消耗作为产品设计的一部分。

现阶段的工程基础由 Kiyori 产品壳、共享浏览器及内置 Operit AI 运行时组成。各领域逐步连接到统一的任务体验，已经实现的边界见下文，进一步的演进以专项计划为准。

## 人与 AI 如何协作

一次典型协作可以分为五步：

1. **明确目标。** 描述问题、完成标准和允许操作的范围；通过对话、角色与提示词、附件和工作区补充上下文。
2. **理解现场。** 打开网页、选择文件、补充背景；借助网页快照、文件读取、搜索和记忆检索建立任务依据。
3. **共同执行。** 审阅关键操作并处理授权；AI 使用浏览器工具、可见终端、代码运行器、扩展或工作流，你可以按需手动参与。
4. **检查结果。** 在浏览器、终端、文件管理和播放器中核对页面变化、命令输出及生成文件，在对话详情中查看执行记录。
5. **继续迭代。** 修订要求、调整工具，把有用的上下文和产物保留在对话历史、工作区、记忆、下载或导出文件中。

**目前最直接的协作基础是共享状态。** 人工浏览与 AI 浏览器工具使用同一 Browser Runtime 和真实 WebView 会话；代码运行器的终端执行路径使用可见 PTY 会话；文件管理与 AI 文件工具复用现有文件能力。浏览器发现的媒体可以交给统一播放器或下载管理器处理。

### 可以从这些任务开始

以下是任务组织示例，需要先配置相应模型、工具和权限。执行效果取决于模型能力、网站限制、文件格式与设备环境。

**资料研究** · 浏览器、搜索、AI 对话、文件工具

> 比较这几个页面的技术方案，保留来源，把结论整理到指定工作目录。

**移动开发** · 工作区、文件工具、终端、代码运行器

> 阅读这个项目，解释入口，修改指定文件，并在终端运行项目已有检查。

**数据整理** · 文件管理、AI 工具、操作授权

> 先列出这些文件的整理方案，确认后执行重命名，并输出变更清单。

**媒体处理** · 文件管理、FFmpeg 工具、播放器

> 检查这个视频的信息，使用已配置的媒体工具处理，再打开结果核对。

**重复工作** · 工作流、脚本、ToolPkg、对话与日志

> 把已经验证过的几步处理整理成工作流，保留运行结果。

Android 存储与 Ubuntu 文件系统有各自的路径和权限；跨环境使用文件时，需要通过实际支持的复制或挂载路径完成衔接。

## 能力总览

- **[AI 对话](#ai-对话模型与记忆)**：多供应商与协议、流式对话、思考模式、角色卡、附件、语音与工具调用。入口为 AI 首页和 AI 助手设置。
- **[浏览器](#浏览器与网页协作)**：多窗口、书签、历史、搜索、会话恢复、网站设置、广告拦截和网页工具。入口为浏览器与负一屏。
- **[终端与代码](#终端代码运行与远程开发)**：Ubuntu/PRoot、可见会话、多语言运行和 SSH。通过工具箱、代码运行器与相关扩展使用。
- **[文件与工作区](#文件管理与项目工作区)**：目录、标签与窗格、搜索、授权文件工具、工作区绑定和项目规则。入口为文件管理与对话工作区。
- **[媒体与下载](#播放器下载与媒体处理)**：资源发现、下载、mpv、字幕、队列、Anime4K、悬浮播放和 FFmpeg 工具。由浏览器、下载与播放器入口衔接。
- **[记忆与自动化](#扩展工具与工作流)**：记忆空间、文档与图谱、检索、可视化工作流和后台调度。入口位于 AI 菜单的记忆与工作流。
- **[扩展生态](#扩展工具与工作流)**：JavaScript/TypeScript、ToolPkg、Skill、MCP、市场、环境变量和工具授权。通过扩展与工具箱管理。
- **[设备与连接](#android-能力与跨设备连接)**：集中权限、Android 自动化、Shizuku/Root、应用代理、HTTP 和远程扩展。通过设置和已启用的工具使用。

## 核心能力

### AI 对话、模型与记忆

AI 是任务的讨论与协调入口。你可以为不同用途配置模型、角色和工具，在对话中加入附件或绑定工作区，并查看实际执行过程。

- **模型选择**：接入 OpenAI、Anthropic、Google、DeepSeek、通义千问等供应商，以及兼容服务和自定义端点；具体组合由供应商与协议配置共同决定。
- **交互与表达**：流式输出、思考模式、Markdown、代码与公式显示；附件理解和语音能力取决于所选模型及服务配置。
- **角色与上下文**：角色卡、用户偏好、提示词、历史管理、上下文压缩和工作区规则，为任务提供持续背景。
- **记忆管理**：记忆空间、文档导入、搜索和关联图谱，用于组织可复用的信息；检索效果受内容和配置影响。
- **过程查看**：“对话详情”提供请求、工具、异常、修订和终态的本地记录，并支持诊断导出。
- **失败与恢复**：请求失败和提交状态未知会保留可见结果；支持续接的协议按其具体执行契约恢复，不把所有服务都描述为可无条件断点续传。

模型推理质量、工具选择和执行成功率依赖实际模型与环境。Kiyori 提供运行与协作基础，不保证模型的每项判断正确。

### 浏览器与网页协作

在 AI 左抽屉“扩展 → 脚本”启用“浏览器扩展开发”后，可以要求 AI 为网页编写并安装 Kiyori
`.kbx` 扩展或油猴脚本，并继续启停、修改、测试和删除。自定义扩展显示在浏览器扩展中心的
独立卡片中；AI 与人工管理共享同一份状态。安装与更新先保持关闭，由 AI 在授权范围内显式启用、
刷新指定页面，再用网页快照和断言检查效果。当前支持页面 JS/CSS 与扩展操作按钮，不支持 Chrome
全量 API、后台 worker、popup 或无痕扩展执行。格式与使用步骤见
[AI 浏览器扩展开发](docs/doc-src/architecture/browser_extension_development.md)，设备验证状态见专项计划。

浏览器既是日常网页入口，也是 AI 可以使用的真实操作环境。

- **日常浏览**：多窗口、书签、历史、搜索引擎切换、自定义或空白主页，以及按设置恢复普通会话。
- **页面体验**：网站独立设置、文字缩放、强制缩放、桌面/移动访问相关设置、链接和媒体长按操作、全屏图片查看。
- **内容管理**：广告订阅、自定义 URL 与元素规则、“标记广告”工作台、用户脚本和浏览器插件中心。
- **网页工具**：导航、快照、截图、点击、输入、表单填写、选择选项、文件上传、窗口操作，以及页面源码、控制台和网络资源观察。
- **会话连续性**：人工页面和 AI 工具共用 WebSession；操作与观察围绕同一实际页面进行。
- **网站身份**：普通/无痕 Profile 边界、网站密码管理，以及手动读取当前页面 Cookie 的内置插件。

浏览器工具支持的是项目实现的 API 子集，不能视为完整桌面 Playwright。广告规则与用户脚本也有明确兼容范围；Chrome 扩展并非可以直接安装的通用插件格式。

详细行为见 [浏览器契约](docs/doc-src/contracts/browser.md) 与 [浏览器扩展平台](docs/doc-src/architecture/browser_plugin_platform.md)。

### 终端、代码运行与远程开发

Kiyori 通过 [KiyoriTerminalCore](https://github.com/Kiyori-CN/KiyoriTerminalCore) 集成 Ubuntu 终端，为命令执行与移动开发提供基础。

- **真实终端会话**：Ubuntu/PRoot 环境、会话管理、可见命令与输出，以及运行状态和退出码。
- **多语言执行**：代码运行器提供 JavaScript、Python、Ruby、Go、Rust、C 和 C++ 的代码字符串与文件入口；JavaScript 区分 ES5 与 Node.js 路径。
- **环境准备**：按工具页面准备运行时与编译器，检查实际环境后再执行；Python 与 Node 包使用各自的持久工作环境。
- **人机协作**：AI 的终端执行可落到可见会话，用户可以检查命令、输出与工作目录，继续处理项目。
- **远程连接**：通过 SSH、远程 Kiyori、Windows 配套工具等扩展连接已配置的目标环境。

Ubuntu 内的执行身份不等于 Android 系统 Root。`super_admin:terminal` 面向 Ubuntu/PRoot，`super_admin:shell` 面向 Android Shell/Root，后者依赖真实的设备授权。

环境与执行边界见 [平台、存储与终端](docs/doc-src/contracts/platform_storage.md) 和 [代码运行专项](docs/TODO/code_runner_terminal_toolchain/index.md)。

### 文件管理与项目工作区

文件管理用于查看和整理实际数据，工作区则把目录与 AI 对话联系起来。

- 文件管理页面支持目录浏览、标签与窗格、排序、隐藏文件、搜索，以及新建文件和目录等已接入操作。
- 底层文件工具提供读写、复制、移动、删除、归档等能力，可由已授权的 AI 工具调用；当前长按菜单的部分按钮仍待接入业务动作，不能仅凭菜单文字判断功能已就绪。
- 使用 Android 授权目录与现有文件访问能力；可访问范围取决于系统版本、授权和所选环境。
- 将对话绑定到工作目录，读取项目文件、观察变更，并把相关内容组织为上下文。
- 按工作区约定读取根目录的 `AGENT.md` / `AGENTS.md`，保留现有 `.operit/config.json` 工作区格式。
- 通过文件管理、下载和工作区入口检查产物，避免只依赖模型对“已完成”的文字描述。

批量处理前应明确目标目录、文件范围和覆盖意图。详细契约见 [扩展与工作区](docs/doc-src/contracts/extensions_workspace.md)。

### 播放器、下载与媒体处理

媒体能力覆盖发现、保存、播放和工具处理几个阶段。

- **资源发现**：浏览器按当前页面整理视频、音频、图片、脚本等资源，并根据实际页面与请求证据形成媒体候选。
- **下载管理**：内部下载引擎与 Android 系统下载入口，支持任务状态、筛选、排序、并发配置及批量操作；删除记录与删除文件有不同语义。
- **mpv 播放器**：本地与网络媒体、队列、字幕、速度、手势、旋转、悬浮/全屏切换，以及 Anime4K 着色器设置。
- **缓存策略**：省流、智能均衡、流畅优先和完整缓存四种模式；“完整缓存”只适用于符合条件的媒体，属于会话缓存。
- **媒体工具**：通过 FFmpeg 扩展执行已支持的信息读取、转码及媒体处理，再使用播放器或文件管理检查结果。

**在线播放缓存不等于离线下载。** 媒体能否播放或处理，还取决于源站访问、编码、设备解码能力与所选配置；网页资源发现不保证任意站点内容均可获取。使用媒体工具时应尊重来源网站规则与内容权利。

配置说明见 [网络与媒体指南](docs/user-guide/network_and_media.md)，技术边界见 [媒体与下载](docs/doc-src/contracts/media_downloads.md)。

### 扩展、工具与工作流

扩展系统让能力可以按任务组合，而不必把所有功能都固化到 Android 主应用中。

| 类型 | 用途与资料 |
| --- | --- |
| **脚本** | 通过 JavaScript 与宿主 API 提供工具；TypeScript 可作为源码开发语言。见 [脚本开发指南](docs/SCRIPT_DEV_GUIDE.md)。 |
| **ToolPkg** | 用结构化包组织工具、资源、配置及宿主支持的界面扩展。见 [ToolPkg 格式](docs/TOOLPKG_FORMAT_GUIDE.md)。 |
| **Skill** | 导入和管理可复用的技能说明与资源。见 [扩展使用指南](docs/user-guide/extensions.md)。 |
| **MCP** | 连接已配置的 MCP 服务并使用其工具。见 [扩展运行时契约](docs/doc-src/contracts/extensions_workspace.md)。 |
| **工作流** | 用节点组织触发与处理步骤，支持手动执行和已有调度入口。见 [工作流源码](app/src/main/java/com/ai/assistance/operit/core/workflow)。 |

预置工具覆盖搜索、学术检索、代码、文件、媒体、系统与远程连接等领域。可参考 [生产预置清单](tools/example_packages/packages_whitelist.txt) 与 [扩展示例](examples/README.md)；预置不意味着全部默认启用，也不意味着所有第三方服务都已经配置。

例如，OpenAI 搜索、Brave 搜索和学术工具使用各自的配置；Bilibili 工具包提供只读检索、内容导出和媒体处理。需要环境变量的包应先完成配置，再明确启用。

工作流调度使用 Android 后台执行机制，受系统省电、后台限制和设备状态影响，不应依赖它提供精确实时保证。

### Android 能力与跨设备连接

- 通过集中权限管理查看文件、通知、麦克风、悬浮窗、无障碍等功能所需的系统授权。
- 通过已启用的自动化与系统工具使用设备能力；Shizuku、Root 和虚拟显示相关能力有各自的环境要求。
- 通过应用级网络代理统一配置 Kiyori 的 AI、网页、下载、播放器与工具请求，支持规则、全局、直连模式。
- 通过 WebChat、外部 HTTP 对话及远程扩展连接其他操作界面或设备，按相应配置管理访问令牌与网络可达性。

这些入口都依赖明确的配置与权限。应用级代理只改变 Kiyori 的请求路径；它不是系统 VPN，也不会替其他应用建立代理。

## 当前阶段

以下状态用于帮助选择使用方式，具体实现与验收证据以 [专项计划](docs/TODO/README.md) 为准。

| 范围 | 当前说明 |
| --- | --- |
| **已有实现基础** | AI 对话与工具、共享浏览器、终端、文件管理、播放器、下载、记忆、扩展和工作流 |
| **需要自行准备** | 模型服务与 Key、端侧模型、终端工具链、扩展环境变量、远程服务和对应权限 |
| **持续建设** | 更完整的跨模块协作、文件管理长按菜单的业务接入、小程序独立入口、独立音乐与文档阅读体验等 |
| **分别验收** | 真机触控、OEM 行为、长期运行、网络服务、模型兼容性与性能 |
| **尚未提供** | 公开正式发行渠道、面向所有机型或所有服务的完成保证 |

小程序入口的存在不表示完整小程序平台已经交付。README 中的协作示例与愿景也不替代端到端验收；标记为 `verification_pending` 的专项仍保留其设备或外部环境验证要求。

## 开始使用

### 1. 确认设备与安装方式

| 项目 | 要求或说明 |
| --- | --- |
| Android | Android 8.0 / API 26 及以上 |
| CPU 架构 | 当前 APK 仅包含 `arm64-v8a` |
| 应用标识 | `com.kiyori` |
| 版本快照 | `0.1.0`，`versionCode 45`，核对于 2026-09-06；以 [构建配置](app/build.gradle.kts) 与实际 APK 为准 |
| 存储 | 为 APK、终端环境、模型、工作区与下载分别预留空间；需求随使用方式变化 |
| 获取方式 | 按下文从源码构建 Debug APK |

当前 APK 不适用于仅支持 32 位 ARM 或 x86/x86_64 的设备与模拟器。旧 Operit 与 Kiyori 使用不同 application ID，可以作为独立应用安装，数据迁移需显式导入。

### 2. 完成首次设置

1. 安装开发 APK，阅读产品介绍、用户协议与隐私政策。
2. 按实际需要选择权限；可以暂不授权进入应用，以后在 **设置 → 更多功能 → 权限管理** 中处理。
3. 从底栏进入浏览器；需要时在 **设置 → 网页浏览器 → 网页主页** 选择自定义网址或纯空白页。
4. 进入 AI 首页，配置供应商、协议、模型、端点和 API Key，发送一条简短消息验证配置。
5. 使用文件或项目任务时，选择目标工作目录；需要代码执行时，先完成终端环境与对应工具链准备。
6. 在 AI 菜单的“扩展”中配置并启用需要的包，再尝试一个范围明确的工具任务。

需要重看介绍时，打开 **设置 → 更多功能**，在页面最底部的独立 **使用引导** 分组选择 **重新查看首次引导**，每次都从第一页开始。
上下滑动阅读、左右滑动逐页浏览；“跳过介绍”直接前往第五页协议。重看不会重置已有数据或首启完成状态。

浏览网页不要求先购买或配置模型服务；AI 首页也允许在没有 Key 时进入。云服务、语音、搜索与远程工具可能分别计费，具体规则由所选服务商决定。

### 3. 熟悉导航

```text
软件首页区域
├── 左侧：负一屏，书签、历史与下载
├── 中间：软件首页
└── 右侧：AI 首页，对话、模型与工具

底部导航
├── 软件首页
├── 浏览器
├── 小程序
├── 文件管理
└── 设置

AI 页面菜单
├── 扩展 / 工具箱 / 工作流
├── 对话与记忆
└── AI 助手相关设置与入口
```

AI 首页和 AI 顶层页面显示菜单，深层页面逐级返回。从浏览器或 AI 打开的设置会话保留来源，退出后回到原网页或 AI 页面。更多操作见 [首次使用指南](docs/user-guide/getting_started.md)。

## 模型与扩展配置

### 选择模型运行方式

| 运行方式 | 场景与准备 |
| --- | --- |
| **云服务商** | 使用托管模型进行对话和工具任务。准备服务商账户、API Key、模型名称、端点与正确协议。 |
| **兼容 API** | 接入已有网关或自定义端点。确认服务实际支持的协议、认证与模型能力。 |
| **自部署服务** | 连接 Ollama、LM Studio 或其他本地服务。准备手机可达的地址、模型与所需认证。 |
| **设备内推理** | 使用 MNN 或 llama.cpp 运行兼容模型。准备匹配格式的模型文件和足够设备资源。 |

**供应商与协议是两个独立选择。** 项目支持 OpenAI Chat Completions、OpenAI Responses、Anthropic Messages，以及 Google 和端侧引擎等原生路径；不是每个供应商都支持全部协议。使用兼容地址也不表示它具备官方服务的所有扩展能力。

在手机上填写 `localhost` 或 `127.0.0.1`，指向的是手机本身。连接电脑上的模型服务时，需要使用手机可达的地址，并核对服务监听范围、认证和网络设置。

设备内推理不意味着整个应用离线运行：网页、联网搜索、市场、远程工具和部分语音能力仍有各自的网络需求。

### 分别配置工具与服务

- **主模型**：在模型配置中保存供应商、协议、地址和认证；按照实际模型能力设置工具与思考选项。
- **语音**：识别和合成使用各自的服务配置，不能从主模型是否可用推断语音已就绪。
- **搜索**：OpenAI 搜索等独立工具读取自己的包配置，不自动继承聊天模型的端点与 Key。
- **扩展**：在“扩展”中查看包声明的环境变量、权限和启动要求，再启用相应脚本、ToolPkg 或 MCP。
- **网络**：在 **设置 → 更多功能 → 网络代理** 管理应用路由；“直连”表示绕过应用内代理，设备上的系统 VPN 仍可能影响流量。

配置后先运行一个小范围查询或操作，查看真实输出。遇到认证、限流、协议或网络错误时，以实际错误定位问题；重复提交可能再次计费或执行有副作用的动作。

详细说明见 [扩展指南](docs/user-guide/extensions.md)、[网络指南](docs/user-guide/network_and_media.md) 与 [AI 请求契约](docs/doc-src/contracts/ai_execution.md)。

## 从源码构建

此处给出源码到 APK 的主要路径。完整依赖准备、平台差异、native 输入和排障步骤以 [构建指南](docs/doc-src/dev-core/BUILDING.md) 为准。

### 1. 准备工具链

以下基线核对于 2026-09-06；后续以仓库配置和 CI 为准。

| 工具 | 基线 |
| --- | --- |
| JDK | 21；应用 Java/Kotlin 字节码目标为 JVM 17 |
| Gradle | 使用仓库提供的 Wrapper |
| Android SDK | Platform 37；应用 target SDK 34 |
| Android Build Tools | 36.0.0 |
| Android NDK | 28.2.13676358 |
| CMake | 3.22.1 |
| Rust | 1.88.0，target `aarch64-linux-android` |
| Node.js | 22，使用 npm 和已提交的 lockfile |
| Python | Python 3，仓库检查使用项目 `.venv` |

版本来源：[Gradle 版本目录](gradle/libs.versions.toml)、[Wrapper](gradle/wrapper/gradle-wrapper.properties)、[Gradle 属性](gradle.properties) 与 [CI 工作流](.github/workflows)。

### 2. 获取源码与终端子模块

```bash
git clone https://github.com/Kiyori-CN/Kiyori.git
cd Kiyori
git switch main
git submodule sync -- terminal
git submodule update --init --recursive terminal
```

**只初始化常规构建需要的 `terminal`。** 不要对整仓使用 `git clone --recurse-submodules`；可选私有夜间构建子模块不属于普通 Debug 构建。

### 3. 准备构建输入

克隆源码后，还需要按 [完整构建指南](docs/doc-src/dev-core/BUILDING.md) 完成以下准备：

1. 安装固定 Android SDK、NDK、CMake 和 Rust target，配置 Java 与 Android 环境。
2. 安装根工具、WebChat 与所需示例工程的 npm 依赖，建立项目 `.venv`。
3. 参考 `local.properties.example` 创建未跟踪的本地配置，不覆盖已经存在的私人配置。
4. 获取并通过受控脚本准备 `libs.zip`、`models.zip`、`subpack.zip` 和 `jniLibs.zip`，验证所选播放器等 native 输入。
5. 生成 WebChat 和指南要求的示例输入；目录型生产 ToolPkg 由 Gradle 构建任务生成。

**仅克隆源码不足以完成构建。** 大型 AAR、模型、JNI 和子包输入不全部存放在 Git 中；不要将这些受保护输入当作普通缓存删除，也不要通过跳过输入校验处理缺失依赖。

### 4. 生成 Debug APK

Linux / macOS：

```bash
./gradlew :app:assembleDebug --no-daemon --console=plain
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

标准产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 5. 安装与核验

将 APK 传到兼容设备安装，或在已经连接并授权 ADB 的开发设备上执行：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

不同签名的同包名 APK 不能直接覆盖安装；处理签名冲突前先备份数据。构建后应核对 APK 的应用标识、版本、ABI、签名与打包结果，再进行启动和实际操作测试。详细命令见 [APK 核验](docs/doc-src/dev-core/BUILDING.md#9-独立核验-apk)。

文档或代码贡献的验证入口见 [贡献指南](docs/doc-src/dev-core/CONTRIBUTING.md) 与 [CI 指南](ci/README.md)。构建成功证明本机构建链通过，不替代真机体验或正式发行验收。

## 架构与仓库导览

Kiyori 采用 Kotlin、Jetpack Compose 与 Android 原生能力构建产品界面，集成 Operit AI 运行时，并通过独立 Android/native 模块提供终端、推理、脚本和渲染能力。WebChat 使用 React、TypeScript 与 Vite。

### 关键设计

- **产品壳统一导航**：首页、浏览器、AI、文件管理与设置在同一个应用壳中组织，保留各自的状态与返回来源。
- **共享真实运行时**：人工与 AI 浏览器工具复用同一浏览器；播放器、下载、权限与存储各自有明确的状态所有者。
- **工具通过能力调用**：AI、扩展与界面通过已有能力和适配层协作，避免生成另一套页面、下载或文件状态。
- **原生任务隔离**：播放器与 FFmpeg 工具有各自的非导出进程，终端通过子模块服务和 AIDL 连接。
- **兼容边界明确**：保留仍有实际消费者的协议、持久化格式、AIDL/JNI 和上游生态标识。

### 主要目录

```text
Kiyori/
├── app/              Android 主应用、产品壳、AI、浏览器、文件与媒体集成
├── terminal/         KiyoriTerminalCore 子模块，Ubuntu 终端与会话能力
├── llama/            llama.cpp 端侧推理模块
├── mnn/              MNN 推理及语音相关 native 能力
├── quickjs/          JavaScript 引擎与宿主桥
├── dragonbones/      DragonBones 动画模块
├── mmd/              MMD 模型、物理与渲染
├── fbx/              FBX 解析与渲染集成
├── showerclient/     虚拟显示、输入与截图客户端
├── web-chat/         WebChat 前端
├── examples/         脚本、ToolPkg 与配套工具示例
├── buildSrc/         Gradle 自定义任务与行为测试
├── tools/            开发、构建、环境与诊断工具
├── ci/               可复现检查、依赖准备与测试
├── config/           机器可读的架构与工程约束
└── docs/             用户指南、正式设计、专项计划与许可证资料
```

根 settings 声明九个 Android 模块；`buildSrc` 属于构建工程，不进入 APK。`com.kiyori` 承载产品、平台与集成边界，`com.ai.assistance.operit` 中仍保留 AI 和部分产品实现，目录名称不能简单等同于功能归属。

完整说明见 [项目上下文](CONTEXT.md)、[仓库架构](docs/doc-src/architecture/repository_architecture.md)、[目录规范](docs/doc-src/dev-core/REPOSITORY_LAYOUT.md) 与 [运行时契约](docs/doc-src/contracts/README.md)。

## 数据、隐私与权限

### 数据流向

| 数据或能力 | 处理边界 |
| --- | --- |
| 云模型请求 | 发往用户配置的服务商或端点，遵循所选网络路由与服务规则 |
| 端侧推理 | 使用配置的设备内引擎与模型；其他联网工具独立判断 |
| 网页、搜索、市场与语音 | 连接对应网站或服务，不因集成在同一应用中变成本地功能 |
| 远程工具与 HTTP 接口 | 按用户启用的目标、访问令牌和网络配置执行 |
| 新公开文件 | 使用 `Download/Kiyori`、`Pictures/Kiyori` 等产品目录 |
| 浏览器恢复 | 按开启的设置保留普通窗口 URL、标题和必要导航信息，不保存无痕恢复记录 |
| 网站密码 | 普通 Profile 使用设备绑定的加密私有存储；无痕不保存或填写网站密码 |
| 对话审计与诊断 | 审计正文在应用私有加密存储中保存；导出文件需按实际格式与内容审阅 |

Kiyori 不提供大语言模型推理中转，也不连接 Operit 的更新、补丁和远程公告服务。用户配置的服务、市场与扩展仍有各自的数据规则和费用。

### 授权与执行

Android 系统权限、工具是否允许调用、具体操作风险是不同层面的控制。按需启用权限，并在批量文件处理、设备特权、账户或外部操作前确认目标与范围。

应用内 AI 动作按只读、低影响、高影响和关键操作区分授权；高风险动作不会因为某个工具被设为允许，就自动失去其操作确认要求。具体边界见 [工具授权契约](docs/doc-src/contracts/extensions_workspace.md#应用内-ai-动作授权)。

### 备份、迁移与诊断

- **迁移旧 Operit**：先从旧应用导出备份，再通过 Kiyori 的显式导入入口恢复；确认聊天、角色、设置与工作区完整前，保留原始备份。
- **公开目录**：不会自动扫描、合并或删除 `Download/Operit`；需要迁移的文件由用户明确选择。
- **私密信息**：API Key、Authorization、Cookie、密码、令牌、私钥和签名材料不应进入公开 Issue 或仓库。
- **诊断导出**：AI 诊断 Markdown 是 UTF-8 明文；即使凭据经过脱敏，也可能包含私人对话、工具输出和业务内容。完整 `.kiyori-audit` 也只应交给可信、明确授权的接收者。

更多说明见 [隐私、数据与迁移](docs/user-guide/privacy_and_data.md)。用户协议与隐私政策正文可在应用 **设置 → 更多功能** 中阅读。

## 常见问题

<details>
<summary><strong>Kiyori 已经实现 AGI，或者可以完全自主操作手机了吗？</strong></summary>

AGI 是长期研发方向。当前交付的是集成模型、工具和 Android 工作环境的开发中应用。可执行范围由已实现的工具、模型能力、系统权限与真实设备条件共同决定，全面人机协作仍在持续建设。

</details>

<details>
<summary><strong>有官方 APK 下载吗？为什么没有 Release 安装说明？</strong></summary>

目前尚未公开发行，请从本仓库构建开发 APK。Release 签名、发行渠道和发布验收是独立工作，不能用 Debug 构建代替。请勿将第三方 APK 视为官方发行。

</details>

<details>
<summary><strong>必须配置 API Key、Root 或 Shizuku 才能使用吗？</strong></summary>

普通浏览不要求模型 Key，普通使用也不要求提前授予全部特权。云模型需要相应服务认证；设备内推理需要模型文件；Shizuku/Root 仅服务依赖这些执行身份的能力。按照实际使用的功能逐项配置。

</details>

<details>
<summary><strong>支持离线、电脑上的模型和所有兼容 API 吗？</strong></summary>

兼容的 MNN 或 llama.cpp 模型可使用设备内推理路径；电脑或服务器上的模型需要手机能访问到对应服务。协议兼容不等于所有功能兼容，应分别验证工具调用、视觉、思考与恢复等能力。联网工具仍需要网络。

</details>

<details>
<summary><strong>AI、浏览器和终端是否使用同一个工作现场？</strong></summary>

人工浏览与 AI 网页工具共享真实网页会话，代码运行器的终端执行路径使用可见会话，文件工具与文件管理复用已有能力。这些连接有明确的会话、路径与权限边界；播放器等其他领域的全部能力并不因此自动成为可调用的 AI 工具。

</details>

<details>
<summary><strong>能直接安装 Chrome 扩展，或使用全部 Playwright API 吗？</strong></summary>

不能作此假设。Kiyori 有自己的用户脚本、浏览器插件和工具接口，支持范围以实现与契约为准。Chrome 扩展、用户脚本、ToolPkg 和 MCP 是不同的扩展形式。

</details>

<details>
<summary><strong>为什么 APK 安装失败，或者不能覆盖旧 Operit？</strong></summary>

先检查 Android 版本、ARM64 支持、剩余空间和 APK 签名。Kiyori 的 application ID 为 `com.kiyori`，与 Operit 是独立应用，不能直接覆盖旧安装；同包名的不同签名 APK 也不能直接覆盖。处理前先备份数据，迁移使用显式导入。

</details>

<details>
<summary><strong>构建通过为什么仍有功能待验证？</strong></summary>

编译与打包无法证明真机触控、系统限制、解码、网络服务或模型行为全部正确。项目分别记录自动检查、构建、设备和外部环境证据；`verification_pending` 表示仍需完成对应现场验收。

</details>

<details>
<summary><strong>反馈问题时应提供哪些信息？</strong></summary>

提供设备型号、Android 版本、Kiyori 版本或源码提交、相关模型与工具、最短复现步骤、预期和实际行为，以及必要的脱敏日志。构建问题还应提供命令、工具链版本和首个根因错误。提交前检查截图、导出和日志中的私人内容。

</details>

## 文档与贡献

### 按目标查阅

| 你想了解 | 阅读入口 |
| --- | --- |
| 安装后的第一步 | [用户指南](docs/user-guide/README.md) · [首次使用](docs/user-guide/getting_started.md) |
| 扩展、搜索与远程工具 | [扩展指南](docs/user-guide/extensions.md) |
| 网络、缓存与设备特权 | [网络与媒体](docs/user-guide/network_and_media.md) |
| 隐私、导出与旧数据导入 | [数据与迁移](docs/user-guide/privacy_and_data.md) |
| 从源码生成 APK | [构建指南](docs/doc-src/dev-core/BUILDING.md) |
| 理解工程与运行时 | [项目上下文](CONTEXT.md) · [仓库架构](docs/doc-src/architecture/repository_architecture.md) · [领域契约](docs/doc-src/contracts/README.md) |
| 开发脚本或 ToolPkg | [脚本指南](docs/SCRIPT_DEV_GUIDE.md) · [ToolPkg 格式](docs/TOOLPKG_FORMAT_GUIDE.md) · [示例](examples/README.md) |
| 查看计划和待验收事项 | [开发任务索引](docs/TODO/README.md) |
| 查找全部文档与工具 | [文档中心](docs/README.md) · [完整目录](docs/CATALOG.md) · [工具索引](tools/README.md) |

中文为主要文档语言；[English README](README.en.md) 提供对应的项目概览、使用与构建入口。

### 参与建设

欢迎围绕真实使用问题贡献代码、文档、翻译、工具包、可复现问题报告和设备验证结果。当前尤其关注跨模块协作、移动端交互、执行可靠性、性能、模型与扩展兼容性。

1. 先查看 [Issues](https://github.com/Kiyori-CN/Kiyori/issues) 和相关专项，说明问题、场景与预期结果。
2. 阅读 [贡献指南](docs/doc-src/dev-core/CONTRIBUTING.md)，按现有模块和接口组织改动。
3. 为改动提供相称的验证证据，注明尚未进行的设备或外部服务检查。
4. 将 Pull Request 提交到 `main`，保留上游归属、许可证与兼容契约。

## 上游、致谢与许可证

Kiyori 基于 [Operit](https://github.com/AAswordman/Operit) 演进。感谢其作者与贡献者建立 AI、工具和 Android 集成基础；项目继续保留适用的作者信息、源码归属与许可证声明。终端通过独立的 [KiyoriTerminalCore](https://github.com/Kiyori-CN/KiyoriTerminalCore) 子模块集成。

项目也使用或集成 mpv、FFmpeg、Anime4K、llama.cpp、MNN、QuickJS、Mihomo 等开源组件。具体来源、固定制品说明和组件许可见 [NOTICE](NOTICE)、[第三方法律资料](docs/legal/third_party) 与应用内开源协议页。

本仓库按 **[GNU General Public License v3.0 或更高版本](LICENSE)** 提供。各组件的独立许可证、NOTICE 和对应源码义务仍分别适用；修改和分发时应同时遵守。Kiyori 的产品名称、图标、仓库与发行渠道由本项目独立维护。
