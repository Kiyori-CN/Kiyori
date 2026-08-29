# 5. 中转站与环境变量配置

## 5.1 当前合同

revision `7` 只使用 `com.kiyori.openai_web_search` 的 package-scoped host-service 环境变量。
当前响应 schema revision 为 `7`；revision `6` 及更早的 record-set、manifest 和 response codec
只作为历史证据，不能投影为当前绑定状态。

```text
ToolPkgHostEnvironmentRepository
	└─ OpenAIHostedWebSearchBindingCompiler.compilePackageEnvironment()
		└─ OpenAIHostedWebSearchPolicy
			└─ OpenAIHostedWebSearchGateway
```

插件不读取当前聊天 Provider、模型配置、模型名、Key 池或自定义参数，也不保存模型配置引用。
Kiyori 没有用户发布版本，因此旧 `MODEL_CONFIG` 专项字段、解析分支、Key selector、游标和错误码已
完整删除。

这不影响全局四种 OpenAI 聊天 Provider。它们仍分别表示官方或兼容端点的 Chat Completions 与
Responses 协议，不是 Web Search 的配置来源。

## 5.2 产品和启停语义

环境变量只是配置载体，不改变产品分类。

```text
包管理
	└─ 插件
		└─ OpenAI Web Search
			├─ ToolPkg 容器主开关
			├─ 设置页
			└─ openai_web_search subpackage
				└─ openai_web_search:search
```

- container 默认关闭
- subpackage 在 container 启用后默认开启
- 不增加第三个全局启停 owner
- 搜索失败不切换到其他 endpoint、模型、Key、Provider 或搜索后端

## 5.3 原生配置入口

设置页通过固定无参数服务打开原生环境变量编辑器：

```ts
ToolPkg.services.openAIWebSearch.openConfiguration()
```

宿主固定打开：

```text
com.kiyori.openai_web_search
```

只有绑定 ToolPkg 的 active `ui` runtime 可以调用。JavaScript 不能提交任意 package ID、变量名或
值；API Key 仍由原生宿主界面接收和保存。

## 5.4 二十个环境变量

### Provider、endpoint、模型和认证

```text
OPENAI_WEB_SEARCH_PROVIDER_CONTRACT
OPENAI_WEB_SEARCH_RESPONSES_ENDPOINT
OPENAI_WEB_SEARCH_MODEL
OPENAI_WEB_SEARCH_API_KEY
OPENAI_WEB_SEARCH_AUTH_HEADER_NAME
OPENAI_WEB_SEARCH_AUTH_SCHEME
OPENAI_WEB_SEARCH_EXTRA_HEADERS_JSON
```

`OPENAI_WEB_SEARCH_PROVIDER_CONTRACT` 允许：

```text
RESPONSES_HOSTED_OFFICIAL
RESPONSES_RELAY_STRICT
```

默认值：

```text
PROVIDER_CONTRACT=RESPONSES_HOSTED_OFFICIAL
MODEL=gpt-5.6-luna
AUTH_HEADER_NAME=Authorization
AUTH_SCHEME=Bearer
```

要求：

- endpoint 是完整 HTTPS Responses URL
- endpoint 不允许 userinfo、fragment 或重定向
- official contract 要求精确官方 Responses endpoint
- official model 必须位于 `gpt-5.6`、`gpt-5.6-sol`、`gpt-5.6-terra`、
  `gpt-5.6-luna` allowlist
- relay contract 允许自定义模型别名；兼容探测是可选的显式诊断，不阻断普通 search
- API Key 是 package-scoped、sensitive、password、host-service 值
- ToolPkg JavaScript 的 `getEnv()` 不能读取 host-service 值
- auth scheme 为空表示直接发送 Key，默认 `Bearer`
- extra headers 必须是字符串到字符串的 JSON object

extra headers 拒绝：

- 当前认证 Header
- `Authorization` 与 `Proxy-Authorization`
- `User-Agent`
- `Host`
- `Content-Length`
- `Connection`、`Transfer-Encoding` 和其他 hop-by-hop headers

Codex-compatible 中转若要求 `x-openai-actor-authorization`，用户可以按服务商合同显式配置。宿主
不按域名自动添加。

### 思考与输出

```text
OPENAI_WEB_SEARCH_REASONING_EFFORT
OPENAI_WEB_SEARCH_MAX_OUTPUT_TOKENS
OPENAI_WEB_SEARCH_RETURN_TOKEN_BUDGET
OPENAI_WEB_SEARCH_ADDITIONAL_INSTRUCTIONS
```

默认值：

```text
REASONING_EFFORT=medium
RETURN_TOKEN_BUDGET=default
```

`REASONING_EFFORT` 允许：

```text
omit
none
low
medium
high
xhigh
max
```

`RETURN_TOKEN_BUDGET` 允许：

```text
default
unlimited
```

`MAX_OUTPUT_TOKENS` 为空时不发送；设置时必须为正整数且不超过 `64000`。附加指令最多
`4000` 字符，并且只能追加在宿主固定安全指令之后。

### 搜索行为

```text
OPENAI_WEB_SEARCH_EXTERNAL_WEB_ACCESS
OPENAI_WEB_SEARCH_CONTEXT_SIZE
OPENAI_WEB_SEARCH_ALLOWED_DOMAINS_JSON
OPENAI_WEB_SEARCH_BLOCKED_DOMAINS_JSON
OPENAI_WEB_SEARCH_LOCATION_JSON
```

默认值：

