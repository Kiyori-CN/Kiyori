# 主题架构与清理

## 两个视觉域

Compose 主题解析提供两个明确边界：

- 应用强调域：`KiyoriLightColorScheme`、`KiyoriDarkColorScheme` 和现有自定义颜色派生函数，供 AI、设置、应用壳、悬浮聊天、WebChat、分享图片和辅助 Activity 使用
- 浏览器中性域：`KiyoriBrowserLightColorScheme`、`KiyoriBrowserDarkColorScheme` 和嵌套 `KiyoriBrowserTheme`，供 Browser Home、实际浏览器、全屏搜索、四行菜单和浏览器子页使用

两个域共享背景、surface、outline、error 和 Typography 基线，但 browser primary/secondary 固定为中性色。浏览器主题不读取自定义 primary/secondary，也不建立第二个浏览器状态或 WebView owner。

## 默认与持久化

- `themeMode` 默认 `light`，`useSystemTheme` 默认 `false`
- 新安装和主题重置使用蓝色应用强调方案
- 用户显式选择系统模式时只跟随亮暗，不接入壁纸动态色
- `useCustomColors=true` 时只派生应用强调域的 primary/secondary 与容器；背景和 surface 保持中性
- 现有 DataStore key、角色卡主题格式、背景媒体、字体、聊天气泡和自定义顶栏格式不变
- 浏览器中性域忽略应用自定义颜色，但继续跟随同一亮暗模式

## 组合边界

- 根 `OperitTheme` 提供应用强调域，因此整个 AI 首页和设置页面自然响应 primary/secondary
- `WebSessionBrowserScreen` 在实际浏览器 UI 外层嵌套 `KiyoriBrowserTheme`，同时覆盖 App presentation 与 floating presentation
- `KiyoriAppShell` 的全屏搜索和浏览器子抽屉在同一中性主题边界内呈现；已删除的浏览器设置与搜索记录管理页面不保留主题分支
- Software Home、负一屏、文件管理首页和设置首页需要固定颜色的部分直接使用产品 token，不污染两个 ColorScheme

## 清理范围

- 旧 Purple/Pink、XML purple/teal 和壁纸动态色路径保持删除
- 删除旧设置首页 AI/浏览器双入口与响应式双列枚举；当前固定设置首页只保留通往唯一 AI 设置页的明确来源
- 不保留 inherited 紫色、中性 primary 或旧设置首页的并行实现
- 不改变 WebSession、活动 WebView、AI 会话、ToolPkg、MCP 或 Intent 状态所有权

## 验证

纯函数测试分别固定应用亮暗色、浏览器亮暗色、自定义颜色派生、浏览器隔离和 WCAG 对比度。组合测试固定浏览器搜索边框和 App Shell 子页边界；最终仍需要真机检查亮暗模式、自定义颜色和浮动浏览器。

[IN PROGRESS]
