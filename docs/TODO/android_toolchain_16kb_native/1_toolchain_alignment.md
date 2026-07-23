# 1. 工具链对齐

## 采用版本

| 项目 | 版本 | 依据 |
| --- | --- | --- |
| Gradle | 8.13 | AGP 8.13.2 兼容矩阵 |
| Android Gradle Plugin | 8.13.2 | 当前版本已满足 AGP 8.5.1+ |
| JDK（运行 Gradle） | Temurin 21 | 当前 CI 与本机实际运行版本 |
| JVM target | 17 | 保持现有 Java/Kotlin 字节码兼容边界 |
| compile SDK | 36 | 当前应用配置 |
| target SDK | 34 | 当前应用配置，非本轮产品策略变更 |
| Build Tools | 35.0.0 | AGP 8.13 默认版本与 CI 固定版本 |
| NDK | 28.2.13676358 | r28 默认启用 16 KB ELF 对齐 |
| CMake | 3.22.1 | 现有 native 模块配置 |

## 实施

- 根 `gradle.properties` 提供唯一 NDK 版本，所有 Android native 模块显式读取
- CI 的 Android build 与 PR check 安装同一 NDK；不再使用 25.1.8937393
- 本机 Command-line Tools 已从 20.0 升级到 22.0，`latest` 指向 22.0
- README、BUILDING、CONTRIBUTING 与正式准备清单使用同一版本表述
- 依赖准备脚本固定用 NDK 28 的 arm64 C++ runtime，移除外部归档与 ffmpeg AAR 的重复旧 runtime
- `CXX5304` 已定位到 AGP SDK metadata 解析器：AGP 8.13.2 携带的 `sdklib 31.13.2` 只有 `sdk-common-01.xsd` 至 `sdk-common-03.xsd`，而 AGP 9.2 对应的 `sdklib 32.2.0` 已包含 `sdk-common-04.xsd`
- 使用官方兼容组合 Gradle 9.5.0 与 AGP 9.3.0 做过不落盘配置探针；项目在应用 `org.jetbrains.kotlin.android 2.2.0` 时按官方预期因重复 `kotlin` extension 失败，证明 AGP 9 升级必须先完成内置 Kotlin 迁移

## 暂缓

- 暂不升级到 AGP 9.3.0、Gradle 9.5.0 和 Build Tools 36.0.0：根工程、app、八个 Android library、独立 shower 工具及 Flutter 模板仍应用 Kotlin Android plugin，app 同时使用 Kapt。按 [Android 官方内置 Kotlin 迁移说明](https://developer.android.com/build/migrate-to-built-in-kotlin)，这需要单独迁移插件与 Kapt 配置，不能靠 legacy opt-out 规避
- 不为 `CXX5304` 单独升级 CMake：CMake 3.22.1 直接配置成功，告警发生在 CMake 启动前的 AGP SDK metadata 读取阶段

## 当前工具链告警

- `:app:configureCMakeDebug[arm64-v8a] --rerun-tasks` 成功，但输出两次 `CXX5304`：AGP 8.13.2 的 SDK parser 只理解 XML v3，而本机 SDK metadata 为 v4
- 下一步是建立 AGP 9 内置 Kotlin/Kapt 迁移 TODO，并在配置、Kotlin 编译、CMake、Lint 和 Debug APK 全部通过后再切换工具链；不能把本告警错误归因于 CMake
