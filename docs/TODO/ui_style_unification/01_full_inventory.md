# UI 全量盘点与验收矩阵

状态：`AUDITED LOCALLY / DEVICE VERIFICATION PENDING`。本文件是本专项的页面/组件覆盖清单，不替代代码和实际设备验收；状态以当前
工作树和后续检查结果为准。

## 基线扫描

扫描范围：`app/src/main/java` 下全部 Kotlin Compose 入口与其直接子组件，另检查
`app/src/main/res/layout`、独立 Activity/Window overlay 和 `web-chat` 不属于 Android Compose
的用户界面。

2026-08-30 基线统计：

- `421` 个 Kotlin 文件包含 `@Composable`；其中 `87` 个位于 AI/chat，`51` 个位于 settings，
  `42` 个位于 packages/market，`33` 个位于 toolbox，`31` 个位于 websession/browser，
  `21` 个位于 floating，`19` 个位于 main shell。
- 全仓 Compose 调用约 `271` 个 `AlertDialog`、`390` 个 `DropdownMenu`、`162` 个
  `ExposedDropdownMenu`、`512` 个 `OutlinedTextField`、`47` 个 `BasicTextField`、`495` 个
  `IconButton`、`1,377` 个按钮调用和 `199` 个 `clickable`；这些数字用于遗漏复查，不作为
  视觉完成证明。
- 主题根入口为 `MainActivity -> KiyoriTheme -> KiyoriAppShell`；独立路径包括
  `OperitUtilityTheme`、`FloatingWindowTheme`、`PlayerActivity -> KiyoriBrowserTheme`、
  `DataRecoveryActivity`、Crash/Permission/UIDebugger overlay 和 WebView 内嵌 Compose。

## 覆盖域

| 域 | 主要入口与组件 | 统一重点 | 状态 |
| --- | --- | --- | --- |
| App shell/Home | `com.kiyori.app.shell/*`、`ui/main/MainActivity.kt`、`ui/main/shell/*` | 根背景、顶栏、Pager、抽屉、Back、安全区、首页快捷操作 | `AUDITED LOCALLY` |
| AI 对话 | `ui/features/chat/screens/AIChatScreen.kt`、`components/*`、`style/*`、`details/*` | 顶栏按钮、消息层级、输入框、附件/工具/模型菜单、处理中、详情诊断、所有弹窗 | `AUDITED LOCALLY` |
| AI 助手设置 | `ui/main/shell/KiyoriAssistantExperienceSettingsPages.kt`、`ui/features/assistant/*`、`ui/features/settings/*` | 分组卡、行、开关、表单、模型/语音/记忆/权限子页和确认对话框 | `AUDITED LOCALLY` |
| 设置首页与系统设置 | `KiyoriSettingsHomePage.kt`、`KiyoriApplicationSettingsPages.kt`、`KiyoriMoreFeaturesSettingsPage.kt`、`KiyoriSettingsUi.kt` | 统一基准、导航行、选择面板、搜索、折叠标题、禁用态 | `AUDITED LOCALLY` |
| 浏览器宿主 | `ui/features/browser/appshell/*`、`ui/features/websession/browser/*` | 地址栏、标签页、菜单、网站设置、下载抽屉、历史/书签/脚本/网络日志面板 | `AUDITED LOCALLY` |
| 浏览器设置 | `KiyoriBrowserSettingsPage.kt`、`KiyoriBrowserPasswordManagerPage.kt`、`KiyoriAdBlockSettingsPage.kt` | 设置行、规则编辑、密码/清理确认、错误/空状态 | `AUDITED LOCALLY` |
| 播放器 | `ui/features/player/*`、`KiyoriPlayerSettingsPage.kt` | 全屏控制、进度/滑杆、弹出菜单、错误和目录选择；保持播放器手势与内容可读 | `AUDITED LOCALLY` |
| 下载器 | `KiyoriDownloadSettingsPage.kt`、`ui/features/websession/browser/WebSessionDownloadDrawer.kt`、下载 manager UI | 任务行、进度、并发/线程选择、目录和通知设置 | `AUDITED LOCALLY` |
| 文件/工作区 | `ui/features/toolbox/screens/filemanager/*`、`ui/features/chat/webview/workspace/*`、`KiyoriFileManagementPage.kt` | 工具栏、文件行、编辑器、选择器、删除/新建/导入弹窗 | `AUDITED LOCALLY` |
| 工具箱 | `ui/features/toolbox/screens/*`、`ui/features/toolbox/components/*` | 工具入口、状态卡、日志、权限、Shell、SQL、语音、调试覆盖层 | `AUDITED LOCALLY` |
| 扩展/插件/市场 | `ui/features/packages/*`、market、MCP、ToolPkg/脚本/Skill 页面 | Tab、搜索、筛选、卡片、安装/发布/环境变量/权限对话框 | `AUDITED LOCALLY` |
| 记忆/工作流 | `ui/features/memory/*`、`ui/features/workflow/*` | 图表/列表、编辑器、筛选、计划与连接菜单 | `AUDITED LOCALLY` |
| 协议/权限/首启 | `ui/features/agreement/*`、`ui/features/startup/*`、`KiyoriOnboardingScreen.kt`、
  `KiyoriPermissionsSettingsPage.kt` | 首帧、分组状态、权限动作、长文、错误和返回链 | `AUDITED LOCALLY` |
