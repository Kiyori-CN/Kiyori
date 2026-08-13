---
status: local_analysis_complete
baseline_branch: main
baseline_head: 180db686aaeedfa301b3c8a31057ee4940d58649
observed_at: 2026-08-13
scope: implementation_blueprint
---

# 9. 2026-08-13 优化实施计划

本文将 2026-08-13 对 `openai_web_search` 的取消、超时、并发、配置、Provider、结果卡片、
流式和 `User-Agent` 调查收敛为正式实施蓝图。

本轮只完成调查、架构与 UI 设计、候选文件和验收矩阵。生产源码、测试源码、ToolPkg manifest、
构建产物、设备和远端均未修改。

## 9.1 最终结论

本轮选择：

1. 先修复同步非流式搜索，不把 `stream=true` 当作取消问题修复
2. 将超时、主动取消、聊天停止、execution owner 结束、排队取消分成明确生命周期原因
3. 用唯一 admission controller 管理限速、并发、FIFO 排队、配置热变更和 queue timeout
4. 用显式 request lifecycle 状态机原子决定完成或取消，禁止依赖线程时序决定最终 callback
5. 将 `PACKAGE_ENV` 设为唯一配置来源，完整删除本插件的 `MODEL_CONFIG` 专项链
6. 保留四种全局 OpenAI Provider 枚举，改进名称、协议分组和已选值完整显示
7. 搜索结果使用“消息级工具组、结果摘要、答案、来源/轨迹/诊断”分层展开
8. evidence parser 失败必须可观察，不再静默变成普通工具结果
9. 不模拟 Codex Desktop 或其他产品 `User-Agent`
10. background、轮询、可续流和服务端 cancel 作为后续独立里程碑

任务状态：

```text
LOCAL_ANALYSIS_COMPLETE
IMPLEMENTATION_NOT_STARTED
LOCAL_TESTS_NOT_RUN
DEBUG_APK_NOT_BUILT
REMOTE_RELAY_NOT_CALLED
DEVICE_NOT_USED
USER_ACCEPTANCE_NOT_STARTED
```

## 9.2 问题优先级

| 优先级 | 问题 | 当前证据 | 本计划处理 |
| --- | --- | --- | --- |
| P0 | HTTP call timeout 被误报为 `REQUEST_CANCELLED` | 源码与 OkHttp `4.12.0` 行为已确认 | 修复分类顺序并增加阶段诊断 |
| P0 | 完成与取消没有原子终态 | ownership registry 与测试已确认 | 引入 request lifecycle 状态机 |
| P0 | `MODEL_CONFIG` 正常 UI 不可达 | ID 生成、读取与 UI 链已确认 | 完整删除该来源 |
| P1 | 并发与 RPM 排队没有独立 deadline 和诊断 | gateway、limiter、Semaphore 已确认 | 唯一 admission controller |
| P1 | 一个超时值同时控制 connect/write/read/call | gateway 已确认 | 拆分内部阶段预算 |
| P1 | 专用卡 parser 失败静默消失 | parser 与 renderer 已确认 | 显式解析结果与脱敏日志 |
| P1 | 完成结果卡与外层工具组状态分裂 | Compose 实现已确认 | 统一分层状态模型 |
| P2 | 单个搜索工具在常用折叠模式下不建立工具组 | grouping policy 已确认 | 单个富结果搜索也进入组 |
| P2 | Provider 已选值被强制省略 | `SettingsSelectorRow` 已确认 | 纵向、多行摘要 |
| P2 | 来源和 search actions 缺少规模与类型组织 | 专用结果卡已确认 | 分区、数量摘要和按需展开 |
| P2 | warning 没有严重度 | evidence UI 已确认 | info/warning/error 分级 |

## 9.3 复现路径与根因模型

### 复杂查询误报取消

当前默认时序：

```text
ToolPkg 工具执行预算 1800s
	→ RPM 等待
	→ Semaphore 等待
	→ 创建 OkHttp call
	→ call/connect/read/write timeout 均为 60s
	→ OkHttp call timeout 取消 Call
	→ InterruptedIOException("timeout")
	→ call.isCanceled() == true
	→ 当前分类先返回 REQUEST_CANCELLED
```

