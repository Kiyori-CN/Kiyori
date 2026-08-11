---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
---

# GPT-5.6 可恢复执行与统一模型能力

## 目标

本计划将 Kiyori 现有按 provider 分散实现的模型请求改为统一能力与执行架构。第一完整实现
覆盖 `gpt-5.6-sol`、`gpt-5.6-terra` 和 `gpt-5.6-luna`，并保留其他模型通过显式
capability profile 接入的能力。

首要问题是解决 Responses 长推理连接被 HTTP/2 `CANCEL` 重置后，当前实现撤回已显示内容并
完整创建新请求的问题。新的执行链必须持久化远端 response、事件游标和工具调用所有权，断线
后续接同一个 response。

## 状态所有权

- `ProviderExecutionRepository` 是活动 provider 执行、事件游标、消息及 variant 最终
  provider-private state、provider `call_id` 与工具执行结果的唯一写入 owner
- `MessageProcessingDelegate` 分配聊天消息身份
- `EnhancedAIService` 持有一个用户回合内的 hop 顺序
- provider 只负责 wire 编解码、传输、事件标准化和 usage
- 可见消息正文不再作为 provider-private state 的唯一持久化位置

## 用户接口

继续保留：

```text
enable_thinking_mode
thinking_quality_level: 1..5
```

GPT-5.6 映射固定为：

```text
1 -> low
2 -> medium
3 -> high
4 -> xhigh
5 -> max
```

思考关闭映射为 `effort=none`。Pro、Fast、Ultra、传输和执行持久性是独立维度，不进入五档。

## 里程碑

1. [IMPLEMENTED] [Responses 可恢复执行](1_resumable_responses_execution.md)
2. [IMPLEMENTED] [模型能力与请求编译](2_model_capability_and_request_compiler.md)
3. [PARTIAL] [缓存、工具与遥测](3_cache_tools_and_metrics.md)
4. [LOCAL VERIFIED] [验证与交付](4_validation_and_delivery.md)
5. [IN PROGRESS] [故障注入、进程恢复与可观察性收口](5_post_regression_development_plan.md)

当前代码证据：

- `AppDatabase` 版本 21 与四张 provider execution 表
- `ProviderRequestContext` 从消息时间戳/variant 贯穿到 provider hop
- `OpenAIResponsesExecutionState` 与 `starting_after` 同 response 续接
- 官方 Responses 首事件的一基 sequence 校验，未应用事件继续使用独立 `-1` 哨兵
- 提交前 HTTP at-most-once 分类与 `SUBMISSION_UNKNOWN` 状态
- `OpenAIResponsesSubmissionFaultInjectionTest` 使用 loopback HTTP 从公开 `sendMessage`
  驱动生产协调器；首个 `response.created` 前 502 精确验证单次 POST、repository
  `SUBMISSION_UNKNOWN / HTTP_502_SUBMISSION_UNKNOWN` 和异常向收集者传播
- `ModelCapabilityResolver` / `ModelRequestCompiler` 五档强类型编译
- provider-native `call_id` 与 `tool_invocation_ledger` exactly-once
- canonical Prompt Cache key、兼容 strict schema 与大工具集 Tool Search
- SharedStream 与 EnhancedAIService 保留首包前失败原因，不再把错误关闭为空输出成功；
  SharedStream owner 向下游交付失败后正常收口，不把同一异常再次送入全局未捕获异常处理器
- `MessageProcessingDelegate` 主 `sendJob` 是普通发送的唯一消息失败 owner；
  `streamCollectionJob` 交付非取消失败后正常结束，辅助收集器和 Compose 观察器不重复升级
- `ChatServiceCore` 的消息错误回调把安全用户消息写入同一个 `UiStateDelegate.errorMessage`；
  `ChatViewModel` 与 `AIChatScreen` 共享该实例并显示 `ErrorDialog`，Provider 失败不再只留下日志
- `MessageProcessingDelegate.completeAssistantResponse` 在任何成功终态前重建最终正文并记录
  `assistant completion invariant`；空白正文使用 `AI_STREAM_EMPTY_TERMINATION`，没有命中
  任何终态的回合使用 `AI_TURN_TERMINAL_MISSING`
- 普通非取消异常把 Error 同时写入 `finalInputStateAfterSend`，cleanup 后由消息最终 owner
  再次提交，服务 StateFlow 的 `Idle` 不能把失败覆盖为静默结束
