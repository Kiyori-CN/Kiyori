---
title: 旧协议文本实现记录
status: superseded
document_type: agreement-implementation-record
agreement_version: 2026-08-09-r3
last_reviewed: 2026-08-09
---

# 旧协议文本实现记录

本目录原有正文属于 Operit 启动合同，已经停止作为 Kiyori 产品文案依据。当前权威实现为：

- `app/src/main/res/values/strings.xml` 中的
  `kiyori_onboarding_user_agreement_*`
- `app/src/main/res/values/strings.xml` 中的
  `kiyori_onboarding_privacy_policy_*`
- `app/src/main/java/com/ai/assistance/operit/ui/features/agreement/screens/KiyoriAgreementScreen.kt`

当前中文协议版本为 `2026-08-09-r3`。用户协议与隐私政策是两份独立文档；首次启动不记录
打开状态、阅读进度或滚动位置，只由“我已阅读并同意”复选框控制“同意并继续”按钮。

现行正文明确 Kiyori 是开源 AI 浏览器，覆盖浏览器与网站数据、AI 和第三方服务、设备端
数据、权限与高影响能力、扩展生态、数据管理、合法使用以及 GPL-3.0-or-later 许可边界。
其他语言不属于本轮中文首启验收范围。

本轮实现、测试、构建与待真机验证项记录在
`docs/TODO/kiyori_first_run_experience/3_implementation_and_validation.md`。
