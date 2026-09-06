# 播放器与 FFmpegKit 原生依赖栈

本文维护精确成员、SONAME、DT_NEEDED、ELF、哈希与制品提升流程。运行时三执行面和失败语义见 [FFmpeg 架构](FFMPEG_ARCHITECTURE.md)，播放与缓存见 [播放器架构](PLAYER_ARCHITECTURE.md)。

## 两套隔离闭包

非导出 `:ffmpeg` 使用 FFmpegKit 的普通 libav 名称，非导出 `:player` 使用 mpv 与命名空间化 libmp 闭包；主进程通过 Binder 调用。两套均为 ARM64，仅播放器提供共享 C++ runtime，不通过同名覆盖或 packaging 选择掩盖符号冲突。

## 已选 M9 制品

精确源码身份与选择由 [播放器 manifest](../../../tools/player_native_build/closure_manifest.json)、[FFmpegKit manifest](../../../tools/ffmpegkit_native_build/closure_manifest.json) 和 [NOTICE](../../../NOTICE) 管理。以下哈希描述固定输入，不是当前 APK 哈希。

| 输入 | 源码身份 | SHA-256 |
| --- | --- | --- |
| 播放器 M9 源码 AAR | `mpvlibAndroid@168e0a5e43b37c85509050cddcb5eaddc2e313c0`; mpv `2339eb72767517fc5a113283939f59076946fbc1`; FFmpeg `n9.0.1` / `bf1b838f2ab88b4f8fd83443325c782ea0e0f7fa`; Mbed TLS `3.6.7` / `068ff080b369adfac81509f9b57b2afabaf82dc5` | `7CB0B25DC15F21278992243CE2597193B7A54E1A488933555B572982203E2BC0` |
| 播放器已选对齐 AAR | 上述源码闭包转换为 `libmp*.so` 命名空间并确定性完成 16 KiB ZIP 对齐 | `F52ACA6F35C651BE7AAB55F2EFE6B5F40180D1EBAEB1404CC446470BF8DEB6A4` |
| FFmpegKit r6 源码 AAR | 维护框架 `62b07bf097baf26b416c815aea514e05c9ad6d63`; wrapper `8.1.7-kiyori-n9.0.1-r6`; FFmpeg `n9.0.1` / `bf1b838f2ab88b4f8fd83443325c782ea0e0f7fa`; OpenH264 `v2.6.0` / `652bdb7719f30b52b08e506645a7322ff1b2cc6f`; 保留 Android Binder 线程池、能力 stdout 桥接、GPL/HarfBuzz 与 `drawtext`/`eq`/`boxblur` | `0BD7ADDAE2D17960DB940A17A3E2450ACB83EECB46BE0D3800D051BB2075C1CE` |
| FFmpegKit r6 已选对齐 AAR | 上述完整工具闭包移除第二份 `libc++_shared.so`，保留 Java/JNI、SAF、资源、GPLv3 正文与声明 | `7E6B4C20A93DFB3B90BC7F3C5D724CF657B70E2469EA4F2B1110396A8D345394` |

播放器 source AAR 为 23,380,329 bytes，对齐产品为 50,926,119 bytes；FFmpegKit r6 source 为 39,632,341 bytes，对齐产品为 30,486,441 bytes。构建器在隔离工作区生成、独立审计后才提升到稳定路径。

## ELF 命名与链接

播放器转换保持 ELF 字节长度，仅替换等长动态库字符串：

| 上游名称 | 播放器命名空间 |
| --- | --- |
| `libavcodec.so` | `libmpcodec.so` |
| `libavdevice.so` | `libmpdevice.so` |
| `libavfilter.so` | `libmpfilter.so` |
| `libavformat.so` | `libmpformat.so` |
| `libavutil.so` | `libmputil.so` |
| `libswresample.so` | `libmpresample.so` |
| `libswscale.so` | `libmpscale.so` |

同样映射作用于 libmpv、libplayer 和七个 FFmpeg 文件的 SONAME/DT_NEEDED。两套 FFmpeg major 均为 `.63/.63/.12/.63/.61/.7/.10`，不能拼接不同版本 ELF。

