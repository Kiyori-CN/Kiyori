# 6. DeepSeek、长上下文缓存、协议适配与对话统计方案

## 状态与适用范围

状态：`M1-M5 LOCAL VERIFIED / M6 LOCAL VALIDATION COMPLETE / ENDPOINT AND DEVICE VERIFICATION PENDING`。

本专项承接 `1_resumable_responses_execution.md`、`2_model_capability_and_request_compiler.md`、
`3_cache_tools_and_metrics.md` 和 `5_post_regression_development_plan.md`，不创建第二套
Responses、工具执行、Token 统计或上下文压缩状态 owner。

本方案依据当前仓库 `main@111ac08bdaf3962a112ef90440031cd4fbbb06b8`、本地
`D:\01_Environment\Apps\DeepSeekHarness` 中的 `@deepseek-ai/dsh` `0.1.1-rc.2`
安装包，以及 DeepSeek Harness 官方仓库固定提交
`b150a551b8d465e31e418e1b2eaf5e79bbb7d28e` 制定。官方源码只读副本位于本机临时缓存，
不进入 Kiyori 工作树。

`CONTEXT.md` 当前明确记录 Kiyori 从未进行用户面发行。因此本轮涉及用户可见统计菜单、
未稳定的本地 Token 语义和未发布的协议能力画像时，允许直接收敛旧可见方案；已有消息、
变体、备份、审计、ToolPkg 和协议标识仍按兼容读取与明确迁移处理。若后续产品状态改变为
已发布，必须在实现前停止并改为向前兼容迁移。

## 研究结论

### DeepSeek Harness 可复用的结构性设计

官方 Harness 不是把“缓存”实现成一个本地字符串长度计数器，而是把以下边界分开：

1. `llm-deepseek` 只负责 DeepSeek Chat Completions wire 编解码、SSE、reasoning、
   tool call 和 provider usage。
2. `dsh-llm` 用 provider-neutral 的 block stream 表达文本、reasoning、工具调用、工具结果、
   usage 和 finish；`usage` 在 `finish` 之前发出，`finish` 后不再有任何内容。
3. `ReplayEnvelope` 保存提供方重放所需的 opaque metadata，并且与被保留的 block 位置同步
   裁剪，避免工具调用被丢弃后仍遗留不可执行的回放状态。
4. Token meter 以完整的 request envelope、surface、step 边界和 provider usage 为事实源；
   provider usage 与启发式估算分别保存，不能用估算替代提供方计量。
5. provider usage 的输入桶是互不重叠的：
   `uncached input + cache read + cache write + output`。推理 Token 是 output 的细分，
   不能再次加到总量。
6. 上下文压缩先按真实请求压力检查；可选的无模型工具结果剪枝只替换当前 surface 中的
   工具结果节点；需要摘要时把原 system、tools 和被压缩历史按原样放在请求前缀，最后才追加
   固定压缩指令；摘要替换旧历史区域，而不是把摘要追加到旧历史之后。
7. session stats 是 append-only event 的 projection，UI 读取 projection，不重新扫描消息并
   自己猜测请求次数、工具次数或 Token。

### DeepSeek 官方协议边界

根据 DeepSeek 官方 API 资料与固定 Harness adapter 的实现：

| 入口 | 当前有效合同 | Kiyori 行为 |
| --- | --- | --- |
| DeepSeek Chat Completions | 无状态；完整历史；thinking 使用 `thinking.type` 与 `reasoning_content`；工具结果按原始 `tool_call_id` 关联；usage 中读取 `prompt_cache_hit_tokens` 或 `prompt_tokens_details.cached_tokens` | 由 DeepSeek 专用 adapter 编译；真实 provider usage 进入统一 usage 入口 |
| DeepSeek Responses | 提供 Responses wire 形状，但不声明 OpenAI 的 `previous_response_id`、`conversation`、`store`、`background`、`prompt_cache_key` 或 sequence resume 合同 | 只能完整重放 Kiyori 自己保存的历史；不发送 OpenAI 专属能力字段，不按 `response_id` 续接 |
| DeepSeek Anthropic Messages | Anthropic Messages wire；缓存、thinking、tool schema 以 Anthropic 返回的 usage 和协议字段为准 | 走 `ClaudeProvider`；不把 DeepSeek Chat 的字段名硬编码到 Anthropic 分支 |
| OpenAI 官方 Responses | 只有精确官方 endpoint/profile 才能声明 response continuation、background、encrypted reasoning replay、prompt cache key 和 Tool Search | 保留现有 capability profile；每项能力单独声明、单独测试 |
| OpenAI-compatible Responses | 只能使用已经证明该端点支持的基础 wire 能力 | 只做 at-most-once 提交和完整历史重放，不继承官方 OpenAI 高级能力 |
| 其他 Chat/Anthropic/Gemini | 以各自 provider usage 与协议文档为准 | 没有 provider usage 时明确标为不可用，不把本地估算显示成服务端缓存命中 |

DeepSeek 官方磁盘 KV cache 是 best-effort。Kiyori 可以通过稳定请求前缀显著提高命中概率，
但不能宣称或伪造“接近百分百”；最终命中率必须由 provider usage 中明确的 cache-read
字段计算。

## 当前实现中已确认的风险

1. `TokenCacheManager` 的公共前缀比较是本地启发式计量，不是 DeepSeek 或其他 provider 的
   服务端 KV cache 证明；当前 `cachedInputTokenCount` 可能同时代表本地估算和 provider
   返回值，UI 无法知道来源。
2. `AIService.sendMessage()` 的 `onTokensUpdated(input, cachedInput, output)` 只携带三项
   整数，丢失 cache-write、reasoning、provider usage 是否存在、请求/hop 身份和数据来源。
3. `OpenAIProvider.applyUsageToCounters()` 目前向回调传入 total input，而本地
   `TokenCacheManager` 同时维护 cached/current 两个桶；没有独立的“本次 provider usage
   已提交且只计一次”状态，流式 usage、终态 usage 和工具 hop 需要重新核对。
4. `DeepseekProvider.buildMessagesWithReasoning()` 与共享
   `StructuredToolCallBridge` 在历史不完整时会生成 `User cancelled` 工具结果，并且会按
   本地序号重写工具调用 ID。该行为不能作为协议修复：缺失或冲突的工具结果必须在副作用
   前显式报告协议错误。此项已在 M2 收口。
5. DeepSeek Chat 的工具/推理历史、Responses 的 `function_call` /
   `function_call_output` 历史和 Anthropic 的 content block 历史仍由不同 provider
   分支拼装；需要统一 canonical history contract，但不能把三种 wire 格式强行合并。
6. `TokenStatisticsDelegate` 目前只暴露累计 input/output、窗口估算和最近一次
   `(input, output)`；Chat 顶栏无法准确展示 provider cache hit rate，也无法表达“没有
   provider cache usage”与“provider 明确返回 0 命中”的区别。
7. `ApiPreferences.updateTokensForProviderModel()` 以旧三元组累计，兼容性字段可继续保留，
   但新的 provider usage projection 必须防止同一 hop 的流式 usage 和终态 usage 重复累计。
8. 自动摘要路径需要确保 system、tools、保留历史和工具配对边界与对话请求一致；摘要必须
   替换被压缩区域，不能追加旧历史的第二份副本，否则会同时损害上下文质量与 cache prefix。

## 目标

### 功能目标

- DeepSeek Chat、DeepSeek Responses、DeepSeek Anthropic 三条明确协议路线都能正确表达
  thinking/reasoning、工具调用、工具结果、usage 和长上下文历史。
- 长上下文连续对话保持稳定的 provider-visible prefix：system/developer、工具 schema、
  已完成历史、reasoning replay 和工具结果不因本地无关状态变化而重排。
- 自动上下文压缩在达到压力阈值时有可恢复、可审计、保持工具配对的替换事务；摘要请求
  复用原对话前缀，摘要结果进入 durable history。
- Responses 历史重放保留 `response.output` 语义、`function_call` 与对应 output 的相邻关系、
  reasoning metadata 和稳定 call identity；不对 DeepSeek 发送未经其协议声明的 OpenAI 字段。
- provider usage、cache read、cache write、reasoning 和工具 hop 具有明确来源、请求身份和
  去重规则。
- AI 对话顶栏统计按钮显示当前对话范围内准确可解释的 provider usage；没有 provider cache
  usage 时显示“未提供”，绝不显示伪造的 `0%`。

### 非目标

