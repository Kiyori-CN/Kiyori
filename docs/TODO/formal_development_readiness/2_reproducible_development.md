# 可复现开发

## 源码入口

```bash
git clone https://github.com/Kiyori-CN/Kiyori.git
cd Kiyori
git switch main
git submodule sync -- terminal
git submodule update --init --recursive terminal
```

不要对整个仓库使用 `--recurse-submodules`：`terminal` 是常规构建需要的固定子模块；
`tools/hotbuild/OperitNightlyRelease` 是独立的可选私有子模块，不属于 Debug 构建入口。

父仓库只使用 `main` 作为持续开发分支。`terminal` 子模块固定到父仓库 gitlink 指定的提交，子模块自身也只使用 Kiyori 远端的 `main` 发布。

## 版本和工具链

- JDK 21 是运行 Gradle 的本地与 CI 基线；Java/Kotlin 字节码目标保持 JVM 17
- Gradle Wrapper 为 9.5.0，Android Gradle Plugin 为 9.3.1，Kotlin 编译器插件为 2.4.10
- Android compile SDK 为 37，target SDK 为 34，CI Build Tools 为 36.0.0，CMake 为 3.22.1
- 所有 Android native 模块通过 `gradle.properties` 固定使用 NDK 28.2.13676358；该版本属于 NDK r28，源码构建的 ELF 默认支持 16 KB segment 对齐
- JNI API 敏感的 llama.cpp 与 MNN 分别锁定到精确 commit
  `885c5bbe8e04dc78db25beb911a2715312ad7b54` 与
  `ea44a3ebd5dd6348eea501047b17c43aa3ecccb6`；不得改回会随时间漂移的 `master`/`main`
- `cmake/operit_git_source.cmake` 对 40 位十六进制 commit 直接进行内容寻址，不执行远端 ref
  解析；长度错误或非十六进制 token 不得伪装成固定 commit
- native ripgrep 固定使用 rustup 管理的 Rust 1.88.0 与 `aarch64-linux-android` target；Gradle 以 Cargo 锁文件、Rust 源码和固定 NDK 为输入生成 arm64 JNI 库
- shell identity launcher 固定从 `tools/shell_identity_launcher/native-lib.cpp` 生成；Gradle 使用 NDK 28、API 26、`-nostdlib++` 和 16 KB linker 对齐参数构建并验证 ELF，源码 assets 不保留预编译副本
- terminal 的 `sudo` 命令由唯一 `TerminalManager` 在私有 bin 目录生成 `/system/bin/sh` shim；仓库不保留伪装成 native library 的 `libsudo.so`
- Node.js、npm 和 Python 版本以 `.github/workflows/`、已提交 lockfile 和现有脚本为准
- 本地凭据仅放在未跟踪的 `local.properties`，不得写入仓库或 CI 日志

## 预置插件资产

- `tools/example_packages/packages_whitelist.txt` 是生产预置清单
- 普通 JS 脚本包继续使用已审阅并提交的 `app/src/main/assets/packages/*.js`
- 白名单中的目录型 ToolPkg 由 `generateBundledToolPkgAssets` 在构建目录确定性生成，并通过 Android Variant Sources API 接入所有变体
- 生成的 `.toolpkg` 不写回源码 assets，也不得提交；缺失 manifest、运行入口、符号链接、越界路径或重复输出名时构建必须失败

## Native ripgrep

- `buildNativeRipgrep` 将 Cargo 产物写入 `app/build/generated/nativeRipgrep/jniLibs/arm64-v8a/`
- Android Variant Sources API 把该生成目录接入每个 APK 变体，`assembleDebug` 和 CI 不再向 `app/src/main/jniLibs` 复制 ripgrep 产物
- 开发机和 CI 在构建前准备 Rust 1.88.0 与 Android target；Cargo 编译、NDK linker 选择、输出存在性检查和 APK 打包由 Gradle 统一持有
- `NativeRipgrep` 没有替代搜索路径，缺少库时构建链必须在交付前暴露问题

## 必须验证的内容

- 新鲜克隆可以初始化 `terminal`，且子模块工作树干净
- `git diff --check` 通过
- formal readiness 确认 llama.cpp 与 MNN 的精确 commit，CMake 单元测试确认 SHA 判断边界
- 根 `package.json` 为私有工具包，不可被 npm 误发布
- 资源、JSON、Markdown 和现有 CI 单元检查通过
- `assembleDebug` 通过并生成 `app/build/outputs/apk/debug/app-debug.apk`
- APK 包含 `lib/arm64-v8a/liboperit_ripgrep.so`，该文件为 AArch64 ELF 且 `PT_LOAD` 对齐不低于 16 KB
- APK 包含 `assets/operit_shell_exec`，该文件为 AArch64 ELF、所有 `PT_LOAD` 对齐不低于 16 KB，且不依赖 `libc++_shared.so`
- APK 不包含 `libsudo.so`，源码树也不包含预编译 `app/src/main/assets/operit_shell_exec`
- APK 的 `assets/packages/` 包含生产白名单中的预置 ToolPkg

本文件不把 release 签名、商店发布或真机体验当作 Debug 构建的隐含结果。那些项目必须在单独的发布与设备验收清单中确认。
