# 应用壳、导航与设置契约

适用于首页、首启、AI 抽屉、设置来源与系统栏。详细页面设计见 [产品壳架构](../architecture/kiyori_product_shell_and_navigation.md)，视觉取舍见 [设计来源](../decisions/0003_ui_design_source_hierarchy.md)。

## 根与领域

| 组件 | 职责 |
| --- | --- |
| `KiyoriApplication` | Application 装配入口 |
| `KiyoriApp` / `KiyoriAppShell` | 根组合与产品壳 |
| `KiyoriShellState` | 纯壳状态 |
| `KiyoriPrimaryNavigation` | 五个主目的地投影 |
| `KiyoriSoftwareHome` / `KiyoriBrowserSearch` | 软件首页与全屏网页搜索 |
| `KiyoriAiDrawer` | 唯一模态 AI 抽屉 |
| `integration.operit.navigation` | 路由目录、动态注册、gateway 生命周期和根身份转换 |
| `KiyoriMainPendingRequests` / `KiyoriMainIntentDecoder` | 待处理外部请求与无副作用 Intent 解码 |

MainActivity 保持稳定 Manifest 启动入口、公开 action/extra、Android 生命周期与运行时副作用边界。迁移完成情况由 [架构专项](../../TODO/kiyori_architecture_refactor/index.md) 和机器清单声明，不能从旧里程碑文字推断当前路径。

## 首页与 Back

- 软件首页是三页 Pager 中心，左侧负一屏、右侧 AI 首页；五个主目的地分别保留子栈和滚动位置。
- AI 首页始终显示对话与输入控件。没有模型配置或 API Key 时，在实际请求处报告缺失，配置页作为独立路由打开。
- AI 首页与显式注册的 AI 导航根显示菜单按钮，深层页显示 Back；非 AI 页面没有 AI 抽屉入口。
- 原生根按注册 route ID 判断，ToolPkg 根同时匹配 route ID 与注册参数；栈深、实例前缀或任意参数不能证明根身份。
- Back 先处理弹层与当前子栈；AI 顶层返回 AI 首页，AI 首页和负一屏返回软件首页，软件首页根请求退出确认。
- 保留在组合中的后台 Browser 或设置页必须撤销前台 Back 权限，不能因仍在组合而竞争事件。

## 模态 AI 抽屉

- 快捷行顺序为“扩展 / 工具箱 / 工作流”，徽标分别来自启用包数量、唯一 `AppNavigationModel` 中 `NavigationSurface.TOOLBOX` 数量和工作流数量。
- 对话与记忆是列表入口，对话历史、新建、搜索和删除仍属于 AI 首页原选择器；返回 AI 首页不新建对话、不清空草稿。
- ToolPkg 进入时创建新实例，只有明确 `keepAlive=true` 的路由保留组合 key 与子栈。
- 抽屉打开不平移、缩放、倾斜、圆角化或淡化底层页面；页面继续渲染，但不能接受触摸。
- 宽度：小于 `600dp` 为窗口的 `75%`，`600..839dp` 为 `320dp`，不小于 `840dp` 为 `360dp`；分隔铰链限制在左侧物理区域。
- 抽屉复用共享主题，不恢复独立玻璃、背景或强调色偏好。

## 设置来源与所有权

- 底栏设置、Browser 菜单、AI 抽屉均进入同一个来源保持的设置会话；子页逐层返回，退出会话后回到原网页或原 AI 路由。
- 产品设置持有应用语言、外观、存储、浏览器、下载、播放器、通知、共享权限等跨产品行为。
- AI 助手设置持有模型、路由、提示词、角色、用户偏好、上下文、工具授权、对话策略及外部 HTTP 对话。扩展、Skill、MCP、ToolPkg 和工作流仍为独立 AI 目的地。
- AI 设置从相同来源族再次进入可恢复子栈；AI 抽屉与 Kiyori 设置跨来源进入时打开 AI 设置根，保证返回语义与来源一致。
- 设置详情顶栏状态绑定 screen key；已退出页面不得继续决定新页面第一帧标题。文件管理、权限、用户协议与隐私政策均复用既有页面所有者。

## 首次启动

- `KiyoriMainStartupGateCoordinator` 与 `integration.operit.onboarding` 持有唯一首启流程：四页介绍、协议确认、权限选择；协议正文独立于 Pager。
- 首启 Pager 在页面稳定后保存步骤，一次 fling 最多一页；协议未同意时仅开放前五页，原生手势可向后返回，不使用第二个手势检测器。六页进度顶栏固定高度，导航按钮串行切页；正在处理系统授权时锁定翻页，但可停止队列。
- 设置重看使用独立全屏模态窗口隔离触摸与焦点；每次新进入从第一页开始，关闭时保留设置来源。重看不改写首启完成与步骤偏好；查看协议正文后保留原阅读位置。
- 重看 Pager 只属于当前组合会话，不恢复旧页码；首启继续保存恢复进度。普通按钮只允许当前页发起相邻切换，只有明确点击“跳过介绍”可向前跨页到第五页；滚动期间拒绝导航，按钮外观不随滚动切换禁用配色。固定栏按钮的拖动、折返和多指手势不能形成点击。
- 前四页使用统一眉题（第一页为 `Kiyori`）与 2×2 说明卡，每页四项，说明固定两行短句；宽度不足时按真实字体测量调整字号，保持完整文案。卡片不承担导航，页面正文可纵向滚动。
- 系统授权只处理用户所选条目。返回后刷新权限事实，继续下一项与完成引导均需明确操作；旧代际回调不能更改新队列，停止后保留系统已授予的权限。
- 首启与设置共用 `KiyoriPermissionId` 目录、分组、metadata、快照、摘要与动作。当前目录包含 23 项；逐项选择只包含当前可处理且未完成条目，不授权也可以进入应用。
- 通知仅走集中式 `RequestMultiplePermissions`；独立启动通知协调器已删除。精确闹钟不是首启要求，现有工作流使用 WorkManager，普通闹钟使用 `ACTION_SET_ALARM`。
- 显示正式内容前由 `ChatHistoryManager` 验证当前聊天记录；缺失或失效时创建真实空对话，有效聊天按 `startWithNewChat` 偏好处理。
- 软件首页首帧只观察轻量浏览器窗口投影。浏览器、历史、广告和下载运行时按实际使用启动；动态 ToolPkg 路由等待两个 frame 信号和同一 PackageManager 初始化后发布。
- `PluginLoadingState` 是 MCP 启动进度唯一所有者。只在可见 AI 领域投影进度；隐藏 UI 不取消初始化，退出根组合后撤销显示权限。

## 主题与系统栏

- Kiyori 固定配色与语义色属于 `com.kiyori.design.theme`；偏好、字体和 Glass 由应用主题适配，不能另建颜色或偏好所有者。
- 主操作蓝为 `#1E88E5`，大面积背景与文字保持中性；浏览器动作颜色表仅表达动作身份，不增加全局语义。选中底栏填充 `#FFC153`，天气太阳独立使用 `#C57C00 / #FFD166`。
- 已接受产品契约决定行为，Operit 原版决定通用视觉处理，固定旧 Kiyori 参考决定页面内容结构；参考源不是运行时依赖。
- 每个 Activity 根建立自己的 `KiyoriStatusBarAppearanceScope`；声明按稳定 owner 更新、释放。`KiyoriApplicationSystemBars` 是应用 Window 外观唯一写入者，独立播放器全屏策略另有所有者。
- 页面背景延伸到物理顶边，可见状态栏透明，图标亮度跟随实际前景。抽屉遮罩全屏，面板顶边从状态栏底部开始，仅处理横向与底部安全区。
