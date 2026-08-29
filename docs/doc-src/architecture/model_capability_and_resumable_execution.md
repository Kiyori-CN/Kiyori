# 统一模型能力与可恢复执行架构

## 目的

Kiyori 的模型层同时支持官方 API、兼容 endpoint、聚合 provider、本地推理和 ToolPkg
Provider。统一架构必须把用户意图、模型能力、请求编译、传输、provider-private state 和
可见聊天内容分离，避免 provider 子类各自读取设置和猜测模型能力。

## 核心不变量

1. 一个用户可见消息 variant 对应一个独立 provider state owner
2. 一个用户回合可包含多个按顺序排列的 provider exchange
3. 每个 exchange 使用自己的远端 response ID
4. provider event 只应用一次
5. 已确认事件的游标只增不减
6. 传输 EOF 不等于 provider 执行完成
7. 网络中断不撤回已确认内容；正文阶段失败时只投影 replay-safe 的已收到前缀，不把它当作 provider 成功
8. provider `call_id` 是工具稳定身份，不从 XML 标签、工具名称或数组位置重新生成
9. 同一 provider `call_id` 只能对应同一工具名和语义一致的参数，并且只投影、执行和重放一次
10. 用户设置不直接等同 provider wire 参数

## 分层

```text
UI settings
UserExecutionIntent
CapabilityResolver
RequestCompiler
ProviderExecutionCoordinator
ProviderTransport
NormalizedProviderEvent
ProviderExecutionRepository
Chat rendering
```

## 状态载体

活动执行、事件和工具账本存入 Room。最终 provider output items 存入
`message_provider_states`。聊天正文只保存用户可见内容和现有展示 markup，不承担 provider
协议状态的唯一持久化职责。

## Responses

官方 `OPENAI_RESPONSES` 的 GPT-5.6 family 长推理使用 `background=true`、`store=false`。
“官方”不是 provider 枚举名称的别名：只有 provider 类型为官方 OpenAI，且补全后的 endpoint
精确为 `https://api.openai.com/v1/responses` 时，profile 才能声明官方合同。自定义域名即使
沿用历史 `OPENAI_RESPONSES` 配置，也按 Responses 兼容 endpoint 编译。
未应用事件使用 `lastAppliedSequence=-1` 哨兵，官方流首个 `response.created` 使用
`sequence_number=1`；状态机和 Room DAO 共用同一一基 sequence 规则。首事件提交到 Room 后，
后续事件按连续 `sequence_number` 幂等应用。传输中断时使用 response ID 和最后已应用
sequence 发起：

```text
GET /responses/{response_id}?stream=true&starting_after={last_sequence}
```

Responses 兼容 endpoint 不自动声明这项能力，也不自动加入 Prompt Cache、Tool Search、
`reasoning.summary=auto`、`reasoning.encrypted_content` 或 strict schema。GPT-5.6 的用户五档
仍精确编译为 `low / medium / high / xhigh / max`。

`response.created` 前的自动重新提交必须满足 at-most-once 边界：只有明确的 429 限流拒绝
允许再次 POST；408、409、5xx 与传输异常进入 `SUBMISSION_UNKNOWN`。取得 response ID 后，
瞬时错误只重试同一 GET；404/410 将执行标记为 `EXPIRED`。

没有官方 sequence resume 的兼容 Responses 继续使用普通 Responses SSE 解析，但提交失败不再
进入 Chat Completions 的整轮回滚与重新请求。明确的 429 拒绝仍可按提交策略处理；408、409、
5xx 和传输异常直接投影为 `OpenAIResponsesSubmissionUnknownException`，外层消息携带安全的
HTTP 或传输摘要。

Responses 的内容协商和成功终态由协议 owner 明确控制：流式 POST 默认发送
`Accept: text/event-stream`，非流式 POST 默认发送 `Accept: application/json`；显式自定义
`Accept` 仍按用户配置发送。SSE 流只有在成功处理 `response.completed`，或持久化执行状态明确为
`COMPLETED` 时才能返回成功。`[DONE]` 不能替代 Responses 语义终态；干净 EOF、
`response.failed`、`response.incomplete` 或 `response.cancelled` 都不能进入正常消息收尾。

正文阶段的 EOF、socket reset 或 chunked body 截断仍然属于传输失败，不是 provider 完成。消息层
失败 owner 在错误收口前从共享流/revision cache 固定已收到正文，并通过
`AssistantReplayHistoryProjector` 移除未闭合工具事务；仅非空 replay-safe 前缀会写入
`ASSISTANT_PROJECTION_UPDATED` 的 `PARTIAL/FAILED` 投影。该 assistant 投影用于保留用户已经看到的
内容和后续 replay 边界，不写 `COMPLETED`，也不改变 Responses execution 的
`SUBMISSION_UNKNOWN`/失败终态。没有可安全投影的正文时只保留真实错误和运行态清理，不能生成空成功消息。

