# AI 请求与执行契约

## 模型配置检测

模型与功能配置测试固定启动时的模型配置及参数。媒体请求使用内置带标记的图片、音频和
视频，回复必须匹配素材中的标记才能验证通过；请求成功但标记不匹配为 `unverified`，
请求失败为 `failed`。测试不会在提示词里泄露预期标记，不把“网络已通”解释为“理解能力已验证”。
工具返回中 `success` 表示没有硬失败，`verified` 表示所有探测通过；`passedTests`、
`unverifiedTests`、`failedTests` 分别计数。取消保持取消语义，测试结束释放服务和临时媒体。
聊天模型选择器订阅现有 DataStore 的一致快照，配置增删和修改无需重新打开选择器。

本文定义请求编译、提交、取消、恢复与历史重放的不变量。产品总览见 [CONTEXT](../../../CONTEXT.md)，详细设计见 [统一模型能力与可恢复执行](../architecture/model_capability_and_resumable_execution.md)，现场验收见 [AI 中断恢复](../../TODO/ai_interrupted_turn_recovery/index.md)。

## 对话数据统计

消息变体批量读取在同一 DAO 事务中按去重排序后的精确时间戳集合分批，每批最多 998 个
时间戳加一个 chatId 参数；跨批保持时间戳/变体顺序与长文本分块读取，不扩大为时间区间。
重新生成回答的 `message_persisted` Hook 在变体与审计事务提交后派发，使用持久化基底与新变体的
不可变快照，保留基底时间戳、收藏和角色，不读取可能已被切换的当前页面消息。

工具轮次插入的结果、拒绝和失败 XML 从独立行开始并以换行结束。纯思考警告的实时流与
保存内容使用相同换行边界，避免工具记录被解析成模型正文。

- 对话栏的上下文圆环打开统一模态底部面板，分组展示上下文、生成速度、累计用量、提示词缓存与数据覆盖；支持复制仅包含统计数值及口径的摘要，不复制对话正文或凭据。
- 上下文百分比为 `TokenStatisticsDelegate.currentWindowSizeFlow / (maxWindowSizeInK × 1024)`，保留一位小数；进度条最多填满，文字保留超过 100% 的真实估算值，上限未知时显示未提供。
- `EnhancedAIService` 每个初始请求和工具后续请求独立使用单调时钟，从首个非空输出计到流收集结束。速度是该请求输出 token / 此区间秒数，排除请求准备、首包等待、后续工具执行及用量持久化，不使用整轮消息耗时。该指标描述客户端接收区间，不是服务端纯解码基准。
- 流式期间使用已有 token 回调并标为估算；完成后按 `ProviderUsageSnapshot` 校正输出量和来源。非流式、单输出块、零时长、无输出、无 usage、流修订回滚均不伪造有效速度。取消/失败清理当前样本，工具执行期间保持最近已完成请求的速度，下一模型请求重新计量。UI 只在面板打开时订阅速度，避免高频分片重组整个聊天栏。
- `GenerationSpeedMonitor` 只允许当前请求发布，旧请求的迟到更新/清理无权覆盖新请求。统计委托按 chat 隔离内存样本；会话切换后使用对应样本，不持久化或从历史累计数推算速度。
- 累计用量继续使用已入账的 `ProviderUsageAggregate`，正在执行的回合稍后计入。总量为输入加输出，缓存是输入细分，推理是输出细分，均不重复相加。现有存储没有区分推理细分的未提供和报告 0，界面注明该限制。
- 没有任何供应商用量或缓存指标时，相应字段显示未提供。部分覆盖时明确提示总量不完整；缓存命中率分母只包括报告缓存指标的请求输入量。用量覆盖和缓存覆盖继续使用原有、各自独立的请求计数分母。
- 只读统计不使用禁用菜单项。底部面板复用统一滚动、模态隔离、拖动、关闭和 Back；数字不截断，大字体或窄窗口的标签和值上下排列；入口提供上下文百分比无障碍描述。

## 供应商与协议

- `ApiProviderType` / `apiProviderTypeId` 标识供应商，序列化 `ApiProtocol` 标识传输协议；两者独立，历史枚举仍用于持久化、审计和执行恢复。
- 供应商选择依次分为“中国大陆 / 国际 / 本地与自定义 / ToolPkg”，不重复设置精选供应商。协议目录由 `ApiProviderConfigs` 声明，不支持的供应商与协议组合明确拒绝。
- 多协议供应商由用户显式选定协议，切换供应商只采用该供应商声明的默认协议。地址末尾 `#` 仅控制 endpoint 补全，不触发协议识别；运行失败不自动切换协议。
- Responses 使用 `OpenAIResponsesProvider`，Anthropic Messages 使用 `ClaudeProvider`。Anthropic、Google、xAI 的 OpenAI-compatible Chat 使用通用 `OpenAIProvider`；模型列表的认证、地址和解析仍同时依赖供应商身份与协议。

