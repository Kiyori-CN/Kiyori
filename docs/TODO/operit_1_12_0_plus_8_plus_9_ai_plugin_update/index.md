---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
---

# Operit 1.12.0+8/+9 AI 与插件生态兼容更新

本计划以 Kiyori `main@a7e6d0e5` 和 Operit `upstream/main@93a28251` 为审计基线。
Operit `1.12.0+8` 对应 `3f146057`，`1.12.0+9` 对应 `4bf0138b`，同日后续修复
`fe0c8767` 修正选中角色卡发送语义。

## 目标

- 恢复 Kiyori 对 Operit `1.12.0+9` ToolPkg 公共运行时契约的真实支持
- 合并 `+8/+9` 中与 AI 对话稳定性、插件 Hook、市场和当前公开插件直接相关的更新
- 保持 Kiyori 产品壳、浏览器插件域、存储 owner 和独立产品版本不变
- 以定向测试、正式开发门禁和 Debug APK 证明本地实现闭环

## 已确认事实

- 实时市场共审计 `1257` 项，其中 `19` 个最新 ToolPkg 声明
  `minAppVer=1.12.0+9`
- 19 个发布资产均按市场记录的 SHA-256 校验通过
- `消息时间戳` 直接调用 `ToolPkg.registerChatMessageHook`；任务基线缺少该入口，本轮已补齐
- Kiyori 相对上游 `+9` 的公共 TypeScript API 差异集中在 ChatMessage Hook、
  `SoftwareSettings` 角色卡管理、`System.getLocation(..., includeAddress)` 和对应结果类型

## 分项

1. [上游与市场兼容审计](1_upstream_and_market_audit.md)
2. [ToolPkg 与市场运行时](2_toolpkg_and_market_runtime.md)
3. [AI 对话更新](3_ai_chat_updates.md)
4. [验证与交付](4_verification_and_delivery.md)

## 非目标

- A2A 外部服务、发布签名轮换、终端会话恢复
- 上游 avatar/LLM 模块搬迁、主题系统和 README 视觉改版
- Operit 2 Flutter/Rust 客户端、远端 CI 或设备操作
- 整分支合并、回退代码、第二状态源、提交和推送
