# Kiyori Android 构建指南

本文说明如何从干净源码准备 Kiyori 的本地开发环境、可复现依赖和 Debug APK。它面向
Windows、Linux 与 macOS 开发者；仓库当前不提供公开 Release/AAB 发布流程。

> [!IMPORTANT]
> 常规构建只初始化 `terminal` 子模块。不要使用 `git clone --recurse-submodules`，
> 否则 Git 还会尝试访问不属于常规 Debug 构建的可选私有夜间构建子模块。

## 构建输出

| 变体 | 命令 | 输出 |
| --- | --- | --- |
| Debug | `:app:assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| Clone | `assembleDebugClone` | `app/build/outputs/apk/clone/app-clone.apk` |

当前应用 APK 只打包 `arm64-v8a`。Debug 构建、静态 APK 审计和自动化测试不能替代真机、
Release 签名、商店发布或用户验收。

## 工具链基线

项目和 CI 使用以下受控基线：

| 工具 | 版本或约束 |
| --- | --- |
| JDK | 21；Java/Kotlin 字节码目标为 JVM 17 |
| Gradle | 使用仓库自带 Wrapper |
| Android SDK | Platform 37；target SDK 34 |
| Android Build Tools | 36.0.0 |
| Android NDK | 28.2.13676358 |
| CMake | 3.22.1 |
| Rust | 1.88.0；target `aarch64-linux-android` |
| Node.js | 22 |
| npm | 随 Node.js 22 提供；依赖由已提交的 `package-lock.json` 冻结 |
| Python | Python 3；仓库脚本使用项目 `.venv` |

版本权威来源是 `.github/workflows/`、`gradle/libs.versions.toml`、
`gradle/wrapper/gradle-wrapper.properties` 和 `gradle.properties`。本文件不应独立漂移。

## 1. 克隆源码

直接克隆 Kiyori 并只初始化公开构建依赖 `terminal`：

```bash
git clone https://github.com/Kiyori-CN/Kiyori.git
cd Kiyori
git switch main
git submodule sync -- terminal
git submodule update --init --recursive terminal
```

贡献者使用个人 Fork 时，把第一条命令替换为自己的仓库地址，并把 Kiyori 主仓添加为
`upstream`：

```bash
git remote add upstream https://github.com/Kiyori-CN/Kiyori.git
git fetch upstream
```

## 2. 安装基础工具

### Windows

安装 JDK 21、Android SDK、Node.js 22、Python 3、Git 和 Rust。确保以下命令可用：

```powershell
java -version
node --version
npm --version
python --version
rustup --version
```

设置 `JAVA_HOME`、`ANDROID_HOME` 或 `ANDROID_SDK_ROOT`，并把 Java、Android
Command-line Tools、Platform Tools、Node.js、Python 和 Rust 加入 `PATH`。

### Linux / macOS

通过系统包管理器安装 Git、JDK 21、Node.js 22、Python 3、unzip 和 Rust。Linux 示例：

```bash
sudo apt update
sudo apt install -y git unzip openjdk-21-jdk nodejs npm python3 python3-venv
```

不同发行版和 macOS 的 JDK 路径不同；请把 `JAVA_HOME` 指向实际安装的 JDK 21，不要复制
与本机不匹配的固定路径。

### Node.js 与 npm

根工具、WebChat 和 WASM ToolPkg 示例各自使用已提交的 npm lockfile。ToolPkg 同步脚本复用
根安装树执行 TypeScript 预构建，不会切换包管理器：

```bash
npm ci --no-audit --no-fund
npm --prefix web-chat ci --no-audit --no-fund
npm --prefix examples/toolpkg_wasm_demo ci --no-audit --no-fund
```

根 `package.json` 是私有开发工具包，不用于发布到 npm。

### Python 虚拟环境

仓库 Python 检查只依赖标准库，但仍使用项目 `.venv` 隔离运行时。

Windows：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe --version
```

Linux / macOS：

```bash
python3 -m venv .venv
.venv/bin/python --version
```

### Rust target

native ripgrep 构建锁定 Rust 1.88.0 和 Android arm64 target：

```bash
rustup toolchain install 1.88.0
rustup target add aarch64-linux-android --toolchain 1.88.0
```

### Native source snapshots

