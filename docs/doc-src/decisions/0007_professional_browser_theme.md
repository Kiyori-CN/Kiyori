---
status: accepted
date: 2026-07-25
updated: 2026-08-17
---

# 固定 Kiyori 主题、设置视觉与浏览器保护色域

## 背景

Kiyori inherited 的 Compose 主题曾允许用户同时修改应用 primary/secondary、顶栏颜色和前景色，
背景媒体也在根主题中覆盖整个应用。这使一个 AI 对话外观选择可以改变设置、工具页和应用壳，
同时让浅深色可读性、页面品牌一致性和角色主题所有权难以验证。

Kiyori 尚未正式发布，因此本轮不保留旧全局自定义颜色方案或兼容分支。产品仍保留浅色、深色和
跟随系统的完整主题系统；AI 对话保留背景、气泡、头像、聊天头部与输入区的局部个性化。

## 决策

- Kiyori 使用三个明确视觉域：固定应用主题、共享语义色层和浏览器中性保护色域；AI 对话外观是应用主题之上的局部内容层，不是第四套应用 ColorScheme
- 应用主题只由 `themeMode` 与 `useSystemTheme` 决定；浅色使用固定 `KiyoriLightColorScheme`，暗色使用固定 `KiyoriDarkColorScheme`
- 亮色应用 primary 使用 Material Blue 600 `#1E88E5`，onPrimary 使用深色 `#0A1929`；secondary 使用低饱和蓝灰 `#536D79`，onSecondary 使用 `#FFFFFF`
- `#1E88E5` 与 `#0A1929` 的对比度为 `4.821:1`；白色与该蓝色只有 `3.679:1`，因此 primary 上不使用白色普通文字。暗色 primary/secondary 分别使用 `#90CAF9` 与 `#B9CBD4`
- 默认 AI 顶栏继续使用 background 与 onBackground，并保留细分隔线；蓝色进入发送按钮、用户消息容器、活动工具、选择态和设置控件，不形成全宽蓝色头部
- `KiyoriSemanticTone` 为设置详情、应用壳、AI 支持页和浏览器内容工具提供蓝、绿、紫、橙、红、青和粉色的固定浅深主题图标容器；设置首页的 12 个固定入口由 `KiyoriSettingsHomeIconPalette` 提供 12 组互不重复且与功能对应的浅深色图标容器；设置页面继续由 `KiyoriSettingsTheme` 提供亮色灰白/白卡和暗色近黑/分层深灰表面
- 删除全局自定义 primary/secondary、on-color、透明工具栏、自定义 AppBar 和强制 AppBar 前景色的 UI、DataStore 消费、角色主题绑定与 WebChat 顶层字段，不保留运行时回退
- AI 背景媒体只在 `AIChatScreen` 内渲染；气泡、头像、聊天头部、输入区和局部字体继续由现有 AI 外观偏好及角色绑定拥有
- Browser Home、实际网页宿主、普通地址栏、全屏搜索、底栏、窗口页、菜单和浏览器子抽屉使用固定中性色表面；菜单工具、内容对象、状态和弹窗标题可以使用小面积语义色，不改变 chrome 或网页
- 普通地址栏与全屏搜索框使用相同几何、相同内外背景和固定 `#000000` 的 `1dp` 边框；焦点不会改变边框颜色
- Software Home 保留固定五色 `1dp` 搜索框描边和固定明暗蓝色 Search/AI 分段强调
- 负一屏保留产品合同指定的数据卡和快捷工具结构，但页面、卡片、文字与图标完整适配浅深主题；文件管理首页继续保留其产品装饰；设置首页使用四组各三项的 `3/3/3/3` 信息密度并使用统一设置 token 与语义彩色图标
- App Shell 底栏未选中态保留原空心 Vector；选中态不绘制胶囊、圆形背景、蓝色描边或可见圆形涟漪，而是使用精确 `#FFC153` 的封闭填充层和当前页面背景色内部细节。各图标按可见边界补偿到原图标视觉尺寸，设置入口不重绘外缘；每次点击含重复点击都会从较小填充态连续弹性放大到最终尺寸，前四项使用更低阻尼扩大黄色峰值，设置入口保持原峰值
- AI 首页顶栏浏览器、终端、工作区动作固定使用蓝、青、紫语义色；终端与工作区仅在活动时增加低饱和同色容器
- 软件首页天气图标按天气语义着色，晴天独立使用浅色 `#C57C00`、深色 `#FFD166`，权限和数据异常使用红色或橙色
- AI 空输入状态的语音动作使用透明容器和 onSurfaceVariant 图标；发送、取消、排队等语义动作继续使用对应实心容器
- 默认 Typography 继续使用系统 Sans 和 Material 3 尺度，字符间距统一为零

## 影响

- `ThemeColorSchemeResolver.kt` 只解析固定应用浅深方案，同时继续提供浏览器中性方案；旧全局颜色 key 不再有源码消费者
- 浏览器 UI 在自身组合边界内嵌套中性 `MaterialTheme`，不建立新的浏览器状态所有者，也不改变 WebSession 或活动 WebView
- 共享语义色由 `KiyoriSemanticTheme.kt` 的纯浅深色表解析，不读取 preference，不建立新的状态 owner；浏览器只在中性表面上消费小面积图标和状态色
- 主题设置已经从原综合 AI 设置迁入“界面定制”；“主题”只管理应用模式、状态栏可见性和全局字体，“AI 背景 / 对话 / 输入 / AI 界面”只管理 AI 局部外观
- 设置首页当前使用四组各三项的 `3/3/3/3` 密度，依次为“我的账号 / AI助手 / 小程序”、“网页浏览器 / 文件下载器 / 文件管理器”、“视频播放器 / 音乐播放器 / 文档阅读器”和“界面定制 / 数据备份 / 更多功能”；12 个入口的图标和色彩身份均互不重复。账号、AI、浏览器、播放器、下载器、界面、数据和更多功能入口连接现有 owner；AI 根页内部提供独立的“文本转语音 / 语音转文本”子项，广告拦截器位于“网页浏览器 → 内容过滤”，其余未实现入口没有导航副作用
- 小程序只为底部第三个小程序产品域保留空入口，不连接 AI 包管理、ToolPkg、脚本包或插件市场
- 浏览器菜单第四行设置按钮使用通用“设置”文案并打开来源保持型设置首页；该页面沿用当前浏览器 owner、隐藏软件首页底栏，并与 AI 左抽屉及底部设置共用 `KiyoriSettingsNavigationState` 和 capability-level `KiyoriSettingsRoute`。设置详情按 route stack 逐级返回，Browser/AI 来源在设置首页关闭后恢复原 Browser Home/WebSession 或原 AI 页面/路由栈
- 网页自身 CSS 不属于应用主题，不能通过注入改色
- 真机视觉验收仍是完成条件，自动测试和 Debug 构建不能替代

## 相关资料

- [实施计划](../../TODO/kiyori_professional_browser_theme/index.md)
- [蓝色强调与静态页面阶段](../../TODO/kiyori_professional_browser_theme/6_ai_accent_and_static_home_pages.md)
- [AI 主题精调与浏览器设置移除](../../TODO/kiyori_professional_browser_theme/7_ai_theme_settings_and_browser_settings_removal.md)
- [UI 设计来源层级](0003_ui_design_source_hierarchy.md)
- [Browser Home 使用沉浸式浏览器 chrome](0005_browser_home_immersive_chrome.md)
