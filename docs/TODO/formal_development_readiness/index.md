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
- 自动门禁：2026-07-22 使用项目 `.venv` 执行正式准备检查和 `ci/test`，门禁通过，47 项 Python 测试通过
- Debug 构建：2026-07-22 `assembleDebug` 和 `:app:lintDebug` 通过，产物为 `app/build/outputs/apk/debug/app-debug.apk`，包名 `com.kiyori`，版本 `45 / 0.1.0`，SHA-256 为 `42D927B47F2656BFDB92CAE2C9FE97CFCD7F831E5931BD4C98434142E8F2C7DE`
- 新鲜克隆：候选提交形成后、推送前执行，确保验证的是本次准备基线而不是旧 `HEAD`
- Android 身份迁移：已记录为新 application ID，旧 Operit 安装不能直接覆盖
- 真机验收：待验证，不由静态检查或 Debug 构建代替

详细约束见：[品牌与兼容性](1_brand_and_compatibility.md)、[可复现开发](2_reproducible_development.md)、[CI 与安全门禁](3_ci_and_security_gates.md)、[身份与数据迁移](4_identity_and_data_migration.md)、[发布与真机验收](5_release_and_device_acceptance.md)。
