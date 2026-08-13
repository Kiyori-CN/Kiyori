---
status: verification_pending
implementation: local_implementation_complete
last_updated: 2026-08-13
---

# OpenAI Hosted Web Search 架构

## 1. 文档职责

本文定义 Kiyori 将 OpenAI 官方 Web Search 作为独立工具提供给所有主聊天模型时的长期架构边界。
实施进度、分阶段任务与验证矩阵记录在
[OpenAI 官方联网搜索插件化接入](../../TODO/openai_hosted_web_search/index.md)。

当前本地实现是 revision `7`。revision `6` 的请求生命周期、admission、单一 `PACKAGE_ENV`、
Provider 展示、三态 evidence parser 和分层结果卡继续作为基础；revision `7` 已完成 URL identity、
域名策略、零证据、answer normalization、强类型参数、来源投影、执行诊断、日志隐私和 ToolPkg
注册可观察性。该功能没有 Kiyori 用户发布版本，因此旧 `MODEL_CONFIG` 专项链已完整删除，不保留
迁移窗口、兼容分支或回退路径。

revision `3` 至 `5` 的 relay、parser、ToolPkg 和 APK 数据继续作为历史证据保存在专项 TODO 中。
revision `6` 的现场矩阵和历史 APK 也只作为问题基线，不能替代 revision `7` 当前工作树、构建、
设备或用户验收。当前任务不调用
真实 relay，不安装 APK，不操作设备，因此最终状态仍应区分本地实现、远端 relay、设备和用户验收。

## 2. 产品定义

OpenAI Hosted Web Search 是 Kiyori AI 包管理域中的可安装、可启停 ToolPkg 插件容器。

它与 `APK 逆向工具包`、`楼层限制器`、`深度搜索`、`额外信息注入` 同级，出现在包管理的“插件”
标签，而不是“脚本包”标签。

它不是：

- Kiyori Browser Plugin
- userscript
- MCP server
- Skill
- 当前聊天必须选择的 AI Provider
- 不依赖模型的普通搜索 endpoint

它允许：

```text
DeepSeek / Gemini / Claude / OpenAI / local model
	└─ Kiyori tool call
		└─ OpenAI Web Search ToolPkg
			└─ openai_web_search subpackage
				└─ OpenAI GPT-5.6 search service
					└─ OpenAI hosted web_search
```

主聊天模型负责用户对话与最终回答。GPT-5.6 搜索服务只负责联网检索、证据整理和来源输出。

ToolPkg 内部使用 TypeScript 或 JavaScript 实现入口与 subpackage，不改变其插件产品分类。

## 3. 唯一状态所有者

### 搜索服务绑定

搜索绑定没有独立的可变副本：

- `ToolPkgHostEnvironmentRepository` 持有 `com.kiyori.openai_web_search` 的二十个
  package-scoped、host-service 环境变量
- `OpenAIHostedWebSearchBindingResolver` 只通过
  `OpenAIHostedWebSearchBindingCompiler.compilePackageEnvironment()` 编译完整运行绑定
- `OpenAIHostedWebSearchCompatibilityRepository` 持有 relay strict 成功/失败 record-set、
  探测指纹、response schema revision 和成功 evidence mode；最多保留 16 个最近 fingerprint，
  不同凭据指纹互不覆盖

ToolPkg 不能保存另一份完整绑定，也不能从当前聊天状态推断绑定。

### 模型与 API Key

`ToolPkgHostEnvironmentRepository` 是本插件唯一配置事实源，持有：

- package-scoped endpoint
- model
- API Key
- auth mode
- extra headers
- reasoning 和搜索参数

Key 必须标记为 sensitive、password 和 `host_service` consumer。ToolPkg JavaScript 的 `getEnv()`
不能读取该值。

用户若希望搜索与某个聊天配置使用同一账号，必须在插件原生环境变量界面显式录入搜索绑定。宿主
不读取、引用、复制或跟随当前聊天的 `ModelConfigManager` 状态。

### 搜索执行

`OpenAIHostedWebSearchRequestLifecycle` 是每次搜索的唯一生命周期 owner。它记录：

- phase
- submission state
- cancellation owner
- elapsed time
- configured HTTP timeout
- queue wait
- provider request ID
- 唯一 terminal outcome

取消和 worker 完成必须通过同一个 `settle()` 领取终态。第一次取消进入
`CANCEL_REQUESTED`，只有第一次取消执行 transport/job cancel；取消先发生时，后续成功结果收敛为
`CANCELLED`，成功先 settle 后的新取消返回 false。

