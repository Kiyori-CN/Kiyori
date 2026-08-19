<div align="center">
  <img src="app/src/main/assets/logo.svg" width="152" alt="Kiyori Logo">
  <h1>Kiyori</h1>
  <p>由 Operit AI 驱动的 Android 全能型浏览器</p>
  <p>
    <a href="README.en.md">English</a> |
    <a href="https://github.com/Kiyori-CN/Kiyori">项目仓库</a> |
    <a href="https://github.com/Kiyori-CN/Kiyori/issues">问题反馈</a>
  </p>
  <p>
    <img src="https://img.shields.io/github/last-commit/Kiyori-CN/Kiyori" alt="Last Commit">
    <img src="https://img.shields.io/github/license/Kiyori-CN/Kiyori" alt="License">
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0+">
    <img src="https://img.shields.io/badge/status-development-orange" alt="Development status">
  </p>
</div>

> [!IMPORTANT]
> Kiyori 目前处于正式开发阶段，尚未建立公开发行渠道。请从本仓库构建开发版，不要将第三方 APK 视为 Kiyori 官方版本。

## 项目概览

Kiyori 是一款以浏览器为产品中心、以内置 Operit AI 为智能子系统的 Android 应用。人与 AI 共用同一套 Browser Runtime、WebSession 和真实 WebView，因此标签页、网页状态、Cookie、历史、下载和自动化操作不会被拆成互相不一致的两套实现。

项目在保留 Operit 对话、模型配置、工具调用、工作流、记忆、终端、MCP、Skill、ToolPkg 和本地模型能力的同时，由 Kiyori 负责应用壳、全局导航、浏览器体验、产品身份、系统设置和后续发行。

| 项目 | 当前状态 |
| --- | --- |
| 开发版本 | `0.1.0`（`versionCode 45`） |
| Android 标识 | `com.kiyori` |
| 支持系统 | Android 8.0 及以上（`minSdk 26`） |
| 当前 APK ABI | `arm64-v8a` |
| 媒体 native closure | mpv `2339eb727` + FFmpeg `n9.0.1` + Mbed TLS `3.6.7`；FFmpegKit `8.1.7-kiyori-n9.0.1-r6` + FFmpeg `n9.0.1` + OpenH264 `v2.6.0` + GPL/HarfBuzz/`drawtext`/`eq`/`boxblur` |
| 持续开发分支 | `main` |
| 正式发行 | 尚未开放 |
| 许可证 | GNU GPL v3 或更高版本 |

## 核心能力

- **共享浏览器运行时**：Browser Home 与 AI 浏览器工具共用标签页、WebView、Cookie、历史、书签、下载、用户脚本和窗口状态。
- **可追溯的设置返回**：从底部设置、浏览器菜单或 AI 左抽屉进入同一个设置首页；设置分类和子页面按进入顺序逐级返回，浏览器与 AI 来源最终回到原网页或原对话页面。
- **受控窗口与网页历史**：普通网页的同站、跨站、`target="_blank"` 和用户 `window.open()` 默认继续在当前窗口导航；只有配置主页上的真实用户跨站跳转会保留主页并创建同 Profile 子窗口。
- **可选启动恢复**：网页浏览器设置提供边缘滑屏前进后退、恢复上次搜索结果、恢复前询问和保留普通多窗口四个独立开关；无痕窗口和无痕元数据不会写入恢复记录。
- **按元素适配的网页长按**：链接、图片、图片链接、媒体、文本和普通元素显示各自可执行的紧凑菜单；图片可全屏查看、进入有界看图模式、保存和识别二维码。设置可关闭普通元素菜单，输入框、文本域和可编辑内容始终保留 Android WebView 系统原生剪切、复制、粘贴和选区手柄。
- **Operit AI 子系统**：支持多模型配置、对话、角色卡、记忆、工具调用、工作流、附件、语音和可恢复执行。
- **可审计 AI 对话**：每条已保存对话都有持久化“对话详情”，可查看输入、上下文转换、Provider、工具、异常、修订和终态，并导出完整 `.kiyori-audit` 包或适合外部 AI 审阅的隐私增强 Markdown。
- **扩展生态**：支持脚本包、ToolPkg 插件、Skill、MCP、市场安装、环境变量和权限管理。
- **本地工作区**：集成文件管理、Ubuntu 终端、SSH、开发工具、工作区和自动化能力。
- **媒体能力**：提供当前网页静态资源目录、图片缩略图与支持双指缩放的全屏分页查看/保存、音视频资源嗅探、下载、独立 mpv 播放器、播放队列、字幕、Anime4K、会话级完整视频缓存和悬浮/全屏切换。
- **本地推理**：集成 MNN、llama.cpp 等本地运行时；模型文件需要由开发者或用户按项目约定准备。
- **Android 集成**：覆盖系统助手、无障碍、悬浮窗、通知、文件提供者、Shizuku 和系统权限入口。

