# 5. 中转站与环境变量配置

## 5.1 结论

用户通过中转站使用 GPT-5.6 Web Search 时，endpoint、模型名、API Key、认证方式、额外请求头、
思考强度和搜索行为都需要可配置。

这些配置属于 OpenAI Web Search ToolPkg 插件，不属于当前主聊天模型。正式方案支持两种显式配置
来源：

```text
PACKAGE_ENV
	插件使用独立的包级环境变量

MODEL_CONFIG
	插件绑定一个固定的 Kiyori Responses 模型配置
```

两种来源必须二选一。一次执行不能从两边拼接字段，也不能因为所选来源缺少某个值而读取另一来源。

默认推荐 `PACKAGE_ENV`，因为它最符合“搜索服务与主聊天模型独立”的产品语义，也便于为中转站单独
配置账号、预算、模型和参数。

## 5.2 为什么它仍然是 ToolPkg 插件

环境变量只是配置载体，不决定产品类型。

正式产品形态是：

```text
包管理
	└─ 插件
		└─ OpenAI Web Search
			├─ ToolPkg 容器主开关
			├─ 设置页
			├─ 可选的输入菜单快捷开关
			└─ openai_web_search subpackage
				└─ openai_web_search:search
```

它与下列现有 ToolPkg 同级：

- `APK 逆向工具包`
- `楼层限制器`
- `深度搜索`
- `额外信息注入`

内部仍使用 TypeScript 或 JavaScript 编写 ToolPkg 入口和 subpackage 包描述，但这不把它归类为
“脚本包”。普通 `tavily.js` 继续属于脚本包标签，两者只在“都能向主模型提供工具”这一层相似。

## 5.3 启停语义

第一版只需要两个权威开关：

1. ToolPkg 容器主开关决定插件是否运行
2. `openai_web_search` subpackage 开关决定搜索工具是否暴露给模型

建议：

```text
container.enabled_by_default = false
subpackage.enabled_by_default = true
```

这样用户安装后必须主动开启插件；开启容器后，搜索工具按插件声明立即可用，不需要再寻找第二个
默认关闭开关。

输入菜单快捷开关不是第一版必需条件。如果后续增加，它只能表示“当前会话是否允许暴露搜索工具”，
不能复制包管理主开关，也不能成为第三个全局启停状态 owner。

## 5.4 配置来源

### `PACKAGE_ENV`

适合：

- 使用中转站
- 为搜索单独准备 Key
- 搜索模型与主聊天模型来自不同账号或服务
- 需要可迁移、可复制的插件配置名称

该来源读取插件自己的包级环境变量，由宿主 Gateway 发起请求。

### `MODEL_CONFIG`

适合：

- 已经在 Kiyori 中建立了可用的 `OPENAI_RESPONSES`
- 已经建立了经过 Web Search 兼容探测的 `OPENAI_RESPONSES_GENERIC`
- 希望共用 endpoint、Key 池、认证和自定义请求头

该来源保存：

```text
modelConfigId
exactModelName
```

宿主按固定 ID 读取配置，不自动跟随当前聊天 Provider、当前聊天配置、功能模型绑定或模型列表索引。

从模型配置中只复用传输身份：

- Responses endpoint
- API Key 或 Key 池
- 认证相关请求头
- 用户明确设置的额外请求头
- 配置级并发与每分钟请求上限

不复用聊天请求语义：

- `customParameters`
- 当前聊天 system prompt
- 聊天历史
- reasoning replay
- background response
- Prompt Cache
- Tool Search
- function tools
- sequence resume

搜索专用参数仍由插件配置持有。

## 5.5 建议环境变量

### 配置来源

```text
OPENAI_WEB_SEARCH_CONFIG_SOURCE
OPENAI_WEB_SEARCH_MODEL_CONFIG_ID
```

允许值：

```text
OPENAI_WEB_SEARCH_CONFIG_SOURCE=PACKAGE_ENV
OPENAI_WEB_SEARCH_CONFIG_SOURCE=MODEL_CONFIG
```

`MODEL_CONFIG` 要求 `OPENAI_WEB_SEARCH_MODEL_CONFIG_ID` 非空。

### endpoint、模型与认证

```text
OPENAI_WEB_SEARCH_RESPONSES_ENDPOINT
OPENAI_WEB_SEARCH_MODEL
OPENAI_WEB_SEARCH_API_KEY
OPENAI_WEB_SEARCH_AUTH_HEADER_NAME
OPENAI_WEB_SEARCH_AUTH_SCHEME
OPENAI_WEB_SEARCH_EXTRA_HEADERS_JSON
```

