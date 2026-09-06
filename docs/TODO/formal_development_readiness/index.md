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
├── 5_release_and_device_acceptance.md
└── 06_historical_evidence.md
```

## 验收顺序

1. 先阅读品牌与兼容性边界，避免把实现标识误当成可批量替换的品牌文字
2. 使用新鲜克隆检查确认父仓库和终端子模块可以独立复现
3. 运行正式开发检查、按影响面选择的测试和 Debug 构建
4. 在 Android 设备上完成启动、图标、About、通知、助手入口和终端入口验收
5. 只有发布身份、签名、隐私、许可证和回滚策略单独评审通过后，才进入正式发行准备

## 当前验收边界

| 领域 | 状态与权威入口 |
| --- | --- |
| 品牌与兼容性 | 源码契约由正式检查验证；启动器、通知与 OEM 展示保持待真机验收，见 [品牌清单](1_brand_and_compatibility.md) |
| 可复现开发 | 使用固定工具链、native 输入与 terminal gitlink；新候选提交需按 [可复现开发](2_reproducible_development.md) 验证 |
| CI 与安全 | 本地检查与远端 Actions 分别记录；过去的 Actions 禁用观察不能当作当前实时状态，见 [CI 清单](3_ci_and_security_gates.md) |
| 身份与数据 | Kiyori 是独立 application ID，旧 Operit 通过显式备份导入，见 [迁移清单](4_identity_and_data_migration.md) |
| 发布与设备 | 未发布；Debug 通过不关闭设备、签名与正式发行边界，见 [验收清单](5_release_and_device_acceptance.md) |

## 证据归档

此前的工程收口计划、测试数量、Lint baseline、native 审计、APK 哈希、市场适配和远端 CI 观察完整保存在 [历史验证证据](06_historical_evidence.md)。它们描述各记录日期的状态，不是当前工作区验收结果。

后续修改在对应专项 `index.md` 记录日期、命令、产物和未完成验收，再由 [开发任务索引](../README.md) 导航；不在本入口反复追加长日志。本次文档整理证据见 [文档体系专项](../documentation_system_refinement/index.md)。

## 验证入口

在仓库根目录使用项目 `.venv`：

```powershell
.\.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main
```

仓库修改收尾按 [AGENTS](../../../AGENTS.md) 串行构建 Debug APK。单元测试、Lint 与其他检查依据当前任务授权和影响面选择；候选提交形成后，新鲜克隆检查验证该提交及固定 terminal gitlink。所有结果都需注明证据等级。
