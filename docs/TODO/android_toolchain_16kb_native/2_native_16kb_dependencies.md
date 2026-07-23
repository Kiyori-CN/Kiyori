# 2. 16 KB native 依赖审计与处理

## 审计顺序

1. 从最新 Debug APK 列出全部 ABI `.so`
2. 对 APK 内文件执行 `zipalign -c -P 16 -v 4`
3. 使用 NDK r28 的 `llvm-readelf -lW` 检查每个 ELF 的 `LOAD` segment 对齐
4. 将源码构建产物、仓库内预置二进制、Maven/AAR 预编译库分别记录

## 最终 APK 证据

最终 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-23 05:36:06 +08:00`，大小 `415154103` 字节，SHA-256 `E9DB93145EAA5724701AF8883F9A9D473AF64836597AAB75106D71A0B9B849B5`。

- `zipalign -c -P 16 -v 4`：通过
- arm64 `.so`：44 个文件，其中 43 个可识别 ELF、34 个 `LOAD >= 0x4000`、9 个未对齐 ELF、1 个非 ELF
- 未对齐 ELF：ffmpeg-kit 的 `libavcodec.so`、`libavdevice.so`、`libavfilter.so`、`libavformat.so`、`libavutil.so`、`libffmpegkit_abidetect.so`、`libffmpegkit.so`、`libswresample.so`、`libswscale.so`
- 非 ELF：terminal 的 `libsudo.so`，文件内容为 2 字节 `$@` 占位脚本；strip 失败是预置文件类型问题，不是 CMake 产物

当前 `ffmpeg-kit-local.aar` 的 SHA-256 为 `562944A2EE83BA86A09CC40C9E1EE4B0D83FF6944522F1C1AFCB7D437E47834D`。native 字符串确认它包含 FFmpeg `n6.0`，原构建路径为 `/home/aaswordsman/build/ffmpeg-kit`，使用 NDK `22.1.7171670`。官方 `v6.0` 与 `v6.0.LTS` tag 均指向 `d6be56d7aec286eb3c292d6b23ff07a6b70d8693`；重建脚本现已强制校验该 commit 与干净 tracked source。

Lint 最终报告（`2026-07-23 06:04:05 +08:00`）为 38 warnings + 2 hints，其中 `Aligned16KB=6`：ffmpeg AAR 的重复 `libavcodec.so` 记录 3 条，以及不进入当前 arm64 APK 的 TensorFlow Lite x86_64 记录 3 条。

## 处理规则

- 本仓库可控的 CMake/Fetched native 源码在 NDK r28 下重建
- 有明确上游 16 KB 支持版本的 Maven/AAR 依赖才定向升级，并逐项验证 API/ABI
- 无源码或无兼容版本的预编译 `.so` 暂缓，记录坐标、文件、上游入口和影响
- 不通过压缩、baseline、lint suppress 或新增空规则文件伪造修复

## 最终清单

### 已修复

- MediaPipe `0.10.11 → 0.10.35`、ML Kit Text `16.0.0 → 16.0.1`（bundled common `17.0.0`）、ONNX Runtime `1.17.1 → 1.27.0`、android-gif-drawable `1.2.28 → 1.2.32`；候选 AAR 的 arm64 ELF 与最终 APK 均已复核
- 源码 native 模块统一 NDK `28.2.13676358`，最终 APK 中 `libpty.so`、MediaPipe、ML Kit、ONNX Runtime、TensorFlow Lite arm64 及其他源码产物均已达到 16KB
- 依赖准备脚本删除 jniLibs.zip 中旧 GIF native 副本、删除 ffmpeg AAR 内重复的 arm64 `libc++_shared.so`，并以 NDK 28 arm64 runtime 作为唯一提供者；最终 APK 不再含旧 GIF 或旧 libc++
- ffmpeg-kit 重建入口已固定官方 `v6.0` commit，并拒绝错误 commit 或已有 tracked 修改的源码；导入脚本不再写死旧 `/mnt/d/Code/prog/assistance` 路径，而是把当前仓库目标路径转换为 WSL 路径

### 暂缓及理由

- ffmpeg-kit local AAR 的 9 个 arm64 `.so` 仍为 `LOAD=0x1000`。源码 commit 与构建入口已经固定，但本机没有 Linux/WSL、Docker 或 Podman，Kiyori 仓库的 GitHub Actions 又处于仓库级禁用状态，因此本轮不能执行或声称完成重建。最短后续动作是在获授权的 Linux/WSL 环境使用固定 commit 与 NDK 28 运行脚本，导入新 AAR 后复核 hash、Java API、运行行为与最终 APK
- terminal `libsudo.so` 是预置的 2 字节 `$@` 文件，不能按 ELF 对齐；保持现有运行时语义，不改名、不删除、不用 suppress 掩盖
- TensorFlow Lite x86_64 仍触发 Lint `Aligned16KB`，但当前 app `abiFilters` 只打包 arm64，最终 APK 的 arm64 `libtensorflowlite_jni.so` 已为 `0x10000`；只有未来扩大 ABI 范围时才需要升级该依赖

### 工具链待升级项

- AGP 9.3.0、Gradle 9.5.0 与 Build Tools 36.0.0 需要和 AGP 9 内置 Kotlin/Kapt 迁移一起实施；配置探针已确认当前工程不能只改版本号
- CMake 3.22.1 不是 `CXX5304` 的根因；除非 native 模块本身提出新版本需求，不为消除此告警单独升级
- 本机/CI 声明已统一 JDK 21、Gradle 8.13、AGP 8.13.2、compile SDK 36、Build Tools 35.0.0、NDK 28.2.13676358；CI 实际执行仍取决于重新启用仓库级 GitHub Actions