要求：

- endpoint 是完整 Responses URL，不接受只包含域名的模糊 base URL
- 第一版只接受 HTTPS
- endpoint 不允许 userinfo、fragment 或重定向
- 模型名保存并发送精确字符串，不改写中转站别名
- `AUTH_HEADER_NAME` 默认 `Authorization`
- `AUTH_SCHEME` 默认 `Bearer`
- `EXTRA_HEADERS_JSON` 必须是字符串到字符串的 JSON object
- extra headers 不能覆盖独立认证字段
- 拒绝 `Host`、`Content-Length`、`Connection`、`Transfer-Encoding` 和其他 hop-by-hop header
- 请求发生重定向时直接失败，不能把 Key 转发到新的 origin
- Codex-compatible 中转如果要求 actor 授权，可以显式配置：

```json
{
  "x-openai-actor-authorization": "enabled"
}
```

该 header 不是所有 OpenAI Responses endpoint 的通用必填项，宿主不按域名自动添加。配置是否需要
它由中转站协议决定；header 名属于 compatibility fingerprint，改变后必须重新探测。

2026-08-12 对 Pixel 与 Sub2api 的 revision `4` 根因矩阵没有使用该 header。两个站点在省略空
`blocked_domains` 后均可通过非流式 Web Search 请求，因此不能把 actor header 当作这两个站点的
默认修复项。

### 思考与输出

```text
OPENAI_WEB_SEARCH_REASONING_EFFORT
OPENAI_WEB_SEARCH_MAX_OUTPUT_TOKENS
OPENAI_WEB_SEARCH_RETURN_TOKEN_BUDGET
OPENAI_WEB_SEARCH_ADDITIONAL_INSTRUCTIONS
```

`OPENAI_WEB_SEARCH_REASONING_EFFORT` 允许：

```text
omit
none
low
medium
high
xhigh
max
```

映射：

```text
omit
	不发送 reasoning

其他值
	reasoning.effort = 对应值
```

建议默认 `medium`。中转站不支持该字段时，用户应将配置显式改为 `omit` 并重新执行兼容探测。
Gateway 不自动删除字段后提交第二次请求。

`OPENAI_WEB_SEARCH_MAX_OUTPUT_TOKENS` 是可选正整数。宿主需要设置产品级最大值，防止异常配置产生
不可控成本或响应体。

OpenAI 当前 Web Search 文档中 `return_token_budget` 的允许值是：

```text
default
unlimited
```

所以：

```text
OPENAI_WEB_SEARCH_RETURN_TOKEN_BUDGET=default
OPENAI_WEB_SEARCH_RETURN_TOKEN_BUDGET=unlimited
```

不能把它设计为 `4096..131072` 数值范围。

`OPENAI_WEB_SEARCH_ADDITIONAL_INSTRUCTIONS` 只能追加到宿主固定安全指令之后，不能替换安全前缀。

### 搜索行为

```text
OPENAI_WEB_SEARCH_EXTERNAL_WEB_ACCESS
OPENAI_WEB_SEARCH_CONTEXT_SIZE
OPENAI_WEB_SEARCH_ALLOWED_DOMAINS_JSON
OPENAI_WEB_SEARCH_BLOCKED_DOMAINS_JSON
OPENAI_WEB_SEARCH_LOCATION_JSON
```

允许值：

```text
OPENAI_WEB_SEARCH_EXTERNAL_WEB_ACCESS=true | false
OPENAI_WEB_SEARCH_CONTEXT_SIZE=low | medium | high
```

语义：

```text
true
	对应 live 搜索

false
	对应 indexed 搜索
```

第一版产品预设为 `true`，但变量保留显式配置能力。

域名 JSON 是字符串数组，只接受不带 scheme、port、path、query 和 fragment 的 host。allowed 和
blocked 不能出现同一 host。

位置 JSON 只接受 OpenAI `approximate` 结构：

```json
{
  "type": "approximate",
  "country": "US",
  "city": "San Francisco",
  "region": "California",
  "timezone": "America/Los_Angeles"
}
```

默认不发送位置，也不自动读取 Android 定位权限、GPS、网络定位或设备时区。

### 执行控制

```text
OPENAI_WEB_SEARCH_TIMEOUT_SECONDS
OPENAI_WEB_SEARCH_MAX_CONCURRENT_REQUESTS
OPENAI_WEB_SEARCH_REQUESTS_PER_MINUTE
```

建议默认：

