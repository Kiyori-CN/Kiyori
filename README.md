<div align="center">
  <img src="app/src/main/assets/logo.svg" width="128" alt="Kiyori 标志">
  <h1>Kiyori</h1>
  <p><strong>浏览网页，理解内容，把想法变成行动。</strong></p>
  <p>内置 Operit AI 的 Android 浏览器 · 网页 · AI · 媒体 · 工作区</p>
  <p>
    <a href="#开始使用">开始使用</a> ·
    <a href="docs/user-guide/README.md">使用指南</a> ·
    <a href="docs/README.md">开发文档</a> ·
    <a href="https://github.com/Kiyori-CN/Kiyori/issues">反馈问题</a> ·
    <a href="README.en.md">English</a>
  </p>
  <p>
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0 及以上">
    <img src="https://img.shields.io/badge/ABI-arm64--v8a-1E88E5" alt="仅 ARM64">
    <img src="https://img.shields.io/badge/status-in_development-F5A623" alt="开发中">
    <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0--or--later-555555" alt="GPL v3 或更高版本"></a>
  </p>
</div>

> [!IMPORTANT]
> Kiyori 尚未公开发行。目前通过本仓库构建开发版，功能与设备体验仍在持续完善。请勿将第三方 APK 视为官方版本。

## 为日常浏览，也为更进一步的工作

Kiyori 把网页浏览、AI 对话、内容保存和本地工作区放在同一个 Android 应用中。你可以阅读网页、管理窗口与下载、查看图片和播放媒体，也可以进入 Operit AI，通过自己配置的模型使用对话、工具、工作流与扩展。

人工浏览和 AI 浏览器工具共享网页会话，网页与工具能够围绕同一份页面状态工作。Kiyori 负责产品界面、浏览器与系统设置，Operit AI 提供内置的智能能力。

## 你可以用它做什么

| 场景 | 已有能力 |
| --- | --- |
| **浏览与整理** | 网页窗口、书签与历史、搜索引擎切换、自定义或空白主页、普通会话恢复、网站独立设置 |
| **阅读与专注** | 广告订阅与自定义元素规则、网页文字缩放、图片查看、用户脚本和扩展管理 |
| **AI 对话与工具** | 多供应商与协议配置、角色卡、记忆、附件、语音、工具调用、工作流及对话详情 |
| **保存与播放** | 网页资源目录、音视频嗅探、下载管理、mpv 播放器、字幕、队列、Anime4K 与悬浮播放 |
| **本地工作区** | 文件管理、Ubuntu 终端、SSH、代码执行和工作区工具 |
| **扩展与连接** | 脚本、ToolPkg、Skill、MCP、市场安装、环境变量与权限管理 |

音乐播放器、文档阅读器和小程序等部分独立入口仍在建设；本地推理需要另行准备兼容模型。这里的能力概览不表示所有页面、机型或外部服务都已完成验收。具体进度见 [开发任务索引](docs/TODO/README.md)。

## 开始使用

### 1. 确认设备与版本

- **系统**：Android 8.0 及以上。
- **处理器**：当前 APK 仅支持 `arm64-v8a`，不适用于 x86 模拟器或 32 位 ARM 设备。
- **应用标识**：`com.kiyori`。
- **开发版本**：`0.1.0`，`versionCode 45`；以实际 APK 和 [构建配置](app/build.gradle.kts) 为准。
- **获取方式**：当前需要自行构建 Debug APK，尚未提供公开发行下载入口。

### 2. 从源码构建

先获取源码与必要终端子模块：

```bash
git clone https://github.com/Kiyori-CN/Kiyori.git
cd Kiyori
git submodule sync -- terminal
git submodule update --init --recursive terminal
```

然后按 [完整构建指南](docs/doc-src/dev-core/BUILDING.md) 准备 JDK 21、Android SDK/NDK、Rust、Node.js，以及项目所需的大型依赖。**仅克隆源码不足以完成构建**；大型 AAR、模型、JNI 与子包输入由受控准备流程提供。

环境和输入准备完成后执行：

```bash
./gradlew :app:assembleDebug --no-daemon --console=plain
```

Windows PowerShell 使用：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

产物为 `app/build/outputs/apk/debug/app-debug.apk`。将 APK 传到兼容设备后按 Android 安装提示操作；已连接且授权 ADB 的开发设备也可使用：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

不要对整仓执行递归子模块克隆：可选私有夜间构建子模块不属于常规 Debug 构建。

### 3. 完成首次设置

1. 阅读产品介绍、用户协议与隐私政策，按自己的选择确认。
2. 只选择实际需要的权限；可以暂不授权进入应用，以后在 **设置 → 更多功能 → 权限管理** 中处理。
3. 从底栏进入浏览器，或在 **设置 → 网页浏览器 → 网页主页** 中设置自定义网址或纯空白页。
4. 使用 AI 时进入 AI 首页，从模型配置入口选择供应商、协议、模型并填写自己的 API Key。没有 Key 也可以保留在 AI 首页，浏览器使用不以模型配置为前提。
5. 使用终端或本地模型前，按相应页面完成环境或模型准备。

完整步骤见 [首次使用指南](docs/user-guide/getting_started.md)。

## 熟悉你的工作空间

```text
软件首页
├── 向左：负一屏，进入书签、历史与下载
├── 向右：AI 首页，对话、模型与工具
└── 底部导航
    ├── 软件首页
    ├── 浏览器
    ├── 小程序
    ├── 文件管理
    └── 设置
```

