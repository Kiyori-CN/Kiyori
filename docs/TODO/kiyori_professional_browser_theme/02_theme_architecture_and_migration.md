# 主题架构与清理

本文件的旧自定义应用颜色方案已由
[设置 UI、主题边界与现代化配色统一](../kiyori_settings_theme_unification/index.md) 取代。

## 三个视觉域与一个局部层

- 固定应用主题：`KiyoriLightColorScheme`、`KiyoriDarkColorScheme`，只由浅色、深色和跟随系统决定
- 设置视觉：`KiyoriSettingsTheme`，为设置首页、拆分设置根和设置子页提供统一页面、卡片、文字、分隔线、开关与语义图标色
- 浏览器保护色域：`KiyoriBrowserLightColorScheme`、`KiyoriBrowserDarkColorScheme` 和 `KiyoriBrowserTheme`
- AI 对话局部外观：背景、气泡、头像、聊天头部、输入区和局部字体，不能改变前三个视觉域

## 默认与持久化

- `themeMode` 默认 `light`，`useSystemTheme` 默认 `false`
- 用户选择系统模式时只跟随亮暗，不接入壁纸动态色
- 全局自定义 primary/secondary、on-color 和 AppBar 颜色已经删除，不保留派生函数或兼容读取
- 全局字体与隐藏状态栏继续属于应用级显示偏好
- 角色主题前缀只包含 AI 对话局部外观，不包含应用模式、全局字体、状态栏或 AppBar

## 组合边界

- 根 `OperitTheme` 提供固定应用主题，不渲染 AI 背景媒体
- `AppContent` 为 Settings 目的地嵌套 `KiyoriSettingsTheme`
- `AIChatScreen` 在对话根内渲染背景媒体
- `WebSessionBrowserScreen` 与浏览器子页继续嵌套 `KiyoriBrowserTheme`
- 所有边界复用既有状态 owner，不建立第二个路由、WebView、播放器、下载器或 preference

## 验证

纯函数测试固定应用亮暗色、设置浅深色、七种语义图标 tone、浏览器中性色和 WCAG 对比度；
静态扫描固定旧全局颜色标识为零；最终仍需要真机检查浅色、暗色、系统模式和 AI 局部外观。

[IN PROGRESS]