## 请求、终态与重放

- “思考模式”与现有 `thinking_quality_level: 1..5` 是唯一用户 reasoning 接口。普通 OpenAI
  Chat Completions / Responses profile 的五档显式映射为 `low / low / medium / high / high`；
  关闭思考固定编译为 `none`。`gpt-5.6*` 前缀模型使用 Codex 五档
  `low / medium / high / xhigh / max`。Pro、Fast、Ultra、传输和执行持久性不是第六档，
  也不能改写用户选择的五档。
- `ModelCapabilityResolver` 与 `ModelRequestCompiler` 是模型能力和 wire 参数的语义所有者。
  所有 `gpt-5.6*` 前缀模型进入同一 Codex reasoning profile；普通 OpenAI/兼容模型仍可在
  已声明 reasoning 能力的情况下使用思考模式，但不会使用 `xhigh` 或 `max`。官方能力同时
  要求 provider 类型为官方 OpenAI，并且补全后的请求地址精确属于
  `https://api.openai.com/v1/responses`；仅保存为 `OPENAI_RESPONSES` 不能把 Pipio、Pixel、
  Sekiro 或其他自定义地址提升为官方合同。`gpt-5.6* + Responses` 在官方和兼容 endpoint
  上都请求 `summary=auto`，使服务端真实返回的 reasoning summary 可以进入现有
  `<think>` 可见消息；兼容 Responses 仍不得自动获得 Background、sequence resume、
  Prompt Cache、Tool Search、`reasoning.encrypted_content` 或 strict schema。摘要增量、
  part 完成、output item 完成和终态快照由一个单调 projection 去重；内容分叉必须报协议
  错误，不能拼接损坏文本。普通 OpenAI Chat 请求使用 `reasoning_effort`，Responses 请求
  使用 `reasoning.effort`。
- 官方 GPT-5.6 Responses 使用 `background=true`、`store=false`。`ProviderRequestContext`
  在模型请求前固定 chat、message timestamp、variant 与 hop；`ProviderExecutionRepository`
  持久化 response ID、事件和单调 sequence cursor。未应用事件使用 `-1` 哨兵，官方
  Responses 首事件固定从 `sequence_number=1` 开始，后续事件必须严格连续。已知 response ID
  后的传输中断只能用 `starting_after` 续接同一 response，不能创建新 response 或撤回已确认
  UI 内容。
- `response.created` 前只有明确的 HTTP 429 拒绝允许重新提交；408、409、5xx 和传输异常进入
  `SUBMISSION_UNKNOWN`。已知 response ID 后瞬时 HTTP/传输错误只重试同一 GET，404/410
  标记远端状态已 `EXPIRED`。没有声明官方 sequence resume 的兼容 Responses 仍使用同一
  at-most-once 提交边界：未知提交状态不执行普通流式整轮回滚或重新 POST，已经确认的部分内容
  保持原样，当前回合以可见错误结束。
- Responses 请求使用独立的 OkHttp client 并固定为 HTTP/1.1；该 client 不保留空闲连接，且关闭
  OkHttp 的连接失败重试，因此每个串行工具 hop 在上一个流释放后新建连接，一次 Provider 语义
  提交也只对应一个 POST。Chat Completions、Anthropic Messages 和其他 AI 请求继续使用原有的
  `HTTP_2 + HTTP_1_1`、10 条空闲连接与连接失败重试。该传输选择不改变序列化 `ApiProtocol`、
  endpoint、provider、model、key 或 at-most-once 提交状态机；未知提交状态仍不重新 POST。
- `OpenAIResponsesExecutionPersistence` 是可恢复 Responses 协调器的持久化依赖边界；生产
  唯一实现直接委托 `ProviderExecutionRepository`，不持有第二份执行状态。本地 JVM 故障注入
  从公开 `sendMessage` 流进入同一生产协调器，通过 loopback HTTP 返回首个
  `response.created` 前的 502，并精确验证只发送一个 POST、repository 收到
  `SUBMISSION_UNKNOWN / HTTP_502_SUBMISSION_UNKNOWN`，且异常到达流收集者。