- `MultiServiceManager` 的单功能和全量刷新共用 lease 退休合同；配置保存只让旧服务退出新
  请求缓存，活跃聊天完成后才释放旧 Provider，不再通过刷新取消 GPT-5.6 长推理
- `AssistantTurnCancellationRegistry` 按 chat/turn ID 绑定取消来源；只有用户停止和破坏性
  历史修改进入 `Idle`，配置、生命周期和未知取消进入可见 `Error`
- `MessageProcessingDelegate` 的普通发送链按状态载体、准备、请求创建、流收集和收尾拆分；
  这是针对 vivo Android 16 ART `Unexpected instruction: unused-e6` 的 DEX 兼容性修复，不改变
  Responses 业务状态机
- Responses 完成快照会严格补齐未发出的正文尾部和工具调用；与已确认正文分叉的快照按协议错误
  失败，零输出不再进入 `Completed`
- Responses 工具投影遍历完整终态 `response.output` 并保留原始位置，以 `call_id` 绑定流事件
  与终态快照；`function_call_arguments.done` 的最终参数在关闭 XML 前进入同一解析器
- Provider 工具身份属性使用独立 XML 属性边界；`provider_name` 不会再覆盖工具 `name`
- 相同 provider 与 `call_id` 的同名同参调用在 XML、当前回合执行和 Responses 历史重放中只
  保留一次；身份、工具名、参数或同一输出的内容冲突在副作用或下一 hop 前失败
- compatible endpoint 没有真实 response ID 时只按 `call_id` 做当前回合规范化，不伪造远端
  response ID 或持久化账本身份
- 长期工具 scope 使用 `SupervisorJob` 隔离任务；工具执行和 follow-up 请求以
  `Deferred.await()` 返回主失败 owner，一次 502 不再取消后续独立回合。流完成阶段保留 Error
  并继续抛出，不再将失败清成 `Idle`

## 非目标

- 不改变 application ID、聊天数据库既有消息接口或 ToolPkg/MCP 协议标识
- 不自动切换用户选定的主聊天模型
- 不通过模型名子串推断第三方 endpoint 的高级能力
- 不把 WebSocket 误作正在生成 response 的断线续接协议
- 不调用付费模型 API 作为本地自动测试
- 不提交、不推送、不发布、不安装或操作设备

## 完成标准

- 已取得 `response_id` 的 Responses 流中断后续接同一 response
- 首个 `response.created` 必须按官方 `sequence_number=1` 提交，后续游标严格连续
- `response.created` 前只有明确的 429 拒绝允许重新提交；408、409、5xx 和传输异常不创建
  第二个 response
- 已确认的文本和 reasoning summary 不撤回、不重复
- EOF 未收到明确终态时不报告成功
- 首包前的编译、持久化、HTTP 或协议错误必须到达消息层并显示为失败
- 配置刷新不得取消持有活跃 lease 的模型请求；最后一个租约归还后旧实例只释放一次
- 只有按当前 chat/turn ID 明确记录的用户停止或破坏性历史修改可以静默结束
- provider `call_id` 在重连、进程重建和多 hop 中保持稳定
- 同一 provider `call_id` 只能投影、执行和重放一次；冲突身份必须在副作用前失败
- 一次工具 follow-up 失败不能取消长期工具 scope 或污染后续发送
- GPT-5.6 三模型使用精确 capability profile
- Prompt Cache、工具 schema 和 provider usage 有稳定持久化证据
- 定向测试、正式开发门禁、差异检查和 Debug APK 构建通过
- 真机和真实 endpoint 验收保持独立状态

2026-08-10 的第一次发送即中断修复只覆盖了 SharedStream 转发 owner，用户随后以
Crash `17bc3a28-84be-4fea-98fe-35b7ac406417` 证明消息收集 Job 和 UI 观察器仍存在重复异常
出口。当前实现已补齐消息层单一 owner 与次级观察器边界；包含生产链 502 故障注入在内的
`14/14` 个定向测试和完整 `170 suites / 997 tests` 通过，AndroidTest 编译、formal readiness、
差异检查及 Debug APK
构建审计通过。随后用户提供 vivo Android 16 tombstone：
`SIGABRT / Unexpected instruction: unused-e6`，栈指向
`MessageProcessingDelegate$sendUserMessage$sendJob$1.invokeSuspend`。当前已完成第一阶段
启动 lambda 拆分和第二阶段整轮发送拆分；最新 APK 的 DEX 审计显示主发送 continuation
`33 registers`、主流收集 continuation `30 registers`。真实 endpoint、网络切换、退后台、
进程重建后的自动 UI 恢复与 instrumentation 设备执行仍保持 `verification_pending`。