- 不把 DeepSeek Responses 伪装成 OpenAI 官方有状态 Responses。
- 不向 DeepSeek 发送 `previous_response_id`、`conversation`、`store`、`background`、
  `prompt_cache_key`、`prompt_cache_retention` 或 sequence resume 字段。
- 不新增协议失败后的静默切换、不伪造工具结果、不吞协议错误、不用估算值替代 provider
  usage。
- 不复制 `ProviderExecutionRepository`、`EnhancedAIService`、`TokenStatisticsDelegate`、
  `ApiPreferences`、工具执行器或消息持久化 owner。
- 不改变 application ID、既有 ToolPkg/MCP 协议标识、已有消息/变体公共字段语义或
  用户已保存配置的读取能力。
- 不调用付费 endpoint 作为自动化验证前提，不安装或操作设备，不执行 Release 构建。

## 冻结的数据与状态合同

### 1. Provider usage snapshot

新增 provider-neutral 的一次 provider hop usage 结构，名称和实际包位置以实现阶段的现有
数据层命名约定为准，至少包含：

```text
ProviderUsageSnapshot
  providerModel
  protocol
  requestId / providerRequestContext
  hopOrdinal
  totalInputTokens
  uncachedInputTokens
  cacheReadTokens
  cacheWriteTokens
  outputTokens
  reasoningTokens
  cacheMetricState
  source
  reportedAt
```

约束：

- `totalInputTokens = uncachedInputTokens + cacheReadTokens + cacheWriteTokens`。Anthropic
  cache creation 等写入是本次 prompt 的真实 miss/write，必须进入 prompt 总量与命中率分母；
  reasoning 仍是 output 的细分，不重复加入总量。
- `cacheMetricState` 至少区分 `REPORTED`、`NOT_REPORTED` 和 `INVALID`。
  provider 明确返回 `cached_tokens: 0` 属于 `REPORTED`，不是未知。
- `source` 至少区分 `PROVIDER`、`LOCAL_ESTIMATE`、`UNAVAILABLE`。顶栏缓存命中率只消费
  `source=PROVIDER` 且 `cacheMetricState=REPORTED` 的样本。
- 同一个 `(chatId, turnId, providerHopId)` 的流式 usage 与终态 usage采用同一 usage identity
  last-write-wins 规则；不能把相同 hop 相加两次。
- 既有 `inputTokens` 作为历史兼容字段继续表示 billable total input；既有
  `cachedInputTokens` 继续表示 cache read；新字段不改写旧字段的语义。
- 供应商没有 cache-write 字段时写入“未报告”，不是写入“0 次写入”作为事实。
- provider usage 缺失时仍可保留本地估算用于上下文窗口提示，但该估算不能进入 provider
  cache hit rate。

### 2. Cache hit rate

当前对话统计范围固定为 active chat 的全部已完成 provider hop；摘要请求、标题请求和
功能模型独立请求不进入主聊天顶栏，除非它们已经由现有聊天 owner 明确归属于同一 turn。

对 provider 已报告 cache read 的样本：

```text
providerPromptTokens =
  uncachedInputTokens + cacheReadTokens + cacheWriteTokens
cacheHitRate =
  cacheReadTokens / providerPromptTokens
```

显示状态：

| 状态 | 顶栏内容 |
| --- | --- |
| 没有任何 provider cache usage | `缓存命中率：未提供` |
| 有 provider cache usage，且分母大于 0 | `缓存命中率：xx.x%` |
| provider 明确报告总输入为 0 | `缓存命中率：0.0%`，并保留“已报告”状态 |
| 有请求但部分请求没有 usage | 显示比率，同时显示 `provider usage 覆盖 a/b` |
| 只有本地估算 | `缓存命中率：未提供`，可单独显示 `本地估算：...` |

不允许把 output、reasoning、上下文压缩前 Token、本地字符串长度或工具执行字符数放入
缓存命中率分母。

### 3. Request canonicalization

所有需要 provider-visible 稳定前缀的 provider 共享一个只读 canonicalization 结果，但
每个 wire adapter 仍独立序列化：

1. system/developer 前缀顺序固定。
2. 工具按稳定的工具名排序；同名工具拒绝进入请求，不以输入顺序覆盖。
3. JSON object 的键递归按 Unicode code-point 顺序排序；JSON array 保持原顺序，因为数组
   顺序可能具有协议语义。
4. 工具 schema、description、parameters、strict/tool-search 编译结果作为 request
   envelope 的一部分；trace ID、日志时间、UI 展开状态、当前输入框状态和本地请求计时
   不进入 canonical prefix。
5. 当前用户输入只能位于当前请求尾部；用于 OpenAI 官方 `prompt_cache_key` 的命名空间
   可以引用稳定历史 anchor，但不能把正在变化的当前输入写入 key。
6. DeepSeek 不产生 OpenAI `prompt_cache_key`；DeepSeek 命中情况只以其返回的 usage 为准。
7. reasoning effort、服务等级、重试编号、HTTP request trace 和 provider request ID 不
   进入 prompt cache identity。
8. 压缩前缀和普通对话前缀使用同一 system/tools/history 编译结果，只在最后追加固定摘要
   指令；不要在压缩请求前插入第二个 system prompt。

### 4. History and tool identity

- DeepSeek Chat：assistant 文本、`reasoning_content`、`tool_calls` 和 `tool` 结果按照
  provider 可接受的相邻顺序写回；工具结果必须使用原始 `tool_call_id`。
- DeepSeek thinking 工具轮次必须保留完整 `reasoning_content`；不能为压缩或跨 hop 删除
  这段协议必要的 reasoning。
- 已有 provider 原生 call ID 优先保留；本地生成 ID 只能在协议明确允许且有稳定、可重建
  输入时使用。跨进程、重试和下一 hop 不得按当前数组位置重新命名。
- assistant 工具调用没有匹配 tool result、tool result 没有匹配 assistant call、同一 ID
  的名称/参数/结果冲突时，在请求提交或工具副作用前以可观察协议错误结束。
- 禁止生成 `User cancelled`、`[Empty]` 或其他文本来填补缺失工具结果。真实用户取消应由
  取消状态 owner 表达，而不是伪造 provider history。
- OpenAI Responses 输入编译保持 `message`、`reasoning`、`function_call`、
  `function_call_output` 的协议顺序；同一 call ID 最多输出一次 call 和一次相同 output。
- Anthropic Messages 继续以 content block 表达 thinking/text/tool_use/tool_result，
  不把 Chat/Responses 的 XML 作为第二份事实源。

### 5. Context compaction

自动压缩和手动压缩共用同一事务：

1. 在下一个 provider request 生成前，使用当前 provider usage 锚点加 signed surface delta
   计算压力；没有 provider usage 时使用明确标注为 heuristic 的估算。
2. 先在工具结果 surface node 上执行独立、可审计的 model-free pruning；原始完整工具结果
   仍保留在审计/事件事实源，模型可见 surface 只出现一个替换结果。
3. 选择范围必须位于完整 surface node 边界，不能拆开 assistant tool call 与 tool result。
4. 摘要输入复用上一次请求的 system、工具 schema、被替换区域的原始历史和必要 reasoning
   metadata；固定 compaction instruction 是最后一个 user message。
5. 摘要结果只保留安全的文本 checkpoint；reasoning 和工具调用不能进入摘要文本，避免
   泄漏私有 reasoning 或制造孤立工具调用。
6. 成功摘要以 surface replacement 写入 durable history，旧区域不再再次出现在下一请求。
7. 结果提交前检查 surface generation、工具配对和请求 route 未发生变化；变化时显式结束
   本次压缩，不提交过期摘要。
8. 压缩事件、摘要请求 usage、被替换范围、旧/新 token 估算和 provider route 进入现有
   对话审计链；顶栏只汇总主聊天范围，不把摘要请求误计为用户请求。

## 里程碑与影响文件

### M0 研究与合同冻结

状态：`DONE IN THIS ROUND`。

- [x] 阅读项目 `AGENTS.md`、`CONTEXT.md`、正式开发准备清单、TODO 总索引和现有
  provider/Responses/cache/tool/统计专项。
- [x] 固定 DeepSeek Harness 官方提交并完成 `llm-deepseek`、`dsh-llm`、token-meter、
  compaction-basic、tool-result-pruner、session-stats 源码对照。
- [x] 明确 DeepSeek Responses 与 OpenAI 官方 Responses 的能力边界。
- [x] 明确 UI 未发行判断、兼容读取边界、无设备/真实 endpoint 的证据边界。

