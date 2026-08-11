# 2. 模型能力与请求编译

## 强类型能力

已实现类型：

```text
ModelCapabilityProfile
ModelCapabilityResolver
UserExecutionIntent
CompiledModelRequest
ModelRequestCompiler
```

当前强类型能力轴：

```text
ProviderContractAuthority
ReasoningWireFormat
ReasoningSummaryCapability
ReasoningReplayCapability
ExecutionPersistenceCapability
PromptCacheCapability
ToolSchemaCapability
ToolDiscoveryCapability
```

## GPT-5.6 profile

三模型共享：

- effort：`none/low/medium/high/xhigh/max`
- Responses output item replay
- 精确官方 endpoint：`summary=auto` + `reasoning.encrypted_content`
- 精确官方 endpoint：Background Responses + sequence resume
- 精确官方 endpoint：Prompt Cache key
- 精确官方 endpoint：大工具集 Tool Search
- 精确官方 endpoint 且 schema 全部参数必填时：`strict=true` +
  `additionalProperties=false`
- Responses 兼容 endpoint：五档 reasoning + at-most-once 提交，不自动声明以上官方能力

用户选择的五档保持精确映射，不能因模型不同而改写档位。

Pro、Fast、Ultra、reasoning context、WebSocket、Programmatic Tool Calling 和 Multi-agent
没有被本轮伪装成已实现能力；后续只有在 profile 明确声明且 wire adapter 有测试时才能加入。

## Request compiler

编译阶段：

```text
UserExecutionIntent
CapabilityResolver
PromptCompiler
ContextPlanner
ToolContractCompiler
CachePlanner
ExecutionPolicyCompiler
WireAdapter
```

当前实现已从 provider 中移出的决策：

- 模型名启发式能力判断
- 五档到 wire effort 的映射
- 官方/兼容 endpoint 的执行持久性声明
- Prompt Cache、Tool Search 和 strict-compatible schema 能力声明

provider 仍负责：

- endpoint 和认证
- wire encode/decode
- transport
- provider event normalization
- provider usage parsing
- 读取现有用户五档设置并交给 request compiler

## 验收

- [x] 三个 GPT-5.6 profile 使用 `none/low/medium/high/xhigh/max`
- [x] 不生成未实现的 `reasoning.context`
- [x] Pro、Fast、Ultra 不混入 effort 五档
- [x] 官方 OpenAI profile 与第三方兼容 endpoint profile 分离
- [x] 历史配置即使保存为 `OPENAI_RESPONSES`，自定义域名仍按兼容 endpoint 编译
- [x] 未声明的 Background、Prompt Cache 和 Tool Search 不进入 generic 请求
- [x] 未声明的 reasoning summary、encrypted content 和 strict schema 不进入兼容请求
- [x] 未登记模型使用 passthrough profile，不猜测 GPT-5.6 五档能力
- [x] JVM 测试覆盖五档、关闭思考、官方/兼容 endpoint 分界、伪装域名与未知模型透传
- [x] Android 请求特性测试覆盖官方与兼容请求字段差异
