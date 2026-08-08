# ToolPkg 与市场运行时

## 实施范围

- 增加 `ToolPkg.registerChatMessageHook` 与 `message_persisted` 事件
- 在消息真正写入聊天存储后分发 Hook，载荷与上游 `+9` 保持一致
- 为聊天输入、Prompt 与摘要 Hook 增加共享总时限和 QuickJS 中断
- 让聊天输入与消息注入 Prompt Hook 超时进入现有聊天 Toast 事件链
- 增加 `Tools.SoftwareSettings` 角色卡完整管理接口
- 增加 `System.getLocation` 的 `includeAddress` 参数
- 保留 Kiyori 独立市场兼容版本，并在运行时闭环后提升为 `1.12.0+9`
- 同步市场协议确认与审核后修订提交能力

## 约束

- 复用现有 `PackageManager`、`CharacterCardManager`、聊天持久化和 Toast owner
- 不创建第二个脚本引擎、包注册表、角色卡仓库或市场客户端
- Hook 超时必须中断实际 QuickJS 执行并继续宿主流程，不吞错或伪造成功

## 实施结果

- `ChatMessage Hook` 已覆盖注册别名、主脚本捕获、运行时模型和消息持久化后异步派发
- 聊天输入、Prompt 与摘要 Hook 共享一个可配置总截止时间，QuickJS 接收剩余时限
- 输入与 Prompt 超时通过现有聊天 Toast owner 显示精确 ToolPkg 和 Hook 标识
- `Tools.SoftwareSettings` 已直接复用 `CharacterCardManager` 完成角色卡增删改查、
  激活、Tavern JSON 导入与导出
- 定位 API 已支持 `includeAddress`，内置 `message_insert` 天气采集按上游使用
  `includeAddress=false` 与 5 秒步骤时限
- 市场兼容版本已提升为 `1.12.0+9`，并加入首次协议确认、审核详情、协作者编辑和修改版
  提交流程；服务端修订时间缺失或无效时明确报错并阻止提交
- 旧的本地 12 小时重新提交状态、旧 `/resubmit` 客户端调用和对应界面已移除

[DONE]
