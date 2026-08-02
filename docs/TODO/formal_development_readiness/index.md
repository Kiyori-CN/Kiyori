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
- 自动门禁：M-02 至 M-05 的当前可重现 checkpoint 为 `main@6b6493a0`。完整 architecture 为 `phase=m03`、`errors=[]`，CI Python 为 `166/166`，Android JVM 报告为 `137` 个 suite、`822` 个测试、零失败、零错误、零跳过；formal readiness、fresh clone、working-tree Markdown 和 `git diff --check` 均通过。QD-01 至 QD-04 已把 fresh full lint 从 `27 errors / 287 warnings / 3 hints` 收敛到 `0 errors / 28 warnings / 1 hint`；QD-05A 删除零消费者的 MediaPipe Text 并升级 Filament、ONNX Runtime、Junrar 与 Jsoup 后，当前为 `0 errors / 21 warnings / 1 hint`。Gradle 控制台不计入 `LintBaseline` 状态提示。剩余 compile SDK、Kotlin plugin、第三方 TLS 字节码债务和完整 Kotlin 重编译暴露的历史 compiler 警报继续按 [Stage 4 前质量债务与开发就绪精确清单](../kiyori_architecture_refactor/21_quality_debt_and_stage4_readiness_manifest.md) 收口。现有 baseline 为 `5786` 条，SHA-256 `E382817E50E5B7FD9B6E62D642C8A6E44488772F690267CD797111E41DF3FCA4`；QD-03/QD-04 的结构化交集只删除 5 条已失效记录，没有吸收 current-only 警报或新增 suppress
- 远端 CI：workflow 文件均为 active，但 Kiyori 私有仓库的 Actions 总开关为 `enabled=false`；已确认推送事件存在，workflow run 与 commit check 均为 0。重新启用前，本地门禁不能表述为远端 CI 已通过
- Debug 构建：checkpoint 工作树执行 `:app:assembleDebug --no-daemon --console=plain`，结果为 `233` 个 actionable task、`24` 个 executed、`209` 个 up-to-date，`verifyDebugPlayerRuntimePackaging` 执行。APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `477957302` 字节，包名 `com.kiyori`，版本 `45 / 0.1.0`，SHA-256 为 `E6A5E78CFB4441399DC36D83583BF6F441441E3736A85A17759695D73ACF790C`。Application、稳定 launcher、`:crash`、`:repair`、`:player`、Debug V2 签名与 `zipalign -c -P 16 -v 4` 均通过
- 16KB native：APK 仅包含 `arm64-v8a`，共 `53` 个 native library，零重复 basename；10 个播放器目标库保持单副本，Gradle 的 player native input/runtime packaging 检查和 16 KB ZIP 对齐通过。ELF 输入哈希、owner、符号闭包和 `PT_LOAD` 合同继续由 [Android 工具链与 16 KB native](../android_toolchain_16kb_native/index.md) 与 Gradle verify task 管理
- 新鲜克隆：父仓库 `main@6b6493a0` 已通过克隆、`terminal` 初始化及 gitlink 对齐检查；提交后的主工作树干净，因此 M-02 至 M-05 当前结果已纳入新鲜克隆证据
- Android 身份迁移：已记录为新 application ID，旧 Operit 安装不能直接覆盖
- 插件与存储修复：源码已接入生产 ToolPkg 生成任务、插件/脚本包市场分类、独立的 Operit `1.12.0+4` 市场兼容版本和 `Download/Kiyori` 路径所有者。CI 已修复生产白名单路径，并把白名单变更纳入 ToolPkg 与完整 Android lane；本地 APK 已验证全部 42 个生产包。实时市场代表资产的下载、SHA 与 ToolPkg 结构已验证；Android 市场安装、抽屉/输入框插件与 AI 首页快速点击仍待真机验证
- 真机验收：待验证，不由静态检查或 Debug 构建代替

详细约束见：[品牌与兼容性](1_brand_and_compatibility.md)、[可复现开发](2_reproducible_development.md)、[CI 与安全门禁](3_ci_and_security_gates.md)、[身份与数据迁移](4_identity_and_data_migration.md)、[发布与真机验收](5_release_and_device_acceptance.md)。
