# 1. 工具链对齐

## 采用版本

| 项目 | 版本 | 依据 |
| --- | --- | --- |
| Gradle | 9.5.0 | AGP 9.3.1 兼容矩阵 |
| Android Gradle Plugin | 9.3.1 | 当前稳定工具链与 SDK XML v4 支持 |
| Kotlin | 2.3.21 | Compose、Serialization、Parcelize 与运行时统一版本 |
| JDK（运行 Gradle） | Temurin 21 | 当前 CI 与本机实际运行版本 |
| JVM target | 17 | 保持现有 Java/Kotlin 字节码兼容边界 |
| compile SDK | 36 | 当前应用配置 |
| target SDK | 34 | 当前应用配置，非本轮产品策略变更 |
| Build Tools | 36.0.0 | AGP 9.3.1 默认版本与 CI 固定版本 |
| NDK | 28.2.13676358 | r28 默认启用 16 KB ELF 对齐 |
| CMake | 3.22.1 | 现有 native 模块配置 |

## 实施

- 根工程、Android library、`tools/shower` 与 Android 原生模板迁移到 AGP 9 内置 Kotlin，不再应用 `org.jetbrains.kotlin.android`
- app 的 Kapt 使用 AGP 同版 `com.android.legacy-kapt`；未启用 built-in Kotlin 或新 DSL 的退出开关
- Compose、Serialization、Parcelize 与相关 Kotlin runtime 统一为 2.3.21；Java/Kotlin 字节码目标仍为 JVM 17
- 夜间版与克隆版 APK 文件名改用公开 Android Components Variant API，source set 改用 AGP 9 DSL
- 根工程、`tools/shower`、Android 原生模板及 CI 固定 Gradle 9.5.0、AGP 9.3.1 与 Build Tools 36.0.0
- 所有源码 native 模块继续显式使用 NDK 28.2.13676358；CMake 保持 3.22.1
- AGP 9.3.1 的 SDK metadata 解析器已消除旧 AGP 读取 SDK XML v4 时的 `CXX5304`
- 未修改市场下载或发布渠道；Lint baseline 只删除 200 条失效记录，没有吸收当前问题或新增 suppress

## 验证结果

- `gradlew help --warning-mode all` 成功，无 Gradle 配置告警
- `:app:compileDebugKotlin --rerun-tasks` 成功，共采集 610 条源码 warning：app 575、terminal 25、quickjs 6、dragonbones 2、mnn 2；产品壳提交没有直接新增 warning
- `:app:lintDebug` 成功，0 error、29 warning、1 个 baseline 已应用提示；baseline 保留 5843 条仍存在的记录
- `:app:configureCMakeDebug[arm64-v8a]` 成功，`CXX5304` 与 OpenFST `CMP0063` policy warning 均已消失
- `tools/shower` 与 Android 原生模板 Kotlin 编译成功且无 warning
- `assembleDebug` 成功，当前 APK 的包名、版本、16 KB ZIP 对齐与 ELF 清单见下一步文档

## 暂缓

- Kotlin 2.4 不解决本轮剩余源码或 native 告警；后续如升级应单独评估当前稳定补丁版 2.4.10，不停在 2.4.0
- compile SDK 37 与 Build Tools 37 涉及新的平台/API 策略，不因版本提示机械升级
- Flutter 模板仍使用其已验证的 AGP 8.11.1 与 Kotlin 2.2.20；本机没有 Flutter SDK，不能把未验证迁移并入 Android 原生工具链收尾
- CMake 3.22.1 已满足当前工程；除非 native 模块提出明确需求，不为追逐版本号单独升级