停止条件：研究结论不能由当前源码、官方固定提交或官方协议资料支撑时，不进入实现。

### M1 Provider usage v2 与一次性累计

目标：先修正数据语义，再改请求或 UI。

主要文件：

- `api/chat/llmprovider/AIService.kt`
- `api/chat/llmprovider/OpenAIProvider.kt`
- `api/chat/llmprovider/OpenAIResponsesPayloadAdapter.kt` 所在文件
- `api/chat/llmprovider/ClaudeProvider.kt`
- `api/chat/llmprovider/GeminiProvider.kt`
- `api/chat/EnhancedAIService.kt`
- `services/core/TokenStatisticsDelegate.kt`
- `data/preferences/ApiPreferences.kt`
- `data/model/ChatMessage.kt`
- `data/model/MessageEntity.kt`
- `data/model/MessageVariantEntity.kt`
- `data/model/OperitChatArchive.kt`
- `data/db/AppDatabase.kt` 与对应 DAO/迁移测试

实施要点：

- 增加强类型 provider usage callback/snapshot，保留旧三元组接口直到全部调用者迁移完成。
- OpenAI Chat/Responses、DeepSeek、Anthropic、Gemini 各自把 provider usage 映射到 disjoint
  buckets；明确记录是否报告 cache read/write。
- 对流式 usage、终态 usage、工具 follow-up、重试和重新生成建立 hop identity 去重。
- 本地 TokenCacheManager 只作为 heuristic window estimate；不要再把它当 provider cache
  evidence。
- 在消息/变体持久化中增加必要的 usage source、cache write、reasoning 和 coverage 字段；
  版本化迁移保持旧数据默认值为“未报告”，而不是“已命中 0”。
- `ApiPreferences` 继续读取旧 provider:model 三元组，并新增 v2 聚合读取；旧统计不被
  清零或重新解释。

测试：

- usage 字段映射、0 值与缺失字段；
- DeepSeek `prompt_cache_hit_tokens`、`prompt_tokens_details.cached_tokens`；
- Claude cache creation/read；
- 流式 usage + terminal usage 去重；
- 多 tool hop 累计；
- 重试不重复计费；
- Room migration、backup round-trip、旧 JSON round-trip。

当前实现证据：

- `ProviderUsageAggregate` 已移入 `data.model` 作为持久化与 UI 共用的数据合同；输入桶、
  cache-read、cache-write、output、reasoning 和三类覆盖计数保持互斥语义。
- `ProviderUsageAccumulator` 按 provider hop ID last-write-wins；OpenAI Chat/Responses、
  DeepSeek、Anthropic、Gemini、ToolPkg 的 wire usage 已进入统一 provider snapshot。
- Room 已从 22 迁移到 23；聊天、消息、变体、Operit archive 和 ChatContentDao 投影均保存
  provider usage 字段；旧数据的覆盖计数为 0，表示未报告。
- `TokenStatisticsDelegate` 已按 active chat 恢复 provider aggregate；`ApiPreferences` 已
  保存按 provider:model 的 v2 聚合，同时保留旧三元组统计。
- waifu 模式由最后一个实际持久化分段唯一保存 provider aggregate；其他分段保留旧
  input/output/timing 但 provider usage 为零，避免整轮丢失或按消息重复累计。
- 8 个定向 JVM suite 共 `35` tests 全部通过；AndroidTest Kotlin 编译通过。Room 22→23
  instrumentation 未操作设备，继续保持 `verification_pending`。
- 阶段 `:app:assembleDebug` 通过；APK 为 `472736006` bytes，SHA-256
  `DBFE3F454BD36A1C588CBE0F9E8DD53F50F75360E6FCD253A1D3A2AC389FB564`。唯一 Launcher、
  Android Debug V2 单 signer、16 KiB zipalign、arm64-only、51 个 `.so`、44 个 DEX、
  shell/ripgrep 资产、播放器运行时和 DEX continuation 门禁均通过。

回滚点：只提交数据合同和迁移，若 provider route 测试失败，不进入请求编译阶段。

### M2 DeepSeek Chat canonical request 与工具/推理历史

目标：让 DeepSeek 长上下文每一 hop 看到结构稳定、协议保真的请求。

主要文件：

- `api/chat/llmprovider/DeepseekProvider.kt`
- `api/chat/llmprovider/OpenAIProvider.kt`
- `api/chat/llmprovider/StructuredToolCallBridge.kt`
- `api/chat/llmprovider/ProviderToolCallIdentityContract.kt`
- `util/TokenCacheManager.kt`
- `util/ChatUtils.kt`
- DeepSeek/OpenAI provider adapter tests

实施要点：

- 提取稳定 JSON canonicalizer，工具 schema 及嵌套 object 键按稳定顺序序列化。
- 以 provider-ready history 计算 heuristic 只读 estimate；不把估算 cached bucket 送进
  provider usage projection。
- DeepSeek 请求固定 `stream_options.include_usage=true`；usage 延迟到 `[DONE]` 前，
  `finish` 后不继续发事件。
- 保留 `reasoning_content`；关闭 thinking 时只清理协议明确不可重放的 reasoning 元数据，
  不改动工具 call/result 身份。
- 删除缺失工具结果的文本伪造路径，改为明确的协议错误；不在 provider history 中插入
  `User cancelled`。
- 使用可重建的 provider call identity；同一 provider/call ID 的 name/arguments 冲突在
  请求提交前失败。
- 工具定义稳定排序与 input history 稳定排序分开测试，避免把数组排序误用于有序内容。

测试：

- 首轮和长历史的 byte-stable request prefix；
- system/tools/history 变化位置；
- reasoning-only、reasoning + tool-call、tool result；
- 并行工具调用原顺序；
- 缺失/重复/冲突工具结果；
- `[DONE]` 前 usage、finish 顺序；
- DeepSeek usage cache read 与真实 0 命中；
- 估算值存在但 provider usage 缺失时 UI/数据源仍为 unknown。

当前实现证据：

- 新增 `ProviderToolHistoryProtocol.kt`；`ProviderToolHistoryState` 以 pending call 队列验证
  缺失、多余、孤立、重复和空 payload，并在 history boundary 直接抛出
  `ProviderToolHistoryProtocolException`。
- `OpenAIProvider`、`DeepseekProvider` 和 `StructuredToolCallBridge` 均移除
  `flushOpenToolCallsAsCancelled`；typed 工具节点不再被静默转换为普通文本，原始 XML
  `provider_call_id` 优先用于 wire `tool_call_id`。
- DeepSeek 保留 `reasoning_content`，流式请求增加 `stream_options.include_usage=true`；
  非流式请求不携带该字段。启用模型参数按 `apiName` 排序，重名/保留字段/非法 OBJECT
  参数显式失败。
- 工具定义按名称排序、schema 参数按名称排序并拒绝重名；工具参数和 package proxy
  arguments 使用 canonical JSON；最终 DeepSeek request envelope 也使用 canonical JSON。
- provider 原始 ID、同 ID 同参去重、同 ID 冲突和 legacy 无 ID 的内容稳定 ID 均有 JVM
  覆盖；并行调用结果按原始调用顺序发出。
- 5 个定向 JVM suite 共 `34` tests 全部通过，包含 `DeepseekCanonicalRequestTest` 7、
  `StructuredToolCallBridgeTest` 11、`ProviderToolHistoryProtocolTest` 5、
  `ProviderToolCallIdentityContractTest` 3 和既有 `ProtocolServiceRoutingPolicyTest` 8。

M2 阶段验证：

- `:app:compileDebugAndroidTestKotlin --no-daemon --console=plain` 通过；最近一次复核为
  `BUILD SUCCESSFUL in 45s`，`146` 个任务中 `1` 个 executed、`145` 个 up-to-date。
- `:app:assembleDebug --no-daemon --console=plain` 通过；M2 阶段 APK 为 `472736006` bytes，
  SHA-256 `5F9372567E0F01CC2BC8D2CE7D77710720D676ABD1097DEA45DD862CDDF733EF`，
  身份为 `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`。
- 独立 APK 审计确认唯一 Launcher、Android Debug V2 单 signer、16 KiB zipalign、
  `5504` 个 ZIP entry 无重复、`44` 个 DEX、仅 `arm64-v8a`、`51` 个 `.so` basename
  无重复；加 `assets/operit_shell_exec` 共 `52/52` 个 ELF64/AArch64，`153` 个
  `PT_LOAD` 为 `0x4000 × 151 + 0x10000 × 2`，没有低于 16 KiB 的 segment。