- 播放器产品保留 classes.jar、Manifest/metadata、cacert.pem、subfont.ttf、libmpv、libplayer、七个 namespaced FFmpeg 和唯一 libc++_shared。
- C++ runtime 为 1,374,336 bytes，SHA-256 `C4C2FE5CBCB1FBA0003A31FC7AB29A9BB12DF6CC187EC45A806462540E83D93B`，与 libmpv 同为 Clang 21.0.0 / build 13989888。
- 全部 ELF 为 ELF64/AArch64、小端，PT_LOAD 不小于 0x4000，无 RPATH/RUNPATH；AAR native payload 位于 16 KiB ZIP 对齐偏移。
- 播放器 HTTPS 使用 Mbed TLS 3.6.7，启用 RSA-PSS、禁用 curl。FFmpegKit 的完整工具闭包保留 codec/filter/library、MediaCodec、zlib，禁用 OpenSSL，不改变播放器 TLS 后端。
- FFmpegKit 产品保留 Java/JNI、proguard、SAF、资源与来源声明，native 为七个普通 FFmpeg 加 libffmpegkit、libffmpegkit_abidetect，移除其第二份 libc++_shared。
- 启动复制固定 cacert.pem，设置 tls-ca-file，保持 tls-verify=yes 与 ytdl=no；不分发 yt-dlp。
- 播放请求移除捕获 Range，由 mpv/FFmpeg 管理打开与 seek 偏移；候选和下载仍保留原证据。
- ExoPlayer 属于既有背景视频所有者，不是可选择的第二播放器内核。

## 构建与提升边界

播放器固定 mpv `2339eb727`、FFmpeg `n9.0.1`、Mbed TLS `3.6.7`、arm64/API 24+、NDK `29.0.14206865`。这个 native 制品工具链与应用 JNI 构建的 NDK 28 是不同边界。

Windows 构建修正规范受控：FFmpeg 大写 .S、GNU Make response file 与长头文件安装、libass 单 job、Lua 参数转换、NDK Shaderc 短路径、mpv JNI 根、Meson 生成 RPATH。未知、多处或不匹配形式直接失败，不能任意改写。

source/thin 审计校验完整成员、Java/JNI、ABI、SONAME/DT_NEEDED、RPATH、LOAD 对齐、版本命名空间、FFmpeg/C++ 符号闭包（含 float/double __from_chars_floating_point）、mpv commit、TLS/RSA-PSS/HTTPS、curl-disabled、libplacebo/shaderc 与 ZIP 偏移。

成对提升只接受 `m9_ffmpeg_major_candidate` 与精确产品哈希，依次审计候选、app/libs 临时文件和最终两个产品。仅 FFmpegKit 补丁提升须前后验证 mpv 字节不变，按其 C++ runtime 审计候选/临时/最终 FFmpegKit。

旧私有归档可能仍有 ffmpeg-kit-local.aar 或手工 JNI；依赖准备脚本移除已退役所有者并验证两份选定 AAR。默认验证不从旧输入重建产品，缺失时必须从受审计源码候选恢复。

M8 与 2026-06-25 二进制路径仅能在 work 中重现历史，不是运行时选择或制品提升选项；不得替换版本 marker 或单个 ELF 形成混合闭包。

## FFmpegKit r6 源码修正

- 固定 33 文件 overlay 将 session、日志、report、设备、graph prefix、vstats 留在当前执行；Java/JNI 和 SAF 接口保持。
- FFmpeg constrained baseline 映射为 OpenH264 baseline enum；保留空 BsFlush word 修补，因为 OpenH264 v2.6.0 早于上游 `40555ec684ec0fede3948c8f272c04d88d05189d`。
- 转码前保留 Android 已启动 Binder threadpool；r5 的 HarfBuzz/private FFprobe JSON 保留，r6 把能力 stdout 接入 log callback，启用 GPL、drawtext/eq/boxblur。
- 约束 CONFIG_LIBHARFBUZZ、CONFIG_DRAWTEXT_FILTER、CONFIG_EQ_FILTER、CONFIG_BOXBLUR_FILTER 均为 1，并核对 binary marker 与逐字节 GPLv3 资源。
- 框架使 job 上限实际生效，在 Android helper 重置源码后重新应用 OpenH264 修补。
- :ffmpeg 只有一个 FIFO 顶层 native owner，request/session/result 独立；终态交付先排空活动 session 和 session 0 callback。libffmpegkit 不导入 exit/_exit/quick_exit。

