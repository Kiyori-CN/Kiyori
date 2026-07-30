# 主题状态与个性化边界

## 旧实现问题

旧主题实现把应用模式、全局 primary/secondary、自定义 AppBar、背景媒体、字体、聊天气泡和角色卡
绑定放在同一个 `saveThemeSettings` 与“主题复制”集合中，造成三个系统性问题：

- 用户选择的颜色会改变所有 `MaterialTheme.colorScheme.primary` 消费者
- 角色卡切换会复制或覆盖应用级主题、状态栏、工具栏和字体状态
- 背景图片或视频由 `OperitTheme` 在应用根层绘制，非 AI 页面也会进入透明背景和系统栏分支

隐藏设置入口无法修复这些问题，因为旧状态仍会被主题解析器、工具型 Activity、WebChat 和角色
绑定读取。

## 新状态合同

应用级状态只保留：

- `themeMode`
- `useSystemTheme`
- `statusBarHidden`
- 全局字体与字号

其中颜色方案固定由当前浅深模式选择 `KiyoriLightColorScheme` 或
`KiyoriDarkColorScheme`。系统模式只负责决定浅色或深色，不读取壁纸动态色。

AI 对话局部状态保留：

- 背景图片或视频、透明度、模糊、静音和循环
- 对话样式、气泡颜色与图片、文本颜色、圆角和内边距
- 用户与 AI 头像
- 聊天头部透明、覆盖模式和局部图标颜色
- 输入区样式、透明、悬浮、液态玻璃和水玻璃
- 消息与气泡专属字体、显示信息和角色卡、群组绑定

角色卡和群组的复制、切换、删除与快照解析只遍历这些 AI 局部键。应用主题、全局字体、状态栏、
AppBar 和设置视觉不进入角色前缀。

## 必须删除的旧方案

- `useCustomColors`
- `customPrimaryColor`
- `customSecondaryColor`
- `onColorMode`
- `toolbarTransparent`
- `useCustomAppBarColor`
- `customAppBarColor`
- `forceAppBarContentColor`
- `appBarContentColorMode`
- 主题设置页中的应用主色、次色、前景色模式和 AppBar 颜色入口
- WebChat 快照中的 `use_custom_colors`、顶层 `primary_color` 和 `secondary_color`

聊天头部历史与画中画图标颜色属于 AI 对话局部外观，不属于应用 AppBar 自定义色，可以继续保留。

## 背景迁移

`OperitTheme` 不再创建图片或视频背景播放器，也不再因背景媒体改变应用 Surface 与系统导航栏。
AI 对话根 `AIChatScreen` 在自身内容下方挂载现有 `AppBackgroundLayer`，并继续把
`hasBackgroundImage` 传给聊天消息、输入区和局部玻璃组件。

消息图片导出和 WebChat 继续读取同一 AI 局部背景 preference。它们使用固定 Kiyori 浅深色方案
生成默认气泡与文本颜色，不读取已经删除的全局自定义色。

