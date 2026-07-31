---
fork: https://github.com/Kiyori-CN/Kiyori
status: verification_pending
---

# Kiyori 启动性能优化

> 架构迁移说明：M-01 已在原包内把当前类改名为
> `com.ai.assistance.operit.core.application.KiyoriApplication`。这次纯改名没有改变本文记录的
> 首帧、初始化顺序、幂等、前台服务或性能结论。

## 原本状况

Android 12 及以上会为 Launcher Activity 强制创建系统启动窗口。当前 `Theme.Operit` 只指定背景色，没有指定启动图标，因此系统直接使用自适应 Launcher 图标并放大显示。该窗口一直保留到应用首帧绘制。

`MainActivity.onCreate` 在 `setContent` 前同步执行完整的 `KiyoriApplication.initializeMainApplication`。这条链路除首屏必需状态外，还包含 WorkManager、后台服务职责、PDFBox、编辑器语言、缓存池、调度器和预热任务，使系统启动窗口的可见时间被非首屏工作延长。

## 目标

- 系统启动窗口只绘制与应用主题一致的纯色背景，不显示 Kiyori 大图标
- 首屏必需状态完成后立即挂载 Compose 内容
- 非首屏初始化在首帧提交后转移到后台线程
- 插件加载、权限检查和其他依赖完整运行时的工作必须等待后台初始化完成
- 不增加自定义启动页、加载页、旧流程兼容分支或回退逻辑

## 作用域

```text
kiyori_startup_performance/
	index.md
	1_starting_window_and_first_frame.md
	2_runtime_initialization.md
	3_validation.md
```

实现范围限定在启动主题、`MainActivity` 启动顺序、`KiyoriApplication` 初始化阶段和相称的验证文档。现有下载、终端、浏览器与构建脚本改动不属于本任务。

## 验收

- 浅色与深色主题均使用透明系统 Splash 图标和零图标动画时长
- `setAppContent` 不再等待完整应用初始化
- 完整应用初始化仍保持幂等，前台服务与悬浮服务入口继续可同步确保运行时就绪
- 正式开发准备检查、差异检查和 Debug APK 构建通过
- 真机冷启动不再出现全屏大图标；具体首帧耗时由真机复测确认

## 当前状态

实现与本地自动验证已经完成。Debug APK 已确认包含透明 Splash 资源和零动画时长，启动链路也已把完整运行时初始化移到首帧之后。由于本轮未操作设备，浅色/深色冷启动的视觉结果和真实首帧耗时仍为 `verification_pending`。
