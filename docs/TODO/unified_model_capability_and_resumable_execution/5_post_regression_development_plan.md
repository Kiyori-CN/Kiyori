# 5. 故障注入、进程恢复与可观察性收口

## 目标

发送即中断回归修复后，后续开发继续围绕同一个 `ProviderExecutionRepository`、同一个
Responses 状态机和同一条消息错误链推进。不得创建第二套执行记录、第二套 UI 流或依据模型名
猜测能力的旁路。

## P1 本地传输故障注入

建立不调用付费 endpoint 的可控本地 HTTP/SSE 测试服务，覆盖：

- 首个 `response.created` 使用 sequence `1`
- `response.created` 前连接中断和 408、409、429、5xx
- `response.created` 前 HTTP 502 必须持久化 `SUBMISSION_UNKNOWN`，不得发起第二次 POST
- 已取得 response ID 后的 HTTP/2 reset、普通 EOF 与续流
- reasoning summary、正文和 function arguments 中途断流
- 重复事件、sequence 缺口和远端 response ID 变化
- Provider 在 UI 订阅前失败，晚到收集器仍收到错误，SharedStream owner 不产生未捕获异常
- 消息主收集 Job 与 Compose 次级观察器不会把同一 Provider 失败重复升级为 `APP_FATAL`
- `response.completed` 到达前不得报告成功

完成信号：

- 请求 POST 次数、同一 response 的 GET 次数和 `starting_after` 值都有精确断言
- 已提交 UI 内容不撤回、不重复
- `SUBMISSION_UNKNOWN`、`DISCONNECTED`、`FAILED` 和终态与故障类型一致
- 发送失败进入消息错误链但不进入 `GlobalExceptionHandler`，不会生成 `APP_FATAL`

当前完成的 P1 子项：

- [x] `OpenAIResponsesSubmissionFaultInjectionTest` 使用本地 loopback HTTP 直接收集公开
  `OpenAIProvider.sendMessage` 的生产流
- [x] 首个 `response.created` 前 502 的请求序列精确为一个 `POST /v1/responses`
- [x] 生产 repository 适配器收到一次 `SUBMITTING` 创建和一次
  `SUBMISSION_UNKNOWN / HTTP_502_SUBMISSION_UNKNOWN` 更新
- [x] 该路径没有 append provider event、没有 begin resume、没有第二个 POST，原
  `OpenAIResponsesSubmissionUnknownException` 到达流收集者
- [x] `response.completed` 的完整 `response.output` 会补齐未通过中间事件交付的正文尾部与
  `function_call`；终态正文与已确认流式正文分叉时按协议错误失败
- [x] 终态工具遍历保留原始 `response.output` 位置并以 `call_id` 绑定流事件；不再使用过滤后
  工具数组的本地位置重复投影同一个调用
- [x] `response.function_call_arguments.done.arguments` 作为最终参数快照，在关闭 XML 前进入
  同一增量解析器；与已确认参数分叉时按协议错误失败
- [x] Provider 原生工具标签的独立 `name` 属性不会再被 `provider_name` 覆盖；
  `provider_name="DEEPSEEK"` 不再被误解析为工具名 `DEEPSEEK`
- [x] 相同 provider 与 `call_id` 的同名同参工具在当前回合只执行一次；身份、工具名或参数
  冲突在权限检查和副作用前失败
- [x] Responses 历史输入对每个 `call_id` 最多生成一个 `function_call` 和一个相同内容的
  `function_call_output`；冲突输出按协议错误失败
- [x] 工具执行与 follow-up 请求通过 `Deferred.await()` 把原异常交回主响应流，不再由
  `launch + join()` 静默丢失或进入全局未捕获异常处理器
- [x] 长期工具 scope 使用 `SupervisorJob`；一次 follow-up 502 只终止当前消息链，不取消父
  scope，也不使后续新回合继承旧异常
- [x] `processStreamCompletion` 不再把异常改写为 `Idle`；当前响应轮零输出会进入消息错误链，
  不会被投影为空白 `Completed`
- [x] 全量模型配置刷新与单功能刷新共用 retired/lease 合同；活跃请求继续使用旧实例，新请求
  获取新实例，最后一个租约归还后旧实例只释放一次，刷新不调用 `cancelStreaming`
- [x] 取消来源按 chat/turn ID 一次性登记；用户停止和破坏性历史修改进入 `Idle`，配置刷新、
  生命周期和未知取消进入可见 `Error`，新回合不会继承旧取消来源
- [x] 消息最终 owner 在写 `Completed` 前重建并验证最终正文；共享流零 chunk 或空白正文以
  `AI_STREAM_EMPTY_TERMINATION` 失败，普通异常在 cleanup 后继续保持 Error
- [x] 普通发送退出时必须命中完成、失败或取消终态；缺失终态以
  `AI_TURN_TERMINAL_MISSING` 显示错误，并记录结构化 `turn terminal`
