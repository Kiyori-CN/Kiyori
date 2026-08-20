---
status: accepted
date: 2026-07-22
updated: 2026-08-18
supersedes: 0002_product_shell_and_ai_center_navigation.md
---

# 模态 AI 左抽屉导航

## 背景

首个 Kiyori App Shell 切片把 Operit 左抽屉替换成全屏 AI Center。产品复核后确认，全屏中转页增加了不必要的导航层级，也削弱了 AI 首页与 AI 一级页面之间的直接切换。Kiyori 尚未发布，因此不保留该全屏方案的兼容入口。

原版 Operit 抽屉仍是信息结构与视觉密度来源，但它同时包含边缘拖动、滑动关闭、主页面透视变换和永久平板侧栏。这些容器行为与 Kiyori 首页 Pager、稳定 AI Home 宿主和折叠屏边界冲突。

## 决策

- 删除全屏 AI Center 路由、页面、状态、字符串和来源标记。
- AI 首页及 AI 一级页面的三横线按钮直接打开覆盖当前页面的模态左抽屉。
- 抽屉包含状态头部、包管理、权限授予、工作流、AI 对话、助手配置、记忆库、工具箱、ToolPkg 动态插件和底部“设置”；不包含帮助、关于、使用手册或 Terminal。
- 抽屉只能由三横线按钮打开。禁止左边缘打开、横向拖动和滑动关闭；点击遮罩、再次选择当前入口或系统 Back 可以关闭。
- 抽屉从左侧滑入和滑出，遮罩同步淡入和淡出。底层页面继续渲染但不接收触摸，不发生位移、缩放、倾斜、圆角或透明度变化。
- 抽屉宽度在 `<600dp` 时为窗口宽度的 `75%`，在 `600-839dp` 时为 `320dp`，在 `>=840dp` 时为 `360dp`；遇到分隔铰链时宽度限制在左侧物理区域内。
- 抽屉遮罩覆盖窗口全高，面板从状态栏底部开始并只处理水平与底部安全区；左侧两角为直角，右侧两角为 `16dp` 圆角，底层遮罩使用主题暗色的 `32%` 不透明度。
- 点击其他一级入口时，先同步建立目标一级路由，再关闭抽屉；一级入口互相替换，不连续压栈。点击当前入口只关闭抽屉。动作型 ToolPkg 入口只执行一次。
- AI 一级页面保留各自的页面状态与子栈。一级页面显示三横线，深层页面显示返回箭头；一级页面 Back 返回 AI 首页，深层页面 Back 返回所属一级页面。
- 原生一级根使用稳定实例。ToolPkg 一级根每次进入生成新路由实例，只有对应路由声明 `keepAlive=true` 时才恢复组合状态和保存子栈。
- AI 一级根在路由条目上显式保存注册入口 ID。宿主入口按 route ID 匹配，ToolPkg 插件入口按 route ID 和注册参数匹配；不得通过 route args、返回栈深度或 instance ID 前缀猜测根归属。启动、快捷方式、raw route 与 `AppRouterGateway` 共用这一规则。
- 抽屉底部“设置”启动 `KiyoriSettingsOrigin.AI_HOST` 的来源保持型设置会话，不替换当前 AI 一级路由或子栈；设置首页隐藏软件首页底部五入口，设置详情按 capability-level `KiyoriSettingsRoute` 栈逐级返回，关闭设置首页后回到原 AI 页面。
- AI 助手设置保持单一页面、表单与持久状态，由设置首页进入。Operit 设置 route 携带活动设置 `sessionId`，返回时恢复同一设置 route stack；底部设置、Browser Menu 和 AI 左抽屉不再用 `KiyoriShellChild` 表达设置层级。
- 包管理、ToolPkg、脚本包和插件市场继续属于 AI 抽屉及其独立目的地，不得经由设置首页为底部小程序产品域保留的“小程序管理”空入口打开。
- `KiyoriSettingsRoute.PERMISSIONS` 与首次启动共同消费唯一设备权限事实；`Screen.ShizukuCommands` 只保留独立执行通道/开发诊断职责；`ToolPermissionSystem` 继续只拥有 AI 工具授权，三者不互相复制。
- AI Home 保持单一、稳定的组合宿主。打开或关闭抽屉、切换 AI 一级页面都不能暂停、取消、销毁或重建其流式回答、思考、工具调用、附件、草稿、会话和滚动状态。
- 三页首页共享一个 `PagerState` 和同一产品吸附合同。负一屏与软件首页保留原生
  `HorizontalPager` fling；永久 AI Home 使用公开 API bridge 记录当前手势，并以严格半页、
  `400dp/s`、单页边界、LTR/RTL 和同一 spring 完成释放。移动跟随手指，真实反向拖动可以中断
  尚未完成的吸附，普通点击在水平 touch slop 前保持可用，Shell 状态在页面 settle 后更新。
- 抽屉直接使用共享 `OperitTheme` 的组件视觉语言，默认色板遵守 [专业浏览器灰白默认主题](0007_professional_browser_theme.md)。不得恢复抽屉专属玻璃、背景色、强调色或持久化偏好。
- Kiyori 页面使用同一 edge-to-edge 状态栏：页面背景和抽屉遮罩延伸至物理顶边，抽屉面板从状态栏底部开始，显示中的状态栏保持透明。删除继承的透明状态栏和自定义状态栏颜色设置及偏好，只保留隐藏状态栏。

## 影响

- `KiyoriShellChild.AI_CENTER`、`AiCenterDestination`、`KiyoriAiCenterPage` 和 `RouteEntrySource.AI_CENTER` 被删除。
- AI 一级入口继续使用 `ScreenRouteRegistry` 与 ToolPkg 注册协议，动态插件 route ID 和排序不变。
- 顶部左键由显式根身份解析为三横线或返回按钮，外部直达一级根不会再因宿主初始参数差异误显示返回按钮。
- 大屏仍使用相同的模态抽屉，不恢复 Operit 的 `64dp/280dp` 永久侧栏。
- 真机仍需验证遮罩、圆角、顶部接缝、动画帧、Back、旋转、折叠屏、触摸阻断和流式会话持续性；自动测试与 Debug 构建不能替代这些证据。

## 相关资料

- [被取代的全屏 AI Center 决策](0002_product_shell_and_ai_center_navigation.md)
- [Kiyori 产品壳与导航架构](../architecture/kiyori_product_shell_and_navigation.md)
- [产品壳实施计划](../../TODO/kiyori_product_shell/index.md)
