---
status: accepted
date: 2026-07-24
---

# Browser Home 使用沉浸式浏览器 chrome

## 背景

Browser Home 首期把现有 WebSession UI 接入 Kiyori App Shell，同时保留 Kiyori 五项全局底栏。该结构验证了 APP_SHELL 与 OVERLAY 共用 Browser Runtime 的可行性，但在页面底部形成浏览器工具栏和产品全局底栏两层导航，压缩网页空间，也与旧 Kiyori 的浏览器信息架构不一致。

Kiyori 尚未正式发布，因此不需要为首期临时界面保留并行入口。共享 Browser Runtime、AI 工具和 Android WebView 合同已经通过用户冒烟测试，UI 重构必须建立在这条唯一运行时路径上。

## 决策

- `PrimaryDestination.BROWSER_HOME` 仍是 App Shell 根目的地，但呈现为沉浸式浏览器根。
- Browser Home 隐藏 Kiyori 五项全局底栏并删除对应底部高度预留。
- 浏览器专属底栏固定为后退、前进、主页、标签页和工具箱。
- 标签页打开浏览器内全屏标签总览；工具箱和历史、收藏、下载、Userscripts 使用浏览器底部抽屉。
- 浏览器 UI 主要采用 `kiyori-android@24a2dfa9` 的布局和信息架构，视觉使用 Operit 原版主题。
- 完整全屏搜索页、搜索记录、无痕窗口、窗口预览、X5/TBS、媒体嗅探和播放器交接不进入本决策的实现范围。
- App Shell、Browser Home、overlay 和 AI 工具继续共享 `StandardBrowserSessionTools`、活动 WebView、Cookie、历史、收藏、下载和 Userscripts。
- 标签总览和抽屉是 presentation 瞬态状态，不能成为业务 owner，也不能触发 WebView reload 或重建。

## 影响

- 用户从 Browser Home 通过系统 Back 或地址栏最小化返回 Software Home，再使用 Kiyori 全局导航。
- Browser Home 获得更大的网页可视高度，底部只保留一个导航层。
- 新建标签移到标签总览，底栏中央恢复为浏览器主页。
- 原纵向菜单、列表式标签 sheet 和旧底栏在替换完成后删除。
- Browser Home 与 overlay 使用同一套 browser chrome；UI 变化不会形成第二套 AI 浏览器。

## 相关资料

- [浏览器沉浸式 UI 重构](../../TODO/kiyori_browser_ui_refactor/index.md)
- [浏览器首页与 WebSession 共用计划](../../TODO/kiyori_browser_home_websession/index.md)
- [Kiyori 产品壳与导航架构](../architecture/kiyori_product_shell_and_navigation.md)
