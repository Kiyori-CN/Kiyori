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
- 本机 Command-line Tools 已从 20.0 升级到 22.0，`latest` 指向 22.0；CMake 3.22.1 仍对 SDK XML v4 报 `CXX5304`，说明剩余问题在 CMake/SDK XML 解析器组合，不是仓库源码
- README、BUILDING、CONTRIBUTING 与正式准备清单使用同一版本表述
- 依赖准备脚本固定用 NDK 28 的 arm64 C++ runtime，移除外部归档与 ffmpeg AAR 的重复旧 runtime

## 暂缓

- 不升级 AGP/Gradle：当前已满足兼容矩阵，升级会扩大变更面且与 16 KB 目标无直接关系
- 不升级 CMake：3.22.1 是项目现有配置，CMake 复采未发现源码 warning；升级只为消除 warning 不相称

## 当前工具链告警

- `:app:configureCMakeDebug[arm64-v8a] --rerun-tasks` 成功，但输出两次 `CXX5304`：CMake 3.22.1 只理解 SDK XML v3，而本机 SDK 含 XML v4
- 下一步应在可验证的 CMake/Android Studio 组合上复采所有 native 模块；未建立兼容矩阵前不机械升级 CMake 或 AGP