部分页面、真实设备交互和发行流程仍在持续验收。当前实现状态、唯一状态所有者和兼容性合同以 [`CONTEXT.md`](CONTEXT.md) 为准；阶段任务与现场验收状态以 [`docs/TODO/`](docs/TODO/README.md) 为准。

### 在线播放缓存

“设置 → 视频播放器 → 在线播放缓存”由一个四选一策略控制：`省流模式 / 智能均衡 / 流畅优先 / 完整缓存`，新安装默认使用“智能均衡”。策略在打开下一条媒体时快照，播放中修改设置不会重载当前视频，也不会中途替换当前请求的缓存所有者。

“完整缓存”仍使用同一个 mpv 请求、同一组请求头和同一个播放内核。只有有限时长、完整可拖动、总大小已知且空间充足的 HTTP/HTTPS 直链视频才会使用应用私有会话磁盘缓存；HLS/DASH、直播、滚动 DVR、未知大小或空间不足的媒体会明确保持为不符合完整缓存资格，并继续使用该档内建的基础播放缓冲。切换视频、关闭播放器或播放器进程重新建立时会释放对应会话缓存；它不是离线下载，也不会创建第二个网络请求或本地媒体副本。

## 产品结构

```text
Kiyori App Shell
├── 软件首页
│   └── 负一屏 ← 软件首页 → AI 首页
├── 浏览器首页
├── 小程序首页
├── 文件管理首页
└── 设置首页
```

浏览器是产品主体，Operit AI 是内置智能子系统。Kiyori 能力可以同时提供用户页面和受控的 AI 能力合同；AI 不直接操纵 Activity、Composable 或 ViewModel，也不会创建第二套浏览器、播放器、下载器或设置状态源。

浏览器窗口遵循“明确创建、窗口内按历史返回”的原则。人工新建、跨 Profile 搜索、AI 明确创建和配置主页的用户跨站跳转可以创建产品窗口；普通网页导航不会因为域名变化或网页弹窗请求自行增加窗口。配置主页跨站子窗口的历史耗尽后会关闭该子窗口并回到仍有效的主页窗口，其他窗口的历史耗尽后回到当前配置主页。

## 隐私与联网边界

- 云模型由用户自行选择服务商并配置 API Key、模型和端点；聊天请求由设备直接发送到用户选择的服务商。
- Kiyori 不提供大语言模型推理中转，也不连接 Operit 的应用更新、补丁或远程公告服务。
- 市场、模型服务、GitHub 登录、网页搜索、语音、图片生成和用户主动配置的远程能力会连接对应第三方服务。
- 新建公开数据使用 `Download/Kiyori`、`Pictures/Kiyori` 等 Kiyori 路径；应用不会自动扫描、合并或删除 `Download/Operit`。
- 浏览器启动恢复仅保存用户开启相应设置后所需的普通窗口 URL、标题、顺序、活动窗口、创建原因和明确搜索来源；不保存无痕窗口、Cookie、请求头、表单、网页正文、截图或密码。
- AI 对话审计随已保存聊天持久化，正文进入应用私有加密存储；API Key、Authorization、Cookie、密码、私钥、访问令牌和请求签名在加密前脱敏。只有删除整个聊天时才删除该聊天审计并回收无引用 payload。
- 完整 `.kiyori-audit` 用于本机复现和可信追溯；独立 AI 审阅 Markdown 还会假名化账户、设备标识和私有路径。两种导出都不包含真实凭据。
- API Key、令牌、Cookie、签名材料、私密日志和私人对话不得提交到仓库或公开 Issue。

## 获取源码

```bash
git clone https://github.com/Kiyori-CN/Kiyori.git
cd Kiyori
git switch main
git submodule sync -- terminal
git submodule update --init --recursive terminal
```

不要对整个仓库使用 `--recurse-submodules`：`terminal` 是常规构建所需、固定到特定提交的
KiyoriTerminalCore 子模块；`tools/hotbuild/OperitNightlyRelease` 是独立的可选私有子模块，
不属于常规 Debug 构建入口。

## 开发环境

完整 Android 构建使用以下基线：