- 冷 Provider 流转为共享流时，`SharedStream` 是唯一的上游终止传播所有者：正常完成无原因
  关闭，上游失败携带原异常关闭，当前和晚到收集器都从共享流接收失败。完成转发的 owner
  协程不得再次把同一 Provider 异常抛入 `GlobalExceptionHandler`，否则一次可见发送失败会被
  重复升级为 `APP_FATAL`。首包前的请求编译、持久化、HTTP 或协议错误必须继续传播到
  `EnhancedAIService` 和消息层；禁止把失败按零内容正常完成关闭。
- 普通发送由主 `sendJob` 唯一持有消息失败状态。`streamCollectionJob` 在结构化子作用域中
  收集正文，并通过 `CompletableDeferred` 把原始终止原因交给主任务；非取消失败交付后该
  收集 Job 必须正常结束，`CancellationException` 继续保持取消语义。自动朗读、Waifu 分段、
  修订事件、首包探测和可修订文本渲染属于次级观察器：它们记录并结束自己的非取消失败，但
  不得第二次升级同一个 Provider 终止原因。主任务收到原异常后进入现有
  `InputProcessingState.Error`、错误提示和运行态清理链。
- `InputProcessingState.Error` 与错误弹窗是同一次失败的两个投影：前者驱动发送状态，后者由
  `ChatServiceCore` 持有的唯一 `UiStateDelegate.errorMessage` 驱动。消息处理委托的
  `showErrorMessage` 回调必须把安全用户消息写入该共享 delegate；`ChatViewModel` 取得同一
  实例，`AIChatScreen` 再从中显示 `ErrorDialog`。只记录日志会让 Provider 失败正确结束加载，
  却在主界面表现为无提示静默结束。提交状态未知异常的外层安全文本必须包含底层 HTTP 或传输
  摘要，使 502 和上游错误能直接显示，而不是只显示本地 execution ID。
- 模型服务实例按请求持有 `ServiceLease`。单功能刷新和全量配置刷新只清除新请求可见的缓存
  并将旧实例标记为 retired；活跃租约归零前不得调用 `cancelStreaming` 或 `release`，新请求
  必须创建并使用刷新后的实例。最后一个租约归还后，retired 实例只释放一次。
- 助手取消请求以 chat 和 turn ID 为身份记录。只有明确的用户停止和破坏性历史修改可以静默
  进入 `Idle`；配置刷新、生命周期失效、应用退出和来源未知的 `CancellationException` 必须
  投影为可见 `Error`。已取消的发送 Job 只允许在受限 `NonCancellable` 区域提交终态和错误
  提示及 replay-safe 部分消息，不得重新执行请求、工具或其他业务逻辑。用户停止由外层取消
  owner 保存同一安全投影；破坏性历史修改不保留部分回答。
- 工具执行和工具结果 follow-up 属于同一消息主失败链。长期工具 scope 使用
  `SupervisorJob` 隔离不同任务；当前 `Deferred.await()` 仍把原始失败交回调用者。一次
  follow-up 失败不得取消长期父 scope、并行污染其他独立回合或使后续发送继承旧异常；
  `launch + join()` 不得作为失败传播合同。`processStreamCompletion` 不得把异常改写为
  `Idle`，当前响应轮零输出不得投影为 `Completed`。
- 共享流在同一临界区登记 replay、订阅快照与终态，由单个发布者按队列交付；恢复观察者时
  不持有状态锁。观察者退出、同步重入或迟到订阅不能打断生产者、打乱正文或越过关闭信号；
  生产者自身取消仍阻止后续发布。
- Responses 历史中的 assistant 页图/附件在 wire 适配时按原顺序分拆：文本保留 assistant
  角色，图片使用注明来自 assistant 历史的 user 视觉上下文，不能携带为 assistant `input_image`。
  保留图片 URL/文件 ID/精度参数；不改写本地气泡、不丢弃附件，不放宽工具事务闭合校验。
- SSE 的完整 `data` 行在后续分块读取异常时先交付，随后仍传播原始 IO 异常；缓冲尾部 JSON
  也被截断时保留传输原因。只有真实 `response.completed` 可以判为完成，缺终态仍保留未知
  结果语义。明确 `failed/incomplete/cancelled/error` 及本地协议处理错误不得统一包装成提交未知。
- 流退出前补写原始正文的最后一个审计 delta/revision，再生成工具闭合投影；审计故障保留
  `RECORDING_INTERRUPTED` 和日志，不能替换 Provider 的真实失败或阻止独立的聊天正文保存。
  对话标题在截长前剥离思考及私有 `<meta>`；attempt 分别记录思考/可见字符与已知 response ID。
