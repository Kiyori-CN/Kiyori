# 验证、构建、Git 与真机验收

## 每个里程碑的本地验证

1. 受影响模块的 JVM 或纯逻辑测试
2. `\.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`
3. `git diff --check`
4. 必要的 Kotlin 编译或 Android 单元测试
5. `\.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
6. 核对 `app/build/outputs/apk/debug/app-debug.apk`

阶段 8 与阶段 9 必须分别完成以上六项。阶段 8 的 APK、native 清单和日记事件稳定后才允许开始阶段 9；
最终构建不得用阶段 8 制品冒充。

APK 证据至少记录：绝对路径、生成时间、大小、SHA-256、application ID、versionCode、versionName、minSdk、targetSdk 和 Android Debug v2 签名。涉及 native 依赖时额外记录 ABI、ZIP 16KB 对齐和每个 arm64 ELF 的 `PT_LOAD` 对齐。

播放器 native 审计还必须核对：

- APK 只包含 `arm64-v8a`，同一 SONAME/文件名只出现一次
- `libmpv.so`、`libplayer.so`、七个 `libmp*.so` 播放器 FFmpeg、FFmpegKit 的九个 native 库与唯一
  `libc++_shared.so` 均存在
- 不含旧 FFmpeg `n6.0` 库、未命名空间化的 mpv FFmpeg 依赖或旧手工 C++ runtime
- `libmpv.so`/`libplayer.so` 的 FFmpeg 未定义符号全部由 APK 内隔离的 `libmp*.so` FFmpeg `n8.1.2`
  提供；FFmpegKit 继续只解析正常 `libav*.so` 名称
- `libmpformat.so` 必须保留 `--enable-mbedtls`、Mbed TLS 3.6.6 和 HTTPS 协议证据；播放器初始化必须
  设置固定 `tls-ca-file`、`tls-verify=yes` 和 `ytdl=no`
- `libmpv.so` 所需的 C++ 未定义符号全部由 APK 内唯一 `libc++_shared.so` 提供；至少固定检查 float/double
  两个 `__from_chars_floating_point` 符号
- 固定上游输入哈希、薄 AAR 输出哈希和最终 APK 中每个 native entry 哈希可追溯
- 根 `LICENSE`、`NOTICE`、`PLAYER_NATIVE_STACK.md` 与应用开源许可证列表对同一来源/版本/许可陈述一致

## 当前最新本地制品

### 2026-07-29 原生 HTTP/HTTPS 在线播放修复

- Debug APK：`D:\10_Project\Kiyori\app\build\outputs\apk\debug\app-debug.apk`
- 时间 `2026-07-29 14:22:10 +08:00`，大小 `474822245` 字节，SHA-256
  `969C20C2FC6E1A401CFF51812EC1936AB674ECF5B2F51D0A7AB1589FBB35A6A7`
- `applicationId com.kiyori`、`versionCode 45`、`versionName 0.1.0`、min 26、target 34、compile 36；
  Android Debug v2 与证书 SHA-256
  `E72AD950D07ADBEDFB9C909C48D922FDDB3560677012DA79B686A127867AE902` 验证通过
- APK 只含 `arm64-v8a`；53 个 native entry 无重复 basename，52 个 AArch64 ELF 的全部
  `PT_LOAD >= 0x4000`，唯一非 ELF 为既有 2 字节 `libsudo.so`
- 正常名称 FFmpegKit 九库与播放器七个 `libmp*.so` FFmpeg 同时存在且文件名互斥；
  `libmpv.so` / `libplayer.so` 对正常 `libav*.so` 的 `DT_NEEDED` 为零
- `libmpv.so` 需要 251 个、`libplayer.so` 需要 34 个版本化 FFmpeg 符号，去重并集 256 个；
  namespaced mpv FFmpeg 提供全部定义，缺失 0
- `libmpv.so` / `libplayer.so` 的 99 个唯一 C++ 引用在隔离 FFmpeg 与唯一
  `libc++_shared.so` 中缺失 0；float/double `__from_chars_floating_point` 均存在
- `libmpformat.so` 包含 FFmpeg `n8.1.2`、`--enable-mbedtls`、Mbed TLS `3.6.6`、
  `mbedtls_ssl_handshake` 和 HTTPS 证据；`zipalign -c -P 16 -v 4` 为
  `Verification successful`
- `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 46s`，233 个任务零失败，构建末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- 未安装 APK、未操作设备；真实 HTTPS MP4、HLS、Referer/Cookie、重定向、证书失败与网速/缓冲状态
  保持 `verification_pending`

### 2026-07-28 阶段 9 播放器与浏览器嗅探

阶段 9 播放器与浏览器嗅探最终制品：

- `D:\10_Project\Kiyori\app\build\outputs\apk\debug\app-debug.apk`
- 时间 `2026-07-28 10:59:29 +08:00`，大小 `468740360` 字节，SHA-256 `44642BF9A4EE713D2566B6FEF2E77865067E7CB2D030F705D139075003B1ED03`
- `applicationId com.kiyori`、`versionCode 45`、`versionName 0.1.0`、`minSdk 26`、`targetSdk 34`、`compileSdk 36`
- Android Debug v2 签名通过，证书 SHA-256 `E72AD950D07ADBEDFB9C909C48D922FDDB3560677012DA79B686A127867AE902`；`zipalign -c -P 16 -v 4` 为 `Verification successful`
- APK 只含 `arm64-v8a`；46 个 native 文件名无重复，45 个 ELF 的每个 `PT_LOAD` 最小对齐均至少 `0x4000`；既有 2 字节 `libsudo.so` 是唯一非 ELF
- `libmpv.so` 需要 251 个、`libplayer.so` 需要 34 个版本化 FFmpeg 动态符号，去重并集 256 个；APK 内唯一 `n8.1.2` 库集合全部提供，缺失 0；版本字符串为 mpv `v0.41.0-dev-g2339eb727` 与 FFmpeg `n8.1.2`
- 九个 arm64 ELF 依赖唯一 `libc++_shared.so`，共 146 个 C++ 引用、121 个唯一 C++ 符号，缺失 0；
  `libmpv.so` 与 runtime 均为 Android Clang `21.0.0` build `13989888`，现场缺失的 float/double
  `__from_chars_floating_point` 两个导出均存在
- mpv AAR 为 `29075301` 字节、SHA-256 `ECDC87102E7B4A9BB9C9D46AF863F7C32B25B9AAB7A161614AF6646FFD603F70`；
  FFmpegKit arm64 AAR 为 `29989550` 字节、SHA-256 `1A30A94226BF2157927EC6EDBB20154F9A1C1C53580F59CF55EFE46DB87A5AB3`
- 本轮 native 依赖 Python 测试 `18/18`、输入门禁、构建后 runtime 门禁和 Debug 构建通过；此前全量 JVM
  `555/555` 与播放器行为测试结论未被本轮二进制所有权修改改变
- 用户提供的 `ThreadPoolForeg` 崩溃已修正：`shouldInterceptRequest` 只读 `WebSession.appliedUserAgent`，不再从后台线程调用 `WebView.getSettings()`；该修正后的 APK 才是本节制品
- `MPV_EVENT_END_FILE` 不再把 replace/stop 误判成自然结束；`eof-reached`、JNI `LinkageError` 可见错误、浏览器悬浮后台暂停和 Android 26-28 截图保存路径均已进入本节制品
- 用户 `2026-07-28 10:06` 真机截图中的 `dlopen` 缺失 C++ 符号已在该制品的输入门禁和 APK 审计中消除；
  重新安装后的真实播放、硬件解码、Anime4K 性能、手势、悬浮/全屏往返、后台行为和站点兼容仍为
  `verification_pending`

阶段 8 播放器基础制品：

- `D:\10_Project\Kiyori\app\build\outputs\apk\debug\app-debug.apk`
- 时间 `2026-07-28 01:49:07 +08:00`，大小 `468740920` 字节，SHA-256 `E92FF610D80F9F632EEC3AE08FA857760CD8E65D9FB807DE2829AAE968C2D6C6`
- `applicationId com.kiyori`、`versionCode 45`、`versionName 0.1.0`、`minSdk 26`、`targetSdk 34`、`compileSdk 36`
- Android Debug v2 签名与 `zipalign -c -P 16 -v 4` 通过
- APK 仅含 `arm64-v8a`；46 个 native 名无重复，45 个 ELF 的最小 `PT_LOAD` 均至少 `0x4000`，既有 `libsudo.so` 为唯一非 ELF
- `libmpv.so`/`libplayer.so` 所需 254 个 FFmpeg 动态符号由统一 `n8.1.2` 栈全部提供，缺失 0；mpv 字符串为 `v0.41.0-dev-g2339eb727`
- 该制品只冻结阶段 8 证据；阶段 9 完成后必须以新的最终 APK 替换，不得直接交付此文件

- 浏览器与负一屏共享下载抽屉切片：`D:\10_Project\Kiyori\app\build\outputs\apk\debug\app-debug.apk`
- 生成时间 `2026-07-27 13:31:19 +08:00`，大小 `449493090` 字节，SHA-256 `89CADC2171FC9B07202F9CB084CBC87F2A48ECB7AA17BBA830978C38AF5C924C`
- `applicationId com.kiyori`、`versionCode 45`、`versionName 0.1.0`、`minSdk 26`、`targetSdk 34`、`compileSdk 36`
- Android Debug V2 签名通过；`zipalign -c -P 16 -v 4` 为 `Verification successful`
- 本轮 Shell 回归定向 `KiyoriShellStateTest` 为 `35/35`，覆盖抽屉显示、退出动画、完全隐藏、AI 非根路由下负一屏入口、Back 优先级和完整 Shell 保存恢复；此前下载全链路五组测试基线为 `80/80`。Formal readiness、旧拆分式 Shell 保存变量零匹配与 `git diff --check` 通过
- `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 35s`，231 个任务零失败，其中 31 个执行、200 个为 up-to-date。该制品保留下载抽屉切片，并新增 pnpm 完成事件检测修正、悬浮搜索页 Back dispatcher owner 和 Gradle native ripgrep 打包。软件首页与负一屏实际触摸、抽屉视觉与拖动、菜单周围点击关闭、长按动作、输入法遮挡、SAF 文件与权限、Android `DownloadManager`、真实 M3U8 remux、APK 自动清理、PNPM 卡、AI 文件搜索和悬浮搜索 Back 仍为 `verification_pending`

## Git 门禁

- 当前 Goal 默认只保留本地修改和 Debug APK，不创建提交、不推送
- 用户在当前任务中另行明确授权提交时，才再次确认 `main`、HEAD、工作树和 staged allowlist
- 禁止纳入 `work/`、APK、AAB、Gradle cache、`.venv`、凭据、运行日志和任务日记
- commit message 只描述当前里程碑
- 另行授权推送后，`git push origin main` 成功才使用 `git ls-remote origin refs/heads/main` 核对 SHA
- 已授权推送发生网络失败时，只允许有界重试同一提交；不得产生额外空提交或强制推送

## 终局真机清单

- overlay 背景覆盖状态栏，图标明暗正确，实体/手势 Back 按优先级工作
- 人工普通/无痕窗口可被 AI list、select、snapshot、click 和 type
- 普通与无痕网站数据隔离，最后一个无痕窗口关闭后数据消失
- 首页、全屏搜索、窗口缩略图、Settings Home、下载中心和负一屏在手机、平板、横屏和旋转后正常
- 下载队列、暂停、恢复、失败、重试、打开、删除和设置生效
- 阅读模式、网站配置、广告标记和工具箱不会刷新当前网页
- 视频 Intent 进入播放器，播放、seek、手势、方向变化、悬浮和全屏转场正常
- 浏览器播放器三态切换不刷新、重载、重缓存或改变网页滚动/表单状态

## 交付状态

自动检查和 Debug 构建完成后，只能声明本地实现与构建通过。状态栏、系统 Back、设备 WebView Multi-Profile、OEM overlay、硬件解码、手势和站点兼容必须由用户安装最终 APK 后验收，因此最终任务状态为 `verification_pending`，直到用户反馈关闭。