复杂、reasoning 较高或多轮 agentic search 的服务时间更长，因此更容易越过 `60` 秒。OpenAI
官方资料也明确说明 reasoning agentic search 会增加延迟，深度研究可能持续数分钟。

这能解释一类现场 `REQUEST_CANCELLED`，但不能把每一次取消都归为 timeout。显式停止、聊天取消、
排队期间 owner 结束和 bridge 关闭仍然是真实取消来源。

### 并发或连续调用

普通同一聊天回合中的 `openai_web_search:search` 不在
`ToolExecutionManager.parallelizableToolNames` 中，因此默认串行。用户观察到的并发主要来自：

- 不同聊天或不同 ToolPkg execution
- 插件设置页 compatibility probe 与普通搜索
- 外部同时触发
- 一个自定义 ToolPkg execution 内主动创建多个 Service Promise

当前 admission 顺序是：

```text
插件 RPM
	→ MODEL_CONFIG RPM
	→ 插件 Semaphore
	→ MODEL_CONFIG Semaphore
	→ HTTP
```

删除 `MODEL_CONFIG` 后只保留插件级 admission，但仍需解决配置变化时替换 limiter/Semaphore 导致
旧 gate 与新 gate 同时存在的问题。

### 完成与取消竞态

当前 registry：

```text
requestCancellation()
	读取 entry
	调用 cancel
	不移除 entry

complete()
	移除 entry
	返回是否允许 deliver
```

因此以下两种结果取决于线程时序：

- worker 已获得成功结果，owner 同时请求取消
- callback 已准备投递，execution session 同时结束

现有单元测试允许一次 entry 先取消、随后再 `complete()` 成功。这证明 registry 只做映射，没有表达
最终状态所有权。

## 9.4 目标请求生命周期

新增唯一的请求生命周期模型。候选名称：

```text
OpenAIHostedWebSearchRequestLifecycle
OpenAIHostedWebSearchRequestPhase
OpenAIHostedWebSearchTerminalOutcome
OpenAIHostedWebSearchSubmissionState
```

阶段至少包含：

```text
CREATED
WAITING_RATE_LIMIT
WAITING_CONCURRENCY
PREPARING_HTTP
REQUEST_HEADERS
REQUEST_BODY
WAITING_RESPONSE_HEADERS
READING_RESPONSE_BODY
PARSING_RESPONSE
DELIVERING_CALLBACK
TERMINAL
```

提交状态至少包含：

```text
NOT_SENT
SUBMISSION_UNKNOWN
RESPONSE_STARTED
```

终态转换规则：

1. active request 可以接收一次 cancel request
2. cancel request 使用原子状态转换从 `ACTIVE` 进入 `CANCEL_REQUESTED`
3. 只有第一次 cancel request 执行 gateway/job 取消动作
4. worker 最终调用 `settle(outcome)`
5. `ACTIVE` 状态下，worker 的真实完成结果成为终态
6. `CANCEL_REQUESTED` 状态下，worker 只能产生取消终态
7. 已进入 terminal 后，新的 cancel 返回 `cancelled=false`
8. callback 只由成功领取 terminal 的路径投递一次
9. execution owner 已不存在时，仍清理 request record，但不向失效 JS session 投递 callback
10. 所有路径都必须从 active map 移除，不能留下只能等待 bridge close 的 entry

需要使用 OkHttp `EventListener` 或等价的单请求 tracker 记录请求阶段。只记录时间、阶段、request ID、
HTTP 状态和受限 provider request ID；不记录 query、答案、Key、Authorization、Cookie、完整 headers
或响应正文。

## 9.5 Admission 与预算设计

### 唯一 admission controller

新增 `OpenAIHostedWebSearchAdmissionController`，替代当前两个全局 registry 在本插件执行链中的直接
使用。

它负责：

- 插件 RPM
- 最大并发
- FIFO 等待队列
- queue timeout
- 请求取消
- 配置热变更
- active、queued 和 wait elapsed 诊断

配置更新不能创建一套并行的新 gate。controller 保持稳定实例：

- active count 不因配置变化清零
- 降低并发后，新请求等待到 active count 低于新上限
- 提高并发后，按 FIFO 唤醒可进入的请求
- RPM 变化保留当前窗口中仍有效的 timestamp
- 被取消的 waiter 从队列精确移除
- queue timeout 不创建 HTTP call

