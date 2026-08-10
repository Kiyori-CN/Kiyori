---
title: 已发布用户协议版本确认
status: superseded
document_type: implementation-plan-index
last_reviewed: 2026-07-15
---

# 已发布用户协议版本确认（历史记录）

> 本目录记录的 2026-07-15 协议版本属于旧的 Operit 启动合同，已被
> `docs/TODO/kiyori_first_run_experience/` 与 Kiyori 2026-08-09-r3 首启协议取代。
> 本目录仅保留历史审计信息，不得作为当前产品文案、版权许可或首启流程实现依据。

本计划为已发布客户端补充可审计的协议版本确认。现有布尔确认记录不能证明用户阅读过更新后的条款。

## 目标

每次协议实质更新均使用明确版本号。用户仅在已确认当前版本时进入主应用；已安装旧版的用户在升级后需要重新确认。

## 步骤

1. [协议版本门禁 [DONE]](./1_AgreementVersionGate.md)：保存已确认版本、比较当前版本并在协议页展示版本
2. [协议文本草案 [DONE]](./2_AgreementTextDraft.md)：根据用户审核结果更新人话版和严谨版条款，并同步协议版本

## 完成标记

协议版本门禁完成后，在第一步标题末尾添加 `[DONE]`。协议正文经用户确认并完成同步后，才可将本计划标记为完成。

## 完成记录

状态：历史记录已归档。当前协议版本为 `2026-08-09-r3`。现行中文用户协议与隐私政策分别
位于 `app/src/main/res/values/strings.xml` 的
`kiyori_onboarding_user_agreement_*` 与 `kiyori_onboarding_privacy_policy_*` 资源。
其他语言在六个中文首启页面全部定稿后统一同步。
