# Kiyori 项目上下文

本文件适合开发会话按需注入，只保留产品语言、核心所有权和兼容边界。详细行为见 [运行时契约](docs/doc-src/contracts/README.md)，当前进度见 [开发任务索引](docs/TODO/README.md)，工作规则见 [AGENTS](AGENTS.md)。

## 产品与身份

| 项目 | 定义 |
| --- | --- |
| Kiyori | 面向 Android 的人机协作应用；当前由产品壳、共享浏览器、文件/媒体及 Operit AI 运行时组成，AGI 是长期研发方向 |
| Operit AI | 内置 AI 子系统，负责对话、模型、工具、工作流、记忆与扩展运行时 |
| Android application ID | `com.kiyori` |
| 产品版本 | 由 `app/build.gradle.kts` 声明；与市场兼容版本独立 |
| 仓库 | `Kiyori-CN/Kiyori`；持续开发分支为 `main` |
| 终端 | `terminal/` 是 KiyoriTerminalCore 子模块，父仓库锁定 gitlink |
| 当前发行状态 | 未发布；不接收 Operit 更新、补丁或远程公告 |

协作愿景不表示已经实现通用智能，也不改变下列唯一运行时与兼容边界。
定位演进见 [设计决策](docs/doc-src/decisions/0001_kiyori_product_positioning.md)。

## 唯一所有权

“所有者”指实际持有状态、生命周期或持久化事实的组件。UI、适配器和兼容入口只能消费或委托它，不能复制第二份事实。

| 领域 | 唯一边界与关键符号 | 详细契约 |
| --- | --- | --- |
| 应用壳 | `KiyoriApplication` → `KiyoriApp` → `KiyoriAppShell`；`KiyoriShellState` 持有壳状态 | [应用壳与导航](docs/doc-src/contracts/product_shell.md) |
| 浏览器 | `StandardBrowserSessionTools.getSharedInstance` 构造唯一 Browser Runtime；人工与 AI 共用 WebSession / 真实 WebView | [浏览器](docs/doc-src/contracts/browser.md) |
| 下载 | `BrowserDownloadManager` 持有内部任务；系统下载留在 Android DownloadManager | [媒体与下载](docs/doc-src/contracts/media_downloads.md) |
| 播放器 | 主进程 `PlayerSession`；非导出的 `:player` 进程持有 mpv，Surface 通过 lease 交接 | [播放器架构](docs/doc-src/dev-core/PLAYER_ARCHITECTURE.md) |
| 应用代理 | 主进程 `KiyoriNetworkProxyManager`、一个加密配置、一个 Mihomo；各请求按统一路由执行 | [网络路由](docs/doc-src/contracts/network_proxy.md) |
| 设置 | 产品设置与 AI 助手设置各有领域所有者，共用来源保持的设置会话 | [应用壳与导航](docs/doc-src/contracts/product_shell.md) |
| AI 执行 | `ModelCapabilityResolver` / `ModelRequestCompiler`；执行仓库与工具账本分别持有恢复状态和副作用身份 | [AI 请求与执行](docs/doc-src/contracts/ai_execution.md) |
| 对话审计 | `ConversationAuditRepository` 持有不可变事件、修订、加密 payload 与 seal | [完整审计](docs/doc-src/dev-core/AI_CONVERSATION_AUDIT.md) |
| 扩展 | PackageManager、ToolPkgManager、SkillManager、MCPManager 各自管理所属 registry 与生命周期 | [扩展与工作区](docs/doc-src/contracts/extensions_workspace.md) |
| 平台 | `KiyoriPaths`、`KiyoriLogger`、权限目录及终端分别持有路径、日志、权限事实和会话 | [平台与存储](docs/doc-src/contracts/platform_storage.md) |
| AI 产物 | `KiyoriArtifactStoragePolicy` 持有默认保存偏好；`ArtifactStorageAccess` 只投影已有对话工作区，工具不复制路径状态 | [产物存储](docs/doc-src/contracts/artifact_storage.md) |

## 高频不变量