```text
EXTERNAL_WEB_ACCESS=true
CONTEXT_SIZE=medium
```

- `true` 表示 live，`false` 表示 indexed
- context size 允许 `low`、`medium`、`high`
- allowed/blocked domains 是规范化域名 JSON 字符串数组
- 工具调用只能进一步收紧已配置域名范围
- 请求只序列化实际非空的 allowed 或 blocked 数组
- location 是可选 approximate JSON
- 只有工具调用明确启用 `use_configured_location` 时才发送已保存位置
- 宿主不自动读取 Android 定位权限、GPS、网络定位或设备时区

### Admission 与 HTTP 预算

```text
OPENAI_WEB_SEARCH_QUEUE_TIMEOUT_SECONDS
OPENAI_WEB_SEARCH_TIMEOUT_SECONDS
OPENAI_WEB_SEARCH_MAX_CONCURRENT_REQUESTS
OPENAI_WEB_SEARCH_REQUESTS_PER_MINUTE
```

默认值和范围：

| 字段 | 默认值 | 范围 |
| --- | ---: | ---: |
| `QUEUE_TIMEOUT_SECONDS` | `60` | `1..300` |
| `TIMEOUT_SECONDS` | `300` | `1..300` |
| `MAX_CONCURRENT_REQUESTS` | `1` | `1..8` |
| `REQUESTS_PER_MINUTE` | `0` | `0..600` |

`0` 只表示不设置插件级 RPM 上限。

普通 search 与 compatibility probe 共用同一个稳定 FIFO admission controller。配置变化原地更新
限制，不替换 gate。排队超时时：

- 返回 `QUEUE_TIMEOUT`
- `submission_state=not_sent`
- 记录 `queue_wait_ms`
- phase 是 `waiting_rate_limit` 或 `waiting_concurrency`
- 不创建 HTTP Call

取得许可后：

```text
call timeout    = TIMEOUT_SECONDS
read timeout    = TIMEOUT_SECONDS
connect timeout = min(TIMEOUT_SECONDS, 30)
write timeout   = min(TIMEOUT_SECONDS, 60)
```

OkHttp call timeout 在 cancellation 状态之前识别为 `REQUEST_TIMEOUT`。

## 5.5 宿主固定字段

以下请求字段不能由环境变量或工具调用覆盖：

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

不提供任意 request body、tools、tool choice、include、system prompt、store 或 stream JSON 注入口。
当前同步实现也不使用 background、SSE、轮询、sequence resume 或断线续流。

## 5.6 Readiness

`getStatus()` 和 `validateLocalConfiguration()` 固定投影九项 readiness：

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

每项包含：

```text
state
error_code
message
```

即使 endpoint、Key 或其他字段失败，状态页仍返回其他字段的结构化状态。公开摘要只包含 endpoint
host、模型、非秘密 Header 名、短 credential revision、认证分类、搜索设置、admission 设置和
compatibility 结果，不包含 Key、完整 endpoint、Authorization 值或自定义 scheme 原文。

## 5.7 Compatibility probe

`RESPONSES_RELAY_STRICT` 支持用户从插件设置 UI 显式执行可能产生费用的 probe，但普通 search 在
环境变量本地校验通过后即可直接调用，不要求预先探测。自动测试不得调用真实 endpoint。

通过条件：

1. HTTP success
2. Responses object schema
3. 至少一个 `web_search_call`
4. 非空 output text
5. 至少一个真实 `url_citation`
6. usage 可解析，或返回明确缺失 warning

probe 固定访问 `developers.openai.com` 的公开页面。时间、天气、体育和金融查询可能使用无 URL
结构化 feed，不是 URL evidence 探针。

指纹包含：

```text
endpoint
model
auth mode
不可逆 credential digest
non-secret header names
reasoning effort
external web access
response schema revision
```

当前 response schema revision 是 `7`。revision `6` 及更早 record-set 不能投影为当前状态。成功
与失败按 exact fingerprint digest 保存为最多十六条记录；同一 digest 互斥，不同凭据指纹互不
删除。

record-set 的成功、失败和清理写入使用同步提交，并在探测 callback 交付前确认提交结果。这样
设置页关闭后立即发生的进程退出不会把已成功的相同 fingerprint 探测留在未落盘状态。fingerprint
变化后用户若要刷新诊断记录仍需重新探测，但普通 search 不因缺少或过期记录而被阻断。

## 5.8 ToolPkg manifest

当前 manifest：

```text
toolpkg_id = com.kiyori.openai_web_search
version = 1.0.6
environment = 20
subpackage = 1
```

全部环境变量都是 `scope=package`、`consumer=host_service`。API Key 额外设置
`sensitive=true` 与 `input_type=password`。

生成归档只允许：

```text
manifest.json
dist/main.js
dist/shared.js
dist/packages/openai_web_search.js
dist/ui/index.ui.js
```

不得包含 TypeScript 源码、`.env`、缓存、凭据或私有 endpoint。

## 5.9 验证边界

revision `6` 的本地验证顺序、最终 ToolPkg hash 和 Debug APK 对账记录在
[实施、测试与验收](4_implementation_and_validation.md)。

本地 TypeScript、JVM、Kotlin、formal readiness、ToolPkg 和 APK 静态审计不能代替真实 relay、
目标设备、320dp、字体放大、TalkBack 或用户验收。

历史 `MODEL_CONFIG` 调查和删除决策见
[只读分析检查点](8_20260813_readonly_analysis_checkpoint.md) 与
[优化实施计划](9_20260813_optimization_implementation_plan.md)。
