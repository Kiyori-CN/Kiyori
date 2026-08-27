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
- **浏览器诊断日志**：浏览器菜单提供独立的诊断抽屉，显示当前 WebView provider、能力、导航、权限、脚本和渲染错误等脱敏运行时事件；它与当前页面网络资源目录分开，不持久化网页或隐私数据。
- **可追溯的设置返回**：从底部设置、浏览器菜单或 AI 左抽屉进入同一个设置首页；设置分类和子页面按进入顺序逐级返回，浏览器与 AI 来源最终回到原网页或原对话页面。
- **受控窗口与网页历史**：普通网页的同站、跨站、`target="_blank"` 和用户 `window.open()` 默认继续在当前窗口导航；只有配置主页上的真实用户跨站跳转会保留主页并创建同 Profile 子窗口。
- **可选启动恢复**：网页浏览器设置提供边缘滑屏前进后退、恢复上次搜索结果、恢复前询问和保留普通多窗口四个独立开关；无痕窗口和无痕元数据不会写入恢复记录。
- **当前域名网站配置**：浏览器菜单可为当前 HTTP(S) 完整域名单独关闭广告拦截、用户脚本、返回缓存、左右滑动前进后退、强制缩放、元素长按、外部应用、定位、密码保存与填充、嗅探入口和自动悬浮播放。未配置时完全遵从全局设置，站点规则不能重新开启全局已关闭的能力，并在应用重启后继续生效。
- **按元素适配的网页长按**：链接、图片、图片链接、媒体、文本和普通元素显示各自可执行的紧凑菜单；图片可全屏查看、进入有界看图模式、保存和识别二维码。设置可关闭普通元素菜单，输入框、文本域和可编辑内容始终保留 Android WebView 系统原生剪切、复制、粘贴和选区手柄。
- **Operit AI 子系统**：支持多模型配置、对话、角色卡、记忆、工具调用、工作流、附件、语音和可恢复执行。
- **可审计 AI 对话**：每条已保存对话都有持久化“对话详情”，可查看输入、上下文转换、Provider、工具、异常、修订和终态，并导出完整 `.kiyori-audit` 包或适合外部 AI 审阅的隐私增强 Markdown。
- **扩展生态**：支持脚本包、ToolPkg 插件、Skill、MCP、市场安装、环境变量和权限管理；脚本内置
  `Academic` 学术分组，提供 arXiv、Crossref、PubMed、Semantic Scholar、OpenAlex 官方 API 查询。
- **本地工作区**：集成文件管理、Ubuntu 终端、SSH、开发工具、工作区和自动化能力；“文件管理首页 → 手机存储”和“设置首页 → 文件管理器”进入同一个文件管理器。
- **法律文档**：“设置首页 → 更多功能 → 用户协议与隐私政策”只读展示当前协议版本和两份现行文档，不改变首次启动确认状态。
- **媒体能力**：提供按已知扩展名和首选请求媒体范围分类的当前网页静态资源目录、支持 SVG 的图片缩略图，以及支持双指缩放的全屏分页查看/保存；同时包含音视频资源嗅探、下载、独立 mpv 播放器、播放队列、字幕、Anime4K、会话级完整视频缓存和悬浮/全屏切换。
- **本地推理**：集成 MNN、llama.cpp 等本地运行时；模型文件需要由开发者或用户按项目约定准备。
- **Android 集成**：覆盖系统助手、无障碍、悬浮窗、通知、文件提供者、Shizuku 和系统权限入口。

部分页面、真实设备交互和发行流程仍在持续验收。当前实现状态、唯一状态所有者和兼容性合同以 [`CONTEXT.md`](CONTEXT.md) 为准；阶段任务与现场验收状态以 [`docs/TODO/`](docs/TODO/README.md) 为准。

### 在线播放缓存

“设置 → 视频播放器 → 在线播放缓存”由一个四选一策略控制：`省流模式 / 智能均衡 / 流畅优先 / 完整缓存`，新安装默认使用“智能均衡”。策略在打开下一条媒体时快照，播放中修改设置不会重载当前视频，也不会中途替换当前请求的缓存所有者。

“完整缓存”仍使用同一个 mpv 请求、同一组请求头和同一个播放内核。只有有限时长、完整可拖动、总大小已知且空间充足的 HTTP/HTTPS 直链视频才会使用应用私有会话磁盘缓存；HLS/DASH、直播、滚动 DVR、未知大小或空间不足的媒体会明确保持为不符合完整缓存资格，并继续使用该档内建的基础播放缓冲。切换视频、关闭播放器或播放器进程重新建立时会释放对应会话缓存；它不是离线下载，也不会创建第二个网络请求或本地媒体副本。

