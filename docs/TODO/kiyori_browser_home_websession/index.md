---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
baseline: b9a1255ec0e4426736527a49e4f5932369692bd3
legacy_design_reference: 24a2dfa91f0a4166dc58e5c4732d11861173f766
---

# 浏览器首页与 WebSession 共用计划

## 当前情况

- 底部第二项已经对应 `PrimaryDestination.BROWSER_HOME`，但当前只显示通用占位页
- 现有 WebSession 具备标签、导航、历史、书签、下载、用户脚本、网页权限、文件选择与 AI 页面自动化
- 浏览器会话、活动标签和 WebView 位于 `StandardBrowserSessionTools` 的进程级状态，展示只接入 `TYPE_APPLICATION_OVERLAY`
- AI 工具获取器每次创建新的 `StandardBrowserSessionTools`，但其 companion 状态和宿主又跨实例共享
- 外部 HTTP/HTTPS `ACTION_VIEW` 已注册到 `MainActivity`，当前却被作为分享文本交给 AI 聊天
- 浏览器内核是设备提供的 `android.webkit.WebView`，不是旧 Kiyori 使用的 X5/TBS

## 本轮目标

复用现有 WebSession 浏览器 UI，在 Kiyori App Shell 中实现可正常浏览的 Browser Home。App Shell 和悬浮窗只作为两个 presentation，不能各自拥有浏览器状态。用户与 AI 操作同一标签集合、同一个活动 WebView 和同一组持久化数据。

## 首期范围

- 建立共享 `StandardBrowserSessionTools` 实例和明确的 presentation owner
- 支持 WebView 在 App Shell 与 overlay 容器之间无导航副作用地转挂
- 接入 Browser Home、Shell 底栏、浏览器 Back 与最小化语义
- 首次进入创建空白标签，地址栏同时接受网址和网页搜索词
- 浏览器首页可见时允许 AI 操作共享标签，不要求悬浮窗权限
- 外部 HTTP/HTTPS 打开请求进入共享浏览器
- 修正 SSL 错误无条件继续和浏览器下载目录所有权
- 构建 Debug APK，保留未提交工作树供真机验收

## 非目标

- 不重画现有 WebSession 浏览器 UI
- 不引入 X5/TBS、GeckoView 或独立 Chromium
- 不在本轮迁移旧 Kiyori 的窗口预览、隐私窗口、媒体嗅探或播放器
- 不复制 history、bookmark、download、userscript 或 Cookie 状态
- 不提交、推送、安装、发布或运行未授权测试

## 工作分解

```text
kiyori_browser_home_websession/
	index.md
	1_runtime_and_directory_architecture.md
	2_ui_navigation_and_source_port.md
	3_validation_and_handoff.md
```

1. [运行时与目录架构](1_runtime_and_directory_architecture.md)
2. [界面、导航与 source-port](2_ui_navigation_and_source_port.md)
3. [验证与交付](3_validation_and_handoff.md)

## 完成定义

- Browser Home 不再显示占位页
- 人与 AI 不创建平行浏览器，浏览器首页可见时 AI 可直接操作当前标签
- 切换底部入口后 WebView 不重建、不刷新，返回 Browser Home 保留页面
- 外部 URL、Back、标签、历史、书签、下载和用户脚本走同一运行时
- `assembleDebug` 通过并核对最新 APK
- 真机验收完成前状态保持 `verification_pending`

## 实施结果

- Browser Home 已替换通用占位接线并复用现有 WebSession UI
- `StandardBrowserSessionTools` 已建立应用级共享实例，App Shell 与 AI 工具使用同一 session registry
- 活动 WebView 已支持 `APP_SHELL` 与 `OVERLAY` owner 间转挂，owner 切换不执行网页导航或销毁
- 外部 HTTP/HTTPS、地址解析、浏览器 Back、下载目录和 SSL 取消策略已按本计划接入
- 2026-07-24 Debug 构建成功，APK 元数据、SHA-256 与 v2 签名验证通过
- 真机 WebView、权限、转挂无刷新和人与 AI 同标签交互仍待用户验收

[DONE]

首期实现与本地 Debug 构建已完成；任务保持 `verification_pending`，不代表真机验收通过。
