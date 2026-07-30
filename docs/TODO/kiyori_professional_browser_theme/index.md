---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
---

# Kiyori 专业浏览器主题与固定应用强调色

## 目标

Kiyori 的默认视觉从 inherited Material 动态色和紫色基线，改为稳定、克制、内容优先的专业应用主题。默认亮色使用纯白页面、近黑文字、蓝色应用强调和分层灰阶 surface；错误、警告、在线状态、代码高亮等真实语义继续使用必要颜色。

固定应用强调色统一覆盖 Kiyori App Shell、Operit AI 页面、悬浮聊天、权限弹窗、WebChat 主题快照、
分享图片、恢复/崩溃辅助界面和 Android XML 启动窗口。设置页面使用独立且固定的浅深色设置 token；
Browser Home、浏览器 overlay、全屏搜索和浏览器子页使用固定中性色主题边界。

## 非目标

- 不改变 Browser Runtime、WebSession、活动 WebView 或 AI 工具的状态所有权
- 不向网页注入 CSS，不覆盖网站自身背景和排版
- 不删除 AI 对话局部背景、字体、头像、聊天头部、输入区或聊天气泡设置
- 不改变 AI、浏览器、抽屉和系统 Back 的既有行为；旧版三页面静态复刻明确要求的空按钮除外
- 不引入新的字体文件、图片资产或依赖；Software Home 只恢复既有五色描边和固定明暗蓝色选中效果，不扩展为全局色板
- 不把 Debug 构建、模拟器或静态检查表述为真机视觉验收

## 设计来源

