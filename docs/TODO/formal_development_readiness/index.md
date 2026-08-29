---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
---

# 正式开发准备

本 TODO 定义 Kiyori 从品牌迁移基线进入持续开发前必须具备的工程条件。它是开发入口和验收清单，不是发布公告；完成状态以自动检查、构建证据和真机验收分别记录。

## 当前基线

- 父仓库：`main`，Kiyori 远端只保留 `main`
- 终端模块：`terminal` 指向 KiyoriTerminalCore 的固定 gitlink
- Android application ID：`com.kiyori`
- 开发版本：`versionCode 45`、`versionName 0.1.0`
- 上游兼容 namespace：`com.ai.assistance.operit` 与 `com.ai.assistance.operit.terminal`

## 文档树

```text
formal_development_readiness/
├── index.md
├── 1_brand_and_compatibility.md
├── 2_reproducible_development.md
├── 3_ci_and_security_gates.md
├── 4_identity_and_data_migration.md
└── 5_release_and_device_acceptance.md
```

## 验收顺序

1. 先阅读品牌与兼容性边界，避免把实现标识误当成可批量替换的品牌文字
2. 使用新鲜克隆检查确认父仓库和终端子模块可以独立复现
3. 运行正式开发门禁、现有 Python 单元检查和 Debug 构建
4. 在 Android 设备上完成启动、图标、About、通知、助手入口和终端入口验收
5. 只有发布身份、签名、隐私、许可证和回滚策略单独评审通过后，才进入正式发行准备

## 遗留门禁修复计划

本轮在不改变产品行为、不扩大 Lint baseline 且不伪造 native 兼容性的前提下，完成以下工程收口：

1. 新增仓库级换行契约，阻止 Windows `core.autocrlf` 与 Node 生成任务反复制造无内容差异；不对历史尾随空格做全库改写
2. 使用构建目录中的临时完整 Lint baseline 与已审阅 baseline 求交集，只删除已失效记录，不吸收当前可见告警
3. 为固定 FFmpegKit 输入 AAR 与 mpv 输入 AAR 增加输入哈希、确定性 arm64 输出、互斥 native owner、
   C++ 符号闭包、ABI、ELF 和 16 KB `LOAD` segment 对齐验证；旧本地 FFmpeg AAR 与不合格播放器制品
   必须在 Gradle 构建前失败
4. 只实施有明确使用面和回归证据的同系列低风险补丁更新；compile SDK、Kotlin、Filament 和文档处理栈的策略升级不混入本轮
5. 清理本地构建缓存后串行运行 Python 门禁、Android 测试、Lint、Debug APK 构建、ToolPkg 与最终 APK 审计

远端 GitHub Actions 总开关、Linux/WSL 下的 ffmpeg 重建、Release 签名与真机验收仍是独立权限和验收边界，不由本地 Debug 通过替代。

## 当前结论

