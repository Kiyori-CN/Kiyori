---
title: 用户 Markdown 档案
fork: https://github.com/luojiaping/Operit
branch: agent/user-md-profile
status: complete
---

# 用户 Markdown 档案

本页保存原上游专项的完成范围；其“已发布”背景指原上游，不能作为 Kiyori 的发行状态。

## 原问题与目标

旧版在偏好档案中保存六项结构化用户字段，同一 profile ID 同时选择 ObjectBox 记忆库并绑定角色卡，导致用户身份、助手角色和记忆隔离耦合。

方案使用一个私有 `user.md` 作为用户资料，角色卡继续描述助手；旧 profile ID 转为记忆空间，保留原 ObjectBox 数据和绑定。

## 交付范围

| 分项 | 原记录状态 |
| --- | --- |
| [存储与迁移](1_StorageAndMigration.md) | DONE |
| [提示词与工具](2_PromptAndTools.md) | DONE |
| [Markdown 设置界面](3_MarkdownSettingsUi.md) | DONE |
| [记忆空间清理](4_MemorySpaceCleanup.md) | DONE |

当时只完成静态源码和调用点验证，未编译、构建或执行测试。`complete` 描述上述限定范围，不扩大为设备验收通过。