- DEX continuation 审计为主方法 `23772 / 100000`，三个 continuation 分别为
  `35 / 64`、`33 / 128`、`30 / 128` registers，`violations=[]`。
- Room 22→23 instrumentation、真实 DeepSeek usage/cache 命中率、网络断开/进程重建和设备
  UI 仍为 `verification_pending`。

回滚点：DeepSeek adapter 与 StructuredToolCallBridge 的独立提交；不改 Responses
execution repository。

### M3 Responses 与 Anthropic 协议适配收敛

目标：在共享协议边界修复历史重放和能力声明，不把 provider identity 与 protocol owner
重新合并。

主要文件：

- `api/chat/llmprovider/ModelCapabilityProfile.kt`
- `api/chat/llmprovider/AIServiceFactory.kt`
- `api/chat/llmprovider/OpenAIResponsesProvider.kt`
- `OpenAIResponsesStreamState.kt`
- `OpenAIResponsesTerminalSnapshot.kt`
- `OpenAIResponsesReasoningProjection.kt`
- `ClaudeProvider.kt`
- `ApiProviderConfigCollect.kt`
- 对应 protocol/capability/Responses 测试

实施要点：

- 新增或细化 DeepSeek Responses 的 replay-only capability：
  `executionPersistence=NONE`、`promptCache=NONE`、`reasoningReplay=NONE`，除非当前
  endpoint 有独立证据声明某项能力。
- `OpenAIResponsesRequestFeatureCompiler` 只对精确官方 capability 注入 `background`、
  `store`、`include=reasoning.encrypted_content` 和 `prompt_cache_key`。
- DeepSeek Responses 仍然通过完整 `input` 历史重放；禁止凭 response ID 自动续接。
- `convertMessagesToResponsesInput()` 继续保证 function call/output 相邻、原始 call ID
  稳定、冲突显式失败，不创建缺失 output。
- 终态 `response.output` 与流事件以原始 output index 和 call ID 对齐；usage 终态只向
  M1 usage owner 提交一次。
- Anthropic thinking/tool content block 与 usage 独立测试；不把 OpenAI `reasoning_content`
  字段泄漏到 Anthropic body。

测试：

- DeepSeek Responses request body 无 OpenAI 专属字段；
- OpenAI 官方与兼容 endpoint capability matrix；
- response ID、sequence、resume、terminal snapshot、reasoning replay；
- function_call/function_call_output 排序和冲突；
- Anthropic Messages tool_use/tool_result/thinking；
- protocol factory route matrix。

停止条件：任何 generic endpoint 通过模型名或路径误获得官方高级能力，立即停止并修正
capability resolver。

当前实现证据：

- DeepSeek Responses 使用独立 replay-only capability profile；不会注入 OpenAI 官方
  `previous_response_id`、background、store、encrypted reasoning replay 或
  `prompt_cache_key`。
- OpenAI Responses typed history 严格保留 `reasoning`、`message`、`function_call` 和
  `function_call_output` 顺序；缺失、多余和冲突 output 在提交前失败。
- Anthropic 原生 content block replay 保存完整 thinking/signature、redacted_thinking、
  text 与 tool_use；tool_result 继续按原始 tool_use ID 闭合。流式 block index、delta、
  signature 和 message_stop 均由严格状态机验证。
- Gemini 新增完整 Part replay metadata；同一模型重放原始 Part 顺序与官方 camelCase
  `thoughtSignature`，模型切换时移除 thought Part 与 opaque signature，但保留仍需闭合的
  functionCall。多工具结果按数量、顺序和名称严格匹配，不再生成 `User cancelled`、
  `[Empty]` 或截断多余结果。
- Gemini 响应中的 thinking 与原始 Part accumulator 已从 provider 实例字段移到每个 HTTP
  attempt 的局部状态；provider 实例即使被多个 lease 复用，也不会跨请求共享 replay metadata。
  retry rollback 会直接丢弃失败 attempt 的 Part，只有成功 attempt 能在整个 provider hop
  结束时生成一次 metadata。
- Gemini 工具声明、参数 schema 和模型参数使用稳定排序；重名、非法 OBJECT 参数、无效 endpoint
  与损坏的 SSE/JSON/空响应均在网络提交前或响应边界显式失败。
- ToolPkg provider usage adapter 区分 input、cache read、cache write、output 与 reasoning；
  provider 明确返回 0 cache read 属于 reported，负值和畸形类型属于 invalid。
- `KimiCanonicalRequestTest`、`ModelRequestCompilerTest`、
  `OpenAIResponsesPayloadAdapterTest`、`AnthropicContentBlockReplayTest`、
  `AnthropicUsagePayloadAdapterTest`、`ClaudeCanonicalRequestTest`、
  `GeminiContentPartReplayTest`、`GeminiCanonicalRequestTest`、
  `GeminiToolHistoryProtocolTest`、`GeminiUsagePayloadAdapterTest` 和
  `ToolPkgUsagePayloadAdapterTest` 共 `54` tests，失败、错误、跳过均为 `0`。

M3 阶段验证：

- `git diff --check` 通过；provider 业务链中没有 `User cancelled`、`[Empty]` 或工具结果
  `minOf` 截断，新请求中的 snake_case `thought_signature` 为 0，旧拼写只保留兼容读取。
- `:app:compileDebugAndroidTestKotlin --no-daemon --console=plain` 通过；
  `BUILD SUCCESSFUL in 2m48s`，`146` 个任务中 `5` 个 executed、`141` 个 up-to-date。
- `:app:assembleDebug --no-daemon --console=plain` 通过；
  `BUILD SUCCESSFUL in 3m51s`，`232` 个任务中 `23` 个 executed、`209` 个 up-to-date。
- M3 阶段 APK 为 `472736006` bytes，SHA-256
  `D733CAB690B0E65B0419A64B9AA2F8E97C1FF7E62D1BB28ACAA917784E23A6B8`；身份为
  `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`。
- 独立 APK 审计确认唯一 Launcher、Android Debug V2 单 signer、16 KiB zipalign、
  `5504` 个 ZIP entry 无重复、`44` 个 DEX、仅 `arm64-v8a`、`51` 个 `.so` basename
  无重复、12 个生成式 `.toolpkg`，并包含 shell/ripgrep、FFmpeg、mpv 与 ToolPkg WASM
  runtime；`libsudo.so` 不存在。
- 加 `assets/operit_shell_exec` 共 `52/52` 个 ELF64/AArch64，`153` 个 `PT_LOAD` 为
  `0x4000 × 151 + 0x10000 × 2`，无低于 16 KiB 的 segment。DEX continuation 门禁为主方法
  `23772 / 100000`，三个 continuation 分别为 `35 / 64`、`33 / 128`、`30 / 128`
  registers。

### M4 长上下文自动压缩与 cache-prefix preservation

目标：让长时间连续对话在上下文压力下继续可恢复，并最大化未变化前缀。

主要文件：

- `services/core/MessageProcessingDelegate.kt`
- `api/chat/EnhancedAIService.kt`
- `api/chat/enhance/ConversationService.kt`
- 现有总结/上下文压缩 owner
- `ChatHistoryManager.kt`
- `ConversationAudit*`
- 相关 summary/tool/context tests

实施要点：

- 明确请求 boundary：一次用户回合可包含多个 provider hop；每个 hop 有独立 usage，
  turn projection 只在统一 owner 中折叠。
- 压缩前保留原请求的 system prompt、tool schema、历史 reasoning metadata 和工具配对；
  固定摘要指令置于最后。
- 用 replacement generation 保护异步摘要提交；摘要输入变化时显式失败，不提交过期内容。
- 对超大工具结果执行可审计的 head/middle/tail replacement；完整结果留在审计事实源，
  surface 不出现原文第二份副本。
- 摘要输出只保留文本 checkpoint，明确排除 reasoning/tool call/image。
- 压缩后重新测量下一请求的 heuristic pressure；provider pressure 等待下一次真实 usage
  更新，不能被估算强行覆盖。
- 不把摘要请求算作用户顶栏的请求次数；可在详细审计中显示 `compaction request`。

测试：

- 多轮工具调用后自动压缩；
- 工具 call/result 边界；
- reasoning 保留；
- summary replacement 不重复旧历史；
- 失败摘要、取消、surface generation 变化；
- 压缩请求的 prefix 与普通请求 byte-level 对照；
- 进程重建后恢复 checkpoint 和 tool identity。