## 可复现命令

先准备清单要求的工具链，将下列尖括号替换为本机明确路径。构建与提升会写制品，应限定在当前任务授权范围。

### 验证已选择的产品 AAR（默认不下载、不重建历史产品）

```powershell
.\.venv\Scripts\python.exe -B ci/script/prepare_mpv_player_dependency.py --repository .
```

### 构建播放器 M9 源码并生成对齐候选

```powershell
.\.venv\Scripts\python.exe -B ci\script\build_player_native_closure.py `
  --repository . `
  --profile m9_ffmpeg_major_candidate `
  --work-root <isolated-work-root> `
  --android-ndk <ndk-r29-path> `
  --android-sdk <android-sdk-path> `
  --bash <msys2-bash-path> `
  --build-tools <python-build-tools-path> `
  --host-toolchain <winlibs-root> `
  --jobs 8

.\.venv\Scripts\python.exe -B ci\script\prepare_mpv_player_dependency.py `
  --repository . `
  --source-closure-aar <m9-source-aar> `
  --source-closure-profile m9_ffmpeg_major_candidate `
  --native-readelf <windows-host-ndk-llvm-readelf.exe>
```

### 通过 WSL 构建 FFmpegKit M9 候选

```powershell
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
```

### 两个候选各自通过后，按精确哈希成对提升为产品输入

```powershell
.\.venv\Scripts\python.exe -B ci\script\prepare_mpv_player_dependency.py `
  --repository . `
  --promote-m9-mpv-candidate <m9-player-thin-candidate> `
  --promote-m9-ffmpegkit-candidate <m9-ffmpegkit-thin-candidate> `
  --source-closure-profile m9_ffmpeg_major_candidate `
  --native-readelf <windows-host-ndk-llvm-readelf.exe> `
  --expected-mpv-sha256 f52aca6f35c651be7aab55f2efe6b5f40180d1ebaeb1404cc446470bf8deb6a4 `
  --expected-ffmpegkit-sha256 7e6b4c20a93dfb3b90bc7f3c5d724cf657b70e2469ea4f2b1110396a8d345394
```

### 仅更新 FFmpegKit 补丁版本，保持播放器产品逐字节不变

```powershell
.\.venv\Scripts\python.exe -B ci\script\prepare_mpv_player_dependency.py `
  --repository . `
  --promote-m9-ffmpegkit-patch-candidate <m9-ffmpegkit-thin-candidate> `
  --source-closure-profile m9_ffmpeg_major_candidate `
  --native-readelf <windows-host-ndk-llvm-readelf.exe> `
  --expected-ffmpegkit-sha256 7e6b4c20a93dfb3b90bc7f3c5d724cf657b70e2469ea4f2b1110396a8d345394
```

## 必需产物审计

1. 两个 AAR 的 SHA-256、完整成员与唯一 C++ 所有者。
2. APK 仅 arm64、无重复 native 名称；每个 ELF class/machine 与所有 PT_LOAD 对齐。
3. mpv 闭包没有普通 libav/libsw 依赖，预期 libmp SONAME 与 DT_NEEDED 完整。
4. Mbed TLS、HTTPS、FFmpeg 版本/符号与全部 C++ import 闭合。
5. 最终 APK 时间、大小、SHA-256、包名/版本、Debug V2 签名与 `zipalign -c -P 16 -v 4`。

## 历史证据与当前验收

2026-08-17 的双 M9/r6 封板记录包含 19 个 native 成员对齐、命名空间隔离、版本 marker、GPL/HarfBuzz/filter 以及 C++ 单副本检查。2026-08-16 的 M8 和 stage-13、后续 r2/r3/r4 APK 都只是历史比较；完整逐项原文可从 `145f378900d663b812d9df3c73de15fbad56139c` 的本文件和 [FFmpeg 专项](../../TODO/ffmpeg_runtime_completion/index.md) 追溯。

历史全 APK 审计另观察到 libonnxruntime 与 libsherpa-mnn-jni 的 RPATH/RUNPATH，它们不在播放器/FFmpegKit 的 19 ELF 范围；不能把局部闭包通过表述为全部 native 均无此标记。

设备解码、GPU、Anime4K、真实网络与用户验收保持各专项 `verification_pending`，不由源码、Gradle 或 APK 静态审计替代。
