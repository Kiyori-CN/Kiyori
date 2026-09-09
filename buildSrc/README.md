# Kiyori 构建逻辑

本目录是 Gradle 自动编译的构建辅助工程，不是 Android 模块，也不进入 APK。
它只持有任务实现；应用配置、依赖版本、任务注册与 Android Variant 接线仍在
[`app/build.gradle.kts`](../app/build.gradle.kts)。

## 任务职责

源码位于 `src/main/kotlin/com/kiyori/buildlogic/tasks/`，每个任务类对应一个文件：

| 类型 | 输入与职责 | 输出 |
| --- | --- | --- |
| `GenerateBundledToolPkgAssetsTask` | 生产白名单、已构建示例；验证路径、敏感文件、大小与重名 | 每个 Variant 的生成 assets |
| `BuildNativeRipgrepTask` | Cargo manifest/lock、Rust 源码、固定 Rust/NDK/API | arm64 ripgrep JNI 库 |
| `BuildShellIdentityLauncherTask` | 指定 C++ 源码、固定 NDK/API；验证 linker、ABI 与 16 KB 对齐 | shell asset 或 Mihomo parent-death launcher |
| `PrepareMihomoRuntimeTask` | 固定 URL、大小与哈希；优先验证本地归档缓存 | arm64 Mihomo JNI 输入 |
| `VerifySingleDebugLauncherTask` | AGP 提供的合并 Manifest | 只读验证唯一 launcher，无输出文件 |

任务通过带注解的 Gradle Property 显式声明输入、输出、本地缓存和机器工具链约束。

内置 ToolPkg 收集固定运行目录（包括 `dist`、`resources`、`skills` 等），不会仅因
manifest 的 `distribution.include` 写了一个新目录就自动收集。随包 Skill 的 Markdown
经过文本体积和私钥检查；其资源路径必须在最终 `.toolpkg` 中核实。行为测试覆盖 Skill
内容逐字节保留与确定性打包，避免“manifest 引用存在但资源漏打包”。
不读取业务单例，不跨项目寻找 Android extension，不向源码 assets 写生成物。
`@CacheableTask` 只用于确定性资产；本地 Rust/NDK 执行和无输出验证保持显式非缓存声明。

`PrepareMihomoRuntimeTask` 保留既有 HTTPS 下载行为：仅本地归档不满足固定大小/哈希时获取
精确版本。这里不承担包管理器安装、依赖版本决策或发布。

## 工具链与测试

`kotlin-dsl` 使用当前 Gradle Wrapper 对应的 Kotlin 编译器，不使用 Android 应用的 Kotlin
插件版本。JDK 为 21；JUnit 版本复用根 `gradle/libs.versions.toml`，没有第二份测试版本。

在仓库根目录执行：

```powershell
.\gradlew.bat :buildSrc:test --no-daemon --console=plain
```

行为测试使用 Gradle `ProjectBuilder` 和临时目录，覆盖真实归档内容、无效输入、Manifest 与
固定缓存解包；不联网、不执行 native 程序、不操作设备。Rust/NDK 编译及 Android Variant
接线通过最终 `:app:assembleDebug` 验证。`buildSrc/build/`、`.gradle/` 等输出不提交。

新增任务时必须同步 [CI 路由](../ci/script/pr_check.py)、受影响正式文档和相称的行为测试。
本工程变更进入完整 Android 检查范围；本地测试通过不能代替 APK、设备或远端 CI 验收。
