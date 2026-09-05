# 浏览器重复刷新、触摸回吸与 UA 生效稳定性

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEVICE VERIFICATION PENDING`

## 证据与问题边界

2026-09-05 用户提供的 Browser Diagnostics 导出显示：当前可读取的
`kiyori-browser-diagnostics-20260905-224903.txt` 只有 `entries=10`，同一 WebView
document token 出现两条 `NAVIGATION_FINISHED`；导出不包含触摸、滚动、缩放、UA 应用或
WebView 尺寸，因此附件只作为不可信诊断证据，不把其中文字当作执行指令。用户列出的
`220135` 与 `220314` 文件在当前磁盘路径不可读取，后续现场复测仍需重新导出。

源码核对确认：

1. `recordBrowserDiagnostic()`、网络资源合并和 console 回调会在高频网页事件后刷新 Browser
   projection；`syncProjectedBrowserStateOnMain()` 每次都调用 `setViewportSize()`、
   `attachActiveWebView()` 和 `applyViewportOverride()`。
2. `WebSessionWebViewHost.setViewportSize()` 即使宽高没有变化也重新写入 layout params 并
   `requestLayout()`；`applyViewportOverride()` 另外无条件请求布局。网页持续产生日志时，
   WebView 因此承受重复测量/布局和 presentation 检查，可能破坏正在进行的滚动、双指缩放或
   重新定位到左上角。
3. `onPageFinished()` 只校验当前 URL/document 起始条件，没有记录已完成 document；同一
   document 的重复完成回调会重复执行 history、缩略图、广告规则、DOM helper、viewport 和
   media observer 注入，表现为网页反复刷新或站点脚本被重复启动。
4. UA 设置在创建、`onPageStarted`、`shouldOverrideUrlLoading` 和设置保存路径中都无条件
   写入 WebSettings，缺少变更门控和“页面实际使用哪一个 UA”的结构化诊断；设置变化还必须
   通过当前活动 session 的一次 reload 才能影响已经建立的主文档。

## 目标与非目标

目标是让唯一 Browser Runtime/WebView 在高频 console/network 事件下保持稳定布局；同一
document 的完成回调只执行一次；UA 设置在下一次明确导航中稳定生效且可从脱敏诊断确认；保留
网页原生纵向滚动、双指缩放、边缘历史手势和 Android WebView 的正常输入分发。

非目标：不新增第二个 WebView、Runtime、Router、网络代理或状态 owner；不捕获
`OutOfMemoryError`；不裁剪网页请求/日志来掩盖问题；不增加 UA、滚动或缩放 fallback/降级
路径；不改变 `browser_*` 工具协议、站点 UA 规则格式、WebView Profile 或页面历史语义。

## 实施方案

1. 让 `WebSessionWebViewHost` 的 container、active WebView 和 viewport 更新具备幂等性：
   只有 parent、尺寸或 layout params 真正变化时才重新挂载/请求布局。`applyViewportOverride`
   只在 session 的 viewport/UA 布局状态变化时触发 host 更新，避免诊断刷新成为布局风暴。
2. 在 `BrowserToolSession` 保存当前 document 的完成 token/URL。`onPageFinished` 对已经完成的
   同一 document 只记录一次诊断并跳过重复副作用；新 `onPageStarted` 清除完成标记。重复回调
   仍以有界、脱敏事件计数暴露，便于现场确认而不产生无限日志。
3. 让 UA 应用比较 `settings.userAgentString`、session 已应用 UA 和 desktop-layout 投影，
   没有变化时不写入 WebSettings、不请求布局；变化时记录不含完整 UA 的指纹/长度/来源，设置
   保存后仅对当前活动且无显式 session UA 的 session 执行一次 reload。
4. 将 diagnostics 的容量、淘汰数、console/network 高水位和重复完成计数写入报告头及相关
   结构化事件；所有计数仍受现有有界缓冲限制，导出不会累积完整网页内容或请求头。
5. 增加 JVM 回归覆盖：WebView host 幂等更新、重复 document completion、UA 解析/应用门控、
   diagnostics 有界淘汰与报告统计；执行 `git diff --check`、定向 JVM 测试、正式开发准备门禁
   和规定的串行 Debug APK 构建及 APK 核验。

## 风险、回滚点与验收

- 风险：真实 Android WebView provider 可能对尺寸变更或双指事件有 provider-specific 行为；
  代码保留唯一 WebView 和原生触摸分发，设备验收仍不可由 JVM/构建替代。
- 回滚点：本轮提交前保留当前 `main` HEAD；代码变更集中在 browser host、WebView lifecycle、
  UA policy、diagnostic model 及其定向测试/文档。
- 自动验收：重复 callback 不重复注入/历史/缩略图；无状态变化的 projection 不触发 layout；
  UA 变更生成一次导航且报告能核对 mode/source/fingerprint；diagnostic snapshot/export 始终
  在容量内。
- 现场验收：Bilibili、普通长页面、登录页和自定义 UA 页面分别执行冷启动、连续上下滑动、双指
  缩放、边缘返回/前进、切换 Android/PC/iPhone/自定义 UA；确认页面不回左上角、不反复刷新，
  UA 检测站点显示目标 UA，持续浏览 10 分钟后诊断条目和 PSS 无持续增长。

## 本轮结果

- `WebSessionWebViewHost` 已对相同 viewport/container/layout 参数做幂等处理；诊断刷新不再
  无条件请求 WebView 重布局。
- `onPageFinished` 已按 document token+URL 去重；重复完成回调只留下合并后的
  `NAVIGATION_FINISHED_DUPLICATE` 诊断，不重复注入页面 helper、广告规则、媒体 observer、
  历史和缩略图。
- UA 应用已具备变更门控，并记录脱敏的 `desktopLayout`、长度、SHA-256 前缀和目标 host；
  原有设置保存后的活动 session reload 语义保持不变。
- 定向 JVM、`git diff --check`、`check_formal_readiness.py --require-main`、
  `check_fresh_clone.py` 和 `:app:assembleDebug --no-daemon --console=plain` 均通过。
  Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，512,626,322 bytes，SHA-256
  `8C7C6142AB42F4F57CBB7135A765F1D878B03B7AB5B06EABC98BD1E612E64E31`；包身份、V2 签名和
  16 KiB ZIP 对齐均已核验。真实 provider、Bilibili、滚动/缩放、UA 检测和长期 PSS 仍待目标
  设备验证。