`OpenAIHostedWebSearchAdmissionController` 是进程内稳定、共享的 FIFO admission owner。配置变化
原地更新并发和 RPM，不替换 limiter 或 semaphore。普通搜索与 compatibility probe 使用同一
controller。

`OpenAIHostedWebSearchGateway` 是取得 admission permit 后单次 HTTP 请求的唯一执行 owner。

它负责：

- policy validation
- request identity
- HTTP lifecycle
- cancellation
- response parsing
- usage
- error

排队预算与 HTTP 预算分离。默认 queue timeout 为 `60s`；取得许可后 HTTP call timeout 为
`300s`，connect timeout 为 `min(timeout, 30s)`，write timeout 为 `min(timeout, 60s)`，
read timeout 与 call timeout 相同。queue timeout 返回 `QUEUE_TIMEOUT`、保持
`submission_state=not_sent`，并且不创建 HTTP Call。

### 证据

搜索 evidence 必须绑定工具调用身份。可见聊天正文不是唯一证据存储。

当前实现把完整结构化 JSON 同时保留在工具结果和 provider tool invocation ledger 中。成功结果原子
分离为 `cited_sources`、`all_sources`、`source_summary` 和 `execution_diagnostics`；聊天正文中的
工具结果使用 JSON Unicode escape 消除原始 `<`、`>` 和 `&`，解码后的 answer、URL 和 citation
offset 不变。UI 使用 `formatToolResultForMessage()` 接收完整 ToolResult，保留 `all_sources`、
search actions、query、usage 和完整 evidence schema；主模型使用
`formatToolResultForModel()` 接收受限 projection，只发送 answer、source markers、cited
sources、source summary、warnings 和必要 diagnostics，不重复发送完整 `all_sources`、search
actions、query、usage 或原始 citations。UI 和原始 ToolResult 仍保留完整 `all_sources`；只有
精确 `openai_web_search:search` 且 schema 校验通过的结果进入专用来源卡。
`buildBoundedToolResultMessage()` 是主模型上下文的唯一批量入口，不能将 projection 作为 UI
evidence 重新解析。

### ToolPkg 注册与递归工具可观察性

ToolPkg 注册日志复用实际加载 runtime 的 version、artifact SHA-256 和 source type，并记录
`registration_thread` 与主注册 `elapsed_ms`。OpenAI Web Search 额外记录
`response_schema_revision=7`。注册观测不记录私有路径、脚本正文、环境变量或异常正文；外部缓存
清除一次性 observation，资产快照只保留解析后的 runtime，避免重复输出旧耗时。

增强工具链的整棵递归子树使用 `enhanced.toolSubtree.complete`，并携带：

```text
round
depth
invocationId
resultCount
```

该阶段与每次 Hosted Web Search lifecycle 的单调计时分开，不能把递归子树完成时间当作单个 HTTP
请求耗时。

## 4. 固定不变量

- 搜索插件不自动读取当前聊天 Provider
- 搜索插件不自动读取当前聊天模型
- 搜索插件是 ToolPkg 插件容器，不是普通 JS 脚本包
- container 默认关闭
- subpackage 在 container 启用后默认开启
- 搜索绑定只能由用户或明确的宿主配置改变
- 配置来源只有 package-scoped `PACKAGE_ENV`
- official contract 只允许官方 Responses 与 GPT-5.6 family
- relay contract 必须通过严格兼容探测
- compatibility probe 只能由插件设置页的 `ui` runtime 发起
- 原生配置入口固定为无参数 `ToolPkg.services.openAIWebSearch.openConfiguration()`
- JavaScript 不能提交任意 package ID 或变量名
- ToolPkg JavaScript 不接触 API Key
- ToolPkg `privateData` 不保存 API Key
- 全局 `EnvPreferences` 不作为正式凭据来源
- APK、ToolPkg、资源和源码不内置 Kiyori 自有共享 OpenAI Key
- 搜索工具不是 ToolPkg AI Provider
- 第一版每次工具调用只有一个 Responses POST
- 第一版 `tool_choice=required`
- 第一版 `store=false`
- 第一版按用户固定模式显式设置 `external_web_access=true` 或 `false`
- `filters` 只发送实际非空的 `allowed_domains` 或 `blocked_domains`
- 搜索失败不触发其他搜索服务
- 搜索失败不触发自动重试、background、SSE、轮询或断线续流
- citation span 只属于原搜索 answer
- 主模型改写后，原 evidence 继续独立显示
- 网页内容不能改变 Kiyori 指令、权限或工具边界

