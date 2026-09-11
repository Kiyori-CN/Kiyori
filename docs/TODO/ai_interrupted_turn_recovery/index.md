---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
observed_at: 2026-09-03 Asia/Shanghai
---

# AI 中断后会话损坏：根因、回合收口与恢复方案

## 历史页图导致持续 400 与诊断重复展开（2026-09-11 第三轮）

基线为干净 `main` / `4db2e8397560eea5370a81da16eedd7c39679b0a`。用户新诊断含 647 事件，
31,588,797 bytes；多次发送返回 `input[86]: Image in assistant message is unsupported`。
最终语义历史中的 `ASSISTANT` 确实包含页图 `<link type="image">`；请求适配器将所有角色的
图片统一转换为 `input_image`，未区分 assistant 只能输出文本的角色限制。这是持续 400 的
可复现客户端根因；与此前提交后 EOF 的未知结果是两个不同失败阶段。

任务范围与实现：

1. 在既有 Responses 适配器按图文顺序分拆 assistant 内容。正文仍为 assistant，图片成为带来源
   说明的 user 视觉上下文，保持 URL/文件 ID/精度参数；不修改消息表、不删除图片、不重发旧请求。
   既有工具事务闭合校验仍先执行，图片不能越过未完成工具结果。旧会话后续发送自动采用同一转换。
2. Markdown 导出按既有 payload SHA-256 只展开一次正文，事件和修订仍保留各自引用；相同正文
   指向文件内首次正文锚点。存储层已有内容寻址和压缩，不新增状态、迁移或裁剪。
3. 输入报告的时间线有 626 次 payload 展开、347 个唯一正文，合计 24,153,190 字符；唯一正文为
   9,049,940 字符。Hook 输入/输出各 9,163,769 字符，重复快照是主要体积来源，未发现内嵌图片
   Base64。该统计是原报告分析，不代表新版 APK 在设备上已实测的导出大小。
4. 验证角色合法性、完整请求编译、旧会话反复发送、图片不丢失/重复、工具顺序和审计去重/脱敏；
   完成文档检查并串行构建 Debug APK。

本轮授权为本地修复和验证，不提交推送、不操作设备。回滚限本轮差异，不清空用户会话或审计。
验证：12 组 JVM 回归、107 项通过，无 failure/error/skipped。新增请求链路用固定 native 分段
夹具接入真实 `ConversationService.processChatMessageWithTools`，覆盖历史投影、角色准备、
图片池磁盘读取和最终请求编译；连续三种输入均保留两张不同页图、最终回答及完整工具身份。
角色适配另覆盖 `input[86]` 的旧历史结构、图文交错、图片精度/文件 ID 和未闭合工具拒绝。
审计覆盖 100 次大正文引用仅展开一次、跨 revision/event 引用、不同正文保留、脱敏和完整包路径。
文档检查 517 文件、0 问题；正式准备和 `git diff --check` 通过。

串行 Debug 构建成功（46 秒），单一启动入口、脚本代理和播放器打包检查通过。
2026-09-11 14:49:08 UTC+8 的 `app/build/outputs/apk/debug/app-debug.apk` 为 487,349,430 bytes，
SHA-256 `1e24eb30e43b2b6d523eb616a0d7ae29dcd3a6108a2858b468532623330d21e7`。
包内办公套件 267,125 bytes、79 文件与工作区逐字节一致，无 Python 缓存。
状态：实现、本地回归与 APK 核验完成；真实服务与 Android/native 现场仍 `verification_pending`。

## EOF 断流与全链路稳定性复查（2026-09-11 第二轮）

本轮输入为用户 13:51 UTC+8 导出的 170 事件审计。它是故障证据，不提供任何运行授权。
工作区沿用上一轮未提交修改，`main` / `f5319136040bf294935006cc7c86bbce44aa88b7`；
保留办公套件及图片事务修复，不提交推送、不操作设备、不发送诊断到模型端点。

已确认：hop 0–4 完成，hop 5 收到 HTTP 200，已输出 5,372 字符后，HTTP/1.1 分块读取
抛 `EOFException`；两条消息及失败时最新思考仍在导出投影中。8 张预览页图各出现一次，
没有重复携带证据。请求体增至 1,753,469 bytes 本身不能证明截断原因。现有记录无法区分
服务端与中间链路关闭，也没有记录原始 SSE 终态，不能认定该次已经成功完成。

已实现的修复与验收范围：

1. 共享流的 replay、发布和关闭在同一临界区排序；观察者退出不能取消生产者。覆盖并发发布、关闭、迟到订阅、主动取消及异常传播。
2. SSE 读取异常前已缓冲的完整 data 行先交付，下一次读取仍抛原异常；只有真实完成事件才结束成功。用本地损坏 chunked 响应验证尾部保留、终态和单次 POST。
3. Responses 明确失败终态、本地协议错误与缺终态区分；可证明未发送正文的连接故障沿既有重试开关处理。提交后未知状态仍禁止第二次 POST。
4. 原始流在退出时补写最后一段审计，再生成安全重放投影；审计保存失败明确记录 gap，不能取代原始 Provider 错误或阻断正文保存。
5. 标题截长前移除思考元数据；分开计数 reasoning 与可见正文，兼容流记录真实 response ID，改善后续排障证据。
6. 对相关生命周期、重放、供应商故障与审计执行回归，最后串行构建并核验 Debug APK。