当前实现证据：

- 新增 `ConversationCompactionContract`，自动压缩与手动插入总结共用同一强类型 snapshot、
  route identity、commit decision 和 rejection 合同；提交进入现有 chat mutex 与审计事务。
- 选区验证直接读取持久化消息中的真实 XML 工具形状，严格检查 call/result 数量、顺序、名称和
  `provider_call_id`；完整历史 digest、左右锚点、相邻 summary、当前有效 route 和范围内容任一
  变化都会拒绝写入过期摘要。
- summary 请求统一移除 provider-private replay metadata；空文本、工具标记或残留私有 metadata
  显式失败。旧 package warmup 不再注入 summary，工具 schema 继续由唯一真实 owner 提供。
- `ConversationToolResultPruner` 只修改 provider-visible 投影：保留原工具标签、name、status、
  call ID 与闭合结构，对超大 payload 使用 head/middle/tail 和 SHA-256 标记；数据库原文不改。
- `ConversationCompactionContractTest` 13 项与 `ConversationToolResultPrunerTest` 3 项通过；
  共享 `ProviderToolHistoryProtocolTest` 8 项通过。完整 JVM 回归包含上述合同且无失败。

### M5 统计 projection 与 AI 对话顶栏

目标：顶栏统计内容准确、稳定、可解释。

主要文件：

- `services/core/TokenStatisticsDelegate.kt`
- `services/ChatServiceCore.kt`
- `ui/features/chat/viewmodel/ChatViewModel.kt`
- `ui/features/chat/components/ChatScreenHeader.kt`
- `app/src/main/res/values*/strings.xml`
- `ApiPreferences.kt` 的读取 projection

UI 统计范围固定为当前 active chat，菜单至少显示：

```text
上下文：当前请求估算 / 模型窗口
请求：provider hop 数
累计入：billable input tokens
累计出：output tokens
缓存读取：cache read tokens
缓存命中率：provider cache read / provider prompt tokens
数据覆盖：provider usage hops / total provider hops
总计：累计入 + 累计出
```

规则：

- `缓存命中率` 没有可用 provider cache metric 时显示“未提供”，不显示 `0%`。
- provider 明确报告 0 命中时显示 `0.0%`，并保留“已报告”状态。
- 当前对话混用多个 provider/model 时，仍按 provider usage sample 聚合，同时显示覆盖数；
  不把本地估算和 provider usage 隐式相加。
- 上下文圆环继续使用当前窗口估算/模型容量；圆环不是计费或命中率指标。
- 统计 projection 由 `TokenStatisticsDelegate` 唯一持有，Header 只消费 Flow，不扫描消息。
- 所有文案通过资源；保持七份语言资源 key 集合、占位符和 XML 合法性一致。

测试：

- empty/unknown/reported-zero/partial-coverage cache cases；
- 当前 chat 切换与 Flow 隔离；
- reset、新 chat、重新生成、工具 hop、摘要请求；
- Header source contract、resource consistency、Compose compile。

当前实现证据：

- `ProviderUsageAggregate` 新增 `providerCacheMetricPromptTokens`；只有
  `cacheMetricState=REPORTED` 的 provider hop 才增加该分母，未报告和 invalid hop 不污染
  cache hit rate。明确报告 0 输入仍保留已报告状态并显示 `0.0%`。
- Room 升级到 24：22→23 为 chats/messages/message_variants 增加 provider usage projection；
  23→24 增加 cache-metric prompt 分母，并把无法恢复分母的旧
  `providerCacheMetricRequestCount` 重置为 0，避免把历史记录误解为已报告 0 命中。
- 消息、变体、聊天、DAO、Parcelable、Operit archive、审计映射、`ApiPreferences` 和
  `ChatHistoryManager` 均使用同一 aggregate；Waifu 回合只由最后一个实际持久化分段保存整轮
  provider usage。`ChatMessage.toProviderUsageAggregate()` 与写入映射完整 round-trip。
- 顶栏统计继续由 `TokenStatisticsDelegate` 唯一持有；`ChatScreenHeader` 只消费 active-chat
  Flow，显示上下文、provider hop、输入/输出、缓存读写、命中率、usage/cache 覆盖、推理和总计。
- 七份语言资源新增 12 个 `chat_stats_*` key，XML 可解析且占位符一致。M5 usage/持久化/
  Waifu/统计格式化定向套件共 17 项通过；Room instrumentation 未操作设备。

### M6 全链路验证、APK、审计与交付

每个有仓库修改的里程碑完成后串行执行：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

并核验 `app\build\outputs\apk\debug\app-debug.apk` 的存在、mtime、大小、SHA-256、包名、
version、唯一 Launcher、Debug 签名、16 KB alignment、ABI/native/runtime packaging。

最终验证按影响面执行：

1. 相关 JVM/Kotlin 定向测试；
2. 完整 `:app:testDebugUnitTest`；
3. AndroidTest Kotlin compile（不自动安装设备）；
4. `git diff --check`；
5. architecture/Markdown/XML/localization/resource 门禁；
6. `python -B ci/script/check_formal_readiness.py --repository . --require-main`；
7. `python -B ci/script/check_fresh_clone.py --repository .`；
8. provider usage、request body、Responses persistence、Room migration、UI source contract
   的反向静态审阅；
9. 精确 staged allowlist、敏感内容、构建产物、nested `.git`、子模块、reparse point、
   异常大文件审计；
10. 提交并推送 `main` 后核对 local HEAD、tracking branch、远端 `refs/heads/main` 和
    `git merge-base` 分歧。

真实 DeepSeek/OpenAI/Anthropic endpoint、网络切换、退后台、进程终止恢复、设备 UI、IME、
大字体、浅深主题和真实缓存命中率均保持独立 `verification_pending`，本地自动化与 APK
构建不得冒充这些证据。

当前本地验证证据：

- M4/M5 的 8 个核心 JVM suite 共 `41` tests，失败、错误和跳过均为 0。
- 完整 `:app:testDebugUnitTest` 为 `284` suites / `1658` tests，失败、错误和跳过均为 0。
- `:app:compileDebugAndroidTestKotlin` 为 `BUILD SUCCESSFUL in 26s`；两个 Room migration
  AndroidTest 只完成编译，未安装到设备执行。
- formal readiness、architecture `phase=m03`、七语言资源差分与 `git diff --check` 通过；
  ARCH009/ARCH010 已同步 Room 24 字面量和四个受保护 schema/entity owner 的 LF 规范化哈希。
- `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 2m2s`，232 个任务中
  26 个 executed、206 个 up-to-date；唯一 Launcher 与 player runtime packaging 通过。
- APK 为 `app/build/outputs/apk/debug/app-debug.apk`，宿主写入时间
  `2026-08-22 13:07:36 +08:00`，大小 `472737774` bytes，SHA-256
  `2858892B6B60D4DA1B5A154984196751540ACF18478AEB49F7CFAC131412DC9B`。
- APK 身份为 `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`；唯一
  `MainActivity` Launcher、Android Debug V2 单 signer 与 16 KiB zipalign 通过。
- ZIP 共 5504 个 entry 且无重复，44 个 DEX，仅 `arm64-v8a`，51 个 `.so` basename 无重复，
  12 个生成式 ToolPkg；包含 shell/ripgrep，不含 `libsudo.so`。
- 加 `assets/operit_shell_exec` 共 52/52 个 ELF64/AArch64；153 个 `PT_LOAD` 为
  `0x4000 × 151 + 0x10000 × 2`，无低于 16 KiB。DEX continuation 为
  `35 / 33 / 30` registers，`violations=[]`。
- 当前未创建提交、未推送、未运行 fresh-clone candidate、未安装 APK 或操作设备；真实 endpoint、
  provider cache、Room instrumentation 和设备 UI 保持 `verification_pending`。

### M7 全终态工具历史闭合与中断恢复

状态：`IMPLEMENTATION VERIFIED / DEVICE VERIFICATION PENDING`。

#### 扩展 Goal 与成功条件

用户现场确认手动停止和未手动停止都可能在下一次发送时出现
`MISSING_TOOL_RESULT at user_boundary`。本里程碑必须保证：任何普通发送、工具执行、手动停止、
非手动取消、Provider/工具失败或进程终止留下的 durable history，在进入下一条用户消息或自动
压缩前都不包含未闭合工具事务；正常完成仍须通过严格协议校验。实现不能伪造 tool result、
静默放宽校验、重试工具副作用、切换 Provider/协议或建立第二份历史 owner。