`OpenAIResponsesExecutionPersistence` 是生产 Responses 协调器到持久化层的窄依赖端口。
应用运行时只有 `RepositoryOpenAIResponsesExecutionPersistence` 这一实现，并直接委托唯一
`ProviderExecutionRepository`；端口本身不缓存、不复制执行状态。JVM 故障注入通过该边界
观察生产协调器的持久化指令：本地 loopback HTTP 在首个 `response.created` 前返回 502，
公开 `sendMessage` 流必须只发送一个 POST，把 execution 更新为
`SUBMISSION_UNKNOWN / HTTP_502_SUBMISSION_UNKNOWN`，并向收集者传播
`OpenAIResponsesSubmissionUnknownException`。

Responses 工具流使用原始 `response.output` 位置作为流式投影坐标，并使用 provider
`call_id` 作为身份。`response.completed` 必须直接遍历完整 output，不能先过滤
`function_call` 再使用新的数组位置。`response.function_call_arguments.done.arguments` 是
最终参数快照，必须在关闭 XML 前经过同一参数一致性检查与解析器。随机 XML 标签、过滤后数组
位置和本地 execution ID 都不能替代 `call_id` 或真实远端 response ID。

冷 Provider 流转为共享流时，`SharedStream` 是终止传播所有者：正常完成以无原因关闭，异常
完成以原异常关闭。共享流保留终止原因，因此即使请求在 UI 订阅前失败，晚到收集器仍能得到
失败。完成转发的 owner 协程不再重复抛出已经交给共享流的 Provider 异常，避免同一错误在
消息层之外再次进入 `GlobalExceptionHandler` 并生成 `APP_FATAL`。`EnhancedAIService`
可以先更新输入状态和日志，但不能吞掉 Provider 或工具 hop 的异常；消息层必须收到同一错误，
停止空回复收尾并进入可见错误路径。

普通发送的唯一消息失败 owner 是 `MessageProcessingDelegate` 的主 `sendJob`。正文收集、
自动朗读、Waifu 分段和修订事件在一个结构化收集边界内运行；该边界把非取消终止原因写入
`streamCollectionResult` 后正常完成，主任务再从同一结果抛入自己的消息错误处理。次级
观察器以及 Compose 的首包探测、可修订文本流只记录并结束非取消失败，不能把同一 Provider
异常重新交给线程默认未捕获异常处理器。用户取消仍以 `CancellationException` 传播。

多 hop 失败审计使用异常 cause chain 中的 `MessageFailureDiagnosticSource` 作为执行身份来源。
`ASSISTANT_PROJECTION_UPDATED` 的失败投影和 `PROVIDER_TERMINAL_ERROR` 必须关联同一个真实失败
`localExecutionId`；没有受控诊断身份时保持空值，不能用回合首 hop 的 request context 代替。

工具执行使用长期 `SupervisorJob` scope 隔离不同消息任务。每个工具任务仍由当前调用链创建
`Deferred` 并 `await`，因此当前 follow-up 的原始 Provider 异常继续到达消息 owner；子任务
失败不会取消长期父 scope，也不会使并行的独立工具回合或后续发送继承已经结束的异常。

共享流的正常关闭不能直接证明消息成功。`completeAssistantResponse` 在写 token 统计和
`Completed` 前，必须从 replay 与消息状态重建最终正文，并记录
`assistant completion invariant`：chunk 数、可见字符数、provider、model、provider request
context 是否存在，以及最后输入状态。空白正文抛出带
`AI_STREAM_EMPTY_TERMINATION` 的错误；服务已经投影为 `Error` 时保留其真实错误。普通失败把
Error 同时写入 `finalInputStateAfterSend`，运行态 cleanup 完成后再次由消息 owner 提交，避免
服务 StateFlow 的 `Idle` 重放覆盖错误。若整轮退出时仍没有命中完成、失败或取消终态，则以
`AI_TURN_TERMINAL_MISSING` 显示错误。

消息错误状态和主界面错误弹窗共用同一个失败事实，但由不同 UI 投影消费。
`InputProcessingState.Error` 控制发送与输入状态；`ChatServiceCore` 的消息错误回调还必须把
安全用户消息写入它持有的唯一 `UiStateDelegate.errorMessage`。`ChatViewModel` 直接取得该
实例，`AIChatScreen` 收集后显示 `ErrorDialog`。因此 Provider 失败即使已经到达消息 owner，
回调若只记录日志，用户仍只会看到加载结束。错误弹窗关闭时清空同一 delegate，并将当前聊天
输入状态恢复为 `Idle`，不修改 Provider execution 记录，也不重新提交请求。

