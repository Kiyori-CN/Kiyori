---
status: accepted
date: 2026-07-25
updated: 2026-07-26
---

# 应用蓝色强调与浏览器中性主题边界

## 背景

Kiyori inherited 的 Compose 静态主题曾使用 Material 紫色，Android 12+ 又会采用系统壁纸动态色。第一阶段已删除动态色和旧紫色，并建立稳定的灰白基线，但全局 primary/secondary 完全中性，使 AI 对话、输入动作、设置控件和软件壳选中态缺少清晰的产品强调。

Kiyori 尚未正式发布，不需要保留这套中性 primary/secondary。新的要求同时明确：AI 首页和 AI 对话必须响应全局配色，浏览器 chrome、全屏搜索和浏览器子页不得被全局配色染色。

## 决策

- Kiyori 使用双层主题架构：应用强调主题负责 AI、AI 与系统设置、通用应用控件和软件壳选中态；浏览器中性主题负责所有浏览器 chrome 和浏览器子页
- 亮色应用 primary 使用主题颜色选择器“推荐颜色”第一行第七个 Material Blue 600 `#1E88E5`，onPrimary 使用深色 `#0A1929`；secondary 使用低饱和蓝灰 `#536D79`，onSecondary 使用 `#FFFFFF`
- `#1E88E5` 与 `#0A1929` 的对比度为 `4.821:1`；白色与该蓝色只有 `3.679:1`，因此 primary 上不使用白色普通文字。暗色 primary/secondary 分别使用 `#90CAF9` 与 `#B9CBD4`
- 默认 AI 顶栏继续使用 background 与 onBackground，并保留细分隔线；蓝色进入发送按钮、用户消息容器、活动工具、选择态和设置控件，不形成全宽蓝色头部
- 用户显式保存的 primary/secondary 继续沿用现有 DataStore key 和派生规则，并影响应用强调域；浏览器中性主题不读取这些颜色
- Browser Home、实际网页宿主、普通地址栏、全屏搜索、底栏、窗口页、四行菜单和浏览器子抽屉统一使用固定中性色方案
- 普通地址栏与全屏搜索框使用相同几何、相同内外背景和固定 `#000000` 的 `1dp` 边框；焦点不会改变边框颜色
- Software Home 保留固定五色 `1dp` 搜索框描边和固定明暗蓝色 Search/AI 分段强调，不跟随用户自定义 primary/secondary
- 负一屏、文件管理首页和设置首页按旧版 Kiyori 固定参考提交复刻，保留页面自有灰底、分类渐变和多彩图标，不由应用强调色重着色
- App Shell 底栏选中态只用 primary tint 表达，不绘制胶囊、圆形背景或可见圆形涟漪
- AI 空输入状态的语音动作使用透明容器和 onSurfaceVariant 图标；发送、取消、排队等语义动作继续使用对应实心容器
- 默认 Typography 继续使用系统 Sans 和 Material 3 尺度，字符间距统一为零

## 影响

- `ThemeColorSchemeResolver.kt` 同时提供应用强调方案与浏览器中性方案；现有主题设置持久化格式不变
- 浏览器 UI 在自身组合边界内嵌套中性 `MaterialTheme`，不建立新的浏览器状态所有者，也不改变 WebSession 或活动 WebView
- 主题设置未来从 AI 设置迁到系统设置时，只迁移入口和设置所有权，不重做色板或浏览器隔离
- 设置首页保留固定复刻布局，第一项“AI 设置”进入现有唯一 AI 设置页并使用设置来源返回语义，其余复刻按钮没有导航副作用
- 原浏览器设置页、搜索记录子页、Shell 路由、外部 Intent 和页面专用状态已删除；浏览器菜单第四行设置按钮、原图标与三按钮位置保留为空占位
- 网页自身 CSS 不属于应用主题，不能通过注入改色
- 真机视觉验收仍是完成条件，自动测试和 Debug 构建不能替代

## 相关资料

- [实施计划](../../TODO/kiyori_professional_browser_theme/index.md)
- [蓝色强调与静态页面阶段](../../TODO/kiyori_professional_browser_theme/6_ai_accent_and_static_home_pages.md)
- [AI 主题精调与浏览器设置移除](../../TODO/kiyori_professional_browser_theme/7_ai_theme_settings_and_browser_settings_removal.md)
- [UI 设计来源层级](0003_ui_design_source_hierarchy.md)
- [Browser Home 使用沉浸式浏览器 chrome](0005_browser_home_immersive_chrome.md)