完成条件：

1. 周期快照、全部终态和历史恢复共用一个 replay-safe 投影；已按调用顺序闭合的事务逐字保留；
   完整但按并行完成顺序写入的结果只在 durable replay 副本中无损规范为调用顺序；只有从首个
   未闭合、无法唯一匹配或身份冲突事务开始的后缀被排除出模型可重放 surface
2. UI 仍显示原始实时流，conversation audit 仍保留原始 Provider/工具事件；durable message
   只承担下一请求和 compaction 可安全消费的投影
3. 普通完成和单条消息重新生成若仍有 open tool call，当前回合立即进入可见协议失败，不得先写
   `COMPLETED` 或保存新 variant
4. 旧版本损坏消息先检查工具事务是否被后续持久消息合法闭合；直到 replay boundary 仍未闭合的
   历史必须修复，不能再用 `completedAt != 0` 或缺少同 timestamp 失败审计阻止修复
5. 自动压缩在历史修复之后取 snapshot，并继续用既有 digest、route、工具闭合和相邻 summary
   门禁验证提交；修复不得绕过 `ConversationCompactionContract`
6. 自动化回归、正式开发门禁、Debug APK 和产物核验通过；最终提交推送 `main` 并完成三方 ref
   对账。真实 Provider、进程强杀和设备 UI 复测继续单列 `verification_pending`

#### 首轮实现后的现场纠正

首轮候选把 pending calls 实现为 FIFO queue，并在每个 result 到达时直接比较队首名称。真实完成
回合随后出现 `TOOL_RESULT_NAME_MISMATCH, pending=4, truncationIndex=4246`，且异常发生在
`completeAssistantResponse()` 最终持久化之前，表现为回复已经结束但整条助手消息丢失。

源码证明这不是 Provider 返回了损坏结果：`ToolExecutionManager.executeInvocations()` 对允许并行
的调用创建多个 `async`；`executeAndEmitTool()` 在各工具完成时立即向共享 collector 发射可见
结果，因此 live message 的结果顺序是完成顺序。只有供同一回合后续 Provider 请求使用的
`orderedAggregated` 才按原始 invocation 顺序重排。首版投影把两个不同顺序错误地当作同一顺序，
所以第一个完成的非队首工具会让 pending 数保持为整组调用数并误报名称冲突。

修正后的状态机在同一事务中保留调用顺序表和未匹配结果表：结果有 `provider_call_id` 时以稳定
身份匹配；没有 ID 时按协议工具名匹配第一个尚未闭合的同名调用。结果 XML 的 `name` 保留代理
目标等 UI 展示名，`provider_tool_name` 固定原始 invocation 名；执行层用一元素缓冲把同一工具
Flow 的 start/chunk 等结果标为 `provider_result_terminal=false`，正常结束后只有终态结果闭合
调用。全部调用均取得唯一终态结果后，把结果块放回原调用顺序对应的 result 槽位；所有非工具
字节、标签内容和结果 payload 保持不变。即时拒绝与实际执行结果也按 `normalizedInvocations`
顺序合并，结果数量不足或存在无 owner 结果时显式失败。若结果不足、结果无法匹配、ID 冲突、
孤立结果、截断标签，仍从事务起点截断，绝不伪造结果。完成门禁返回规范化正文，消息 owner
必须持久化这个返回值；live `contentStream` 和审计不重排。

#### 第二次现场纠正：完成标记不等于历史闭合

首轮乱序修正后的现场再次出现
`MISSING_TOOL_RESULT, pending=1 at history_end`。调用链表明异常发生在下一轮请求的
`OpenAIProvider.buildMessagesAndCountTokens()`：历史末尾已经排入一条 `TOOL_CALL`，但发送前恢复
没有修改它，因此严格状态机在 `history_end` 发现一个 open call。

根因不是 Provider 编译器遗漏结果，而是恢复策略把 `completedAt != 0` 或同 message timestamp 的
失败/取消审计当成修复资格。旧损坏消息可能已经被普通收尾写入非零完成时间，也可能没有对应失败
审计；这两项都不能证明工具事务已闭合。当前发送失败产生的新回合审计也不能反向成为旧消息的
同 timestamp 证据。只要该事务直到 replay boundary 仍未被后续持久历史合法闭合，它就无法供任一
严格 Provider 重放，必须建立安全 revision。

第二次修正删除这项资格门禁。`completedAt` 与终态审计继续保留为诊断事实，原始工具/Provider
事件仍留在 append-only audit；修复资格只由 durable replay surface 的协议状态决定。跨消息闭合
必须与实际请求编译链一致：只有 `ai` 消息中的工具结果标签会成为 `TOOL_RESULT`，普通 `user` 或
`summary` 正文中的同名 XML 不能闭合调用。被合法跨消息事务消费的 AI 结果消息与原调用消息共同
保留；结果顺序、工具名或 call ID 不满足 Provider 现有协议时仍按损坏历史处理，不放宽严格校验。

跨消息保留还要求 source message 自身没有待应用的 durable 规范化：调用消息内已经出现的结果
必须满足 Provider FIFO、全部是终态，且不需要结果重排或补写 `provider_tool_name`。因此
`call A, call B, result B` 后接 `result A` 仍是非法历史，不能把后续结果当作“补齐”而保留。
旧 `package_proxy / proxy` 的同消息展示别名可以从调用参数证明真实 invocation 名并原地补写
`provider_tool_name`；跨消息结果无法在保留 source 时原地规范化，所以别名匹配会进入安全修复。

#### 最终反向审查补齐的协议入口

最终候选还补齐了五处不经过普通发送完成 owner、但会生成或消费 replay history 的入口：

1. “插入总结”的切片压缩和通用自动压缩都先等待当前 chat 的活动回合结束，再执行统一 repair
2. 单条消息重新生成在新 variant 入库前执行 `requireClosed("regeneration_completion")`
3. `ConversationService` 将同一并行调用组的连续 `TOOL_RESULT` 合并为一个 Provider turn，保持
   当前 follow-up 与未来 Gemini replay 的结果组形状一致；`TOOL_CALL` 仍逐项保留
4. OpenAI 在 `call.execute()` 前遇到请求体编译、工具历史协议或本地请求创建错误时原样抛出，
   不进入网络重试，也不再误包装成“连接超时”；HTTP 提交后的失败继续遵守既有重试合同
5. 跨消息闭合和 legacy proxy 只接受无需改写即可由严格 Provider 重放的历史，不把修复能力当成
   放宽协议的理由
6. 多结果 follow-up 的总字符预算必须保留每个真实调用的完整结果信封，只压缩 payload；达到总
   长度后停止追加或直接截断 XML 都会重新制造 open call，必须删除

#### 已确认失败矩阵

| 入口 | 当前行为 | 失败结果 |
| --- | --- | --- |
| 周期流式快照 | 每 1 秒把 `contentSnapshot` 原样 upsert 到 `ChatMessage` | 工具组执行期间进程终止会留下 call-only 历史 |
| 手动停止 | `detachStreamingAiMessage()` 原样保存 replay cache | call 已到达而 result 未全部到达时污染下一轮 |
| 非手动取消 | `CancellationException` 跳过 `finalizeMessageAndNotify()` | 最后一次中间快照继续留在数据库 |
| 普通 Provider/工具失败 | 错误可见，但普通消息收尾仍保存原始部分正文 | 失败回合可携带未闭合工具事务 |
| 正常完成 | 只检查非空正文，没有检查工具事务闭合 | 上游误报完成时可写入协议不完整消息 |
| 多结果 follow-up 总长度 | 超过 64000 字符后停止追加，外层仍可直接截断 | 无取消也会只提交部分结果并在 user boundary 失败 |
| 自动上下文压缩 | `ConversationCompactionContract` 正确拒绝非法工具历史 | 拒绝后发送仍使用原历史，并在 Provider 边界失败 |
| 旧版本恢复 | 没有基于中断证据的历史修复 | 已损坏聊天升级后仍无法继续发送 |

并行工具是非手动复现的重要入口：`executeInvocations()` 使用结构化 `coroutineScope` 和多个
`async`。任一工具在审计、账本、结果发射或其他基础设施阶段抛出异常时，兄弟任务会收到
`JobCancellationException`；当前回合按错误结束，但已输出的多个 tool call 不一定都有结果。
这不是网络重试问题，也不能通过给缺失项生成失败文本来掩盖。