- [x] 目标设备首包前 502 已证明异常到达 `provider_failure` 终态；`ChatServiceCore` 的消息
  错误回调现写入主界面共享 `UiStateDelegate.errorMessage`，并由 `ErrorDialog` 展示，不再
  只记录日志
- [ ] P1 其余断线、reasoning、正文、function arguments 和终态前 EOF 场景仍按本节顺序推进

## P0 Native ART continuation 门禁

针对 vivo Android 16 的 `Unexpected instruction: unused-e6`，后续每次修改普通发送链都必须
在 Debug APK 生成后审计以下 DEX continuation：

- `MessageProcessingDelegate$sendUserMessage$sendJob$1`
- `MessageProcessingDelegate$executeSendUserMessageTurn$1`
- DEX 中唯一匹配 `MessageProcessingDelegate$collectAssistantResponseStream$<synthetic-index>`
  的顶层 continuation

门禁至少核对：

- 发送启动与主发送 continuation 不重新出现旧的数百 registers 级别状态机
- 主流收集、自动朗读、Waifu、修订事件和收尾各自保持独立 continuation
- `apkanalyzer dex code` 能完整反汇编上述类的 `invokeSuspend`
- 发现方法体异常增大时，在目标设备复测前停止交付 APK

该门禁是构建产物静态检查，不改变 Responses 请求次数、错误分类、重连或工具调用语义；它
不能替代 vivo PD2507 Android 16 的安装和现场验收。

## P2 跨进程恢复协调器

App 启动后由唯一恢复协调器查询 `ProviderExecutionRepository.getRecoverableExecutions()`，
按 chat、message timestamp、variant 和 hop 恢复：

1. 从 `message_provider_states` 重建已确认 output state
2. 将消息 variant 重新绑定到原 local execution
3. 已知 response ID 时继续 GET 同一 response
4. 未知提交结果时显示明确的 `SUBMISSION_UNKNOWN`，不创建新 POST
5. 已经处于终态的 execution 只投影结果，不再启动传输

`RUNNING` 工具调用不能自动再次执行。恢复协调器只显示其 provider identity、工具名和已知
状态，后续由显式工具恢复协议决定确认、查询或终止动作。

完成信号：

- 进程终止后重新打开同一聊天，已确认内容与 sequence 不丢失
- 恢复只使用原 response ID
- 工具副作用执行次数保持一次

## P3 统一执行错误与 UI 状态

增加强类型 `ProviderExecutionFailure`，至少包含：

```text
phase
code
safeMessage
retrySafety
remoteResponseKnown
localExecutionId
```

UI 只消费脱敏后的安全消息和允许动作。请求编译失败、持久化失败、HTTP 明确拒绝、提交状态
未知、续流过期、协议冲突和用户取消必须是不同状态，不能只依赖异常文本分类。

消息层取消终态已经先行使用强类型 `AssistantTurnCancellationSource`：只有当前 chat/turn ID
明确登记的用户停止和破坏性历史修改可以静默 `Idle`；应用退出、配置刷新、生命周期失效和
来源未知的协程取消均为可见错误。Provider execution 的更细粒度失败类型仍按本节继续推进。

完成信号：

- 任一非取消异常都会到达消息层并留下 execution 状态
- 零内容不能投影为成功完成，即使 provider 已返回明确终态
- UI 动作不会违反 at-most-once 或 exactly-once 边界

## P4 延迟、续流与缓存遥测

在 provider execution 上记录：

```text
requestCompileMs
timeToResponseCreatedMs
timeToFirstVisibleChunkMs
totalLatencyMs
resumeLatencyMs
resumeCount
sequenceGapCount
toolRoundCount
```

Prompt Cache 必须分别展示 provider 返回的 cached tokens、本地估算 token 和 cache key
namespace；不得用本地估算伪装成服务端命中。

完成信号：

- 同 response 续流与新 response 创建在指标中可区分
- Sol、Terra、Luna 的 cache namespace、延迟和 usage 可独立审计
- 指标字段不进入 Prompt Cache key

## P5 其他模型适配

其他模型只有在以下条件全部满足后才新增 profile：

1. provider 与 endpoint 类型明确
2. reasoning wire 参数有正式协议证据
3. execution persistence、Prompt Cache、Tool Search 和 strict schema 分别声明
4. 请求编译与响应状态机有自动测试
5. Generic endpoint 不继承官方能力

完成信号：

- 新模型不修改 GPT-5.6 的五档语义
- 未登记模型继续保持 passthrough profile
- capability revision 变化会更新 Prompt Cache namespace

## 实施顺序与门禁

Native ART continuation 门禁优先于 P1，并与每个后续阶段的 Debug APK 一起执行；其余严格按
P1、P2、P3、P4、P5 串行推进。每个阶段先完成最窄测试，再进行 AndroidTest 编译、
formal readiness、`git diff --check` 和 Debug APK 构建。真实 endpoint、网络切换、退后台、
进程重建和设备 UI 验收继续作为独立授权与证据层级。