## 5. 能力分层

```text
OpenAIHostedWebSearchCapability
	├─ OFF
	├─ RESPONSES_HOSTED_OFFICIAL
	├─ RESPONSES_RELAY_STRICT
	├─ NATIVE_CHAT_RESPONSES
	└─ CODEX_STANDALONE
```

状态：

- `OFF`：无绑定或插件关闭
- `RESPONSES_HOSTED_OFFICIAL`：官方 OpenAI Responses
- `RESPONSES_RELAY_STRICT`：通过付费严格探测的 Responses-compatible 中转站
- `NATIVE_CHAT_RESPONSES`：后续可选
- `CODEX_STANDALONE`：等待公开稳定 API

这些是明确 backend，不在运行失败时互相切换。

## 6. ToolPkg 合同

ToolPkg ID：

```text
com.kiyori.openai_web_search
```

subpackage ID：

```text
openai_web_search
```

公开工具：

```text
openai_web_search:search
```

ToolPkg 容器提供：

- 包管理主开关
- 设置页
- 容器级环境变量声明
- 可选输入菜单会话开关
- `openai_web_search` subpackage

输入菜单开关若实现，只能表示当前会话是否暴露工具，不能复制包管理全局启停状态。

模型可提交：

- query
- context size override
- 更严格的 allowed/blocked domains
- 是否使用已配置的大致位置

模型不能提交：

- API Key
- endpoint
- model
- provider config ID
- arbitrary headers
- OpenAI organization/project header
- reasoning effort
- live/indexed mode
- unlimited return token budget

## 7. 配置来源

```text
PACKAGE_ENV
	独立 endpoint、model、Key、认证和参数
```

revision `7` 的完整二十项环境变量是：

```text
OPENAI_WEB_SEARCH_PROVIDER_CONTRACT
OPENAI_WEB_SEARCH_RESPONSES_ENDPOINT
OPENAI_WEB_SEARCH_MODEL
OPENAI_WEB_SEARCH_API_KEY
OPENAI_WEB_SEARCH_AUTH_HEADER_NAME
OPENAI_WEB_SEARCH_AUTH_SCHEME
OPENAI_WEB_SEARCH_EXTRA_HEADERS_JSON
OPENAI_WEB_SEARCH_REASONING_EFFORT
OPENAI_WEB_SEARCH_MAX_OUTPUT_TOKENS
OPENAI_WEB_SEARCH_RETURN_TOKEN_BUDGET
OPENAI_WEB_SEARCH_ADDITIONAL_INSTRUCTIONS
OPENAI_WEB_SEARCH_EXTERNAL_WEB_ACCESS
OPENAI_WEB_SEARCH_CONTEXT_SIZE
OPENAI_WEB_SEARCH_ALLOWED_DOMAINS_JSON
OPENAI_WEB_SEARCH_BLOCKED_DOMAINS_JSON
OPENAI_WEB_SEARCH_LOCATION_JSON
OPENAI_WEB_SEARCH_QUEUE_TIMEOUT_SECONDS
OPENAI_WEB_SEARCH_TIMEOUT_SECONDS
OPENAI_WEB_SEARCH_MAX_CONCURRENT_REQUESTS
OPENAI_WEB_SEARCH_REQUESTS_PER_MINUTE
```

`OPENAI_WEB_SEARCH_EXTRA_HEADERS_JSON` 拒绝认证字段、`User-Agent`、`Host`、
`Content-Length` 和 hop-by-hop headers。JavaScript 不能在单次调用参数中覆盖这些配置。

设置页固定返回九项 readiness：

```text
provider_contract
endpoint
model
credential
auth
extra_headers
search_options
admission
compatibility
```

即使 endpoint、Key 或其他字段失败，状态页仍保留各字段的 `state`、稳定错误码和脱敏说明，不再
把整套状态清空后只显示笼统“配置未完成”。

完整环境变量合同记录在
[中转站与环境变量配置](../../TODO/openai_hosted_web_search/5_relay_and_environment_configuration.md)。

## 8. 请求与响应

请求必须：

- 使用固定绑定模型
- 只发送当前 query 和固定 search instruction
- 注册一个 `web_search` tool
- 强制工具调用
- 请求完整 sources
- 不存储 response
- 不携带聊天历史
- reasoning 使用插件精确配置
- `return_token_budget` 只接受 `default` 或 `unlimited`
- 只有一侧域名过滤非空时，不序列化另一侧空数组