#### 设计与状态所有权

新增纯 Kotlin replay projection，输入单条当前 assistant surface，输出原内容、规范化完整内容或
安全前缀以及结构化诊断。解析规则支持随机标签后缀；结果按 `provider_call_id` 或名称匹配整组
pending calls，而不是只比较队首；多个并行调用只在全部结果闭合后提交整个事务，并把 durable
结果块规范为原调用顺序。完整第一轮加未闭合第二轮时，只移除第二轮事务；截断 opening/closing
XML 从所属事务起移除。投影不写数据库、不执行工具、不读取 Provider，也不改变 live stream 或
审计。

接线顺序：

1. `persistStreamingSnapshot()` 继续把原始内容写入审计，但 upsert 消息时使用安全投影；带
   `contentStream` 的 UI 仍由 live stream 显示当前工具状态
2. `completeAssistantResponse()` 在写正常终态前取得投影：无损结果重排可正常完成并保存投影
   正文；只有截断诊断才抛出明确协议异常
3. 手动停止、普通失败和非手动取消的部分消息统一通过同一投影后再持久化；破坏性历史修改仍不
   保留部分回答
4. 新用户消息、通用自动 compaction 和“插入总结”的切片 compaction 在读取 snapshot 前执行一次
   replay-boundary 旧历史修复；两类压缩都先等待活动回合完成安全终态持久化，修复再通过
   `ConversationAuditRepository.reviseMessage()` 在同一 Room 事务中写入 before/after、诊断、
   revision、当前 projection、恢复事件和 seal
5. 普通完成与重新生成分别在 `assistant_completion`、`regeneration_completion` 边界要求闭合，
   保存闭合检查返回的规范化正文；连续并行结果在请求编译层合并为一个 Provider result turn
6. `ConversationMarkupManager` 先计算全部结果的最小完整信封，再公平分配剩余 payload 预算；
   `EnhancedAIService` 只接受满足结构化上限的完整消息，不再执行字符级截断
7. Provider adapters 与 `ProviderToolHistoryState` 保持严格，不添加修复分支；
   `ConversationCompactionContract` 继续作为压缩范围与提交校验 owner

#### Responses typed state 边界

`message_provider_states`、`OpenAIResponsesExecutionState` 与
`RepositoryOpenAIResponsesExecutionPersistence` 的读写链已经单独核对。`outputItemsJson` 由同一个
Responses execution 的事件流维护，只用于已知 `remoteResponseId` 和 `lastAppliedSequence` 的
sequence 续接；当前 `functionCallOutputsJson` 固定写入 `[]`。普通下一轮请求、PromptTurn 编译和
`ConversationCompactionContract` 均没有读取这张表，Responses 的跨轮 `function_call` /
`function_call_output` 仍从 `ChatMessage.content` 编译。

因此本里程碑不修改、清空或投影 typed provider state。中断回合的 provider-private output items
继续作为不可覆盖的 execution/audit 事实存在，但不会绕过修复后的 `ChatMessage` 单独进入下一轮
模型请求。这样保持 exactly-once/sequence 恢复职责与跨轮 replay surface 单一所有权，不引入
第二份 compaction 历史。

#### 影响文件与非目标

确认影响：

- `core/chat/AssistantReplayHistoryProjection.kt` 及对应 JVM 测试
- `api/chat/enhance/ConversationMarkupManager.kt`、`ConversationService.kt`、
  `ToolExecutionManager.kt` 及对应 JVM 测试
- `api/chat/EnhancedAIService.kt`
- `api/chat/llmprovider/OpenAIProvider.kt`、`GeminiProvider.kt`、`StructuredToolCallBridge.kt`
  及对应 JVM 测试
- `services/core/MessageProcessingDelegate.kt` 及对应测试
- `services/core/MessageCoordinationDelegate.kt`
- `services/core/ChatHistoryDelegate.kt`
- `data/repository/ChatHistoryManager.kt`
- `data/audit/ConversationAuditRepository.kt`
- `core/chat/ConversationCompactionContract.kt` 及对应 JVM 测试
- `util/ChatMarkupRegex.kt` 及对应 JVM 测试
- `CONTEXT.md`、本文件与 `docs/TODO/README.md`

非目标：不改变工具执行并发策略、工具权限、HTTP 提交后的 Provider network retry、Responses
exactly-once 账本、消息数据库 schema、UI 布局、自动压缩选区算法或用户可见取消交互；不运行
Release，不安装或操作设备。

#### 风险、回滚点与验证

- 风险：错误截断普通 XML。控制方式是只识别现有工具标签语法，只处理 `ai` durable surface，
  并在跨消息场景按真实 PromptTurn sender、FIFO 结果顺序、工具名和 call ID 验证闭合；普通用户
  正文中的工具样式 XML 不构成闭合证据
- 风险：周期投影覆盖 live UI。控制方式是保留原 `contentStream`，只改变持久化 `content`
- 风险：修复与活动回合或异步 compaction 竞争。控制方式是自动压缩先等待活动回合落盘，再在
  compaction plan 之前完成修复，并保留提交时 digest 复核
- 风险：并行工具部分结果被单独保留。控制方式是从该组第一条 call 起按事务移除，而不是只删
  缺失 call
- 风险：跨消息后缀掩盖 source message 内的错序或中间结果。控制方式是只有 source 本身已经满足
  FIFO、无需任何内容规范化且后续终态结果继续按 FIFO 到达时才保留整个事务
- 风险：多工具大结果在总长度门禁中重新丢失。控制方式是先预留全部真实结果信封并用严格状态机
  回归四并行大结果，payload 预算不足只影响内容长度，不影响结果数量和 XML 完整性
- 回滚点：本里程碑独立提交；回滚只能恢复提交前代码，不得通过放宽协议校验或伪造结果替代

验证顺序：纯投影/修复策略 JVM 测试；消息处理与 compaction 定向回归；必要完整 JVM 回归；
formal readiness、architecture/Markdown、`git diff --check`；规定 Debug 构建和 APK 静态核验；
最后执行 staged/sensitive/submodule/artifact 审计、提交、推送及 ref 对账。

#### 当前实现与验证证据

- `AssistantReplayHistoryProjector` 已覆盖纯文本、随机标签后缀、单工具、三个并行工具、四个并行
  工具乱序完成、同名 call ID、代理显示名/协议名、流式中间/终态结果、部分结果、完整事务、连续
  事务、截断 XML、孤立结果、名称与 call ID 冲突、投影幂等，以及跨持久消息闭合/未闭合策略；
  完成路径测试确认实际保存闭合检查返回的规范化正文
- 每秒快照只持久化安全投影；正常完成先执行闭合断言；普通失败、手动停止和非预期取消均不再把
  call-only 后缀写回 durable replay surface
- 旧历史恢复使用不会吞数据库异常的严格读取入口；凡当前 AI variant 直到 replay boundary 仍未
  闭合就建立安全 revision，不再依赖完成时间或同 timestamp 失败审计。已经按真实 PromptTurn
  sender、FIFO 结果顺序、工具名和 call ID 被后续持久消息合法闭合，且 source message 没有
  中间结果、错序或待应用规范化的事务保持不变；原始 Provider/工具事实仍保留在 immutable
  audit payload
- 通用自动压缩与切片压缩入口都等待当前 chat 的活动回合结束，再修复和取得 snapshot；既有
  `ConversationCompactionContract` 的工具闭合、范围 digest、route 和相邻 summary 门禁保持不变
- 重新生成保存新 variant 前执行 `regeneration_completion`；连续并行 `TOOL_RESULT` 编译为同一
  Provider turn；OpenAI 的 HTTP 提交前本地协议失败不再进入五次网络重试
- 最终候选的完整 `:app:testDebugUnitTest` 为 `291 suites / 1715 tests`，
  `failures/errors/skipped = 0/0/0`；`:app:compileDebugAndroidTestKotlin`、formal readiness、
  architecture `phase=m03` 与差异检查通过
- 四并行 `40000` 字符结果在 `64000` 总预算内保留 `4/4` 个完整 XML 信封和各自 payload 前缀，
  并通过严格 `ProviderToolHistoryState.requireClosed("user_boundary")`；带图片的大结果保留完整 link
