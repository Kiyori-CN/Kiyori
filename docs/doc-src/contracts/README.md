# 运行时契约索引

这里承接原 CONTEXT 中需要按领域查阅的稳定行为。根 [CONTEXT](../../../CONTEXT.md) 只保留高频不变量；本目录说明边界和关键符号，正式架构继续负责详细设计，专项 TODO 负责执行状态。

## 按任务选择

| 任务涉及 | 先读 | 继续阅读 |
| --- | --- | --- |
| 首页、首启、抽屉、设置、Back、主题 | [应用壳与导航](product_shell.md) | [产品壳架构](../architecture/kiyori_product_shell_and_navigation.md) |
| 网页、窗口、用户脚本、广告、密码 | [浏览器](browser.md) | [浏览器扩展平台](../architecture/browser_plugin_platform.md) |
| 网络代理、进程就绪、路由与故障 | [网络路由](network_proxy.md) | [代理专项](../../TODO/application_network_proxy/index.md) |
| 下载、播放、媒体候选、缓存 | [媒体与下载](media_downloads.md) | [播放器架构](../dev-core/PLAYER_ARCHITECTURE.md) |
| 模型请求、流、取消、工具闭合、恢复 | [AI 请求与执行](ai_execution.md) | [执行架构](../architecture/model_capability_and_resumable_execution.md) |
| AI 输入、消息选择、分享、历史与小窗 | [AI 对话交互](ai_interaction.md) | [体验专项](../../TODO/ai_chat_experience_refinement/index.md) |
| MCP、ToolPkg、Skill、工作区与检索 | [扩展与工作区](extensions_workspace.md) | [脚本开发](../../SCRIPT_DEV_GUIDE.md) |
| 路径、日志、权限、终端与语音服务 | [平台与存储](platform_storage.md) | [仓库布局](../dev-core/REPOSITORY_LAYOUT.md) |
| 对话详情、修订、审计与导出 | [完整审计](../dev-core/AI_CONVERSATION_AUDIT.md) | [对话审计专项](../../TODO/ai_conversation_audit/index.md) |

## 阅读与维护边界

- 中文解释行为，类名、枚举、协议字段和 wire 值保持源码拼写。
- “当前”必须有源码依据；日期、提交、测试数量、APK 哈希留在专项验证记录。
- 运行时边界与开发规则分开，禁止将产品 R0–R3 授权模型误当成 Agent 的操作授权。
- 修改契约时先定位真实状态所有者，更新这一领域及受影响的调用方文档，不把细节追加回根 CONTEXT。
- 本次分层以 `145f378900d663b812d9df3c73de15fbad56139c` 的原 CONTEXT 为迁移基线；已被新首启替代的通知协调器与过期构建数字不再作为当前定义。完整原文保存在 Git 历史，历史验收仍留在 [TODO 历史](../../TODO/history/README.md) 和原专项中。
