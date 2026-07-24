---
status: verification_pending
reference: D:/10_Project/kiyori-android@24a2dfa91f0a4166dc58e5c4732d11861173f766
---

# 浏览器顶栏、全屏搜索、工具抽屉与窗口重构决策

本轮以旧 Kiyori `24a2dfa…` 的 BrowserTopBar、BrowserToolboxSheet、BrowserBottomBar 和 BrowserWindowPage 为布局参考，但不把参考仓库的 X5/WebView 内核能力误认为当前 Kiyori 已具备。

冻结的实现原则：

- 顶栏为返回、搜索框、刷新；搜索框打开全屏搜索覆盖层
- 搜索引擎、搜索记录、网络日志和页面源码必须有真实状态与真实回调
- 工具网格只渲染当前已接通的能力；未接通能力不显示
- 第四按钮复刻普通 WebSession 窗口总览；无痕窗口等待可验证的 WebView profile 隔离
- 人与 AI 继续共用唯一 `StandardBrowserSessionTools` runtime 和活动 WebView
- 本轮只构建 Debug，改动不提交不推送，真机验收保持 `verification_pending`

完整证据与自问自答见 [专项 TODO](../../TODO/kiyori_browser_topbar_search_toolbox_windows/index.md)。

## 实施结果

- 顶栏、全屏搜索、搜索引擎与最近记录已接入 `WebSessionBrowserHost` 和 `WebSessionHistoryStore`
- 工具抽屉已按真实运行时能力接入加书签、书签、历史、下载、插件、UA标识、网络日志、刷新和查看源码
- 第四按钮已复用现有 session 总览、单项关闭、新建和关闭全部动作；无痕窗口及未接通能力没有伪造入口
- `:app:compileDebugKotlin` 与定向 JVM 单测已通过；正式开发准备门禁通过，Debug APK 已构建并完成元数据与 ZIP 16KB 对齐核对，真机验收仍待完成