- `MessageProcessingDelegate.completeAssistantResponse` 是普通发送进入成功终态前的最终
  内容 owner。它必须从共享流重放和消息状态重建最终正文，记录 chunk 数、可见字符数、
  provider、model、provider request context 和最后输入状态；空白正文以
  `AI_STREAM_EMPTY_TERMINATION` 进入可见 `Error`。普通非取消异常必须同时写入
  `finalInputStateAfterSend`，保证服务随后重放 `Idle` 时，cleanup 后仍由消息 owner 恢复
  Error；缺失任何终态的回合以 `AI_TURN_TERMINAL_MISSING` 失败。写入 `COMPLETED` 前还必须由
  `AssistantReplayHistoryProjector` 证明所有工具事务闭合，不能把 call-only 正文标记为成功。
- `AssistantReplayHistoryProjector` 是 durable assistant replay surface 的唯一纯状态机。它按
  原顺序识别随机后缀 `tool_* / tool_result_*`、独立 `name`、协议工具名
  `provider_tool_name`、可选 `provider_call_id` 和流式终态标记 `provider_result_terminal`。
  `name` 保留代理目标等 UI 展示名，协议匹配和 Provider follow-up 使用原始 invocation 名；同一
  工具 Flow 的中间结果标记为非终态并只留在实时流与审计中，只有正常结束后的终态结果可以闭合
  调用。旧 `package_proxy / proxy` 的同消息结果只有在调用参数能证明原始协议工具名时，才会补写
  `provider_tool_name`；跨消息结果不能原地规范化展示别名，因此不会作为安全闭合证据。并行工具的
  live result 按完成顺序到达，投影必须把终态结果匹配到整组 pending calls，
  并只在 durable 副本中规范为原调用顺序；即时拒绝和实际执行结果交回 Provider 前也必须共同恢复
  invocation 顺序，结果缺失或无 owner 结果直接失败。工具结果后独立输出的纯图片链接属于附件，
  并行或流式结果尚未闭合时由同一投影暂存位置，全部真实终态到齐后移到事务末尾；普通文字
  不获得此豁免。已经有序且附件位于事务外的完整事务逐字保留。未闭合、
  截断、孤立结果、无法唯一匹配或身份冲突从所属事务起移除整个后缀，不生成工具结果。正常完成
  owner 必须保存闭合检查返回的规范化正文，不能校验后写回原始错序正文。实时
  `contentStream` 与 conversation audit 继续保存原始完成顺序；
  每秒消息快照、普通失败收尾、手动停止和非预期取消只把 replay-safe projection 写入
  `messages / message_variants`。
- 活动 assistant 在首个有意义快照前只由 `ChatRuntime.activeStreamingTurn` 持有，不提前写入
  `messages`。投影 eligibility 固定为 `NONE / LOCAL_ONLY_REASONING / REPLAYABLE`：空白、仅状态、
  仅 Provider metadata 和没有安全前缀的未闭合工具事务不形成 durable assistant；仅思考内容可以
  作为本地中断事实保留，但永不进入 Provider replay。发送前修复会对没有 variant 的旧 `NONE`
  assistant 写审计 tombstone 后移除，复杂 variant 仍在请求编译边界重新验证。
- OpenAI-compatible Chat、Anthropic Messages 和 Gemini 的每个流式请求都绑定一个单调 Provider
  session generation。Call、Response、取消状态和清理操作同时校验 session 与句柄 identity；旧流的
  late bind/finally 不能清除或取消新流。未知提交的传输失败、408、409 和 5xx 不创建第二个 POST，
  只有明确 429 拒绝可按既有重试策略重新提交；官方 Responses 已知 response ID 后仍只续接同一
  response。
- 下一次发送在读取自动 compaction snapshot 和添加新用户轮次之前检查旧历史；通用自动压缩
  入口先等待当前活动回合完成安全终态持久化，再执行同一检查，不能把仍在运行的工具事务当成
  中断残留。恢复读取采用严格失败语义，数据库读取失败时不得以空历史继续请求。截断或未闭合
  事务必须先检查是否在后续持久 AI 消息中按 FIFO 顺序、协议工具名和 call ID 合法闭合；普通
  user/summary 正文中的工具样式 XML 不会被请求编译器解释为工具结果，不能作为闭合证据。若直到
  replay boundary 仍未闭合，它已经是 Provider 无法重放的历史，必须修复，不得再以
  `completedAt != 0` 或缺少同 timestamp 失败审计为由保留。完成时间与终态审计只属于诊断事实，
  不是修复资格。跨消息保留还要求调用消息自身已经满足 Provider FIFO、没有流式中间结果，也没有
  待应用的结果重排或协议名规范化；`call A, call B, result B` 后接 `result A` 不能被误认成合法
  闭合。真正损坏的当前 variant 通过
  `ConversationAuditRepository.reviseMessage` 原子写入修复 revision、投影、审计事件和 seal；
  已由后续消息合法闭合的跨消息事务不改写。`ConversationCompactionContract` 随后只对修复后的
  同一 runtime history 建立 snapshot，并继续执行 digest、route、工具闭合和相邻 summary 门禁。
  通用自动压缩与“插入总结”的切片压缩入口都必须先等待活动回合并执行这项修复；单条消息重新
  生成在保存新 variant 前还必须通过 `regeneration_completion` 闭合断言。