1. **一个事实只有一个状态所有者。** AI 通过能力契约调用产品功能，不直接驱动 Activity、Composable 或 ViewModel；不创建第二个浏览器、播放器、下载器或设置存储。
2. **应用壳控制全局导航。** 软件首页居中，左侧负一屏、右侧 AI 首页；底栏包含软件首页、浏览器、小程序、文件管理、设置。
3. **AI 抽屉只属于 AI 页面。** AI 首页和明确注册的 AI 顶层页显示菜单按钮，深层页显示返回；浏览器、小程序、文件管理和 Kiyori 设置不显示该按钮。固定快捷入口依次为“扩展 / 工具箱 / 工作流”。
4. **Back 遵循实际来源和生命周期。** 先关闭当前弹层，再退所属子栈；非软件首页根返回软件首页，软件首页根请求退出确认。仍在组合中的后台页面不得抢占前台 Back。
5. **权限与首启共用事实。** `POST_NOTIFICATIONS` 只走集中式运行时权限流程；首启完成后不存在独立的启动通知请求所有者。
6. **请求失败保持可见。** 未知提交状态不能被当作未执行；不得通过重新 POST、换模型、换端点或静默直连掩盖失败。已知 Responses ID 的恢复只续接同一 response。
7. **历史重放必须工具闭合。** `AssistantReplayHistoryProjector` 只持久化可重放的安全投影；真实实时流和审计保留原始事实，不伪造工具结果。
8. **异步发布校验代际。** 旧请求、旧 WebView、旧服务实例和清理回调不得覆盖新状态；资源释放由实际生命周期所有者完成。
9. **敏感信息先脱敏再持久化或导出。** API Key、Authorization、Cookie、密码、私钥、令牌和签名不进入日志、审计明文或仓库。
10. **构建通过不等于现场通过。** 设备交互、真实服务、发行签名和远端 CI 分别验收；专项 TODO 中的 `verification_pending` 保持原义。
11. **文件管理器会话由 Shell 保留。** 设置往返与应用内最小化复用同一个 `FileManagerViewModel`；网络环境只经标准文件工具分流，未实现操作明确失败。目录工作区列表不自动改写 AI 对话绑定。

## 包结构与兼容性

- `com.kiyori` 承载产品壳、设计系统、平台能力与集成；`com.ai.assistance.operit` 保留 AI 运行时和稳定生态接口。
- `com.kiyori.capability` 定义跨领域能力，`com.kiyori.integration.operit` 适配 Operit；实际已迁移范围以源码和 [架构控制面](config/architecture/README.md) 为准。
- 九个 Android 模块仍由根 settings 声明；`buildSrc` 只持有 Gradle 任务实现，不进入 APK。app 注册任务并提供 Variant 输入；宿主工具统一由 `tools/` 按职责管理，详细边界见 [仓库与源码架构](docs/doc-src/architecture/repository_architecture.md)。
- Gradle namespace、AIDL/JNI、Manifest 稳定组件、Intent action、`operit://`、数据库、偏好、备份、工作区、ToolPkg/MCP 和 `.operit/config.json` 均不是可机械替换的品牌文本。
- 新内置脚本采用 Kiyori 展示名与约定的 `com.kiyori.*`、`remote_kiyori`、`kiyori-pc-agent` 标识；历史 Operit 输入只按显式兼容契约导入。
- Windows 工具包保持 `windows_control` / `WINDOWS_AGENT_*` 兼容；手机配置与执行共用现有环境变量所有者，电脑管理与远程执行分端口、共用服务状态。连接与文件限制见扩展契约。
- `BuildConfig.OPERIT_MARKET_COMPAT_VERSION` 服务市场 `minAppVer/maxAppVer` 判断，不能用 Kiyori 的 `versionName` 替代。
- Kiyori 是独立 application ID，不能覆盖旧 Operit 安装；迁移通过用户明确选择的备份或文件入口。
- 新公开文件写入 `Download/Kiyori` 或 `Pictures/Kiyori`；不自动扫描、合并或删除 `Download/Operit`。设备绑定的凭据和派生缓存按各领域备份边界处理。
- 共享文件回收使用源存储卷的 `.kiyori-recycle-bin/<applicationId>`，避免跨入 `Android/data` 的挂载限制；共享回收内容手动清理，内部及旧专属回收内容仍受应用数据清理影响。
- 上游作者、许可证和来源继续准确标注 Operit 及其他项目，不受产品品牌替换规则影响。

## 按需阅读

- 修改 UI：先读 [设计来源决策](docs/doc-src/decisions/0003_ui_design_source_hierarchy.md) 与对应页面契约，再核对参考源码。
- 修改运行时：从 [领域契约索引](docs/doc-src/contracts/README.md) 进入，继续追踪列出的正式架构与源文件。
- 修改构建：读 [构建指南](docs/doc-src/dev-core/BUILDING.md)、[仓库布局](docs/doc-src/dev-core/REPOSITORY_LAYOUT.md) 与 [CI 指南](ci/README.md)。
- 恢复任务：以专项 TODO `index.md` 为状态入口；[历史记录](docs/TODO/history/README.md) 仅供追溯，不作为当前源码或当前验证结果。
