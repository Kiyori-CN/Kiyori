# 1. Responses 可恢复执行

## 旧实现

`OpenAIProvider.sendMessage` 为一次请求建立 UI 保存点。流读取发生非手动 IO 异常时，旧实现
撤回本次 attempt 的全部内容并重新创建请求，最多执行五次。该策略无法保存服务端正在进行的
推理，也无法证明重复请求不会产生重复计费或重复工具调用。

Responses 事件解析当前没有持久化：

- `response.created` 的 response ID
- 每个事件的 `sequence_number`
- 已应用事件的游标
- 明确终态
- function call 的原始 `call_id`

## 新实现

### Provider request identity

消息层在创建流前分配：

```text
ProviderRequestContext
  localExecutionId
  chatId
  messageTimestamp
  variantIndex
  hopOrdinal
```

该身份沿 `AIMessageManager`、`EnhancedAIService`、`AIService` 传入 provider。provider
不得自行读取当前聊天或推断 variant。

### Room entities

新增：

```text
provider_executions
provider_execution_events
message_provider_states
tool_invocation_ledger
```

活动执行必须记录：

```text
remoteResponseId
lastAppliedSequence
executionStatus
terminalEventType
requestFingerprint
resumeCount
```

事件唯一键：

```text
(remoteResponseId, sequenceNumber)
```

工具调用唯一键：

```text
(provider, remoteResponseId, callId)
```

### Event application

一个事件的以下操作必须处于同一 Room 事务：

1. 写入事件账本
2. 更新标准化 output state
3. 推进 `lastAppliedSequence`
4. 更新 execution 状态

事务提交后才能向 UI 发出新内容。

官方 Responses 的 `sequence_number` 是一基序列：

```text
NO_APPLIED_SEQUENCE = -1
FIRST_EVENT_SEQUENCE = 1
next = lastApplied + 1
```

`-1` 只表示本地尚未应用任何事件，不能通过普通加一把首事件推导为 `0`。状态机与 Room DAO
必须调用同一个 sequence 规则，否则首个 `response.created` 会在任何可见正文之前被误判为
缺口。

### Stream termination

只有明确收到以下事件才允许进入终态：

```text
response.completed
response.failed
response.incomplete
response.cancelled
```

EOF、HTTP/2 reset 和普通 socket 中断只改变传输状态。已有 response ID 时，从
`lastAppliedSequence` 续接同一 response；没有 response ID 且提交状态无法确认时，记录
`SUBMISSION_UNKNOWN`，禁止自动创建看似相同的新执行。

Provider 冷流转为共享流时必须使用真实上游异常关闭共享流。`EnhancedAIService` 在写入内部
Error 状态后继续向消息层抛出同一异常；首包前失败不能被共享层或服务层转换为零内容正常结束。

`SharedStream` 同时承担这次上游终止的唯一传播所有权。转发协程捕获上游失败后，以该失败
关闭共享流，让当前和晚到收集器在自己的收集边界抛出；转发 owner 本身随后正常结束，不能把
同一异常再抛入 `GlobalExceptionHandler`。这不是吞掉错误，而是避免一个已由消息层处理的
发送失败被第二次当作进程级未捕获异常并生成 `APP_FATAL`。

共享流之下仍必须保持单一消息错误 owner。`MessageProcessingDelegate` 的
`streamCollectionJob` 在结构化子作用域内运行正文和辅助收集器，将非取消终止原因写入
`streamCollectionResult` 后正常结束；主 `sendJob` 等待该结果并进入既有发送错误链。
自动朗读、Waifu 分段、修订事件、`ChatArea` 首包观察和
`rememberRevisableTextStream` 只拥有自己的观察生命周期：Provider 失败时记录并结束，
不得重复抛向 `GlobalExceptionHandler`。取消异常继续传播，不能被转换为普通发送失败。

提交前 HTTP 失败采用最保守的 at-most-once 分类：429 明确表示本次被限流拒绝，可以重新
提交；408、409、5xx 和请求传输异常都不能证明服务端未接受请求，必须进入
`SUBMISSION_UNKNOWN`。已知 response ID 后不再 POST，408、409、429 和 5xx 只重试同一
response 的 GET，404/410 表示远端可恢复状态已经过期。

### Cancellation

用户取消必须：

1. 标记本地执行为 cancelling
2. 关闭当前传输
3. 对 background response 调用远端 cancel
4. 记录明确 cancelled 终态

网络中断不得进入用户取消路径。

## P0 验收

- [x] Room 20 -> 21 迁移严格创建四张表和索引
- [x] message/variant 删除与 chat 删除能清理关联状态
- [x] `response.created` 立即持久化 response ID
- [x] 首个官方 Responses 事件从 sequence `1` 开始，`-1` 哨兵不参与普通递增
- [x] 事件游标单调递增，重复事件不重复应用
- [x] sequence 出现缺口时停止推进
- [x] EOF 无终态时进入 disconnected
- [x] `starting_after` 请求使用最后已提交游标
- [x] 重连不触发 UI 整体 rollback
- [x] 原始 `call_id` 进入工具调用账本
- [x] 同一 `call_id` 的工具结果可复用且不重复执行
- [x] 进程重建后能查询未完成执行
- [x] 用户取消与网络中断使用不同状态，并在已知 response ID 时调用远端 cancel
- [x] 首包前异常穿过 SharedStream 与 EnhancedAIService 到达消息错误链
- [x] SharedStream owner 不重复抛出已交付给下游的 Provider 异常，不进入进程级未捕获异常链
- [x] stream collection Job 只向主 send Job 交付一次非取消失败，自身正常完成
- [x] 自动朗读、Waifu、修订事件和 Compose 次级观察器不重复升级 Provider 终止异常
- [x] 主收集器与次级观察器保留 `CancellationException` 语义

实现边界：

- 自动续接只对 provider 类型与实际 endpoint 都属于官方 OpenAI 的 GPT-5.6 family profile
  启用
- Responses 兼容 endpoint 未声明官方 Background/sequence resume 能力，但仍保持单次提交
  边界；未知提交状态不执行普通流式整轮回滚或重新 POST
- 非可恢复 Chat Completions 与 Responses 只保留原始 `call_id` 供下一 hop 重放，不进入要求
  完整 provider、response ID 与 call ID 的 exactly-once 账本
- 真实 endpoint、网络切换和进程重建后的自动 UI 恢复仍需独立集成/设备验收