- 品牌/兼容性：待验证。Android 系统界面、默认角色、WebChat、工作区模板、当前宿主沙箱路径、预置 ToolPkg 与 Terminal/Ubuntu 展示已统一为 Kiyori；通知的状态栏单色图标、三个 iQOO 卡片大图标和 Manifest 应用图标资源 ID 已分别指定 Kiyori 资产，其中卡片大图标直接嵌入 Bitmap 以避开 OEM 资源缓存；namespace、协议、存储、备份、Terminal rootfs 和市场生态标识保留，真机显示仍待验收。当前 Kiyori 软件图标不使用版本后缀；两张源码 PNG、旧 Kiyori 活动资源和 APK 内资源逐字节一致，SHA-256 分别为 `2220044E62C8BDAD0AFB9BB03C00AC2D106AB55D417EEFA26AB52E0A833DBE01` 与 `9AAF6FA98EA65D8FA5D82F5017C8B9924C7D430AC6EE2A8C1588B3CDC4E9D2C3`，并非 Operit 的 `ic_launcher_simple` 机器人矢量
- 自动门禁：M-02 至 M-05 的历史封板 checkpoint 为 `main@6b6493a0`，依赖批次历史提交为 `main@b1a8e4c9`。当前 FFmpeg 收口已重新验证 Python `218/218`、architecture `phase=m03`、formal readiness、WebChat、GitHub 示例、WASM ToolPkg、生产 ToolPkg 白名单和 `ci.test.test_toolpkg_sync 7/7`。完整 Gradle/JVM/AndroidTest/Lint 聚合矩阵为 `BUILD SUCCESSFUL in 7m54s`、`431` tasks（`18` executed / `413` up-to-date），AndroidTest Kotlin/Java 编译通过。当前完整 Lint baseline 仍为 `5279` 条，结构化交集 `retained=5256 / stale=24 / current-only=23`，SHA-256 `B0A52E2B1C42516B84BB1B22E0940A8DB3130840D5D754E09BDD2444A366D621`；App 为 `0 errors / 23 warnings`，Terminal 为 `0 errors / 1 warning`，其余模块无诊断，未新增 suppress、lint disable 或 baseline 条目。App 的 20 条依赖提示不在 FFmpeg 收尾中机械升级，3 条 `UseKtx` 因现有同步 `commit()` Boolean 失败检测合同保留；Terminal 的 `ChromeOsAbiSupport` 与 arm64-only 产品合同一致。ToolPkg 预构建已统一复用 npm lockfile，不再混用 pnpm 改写安装树。llama.cpp 与 MNN 已分别锁定到 `885c5bbe8e04dc78db25beb911a2715312ad7b54` 和 `ea44a3ebd5dd6348eea501047b17c43aa3ecccb6`；正式门禁和 CMake 单元测试拒绝移动 ref 与无效 40 位 SHA
- 远端 CI：workflow 文件均为 active，但 Kiyori 私有仓库的 Actions 总开关为 `enabled=false`；已确认推送事件存在，workflow run 与 commit check 均为 0。重新启用前，本地门禁不能表述为远端 CI 已通过
- Debug 构建：包含 FFmpegKit `8.1.7-kiyori-n9.0.1-r6` / OpenH264 `v2.6.0` 的规定 `:app:assembleDebug --no-daemon --console=plain` 于 2026-08-19 播放器真机日志与无缝进度/缓存流转修复后重新验证为 `BUILD SUCCESSFUL in 1m4s`，`232` 个任务中 `23` 个 executed、`209` 个 up-to-date；唯一 launcher、player native input 与 runtime packaging 门禁通过。APK 路径为 `app/build/outputs/apk/debug/app-debug.apk`，写入时间 `2026-08-19 20:22:21 +08:00`，`472553854` bytes，SHA-256 `E4BA494F49BFB2959B8E4B9A359EF2820F5E64A38CC3223F8FC8A8D4429C1E1C`；包名/版本/SDK 为 `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，Android Debug V2 单 signer 与 16 KB zipalign 通过
- 16KB native：APK 仅包含 `arm64-v8a`，共 `51` 个 `.so`，零重复 basename；加上生成式 `assets/operit_shell_exec` 共 `52` 个 AArch64 ELF64，`153` 个 `PT_LOAD` 为 `0x4000 × 151` 与 `0x10000 × 2`。播放器 10 个与 FFmpegKit r6 的 9 个 native payload 均与产品 AAR 逐字节一致；两套 FFmpeg 均为 `n9.0.1`，FFmpeg 8 markers/majors、normal/namespaced basename 交集和相关 19 个 ELF 的 `RPATH/RUNPATH` 均为 0。APK 的 `44` 个 DEX 包含 FFmpeg runtime service、media-information parser 与 probe executor；`libffmpegkit.so` 包含 r6 wrapper、FFmpegKit log callback 与 Binder patch marker，`libavutil.so` 包含 GPL/HarfBuzz/`eq/boxblur` configure marker，`libavfilter.so` 包含 `drawtext/eq/boxblur`。GPLv3 resource、`ffmpeg.js` 和生产 ToolPkg 43 项均与源码合同一致，无 test-mode 资产残留。launcher 只依赖五个 Android 系统库，不依赖 `libc++_shared.so`；Gradle 的 player native input/runtime packaging 检查和 16 KB ZIP 对齐通过。全 APK 扫描另观察到两个其它 owner `libonnxruntime.so`、`libsherpa-mnn-jni.so` 带 `RPATH/RUNPATH`，不属于播放器/FFmpegKit 19 个 closure ELF。APK 内只有一份共享 AAPT2，两个旧模板路径不存在。ELF 输入哈希、owner、符号闭包和 `PT_LOAD` 合同继续由 [Android 工具链与 16 KB native](../android_toolchain_16kb_native/index.md) 与 Gradle verify task 管理
- 2026-08-29 Operit/Market v2 适配后的最终 Debug APK 生成于 `2026-08-29 21:16:48 +08:00`，为 `503686789` bytes，SHA-256 `FBCD24F29EF7E051AA0F74F1ACA19B2AAFEE2198CE00DD51F463A60A92D2C4E7`；`com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`、唯一 launcher、Android Debug V2 单 signer 和 16 KiB zipalign 均通过。APK 有 5512 个无重复 entry、44 个 DEX、仅 `arm64-v8a` 的 53 个 `.so` 且 basename 无重复；加 shell launcher 共 54/54 个 ELF64/AArch64，161 个 `PT_LOAD` 为 `0x4000 × 159 + 0x10000 × 2`。49 项生产 ToolPkg 与白名单名称集合完全一致；真机与真实 Market 工作流仍待验证
- 新鲜克隆：父仓库基线与 `terminal@e11053d60dc02b0d162ee4a3a2f655c28c1afcbb` 的 clone/gitlink 门禁已通过；候选提交形成后仍必须重新执行 `check_fresh_clone.py`，并在最终交付中核对 local、tracking 与远端 ref
- Android 身份迁移：已记录为新 application ID，旧 Operit 安装不能直接覆盖
- 插件与存储修复：源码已接入生产 ToolPkg 生成任务、插件/脚本包市场分类、独立的 Operit `1.12.1+3` 市场兼容版本和 `Download/Kiyori` 路径所有者。当前合同在原 `+9` 能力上补齐资源型 ToolPkg logo、市场 owner 撤回后按原 entry ID 续发、contributor 说明 partial patch、损坏安装 marker 单条隔离、Compose DSL 六模式 picker 和 `ChatInfo` 角色卡/群组 ID；Kiyori 的 `com.kiyori / 45 / 0.1.0` 产品版本轴不变。实时市场资产的下载、SHA 与 ToolPkg 结构已验证；Android 市场动态写入、真实插件 logo、六类 picker、抽屉/输入框插件与 AI 首页快速点击仍待真机验证
- 真机验收：待验证，不由静态检查或 Debug 构建代替

详细约束见：[品牌与兼容性](1_brand_and_compatibility.md)、[可复现开发](2_reproducible_development.md)、[CI 与安全门禁](3_ci_and_security_gates.md)、[身份与数据迁移](4_identity_and_data_migration.md)、[发布与真机验收](5_release_and_device_acceptance.md)。
