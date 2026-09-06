---
fork: https://github.com/AAswordman/Operit.git
status: in_progress
---

# 市场发布作者验证

本专项保存上游市场登记方案与实施证据，不代表当前 Kiyori 发布流程已完成远端验收。

## 背景与目标

原制品发布仅支持通过 OperitForge 直接上传，作者已有的 GitHub Release 资产无法复用相同流程登记。Release 正文应保持作者自己的发布说明，市场通过 GitHub metadata 验证所选资产，不写入或读取 Operit 专属正文标记。

## 范围

- 移除 Android proof 请求与 Release 正文修改。
- 本地选择制品后提供“直接上传 / 已有 GitHub Release”两种来源。
- 验证 Release 创建者与当前市场发布者身份一致。
- 服务端解析 canonical 资产 URL，保留 SHA-256 校验，持久化 owner/repository/tag。
- 在作者指南中说明独立 Release 与市场登记流程。

## 分项与验收

1. [移除正文证明](1_RemoveReleaseProof.md)：代码与静态引用检查完成，测试未执行。
2. [双来源登记](2_DualSourceMarketRegistration.md)：代码与静态引用检查完成，测试未执行。

两条路径应共用市场资产契约；远端行为和实际发布需另行验收，原 `in_progress` 状态保持。