响应必须保留：

- response ID
- answer
- original annotation spans
- stable per-call source IDs
- search/open/find actions
- full sources
- usage
- warnings
- evidence mode
- source diagnostics

证据是显式模式合同，不要求所有 relay 同时返回 annotations 与 `action.sources`：

```text
url_citations_and_action_sources
url_citations
action_sources
structured_feeds
```

具体规则：

- `type=url` 且 URL 为 HTTP(S) 的条目进入引用来源集合
- `oai-sports`、`oai-weather` 和 `oai-finance` 作为官方已知无 URL 实时 feed 单独保留
- relay 的命名 `api` feed 单独保留，名称为空时视为损坏条目
- 未知、缺字段或非法 URL 条目进入结构化诊断
- 任意一个损坏或无 URL feed 不得使其他有效 URL source 丢失
- `url_citation` annotation 是行内 citation span 的权威证据
- action sources 是独立 search audit channel，不是 citation 白名单
- citation 与 action source URL 不完全重合时保留有效 citation，并输出精确差集诊断
- action source coverage 只描述 search action 的 sources 字段是否缺失、部分缺失、完整或不适用
- `open_page.url` 只保留为动作诊断，不冒充 citation 或 action source
- citation-only 由真实 annotation URL 建立来源，不声称 action source 存在
- 没有真实 citation span 时，`answer_with_source_markers` 不插入 `[S1]`

## 9. 凭据策略

默认建议独立 OpenAI Project 和 Service Account Key。

当前聊天模型配置永远不是隐式搜索配置。

第一版是用户自备 Key 的本地直连模式。原生宿主隔离 ToolPkg，但不宣称 Android 客户端具有
服务器级秘密保护。未来若由 Kiyori 提供统一搜索额度，必须使用服务端代理和短期用户凭据，不能
向 APK 分发运营方 OpenAI Key。

## 10. 中转站合同

`RESPONSES_RELAY_STRICT` 通过条件：

- HTTP success
- Responses object schema
- 至少一个 `web_search_call`
- 非空 output text
- 至少一个 `url_citation`
- usage 可解析或有明确缺失 warning

探测固定访问 `developers.openai.com` 的公开 Web Search 页面。日期、天气、体育和金融问题可能使用
无 URL 的结构化实时 feed，不属于 URL citation 兼容性探针。

探测指纹至少包含 endpoint、model、auth mode、不可逆 credential digest、非秘密 header names、
reasoning 和 external web access。任一字段或 Key 改变后必须重新探测。response schema revision
当前为 `7`；revision 不一致时成功与失败记录都失效。revision `6` 及更早 record-set 不能投影为
revision `7` 状态。成功与失败按 exact fingerprint digest 保存在最多 16 条的 record-set 中，
同一 digest 互斥，不同凭据指纹记录互不删除。成功、失败和清理 record-set 在 callback 交付前
同步提交并检查提交结果，避免设置页关闭后立即进程退出造成相同 fingerprint 的成功证据丢失。
该持久化修复不绕过 strict relay probe；fingerprint 输入变化后仍必须重新探测。

HTTP 非成功响应最多读取 64 KiB，只投影经过控制字符清理、凭据脱敏和长度限制的 provider
error type、code、message 与 request/trace ID。状态页仅显示短 credential revision 和
`bearer`、`direct`、`custom` 分类，不显示 Key、Authorization 值或自定义 scheme 原文。

只返回普通文本的 endpoint 不是等价 Web Search backend。

## 11. UI 证据

精确工具名 `openai_web_search:search` 在 `READ_ONLY`、`ALL` 和 `FULL` 模式下，即使单工具也建立
消息级 L0 工具组。它复用现有 `rendererId + stableKey + expanded + userOverride +
hasLiveXmlStream` 状态 owner；流式自动展开、静态完成自动收起、用户操作后尊重 override 的逻辑
不创建第二份状态。

结果卡层级是：

```text
L0 消息级工具组
L1 搜索摘要
L2 答案
L3 来源
L3 搜索轨迹
L3 诊断与用量
```

L1 默认收起。展开后显示答案；来源、搜索轨迹和诊断独立折叠。来源保持原顺序并按每批八条增加
可见数量。URL source 显示标题、host 和可选择的完整 URL；结构化 feed 显示为不可点击来源；
`search`、`open_page`、`find_in_page` 使用不同动作图标。