直接与 Kiyori JNI 代码共同编译的 llama.cpp 与 MNN 使用精确上游 commit：

```text
llama.cpp  885c5bbe8e04dc78db25beb911a2715312ad7b54
MNN        ea44a3ebd5dd6348eea501047b17c43aa3ecccb6
```

不要把它们改回 `master` 或 `main`。移动 ref 会让同一 Kiyori commit 在不同日期解析到不同
native API 和产物。更新 snapshot 时必须在同一变更中完成 JNI/CMake 兼容性检查、定向
arm64 构建、完整 `assembleDebug`、ELF 审计和正式 readiness 更新。

## 3. 安装 Android SDK、NDK 与 CMake

可使用 Android Studio SDK Manager，也可使用 `sdkmanager`：

```bash
sdkmanager \
  "platform-tools" \
  "platforms;android-37" \
  "build-tools;36.0.0" \
  "ndk;28.2.13676358" \
  "cmake;3.22.1"
```

首次安装后接受 Android SDK 许可证：

```bash
yes | sdkmanager --licenses
```

Windows PowerShell 可直接运行 `sdkmanager.bat`，或在 Android Studio 中完成同样操作。

## 4. 配置本地属性

复制模板：

Windows：

```powershell
Copy-Item local.properties.example local.properties
```

Linux / macOS：

```bash
cp local.properties.example local.properties
```

如需 GitHub 登录或相关集成功能，为自己的 GitHub OAuth App 配置 Client ID。稳定回调协议仍是：

```text
operit://github-oauth-callback
```

`local.properties` 已被 Git 忽略，不得把真实 Client ID、Client Secret、签名材料或本机路径提交
到仓库。

## 5. 准备大型 Android 输入

仓库不会提交全部 AAR、模型、subpack 和 JNI 输入。完整构建需要以下四个归档：

| 归档 | 目标 |
| --- | --- |
| `libs.zip` | `app/libs/` |
| `models.zip` | `app/src/main/assets/models/` |
| `subpack.zip` | `app/src/main/assets/subpack/` |
| `jniLibs.zip` | `app/src/main/jniLibs/` |

这些目录是本机准备的构建输入，不是普通缓存。不要使用 `git clean -X` 或无差别删除命令清理
它们。

### 在 Bash 环境下载固定归档

CI 使用受控下载脚本。把归档写入已忽略的 `work/manual-deps`：

```bash
export RUNNER_TEMP="${RUNNER_TEMP:-/tmp}"
bash ci/script/download_android_dependencies.sh full "$PWD/work/manual-deps"
```

### 解包、净化并验证输入

Windows：

```powershell
.\.venv\Scripts\python.exe -B ci\script\prepare_android_dependencies.py `
  --profile full `
  --archives work\manual-deps `
  --repository . `
  --android-ndk "$env:ANDROID_HOME\ndk\28.2.13676358"
```

Linux / macOS：

```bash
.venv/bin/python -B ci/script/prepare_android_dependencies.py \
  --profile full \
  --archives "$PWD/work/manual-deps" \
  --repository "$PWD" \
  --android-ndk "$ANDROID_HOME/ndk/28.2.13676358"
```

准备脚本限制归档成员、解压大小、压缩比、文件类型、符号链接和越界路径，并生成经过哈希、
ABI、native owner、TLS 与 C++ 符号检查的播放器 AAR。详细合同见
[Player native stack](./PLAYER_NATIVE_STACK.md)。

只验证当前已选播放器输入时：

Windows：

```powershell
.\.venv\Scripts\python.exe -B ci\script\prepare_mpv_player_dependency.py --repository .
```

Linux / macOS：

```bash
.venv/bin/python -B ci/script/prepare_mpv_player_dependency.py --repository .
```

Selected dual M9 native source closure qualification:

```powershell
.\.venv\Scripts\python.exe -B ci\script\build_player_native_closure.py `
  --repository . `
  --profile m9_ffmpeg_major_candidate `
  --work-root <isolated-work-root> `
  --android-ndk <isolated-r29-path> `
  --android-sdk <android-sdk-path> `
  --bash <msys2-bash-path> `
  --build-tools <python-build-tools-path> `
  --host-toolchain <winlibs-root> `
  --jobs 8

