# 强制页面缩放与 UA 适配

状态：`IMPLEMENTATION VERIFIED / DEVICE VERIFICATION PENDING`

日期：2026-08-27

范围：Kiyori Android 浏览器的“强制页面缩放”运行时、PC UA/Android UA 视口协作和自动化回归。

## 目标

在强制页面缩放开启时，Android System WebView 的双指手势必须同时支持持续放大和持续缩小。PC UA 页面不能因为 overview 初始适配而从最低比例开始；Android/iPhone 等移动 UA 必须继续遵守页面的响应式布局语义。现有站点级禁用、文字缩放、页面历史、WebSession 复用和 UA 优先级不变。

## 非目标与约束

- 不新增 WebView、手势层、页面缩放状态 owner 或第二份 UA 配置。
- 不更换已存在的 UA 字符串、站点 UA 规则或 `session.customUserAgent` 优先级。
- 不移除 `initial-scale`，避免改变站点首次布局意图；不通过刷新、JS CSS transform、页面重载或 Android VPN 伪造缩放。
- 不引入额外旁路或降级路径；若 WebView/设备对页面自身约束仍有差异，保留真实可观察行为并在设备验收记录。
- Kiyori 当前未发布，旧方案无需做已发布接口兼容迁移；内部 `WebSession` 和持久化设置标识仍保持兼容。

## 证据与根因

1. [Android `WebSettings.setLoadWithOverviewMode`](https://developer.android.com/reference/android/webkit/WebSettings#setLoadWithOverviewMode(boolean)) 定义 overview 为按 WebView 宽度缩出内容；该设置在 `getUseWideViewPort()` 开启且内容宽于控件时生效。
2. [Android `WebSettings.setUseWideViewPort`](https://developer.android.com/reference/android/webkit/WebSettings#setUseWideViewPort(boolean)) 定义 wide viewport 会采用 viewport meta 或宽视口；PC UA 下当前实现开启它以保留桌面页面布局。
3. Chromium `ViewportDescription::Resolve` 与 `PageScaleConstraints::FitToContentsWidth` 的源码显示，页面最小比例会受 min/max viewport 和“视口宽度 / 内容宽度”约束；`user-scalable=yes` 与较大的 maximum scale 不能抵消 overview 造成的最低比例。
4. 当前 `BrowserDisplaySettingsSupport.kt` 只删除 `user-scalable`、`maximum-scale`；当前 `BrowserWebViewSupport.kt` 在 `applySessionUserAgent`/`applyViewportOverride` 对桌面 UA 开启 overview，且页面完成后才注入 viewport 脚本。

## 实施阶段

### M1：策略与脚本合同（DONE）

- 在显示设置策略中定义强制缩放 viewport 的最小比例常量和 desktop overview 判定，形成单一可测试策略。
- 脚本过滤 `minimum-scale`、`maximum-scale`、`user-scalable` 后写入 `user-scalable=yes, minimum-scale=0.1, maximum-scale=10.0`；继续保留 `initial-scale`、其他 viewport 指令、MutationObserver 的可撤销恢复和无 reload 约束。

### M2：WebView/UA 时序接线（DONE）

- `applySessionUserAgent` 在目标 URL 上解析站点级强制缩放状态；PC UA 保持 wide viewport，仅在强制缩放开启时关闭 overview。
- `applyViewportOverride` 与 `applyBrowserDisplaySettingsOnMain/Page` 共用同一策略，覆盖设置切换、站点级开关、导航、重定向和页面完成后的所有路径。
- Android/iPhone UA 保持当前 overview/wide viewport 适配结果；自定义 UA 仅按现有 desktop 判定参与该策略。

### M3：验证与交付（LOCAL DONE / DEVICE PENDING）

- 增加脚本合同测试：minimum-scale 被移除并显式声明 0.1；关闭脚本仍恢复原 viewport；不出现 `location.reload`。
- 增加 desktop overview 策略测试：PC UA 在强制缩放开启时不启用 overview，关闭时恢复；Android UA 不被改变。
- 运行定向 JVM 测试、主代码编译、`git diff --check`、正式开发准备门禁及规定的 `:app:assembleDebug --no-daemon --console=plain`，核验 Debug APK 路径、包身份、签名和 16 KB 对齐。
- 审计最终差异和暂存内容后提交 `main` 并推送 `origin/main`，核对本地、tracking 与远端 ref。

## 影响文件

- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserDisplaySettingsSupport.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserWebViewSupport.kt`
- `app/src/test/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserDisplaySettingsPolicyTest.kt`
- 必要时同步 `CONTEXT.md` 中浏览器 viewport/UA 合同；不修改无关设置或翻译资源。

## 风险与回滚点

- 风险：不同 Android System WebView 版本对 viewport min scale 的计算可能不同；本地 JVM 只能验证策略字符串和布尔接线，不能替代手势现场。
- 风险：PC UA overview 关闭后首帧可能比旧行为更大，这是强制缩放为用户提供缩小区间的必要结果；wide viewport 保持不变以避免移动化布局。
- 回滚点：仅限本专项新增代码/文档的提交；不得回退或覆盖用户已有改动。若设备发现 Android UA 响应式页面退化，应定位具体策略调用链，不添加额外分支。

## 验收矩阵

| 场景 | 期望证据 |
| --- | --- |
| Android UA + 强制缩放开 | 脚本覆盖 viewport；双指可连续放大/缩小；响应式布局仍为移动页面 |
| PC UA + 强制缩放开 | wide viewport 保持；overview 关闭；首帧不是最低概览比例；双指向内继续缩小 |
| PC UA + 强制缩放关 | 恢复原 overview 行为与原网页 viewport 限制 |
| 站点级禁用 | 当前域名脚本关闭且 global 行为不被改变；其他域名仍按全局设置 |
| UA 切换/重定向 | 目标 URL 的 UA 与缩放策略先于页面加载生效；不重复创建 WebView、不产生额外导航 |
| 文档切换/设置切换 | 活动与非活动 WebSession 均同步；关闭强制缩放可恢复网页原始 viewport |

## 当前验收状态

- 定向 JVM、浏览器合同测试、formal readiness、Debug APK、APK 身份/签名/16 KB 对齐：已通过。
- `git diff --check`：已通过；候选 revision Markdown 链接与 fresh-clone 门禁在提交后执行。
- 真实设备、真实 PC UA 页面、Android 响应式页面和连续手势：`verification_pending`。