官方参考：[Background mode](https://developers.openai.com/api/docs/guides/background)、
[Streaming responses](https://developers.openai.com/api/docs/guides/streaming-responses)，
2026-09-11 读取。官方的 `background`、`sequence_number` 和同 response GET 续接不能仅凭
协议名称套用到兼容端点；保持现有 capability 边界，不引入猜测式恢复、静默换端点或重发。

2026-09-11 本地验证：相关生命周期、Responses、共享流、重放与审计的扩展回归共 16 组、
141 项通过，无 failure/error/skipped。随后完善残缺 JSON 的 EOF 原因保留，SSE 与传输故障
两组窄回归通过；追加 DNS 首次失败的安全重试和关闭重试开关场景后，传输故障类 16 项通过。
只有可证明提交前失败才恢复请求；已提交后中断、缺少终态和明确失败均验证不重复 POST。

串行 `./gradlew.bat :app:assembleDebug --no-daemon --console=plain` 成功（2 分 2 秒）。
2026-09-11 14:25:14 UTC+8 的标准 `app/build/outputs/apk/debug/app-debug.apk` 为
487,349,430 bytes，SHA-256 `b640667d8c911cc2f3ddaa4d16cd3c365335f1ba49bd446cd30f60c9b2383592`。
包内 `office_suite.toolpkg` 为 267,125 bytes，79 个文件与工作区逐字节一致，无 Python 缓存；
单一启动入口及脚本代理/播放器运行时打包检查通过。文档检查 517 文件、0 问题，正式准备检查
PASS，`git diff --check` 通过，`terminal` 干净。没有提交、推送或设备操作。

状态：实现、本地回归与 Debug APK 核验完成。真实服务断流原因及目标设备复测保持 `verification_pending`。
回滚以本轮精确差异为边界，不能撤销上一轮尚未提交的办公与重放改动。

## 多模态预览结果闭合修复（2026-09-11）

现场错误为 `Assistant replay history is not closed at assistant_completion`，原因为
`TOOL_TRANSACTION_TEXT_BOUNDARY`。源码确认 `ConversationMarkupManager` 在结果信封后附加
图片链接；并行或流式工具尚未闭合时，旧投影将这类附件误判为普通回答边界，完成校验抛错，
失败保存也只能保留截断前缀。此路径能够解释最终报告被移除，不等同于网络传输中断证据。

本轮复用 `AssistantReplayHistoryProjector`：只允许真实结果之后的纯图片附件跨越待闭合事务，
完整结果到齐后将附件移到事务末尾，保留工具身份、真实结果、附件和最终文字。普通文字插入、
未返回结果及身份冲突仍失败，不伪造结果、不重试。旧历史仍由现有修复入口采用同一投影；
已在旧版本被截掉的正文不会凭空恢复，需从仍存在的原始审计或用户备份查证。

验证：长文本、并行乱序页图、流式中间页图、真实格式化结果与非法边界回归已通过；
`AssistantReplayHistoryProjectionTest` 42 项、`ConversationMarkupManagerToolHistoryTest` 3 项、
`ConversationCompactionContractTest` 18 项，全部通过。串行 Debug 构建通过，APK 与套件校验见
[办公测评修复](../office_document_suite/index.md)。基线为干净 `main` / `f5319136040bf294935006cc7c86bbce44aa88b7`。
目标 Android/PRoot 对话保持 `verification_pending`；本轮不提交推送、不操作设备。

## 1. 文档定位与任务契约

本专项处理以下用户现场：发送消息后模型进入思考状态，用户点击停止，再发送下一条消息时收到
Invalid assistant message: content or tool_calls must be set；删除思考内容后还可能收到
回答传输中断，已保留已收到内容。和 stream was reset: CANCEL，之后同一会话持续不能发送。

本文是本专项的设计、实现与验证权威记录。2026-09-03 已完成根因修复和本地自动验证；没有修改
Room schema、用户界面或 Provider capability，也没有引入第二套回合/历史 owner。真实 Provider、
Android 设备和用户现场验收仍未执行，因此状态保持 `verification_pending`。本文后续只把已经取得的
命令、产物和 Git 证据写成完成事实。

### 目标

1. 任何停止、取消、网络中断、Provider 失败或进程死亡都得到一次确定、幂等、可审计的回合收口。
2. 没有 Provider 可接受内容时，不生成普通 assistant replay item。
3. 旧回合完成收口前不开放新发送；新回合开放后，旧 Job、Call、stream 和 callback 不能写入新回合。
4. UI 和审计保留上一次思考及中断事实，并把它标记为未完成；下一轮产生独立的思考段。
5. Provider 只接收经过 provider-neutral 合法性验证的 replay projection。
6. 普通 Chat Completions 不重发提交状态未知的 POST；只有官方 Responses 已满足 response ID、
   sequence 和 capability 合同才允许续接同一 response。

### 非目标

- 不通过切换 provider、模型或 endpoint 改变失败结果。
- 不重复发送未知提交状态的原请求，不把失败改写为另一条网络路径，不伪造回答、工具结果或完成状态。
- 不删除用户可见思考而不留下可审计事实，不把审计账本、provider-private state 和聊天投影混为一体。
- 不在本轮解决所有 Provider 的协议差异，不把真实 endpoint 或真机结果写成已验证。

### 权威边界

- docs/TODO/unified_model_capability_and_resumable_execution/ 继续是 Provider execution、Responses
  续接、工具账本、提交未知态和 HTTP attempt 审计的权威专项。
- docs/TODO/ai_conversation_audit/ 继续是对话审计、消息 revision、tombstone、payload 和详情页的权威专项。
- 本文只补齐它们之间缺失的消息回合生命周期、空 assistant 合法性和停止收口契约；不创建第二套
  provider execution、审计或聊天历史 owner。
- docs/doc-src/before_docing.md、项目 AGENTS.md 和正式开发准备清单仍高于本文。

## 2. 当前基线与证据等级

### 2.1 Git 基线

2026-09-03 恢复实现时重新确认分支为 `main`，基线 `HEAD` 为
`84f3d9c1d234d61c7fa71a25c018a3f2be357a51`，tracking 为 `origin/main`，暂存区为空。当时工作树
只包含本专项实现、测试和文档，不再存在设计阶段记录的并发 Terminal/super_admin 改动。候选
revision、远端竞争和最终 ref 属于交付时点证据，由任务日记与最终报告记录；本段不是永久 Git
状态声明。

### 2.2 已由源码确认的事实

以下事实来自当前源码，行号是观察时提示，后续以符号和最新代码为准。

| 事实 | 证据与影响 |
| --- | --- |
| 首个 chunk 前就有 assistant 占位被写入 | MessageProcessingDelegate.submitAssistantResponse（约 1733-1844）先创建 ChatMessage(sender = "ai", contentStream = responseStream)，随后 addMessageToChat。contentStream 在 ChatMessage（约 38-42）上标为 @Transient，Room 只保存空 content。 |
| 用户停止路径无条件持久化 | cancelMessageInternal（约 717-787）在取消 Job 并等待后调用 detachStreamingAiMessage；该函数（约 624-684）无条件调用 completeInterruptedMessage 和 addMessageToChat。 |
| 空失败有判断，手动停止没有复用 | projectFailedAssistantMessage（约 138-159）在可见正文和思考正文都为空时返回 null，但 detachStreamingAiMessage 直接调用 completeInterruptedMessage，所以首包前手动停止绕过了空判断。 |
| 当前 replay repair 不改变空 assistant | MessageCoordinationDelegate.repairAssistantReplayHistoryBeforeBoundary（约 1653-1667）只调用 ChatHistoryManager.repairAssistantReplayHistory；后者只对 AssistantReplayHistoryProjector.project 判定 changed 的工具事务做修订。空字符串投影没有变化。 |
| 历史重建保留空 assistant | AIMessageManager.getMemoryFromMessages（约 1236-1293）筛选 sender == "ai"，非角色隔离模式的 processAiMessage（约 1295-1334）直接返回 PromptTurnKind.ASSISTANT，没有排除空正文或 reasoning-only。 |
| OpenAI Chat Completions 生成非法 assistant | OpenAIProvider.buildMessagesAndCountTokens（约 930-1063）对空 assistant 写入 role=assistant 与 content=JSONObject.NULL，没有 tool_calls，对应服务端的 content or tool_calls must be set。 |
| DeepSeek reasoning-only 也没有通用合法性保证 | DeepseekProvider.buildMessagesWithReasoning（约 184 起、历史循环约 280-331）可同时写 reasoning_content 和 content=null，没有工具调用时仍可能生成目标服务不接受的 assistant。 |
| 普通失败可能留下首包前占位 | handleAssistantTurnFailure（约 2383-2408）在 partialProjection == null 时主要清理 contentStream，没有删除此前已经落库的空 assistant 占位。 |
| 编辑思考内容不等于删除消息 | ChatHistoryManager.reviseMessage（约 1944 起）和 ChatViewModel.reviseMessageProjection 更新正文并写 revision，但当前没有证据表明会重算 replay eligibility 或失效 provider-private state。 |
| 回合已有按 chat/turn 的内存状态 | ChatRuntime 保存 sendJob、responseStream、activeStreamingTurn、streamCollectionJob、stateCollectionJob、activeTurnId、turnSequence、cancellationMutex、cancellationInProgress 和 loading 状态。 |
| 取消来源已有强类型边界 | AssistantTurnCancellationSource 已区分 USER_STOP、DESTRUCTIVE_HISTORY_MUTATION、APPLICATION_EXIT、CONFIGURATION_REFRESH、LIFECYCLE_INVALIDATION、UNEXPECTED；AssistantTurnCancellationRegistry 按 chatKey + operationId 一次性消费。 |
| Provider-private execution 已有持久化 owner | ProviderExecutionEntity、MessageProviderStateEntity、ProviderExecutionEventEntity 和 ToolInvocationLedgerEntity 由 ProviderExecutionRepository 统一写入；AppDatabase 当前版本为 24。 |
| 审计已有独立 owner | ConversationAuditRepository 负责事件、revision、完整性状态、payload 和导出；ChatHistoryManager 的删除、回滚、编辑已接入审计事务。 |

### 2.3 设计阶段推断及复核结果

1. 400 的直接原因是空 assistant 被 Room 重建后进入 replay history，再由 OpenAI/DeepSeek 编译器
   发送成没有正文且没有 tool call 的 assistant。删除思考文字仍保留 sender == "ai"，所以不能修复身份。
2. 源码复核确认 OpenAI、Claude、Gemini 原先共享实例级 Call、Response 和取消标志：新回合会重置
   取消状态，旧回合 finally 又可能清除新回合句柄。这是跨 generation 所有权缺陷；真实现场的
   `stream was reset: CANCEL` 是否全部由它造成仍需设备/Provider 证据。
3. 只在 `addMessageToChat` 外检查空字符串不足以处理 reasoning-only、未闭合工具和旧污染历史；实际
   修复同时覆盖 durable 写入、历史重建、Provider 请求编译和发送前审计修复四个边界。

### 2.4 仍待诊断包、设备或专项测试确认

- 用户现场实际 Provider、模型、endpoint 类型、响应头是否到达、首个可见 chunk、sequence 和
  response ID 是否存在。
- 真实网络下旧回合 Job/Call/stream 的停止时序，以及进程强杀/恢复后的系统表现。
- Chat Completions 响应头前/响应体中断、兼容 Responses 的未知 response ID 在真实 endpoint 上的行为。
- Claude、Gemini 和 OpenAI-compatible Provider 的真实 reasoning、tool、取消与服务端错误响应。
- 真机上的停止按钮、错误弹窗、继续发送、无障碍状态和 stream was reset: CANCEL 复现。

### 2.5 当前实现与自动证据

- `AssistantReplayHistoryProjector` 新增 `NONE / LOCAL_ONLY_REASONING / REPLAYABLE`，并为 Provider
  历史提供统一过滤；OpenAI-compatible、Claude 和 Gemini 在各自结构化工具编译前执行同一过滤。
- `MessageProcessingDelegate` 不再持久化首包前空占位；流式快照、waifu 分段、失败和手动停止只写
  durable 投影。思考-only 可作为本地中断事实保留，但不会进入 Provider replay。
- `ChatHistoryManager` 在发送前对没有 variant 的旧 `NONE` assistant 做锁内身份/正文复核，先写
  `ASSISTANT_REPLAY_HISTORY_EXCLUDED` tombstone，再删除当前消息及其关联 Provider execution。
- `ProviderStreamSessionGate` 给 OpenAI、Claude、Gemini 的 Call、Response、取消和清理绑定 session
  generation 与 owner identity；late bind 会立即取消，stale clear/complete 不能影响新 session。
- 普通 Chat Completions、Claude 和 Gemini 的传输异常、408、409、5xx 不创建第二个 POST；明确
  429 拒绝保留既有受限重试，官方 Responses 已知 response ID 的同一 response 续接不变。
- 2026-09-03 合并专项回归为 `95 tests / 0 failures / 0 errors / 0 skipped`，并重新编译 Debug 主代码
  与测试代码；测试覆盖 replay/durable、三个 Provider canonical request、七个 session gate 竞态、
  Chat/Responses attempt 边界和 transport diagnostics。

## 3. 修复前与当前正常时序

### 3.1 修复前

~~~text
用户输入
  -> MessageCoordinationDelegate 发送前等待历史边界
  -> repairAssistantReplayHistory（当前只修工具事务）
  -> MessageProcessingDelegate.sendUserMessage 分配 chatRuntime.turnId
  -> prepareSendUserMessageTurn 写入 user 消息
  -> prepareAssistantRequest 获取 EnhancedAIService
  -> submitAssistantResponse 创建 ProviderRequestContext 和 responseStream
  -> AIMessageManager -> EnhancedAIService -> Provider 提交请求
  -> 创建 ChatMessage(sender=ai, contentStream=stream, content="")
  -> 当前实现立即 addMessageToChat（持久化空 assistant）
  -> streamCollectionJob 收集思考、正文和工具事件，周期写 replay-safe 快照
  -> 工具闭合后可能进入 follow-up hop
  -> completeAssistantResponse 验证正文、写指标和审计终态
  -> finalize/cleanup 清除 Job、stream、activeStreamingTurn，设置 Idle
  -> 下一轮发送
~~~

### 3.2 当前实现

~~~text
用户输入
  -> 发送前 repairAssistantReplayHistory 修复工具事务并审计移除旧 NONE assistant
  -> AIMessageManager 构建 provider-neutral replay-safe PromptTurn
  -> Provider adapter 在结构化工具编译前再次过滤历史
  -> ProviderStreamSessionGate 分配本轮 generation 并绑定 Call/Response
  -> ChatRuntime 持有空的 live assistant，不写 Room
  -> 首个 durable 快照到达后才写 messages；NONE 始终不写
  -> 停止/失败等待当前 Job 收口并提交 empty/local-only/replayable 投影
  -> Provider finally 只能清理同 generation、同 owner 的句柄
  -> runtime 清理完成后开放下一轮
~~~

当前关键边界是：pending UI projection 可以立即存在，但 durable chat projection 不会在没有合法
正文、完整工具事务或明确 local-only reasoning 前写入；Provider wire 还会再执行一次独立过滤。

## 4. 修复前失效时序

~~~text
用户发送
  -> Provider 尚未返回首个 chunk
  -> submitAssistantResponse 已将空 assistant 写入 Room
  -> 用户点击停止
  -> cancelMessageInternal 记录 USER_STOP，取消 Call/Job 并 join
  -> detachStreamingAiMessage 无条件 completeInterruptedMessage
  -> 再次写入 content=""、contentStream=null 的 assistant
  -> runtime 清理并开放 Idle
  -> 用户发送下一条
  -> 发送前工具 repair 看到空字符串未 changed，未删除该行
  -> getMemoryFromMessages 返回空 PromptTurn(ASSISTANT)
  -> OpenAI compiler 生成 content=null 且无 tool_calls
  -> Provider HTTP 400: Invalid assistant message
  -> 删除思考只改正文，不改 sender/身份/eligibility
  -> 后续请求继续带非法 assistant；旧流的 CANCEL 还可能晚到
~~~

失败回合的另一个分支是：收到思考或正文片段后传输被 CANCEL/EOF 打断。若只把原始流关闭
当作完成，可能写入 reasoning-only、未闭合工具调用或错误状态不一致的消息；若完全删除消息，
又会丢失用户在 UI 和审计中应能看到的中断事实。

## 5. 单一回合与消息状态 owner

### 5.1 推荐边界

在现有 MessageProcessingDelegate 的 ChatRuntime 和 SendUserMessageTurnState 上建立一个内部
AssistantTurnLifecycleOwner（名称可调整，但不能增加第二个并行服务）。它是唯一负责：

- 分配并锁定 chatId + turnKey + generation + messageTimestamp + variantIndex；
- 保存 pending/live snapshot、已观察的 chunk/工具事务和 cancellation source；
- 接受或拒绝带 generation 的 provider、stream、Job、Room 回调；
- 调用 provider-neutral validator，选择 durable projection；
- 在一次 commit barrier 中提交消息投影、provider execution 状态和 audit 终态；
- 在 commit 完成后才释放发送 gate、清理 runtime 并发布 Idle/Error。

MessageProcessingDelegate 仍是现有消息处理入口和 UI 状态桥接；它不能再让周期 collector、
stop path、普通失败 path 和 finalizer 各自直接 addMessageToChat。

### 5.2 既有 owner 的职责不变

| Owner | 唯一职责 | 禁止越界 |
| --- | --- | --- |
| AssistantTurnLifecycleOwner | 回合生命周期、generation、取消、最终 commit 顺序 | 不直接编译 provider wire，不维护第二份历史 |
| ChatHistoryManager/对应 DAO | messages、message_variants 的 durable chat projection 事务 | 不自行推断 Provider 能否续接 |
| ProviderExecutionRepository | execution、event cursor、message provider state、tool ledger | 不把 provider-private JSON 当作下一轮普通聊天历史 |
| ConversationAuditRepository | append-only 审计、revision、tombstone、payload 和 seal | 不改变可发送历史，不伪造远端事实 |
| AIMessageManager | 从已验证 projection 编译 PromptTurn | 不从 live stream 或未经验证的空 assistant 读取 |
| Provider adapter | wire 编解码、transport、能力合同、usage 和 provider-specific resume | 不自行创建第二个 turn/message owner |
| UI/ViewModel | 展示状态、允许动作、无障碍语义 | 不用按钮点击直接清理 Room 或取消任意旧回合 |

### 5.3 身份要求

- turnKey 使用一次性稳定 UUID 作为持久审计关联；现有 turnId/operationId 可继续作为 chat
  内单调序号，但不能单独承担跨进程唯一身份。
- generation 是 chat runtime 的写入代数；任何回调必须携带并比较 turnKey + generation。
- messageTimestamp + variantIndex 是消息投影定位；provider-private execution 继续以
  localExecutionId 关联，不能只按 provider/model 名称关联。
- 一个 chat 同时最多一个 open turn；工具 follow-up 属于同一 turn 的 hop，不是新的用户 turn。

## 6. 完整回合状态机

### 6.1 状态

| 状态 | 语义 | 允许持久化的内容 |
| --- | --- | --- |
| IDLE | 没有 open turn，可以发送 | 既有已验证历史 |
| PREPARING | 已分配 turn 身份，准备用户消息、配置和上下文 | 不创建普通 assistant |
| SUBMITTING | 请求编译或提交中 | pending UI、audit；provider execution 按其状态 |
| STREAMING_NO_CONTENT | 连接存在但没有正文、思考或工具 | 不写普通 assistant |
| STREAMING_THINKING | 只有思考增量 | UI/live 和 audit；durable 只能是 local-only |
| STREAMING_VISIBLE | 收到可见正文片段 | 仅写 validator 认可的安全前缀 |
| STREAMING_TOOL_OPEN | 工具 XML/JSON 或结构化调用尚未闭合 | 原始事实进 audit/private state，不能进入 replay |
| WAITING_TOOL | 工具调用完整，等待执行/结果 | tool ledger 和 audit；不宣称 assistant 完成 |
| SUBMITTING_TOOL_OUTPUT | 正在提交工具结果 | 仍属于同一 turn，发送 gate 保持关闭 |
| FOLLOW_UP_STREAMING | 工具结果后的模型 follow-up 正在流式返回 | 复用同一 turn owner，禁止新 turn 写入 |
| COMPLETING | 已收到明确终态，正在验证和原子提交 | 只允许 finalizer 写 durable |
| CANCELLING | 已登记取消来源，收口正在进行 | 禁止新事件改变终态 |
| COMPLETED | 正文或完整工具事务通过验证并已提交 | REPLAYABLE |
| INTERRUPTED_EMPTY | 停止/取消时没有正文、思考或工具事实 | 不建普通 assistant row，audit 保留 |
| INTERRUPTED_LOCAL_ONLY | 有思考、工具或其他本地事实但没有 Provider 可接受历史 | 可建标记为 LOCAL_ONLY 的展示投影，永不 replay |
| INTERRUPTED_PARTIAL | 有可见安全前缀，远端/传输未完成 | 保存安全前缀并标记 PARTIAL，原始流进 audit |
| FAILED_EMPTY | Provider 明确失败且无可展示内容 | 不建普通 assistant row |
| FAILED_PARTIAL | Provider 明确失败但有安全正文前缀 | 保存安全前缀，标记失败而非完成 |
| DISCONNECTED | 已开始执行但传输中断，提交状态需按 adapter 判断 | 普通 Chat 不重发；官方 Responses 可按合同续接 |
| SUBMISSION_UNKNOWN | 请求可能已提交但本地不知道结果 | 明确显示未知，不创建第二个 POST |
| RECOVERY_REQUIRED | 进程重启后仍需恢复或用户决定 | 只显示事实和允许动作，不自动编造结果 |
| RECORDING_INTERRUPTED | 审计写入本身失败 | 不伪装为完整成功，阻止越过审计门槛 |

### 6.2 转移规则

1. PREPARING 之后所有事件带 turn 身份；缺少身份直接失败，不写当前 chat。
2. COMPLETING、CANCELLING 和任何 terminal state 是单向的；重复 terminal commit 返回同一结果。
3. CANCELLING 先冻结接收，再取消 transport/Job；晚到事件只能被记录为 stale callback，不能改写新回合。
4. COMPLETED 必须同时满足正文/工具合法性、审计终态和 provider-private 终态要求。
5. INTERRUPTED_LOCAL_ONLY 与 INTERRUPTED_PARTIAL 可以在 UI 显示中断事实，但 replay validator
   必须依据 eligibility 决定是否排除。
6. 任何终态 commit 失败进入 RECORDING_INTERRUPTED 或明确 Error，不回到静默 Idle。

## 7. 五类投影和数据边界

1. **Pending UI projection**：实时 stream、思考、工具片段和停止中的草稿；可有空内容、未闭合
   标记和半个 JSON，但不能作为下一轮输入或 Room 历史合法性证据。
2. **Durable chat projection**：messages/message_variants 面向普通聊天 UI 的投影；只由
   lifecycle owner 通过 ChatHistoryManager 原子写入。需要保留本地思考时使用显式 LOCAL_ONLY
   生命周期，不让它伪装成已完成 assistant。
3. **Provider replay history**：由 durable projection 经过 provider-neutral validator 后派生的
   canonical PromptTurn 列表；不读取 live stream，不直接读取 audit raw payload，也不把
   contentStream 序列化。
4. **Provider-private execution**：ProviderExecutionEntity、MessageProviderStateEntity、
   provider event cursor 和 ToolInvocationLedgerEntity；只承载 response ID、sequence、原生
   output item、工具 exactly-once 和恢复事实。
5. **Audit**：ConversationAuditRepository 的 append-only event/payload/revision/tombstone；
   保存用户看见的思考、原始片段、失败因果和修复前后 digest，但不自动成为下一次 prompt。

五者不能互相代写：live stream 不能直接决定 provider replay，provider-private JSON 不能绕过
消息投影，audit 不能补造缺失的 Provider 结果。

## 8. 终态和内容矩阵

| 情形 | UI/本地事实 | durable chat projection | replay eligibility | provider execution / 下一步 |
| --- | --- | --- | --- | --- |
| 首包前用户停止：无正文、无思考、无工具 | 显示已停止，没有收到回答 | 不生成普通 assistant row | 无 | execution 标记 CANCELLED；commit 后才允许新发送 |
| 只收到思考 | 显示思考并标记已中断 | 推荐保留 LOCAL_ONLY_REASONING row；若详情页可直接读 audit，也可只保留审计投影 | LOCAL_ONLY，永不发送 | 不创建可 replay assistant；下一轮从前一条合法历史继续 |
| 收到可见回答片段 | 显示已收到片段和中断状态 | 只保存 validator 输出的安全可见前缀 | CONTENT_ONLY/REPLAYABLE_PARTIAL | 普通 Chat 不重发原 POST；记录断流事实 |
| 思考加可见回答片段 | 思考和正文都保留，二者标记为同一中断回合 | 保存正文安全前缀；思考仅作为 local-only 展示或 audit | 仅正文满足 replay，思考由 adapter 决定是否丢弃 | 继续发送时不得冒充上次思考已完成 |
| 工具 XML/JSON 尚未闭合 | 展示原始工具片段或中断提示 | 只保存此前安全正文；未闭合后缀不进入普通 replay | EXCLUDED，不得补闭合或伪造结果 | execution 为 INCOMPLETE/DISCONNECTED；工具 raw 进 audit |
| 工具调用完整但结果未返回 | 展示等待/停止的工具身份 | 不把 call-only assistant 当可 replay 历史；可建 local-only 工具状态 | EXCLUDED，保持 pending ledger | 不自动再次执行工具；由显式恢复协议处理 |
| 收到部分工具结果 | 展示真实已收到结果及 pending 数 | 只提交完整闭合组和其前缀；未闭合尾部排除 | 完整组 REPLAYABLE，尾部 EXCLUDED | ledger 保持每个 call 的真实状态，不合成缺失结果 |
| 全部工具结果 | 显示真实执行顺序与最终正文 | 按 provider call identity 规范为完整工具事务 | REPLAYABLE | 可进入 follow-up；完成前不释放 turn gate |
| 工具 follow-up 正在运行 | 显示同一回合的 hop/工具状态 | 不提交最终完成 assistant；必要的正文快照仍受 validator 约束 | 仍由当前 turn 持有 | WAITING_TOOL/SUBMITTING_TOOL_OUTPUT/IN_PROGRESS，停止后一次收口 |
| Chat Completions 响应头前中断 | 无远端 response ID 的提交事实 | 仅保存安全本地片段或不建 row | 不允许 POST 重放 | 标记 SUBMISSION_UNKNOWN；UI 明确告知未知 |
| Chat Completions 响应体中断 | 可能已有部分正文但没有续接合同 | 保存安全正文前缀和断流状态 | 只 replay 安全前缀 | 标记 DISCONNECTED 或 SUBMISSION_UNKNOWN，不创建第二次 POST |
| 兼容 Responses 无可续接 response ID | 保留已知的本地请求/错误事实 | 同上，不能假设兼容 endpoint 支持 GET | 不自动续接 | SUBMISSION_UNKNOWN；除显式 adapter 合同外不 GET/POST |
| 官方 Responses 已知 response ID、sequence、capability | 显示可续接或用户停止事实 | 已确认正文按 projection 保存，未确认部分不冒充完成 | 可在同一 response 合同下续接 | 仅 GET 同一 response 并使用 starting_after=lastAppliedSequence；不新建 POST |

## 9. 取消来源、竞态和停止协议

### 9.1 取消来源

| 来源 | 终态语义 | UI | provider 动作 |
| --- | --- | --- | --- |
| 用户停止 | CANCELLED/INTERRUPTED_* | 显示已停止和已保留内容 | 只取消当前 turn；Responses 有 ID 时按合同调用 cancel |
| 生命周期取消 | ERROR/RECOVERY_REQUIRED | 可见错误，不静默 Idle | 记录 lifecycle 失效，禁止新回合继承旧流 |
| 配置刷新 | 当前 execution 依既有 lease 合同收口 | 显示配置变更影响（若有） | 不用刷新动作猜测或重发请求 |
| 进程死亡 | RECOVERY_REQUIRED | 重启后显示恢复/未知状态 | 由恢复协调器按 execution 状态处理 |
| 网络中断 | DISCONNECTED 或 SUBMISSION_UNKNOWN | 显示断线，不称为用户停止 | 不把 EOF/HTTP reset 转换成第二次 POST |
| Provider 明确失败 | FAILED_EMPTY/FAILED_PARTIAL | 安全错误消息和失败原因 | 写原始 failure code，不能以空流成功结束 |

### 9.2 原子停止协议

顺序必须固定，且所有步骤由同一 chat 的 cancellationMutex/turn commit lock 串行化：

1. 读取并确认当前 turnKey + generation，把状态从 open state CAS 到 CANCELLING。
2. 写入不可变的 cancellation source 和 TURN_CANCEL_REQUESTED audit event；从此刻拒绝新的
   user send 和普通 durable write。
3. 捕获 live content、thinking、工具 ledger、provider execution ID 和当前序号。
4. 取消旧 turn 的 sendJob、state collector、stream collector、Provider Call/Response；每个回调
   仍必须在执行前验证 turn identity。
5. 等待旧 Job 收口，关闭旧 stream；不能在等待期间把 chat runtime 标为可发送。
6. 在 NonCancellable commit 区调用 validator，决定 empty/local-only/partial/tool-safe projection。
7. 在同一顺序中提交 durable message/variant projection、provider execution terminal status 和
   audit terminal event。谁先成功取得 commit token，谁就是最终 commit owner；重复调用只读取已提交结果。
8. 标记 turnClosed=true，释放 provider/stream 引用和旧 callback gate，递增 generation。
9. 最后才把 isLoading 置 false、发布 Idle 或明确 Error，允许下一次发送。

### 9.3 竞态规则

- **停止与首个 chunk**：先取得 commit token 的一方决定结果；chunk 若已被接受就进入 snapshot，
  否则以 stale callback 丢弃。不能一半写入空 row、一半写入正文 row。
- **停止与最终 chunk**：已经 COMPLETING 且完成 commit 时，停止返回幂等成功；已经 CANCELLING
  时最终 chunk 不得把状态改回 Completed。
- **停止与工具结果**：按 provider call ID 在同一 ledger/turn lock 下决定结果是否已闭合；不以
  到达时间制造工具顺序，不补缺失结果。
- **停止与 Room 写入**：所有 upsert 带 turnKey/generation，由 DAO 事务检查当前消息版本；
  旧版本更新为 0 行时记录 stale write，不覆盖新 projection。
- **停止与消息删除/回滚**：beforeDestructiveHistoryMutation 先等待 turn commit，再让 deletion
  事务使 generation 失效、撤销 replay projection 并清理 provider-private state。
- **停止与新一轮发送**：发送入口只能在 turnClosed 和 durable/audit commit 成功后通过 gate；
  不能静默排队，也不能让新回合覆盖旧回合。

## 10. MessageEntity 与 provider-private state 取舍

### 10.1 方案 A：消息投影增加生命周期和 replay 字段

在 MessageEntity 与 MessageVariantEntity 增加最小可查询字段，例如：

- lifecycleState：PENDING、STREAMING、COMPLETED、INTERRUPTED、FAILED、INVALIDATED；
- replayEligibility：REQUIRES_VALIDATION、REPLAYABLE、CONTENT_ONLY、LOCAL_ONLY、EXCLUDED；
- turnKey：持久 UUID，用于回合和 audit/provider execution 关联；
- terminalReason：USER_STOP、NETWORK_DISCONNECT、PROVIDER_FAILURE、EMPTY_ASSISTANT 等。

优点：查询和 UI 展示直接，发送前可以用 SQL/纯 Kotlin 明确排除，编辑/删除能在同一消息事务
中使 projection 失效，旧数据迁移可被审计。代价是 Room 版本迁移、variant round-trip、DAO
查询和所有 ChatMessage 转换都要同步。

### 10.2 方案 B：只复用 MessageProviderStateEntity、ProviderExecutionEntity 和 audit

不改消息表，所有状态由 execution/audit join 推导，空 assistant 仍留在 messages，发送前依靠
validator 过滤。

优点是没有立即的 Room schema 变更。缺点是 UI 无法在不查私有账本时区分空、思考、中断和失败；
没有 provider execution 的首包前回合没有稳定 owner；编辑、variant 删除和 audit 恢复容易出现
旧 projection 继续参与 prompt；每个查询入口都可能重新实现过滤规则。

### 10.3 实际决策

实现阶段重新核对现有 owner 后，未采用新增 MessageEntity/MessageVariantEntity 字段，也没有升级
Room schema。原因是本次损坏闭环不需要持久化第二套 lifecycle：

1. 首包前空 assistant 现在不进入 Room，活动状态继续由现有 `ChatRuntime`、turnId、取消 registry
   和 loading gate 持有。
2. eligibility 是正文和完整工具事务的确定性派生值，由唯一纯 Kotlin projector 在 durable、历史重建
   和 Provider wire 三个边界复算，不会产生可漂移的持久标志。
3. reasoning-only 的本地展示事实仍由消息正文与现有 audit 表达；Provider execution/Responses
   response ID 继续由既有私有表持有，二者不互相代写。
4. 旧的无内容 base message 在发送前使用现有 chat mutex、审计事务和 DAO 删除；有 variant 的复杂
   消息不做粗暴迁移，而是在 Provider 编译边界过滤。
5. 消息/variant 删除已有 provider execution 清理与外键级联，编辑和 destructive history mutation
   已有 audit/revision owner；本轮没有证据要求复制另一组 invalidation 字段。

只有未来 UI 必须查询无法从正文、现有 audit/execution 或运行态确定的跨进程生命周期时，才应另立
schema 设计和 migration；这不属于本次根因修复的隐含要求。

## 11. Provider-neutral replay history validator

新增纯 Kotlin、无 Android/网络副作用的验证器（可先扩展 AssistantReplayHistoryProjection，
但必须保持一个唯一入口），输入为带生命周期/eligibility 的 message/variant sequence，输出：

- canonical PromptTurn 列表；
- 每条被排除、截断、重排或标记 local-only 的原因；
- open tool call、closed transaction、provider call ID 和内容 digest；
- REPLAYABLE、PARTIAL、LOCAL_ONLY、UNREPLAYABLE 的整体结论；
- 可供 audit 的诊断，而不是隐式字符串。

硬规则：

1. assistant 只有在可见正文非空，或拥有完整且有 provider identity 的工具调用事务时才有资格
   进入 replay；空 assistant、只含思考的 assistant 和只有空白/状态标签的 assistant 默认排除。
2. reasoning_content、think 等 provider-private/展示内容必须与可见正文分离；adapter 不
   支持 reasoning replay 时只保留本地事实。
3. 工具调用和结果按 provider_call_id 优先、协议工具名其次匹配；名称冲突、重复、孤立结果、
   未闭合 XML/JSON、缺少结果和跨文本边界均是明确 violation。
4. 完整并行结果可以按调用顺序规范化；live 到达顺序和 audit 原始顺序不能被改写。
5. 永远不生成缺失的 tool result、空 assistant 正文、假的 response ID 或假的完成事件。
6. repair 模式只做有证据的截断/规范化，并追加 audit revision；validate 模式失败就阻止
   provider submit，不能自动换路。
7. 编译器在生成 wire message 前再执行一次结构断言：不能出现 assistant content=null 且无
   tool_calls，不能出现未闭合 tool transaction。

## 12. Provider adapter 协议矩阵

| Adapter | assistant/reasoning/tool 规则 | 中断与恢复 | 本专项要求 |
| --- | --- | --- | --- |
| OpenAI Chat Completions | assistant 必须有 content 或 tool_calls；工具 call/result 必须配对 | 没有已证明的未知提交安全重放合同 | header/body 中断区分 SUBMISSION_UNKNOWN/DISCONNECTED；绝不自动第二次 POST |
| OpenAI 官方 Responses | output item、response ID、sequence、工具输出由 execution state 保存 | 已知 response ID、sequence 和 capability 时 GET 同一 response，使用 starting_after | 只续接同一 response；用户停止按 cancel 合同；没有能力证明就不续接 |
| OpenAI 兼容 Responses | 可能仿造 Responses 字段，但没有统一 resume 合同 | 默认视为不可续接 | 除显式 endpoint capability 外，按 at-most-once 和 SUBMISSION_UNKNOWN 处理 |
| DeepSeek Chat | Chat Completions 兼容字段，另有 reasoning_content | 没有通用跨请求 resume 合同 | reasoning-only 不作为普通 assistant；工具名/call ID 严格匹配，正文合法性先于提交 |
| Claude | native content blocks、thinking、tool_use、tool_result | 当前无统一可续接合同 | 只 replay 完整 block/tool 事务；取消和 active state 必须按 turn 验证 |
| Gemini | parts/thought、functionCall/functionResponse；响应 accumulator 必须按 attempt 隔离 | 当前无统一跨进程 resume 合同 | 不从 provider-private state 自动拼下一轮；函数调用结果完整后才可 replay |
| 其他继承 OpenAI/兼容层的 Provider | 只能继承明确声明的 wire 规则，不能按模型名猜能力 | 默认没有 resume | 先注册 capability/profile 和 contract test，未注册路径拒绝不明行为 |

适配器矩阵只描述设计边界；每个 Provider 的真实协议仍需官方文档、源码和 MockWebServer/真机
证据。不得因为字段名称相似就把兼容 Responses 当作官方 Responses。

## 13. 已污染聊天的正式、可审计修复

本次不执行全库 schema migration，而是复用每次发送和自动 compaction 已有的严格历史边界：

1. `ChatHistoryManager.repairAssistantReplayHistory` 严格读取当前 chat 的 runtime messages；读取失败
   直接阻止发送，不把空历史当成功。
2. 空白、status-only、metadata-only 或没有安全前缀的损坏 assistant，只有在 sender 仍为 `ai`、
   base variant 被选中且数据库没有实际 variant row 时才可移除。锁内还会复核正文与 eligibility，
   避免和并发编辑/variant 写入竞争。
3. 删除前通过 `ConversationAuditRepository.mutateAndAppendEvent` 写
   `ASSISTANT_REPLAY_HISTORY_EXCLUDED`、`TOMBSTONE / EXCLUDED / PARTIAL` 和具体 failure code；原始
   内容进入审计 payload。thinking-only 不属于删除候选，继续作为本地中断事实保留。
4. 有安全正文但工具尾部未闭合时，继续走既有 `ASSISTANT_REPLAY_HISTORY_REPAIRED` revision；完整
   并行工具结果只在身份可证明时规范为调用顺序，跨消息合法闭合不改写。
5. `MessageDao.deleteMessageByTimestamp` 先删除对应 provider execution，再删除 message；其关联
   state/event/tool ledger 由现有外键级联清理。修复不伪造工具结果、response ID 或完成状态。
6. 有实际 variant 的消息不做数据破坏；无论历史修复是否改行，AIMessageManager 和每个 Provider
   adapter 都会再次应用 provider-neutral validator，保证非法 assistant 不到达 wire。

## 14. 编辑、删除、variant 和回滚后的失效规则

- 编辑仍通过现有 immutable revision 和当前投影事务更新正文；下一次历史重建不信任旧判断，而是
  从最新正文重新计算 eligibility。编辑成空白或 reasoning-only 不会生成 Provider assistant turn。
- 删除单条、批量删除、`deleteMessagesFrom`、清空聊天、删除聊天、回滚和删除 variant 继续先经过
  `beforeDestructiveHistoryMutation`，等待活动 turn 收口，再由现有审计事务与 DAO 清理相关
  execution/state/ledger。
- variant 选择改变下一次 prompt 时，Provider-neutral validator读取当前 selected projection；删除
  variant 的原始正文和 tombstone 仍由审计保留。
- Provider session gate 只解决同一 Provider 实例的 transport generation 所有权；它不绕过上述
  chat mutation barrier，也不允许旧回调以新消息 timestamp 重新插入 row。

## 15. 进程重启规则

启动时唯一恢复协调器查询 ProviderExecutionRepository.getRecoverableExecutions()，同时扫描
消息上的 PENDING/STREAMING/REQUIRES_VALIDATION：

| 重启前状态 | 启动后动作 |
| --- | --- |
| STREAMING/PENDING 且没有 provider execution | 标为 RECOVERY_REQUIRED；保留已知本地事实，不宣称完成，不创建第二次 POST |
| SUBMITTING/SUBMISSION_UNKNOWN | 显示提交结果未知；等待用户或明确诊断动作，不重发 |
| DISCONNECTED 且官方 Responses 有 response ID、sequence、capability | 可以按恢复策略 GET 同一 response；使用原 localExecutionId 和 cursor |
| DISCONNECTED 但兼容 Responses/Chat 无续接合同 | 保持未知/断线；不自动 POST |
| WAITING_TOOL/SUBMITTING_TOOL_OUTPUT | 读取 tool ledger；RUNNING 工具不自动再次执行，等待显式恢复协议 |
| COMPLETED | 仅在最终 output 和 replay validator 都通过时投影为完成；否则标为 recovery error |
| CANCELLING/CANCELLED | 幂等完成本地中断 projection 和审计，不重新启动 transport |
| RECORDING_INTERRUPTED | 先修复审计链/记录失败，不开放看似正常的下一轮 |

恢复协调器不直接更新普通消息正文；它向 lifecycle owner 提交带 turnKey/generation 的恢复
命令，再由既有 owner 完成 commit。

## 16. UI、错误反馈和无障碍

影响 ChatViewModel、AIChatScreen、ChatScreenContent、ConversationDetailsScreen 和
ChatServiceCore 的交互设计：

- 停止按钮进入正在收口状态，直到 commit barrier 完成；期间发送按钮不可执行第二个 turn。
- 首包前停止显示已停止，没有收到回答，不制造空 assistant 气泡。
- 思考-only 显示原思考并标记已中断，仅本地保留；正文 partial 显示回答传输中断，已保留已收到
  内容，不能显示已完成。
- 工具未闭合显示工具名称、调用状态和未完成，不显示伪造结果；完整工具结果和 follow-up 使用
  同一回合编号。
- SUBMISSION_UNKNOWN、DISCONNECTED、Provider 明确失败、用户停止和生命周期取消使用不同
  安全文案和允许动作；不把异常堆栈、API key、Cookie 或原始 Authorization 展示给用户。
- 错误事件进入现有 UiStateDelegate.errorMessage/ErrorDialog 链；不能只写日志，也不能让
  SharedStream 的晚到错误升级成重复全局 fatal。
- Compose 语义需提供 stateDescription、停止/收口的进度和 live-region 提示；大字体、TalkBack、
  横屏和窄屏下状态文案不能遮挡输入框或操作按钮。
- 继续发送只有在旧 turn 明确 closed 且 replay validator 通过后可用；官方 Responses 的续接是
  同一 execution 的恢复动作，不是新消息重发。

## 17. 审计事件和脱敏

建议事件类型（最终名称需复用现有审计枚举/字符串合同）：

- TURN_CREATED、ASSISTANT_PENDING_CREATED、TURN_CANCEL_REQUESTED、TURN_COMMIT_STARTED、
  TURN_TERMINAL_COMMITTED；
- ASSISTANT_LOCAL_ONLY_PROJECTED、ASSISTANT_REPLAY_EXCLUDED、ASSISTANT_REPLAY_REPAIRED、
  HISTORY_VALIDATION_FAILED；
- PROVIDER_ATTEMPT_STARTED、PROVIDER_ATTEMPT_CANCELLED、PROVIDER_ATTEMPT_FAILED、
  PROVIDER_EXECUTION_INVALIDATED；
- STALE_CALLBACK_IGNORED、RECOVERY_REQUIRED、REPLAY_MIGRATION_DETECTED、
  REPLAY_MIGRATION_COMPLETED、RECORDING_INTERRUPTED。

每个事件至少带 chatId、turnKey、generation、messageTimestamp、variantIndex、localExecutionId
（若有）、provider call ID（若有）、occurredAt/recordedAt、parentEventId、terminalState、
failureCode、visibility、completeness 和 payload digest。原因顺序必须能证明：

turn opened -> provider submitted -> chunk/tool observed -> cancel/failure -> commit -> terminal

payload 只保存脱敏正文、思考、工具和错误事实；禁止 API key、Authorization、Cookie、密码、私钥、
令牌和请求签名。用户停止与 Provider 失败必须使用不同 actor/failure code；审计记录失败本身时
使用 RECORDING_INTERRUPTED，不能把缺失事件补写成成功。

## 18. 精确影响文件清单

### 首要实现文件

- app/src/main/java/com/ai/assistance/operit/services/core/MessageProcessingDelegate.kt
- app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt
- app/src/main/java/com/ai/assistance/operit/services/core/ChatHistoryDelegate.kt
- app/src/main/java/com/ai/assistance/operit/services/ChatServiceCore.kt
- app/src/main/java/com/ai/assistance/operit/core/chat/AIMessageManager.kt
- app/src/main/java/com/ai/assistance/operit/core/chat/AssistantTurnCancellation.kt
- app/src/main/java/com/ai/assistance/operit/core/chat/AssistantReplayHistoryProjection.kt
- 新增的纯 Kotlin lifecycle owner/validator 文件（确有复杂度时才新增）

### 历史、Room 和 provider-private

- app/src/main/java/com/ai/assistance/operit/data/model/ChatMessage.kt
- app/src/main/java/com/ai/assistance/operit/data/model/MessageEntity.kt
- app/src/main/java/com/ai/assistance/operit/data/model/MessageVariantEntity.kt
- app/src/main/java/com/ai/assistance/operit/data/model/MessageProviderStateEntity.kt
- app/src/main/java/com/ai/assistance/operit/data/model/ProviderExecutionEntity.kt
- app/src/main/java/com/ai/assistance/operit/data/repository/ChatHistoryManager.kt
- app/src/main/java/com/ai/assistance/operit/data/repository/ProviderExecutionRepository.kt
- app/src/main/java/com/ai/assistance/operit/data/dao/MessageDao.kt
- app/src/main/java/com/ai/assistance/operit/data/dao/MessageVariantDao.kt
- app/src/main/java/com/ai/assistance/operit/data/db/AppDatabase.kt

### Provider 和执行链

- app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt
- app/src/main/java/com/ai/assistance/operit/api/chat/enhance/ConversationService.kt
- app/src/main/java/com/ai/assistance/operit/api/chat/enhance/ConversationRoundManager.kt
- app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/OpenAIProvider.kt
- app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/OpenAIResponsesProvider.kt
- app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/DeepseekProvider.kt
- app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/ClaudeProvider.kt
- app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/GeminiProvider.kt
- 其他继承 OpenAI/兼容层的 Provider 及其 capability/profile

### UI、审计和测试

- app/src/main/java/com/ai/assistance/operit/ui/features/chat/viewmodel/ChatViewModel.kt
- app/src/main/java/com/ai/assistance/operit/ui/features/chat/screens/AIChatScreen.kt
- app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/ChatScreenContent.kt
- app/src/main/java/com/ai/assistance/operit/ui/features/chat/details/ConversationDetailsScreen.kt
- app/src/main/java/com/ai/assistance/operit/data/audit/ConversationAuditRepository.kt
- 对应 app/src/test JVM、Room、MockWebServer、协程竞态和 provider contract 测试
- 若修改持久语义，同步更新职责对应的 CONTEXT.md/正式 docs；不把过程日志写进 README

并发任务当前 dirty 的文件不属于上面实现清单，后续必须先隔离和审阅，不能顺手修复。

## 19. 分阶段实施顺序、回滚点和门槛

| 阶段 | 当前状态 | 实际结果与回滚边界 |
| --- | --- | --- |
| 0. 证据冻结 | DONE | 根因链、Room/audit owner、Provider attempt 合同与 fixture 已复核；无真实 endpoint 请求。 |
| 1. replay/durable 纯合同 | DONE | eligibility、工具安全投影和请求编译测试通过；新增 projector 逻辑可独立回滚。 |
| 2. Room 字段与 migration | NOT REQUIRED | 复核后选择确定性派生 eligibility 和发送前审计修复，没有 schema/数据迁移副作用。 |
| 3. 停止/失败收口 | DONE | pending live 与 durable 写入分离，停止/失败复用 replay-safe 投影；保留现有 ChatRuntime owner。 |
| 4. Provider adapter | DONE | OpenAI-compatible、Claude、Gemini 接入历史过滤与 session gate；未知提交单 POST 有自动证据。 |
| 5. 旧污染历史 | DONE | 无 variant 的 NONE assistant 先审计 tombstone 后删除；工具修订和删除级联复用既有事务。 |
| 6. 进程/UI/无障碍 | DEVICE VERIFICATION PENDING | 本轮没有改变 UI；现有 stopping/error/loading owner 继续使用，真实进程与可访问性场景待设备验收。 |
| 7. 验证与交付 | LOCAL VALIDATION DONE | 95 项专项 JVM、正式门禁与 Debug APK 审计通过；候选 revision 的链接、新鲜克隆和最终 ref 证据由任务日记与交付报告记录。 |

任何剩余门槛失败都停在对应阶段，不能以切换 Provider、重复请求、静默删除或吞错继续。

## 20. 验证矩阵

### 自动化

- **已通过**：`AssistantReplayHistoryProjectionTest` 39、`MessageProcessingDelegateTest` 8、
  `ProviderStreamSessionGateTest` 7、DeepSeek/Claude/Gemini canonical request 22、OpenAI/native
  attempt boundary 4、Responses submission fault injection 11、transport diagnostics 4；合计
  `95/95`，零失败、零错误、零跳过。
- **已覆盖**：空/status/reasoning-only assistant、正文与工具安全前缀、并行工具结果、stale
  completion/clear、cancel/late bind、OpenAI 响应体中断与 503 单 POST、Claude/Gemini 请求提交后
  断连单 POST、官方 Responses 提交未知与同 response 续接既有合同。
- **不适用**：本轮没有 Room schema 变化，因此不运行 24 到新版本 migration；删除 execution 和
  外键级联沿用已有 DAO 合同，并已做源码反向审查。
- **正式门禁**：项目 `.venv` 运行 `check_formal_readiness.py --repository . --require-main` 返回
  `PASS`；`git diff --check` 通过。`check_markdown_links.py` 与新鲜克隆检查依赖候选 revision，结果
  作为交付时点证据记录在任务日记与最终报告中，避免设计文档自引用提交状态。
- **Debug 构建**：`.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 4m22s`，`235` 个任务中 `23 executed / 212 up-to-date`；唯一 launcher、脚本
  代理 runtime 和播放器 runtime packaging 任务通过。
- **APK 审计**：`app/build/outputs/apk/debug/app-debug.apk` 写入时间为
  `2026-09-03 14:20:20 +08:00`，大小 `496156261` bytes，SHA-256
  `1A3BE445364E9FD3E89AED5DD7301AC131C85AE7EEAC03D65EB5AC4FF8624490`。manifest 为
  `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`，唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`；Android Debug V2 单 signer 与 16 KiB zipalign 通过。
  APK 有 `5512` 个无重复 entry、`44` 个 DEX、仅 `arm64-v8a` 的 `53` 个 `.so` 且 basename 无重复；
  加 `assets/operit_shell_exec` 共 `54/54` 个 ELF64/AArch64，`161` 个 `PT_LOAD` 为
  `0x4000 × 159 + 0x10000 × 2`。49 项生产 package 与白名单名称集合完全一致。

### 真机/用户验收

- Android 目标设备上首包前停止、思考-only 停止、正文 partial、工具中断、Provider 明确失败；
  停止后立即发送新消息和快速连续点击。
- 退后台、旋转、进程强杀、重新打开同 chat；验证 UI/audit 保留事实、发送 gate 和恢复文案。
- OpenAI Chat、DeepSeek、Claude、Gemini 和兼容 endpoint 分别验证；真实网络断开、HTTP/2 reset、
  response header/body EOF、官方 Responses 续接。
- TalkBack、大字体、窄屏、横屏、错误弹窗、停止反馈、继续发送和回到详情页。

本轮不会操作设备或真实收费 Provider。自动测试、构建、Git/远端证据与真机/真实 Provider 证据始终
分开报告。

## 21. 不变量和禁止方案

### 必须长期成立的不变量

1. 每个 chat 至多一个 open turn；一个 turn 的 hop 不产生新的用户 turn。
2. durable assistant 只能是本地 reasoning 事实或含非空可见正文/完整工具事务的 REPLAYABLE 投影；
   只有后者可以进入 Provider history。
3. reasoning-only、空 assistant、未闭合工具和未知提交事实不会进入普通 provider replay。
4. 任何 wire assistant 都满足 content 或合法 tool_calls 至少一个存在。
5. 不生成假的 tool result、response ID、sequence、usage、完成事件或用户回答。
6. 旧 turn 的 Job、Call、stream、callback 和 Compose collector 不能写入新 generation。
7. 旧 turn 未完成 commit 前，发送 gate 不开放；terminal commit 重复执行幂等。
8. 用户停止、生命周期取消、网络中断和 Provider 失败在 UI、provider execution 和 audit 中可区分。
9. 普通 Chat Completions 和无证明的兼容 Responses 不重发提交未知的 POST。
10. 官方 Responses 续接只使用同一 response ID、已知 sequence 和 capability 合同。
11. 编辑、删除、variant 删除/切换和回滚会重算 replay projection，并使不再适用的 private execution
    失效。
12. 审计链保留原始事实和修复因果；任何审计写入失败都不能伪装成完整成功。

### 明确禁止

- 为掩盖失败而改换 provider、模型、endpoint 或网络路径。
- 对未知提交结果重复 POST；把 transport EOF/HTTP 502/CANCEL 当作可安全重试。
- 给空 assistant 补一个空格、[Empty]、User cancelled 或其他伪造正文。
- 为缺失工具结果写假的成功/失败结果，或把未闭合 XML/JSON 强行闭合后宣称完整。
- 让 audit、provider-private state、live stream 或 UI 各自拥有一份历史真相。
- 依赖 completedAt、消息 sender 或删除思考文字推断 replay 合法性。
- 捕获并吞掉异常、把异常关闭的 stream 当成功 Idle、把 Provider 失败升级为无原因 fatal。
- 在没有官方合同的兼容 Responses 上猜测 GET 续接，或引入第二个 Browser/Provider runtime。

## 22. 当前结论、下一步和状态标记

### 已确认

- 空 assistant 的首包前持久化、停止后再次保存、无条件 replay 和 OpenAI/DeepSeek 非法 wire 是
  现场 400 的完整源码根因链；这些入口均已修复并有自动测试保护。
- OpenAI、Claude、Gemini 的实例级取消字段存在跨 generation 所有权缺陷；现在统一由 session gate
  隔离 Call、Response、取消状态与 finally 清理。
- 现有 ChatRuntime、取消 registry、Provider execution、工具 ledger 和 audit owner 足以完成本次
  闭环，不需要新增 Room 字段、第二生命周期服务或兼容旁路。

### 合理推断

- session gate 修复了源码中可证明的旧 transport 句柄竞争，但现场 `stream was reset: CANCEL` 仍可能
  包含真实网络/HTTP2 断流；没有设备和真实 Provider 证据时不能把所有 CANCEL 归为同一原因。

### 待验证

- 真实 Provider response header/body/sequence、兼容 Responses resume 能力、跨进程行为、设备 UI、
  快速停止后重发和无障碍。

### 最短下一步

1. 在目标设备分别使用真实 OpenAI-compatible、DeepSeek、Claude、Gemini 场景复测首包前停止、
   reasoning-only、正文 partial、网络断流和立即重发；完成前保持 `verification_pending`。

本地实现与自动验证完成不等于设备、真实 Provider 或用户验收完成。

## 2026-09-05 DeepSeek Chat HTTP/2 CANCEL 诊断与连接隔离

状态：`LOCAL IMPLEMENTATION, AUTOMATED VALIDATION AND DEBUG APK VERIFIED / REAL PROVIDER AND DEVICE VERIFICATION PENDING`。

附件 `20260905-151206-954-测试Code Runner与超级管理员功能-621753ac-ai-diagnostics.md` 的末尾事件
597-602 与最终 throwable 形成了完整的传输证据链：第 32 次 DeepSeek Chat Completions 请求的
第 1 次 attempt 已发送完整 `102630` 字节请求体，transport metadata 为
`protocol=h2`、`connectionReused=true`、`requestBodyStarted=true`、
`responseHeadersReceived=false`、`transportStage=WAITING_FOR_RESPONSE_HEADERS`，随后
OkHttp 抛出 `StreamResetException: stream was reset: CANCEL`。没有
`CancellationException` 或 `PROVIDER_ATTEMPT_CANCELLED`，因此这不是用户点击停止；事件 600
正确把它记为提交状态未知并抑制自动重试，事件 601 保留已收到的部分回答。

前约 31 个工具 hop 已成功，失败只出现在长时间连续请求后的复用 HTTP/2 连接上。服务端或中间
代理在响应头前发送 `RST_STREAM(CANCEL)` 是当前可由日志确认的传输根因；请求体已经离开进程，
重新 POST 可能重复提交或重复工具 hop，不能用透明重试掩盖未知提交状态。

本轮实现将 DeepSeek 的 `OPENAI_CHAT_COMPLETIONS` 路由标记为 `DEEPSEEK_CHAT`，并使用独立的
HTTP 客户端策略：只协商 `HTTP/1.1`、`maxIdleConnections=0`、关闭 OkHttp
`retryOnConnectionFailure`。这与已有 OpenAI Responses 隔离策略一致，切断长链路 HTTP/2 连接复用，
同时保持 provider 层 at-most-once 边界，不切换 Provider/endpoint、不增加直连路径或第二代理核心、
不吞异常、不伪造成功。

已修改 `AIServiceFactory.kt` 及其路由/传输回归测试；本地定向测试、Debug APK、正式开发门禁和
新鲜克隆检查均已完成。真实 DeepSeek endpoint、HTTP/2 reset 复现、长工具
链路和 vivo Android 设备复测尚未执行，完成前保持 `verification_pending`。
