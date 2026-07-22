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
- Android compile SDK 为 36，target SDK 为 34，Build Tools 为 35.0.0，CMake 为 3.22.1
- 所有 Android native 模块通过 `gradle.properties` 固定使用 NDK 28.2.13676358；该版本属于 NDK r28，源码构建的 ELF 默认支持 16 KB segment 对齐
- Node.js、pnpm 和 Python 版本以 `.github/workflows/` 和现有脚本为准
- 本地凭据仅放在未跟踪的 `local.properties`，不得写入仓库或 CI 日志

## 必须验证的内容

- 新鲜克隆可以初始化 `terminal`，且子模块工作树干净
- `git diff --check` 通过
- 根 `package.json` 为私有工具包，不可被 npm 误发布
- 资源、JSON、Markdown 和现有 CI 单元检查通过
- `assembleDebug` 通过并生成 `app/build/outputs/apk/debug/app-debug.apk`

本文件不把 release 签名、商店发布或真机体验当作 Debug 构建的隐含结果。那些项目必须在单独的发布与设备验收清单中确认。