AI 页面左上角菜单打开扩展、工具箱与工作流；深层页面按返回链逐级退出。从浏览器或 AI 进入设置后，返回会保留原来的网页或对话来源。

### 按你的习惯浏览

- **主页与恢复**：选择自定义或空白主页，分别控制边缘滑动历史、上次搜索恢复和普通多窗口保留；无痕不写入启动恢复记录。
- **网站独立设置**：为当前域名调整广告拦截、脚本、缩放、外部应用等行为，不必修改所有网站的全局体验。
- **长按与看图**：链接、图片、媒体和文本提供相应动作；图片支持全屏、分页和双指缩放，输入框保留系统原生编辑菜单。
- **广告与扩展**：使用内置订阅、导入自己的规则，或通过“标记广告”管理当前网页元素；脚本和插件按各自权限运行。

### 用 AI 连接内容与工具

- 自行选择云服务商与模型，也可按项目支持的方式准备本地模型。
- 在扩展中配置脚本、ToolPkg、Skill 和 MCP；需要环境变量的包完成配置后再启用。
- OpenAI 搜索、Brave 搜索与学术检索各有独立配置。内置 Bilibili 工具包支持只读检索、内容导出和媒体处理，Cookie 由宿主持有。
- “对话详情”可查看本地记录的请求、工具、异常、修订和终态；诊断导出适合排查问题，分享前仍需检查私人内容。

扩展作者可从 [脚本开发指南](docs/SCRIPT_DEV_GUIDE.md) 开始；Bilibili 与远程工具配置见 [扩展使用指南](docs/user-guide/extensions.md)。

### 播放、下载与网络

内置 mpv 播放器支持队列、字幕、速度、Anime4K 与悬浮/全屏切换。在线播放缓存提供四种策略，“完整缓存”是符合条件媒体的会话缓存，不是离线下载。

**设置 → 更多功能 → 网络代理** 统一控制 Kiyori 应用请求，可配置规则、全局或直连模式。它不改变其他应用的网络，也不等同 Android 系统 VPN。

详细行为见 [网络、媒体与特权能力](docs/user-guide/network_and_media.md)。

## 你的数据与连接

- 云模型请求发送到你配置的服务商；Kiyori 不提供模型推理中转，也不连接 Operit 更新和公告服务。
- 网页、市场、搜索、语音、GitHub 登录和远程扩展按功能连接对应服务。
- 新公开文件使用 `Download/Kiyori`、`Pictures/Kiyori` 等路径。旧 Operit 与 Kiyori 是独立应用，迁移需先导出、再显式导入。
- API Key、Cookie、密码与令牌不应出现在公开 Issue。AI 诊断 Markdown 是明文，即使凭据已脱敏，也可能包含私人对话和工具内容。
- 网站凭据保存在设备绑定的加密私有存储中，无痕模式不保存或填写网站密码。

更多说明见 [隐私、数据与迁移](docs/user-guide/privacy_and_data.md)，完整法律正文可在应用 **设置 → 更多功能** 阅读。

## 常见问题

| 问题 | 处理方式 |
| --- | --- |
| 有官方 APK 下载吗？ | 目前没有公开发行渠道，请按源码构建指南生成开发版。 |
| 不配置 AI 能浏览网页吗？ | 可以。模型与 API Key 是使用相应 AI 服务的前提，不是进入浏览器或 AI 首页的前提。 |
| APK 无法安装怎么办？ | 核对 Android 版本、ARM64 架构、剩余空间和签名；不同签名不能直接覆盖，处理前先备份数据。 |
| 能直接覆盖原 Operit 吗？ | 不能，两者 application ID 不同。使用显式备份导入，不自动接管旧数据。 |
| 为什么某个网页或扩展需要额外权限？ | 相关功能依赖具体 Android 能力或第三方服务配置，在权限管理和扩展配置中按需要授权。 |
| Debug 构建通过是否说明所有功能可用？ | 不能。真机、OEM、网络服务与发行流程分别验收；未完成状态会保留在专项计划中。 |
| 应该提交什么排障信息？ | 设备与 Android 版本、Kiyori 版本或提交、复现步骤、预期和实际结果，以及必要的脱敏日志。 |

## 参与 Kiyori

| 你想做什么 | 入口 |
| --- | --- |
| 报告问题或建议 | [Issues](https://github.com/Kiyori-CN/Kiyori/issues) |
| 了解开发流程 | [贡献指南](docs/doc-src/dev-core/CONTRIBUTING.md) |
| 配置开发环境 | [构建指南](docs/doc-src/dev-core/BUILDING.md) |
| 理解模块与不变量 | [项目上下文](CONTEXT.md) · [运行时契约](docs/doc-src/contracts/README.md) |
| 查阅全部资料 | [文档中心](docs/README.md) · [文档目录](docs/CATALOG.md) |
| 编写插件与工具 | [脚本指南](docs/SCRIPT_DEV_GUIDE.md) · [ToolPkg 格式](docs/TOOLPKG_FORMAT_GUIDE.md) |

## 上游与许可证

Kiyori 基于 [Operit](https://github.com/AAswordman/Operit) 演进，保留其作者、贡献者、源码归属与适用许可证声明。浏览器、媒体、终端和其他第三方组件的来源见 [NOTICE](NOTICE) 与应用内开源协议页。

本仓库按 [GNU GPL v3 或更高版本](LICENSE) 提供。各组件的许可证与随附声明仍分别适用；Kiyori 的名称、图标、仓库与发行渠道由本项目独立维护。