- 规定的 `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 47s`，232 个任务中 22 个 executed、
  210 个 up-to-date。APK 于 `2026-08-22 20:32:57 +08:00` 写入
  `app/build/outputs/apk/debug/app-debug.apk`，大小 `472737951` bytes，SHA-256
  `5317969BAD123305343D957A337ABC35DC99667841C633001156CF42E02B8E2F`；身份为
  `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，唯一 `MainActivity` launcher、Android Debug V2
  单 signer 与 16 KB ZIP alignment 通过。APK 共 `5506` 个 file entry、`44` 个 DEX，仅含
  `arm64-v8a` 的 `51` 个无重复 basename `.so`；生产包白名单 `45/45`，其中 `12` 个生成式
  ToolPkg；shell/ripgrep 存在，不含 `libsudo.so`
- 加 shell 共 `52/52` 个 ELF64/AArch64；`153` 个 `PT_LOAD` 为
  `0x4000 x 151 + 0x10000 x 2`，无低于 16 KiB；播放器与 FFmpegKit 的 `19/19` 个 AAR native
  payload 和 APK 逐字节一致。消息处理 DEX 主方法为 `25438 / 100000`，三个 continuation 为
  `35 / 33 / 30` registers，`violations=[]`；关键投影类、完成/重新生成边界和 Provider 协议错误
  已在最终 DEX 中检出，结构化结果预算与外层超限断言也已进入 DEX
- 真实 Provider、进程强杀、手动/非手动中断交互和自动压缩现场复测保持
  `verification_pending`

### M8 2026-08-29 DeepSeekHarness Responses 提交稳定性与第三方协议边界

状态：`LOCAL IMPLEMENTATION, AUTOMATED VALIDATION AND DEBUG APK VERIFIED / DEVICE VERIFICATION PENDING`。

#### 研究输入与已确认事实

- DeepSeekHarness `0.1.2-alpha.1` 的 DeepSeek adapter 将 Chat Completions wire、Responses
  wire、provider usage 和 retry policy 分开；缓存命中只接受 provider usage 的明确字段。
- 官方 DeepSeek Responses 审计使用 `https://api.deepseek.com/v1/responses` 并成功完成多轮工具
  调用，证明当前 Responses 基础解析、工具事件和官方路径可用。
- SiliconFlow 审计的配置是 provider `DEEPSEEK`、protocol `OPENAI_RESPONSES`、base URL
  `https://api.siliconflow.cn/`，Kiyori 自动补全为 `/v1/responses` 后收到 HTTP 404。SiliconFlow
  的公开 DeepSeek 接口是 `https://api.siliconflow.cn/v1/chat/completions`，属于 Chat
  Completions，不是 Responses。
- `CONTEXT.md` 的协议合同要求用户显式选择并持久化 `ApiProtocol`，运行时失败后不得自动切换
  协议或端点；因此不能用错误响应触发静默降级。

#### 实施合同

1. DeepSeek Responses capability profile 使用
   `ExecutionPersistenceCapability.RESPONSES_AT_MOST_ONCE`。这表示提交状态未知时禁止第二次
   POST，不表示可以发送 OpenAI `previous_response_id`、`background`、`store`、sequence
   resume、encrypted reasoning 或 `prompt_cache_key`。
2. 提交阶段只有 HTTP `429` 可按统一有限次数策略重试；`404/400/401/403/422` 单次明确失败；
   `408/409/5xx` 和响应头前/流中传输异常写入 `SUBMISSION_UNKNOWN`，保留已确认的本地执行事实。
3. SiliconFlow 只能通过显式 `OPENAI_CHAT_COMPLETIONS` 路由请求
   `/v1/chat/completions`。DeepSeek provider 下使用 SiliconFlow 时，用户必须把协议切到 Chat
   Completions；软件不从 404 或模型名推断协议，不新增第二 provider 或旁路代理。
4. DeepSeek Chat 工具结果解析保留 `provider_call_id` 和工具名；没有显式 ID 时只按已验证的
   调用顺序匹配，不生成新的 provider ID。`reasoning_content`、工具调用身份和 usage 的
   `uncached input/cache read/cache write/output` 桶保持互不重叠。

#### 影响文件与验收矩阵

- `ModelCapabilityProfile.kt`：DeepSeek Responses profile 改为 at-most-once。
- `OpenAIProvider.kt`：复用现有 at-most-once 提交分支，确认 404 不进入普通 retry；DeepSeek
  Chat 结果记录使用原始 call ID。
- `AIServiceFactory.kt`、`EndpointCompleter.kt`、`ApiProviderConfigCollect.kt`、
  `ModelListFetcher.kt`：验证官方 DeepSeek Responses 与 SiliconFlow Chat endpoint 的显式
  协议路由不互相覆盖。
- 对应 JVM 测试覆盖：DeepSeek profile、404 单 POST、传输中断 `SUBMISSION_UNKNOWN`、官方
  `/v1/responses`、SiliconFlow `/v1/chat/completions`、`reasoning_content`/tool call identity、
  cache-hit disjoint buckets。

#### 风险、回滚点与验证

- 风险：用户仍保存了 SiliconFlow base URL 与 Responses 协议。控制方式是保留明确的单次 HTTP
  404/协议错误和可诊断提交状态，文档与 endpoint 预览说明正确的 Chat 配置；不在运行时改写协议。
- 风险：工具结果 XML 中缺少 call ID。控制方式是按调用顺序匹配并让名称/数量不一致进入既有
  严格协议错误；不伪造 ID 或结果。
- 回滚点：本轮单独提交；回滚只恢复修改前 capability、解析和测试，不放宽协议检查或重新启用
  重复 POST。
- 验证顺序：定向 JVM -> `check_formal_readiness.py --require-main` -> `check_fresh_clone.py` ->
  `git diff --check` -> 串行 `:app:assembleDebug` 与 APK 审计 -> staged/sensitive/submodule/
  artifact 审计 -> 提交、推送和三方 ref 对账。真实 provider、真实 cache hit、设备 UI 与中断交互
  仍保持 `verification_pending`。

#### 本轮已完成的本地证据

- 最终代码状态下串行执行 `./gradlew :app:testDebugUnitTest --no-daemon --console=plain`，
  `BUILD SUCCESSFUL`；formal readiness 与 fresh-clone 基线检查均通过，`git diff --check` 无空白错误。
- 串行执行 `./gradlew :app:assembleDebug --no-daemon --console=plain`，`BUILD SUCCESSFUL`。
  APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `503676753` bytes，SHA-256
  为 `22AFBB5599A9D3D2882FE032F90E825593314CEDB2CB4793821508F004FB74A6`；`com.kiyori`、
  versionCode `45`、单 signer、V2 签名和 zipalign 核验通过。
- APK 包含 `lib/arm64-v8a/liboperit_ripgrep.so`、`assets/operit_shell_exec` 和生产白名单的
  `12` 个 `.toolpkg`，不包含 `libsudo.so`；上述产物检查不替代真实 Provider、缓存命中、设备
  交互或进程中断验收。

## 可恢复开发与上下文压缩合同

本任务允许跨多轮继续，但每一轮必须从以下持久状态恢复，不依赖模型记忆：

- 当前 Goal：本任务 objective 与阶段状态；
- 任务日记：目标、非目标、基线 HEAD、已有用户改动、已完成研究、正在实施的里程碑、
  修改文件、测试/构建结果、未验证边界和下一步；
- 本文件：唯一设计/验收矩阵；
- `docs/TODO/README.md`：总索引中的当前状态；
- 每个实现里程碑的 Git 提交作为回滚点；未经用户授权不使用 destructive reset。

上下文接近压缩时，先写入最小恢复 checkpoint，再继续当前里程碑；不要把大段命令输出复制
进日记。下一轮开始先检查 Git 状态、当前计划、TODO 状态和最近 checkpoint，再执行下一条
尚未验证的命令。若新证据推翻本方案，先修改本文件和阶段状态，再改代码。

## 当前停止点

M0-M6 已完成既有 DeepSeek/多协议/长上下文范围的本地实现与主分支交付。M7 已完成三次现场问题的
根因修正、最终反向审查、代码与文档实现，并重新通过完整 JVM、AndroidTest Kotlin 编译、Debug
APK、签名/对齐、消息处理 DEX、formal readiness 和 architecture `phase=m03`。本轮 Git 交付目标
是唯一 `main` 与 `origin/main`；最终提交和三方 ref 证据以本次交付报告为准，不在本设计文件中
固化易漂移的提交哈希。设备上的真实 Provider、进程强杀、手动/非手动中断交互和自动压缩现场
复测仍为 `verification_pending`。