- `ConversationService` 把同一批并行调用产生的连续 `TOOL_RESULT` 合并成一个 Provider turn，
  与首次工具 follow-up 的聚合形状一致；`TOOL_CALL` 继续保持独立，不能丢失调用级元数据。
- 多工具 follow-up 的 `64000` 字符总预算先为每个真实调用预留完整 `tool_result` XML 信封，再把
  剩余 payload 空间按结果数分配；不得在达到总长度时停止追加后续结果，也不得对最终 XML 直接
  按字符截断。结构信封本身无法装入上限时必须在本地显式失败，不能让 Provider 收到部分结果组。
- OpenAI 请求体编译、工具历史校验或本地请求创建在 `call.execute()` 之前失败时，异常必须原样
  交回消息失败链，不进入网络重试，也不能包装成“连接超时”；HTTP 提交开始后的传输失败继续由
  既有重试与 at-most-once 合同处理。
- Provider 原生工具标签中的 `name`、`provider_name`、`provider_call_id` 和
  `provider_response_id` 是四个独立属性。工具解析只读取独立 `name` 属性，不能把
  `provider_name` 的值当作工具名。
- `response.completed` 的完整 `response.output` 是终态一致性快照。工具项必须保留原始
  `response.output` 位置，并通过稳定 `call_id` 与流事件绑定；过滤后的工具数组位置和随机
  XML 标签只属于本地投影，不能成为工具身份。`response.function_call_arguments.done` 携带
  的完整参数在关闭 XML 前进入同一解析器。中间文本或 `function_call` 事件缺失时，只补齐
  尚未交付的正文尾部和工具调用；快照与已确认正文、工具名、调用身份或参数分叉时按协议错误
  失败，不撤回或拼接已确认内容。
- 普通发送实现按单回合状态载体、用户消息准备、助手请求准备、Responses 提交、共享流收集
  和完成收尾分开编译。该结构是针对 vivo Android 16 `SIGABRT / Unexpected instruction:
  unused-e6` 的 ART DEX 兼容性修复；它不增加请求重试、不改变提交次数、不引入模型降级，也
  不改变已知 `response_id` 的 `starting_after` 续接。
- `message_provider_states` 保存同一个 Responses execution 的 provider-private output items、
  sequence cursor 与 usage，只服务已知 `remoteResponseId` 的断流续接；普通下一用户轮次、
  PromptTurn 历史编译和 compaction 不读取该表。跨轮 `function_call / function_call_output`
  仍由 replay-safe `ChatMessage.content` 编译，typed execution state 不能成为第二份跨轮历史
  owner。相同 provider 与 `call_id` 只能对应同一工具名和语义一致的参数；相同身份
  在 XML 投影、当前回合执行和 Responses 历史重放中都只保留一次，冲突必须在副作用前失败。
  compatible endpoint 没有真实远端 response ID 时只做当前回合身份规范化，不伪造 response
  ID。`tool_invocation_ledger` 仅以真实 provider、response ID 和原始 `call_id` 为键，已运行
  或完成的 provider-native 工具调用不能再次执行；每个 `call_id` 的历史输入最多包含一个
  `function_call` 和一个语义一致的 `function_call_output`。
- Prompt Cache key 使用 profile revision、精确模型、稳定 system/developer 前缀和 canonical 排序的
  tool schema，不包含当前用户消息、effort、trace ID 或时间戳。官方 GPT-5.6 Responses 仅在
  大工具集存在足够长尾函数时启用 Tool Search；常用文件、搜索和计算工具保持 eager。
- 首条用户消息从动态输入变为历史时不改变缓存路由键；同一键仅用于供应商路由，实际命中仍取决于
  精确输入前缀与供应商策略。编辑消息、压缩历史、修改系统提示或工具定义均可能影响后续命中。
- Responses 按 SSE 事件边界合并多行 data，再处理 JSON。收到 `response.completed` 后立即收尾，
  不等待中转关闭连接或额外 `[DONE]`；没有完成事件的 EOF 继续显式失败，不重发未知提交。