.\.venv\Scripts\python.exe -B ci\script\prepare_mpv_player_dependency.py `
  --repository . `
  --source-closure-aar <source-aar> `
  --source-closure-profile m9_ffmpeg_major_candidate `
  --native-readelf <windows-host-ndk-llvm-readelf.exe>

.\.venv\Scripts\python.exe -B ci\script\build_ffmpegkit_native_closure.py `
  --repository . `
  --distribution Ubuntu-22.04 `
  --linux-user <wsl-user> `
  --work-root <wsl-work-root> `
  --android-ndk <wsl-ndk-r29-path> `
  --android-sdk <wsl-android-sdk-path> `
  --native-readelf <windows-host-ndk-llvm-readelf.exe> `
  --jobs 12 `
  --candidate-output

.\.venv\Scripts\python.exe -B ci\script\prepare_mpv_player_dependency.py `
  --repository . `
  --promote-m9-mpv-candidate <m9-player-thin-candidate> `
  --promote-m9-ffmpegkit-candidate <m9-ffmpegkit-thin-candidate> `
  --source-closure-profile m9_ffmpeg_major_candidate `
  --native-readelf <windows-host-ndk-llvm-readelf.exe> `
  --expected-mpv-sha256 f52aca6f35c651be7aab55f2efe6b5f40180d1ebaeb1404cc446470bf8deb6a4 `
  --expected-ffmpegkit-sha256 7e6b4c20a93dfb3b90bc7f3c5d724cf657b70e2469ea4f2b1110396a8d345394
```

若 mpv 产品 AAR 保持固定哈希、只晋级经过审计的 FFmpegKit patch-level closure：

```powershell
.\.venv\Scripts\python.exe -B ci\script\prepare_mpv_player_dependency.py `
  --repository . `
  --promote-m9-ffmpegkit-patch-candidate <m9-ffmpegkit-thin-candidate> `
  --source-closure-profile m9_ffmpeg_major_candidate `
  --native-readelf <windows-host-ndk-llvm-readelf.exe> `
  --expected-ffmpegkit-sha256 7e6b4c20a93dfb3b90bc7f3c5d724cf657b70e2469ea4f2b1110396a8d345394
```

The default prepare command only validates the selected product AAR and fixed FFmpegKit AAR. It does not download
or recreate the historical mpv product when the selected AAR is absent. The explicit legacy `--mpv-input-aar` mode
writes only a baseline under `work/`.

The selected player source and candidate commands use `--profile m9_ffmpeg_major_candidate`. Paired promotion
accepts only that profile and the two fixed product hashes; it audits both candidates, both product-directory
temporary files and both final product AARs. The source builder records an ignored
`work/player-native-build/<profile>/source-lock.json`. Windows builds require MSYS2 Bash with Autotools, an isolated
NDK r29 and SDK path, plus a complete fixed WinLibs host toolchain. The builder checks
`x86_64-w64-mingw32/include/assert.h`, `stdint.h`, and `stdio.h` before compiling; a partial archive extraction is
rejected before the FFmpeg host code generators run. NDK 28.2 is only a smoke environment and cannot be used as final
M8/M9 selection evidence.

FFmpegKit compilation uses the fixed WSL NDK r29 path. The `--native-readelf` argument is different: the closure
auditor runs as a Windows-host Python process, so it requires a Windows `llvm-readelf.exe` and rejects a `/home/...`
WSL binary path before touching the workspace. A compatible Windows NDK `llvm-readelf.exe` may inspect the generated
AArch64 ELF files; it does not change the NDK revision recorded by the built closure.

## 6. 生成 WebChat 与示例输入

先执行 TypeScript 检查并生成 WebChat：

```bash
npm --prefix web-chat run typecheck
npm run build:webchat
```

`build:webchat` 会构建 Vite 产物并同步到忽略的
`app/src/main/assets/web-chat/`。该目录可重新生成，不应提交。

验证 GitHub 示例和 WASM ToolPkg：

```bash
npm run build:examples:github
git diff --exit-code -- examples/github.js
npm --prefix examples/toolpkg_wasm_demo ci --no-audit --no-fund
npm --prefix examples/toolpkg_wasm_demo run pack:toolpkg
```

Android 构建会从生产白名单生成目录型 ToolPkg 到 `app/build/generated/`，不把生成的
`.toolpkg` 写回源码目录。需要核对生产脚本包同步时运行：