| 关于/帮助/恢复 | `ui/features/about/*`、`ui/features/help/*`、`ui/recovery/*`、Crash report | 品牌、链接、长文、恢复操作和异常提示 | `AUDITED LOCALLY` |
| 悬浮/系统 overlay | `ui/floating/*`、`services/floating/*`、`ui/common/displays/*`、permission overlay | 小窗口、球、OCR、系统安全区、触摸目标和透明层 | `AUDITED LOCALLY` |
| 共用渲染组件 | `ui/common/markdown/*`、`ui/components/*`、主题/动画/图标 | Markdown/代码/公式、图片/媒体、共享弹窗、抽屉、错误和颜色对比度 | `AUDITED LOCALLY` |

## 逐文件检查协议

每个域在标记 `DONE LOCALLY` 前都要对该域所有 Compose 文件执行以下检查，并在对应里程碑记录
实际文件与命令：

1. 入口与状态：确认页面是否由唯一 Shell/Host/Repository/ViewModel 持有；不新增并行 owner。
2. 视觉：检查 `MaterialTheme`/`KiyoriUiTokens`、背景层级、圆角、间距、Typography、深浅主题和
   透明/图片背景；清理仅为旧布局存在的重复颜色。
3. 控件：检查所有按钮、图标语义、最小触摸目标、选中/禁用/加载状态、文本省略和横向空间。
4. 输入/弹窗：检查键盘、焦点、滚动、安全区、确认/取消顺序、Back 和错误可见性。
5. 内容：检查用户可见文案、单位、空/失败/部分数据状态和可复制/可选择内容。
6. 验证：先定向编译/测试，再 `git diff --check`、正式门禁和串行 Debug 构建；设备视觉/手势
   仍单独标记 `verification_pending`。

## 遗漏复查

- 每个里程碑完成后重新运行 `rg -l -g '*.kt' '@Composable' app/src/main/java`，将新增/未触及
  文件与本表逐一对账。
- 重新扫描 `AlertDialog|DropdownMenu|ExposedDropdownMenu|TextField|OutlinedTextField|BasicTextField|Button|IconButton|clickable`，
  对新增调用确认是否继承根主题和统一 token。
- 提交前检查 `git diff --name-only` 是否只包含本专项实现、文档和必要测试；构建目录、APK、
  `work/` checkpoint、凭据和子模块内部改动不得进入提交。