每个普通聊天执行通过 `MultiServiceManager.ServiceLease` 固定本轮模型服务实例。模型配置的
单功能刷新和全量刷新只撤销旧实例对新请求的缓存可见性并将其标记为 retired；持有活跃租约的
实例继续完成当前请求，最后一个租约归还后再执行一次 `release`。配置刷新不得对活跃租约调用
`cancelStreaming`，也不得让新请求重新取得 retired 实例。

取消意图由 `AssistantTurnCancellationRegistry` 按 chat 和 turn ID 一次性登记。明确的用户
停止与破坏性历史修改投影为 `Idle`；配置刷新、生命周期失效、应用退出与没有登记来源的
`CancellationException` 投影为可见 `Error`。错误终态在原发送 Job 已取消时使用受限
`NonCancellable` 提交，只写状态和用户提示，不重新执行 Provider 请求或工具。新回合开始时
清除不匹配的旧取消记录，防止上一轮停止操作污染下一轮。

普通发送的 Kotlin 协程实现还必须遵守 Android ART continuation 边界。发送启动 Job 只负责
调用独立的 `executeSendUserMessageTurn`；该入口使用单回合状态载体，把准备用户消息、准备
助手请求、提交 Responses、收集共享流和完成收尾分成独立挂起阶段。这样可以避免把一轮发送的
全部局部变量、嵌套观察器和异常恢复点展开到同一个 DEX continuation。该边界来自 vivo Android
16 现场的 `SIGABRT / Unexpected instruction: unused-e6` tombstone，不是新的业务错误处理
分支，也不改变 at-most-once 或同一 `response_id` 续接合同。

完整 exchange 保存：

```text
responseId
outputItems
completionStatus
usage
```

provider-native function call 的参数、状态和结果独立保存在 `tool_invocation_ledger`，仅以
真实 provider、response ID 和原始 call ID 作为唯一身份。compatible endpoint 没有真实远端
response ID 时，不构造持久化账本身份；当前工具回合仍按 provider 与 call ID 规范化，相同
身份同名同参只保留一次，冲突在权限检查和副作用前失败。Responses 历史重放对每个 call ID
最多写入一个 `function_call` 和一个内容一致的 `function_call_output`。

多 hop 按原顺序重放：

```text
hop0.outputItems
hop0.toolInvocationLedger
hop1.outputItems
hop1.toolInvocationLedger
```

## 生命周期

```text
COMPILING
SUBMITTING
SUBMISSION_UNKNOWN
QUEUED
IN_PROGRESS
DISCONNECTED
RESUMING
WAITING_TOOL
SUBMITTING_TOOL_OUTPUT
COMPLETED
FAILED
INCOMPLETE
CANCELLING
CANCELLED
EXPIRED
```

状态转换由标准化 provider event 和明确的本地用户操作驱动。异常文本本身不能直接决定终态。

## 安全与隐私

- provider-private state 不进入复制文本、普通导出或日志
- 日志只记录 response ID 的短后缀、sequence、状态和大小
- 工具参数与结果不写入普通网络日志
- Background Responses 使用 provider 所需的短期远端状态，设置页必须说明该语义
- no-store 与可恢复执行的实际 provider 契约由 capability profile 表达

## 扩展

其他 provider 通过相同抽象声明：

```text
reasoning control
reasoning state
execution persistence
transport
prompt caching
tool discovery
structured output
compaction
multimodal
```

模型名、endpoint 和 capability revision 必须分别保存。模型名相同不能证明 endpoint 支持相同
协议。

## 当前 GPT-5.6 capability profile

```text
gpt-5.6-sol
gpt-5.6-terra
gpt-5.6-luna
```

三者使用同一用户五档：

```text
1 low
2 medium
3 high
4 xhigh
5 max
disabled none
```

官方 GPT-5.6 Responses profile 额外声明 reasoning 自动摘要与加密重放、Prompt Cache、
Background sequence resume、strict schema 和大工具集 Tool Search；GPT-5.6 compatible
endpoint profile 只声明五档 reasoning wire 与 Responses at-most-once 提交，不假定服务端具备
官方执行持久性、缓存、工具发现或 reasoning 重放能力。

未登记的 OpenAI 或兼容模型使用 passthrough profile：保留调用方已有 wire 参数，但不猜测
它支持 GPT-5.6 的 `xhigh/max`、Background 或 Tool Search。新增模型适配必须先增加显式
capability profile 和测试。
