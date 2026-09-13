# UI 覆盖矩阵与设计规则

## 设计原则

Kiyori 的页面以中性表面承载内容，以小面积语义色帮助识别，不使用大面积随机彩色背景。

- 页面背景、App Bar、正文卡片、浏览器 chrome 和网页容器保持中性
- 图标、状态、选中边界、标签和关键动作使用稳定的蓝、绿、紫、橙、红、青、粉语义色
- 相同功能在首页、抽屉、设置和独立页面使用相同色调
- 标题、正文和说明分别使用 `onSurface`、`onSurface` 与 `onSurfaceVariant`
- 删除、退出、失败和高风险权限使用红色；成功、完成和已授权使用绿色
- 普通图标优先使用统一圆角语义徽章；紧凑工具栏图标保持无容器，但颜色和触控面积必须一致
- 可点击区域不小于现有 Material 最小触控合同；纯装饰图标不得抢占点击或无障碍焦点
- 空态、加载态、错误态、禁用态和无结果状态必须有不同文字和视觉语义
- 深色主题不能仅靠降低透明度模拟；背景、容器、边界和文字必须来自当前主题

## 特殊渲染域

以下界面不机械套用普通页面配色：

- 视频播放器、悬浮播放器和图片全屏预览保留黑色沉浸遮罩
- 代码编辑器、终端、日志正文和语法高亮保留各自内容主题
- 屏幕识别、UI 自动化和虚拟显示覆盖层保留高对比诊断色
- AI 气泡、头像、背景、聊天头部和输入区继续使用 AI 局部个性化
- 网页内容、favicon 和第三方 ToolPkg 自绘界面不由 Kiyori 强制改色

这些区域仍需检查关闭、返回、系统栏、错误反馈、遮罩层级、触控热区和资源释放。

## 覆盖矩阵

| UI 域 | 主要入口与文件 | 当前重点 | 目标 |
| --- | --- | --- | --- |
| App Shell 与导航 | `KiyoriAppShell`、`AppContent`、`OperitApp`、`KiyoriAiDrawer` | 顶栏、底栏、跨页面背景、Back、缓存页面可见性 | 中性壳层、稳定状态栏、无隐藏页面交互泄漏 |
| 软件首页与负一屏 | `KiyoriShellPages`、`KiyoriMinusOnePage` | 搜索框、天气、快捷动作、数据卡 | 保留既定布局，统一排版、语义图标和状态反馈 |
| 设置首页与设置根 | `KiyoriSettingsHomePage`、`KiyoriApplicationSettingsPages`、`KiyoriCollapsingSettingsPage` | 已完成第一轮统一，需审计全部子页和弹窗 | 所有设置路径使用相同标题、分组、卡片、选择面板和禁用态 |
| 文件管理器设置页 | `KiyoriFileManagementPage` | 固定白底、浅灰文字、独立渐变色和大量空点击 | 浅深主题完整适配，保留空入口但显示诚实禁用语义 |
| AI 对话根页面 | `AIChatScreen`、`ChatHeader`、`ChatScreenHeader`、`ChatScreenContent` | 局部个性化、抽屉、历史、加载和弹窗层级 | 系统动作使用 Kiyori 语义色，聊天内容保留局部个性化 |
| AI 消息与输入 | bubble、cursor、agent、classic、附件、导出和预览组件 | 字体、头像查询、空回调、导出取消、对话框风格 | 避免主线程阻塞，统一状态和弹窗，保留消息样式合同 |
| 助手配置与角色 | `AssistantConfigScreen`、头像和语音绑定组件 | 表单密度、头像卡、对话框、保存反馈 | 粉色个性化语义、统一表单和确认反馈 |
| 记忆 | `MemoryScreen`、`FolderNavigator`、`GraphVisualizer`、dialogs | 搜索、侧栏、五个浮动动作、导入、图谱状态 | 搜索/文件夹/链接/导入/新增拥有稳定色调，导入错误可见 |
| 包管理与市场 | packages、market、MCP、Skill、发布和详情页 | 根页已统一，深层页面和弹窗仍来源多样 | 插件紫、脚本青、Skill 橙、MCP 蓝，发布与危险操作语义明确 |
| 权限与 Shizuku | `PermissionGuideScreen`、`ShizukuDemoScreen`、`AppPermissionsScreen` | Kiyori 权限页已统一，应用权限工具仍有独立硬编码色 | 权限组映射到统一语义色，授权/拒绝/高风险状态一致 |
| 工作流 | `WorkflowListScreen`、`WorkflowDetailScreen`、Canvas 与 dialogs | 列表和画布已部分统一，详情和调度仍需审计 | 工作流橙、节点类型稳定分色、执行状态和日志一致 |
| 工具箱根 | `ToolboxScreen`、`ScreenRouteRegistry` | 全部工具卡统一蓝色 | 根据稳定 entry id 映射语义色，卡片密度和反馈统一 |
| 工具页 | 文件、终端、语音、日志、SQL、FFmpeg、HTML、AutoGLM、测试器 | 多种 Scaffold、空动作、不同顶栏和错误反馈 | 不改变工具行为，统一外壳、空态、加载、错误和动作层级 |
| 浏览器根与 chrome | browser app shell、top/bottom bar、tab overview | 前轮已部分统一，需检查完整状态组合 | chrome 中性、网页不染色、状态与内容工具使用小面积语义色 |
| 浏览器抽屉与弹窗 | menu、bookmark、history、download、userscript、media、network、UA、popup | 前轮已统一重点页面，需复核隐藏态、遮罩和所有对话框 | 所有 Drawer、Sheet、Dialog 具备一致表面、标题、动作和 Back |
| 播放器 | `PlayerScreen`、`PlayerControls`、`PlayerGestureLayer`、floating player | 沉浸式固定黑色合理，需审计禁用按钮、状态和 Surface UI | 保留媒体语义，修复明确交互问题，不改变 Player Runtime |
| 浮动 AI | `FloatingChatWindow`、ball、fullscreen、window、screen OCR | 独立玻璃和黑色覆盖层 | 保留局部视觉，统一错误/加载/关闭与触控反馈 |
| About、Help、Agreement | about、help、agreement | About 图标单色；Help 白色遮罩和 WebView 生命周期；协议页单一蓝色 | 统一语义图标、主题遮罩、返回和资源释放 |
| 启动、恢复与崩溃 | PluginLoading、DataRecovery、CrashReport | 独立任务状态和错误恢复 | 状态颜色一致，关键恢复动作清晰，不掩盖失败 |
| 通用渲染组件 | `CustomScaffold`、ErrorDialog、Markdown、附件预览 | 共用组件影响面大，特殊内容域混杂 | 只提取稳定通用规则，不用一个组件强行覆盖特殊渲染域 |