### 预算

正式实现采用两类用户可见预算：

```text
OPENAI_WEB_SEARCH_QUEUE_TIMEOUT_SECONDS
OPENAI_WEB_SEARCH_TIMEOUT_SECONDS
```

第一项只控制 admission 等待，建议默认 `60` 秒，范围 `1..300`。到期返回
`QUEUE_TIMEOUT`，阶段为 `rate_limit` 或 `concurrency`，提交状态固定为 `NOT_SENT`。

第二项控制单次同步 HTTP call，建议默认从 `60` 调整为 `300` 秒，范围保持 `1..300`。内部阶段预算
不再四项相同：

```text
call timeout = 配置的 HTTP 总预算
connect timeout = 不超过 30s
write timeout = 不超过 60s
read timeout = 不超过 HTTP 总预算
```

这不是只增大旧值。实施必须同时完成：

- timeout 分类
- queue 与 HTTP 分离
- phase 诊断
- lifecycle 原子终态
- 受控测试

超过五分钟的搜索不继续扩大同步 call 上限，转入后续 background 里程碑。

### 错误合同

新增或调整：

| code | 含义 | submission state |
| --- | --- | --- |
| `QUEUE_TIMEOUT` | RPM 或 concurrency 等待到期 | `NOT_SENT` |
| `REQUEST_TIMEOUT` | connect/write/read/call timeout | 由 tracker 决定 |
| `REQUEST_CANCELLED` | 用户、聊天、execution owner 或 bridge 显式取消 | 由 tracker 决定 |
| `NETWORK_FAILURE` | 非 timeout 的传输失败 | 由 tracker 决定 |

错误 envelope 增加脱敏字段：

```text
phase
cancel_owner
submission_state
elapsed_ms
configured_timeout_ms
queue_wait_ms
provider_request_id
```

`retryable` 当前没有唯一语义，却可能诱导未来加入自动 POST。revision `6` 删除该字段。是否允许用户
手动发起新请求由 UI 根据错误 code 和 `submission_state` 解释，宿主仍不自动重试。

## 9.6 配置架构：只保留 `PACKAGE_ENV`

项目权威文件明确 Kiyori 尚未有用户发布版本。当前 `MODEL_CONFIG`：

- 要求正常 UI 不显示的内部 UUID
- 需要第二个精确模型字段
- 叠加模型配置的 Key、headers、RPM 与并发
- 配置删除或模型变更时不参与绑定协调
- 扩大 resolver、状态、测试和文档复杂度

正式实现完整删除：

```text
OPENAI_WEB_SEARCH_CONFIG_SOURCE
OPENAI_WEB_SEARCH_MODEL_CONFIG_ID
OpenAIHostedWebSearchConfigSource
compileModelConfig()
OpenAIHostedWebSearchModelConfigKeySelector
modelConfigId
modelConfigMaxConcurrentRequests
modelConfigRequestsPerMinute
modelConfigKeySelectionMutex
MODEL_CONFIG_NOT_FOUND
MODEL_NOT_IN_CONFIG
MODEL_CONFIG 专用测试与文档
```

保留：

```text
OPENAI_WEB_SEARCH_PROVIDER_CONTRACT
OPENAI_WEB_SEARCH_RESPONSES_ENDPOINT
OPENAI_WEB_SEARCH_MODEL
OPENAI_WEB_SEARCH_API_KEY
OPENAI_WEB_SEARCH_AUTH_HEADER_NAME
OPENAI_WEB_SEARCH_AUTH_SCHEME
OPENAI_WEB_SEARCH_EXTRA_HEADERS_JSON
搜索参数、位置、域名、reasoning、用量与 admission 配置
```

删除后：

- `ToolPkgHostEnvironmentRepository` 是搜索配置唯一持久化 owner
- API Key 继续是 `PACKAGE + HOST_SERVICE + sensitive`
- JavaScript 仍不能读取 Key、endpoint 或完整 headers
- 主聊天当前 Provider、配置和模型继续不影响搜索插件
- compatibility fingerprint 不再包含 config source 或 model config ID
- compatibility record 继续包含 endpoint、model、credential digest、header names 和 schema revision

### 设置 UI

