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

- 品牌/兼容性：部分完成。用户可见的终端名称已改为 Kiyori，协议、存储、备份和市场生态标识保留
- 自动门禁：2026-07-23 使用项目 `.venv` 执行正式准备检查通过，完整 Python 测试 `50/50`、其中 Android dependency 测试 `13/13` 通过；强制 Kotlin 复采为 436 条既有 warning，最终 Lint 为 38 warnings + 2 hints，baseline 未修改。本轮高风险候选、产品壳新增告警、PrivateResource 和旧 library manifest 告警均已修复
- 远端 CI：workflow 文件均为 active，但 Kiyori 私有仓库的 Actions 总开关为 `enabled=false`；已确认推送事件存在，workflow run 与 commit check 均为 0。重新启用前，本地门禁不能表述为远端 CI 已通过
- Debug 构建：当前工作树的 `assembleDebug` 通过，最终 APK 为 `app/build/outputs/apk/debug/app-debug.apk`，生成于 `2026-07-23 05:36:06 +08:00`，大小 `415154103` 字节，包名 `com.kiyori`，版本 `45 / 0.1.0`，SHA-256 为 `E9DB93145EAA5724701AF8883F9A9D473AF64836597AAB75106D71A0B9B849B5`
- 16KB native：`zipalign -P 16` 通过；44 个 arm64 `.so` 中 34 个 ELF 已对齐，剩余 9 个为 ffmpeg-kit、1 个非 ELF `libsudo.so`；详见 [Android 工具链与 16 KB native](../android_toolchain_16kb_native/index.md)
- 新鲜克隆：候选提交形成后、推送前执行，确保验证的是本次准备基线而不是旧 `HEAD`
- Android 身份迁移：已记录为新 application ID，旧 Operit 安装不能直接覆盖
- 真机验收：待验证，不由静态检查或 Debug 构建代替

详细约束见：[品牌与兼容性](1_brand_and_compatibility.md)、[可复现开发](2_reproducible_development.md)、[CI 与安全门禁](3_ci_and_security_gates.md)、[身份与数据迁移](4_identity_and_data_migration.md)、[发布与真机验收](5_release_and_device_acceptance.md)。