## 稳定语义映射

| 色调 | 功能域 |
| --- | --- |
| 蓝 | AI 对话、浏览器、连接、通用主动作、MCP |
| 绿 | 下载、数据、成功、已授权、触发节点 |
| 紫 | 插件、包、收藏、扩展、逻辑节点 |
| 橙 | 工作流、历史、运行、调度、条件节点 |
| 红 | 权限风险、错误、删除、退出、停止 |
| 青 | 网络、媒体发现、日志、工具、脚本 |
| 粉 | 角色、头像、外观、个性化 |

动态 ToolPkg 或工具箱入口使用稳定 ID 映射现有色调，不能按列表位置随机变色。

## 反向检查

- 普通页面是否仍直接使用 `Color.White`、`Color.Black` 或浅色专用灰
- 同一功能是否在不同入口使用不同语义色
- 深色模式下禁用文字、分隔线、输入框、Dialog 和 Sheet 是否仍可读
- 全屏遮罩、Dialog、Drawer 和 Sheet 是否存在点击穿透
- 隐藏但 keep-alive 的页面是否仍接收输入、IME、Back 或无障碍事件
- 小程序、负一屏快捷工具、文件管理规划项等空入口是否被误接到其他功能
- 特殊内容域是否被普通主题覆盖而失去对比度

## 本轮结论

- 普通页面已统一到固定 Kiyori 浅色、深色和跟随系统主题；旧全局自定义颜色键、设置区和页面消费点经反向搜索为零
- 彩色图标使用稳定功能语义而不是列表位置；浏览器 chrome 继续保持中性，蓝、绿、紫、橙、红、青、粉只用于识别、状态和关键动作
- AI 消息、头像、气泡、背景和输入区保留现有局部个性化，不再影响设置、工具、浏览器或应用壳
- 视频 Surface、全屏预览、代码、终端、日志、图谱和诊断 Overlay 作为特殊渲染域保留其高对比内容配色
- 负一屏、文件管理和设置首页中的未实现入口已改为诚实禁用或静态状态；小程序管理没有接入包管理或其他 AI 内容
- 浏览器各 Drawer、Sheet、Dialog、AI 抽屉、包管理、权限和工作流页面完成主题、图标、状态与错误反馈复核
- 真机上的系统栏、输入法、横竖屏、折叠/大屏、遮罩高度和触控热区仍为 `verification_pending`