```text
OPENAI_WEB_SEARCH_TIMEOUT_SECONDS=60
OPENAI_WEB_SEARCH_MAX_CONCURRENT_REQUESTS=1
OPENAI_WEB_SEARCH_REQUESTS_PER_MINUTE=0
```

`0` 只在每分钟请求上限中表示插件不额外限流。若 `MODEL_CONFIG` 自身有更严格限制，宿主同时执行
两组限制。

## 5.6 不能开放覆盖的字段

以下字段由宿主固定：

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

不提供：

```text
OPENAI_WEB_SEARCH_REQUEST_BODY_JSON
OPENAI_WEB_SEARCH_TOOLS_JSON
OPENAI_WEB_SEARCH_TOOL_CHOICE
OPENAI_WEB_SEARCH_INCLUDE_JSON
OPENAI_WEB_SEARCH_SYSTEM_PROMPT
OPENAI_WEB_SEARCH_STORE
OPENAI_WEB_SEARCH_STREAM
```

否则用户配置或恶意 ToolPkg 可以移除 Web Search、来源展开、安全指令或非存储要求，使插件无法
证明自己执行了真实搜索。

## 5.7 当前环境变量系统的缺口

当前 `EnvVar` 只有：

```text
name
description
required
defaultValue
```

当前 `EnvPreferences`：

- 按全局变量名存储
- 没有 ToolPkg ID namespace
- 没有 sensitive 标志
- 没有 password 输入类型
- 没有宿主专用 consumer
- `getEnv()` 可以按已知名称读取值

因此，直接把 `OPENAI_WEB_SEARCH_API_KEY` 放进现有环境变量系统，只能实现用途和账单分离，不能
实现插件级秘密隔离。

正式版需要扩展环境变量声明：

```text
scope = global | package
sensitive = true | false
consumer = javascript | host_service
inputType = text | password | enum | boolean | number | json
allowedValues
```

Web Search Key 的声明必须是：

```text
scope = package
sensitive = true
consumer = host_service
inputType = password
```

endpoint、模型和普通参数可以是：

```text
scope = package
sensitive = false
consumer = host_service
```

这些值仍然显示在包管理的环境变量界面中，但读取路径是：

```text
ToolPkg host identity
	└─ ToolPkgHostEnvironmentRepository
		└─ OpenAIHostedWebSearchGateway
```

不能通过：

```text
ToolPkg JavaScript
	└─ getEnv("OPENAI_WEB_SEARCH_API_KEY")
```

## 5.8 ToolPkg manifest 配置声明

当前 ToolPkg manifest 没有容器级环境变量声明。为使插件配置归属于 ToolPkg 容器，而不是伪装成
普通 subpackage 脚本包，建议扩展 manifest：

```json
{
  "schema_version": 1,
  "toolpkg_id": "com.kiyori.openai_web_search",
  "version": "0.1.0",
  "main": "dist/main.js",
  "enabled_by_default": false,
  "environment": [
    {
      "name": "OPENAI_WEB_SEARCH_API_KEY",
      "required": false,
      "scope": "package",
      "sensitive": true,
      "consumer": "host_service",
      "input_type": "password"
    }
  ],
  "subpackages": [
    {
      "id": "openai_web_search",
      "entry": "dist/packages/openai_web_search.js"
    }
  ]
}
```

`API_KEY.required=false` 表示 manifest 静态层不能在 `MODEL_CONFIG` 模式下误判缺失；宿主 policy
必须在 `PACKAGE_ENV` 模式下把它作为条件必填项。

这是新增 schema 能力，开发阶段需要同步：

- manifest parser
- artifact scanner
- package manager facade
- 环境变量抽屉
- host-only repository
- ToolPkg execution context
- TypeScript 类型
- 架构门禁和测试

现有普通脚本包的全局 `EnvPreferences` 合同保持不变。Web Search 插件不借本任务重写全部旧脚本包。

## 5.9 中转站兼容等级

支持两种明确 contract：

```text
RESPONSES_HOSTED_OFFICIAL
RESPONSES_RELAY_STRICT
```

### `RESPONSES_HOSTED_OFFICIAL`

要求：

- provider 是 `OPENAI_RESPONSES`
- endpoint 精确属于官方 Responses
- 模型位于第一版 GPT-5.6 allowlist

### `RESPONSES_RELAY_STRICT`

要求：

- 配置来源是 `PACKAGE_ENV`，或固定 `MODEL_CONFIG` 的 provider 是
  `OPENAI_RESPONSES` / `OPENAI_RESPONSES_GENERIC`
