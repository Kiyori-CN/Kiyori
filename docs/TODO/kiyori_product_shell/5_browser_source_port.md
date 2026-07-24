---
status: first_slice_verification_pending
source_repository: https://github.com/Kiyori-CN/kiyori-android
source_commit: 24a2dfa91f0a4166dc58e5c4732d11861173f766
---

# 浏览器 source-port

## 目标

以旧 `kiyori-android` 浏览器为页面、状态和交互来源，把浏览器能力迁入当前 Kiyori 产品壳，并用 Operit 原版视觉体系统一表达。迁移不是把旧工程作为依赖，也不是只复制界面截图。

## 首轮源库存

- `browser/ui/BrowserActivity.kt`
- `browser/ui/BrowserProcessLaunchPolicy.kt`
- `browser/ui/BrowserScreen.kt`
- `browser/ui/BrowserTopBar.kt`
- `browser/data/BrowserWindowRepository.kt`
- `browser/web/BrowserWebViewController.kt`
- `browser/domain/`、`browser/data/`、`browser/security/`、`browser/playback/`
- 历史、书签、搜索记录、窗口预览、下载、网页权限和媒体识别相关页面

## 已确认的源行为

- 软件首页“搜索”调用 `BrowserActivity.start(openSearch = true)`
- `openSearch` 显示完整的 `BrowserUrlDropdownOverlay`
- 搜索页包含地址输入、搜索引擎和搜索记录
- 软件首页“AI”按钮不进入浏览器，而是进入 AI 首页
- 浏览器冷启动会激活或创建首页窗口，同时保留既有普通窗口
- Activity 恢复和播放器返回不额外创建首页窗口
- 旧浏览器使用 X5/TBS；当前 Kiyori WebSession 使用 `android.webkit.WebView`，首期不会并行接入两种内核

## 首期接入决策

- 先复用当前 WebSession UI 和唯一 session registry，把 Browser Home 接入 App Shell
- App Shell 与 overlay 只是 presentation owner，同一 WebView 通过容器转挂保持页面
- Browser Home 可见时 AI 直接操作共享标签，不要求悬浮窗权限
- 地址输入支持网址和网页搜索词，外部 HTTP/HTTPS 进入共享浏览器
- 窗口持久化、预览、隐私窗口、媒体嗅探和播放器交接留给后续可追踪 source-port

实施细节见 [浏览器首页与 WebSession 共用计划](../kiyori_browser_home_websession/index.md)。

## 迁移要求

1. 为每个源文件记录目标文件、保留行为和刻意差异。
2. 保留浏览窗口稳定 ID、活动窗口、无痕状态和页面预览语义。
3. 保留网址规范化、搜索引擎、网页权限、外部 scheme 和下载安全策略。
4. 把旧硬编码主题替换为 Operit 原版主题 token 与共用组件。
5. 接入 Kiyori App Shell、Back 契约和窗口自适应状态。
6. 给浏览器状态与命令建立 Kiyori Capability API，不让 AI 直接调用页面组件。
7. 对照源行为完成后，删除不属于当前仓库边界的旧宿主代码。

## 待确认

- 软件首页进入网页搜索时，是否完整保留旧活动窗口激活规则
- 浏览器首页作为五个根页面之一时，如何与全屏网页内容页切换
- 浏览器搜索记录与普通浏览历史的展示边界
- Android System WebView 长期内核的 provider 版本、调试与安全诊断页面

## 验收

- 每项迁移能力都有源提交、源文件、目标文件和差异说明
- 首页搜索、浏览器地址栏、历史、书签和窗口管理行为通过合同测试
- 冷启动、Activity 恢复、播放器返回和外部 URL 不产生错误窗口
- 浏览器 UI 与 Operit AI 页面使用同一视觉 token 和组件语言
- 手机、平板和折叠屏上页面状态保持一致
- 真机网页、权限、下载、媒体识别和 Back 验收完成前保持 `verification_pending`
