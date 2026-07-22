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

### 暂缓及理由

- ffmpeg-kit local AAR 的 9 个 arm64 `.so` 仍为 `LOAD=0x1000`。仓库只含 AAR 和 WSL 构建脚本，没有可审计的 ffmpeg-kit 源码/hash；本机也没有 WSL，因此本轮不能声称完成重建。最短后续动作是取得固定 ffmpeg-kit 源码与构建输入，在 Linux/WSL 使用脚本和 NDK 28 重建后重新导入并复核 AAR hash、API 与 APK
- terminal `libsudo.so` 是预置的 2 字节 `$@` 文件，不能按 ELF 对齐；保持现有运行时语义，不改名、不删除、不用 suppress 掩盖
- TensorFlow Lite x86_64 仍触发 Lint `Aligned16KB`，但当前 app `abiFilters` 只打包 arm64，最终 APK 的 arm64 `libtensorflowlite_jni.so` 已为 `0x10000`；只有未来扩大 ABI 范围时才需要升级该依赖

### 工具链待升级项

- CMake `3.22.1` 配置成功但输出两次 `CXX5304`（SDK XML v4 与解析器只支持 v3）；需在验证过的 CMake/SDK 组合上升级并重新采集，不为形式化清零盲目升级
- 本机/CI 已统一 JDK 21、Gradle 8.13、AGP 8.13.2、compile SDK 36、Build Tools 35.0.0、NDK 28.2.13676358；这些不作为待升级项
