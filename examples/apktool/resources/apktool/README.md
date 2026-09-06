# APK 逆向工具运行资源

本目录定义 APK 逆向工具的 Android 运行资源布局。`.jar` 是本地构建产物，不由 Git 跟踪；生成入口为 [build_runtime_android_resources.ps1](../../build_runtime_android_resources.ps1)。

## 资源分层

| 资源 | 职责 |
| --- | --- |
| `apktool-runtime-android.jar` | `brut.androlib.*` 与 Android 专属 `prebuilt/android/aapt2` |
| `android-framework.jar` | 独立的 Android framework 资源，不重复嵌入 apktool JAR |
| `jadx-runtime-android.jar` | 无界面的 JADX APK 处理链 |
| `apk-reverse-helper-runtime-android.jar` | 稳定 helper 接口与 native 分析辅助 |

## 加载与打包契约

- 所有运行 JAR 都必须是包含 `classes.dex`、可由 Android 加载的 dex-jar。
- 使用 `ToolPkg.readResource(...)` 与 `Java.loadJar(..., { childFirstPrefixes: [...] })` 加载；JS 只负责参数、资源提取和结果组织。
- 不调用 CLI、终端子进程或 `runJar`。JADX 或 helper JAR 缺失时明确失败。
- JADX 的 `jadx.core.dex.visitors.SaveCode` 修补避开较新 JDK 专属的 `PrintWriter(File, Charset)`。
- 打包移除桌面 GUI、`jadx-script`、`java-convert`、`aab-input`、`java-input` 和 `raung`，以及 Windows/Linux/macOS 的 `aapt2`。
