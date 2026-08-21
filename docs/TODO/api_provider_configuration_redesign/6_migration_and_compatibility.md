# 迁移与兼容边界

## 读取兼容

`ModelConfigManager` 的宽松 JSON 读取继续保留。新增 `apiProtocol` 缺失时：

1. 先按 `apiProviderType` 旧枚举推导
2. `NOVITA` 再检查已保存 endpoint 是否明确包含 `/anthropic/`
3. 旧自定义 provider ID 仍交给 `ToolPkgAiProviderRegistry`
4. 无法识别的内置 provider 继续进入现有不可用状态，而不是猜测供应商或协议

读取不应改写原始 JSON。只有用户在页面明确保存后才写入新字段。

## 可见入口收敛

以下值保留为内部兼容 ID，但从 provider dialog 中移除：

- `OPENAI_RESPONSES`
- `OPENAI_GENERIC`
- `OPENAI_RESPONSES_GENERIC`
- `ANTHROPIC_GENERIC`
- `GEMINI_GENERIC`

旧配置显示为 canonical provider + 当前协议。这样既不会让旧配置无法打开，也不会继续
制造多个同名供应商入口。

## 运行时与审计

- 旧 `providerTypeId:model` 统计键继续可读取。
- 新配置使用 canonical provider ID；协议单独存在于配置和请求 trace 中。
- `providerModel`、Responses execution persistence、ToolPkg provider ID、备份 JSON 和
  既有 `Screen`/route 标识不得被批量重命名。
- 连接测试必须使用与正常聊天相同的协议解析和 endpoint completion。
- Novita Anthropic 的供应商身份仍是 `NOVITA`，协议和 Claude request owner 独立表达。

## 回滚点与停止条件

每个代码里程碑完成后以干净的 `main` 提交作为回滚点；本任务不通过保留另一套用户可见
页面或隐藏开关实现回滚。

发现以下情况时停止当前里程碑并记录证据：

- 新字段无法被旧 JSON 解码器忽略或新 JSON 无法被当前 Json 配置读取
- 协议选择不能在 UI、连接测试和 AIServiceFactory 之间保持一致
- providerModel 审计键、Responses persistence 或 ToolPkg provider ID 被无意改变
- 需要引入第二状态 owner、额外网络服务或数据迁移脚本才能完成本轮范围