- endpoint 使用 HTTPS
- 用户显式执行一次可能产生费用的兼容探测
- 探测使用当前 endpoint、模型、认证、headers 和 reasoning 设置

通过条件：

1. HTTP 请求成功
2. 返回 Responses object schema
3. 至少一个 `web_search_call`
4. 非空 assistant output text
5. 至少一个 `url_citation`
6. usage 可解析，或明确记录缺失 warning

如果中转站只返回普通文本，或自行执行其他搜索后伪装成模型回答，不能标记为
`RESPONSES_RELAY_STRICT`。

OpenAI 官方说明完整 sources 的数量通常多于 citation，并可能包含无 URL 的
`oai-sports`、`oai-weather` 或 `oai-finance` 实时第三方 feed。parser 必须保留这些 feed，
也必须继续审计未知或损坏条目。relay 的命名 `{type:"api", name:"..."}` 结构化 feed 也可以作为
普通搜索结果保留，但不能满足固定公开网页 compatibility probe 的 URL citation 门禁。

普通搜索的 evidence mode：

```text
url_citations_and_action_sources
url_citations
action_sources
structured_feeds
```

前两种只有在真实 annotation 存在时才包含 citation span 和 `[S1]`。`action.sources` 与
`url_citation` 是独立证据通道，不要求 URL 集合严格对齐；只有一种时不伪造另一种。两者同时存在
但不完全覆盖时，结果继续成功并返回结构化差异诊断。

探测结果按下列指纹保存：

```text
endpoint origin + path
model
auth mode
non-secret header names
reasoning effort
external web access
response schema revision
```

任何指纹字段改变后，状态恢复为“待探测”。宿主不能根据旧探测结果推断新 endpoint 或新模型仍然
兼容。

当前 response schema revision 为 `5`。revision `4` 及更早的成功记录和失败记录都不能继续投影
为当前绑定状态。探测结果按 fingerprint digest 保存为最多 `16` 条的 record-set；同一 digest 的
成功和失败互斥，不同 Key 的记录互不覆盖。探测失败必须保存当前指纹、时间、结构化错误码、可选
HTTP 状态和脱敏消息，让 UI 明确区分 `missing`、`stale` 与 `failed`。

## 5.10 是否与主模型共用 API

默认不共用。

推荐：

```text
主聊天模型配置
	DeepSeek / Gemini / OpenAI / local

OpenAI Web Search 插件配置
	独立 endpoint + 独立 Key + 固定 GPT-5.6 搜索模型
```

允许显式共用：

```text
OPENAI_WEB_SEARCH_CONFIG_SOURCE=MODEL_CONFIG
OPENAI_WEB_SEARCH_MODEL_CONFIG_ID=<固定配置 ID>
OPENAI_WEB_SEARCH_MODEL=<精确模型名>
```

共用的是固定配置的 endpoint、Key 和传输身份，不是“当前主模型”。用户切换聊天模型或聊天配置
不能改变搜索插件的真实计费账号。

共用时设置页必须显示：

- 正在共用哪个 model config
- 正在共用其预算、限额和 Key 池
- 搜索专用 reasoning、Web Search 和输出参数仍由插件持有
- 删除或修改该 model config 会让插件进入配置无效状态

## 5.11 推荐第一版产品预设

```text
CONFIG_SOURCE=PACKAGE_ENV
MODEL=gpt-5.6-luna
REASONING_EFFORT=medium
EXTERNAL_WEB_ACCESS=true
CONTEXT_SIZE=medium
RETURN_TOKEN_BUDGET=default
TIMEOUT_SECONDS=60
MAX_CONCURRENT_REQUESTS=1
REQUESTS_PER_MINUTE=0
LOCATION=disabled
```

endpoint 和 Key 不提供公开默认值，必须由用户填写。

中转站使用自定义模型别名时，允许覆盖 `MODEL`。第一版产品 UI 优先推荐 GPT-5.6 family，但不能
通过字符串前缀判断中转站是否真实支持 Web Search，最终以严格兼容探测为准。

## 5.12 开发顺序

1. 扩展 ToolPkg 容器级环境变量 schema
2. 建立包级、敏感、host-only 环境变量存储和读取边界
3. 实现配置来源解析和强类型校验
4. 实现 official 与 relay strict contract
5. 实现付费兼容探测
6. 实现搜索 Gateway、parser 和 evidence
7. 创建 ToolPkg 设置页和 subpackage
8. 完成 mock、构建、真实 API 和设备分层验收

不能先创建一个通过 `getEnv()` 读取 Key 并直接联网的 ToolPkg，再把它作为正式版本发布。
