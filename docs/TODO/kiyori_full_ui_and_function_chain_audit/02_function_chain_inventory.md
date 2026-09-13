# 功能逻辑链路矩阵

## 审计方法

每条功能链按以下顺序核对：

1. 用户入口是否真实可达，入口文案与行为是否一致
2. 导航是否进入正确 route，Back 是否先处理页面内部状态
3. 是否只有一个状态 owner，Compose 是否只投影状态
4. 异步任务是否可取消、不会重复启动、不会在页面销毁后写入过期状态
5. 持久化、恢复、跨页面同步和进程重建是否保持一致
6. 权限、系统能力和外部依赖的前置条件是否可解释
7. 成功、失败、空结果和部分结果是否真实反馈
8. 资源、监听器、Receiver、Service、WebView、Surface 和临时文件是否释放

## 链路矩阵

| 功能域 | 入口 | 唯一 owner 与执行层 | 本轮检查 |
| --- | --- | --- | --- |
| 顶层导航 | 五入口、首页 Pager、AI 抽屉、设置入口 | `KiyoriShellState`、`AppRouterState`、route entry | root 保留、Back、重复导航、来源返回、隐藏页面交互 |
| 主题 | 界面定制、AI 外观 | `UserPreferencesManager` 与 AI 局部 preference | 旧全局颜色零残留、局部外观不越界、系统模式切换 |
| AI 对话 | AI Home、首页快捷动作、浮窗 | `ChatViewModel`、Chat core、历史 repository | 一次性快捷动作、发送/停止、流式状态、错误、历史切换、资源释放 |
| 角色与头像 | 角色选择、助手配置、消息气泡 | `CharacterCardManager`、用户 preference | 头像查询不阻塞渲染线程，角色切换与消息显示一致 |
| AI 导出 | 对话工作区导出入口 | Android/Windows exporter 与协程 Job | 取消必须取消真实任务，完成回调不能在取消后弹出成功 |
| 包管理 | AI 抽屉、市场、详情、管理 | `PackageManager`、市场 repository、ToolPkg runtime | 安装/更新/删除、刷新、详情返回、动态入口生命周期 |
| MCP 与 Skill | 包管理标签和配置页 | MCP repository、Skill 配置 owner | 部署进度、环境变量、错误、取消、配置保存一致 |
| 权限中心 | AI 抽屉权限、Kiyori 权限页 | Android capability owner、Shizuku/Root/Accessibility state | 权限状态刷新、系统返回、引导、状态文字和错误 |
| 应用权限工具 | 工具箱应用权限 | PackageManager、`AndroidShellExecutor` | 快速切换应用的陈旧响应、并发授权、错误、不可修改权限 |
| 工作流 | AI 抽屉、列表、详情、调度 | `WorkflowViewModel`、repository、scheduler | 同步阻塞查询、保存错误、调度状态、执行日志、节点修改 |
| 浏览器 | 首页搜索、Browser Home、AI browser tools | 单一 WebSession 与 Browser Runtime | Profile、窗口、WebView 挂载、Back、搜索、弹窗、脚本和数据 |
| 书签与历史 | 负一屏、浏览器菜单 | bookmark/history store | 计数、搜索、编辑、排序、删除、抽屉返回与同步 |
| 下载 | 浏览器、播放器、负一屏、设置 | `BrowserDownloadManager` 与 settings store | 既有封板合同回归，不建立第二任务源 |
| 播放器 | 浏览器候选、系统 Intent、悬浮/全屏 | `PlayerSession` 与 `:player` runtime | request、Surface lease、控制状态、返回、错误和进程死亡 |
| 记忆 | AI 抽屉、搜索、文件导入、图谱 | `MemoryViewModel` 与 memory repository | 导入资源关闭、临时文件、空间切换、搜索任务、错误可见 |
| 语音 | 设置、TTS/STT 工具、AI 输入与浮窗 | speech preferences 与现有 runtime | 初始化、测试、停止、权限、页面销毁和错误 |
| 文件 | Kiyori 文件页、工具箱文件管理 | 现有文件工具和规划中的 Kiyori 页面 | 空入口保持诚实；真实文件操作检查冲突、错误和选择结果 |
| 工具箱 | Tool registry 与 route catalog | `ScreenRouteRegistry`、各工具 owner | 动态列表稳定、重复点击、页面返回、未完成工具不伪装可用 |
| Help 与外链 | Help、About、项目链接 | 页面持有的 WebView 或系统 Intent | WebView Back、销毁、加载错误；外链失败可见 |
| 浮动 AI | 悬浮服务、窗口、球和全屏模式 | `FloatingChatService` 与对应 ViewModel | 服务生命周期、模式切换、关闭、权限和监听器释放 |
| 启动与恢复 | 启动加载、崩溃报告、数据恢复 | Application、恢复 ViewModel、CrashReport | 阻塞启动、错误说明、恢复动作、重启边界 |

## 已确认的首批缺陷候选

以下项目已有代码证据，进入实现前还需读取完整调用链：

1. `BubbleUserMessageComposable` 和 `BubbleAiMessageComposable` 在组合期间使用 `runBlocking`
   查询角色头像，可能阻塞主线程和列表滚动
2. AI Android/Windows 导出进度的取消按钮只隐藏 Dialog，没有取消导出协程
3. `WorkflowViewModel.isWorkflowScheduled` 使用 `runBlocking`，若从 Compose 渲染路径调用会阻塞主线程
4. `HelpScreen` 的 `onBackPressed` 未使用，WebView 离开组合后没有执行销毁
5. `AppPermissionsScreen` 的权限加载请求没有取消或请求身份保护，快速切换应用可能显示前一个应用结果
6. Memory 文件导入的输入流、Reader、输出流和临时文件名需要检查异常路径与资源释放

候选只有在调用链确认后才能修改；静态命中不直接等于缺陷。

## 禁止扩大范围

- 不增加新的工具、设置项、下载能力、播放器能力或小程序页面
- 不把现有空入口连接到不相干的 owner
- 不迁移数据库、协议、application ID 或 ToolPkg/MCP 标识
- 不为错误增加另一条执行路径
- 不把专项架构重构混入 UI 和功能闭环

## 本轮结论

- 顶层导航继续由既有 Shell 和 Router 状态所有者控制；负一屏关闭动作已回到真实 Home 状态
- AI 角色头像读取移出 Compose 阻塞路径；对话导出取消会取消真实任务，并清理部分输出和临时目录
- Android 与 Windows 导出的 ZIP 解包增加规范化路径约束，拒绝正斜杠和反斜杠形式的目录越界
- 权限工具的应用切换请求和修改操作已序列化并具备请求身份保护；只处理实际危险权限，不把不可修改权限伪装成成功
- 工作流列表、详情和节点弹窗补齐真实错误反馈；调度查询不再从渲染路径同步阻塞
- Memory、语音、恢复、包市场、GitHub 登录、悬浮 AI、播放器附件和系统工具中的取消、关闭、资源释放和错误记录已按生命周期收口
- Help 与 Token WebView 补齐页面内 Back、销毁、主题和外部失败反馈
- `GlobalScope` 只在经审计的进程级 owner 中被替换为命名作用域；剩余同步桥接点属于既有严格同步合同，没有异步化以破坏顺序
- Browser Runtime、Player Runtime、下载管理器、持久化 owner 和兼容标识保持不变；未增加第二执行路径或 fallback