Windows：

```powershell
.\.venv\Scripts\python.exe -B tools\example_packages\sync_example_packages.py `
  --mode normal `
  --no-hot-reload
```

Linux / macOS：

```bash
.venv/bin/python -B tools/example_packages/sync_example_packages.py \
  --mode normal \
  --no-hot-reload
```

## 7. 运行开发门禁

修改前后至少运行与改动相关的检查。完整仓库验证使用根聚合任务，而不是只检查 `:app`。

Windows：

```powershell
.\.venv\Scripts\python.exe -B -m unittest discover -s ci\test -p "test_*.py"
.\.venv\Scripts\python.exe -B ci\script\check_formal_readiness.py --repository . --require-main
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py --repository . --require-main
.\.venv\Scripts\python.exe -B ci\script\normalize_lint_baseline.py --check
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin compileDebugAndroidTestJavaWithJavac lintDebug `
  --stacktrace --no-build-cache --no-daemon --console=plain
```

Linux / macOS：

```bash
.venv/bin/python -B -m unittest discover -s ci/test -p "test_*.py"
.venv/bin/python -B ci/script/check_formal_readiness.py --repository . --require-main
.venv/bin/python -B ci/script/check_architecture_boundaries.py --repository . --require-main
.venv/bin/python -B ci/script/normalize_lint_baseline.py --check
./gradlew testDebugUnitTest compileDebugAndroidTestKotlin compileDebugAndroidTestJavaWithJavac lintDebug \
  --stacktrace --no-build-cache --no-daemon --console=plain
```

候选提交的 Markdown、本地化、仓库卫生和 fresh-clone 检查见 [`ci/README.md`](../../../ci/README.md)。

## 8. 构建 Debug APK

Windows：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Linux / macOS：

```bash
./gradlew :app:assembleDebug --no-daemon --console=plain
```

构建会同时验证：

- 唯一 Debug launcher
- 受控 player AAR 输入与 native owner
- 精确 commit 的 llama.cpp 与 MNN 产品 JNI 目标
- arm64 native ripgrep
- 16 KB 对齐的 shell identity launcher
- 生产 ToolPkg 生成输入

## 9. 独立核验 APK

不能只凭 Gradle 退出码判断产物正确。至少检查：

```text
aapt dump badging app/build/outputs/apk/debug/app-debug.apk
apksigner verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
zipalign -c -P 16 -v 4 app/build/outputs/apk/debug/app-debug.apk
```

16 KB ZIP alignment 不等于 ELF segment alignment。native 产物还需要使用 NDK
`llvm-readelf -lW` 核对所有 `PT_LOAD` 不低于 `0x4000`。

APK 还应只包含 `arm64-v8a`、零重复 `.so` basename、一份共享模板 AAPT2，并包含
`liboperit_ripgrep.so` 与 `assets/operit_shell_exec`，同时不包含 `libsudo.so`。

## 常见问题

| 现象 | 检查方向 |
| --- | --- |
| `JAVA_HOME` 或 Java 版本错误 | 确认 JDK 21 实际路径及 `java -version` |
| `sdkmanager` 不存在 | 把 Android Command-line Tools 的 `latest/bin` 加入 `PATH` |
| Android 许可证错误 | 重新运行 `sdkmanager --licenses` |
| NDK/CMake 不存在 | 安装本文件锁定版本，并核对 `ANDROID_HOME` |
| `npm ci` 报 lockfile 不一致 | 不要手工改 `node_modules`；同步更新对应 `package.json` 与 `package-lock.json` |
| WebChat 产物缺失 | 先执行 `npm --prefix web-chat ci` 和 `npm run build:webchat` |
| AAR、模型或 subpack 缺失 | 重新下载四个归档并运行依赖准备脚本 |
| player input hash 或 native owner 失败 | 不要手工替换 AAR；重新运行受控准备脚本 |
| `terminal` 类或资源缺失 | 运行 `git submodule update --init --recursive terminal` |
| Gradle 内存不足 | 先关闭并发构建，再按本机内存审慎调整 `org.gradle.jvmargs` |

如问题仍未解决，请在 Issue 中提供完整命令、首个根因错误、操作系统、JDK/SDK/NDK 版本和经过
脱敏的必要日志；不要只提交最终的级联异常。
