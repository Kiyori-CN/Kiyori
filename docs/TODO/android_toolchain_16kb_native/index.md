---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
---

# Android 工具链与 16 KB native 对齐

## 目标

统一本地与 CI 的 Android 构建工具链，并在不改变市场下载逻辑、不做无关重构的前提下，识别和处理 APK 中不支持 16 KB page-size 的 native 依赖。

## 已确认基线

- AGP `8.13.2` 与 Gradle `8.13` 已互相对齐，不因告警机械升级
- Gradle 运行时使用 JDK 21；Java/Kotlin 字节码目标继续为 JVM 17
- compile SDK `36`、target SDK `34`、Build Tools `35.0.0`、CMake `3.22.1`
- 所有源码 native 模块统一 NDK `28.2.13676358`
- Android 官方 16 KB 指南：AGP `8.5.1+`，NDK r28+ 默认生成 16 KB ELF 对齐；预编译依赖仍需单独确认

## 作用域

- Gradle native 模块、根 `gradle.properties`、GitHub Actions 工具链版本
- README、构建/贡献文档和正式开发准备清单
- APK `.so` 对齐审计、可控源码 native 重建、可升级 Maven/AAR 依赖的定向评估

## 非目标

- 不修改市场下载、更新或公告逻辑
- 不更新 lint baseline，不用压制规则掩盖告警
- 不批量升级与 16 KB 无关的依赖，不做架构重构
- 不把第三方预编译库的告警冒充为源码已修复

## 验收

- 本地与 CI 固定同一 JDK/SDK/NDK/CMake 版本
- Kotlin、Gradle、CMake、Lint 告警重新采集并按源码/生成代码/工具链/第三方依赖分类
- APK 中每个 `.so` 均有 `zipalign -c -P 16` 与 ELF program-header 证据
- 可控源码 native 产物在 NDK r28 下重建并通过构建；不可控预编译依赖列入暂缓清单并说明升级入口

## 子任务

- [x] [工具链对齐](1_toolchain_alignment.md)
- [x] [16 KB native 依赖审计与处理](2_native_16kb_dependencies.md)

## 当前边界

- APK 内 arm64 native 已只剩 ffmpeg-kit 的 9 个未对齐 ELF；ffmpeg 源码构建受本机缺少 WSL/ffmpeg-kit 源码阻塞，保留为暂缓项
- Android Lint 的剩余 `Aligned16KB` 还包含不随当前 arm64 APK 打包的 TensorFlow Lite x86_64 变体
- CMake 3.22.1 配置仍报告两次 `CXX5304` SDK XML v4 解析告警；不为形式清零升级 CMake，列入工具链待升级项