插件 Compose DSL 设置页不接收秘密。它只：

- 展示 readiness
- 展示 endpoint host、安全模型名、contract、credential revision、auth classification
- 执行本地校验
- 执行明确的付费 compatibility probe
- 提供宿主固定动作打开该插件的原生环境变量编辑器

原生编辑器负责 endpoint、Key、auth 和 headers。宿主打开动作必须固定绑定
`com.kiyori.openai_web_search`，不能接受 JavaScript 提交任意 package ID 或变量名。

readiness 至少包含：

| 检查项 | 状态 |
| --- | --- |
| provider contract | `official` / `relay` / `invalid` |
| endpoint | `missing` / `invalid` / `ready` |
| model | `missing` / `not_allowed` / `ready` |
| credential | `missing` / `ready` |
| auth | `invalid` / `ready` |
| extra headers | `invalid` / `ready` |
| search options | `invalid` / `ready` |
| admission | `invalid` / `ready` |
| compatibility | `not_required` / `missing` / `stale` / `failed` / `valid` |

状态页必须总能返回 readiness，不能只有完整 resolve 成功后才有内容。

## 9.7 Provider 设置 UI

四种持久化枚举保持不变：

```text
OPENAI
OPENAI_GENERIC
OPENAI_RESPONSES
OPENAI_RESPONSES_GENERIC
```

只调整用户可见名称：

| 枚举 | 建议名称 |
| --- | --- |
| `OPENAI` | OpenAI Chat Completions（官方） |
| `OPENAI_GENERIC` | OpenAI Chat Completions（兼容端点） |
| `OPENAI_RESPONSES` | OpenAI Responses（官方） |
| `OPENAI_RESPONSES_GENERIC` | OpenAI Responses（兼容端点） |

Provider 对话框按协议分组：

```text
OpenAI Chat Completions
	官方
	兼容端点

OpenAI Responses
	官方
	兼容端点
```

已选值摘要改为纵向：

```text
API 提供商
OpenAI Responses（兼容端点）
Responses API · 自定义 endpoint
```

完整 Provider 名称允许两行。验收覆盖：

- 320dp 窄屏
- 字体缩放
- 中文与英文
- 动态 ToolPkg Provider 名称
- TalkBack 读取完整名称和协议说明

## 9.8 搜索结果卡片

### 层级

```text
L0 消息级工具组
	工具调用或思考与工具

L1 搜索结果摘要
	成功/失败、查询摘要、来源数、动作数、耗时、用量摘要

L2 答案
	带真实 citation marker 的答案

L3 证据分区
	来源
	搜索轨迹
	诊断与用量
```

### 自动状态

- 单个 `openai_web_search:search` 在 `READ_ONLY`、`ALL` 和 `FULL` 模式下也建立 L0 工具组
- 流式消息尾部仍按现有规则展开 L0
- 消息完成或用户取消并落为静态消息后，L0 自动收起
- 用户已经操作 L0 时尊重 `userOverride`
- 用户重新展开 L0 时只看到 L1 摘要
- L1 默认收起；展开后显示 L2
- 来源、轨迹、诊断三个 L3 分区默认分别收起
- failure card 使用同一层级，不另建第二套 UI

### 状态 owner

新增纯状态模型，候选名称：

```text
OpenAIWebSearchResultPresentationState
OpenAIWebSearchResultPresentationPolicy
```

状态 key 包含：

```text
rendererId
message stable key
requestId
schema revision
```

旋转、列表回收和消息重组不能把一个搜索结果的展开状态复用到另一个 request。

### 来源与轨迹

- 来源标题始终显示总数量
- 收起时不组合所有来源行
- 展开后保留完整证据，不截断数据
- 来源很多时按固定批次逐步显示，继续展开不改变原始顺序
- URL 行以 title 和 host 为主，完整 URL 可选择和打开
- structured feed 使用独立类型和不可点击语义
- search、open_page、find_in_page 按动作类型显示图标和主字段
- search actions 保留原始执行顺序

### warning

至少分成：

```text
info
warning
error
```

`CITATION_NOT_IN_ACTION_SOURCES` 与 `ACTION_SOURCES_MISSING` 属于 audit channel 差异，不使用
error 色。citation span、URL 或 schema 无效才进入 error。

