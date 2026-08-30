# 全软件 UI 风格统一优化

状态：`LOCAL SETTINGS TITLE IMPLEMENTATION VERIFIED / DEBUG APK VERIFIED / DEVICE VERIFICATION PENDING`。本专项针对 Kiyori 未发布版本，统一现有用户界面的视觉语言和高频交互表达，
不改变协议、数据格式、导航兼容标识或既有状态所有权。

## 目标与非目标

目标：

- 以现有“设置”页面的浅灰页面底色、白色分组卡、蓝色语义强调、紧凑层级和明确分隔线为基准，
  建立跨页面可复用的颜色、形状、间距、按钮、输入框、选择器和弹窗 token。
- 优先统一 AI Home/AI 对话顶栏、消息输入区、AI 左抽屉、设置 → AI 助手及其子页，随后覆盖
  浏览器、播放器、下载器及其设置入口和高频弹窗。
- 在统一外观的同时修复可由静态代码证据确认的 UI 缺陷：文字与徽标重叠、窄屏横向溢出、
  不一致的禁用态、缺少可访问语义、弹窗层级与安全区处理不一致。
- 维持 Material 3、现有 Compose 导航和唯一 WebView/播放器/下载状态 owner；图标继续使用
  Material/Lucide 等现有图标体系，不新增并行资源协议。

非目标：

- 不重写业务流程、Provider/网络/播放器/下载协议，不改变设置键、数据库、ToolPkg 或外部
  `operit://` 兼容标识。
- 不保留已经确认未发布且被新视觉取代的旧可见布局，不增加第二套主题或第二个状态源。
- 不通过条件切换或降级逻辑掩盖实际错误；必要的行为修复必须针对根因并有测试。
- 本轮不宣称真机视觉、手势、输入法、旋转或 OEM 系统弹窗验收已完成；这些保持
  `verification_pending`。

## 设计合同

1. `KiyoriTheme` 继续提供全局 Material 颜色；`KiyoriSettingsTheme` 作为设置风格的语义来源。
   新增的跨域 token 只从当前 `MaterialTheme.colorScheme` 派生，不复制一份可漂移的颜色表。
2. 页面背景使用 `background`，主要内容使用 `surface`/`surfaceContainer`，分组卡片最多使用
   `RoundedCornerShape(16.dp)`；按钮、输入框和菜单使用 8/12/14dp 的紧凑圆角，避免无意义的大
   胶囊化。播放器全屏控制层可以保留暗色覆盖，但其强调色与同一语义色映射一致。
3. 触摸目标至少 40dp；图标按钮提供 `contentDescription`，仅图标的陌生动作提供 tooltip 或
   可读语义；文本操作使用文字按钮，撤销、关闭、返回、搜索等使用熟悉图标。
4. 选择器和弹窗统一标题、辅助说明、内容滚动、确认/取消顺序和 `safeDrawing`，输入框统一
   focused/unfocused/disabled 边框、容器、标签和光标颜色。
5. 所有长标题、状态徽标和动态数值在窄屏、大字体、深色模式下保持可测量的最大行数与省略，
   不让右侧动作挤压正文；列表分隔线只在同组条目之间出现。

## 里程碑与影响文件

- M1 盘点与 token：`com.kiyori.design.theme`、通用 Compose UI 组件、主题测试；同步本文件和
  `docs/TODO/README.md`。
- M2 AI：`AIChatScreen`、`ChatScreenHeader`、`ChatScreenContent`、Agent/Classic 输入区、
  `KiyoriAiDrawer`、`ConversationDetailsScreen` 与 AI 助手设置页面及相关弹窗。
- M3 内容工具：浏览器 Home/设置/菜单、播放器控制层/设置、下载中心/设置及共享选择/确认
  弹窗。只修改呈现层和明确的布局/语义 bug，不触碰网络、媒体和任务 owner。
- M4 验证与交付：定向 Kotlin 编译/测试、`git diff --check`、正式开发准备门禁、串行
  `:app:assembleDebug`，核验 Debug APK；审计敏感内容、生成产物、子模块和 `main` 远端 ref，
  再提交并推送。

## 2026-08-30 本轮执行记录

- 已建立并接入 `KiyoriUiTokens.kt`：统一 `card=16dp`、`control=12dp`、`field=14dp`、
  `dialog=20dp`、`sheet=16dp` 顶部圆角和 `touchTarget=40dp`；`KiyoriTheme`、设置、浏览器、
  工具箱、浮窗以及 ToolPkg Compose DSL 均使用同一套 Material shape 映射。