2026-08-11 的真机复测确认 native 闪退已经消失，但暴露出两条业务链根因：零输出和
`processStreamCompletion` 异常被收成空白完成，以及工具标签的 `provider_name` 被旧正则当成
工具 `name`。当前实现已修正名称边界、工具子任务失败所有权、完成阶段错误传播和 Responses
终态快照补齐。定向 JVM `44/44`、完整 JVM `1005/1005`、AndroidTest Kotlin 编译、
formal readiness、差异检查和 Debug 构建通过；最终 APK SHA-256 为
`C616FCD377C2BB1973DC9AE187ABB9940AE09847FB3B996986E7DEE408D69D80`，DEX continuation
仍为 `35 / 33 / 30 registers` 且无违规。目标设备复测继续保持 `verification_pending`。

同日后续真机复测确认工具调用已经恢复，但 GPT-5.6 仍会在首包前数秒静默结束。生产链审计
定位到全量模型配置刷新会越过活跃 `ServiceLease`，直接对当前 Provider 执行
`cancelStreaming + release`，而消息层又把所有 `CancellationException` 无来源区分地写成
`Idle`。当前实现已统一全量刷新与单功能刷新的 retired/lease 生命周期，并增加按 chat/turn ID
绑定的强类型取消终态。定向 JVM `63/63`、完整 JVM `1017/1017`、AndroidTest Kotlin 编译、
formal readiness、差异检查和 Debug 构建通过；最终 APK 为 `485888849` bytes，SHA-256
`23586FF77D4C8F7C3D51B77AE632B83B132662FE5ABE0B10274C81353770ADDF`，DEX continuation
保持 `35 / 33 / 30 registers` 且无违规。目标设备复测继续保持 `verification_pending`。

最新目标设备复测再次确认上一轮取消链修复没有覆盖最终现场路径：GPT-5.6 仍可在数秒后无正文、
无错误结束。当前增量把根因边界继续下沉到消息最终 owner：即使 Provider、共享流或服务层以
正常完成返回，`completeAssistantResponse` 也必须验证最终正文；零 chunk 或空白正文进入
`AI_STREAM_EMPTY_TERMINATION`，普通异常在 cleanup 后保持 Error，缺失回合终态进入
`AI_TURN_TERMINAL_MISSING`。相关 12 个定向 JVM suite、`72/72` 项和完整
`175 suites / 1024 tests` 均通过，AndroidTest Kotlin 编译、formal readiness、差异检查和
Debug 构建通过；最终 APK 为 `485889709` bytes，SHA-256
`19270A806AF7547D19D5A1AF6821DD51F0EA4CD39B37C552631EFB242904FE10`，Debug v2 单签名与
16 KB ZIP 对齐通过，DEX continuation 保持 `35 / 33 / 30 registers` 且无违规。目标设备复测
继续保持 `verification_pending`。

2026-08-11 15:14 的目标设备日志确认 GPT-5.6 已返回正文和工具调用，但终态快照以过滤后的
工具位置重复投影同一 `call_id`；一次工具 follow-up 502 又取消了长期普通父 Job，使后续回合
继承旧异常。当前实现已按完整 `response.output` 原始位置和稳定 `call_id` 统一流式投影、终态
快照、当前回合执行与历史重放，并在 XML 关闭前消费 `function_call_arguments.done` 的完整
参数。长期工具 scope 使用 `SupervisorJob`，当前 Deferred 仍把原始失败交回消息 owner。
完整 JVM 为 `175 suites / 1033 tests` 且失败、错误和跳过均为 `0`；AndroidTest Kotlin 编译、
formal readiness、差异检查和 Debug 构建通过。最终 APK 为 `485889789` bytes，SHA-256
`E43FB70208FD9F59B87D009D537C46A6524437A3480834A9B5508ED98BFDBE3F`，Debug V2 单签名、
16 KB ZIP 对齐、唯一 `arm64-v8a` 的 `51` 个 `.so` 和 DEX `35 / 33 / 30 registers` 均通过
静态审计；instrumentation 与目标设备复测保持 `verification_pending`。
