# 3. 缓存、工具与遥测

## Prompt Cache

缓存前缀按稳定性排列：

```text
PRODUCT_POLICY
AGENT_CONTRACT
WORKSPACE_RULES
PERSONA
TOOL_CONTRACT
cache breakpoint
SESSION_CHECKPOINT
HISTORY_AND_PROVIDER_STATE
CURRENT_TASK
```

当前 cache descriptor 实际包含：

```text
profile namespace
exact model name
tool-call mode
canonical sorted tool schema
stable system/developer prefix
first user anchor
```

不包含：

```text
当前用户消息
动态历史
effort
reasoning mode
service tier
trace ID
时间戳
tool result
```

本地 token 估算与 provider Prompt Cache usage 必须拆分。

## 工具

工具发现：

```text
DIRECT
LOCAL_CATALOG_PROXY
OPENAI_NATIVE_TOOL_SEARCH
```

工具定义和参数按名称稳定排序。GPT-5.6 官方 Responses 在至少有八个非核心函数可延迟时，
保持常用文件/搜索/计算工具 eager，把长尾函数标为 `defer_loading=true` 并加入
`tool_search`；小工具集不增加搜索往返。

仅当一个函数的结构化参数全部必填时，OpenAI function schema 使用 `strict=true` 与
`additionalProperties=false`。含可选参数的函数保留 baseline schema，避免伪造 nullable
合同。

并行与权限共用：

```text
ToolExecutionSemantics
  readOnly
  idempotent
  parallelGroup
  resourceLocks
  riskLevel
  sideEffectScope
```

## Usage 与遥测

```text
ProviderUsageSnapshot
  inputTokens
  uncachedInputTokens
  cacheReadTokens
  cacheWriteTokens
  reasoningTokens
  outputTokens

TurnExecutionMetrics
  requestCompileMs
  timeToResponseCreatedMs
  timeToFirstTokenMs
  totalLatencyMs
  resumeCount
  sameResponseResume
  sequenceGapCount
  toolRoundCount
  retryCount
```

## 验收

- [x] request compile 和 tool schema 序列化稳定
- [x] cache key 不包含当前用户消息、effort、trace ID 或时间戳
- [x] provider usage 原始 JSON 与本地 token 估算分开保存
- [x] strict schema 只对兼容函数启用
- [x] Tool Search 只在 capability 和工具规模满足时启用
- [ ] 工具并发不依赖硬编码名称列表
- [x] `resumeCount`、response ID 与 execution 状态能区分同 response 续接与新 response 创建
- [ ] 完整的 compile/first-token/total-latency 指标面板