- [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) 提供语义 color role、组件和主题实现原则
- [Fluent 2 Design System](https://fluent.microsoft.com/) 提供中性层级、内容优先和低干扰工具界面的交叉参考
- [WCAG 2.2 Contrast Minimum](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) 规定普通文本至少 `4.5:1`，大文本至少 `3:1`
- [UI 设计来源层级](../../doc-src/decisions/0003_ui_design_source_hierarchy.md) 继续约束组件、排版、形状和交互密度
- [专业浏览器灰白默认主题](../../doc-src/decisions/0007_professional_browser_theme.md) 定义本轮 Kiyori 产品级视觉差异

官方资料定义设计原则，不替 Kiyori 指定最终十六进制色值。最终 token 由当前 Compose 使用面、文字对比度、系统栏行为、AI 长文本阅读和浏览器 chrome 层级共同决定。

## 文档树

```text
kiyori_professional_browser_theme/
	index.md
	1_visual_language_and_palette.md
	2_theme_architecture_and_migration.md
	3_ai_browser_shell_integration.md
	4_validation_and_device_acceptance.md
	5_home_browser_control_alignment.md
	6_ai_accent_and_static_home_pages.md
	7_ai_theme_settings_and_browser_settings_removal.md
```

## 实施顺序

1. 固定亮色、暗色和 Typography token，建立单一 Compose 主题解析入口
2. 删除动态壁纸取色、旧紫色静态色板和辅助主题中的 inherited 紫色默认值
3. 修改 AI 顶栏的 surface/content 配对、活动工具状态和分隔层级
4. 统一 Software Home 的默认 surface；按后续产品迭代恢复其既有五色描边与固定明暗蓝色选中效果
5. 统一浏览器搜索、反馈、标签和 chrome 的中性色角色，同时保持网页内容边界
6. 同步 Android XML 启动主题、WebChat、悬浮窗、权限弹窗、分享图片和辅助 Activity
7. 运行对比度单元测试、现有相关 JVM 测试、静态检查与 Debug APK 构建
8. 保留真机视觉验收，覆盖亮色、暗色、系统模式、设置浅深色和 AI 局部背景媒体
9. 统一浏览器普通顶栏与全屏搜索几何，并按旧版 Kiyori 的描边 Vector 与紧凑几何统一 App Shell、浏览器底栏和浏览器四行菜单
10. 建立蓝色应用强调域与浏览器中性保护域，修正底栏和 AI 语音动作，并静态复刻负一屏、文件管理首页和设置首页
11. 精调 AI 默认强调色与文件/设置首页，恢复唯一 AI 设置入口，并彻底移除待重做的浏览器设置页面和专用逻辑
12. 删除用户对全应用颜色的控制，建立固定设置视觉与 AI 对话局部个性化边界

## 完成条件

- 亮色默认背景为 `#FFFFFF`，主文字为 `#202124`，应用 primary 为 `#1E88E5`，secondary 为 `#536D79`
- 新安装和重置主题默认使用 Kiyori 亮色，不由壁纸颜色改变
- 用户显式选择系统模式时，只跟随亮暗模式，不接入壁纸动态色
- AI 顶栏与页面同底，黑色内容清晰，活动状态不依赖低对比度 tint
- Browser Home 顶栏、底栏、空白区和 WebView 宿主背景连续一致
- Browser Home、全屏搜索、四行菜单和浏览器子页不读取应用 primary/secondary，两个搜索框始终使用黑色边框
- 所有普通文字/背景组合达到 `4.5:1`，大文字达到 `3:1`
- 旧 `Purple*`、XML purple/teal 主题资源不再承担默认品牌视觉；首页仅保留产品合同明确指定的渐变描边和蓝色模式强调
- 相关测试和 `:app:assembleDebug` 通过，APK 路径、大小、时间和 SHA-256 已核验
- 真机验收未完成时，文档和任务状态保持 `verification_pending`

## 本轮实施结果

- [DONE] 单一 Compose 灰白/灰黑 ColorScheme、固定亮色默认和显式系统亮暗已实现
- [DONE] AI 顶栏、活动工具、Software Home、浏览器宿主、搜索反馈、Utility、Floating、WebChat resolver、分享图片和 XML 启动主题已统一
- [DONE] 主题精确值、WCAG 对比度与 Typography 契约测试以及相关 Shell/Browser 测试共 `45/45` 通过
- [DONE] `:app:assembleDebug` 通过；APK 为 `449494162` 字节，SHA-256 `5E83D22EA05FF147F08298D0B032D275884000EF189A4F21BD954B47D9C2ED82`
- [DONE] 首页彩色强调恢复、App Shell/浏览器底栏对齐和两个顶部搜索框几何统一
- [DONE] 三处产品 chrome 已统一为 Kiyori 自有空心描边 Vector；菜单已删除固定高度和五槽空位，按旧版紧凑四行结构排列
- [DONE] 图标与四行菜单迭代的聚焦测试 `14/14`、`git diff --check`、正式准备门禁和 Debug 构建通过
- [DONE] 最新 APK 为 `449500501` 字节，SHA-256 `88779DDF0C88C2D55C1B94EACC407334D5438531B7A289BBB753412A9B4158C3`，包名 `com.kiyori`，版本 `45 / 0.1.0`，V2 Debug 签名与 16 KB ZIP 对齐通过
- [DONE] 蓝色应用强调域、浏览器中性保护域、AI 语音按钮和旧版三页面静态复刻已按 [第六阶段](6_ai_accent_and_static_home_pages.md) 实施；最终聚焦测试 `50/50`、`git diff --check`、正式准备门禁和 Debug 构建通过
- [DONE] 最新 APK 为 `449500158` 字节，SHA-256 `B7DE438B955CEC01962AEFCE08B90E07C038DDED672C97ED1AAFCF80A6384309`，包名 `com.kiyori`，版本 `45 / 0.1.0`，V2 Debug 签名与 16 KB ZIP 对齐通过
- [DONE] [第七阶段](7_ai_theme_settings_and_browser_settings_removal.md)：AI 色板精调、真实存储容量、AI 设置入口和浏览器设置实现移除已完成；第四行设置按钮原样保留为空占位
- [DONE] 第七阶段聚焦 JVM 测试 `37/37`、`git diff --check`、正式开发准备门禁和 Debug 构建通过
- [DONE] 最新 APK 为 `449485670` 字节，SHA-256 `F33D327D840F4D45BDE5167A1A2FCCC8E4F393A3CCE643D004C5AA1DFF3C6B9E`，包名 `com.kiyori`，版本 `45 / 0.1.0`，V2 Debug 签名与 16 KB ZIP 对齐通过
- [DONE-local] [设置 UI、主题边界与现代化配色统一](../kiyori_settings_theme_unification/index.md)：固定应用主题、设置语义色板和 AI 局部个性化边界已完成本地实施与构建验收
- [ ] 真机亮色、暗色、系统模式、设置页面与 AI 局部个性化视觉矩阵仍待验收