- JDK 21；Java/Kotlin 字节码目标为 JVM 17
- Android SDK Platform 37、target SDK 34、Build Tools 36.0.0
- Android NDK 28.2.13676358、CMake 3.22.1
- Rust 1.88.0 与 `aarch64-linux-android` target
- Node.js 22 与 npm
- Python 与项目 `.venv`

仓库不会直接提交全部大型模型和本地运行库。`app/libs/`、`app/src/main/assets/models/`、`app/src/main/assets/subpack/` 和 `app/src/main/jniLibs/` 可能包含本机准备的构建输入，不能作为普通缓存批量删除。完整环境准备、依赖来源和故障排查见 [构建指南](docs/doc-src/dev-core/BUILDING.md)。

## 构建 Debug APK

准备好依赖后，在 Windows 运行：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

在 macOS 或 Linux 运行：

```bash
./gradlew :app:assembleDebug --no-daemon --console=plain
```

APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

当前 Debug 产品合同只打包 `arm64-v8a`。Release、AAB、签名、商店发布和真机验收具有独立的授权与验证流程，不能由 Debug 构建结果替代。

## 常用质量检查

仓库自有 Python 入口应使用项目 `.venv`。Windows 示例：

```powershell
.\.venv\Scripts\python.exe -B -m unittest discover -s ci\test -p "test_*.py"
.\.venv\Scripts\python.exe -B ci\script\check_formal_readiness.py --repository . --require-main
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py --repository . --require-main
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin compileDebugAndroidTestJavaWithJavac lintDebug --no-daemon --console=plain
```

完整门禁、候选提交边界和各检查入口见 [`ci/README.md`](ci/README.md)。

## 文档导航

| 文档 | 用途 |
| --- | --- |
| [`CONTEXT.md`](CONTEXT.md) | 产品术语、模块所有权、状态、协议、兼容性和不变量 |
| [`docs/README.md`](docs/README.md) | 项目文档总入口 |
| [构建指南](docs/doc-src/dev-core/BUILDING.md) | 环境、依赖、构建和故障排查 |
| [贡献指南](docs/doc-src/dev-core/CONTRIBUTING.md) | 开发流程、变更和验证要求 |
| [仓库布局](docs/doc-src/dev-core/REPOSITORY_LAYOUT.md) | 根目录、模块、生成目录和本地输入边界 |
| [AI 对话详情与完整审计](docs/doc-src/dev-core/AI_CONVERSATION_AUDIT.md) | 审计数据模型、写入门禁、修订、导入导出、安全和验证边界 |
| [正式开发准备](docs/TODO/formal_development_readiness/index.md) | 主分支、复现、CI、安全和设备验收门禁 |
| [`docs/TODO/README.md`](docs/TODO/README.md) | 当前长期任务、专项计划和历史验证证据 |

## 兼容性与数据迁移

Kiyori 是新的 Android 应用身份，历史 `com.ai.assistance.operit` 安装不能被 `com.kiyori` 直接覆盖。需要先在旧应用导出备份，再由 Kiyori 的明确导入入口恢复数据。

为保持数据和生态兼容，以下标识不会因产品品牌变化而被机械重命名：

- 源码 namespace `com.ai.assistance.operit`
- `operit://` OAuth 回调及既有 Intent、AIDL、JNI 和 IPC 标识
- 数据库、偏好、备份、工作区和文件格式
- 插件、ToolPkg、MCP 和市场协议标识

任何协议级迁移都必须具有独立的版本、数据迁移和回滚设计。

## 贡献与反馈

开始开发前请阅读 [贡献指南](docs/doc-src/dev-core/CONTRIBUTING.md)、[`AGENTS.md`](AGENTS.md) 和相关专项 TODO。提交问题时请提供设备型号、Android 版本、复现步骤和经过脱敏的必要日志：

- [问题反馈](https://github.com/Kiyori-CN/Kiyori/issues)
- [功能建议](https://github.com/Kiyori-CN/Kiyori/issues/new/choose)

## 上游与许可证

Kiyori 基于 [Operit](https://github.com/AAswordman/Operit) 演进。Operit 的历史作者、贡献者、源码归属和许可证声明继续保留；Kiyori 的名称、图标、仓库和发行渠道由 Kiyori 项目独立维护。

本仓库按 [GNU GPL v3 或更高版本](LICENSE) 提供。分发修改版本前，还应检查 [`NOTICE`](NOTICE)、随附第三方许可证、对应源码义务、品牌要求和相关服务条款。
