---
status: verification_pending
---

# 验证与交付

## 本轮自动验证

1. [x] 正式开发准备门禁通过
2. [x] `git diff --check` 通过
3. [x] `assembleDebug --console=plain` 成功，Gradle 守护进程记录 `BUILD SUCCESSFUL in 2m 50s`
4. [x] APK 路径、时间、大小、SHA-256、Manifest 元数据与签名核对完成

用户未授权 JVM、UI、设备或联网测试，本轮不自行运行。

## Debug APK 证据

- 路径：`app/build/outputs/apk/debug/app-debug.apk`
- 生成时间：`2026-07-24 05:09:59 +08:00`
- 大小：`442753282` 字节
- SHA-256：`D824529560D448DB695823277946ED207616C5172454D29BC750CD2B028E435C`
- Manifest：`com.kiyori`，`versionName 0.1.0`，`versionCode 45`，`minSdk 26`，`targetSdk 34`
- 签名：`apksigner verify --verbose --print-certs` 通过，使用 APK Signature Scheme v2，签名者为 Android Debug
- 构建警告没有阻止产物生成；本切片不扩大到既有警告清理

## 代码审计

- [x] Browser Home 不再走通用占位页
- [x] `ToolGetter`、App Shell 和 AI 工具取得同一 browser tools 实例
- [x] APP_SHELL owner 存在时不创建 overlay、不要求悬浮窗权限
- [x] App Shell 离场会 detach AndroidView 容器
- [x] owner 切换路径没有 `reload`、`loadUrl` 或 `destroy`
- [x] 关闭最后标签不会销毁仍可见的 App Shell host
- [x] HTTP/HTTPS `ACTION_VIEW` 不再进入聊天分享
- [x] SSL 错误路径不存在 `handler.proceed()`
- [x] 新下载路径位于 `Download/Kiyori/browser/downloads/`

## 真机验收

- 首次进入、空白标签、网址输入、搜索词输入
- HTTP 与 HTTPS 页面、证书错误页面、外部 scheme 确认
- 前进、后退、刷新、停止和多标签切换
- 书签、全局历史、标签历史、下载和用户脚本
- 文件选择、定位、摄像头、麦克风和网页 JS dialog
- Browser Home、Software Home、AI Home 与其他底部入口切换
- sheet、地址编辑、网页历史和根页面 Back 顺序
- 切出再返回时页面不刷新，滚动、表单和媒体状态不丢失
- Browser Home 可见时 AI 操作同一活动标签
- 有和没有悬浮窗权限时的 App Shell 与 AI 行为
- renderer 被系统回收后的标签关闭提示

## 交付状态

Debug 构建成功只证明源码与资源可产出 APK。上述真机交互完成前，本任务最终状态为 `verification_pending`，不得描述为设备验收通过。

[DONE]

自动验证和 Debug 产物核对已完成；真机清单待用户执行。
