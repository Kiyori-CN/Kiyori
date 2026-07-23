# 可复现开发

## 源码入口

```bash
git clone --recurse-submodules https://github.com/Kiyori-CN/Kiyori.git
cd Kiyori
git switch main
git submodule sync -- terminal
git submodule update --init --recursive terminal
```

父仓库只使用 `main` 作为持续开发分支。`terminal` 子模块固定到父仓库 gitlink 指定的提交，子模块自身也只使用 Kiyori 远端的 `main` 发布。

## 版本和工具链

- JDK 21 是运行 Gradle 的本地与 CI 基线；Java/Kotlin 字节码目标保持 JVM 17
- Gradle Wrapper 为 9.5.0，Android Gradle Plugin 为 9.3.1，Kotlin 编译器插件为 2.3.21
- Android compile SDK 为 36，target SDK 为 34，Build Tools 为 36.0.0，CMake 为 3.22.1
- 所有 Android native 模块通过 `gradle.properties` 固定使用 NDK 28.2.13676358；该版本属于 NDK r28，源码构建的 ELF 默认支持 16 KB segment 对齐
- Node.js、pnpm 和 Python 版本以 `.github/workflows/` 和现有脚本为准
- 本地凭据仅放在未跟踪的 `local.properties`，不得写入仓库或 CI 日志

## 预置插件资产

- `tools/example_packages/packages_whitelist.txt` 是生产预置清单
- 普通 JS 脚本包继续使用已审阅并提交的 `app/src/main/assets/packages/*.js`
- 白名单中的目录型 ToolPkg 由 `generateBundledToolPkgAssets` 在构建目录确定性生成，并通过 Android Variant Sources API 接入所有变体
- 生成的 `.toolpkg` 不写回源码 assets，也不得提交；缺失 manifest、运行入口、符号链接、越界路径或重复输出名时构建必须失败

## 必须验证的内容

- 新鲜克隆可以初始化 `terminal`，且子模块工作树干净
- `git diff --check` 通过
- 根 `package.json` 为私有工具包，不可被 npm 误发布
- 资源、JSON、Markdown 和现有 CI 单元检查通过
- `assembleDebug` 通过并生成 `app/build/outputs/apk/debug/app-debug.apk`
- APK 的 `assets/packages/` 包含生产白名单中的预置 ToolPkg

本文件不把 release 签名、商店发布或真机体验当作 Debug 构建的隐含结果。那些项目必须在单独的发布与设备验收清单中确认。