evidence parser 返回 `NotApplicable`、`Parsed` 或 `Invalid`。`Invalid` 只包含错误类别、
schema revision、request ID 是否存在和字段名；有界日志最多保留 128 个诊断键，不记录 query、
answer、sources 或完整 result。失败卡和 parser-invalid 卡仍保留普通 `ToolResultDisplay`，保证
完整原始工具结果可复制而不会静默消失。

warning 分为 `INFO`、`WARNING` 和 `ERROR`。跨 evidence 通道的正常差异属于信息或警告，损坏的
action source 才属于错误。

## 12. 安全

- official endpoint 使用 exact match
- relay endpoint 要求 HTTPS 和有效探测指纹
- ToolPkg runtime 不能在单次调用中提交或覆盖 header
- extra headers 使用强类型 JSON object 并拒绝认证冲突和 hop-by-hop header
- HTTP redirect 直接失败
- 不记录 Key 或 Key 片段
- 不自动读取精确位置
- domain 输入按 host 校验
- 网页内容标记为 untrusted evidence
- 用户停止取消单次 HTTP call
- 超时、限流和认证错误分类可见
- 自动测试只使用 mock

宿主固定以下字段，插件配置不能覆盖：

```text
tools[].type = web_search
tool_choice = required
include contains web_search_call.action.sources
store = false
stream = false
固定安全指令前缀
搜索证据 parser
redirect policy = reject
```

## 13. 与 Kiyori 现有架构的关系

复用：

- `SharedHttpClient`
- `OpenAIResponsesPayloadAdapter`
- ToolPkg container identity
- ToolPkg UI、subpackage、IPC 和 storage
- provider/tool execution 持久化

revision `7` 当前专用 owner：

- ToolPkg container-level environment schema
- package-scoped sensitive host-only environment repository
- official/relay contract 和 compatibility probe
- OpenAI Web Search package-bound host bridge
- `OpenAIHostedWebSearchRequestLifecycle`
- `OpenAIHostedWebSearchAdmissionController`
- `OpenAIHostedWebSearchReadinessEvaluator`
- Web Search JSON XML 安全承载和 evidence source card
- `OpenAIWebSearchResultPresentationPolicy`
- `ModelApiProviderPresentationPolicy`
- `OpenAIHostedWebSearchMainModelProjection`
- `OpenAIHostedWebSearchUrlIdentity`
- `ToolPkgRegistrationObservation`
- `ToolSubtreeTrace`

不复用为错误角色：

- 不把 `OpenAIResponsesProvider` 当作搜索工具直接发送伪聊天
- 不用 `ToolPkg.registerAiProvider`
- 不用 Browser Runtime 抓取网页替代 OpenAI Web Search
- 不用全局 env 作为正式 Key

## 14. 兼容和发布

Kiyori 没有用户发布版本，因此 revision `7` 继续沿用 revision `6` 对旧配置来源的删除结果，
不保留兼容路径。全局四种
OpenAI Provider 枚举不是该插件配置来源，继续保留并在设置页明确显示：

```text
OpenAI Chat Completions（官方）
OpenAI Chat Completions（兼容端点）
OpenAI Responses（官方）
OpenAI Responses（兼容端点）
```

选择器按 Chat Completions、Responses、其他内建 Provider 和动态 ToolPkg Provider 分组。已选
Provider 使用多行全名与协议/端点摘要，TalkBack 语义包含标题、完整 Provider 名和摘要。

revision `7` 当前 ToolPkg 版本是 `1.0.6`，manifest 声明二十个 host-service 环境变量，response
schema revision 是 `7`。本地 Hosted Web Search JVM 矩阵为 `26 suites / 142 tests`，失败、错误和
跳过均为 `0`；Kotlin compile、TypeScript strict、dist hash stability、formal readiness 和
Debug APK 验证已通过。真实 relay revalidation、设备和用户验收仍未执行。

最终仍需分别报告：

```text
LOCAL_IMPLEMENTATION_COMPLETE
REMOTE_RELAY_REVALIDATION_PENDING
DEVICE_PENDING
USER_ACCEPTANCE_PENDING
```

本地单元、TypeScript、构建和 APK 静态审计不能替代真实 relay、320dp/字体放大/TalkBack、目标
Android 设备或用户验收。revision `3` 至 `6` 的历史制品与现场失败数据见专项 TODO，不能作为
revision `7` 当前交付物。