## 9.9 Evidence parser 可观察性

将：

```text
parseOrNull()
```

改为：

```text
NotApplicable
Parsed(evidence)
Invalid(code, sanitizedSummary)
```

`Invalid` 路径：

- 保留通用工具结果入口
- 显示“搜索结果卡解析失败”的诊断摘要
- 记录一次有界、脱敏日志
- 包含 schema revision、错误类别、request ID 是否存在和字段名
- 不记录完整 query、answer、sources JSON 或用户内容

测试必须证明目标工具 schema 回归不会静默消失。

## 9.10 `User-Agent` 与 headers

本轮不修改 `User-Agent` 来处理取消或限流。

正式规则：

- 不模拟 Codex Desktop、浏览器或其他产品
- 不把 `User-Agent` 变化加入取消修复验收
- 将 `user-agent` 加入 extra headers 禁止覆盖名单，避免 JSON 配置伪造客户端身份
- 保持认证 Header、hop-by-hop Header、`Host`、`Content-Length` 等现有限制
- relay 将来若提供正式、可核实的 UA 要求，另行评审专用字段与兼容探测

OpenAI 官方 Web Search、Streaming、Background 与 Responses cancel 文档没有要求客户端模拟
Codex Desktop。

## 9.11 流式与 background 后续里程碑

当前同步非流式实现保持：

```text
stream = false
background omitted
store = false
```

原因：

- 当前根因是 timeout 分类、预算、admission 和 lifecycle
- SSE 只改变结果传输方式，不自动提供可恢复执行
- 当前 parser、ToolPkg callback、XML 持久化和 evidence card 都以完整 Responses object 为合同
- relay 的流式 citation 与 action sources 兼容仍需独立矩阵

后续 background 里程碑必须整体设计：

```text
background = true
response ID 持久化
queued/in_progress/terminal 轮询
服务端 cancel endpoint
可选 stream=true
sequence_number cursor
断线续流
ToolPkg privateData 状态
聊天消息 pending/final 所有权
应用重启恢复
relay capability probe
计费与隐私说明
```

同步搜索超过五分钟的需求进入该里程碑，不继续扩大当前 call timeout。

## 9.12 候选文件

核心执行：

```text
app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/
	OpenAIHostedWebSearchModels.kt
	OpenAIHostedWebSearchBindingResolver.kt
	OpenAIHostedWebSearchPolicy.kt
	OpenAIHostedWebSearchGateway.kt
	ToolPkgOpenAIWebSearchBridge.kt
	RequestConcurrencyRegistry.kt
	RateLimiterRegistry.kt
	SlidingWindowRateLimiter.kt
```

优先新增插件专用 admission 与 lifecycle 文件，不把其他 Provider 的通用 limiter 行为一起重构。

ToolPkg：

```text
examples/openai_web_search/
	manifest.json
	src/shared.ts
	src/ui/index.ui.ts
	src/packages/openai_web_search.ts
```

UI：

```text
app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/part/
	ThinkToolsXmlNodeGrouper.kt
	CustomXmlRenderer.kt
	OpenAIWebSearchToolResultDisplay.kt

app/src/main/java/com/ai/assistance/operit/ui/features/settings/sections/
	ModelApiSettingsSection.kt
```

文档：

```text
CONTEXT.md
docs/TODO/README.md
docs/TODO/openai_hosted_web_search/
docs/doc-src/architecture/openai_hosted_web_search.md
```

正式实现完成前，`CONTEXT.md` 和正式架构文档继续描述当前 revision `5` 实现；编码轮必须与实际
源码同时更新为 revision `6`。

## 9.13 测试矩阵

### Gateway 与 timeout

- call timeout 产生 `InterruptedIOException("timeout")` 且 `call.isCanceled() == true`，最终仍为
  `REQUEST_TIMEOUT`
- connect timeout、write timeout、read timeout
- 显式 `gateway.cancel()` 为 `REQUEST_CANCELLED`
- 父协程取消为 `REQUEST_CANCELLED`
- request body 已发送但 response headers 未到达时，`submission_state=SUBMISSION_UNKNOWN`
- response headers 已到达后 body read timeout，`submission_state=RESPONSE_STARTED`
- 每个测试只产生一个 POST
- redirect、retryOnConnectionFailure 和自动第二请求继续为禁用