- 已覆盖 AI 对话/抽屉/助手设置、设置首页及子页、浏览器与下载抽屉、播放器控制层、工具箱、
  文件工作区、扩展/市场、记忆/工作流、协议/权限、恢复页、浮窗与系统 overlay 的代码入口；
  对所有 `@Composable` 文件重新扫描并与工作树对账，共 `421` 个文件。
- 本轮额外修复实际交互容器小于 40dp 的遗漏：聊天统计详情、附件菜单行、附件标签、聊天滚动、
  Agent/Classic 权限选择、经典输入设置、待发送队列、浏览器视频徽标、搜索引擎快捷切换、下载
  后缀提取和下载行操作；图标绘制尺寸保持独立，避免无谓放大内容。
- 控件扫描基线：`AlertDialog 98`、`DropdownMenu 45`、`ExposedDropdownMenu 17`、
  `TextField 126`、`OutlinedTextField 91`、`BasicTextField 21`、`Button 239`、
  `IconButton 130`、`clickable 153`。定向 Kotlin 编译、formal readiness、fresh-clone 和
  `git diff --check` 均已通过；串行 `:app:assembleDebug --no-daemon --console=plain`、
  唯一 launcher、脚本代理 runtime 与播放器 runtime packaging 门禁以及 Debug APK 静态产物审计
  均已通过；下一关为精确暂存、提交并推送 `main`。

## 验收清单

- AI 对话顶栏按钮有一致尺寸、选中态和语义色；消息输入框、附件/工具菜单、处理中状态、
  发送/停止状态和浮动模式在浅色/深色与窄屏下不重叠。
- AI 左抽屉的状态头、快捷入口、分组行、徽标和 Settings 入口使用同一行高/图标容器/选中态；
  Back 链与当前 owner 不变。
- 设置 → AI 助手及所有子页、选择面板、输入框、开关、确认/错误弹窗与设置首页视觉一致。
- 浏览器、播放器、下载器的页面级按钮、菜单、空/加载/错误状态与设置风格具有一致层级；
  播放器全屏仍保持内容可读性和原有手势。
- 相关测试与 Debug 构建通过；设备验收单独记录为 `verification_pending`，不得用 APK 通过
  替代真实设备结论。

## 风险与回滚点

- Compose 主题边界可能影响弹窗或插件扩展页面；每个新 token 必须保留在现有主题边界内，先做
  编译和定向 UI/架构测试再扩大范围。
- AI 输入区、播放器控制层包含较多本地状态和手势；只调整 modifier/颜色/语义，不改变事件
  顺序，发现行为回归时撤销对应呈现改动，保留业务状态和事件顺序。
- 全量设备尺寸和 OEM 视觉尚未可用；最终状态即使本地验证通过仍保留 `verification_pending`。

## 2026-08-30 设置页标题与文件管理器入口增量

本增量继续沿用上一轮的 Kiyori 设置视觉合同，专项收口设置首页及“我的账号”到“更多功能”之间
全部设置子页面的顶部标题 owner。当前发现设置页同时存在折叠标题、固定工作台标题、外层通用
`TopAppBar` 和无标题 `CustomScaffold` 四种呈现，导致 AI 助手子页与浏览器/下载器的标题格式不一致。
本轮将所有旧设置页面改为显式使用 `KiyoriSettingsWorkspacePage` 并传入唯一 `onBack`，固定态统一
为 56dp 高、48dp 返回触摸目标、20sp 半粗标题、设置页背景和细分隔线；滚动型页面继续使用
`KiyoriCollapsingSettingsPage` 的同一固定态合同。

设置首页的“文件管理器”仅保留展示项，不再拥有 `openFileManager()` 回调或设置路由跳转；点击
保持无动作，文件管理主页自身的“手机存储”入口仍由 Shell 文件管理状态 owner 处理。未改变协议、
设置键、数据库、唯一 WebView/播放器/下载 runtime 或返回链。

本增量的自动验收结果：设置页面标题/返回链结构测试、文件管理器空动作回归测试、`compileDebugKotlin`、
正式开发准备门禁、`git diff --check`、串行 Debug APK 构建与产物审计；真实设备的浅深色、窄屏、
大字体、旋转、输入法和系统返回仍保持 `verification_pending`。