### 应用级网络代理

打开 **设置首页 → 更多功能 → 网络代理**。这是 Kiyori 进程内唯一的网络路由设置，覆盖 AI 主模型
与语音、AI 工具、浏览器、下载、播放器、传统脚本和 Kiyori 自身在线服务。顶部的代理模式为“规则 / 全局 /
直连”：规则按域名决定是否进入当前策略组，全局让 Kiyori 的代理请求统一进入当前策略组，直连绕过内嵌
Mihomo。每个模块可以选择“跟随模式 / 直连 / 代理”，其中“代理”使用顶部当前模式；传统 JsEngine 脚本还可以在“脚本规则”中按包名
覆盖。环境变量抽屉底部的“网络代理”按钮会直接跳到这里，ToolPkg 不会被伪装成传统脚本。

页面主页提供代理开关、代理模式、节点选择、订阅管理、规则管理、模块连接模式、逐脚本连接模式、代理日志、局域网地址、
系统 VPN 并存和重置。节点选择、订阅管理、模块、逐脚本规则和日志各自进入独立子页面，避免同一订阅和
策略组在主页重复出现。规则管理页把当前订阅的可执行规则作为只读来源展示，并提供独立加密的自定义规则新增、编辑、启用/停用和删除；自定义规则类型为“完整域名 / 域名后缀 / 域名关键字”，订阅更新不会覆盖自定义规则。节点选择页使用横向分组标签、当前组搜索、顶部整组测速和排序；排序提供“默认、名称、延迟”，
默认保留订阅顺序，名称按不区分大小写排序，延迟把已成功测速的节点置于未测速或失败节点之前。节点逐行显示，点击
`select` 组节点直接切换，行尾测速按钮只测速该节点，自动策略组不提供伪装的手动选择。逐脚本页列出已启用且
可执行的传统 JsEngine 包，并保留手动输入包名入口；没有脚本时代表当前没有可配置的传统脚本，不影响 AI 工具模块。

“代理日志”仅在当前 Kiyori 进程内保留最近 1000 条订阅校验、Mihomo 启停、选点和测速记录。核心输出会先遮蔽
URL 凭据与路径、Bearer、secret/password/token、UUID、私有文件路径和长凭据；日志页支持查看、复制、通过
Android 系统文件选择器导出文本，以及二次确认后清空。日志不会持久化订阅 YAML 或 Controller secret。

订阅管理页提供“添加订阅地址”和“导入 YAML 文件”两个按钮，订阅行点击立即切换当前订阅，右侧三点菜单提供
“更新、编辑、复制、删除”；复制会创建新的订阅条目，不会切换当前订阅。订阅地址只要求填写 Clash/Mihomo
订阅 URL，或通过“导入 YAML 文件”选择单文档 UTF-8 YAML。客户端使用
`Clash.Meta` 身份请求 YAML mapping；Base64 节点列表、重复键、无有效出站、非法 provider 或 Mihomo
校验失败会拒绝导入。订阅中的局域网、loopback、链路本地、私有和组播节点会被隔离并显示数量，其余
策略组顺序和可选项会保留。订阅 URL、清洗后的 YAML 和控制器密钥使用 Android Keystore 加密并保存于
no-backup 私有目录。

运行配置不会继承订阅中依赖外部 GeoSite/GeoIP 数据库或已移除 rule-provider 的 DNS 匹配器；DNS
解析不会重新进入业务规则图，并且订阅没有 DNS 段时使用受控的 IP nameserver；这保证 `mihomo -t`
能在新的私有工作目录中离线完成结构校验，而不会在代理尚未运行时先下载额外数据库。

播放器的 HTTP(S) 直链和 HLS 媒体在应用代理模式下使用独立进程内的 IPv4 loopback 流式桥接：
mpv 读取本地 HTTP 流，桥接层按 `PLAYER` 路由请求真实媒体并转发 Range 与请求头，从而覆盖 mpv
`http-proxy` 不支持的 HTTPS；桥接端点只监听 `127.0.0.1`，不会暴露 LAN 或创建第二个代理核心。

Kiyori 内嵌 Mihomo 只监听随机 loopback mixed-port，不启用 TUN、LAN 入站或订阅提供的 Controller。
外部 Clash 使用系统 VPN/TUN 时无需填写主机、端口、用户名或密码；“直连”只表示绕过 Kiyori 应用层代理，
仍可能经过系统 VPN。检测到外部 VPN 后，内嵌代理默认拒绝启动；在高级设置明确允许并存后，代理模块按
`Kiyori Mihomo → 系统 VPN → 节点` 连接。该功能只影响 Kiyori 进程，不改变其他应用的网络。

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