### Admission

- FIFO
- RPM wait
- concurrency wait
- queue timeout 不创建 HTTP call
- waiter cancel 精确移除
- 并发上限提高和降低
- RPM 热变更保留有效 timestamp
- 配置变化期间 active 数不超过当前 controller 规则

### Lifecycle

- success 先 settle，后 cancel 返回 false
- cancel 先领取，worker 完成后只投递取消
- 同时 cancel 只执行一次取消动作
- callback 投递一次
- owner 结束后 record 清理且不向失效 session 投递
- 同一 execution 的多个 request 独立完成
- owner 结束取消该 owner 的全部 active request，不影响其他 call

### 配置

- manifest 不再包含 config source 与 model config ID
- host-service 环境变量数量与名称精确锁定
- PACKAGE_ENV official 与 relay strict
- Key 不可被 JavaScript 读取
- readiness 在字段失败时仍返回结构化结果
- compatibility fingerprint revision `6`
- revision `5` probe record 不被 revision `6` 复用

### UI

- 单搜索工具在常用模式下建立 L0 group
- 静态完成后自动收起
- `userOverride` 不被自动状态覆盖
- L1/L2/L3 状态转换纯 Kotlin 测试
- source/action/diagnostics 默认收起
- request ID 改变后状态不串用
- 320dp、字体放大和长 Provider 名称
- parser `Invalid` 路由可见且日志脱敏
- structured feed、零 URL citation、citation-only 和大量来源

### ToolPkg

- TypeScript typecheck
- deterministic dist
- manifest contract
- generated `.toolpkg` 与 APK 内资产逐字节一致
- 制品不含 Key、endpoint 私有值、TypeScript 源码或缓存

## 9.14 实施顺序

### M1 生命周期与错误分类

1. 建立 lifecycle、phase、submission state
2. 修复 timeout 分类
3. 原子完成/取消
4. 增加 MockWebServer 与竞态测试

完成信号：复杂请求 timeout 不再显示 `REQUEST_CANCELLED`，取消与完成 callback 唯一。

### M2 Admission 与预算

1. 插件专用 admission controller
2. queue timeout
3. HTTP 阶段预算
4. 热变更测试和诊断

完成信号：并发、排队和限速分别可观察，配置变化不产生平行 gate。

### M3 单一 `PACKAGE_ENV`

1. 删除 `MODEL_CONFIG`
2. 更新 manifest、resolver、gateway、status、fingerprint 和测试
3. readiness
4. 原生环境编辑器入口

完成信号：用户不再看到或填写 modelConfigId，所有配置路径在插件自身入口可发现。

### M4 UI 与可观察性

1. 单搜索工具分组
2. 分层结果卡
3. parser 显式状态
4. Provider 摘要与名称
5. warning 分级

完成信号：完成结果自动收起，逐级展开清晰，Provider 全名可读，schema 回归可诊断。

### M5 文档与封板

1. `CONTEXT.md`
2. 正式架构文档
3. 专项 TODO 状态
4. 定向测试
5. formal readiness
6. `git diff --check`
7. 项目规定的 Debug APK 构建与制品核验

设备和真实 relay 验收仍单独保持待验证，不能由本地构建代替。

## 9.15 非目标

- 不自动重试 Responses POST
- 不切换 endpoint、模型、Key、认证、Provider 或搜索后端
- 不恢复已删除的 `MODEL_CONFIG`
- 不把当前聊天模型作为搜索配置源
- 不在 Compose DSL 中接收或显示 API Key
- 不模拟 Codex Desktop `User-Agent`
- 不在本里程碑引入 SSE、background、轮询或断线续流
- 不删除四种全局 OpenAI Provider 枚举
- 不调用真实付费 API 完成自动测试

## 9.16 正式编码前门禁

开始编码前重新确认：

- `main` 与 HEAD
- 当前工作树已有文档改动
- 用户仍授权修改生产源码
- 不提交、不推送边界是否仍保持
- 不调用真实 relay、设备或模拟器边界是否仍保持
- 项目正式开发准备门禁

当前最短下一步是等待用户明确授权进入 M1 至 M4 的生产实现。本轮文档完成不等于实现完成。
