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

## 当前结论

- 品牌/兼容性：待验证。Android 系统界面、默认角色、WebChat、工作区模板、当前宿主沙箱路径、预置 ToolPkg 与 Terminal/Ubuntu 展示已统一为 Kiyori；通知的状态栏单色图标、iQOO 卡片大图标和 Manifest 应用图标资源 ID 已分别指定 Kiyori 资产；namespace、协议、存储、备份、Terminal rootfs 和市场生态标识保留，真机显示仍待验收
- 自动门禁：`main@2b1fe99d` 的 2026-07-23 基线使用项目 `.venv` 通过正式准备检查、完整 Python 测试 `50/50`、Android dependency 测试 `13/13`、新鲜克隆与 `terminal` 初始化检查。该基线的 AGP 9.3/Kotlin 2.3.21 强制 Kotlin 复采为 610 条源码 warning，最终 Lint 为 46 warnings + 2 hints，baseline 未修改。本轮路径审计、市场兼容版本修复和两项 Kotlin/Compose 告警优化已重新通过 Debug 构建；完整 Lint 和真机验收仍未在本轮执行
- 远端 CI：workflow 文件均为 active，但 Kiyori 私有仓库的 Actions 总开关为 `enabled=false`；已确认推送事件存在，workflow run 与 commit check 均为 0。重新启用前，本地门禁不能表述为远端 CI 已通过
- Debug 构建：`2026-07-23 23:18:49 +08:00` 的 APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `458000918` 字节，包名 `com.kiyori`，版本 `45 / 0.1.0`，SHA-256 为 `3ECD72FF3EB8CB82E45411640A6289E1CCAE6D0681A3487DB3CAE0A3EDFF0D7F`。本次构建验证了 Kiyori 品牌、图标、当前应用路径、WebChat 资产与全部 11 个预置 ToolPkg；编译后 Manifest 的 application icon 全部解析到新的 `ic_kiyori_launcher`，真机 Launcher、系统文件选择器、快捷方式、通知、Terminal 和文件系统验收仍待执行
- 16KB native：`zipalign -P 16` 通过；43 个 arm64 `.so` 中 33 个 ELF 已对齐，剩余 9 个为 ffmpeg-kit、1 个非 ELF `libsudo.so`；详见 [Android 工具链与 16 KB native](../android_toolchain_16kb_native/index.md)
- 新鲜克隆：当前候选提交已通过父仓库克隆、`terminal` 初始化及 gitlink 对齐检查
- Android 身份迁移：已记录为新 application ID，旧 Operit 安装不能直接覆盖
- 插件与存储修复：源码已接入生产 ToolPkg 生成任务、插件/脚本包市场分类、独立的 Operit `1.12.0+4` 市场兼容版本和 `Download/Kiyori` 路径所有者。实时市场代表资产的下载、SHA 与 ToolPkg 结构已验证；当前源码构建、Android 市场安装、抽屉/输入框插件与 AI 首页快速点击仍待验证
- 真机验收：待验证，不由静态检查或 Debug 构建代替

详细约束见：[品牌与兼容性](1_brand_and_compatibility.md)、[可复现开发](2_reproducible_development.md)、[CI 与安全门禁](3_ci_and_security_gates.md)、[身份与数据迁移](4_identity_and_data_migration.md)、[发布与真机验收](5_release_and_device_acceptance.md)。
