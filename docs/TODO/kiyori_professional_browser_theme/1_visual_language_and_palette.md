# 视觉语言与色板

本文件记录第一阶段采用的色板。当前默认应用强调色已由[第七阶段](7_ai_theme_settings_and_browser_settings_removal.md)取代为 `#1E88E5 / #536D79`；下表保留为阶段历史，不再代表当前默认值。

## 视觉目标

默认主题应像成熟的 AI 浏览器，而不是营销页面或彩色工具箱。背景、长文本和大面积 surface 保持中性，蓝色承担主动作、选择和用户消息；蓝、绿、紫、橙、红、青、粉的共享语义色只用于小面积图标、状态和边界。浏览器 chrome 继续以中性色承载网页，不读取用户颜色。

## 应用亮色色板

| Color role | 色值 | 责任 |
| --- | --- | --- |
| `primary` | `#2563EB` | AI 发送、主要动作、选中图标 |
| `onPrimary` | `#FFFFFF` | 主按钮内容 |
| `primaryContainer` | `#DCE8FF` | 用户消息、活动工具、明确选中 |
| `onPrimaryContainer` | `#132F56` | 浅蓝容器内容 |
| `secondary` | `#4F6B95` | 次级蓝灰动作 |
| `onSecondary` | `#FFFFFF` | 次级实心按钮内容 |
| `secondaryContainer` | `#E7EEF8` | 次级选择和设置控件 |
| `onSecondaryContainer` | `#233A5C` | 次级容器内容 |
| `tertiary` | `#3C4043` | 第三级中性强调 |
| `background` / `surface` | `#FFFFFF` | 页面和 AI 顶栏 |
| `surfaceContainerLow` | `#FAFAFA` | 低层卡片与普通抽屉项 |
| `surfaceContainer` | `#F7F7F7` | 设置组与面板 |
| `surfaceContainerHigh` | `#F1F3F4` | 输入框与强调面板 |
| `surfaceContainerHighest` | `#E8EAED` | 最高中性层 |
| `onBackground` / `onSurface` | `#202124` | 主文字与图标 |
| `onSurfaceVariant` | `#5F6368` | 说明、时间和次级信息 |
| `outline` | `#9AA0A6` | 强边框和可交互轮廓 |
| `outlineVariant` | `#DADCE0` | 顶栏底线和细边框 |
| `surfaceTint` | 透明 | 禁止 elevation 自动染色 |

对比度：`#FFFFFF` 对 `#2563EB` 为 `5.169:1`，`#FFFFFF` 对 `#4F6B95` 为 `5.427:1`，`#132F56` 对 `#DCE8FF` 为 `10.856:1`，`#233A5C` 对 `#E7EEF8` 为 `9.820:1`。

## 应用暗色色板

暗色背景保持近黑，蓝色提高明度，避免在深色 surface 上失去动作层级。

| Color role | 色值 |
| --- | --- |
| `primary` / `onPrimary` | `#AFC6FF` / `#0E2D61` |
| `primaryContainer` / `onPrimaryContainer` | `#184A9A` / `#DCE8FF` |
| `secondary` / `onSecondary` | `#C3D0E4` / `#24354C` |
| `secondaryContainer` / `onSecondaryContainer` | `#354968` / `#E7EEF8` |
| `background` / `surface` | `#121212` |
| `surfaceContainerLow` | `#1A1A1A` |
| `surfaceContainer` | `#202124` |
| `surfaceContainerHigh` | `#282A2D` |
| `surfaceContainerHighest` | `#303134` |
| `onBackground` / `onSurface` | `#E8EAED` |
| `onSurfaceVariant` | `#BDC1C6` |
| `outline` / `outlineVariant` | `#9AA0A6` / `#3C4043` |

四组蓝色暗色内容配对的对比度均高于 `6.8:1`。

## 浏览器中性色板

浏览器亮暗色板沿用上一阶段的中性角色：亮色 primary `#202124`、secondary `#5F6368`、背景 `#FFFFFF`；暗色 primary `#F1F3F4`、secondary `#BDC1C6`、背景 `#121212`。浏览器搜索框边框是固定 `#000000`，不由主题焦点或自定义颜色改变。

## 固定页面色板

Software Home 的五色搜索框和固定蓝色分段强调属于品牌装饰。负一屏保留数据卡和快捷工具结构，
但页面、卡片、文字和语义图标随固定浅深主题变化；文件管理首页保留其产品装饰，设置首页使用
`KiyoriSettingsTheme`。这些页面都不读取用户 primary/secondary。

## 语义色与排版

- 错误、破坏性动作、联网状态、警告、代码高亮、Diff、日志和图表保留必要功能色
- 继续使用 Android 系统默认 Sans，Material 3 所有 Typography role 的字符间距为 `0sp`
- AI 正文保持 `16sp / 24sp` 的长文本尺度；紧凑工具栏和浏览器地址使用现有 semantic typography
- 不引入字体文件，不使用大面积蓝色头部或背景制造品牌差异

## 自问自答

### 蓝色会不会破坏专业黑白基线

不会。蓝色只出现在需要交互优先级的局部控件，背景、长文本、顶栏和卡片仍由中性色承担。secondary 采用蓝灰而不是另一种高饱和蓝，避免一色铺满。

### 为什么 AI 顶栏不是蓝色

全宽蓝色顶栏会割裂长对话并制造旧式工具栏感。白底、近黑内容和细分隔线更安静，蓝色发送按钮和活动状态已经足以建立识别。

### 用户自定义颜色影响哪里

Kiyori 不再提供用户对全应用颜色的控制。用户只可在 AI 对话中局部自定义背景、气泡、头像、
聊天头部、输入区和局部字体；设置、应用壳、包管理、权限、工作流和浏览器不读取这些颜色。

[DONE]
