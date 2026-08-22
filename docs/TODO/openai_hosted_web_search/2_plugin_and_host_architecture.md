# 2. 插件与宿主架构

## 2.1 设计目标

正式架构需要同时满足：

- 任意主聊天模型都能使用
- 搜索账号不受主聊天模型切换影响
- 产品上与其他 ToolPkg 插件同级
- 支持包管理安装、更新和启停
- 插件不接触原始 API Key
- 不复制现有 OpenAI Responses 实现
- 不把搜索注册为完整聊天 Provider
- 支持官方 OpenAI 和严格兼容中转站
- 只使用插件独立的 package-scoped host-service 环境变量
- 保留 ToolPkg 的安装、启停、UI 和包管理体验
- 失败时返回明确错误，不切换成其他搜索服务
- 搜索证据不会在主模型改写答案后失去来源关系

## 2.2 候选方案

### 方案 A：普通 JS 脚本包直连

```text
openai_web_search.js
	└─ getEnv("OPENAI_WEB_SEARCH_API_KEY")
		└─ OkHttp POST /v1/responses
```

优点：

- 与 `tavily_search.js` 结构接近
- 实现量小
- 适合验证请求和响应字段

问题：

- `EnvPreferences` 是全局按名称读取，不是包级秘密隔离
- JavaScript 可以直接读取和发送 Key
- 需要在脚本中重复实现 Responses 编译、解析和错误分类
- 无法自然复用 Kiyori 模型配置、Key 池、endpoint 校验和 usage
- 难以把 citation/source 投影到聊天持久化与 UI

结论：只适合开发原型，不作为正式交付。

### 方案 B：ToolPkg 内直接请求

ToolPkg 可以增加配置 UI、IPC 和 privateData，但它仍然要在 JavaScript 中拿到 Key 并发起 HTTP
请求。`privateData` 提供包级存储隔离，不等于 Android 凭据保险箱，而且插件自身仍然可以读取
其中内容。

结论：产品外壳更好，凭据和协议边界仍然不合格。

### 方案 C：注册 ToolPkg AI Provider

`ToolPkg.registerAiProvider` 的职责是：

- 列出模型
- 测试完整聊天连接
- 发送聊天消息
- 估算输入 token
- 作为主 `apiProviderType` 被选择

用户需要的是：

```text
任意主模型 + 一个 OpenAI 搜索工具
```

不是：

```text
把当前聊天切换成 OpenAI 搜索 Provider
```

结论：不采用。

### 方案 D：宿主 Gateway + ToolPkg

```text
ToolPkg
	├─ 配置 UI
	├─ subpackage 搜索工具
	└─ 只调用宿主的 package-bound 搜索桥

Kiyori host
	├─ 固定服务绑定
	├─ API Key
	├─ Responses Web Search gateway
	├─ 证据解析
	└─ usage、错误、取消和持久化
```

优点：

- Key 不进入 JavaScript
- 不复制协议
- 与 APK 逆向、楼层限制器、深度搜索和额外信息注入保持同一插件产品体验
- 可以给任意主模型使用
- 可以建立真实证据 sidecar
- 可以独立管理预算、并发和用量

结论：正式推荐方案。

### 方案 E：直接给当前 OpenAI 聊天请求增加 `web_search`

当当前聊天本身就是官方 OpenAI Responses + GPT-5.6 时，可以把 `web_search` 直接放入主请求。
这样减少一次 GPT 请求，并保留原生 annotations。

它无法满足“其他厂商主模型也能使用”，因此只能作为后续可选模式，不能取代独立搜索服务。

## 2.3 最终组件关系

```text
Main chat turn
	├─ provider: DeepSeek / Gemini / Claude / OpenAI / local
	└─ tool call: openai_web_search:search
		└─ Package Manager > Plugins
			└─ com.kiyori.openai_web_search ToolPkg
				├─ settings UI
				├─ optional input-menu toggle
				└─ openai_web_search subpackage
					└─ ToolPkgOpenAIWebSearchBridge
						├─ caller identity
						├─ OpenAIHostedWebSearchBindingResolver
						├─ ToolPkgHostEnvironmentRepository
						├─ OpenAIHostedWebSearchCompatibilityRepository
						├─ OpenAIHostedWebSearchRequestLifecycle
						├─ OpenAIHostedWebSearchAdmissionController
						└─ OpenAIHostedWebSearchGateway
							├─ SharedHttpClient
							├─ request compiler
							├─ response parser
							└─ usage/error telemetry
```

## 2.4 宿主职责

### 搜索绑定所有者

搜索绑定不是一份可漂移的独立配置副本，而是由以下现有事实源和解析器共同持有：

- `ToolPkgHostEnvironmentRepository`：持有目标 ToolPkg 的二十个 package-scoped、
  host-service 环境变量
- `OpenAIHostedWebSearchBindingResolver`：只通过
  `OpenAIHostedWebSearchBindingCompiler.compilePackageEnvironment()` 编译一次完整运行绑定，不读取
  当前聊天配置
- `OpenAIHostedWebSearchCompatibilityRepository`：持有最多 `16` 条、按 exact fingerprint digest
  区分的 relay strict 成功/失败 record-set；同一 digest 的状态互斥，不同 Key 互不覆盖

ToolPkg `privateData` 不保存绑定副本或 Key。本插件不读取 `ModelConfigManager`、Key pool 或当前
聊天 Provider。

### `OpenAIHostedWebSearchPolicy`

负责：

- 校验二十个 package environment 字段
- 校验 official 或 relay strict contract
- official 模式校验精确官方 endpoint 和 GPT-5.6 allowlist
- relay 模式校验 HTTPS、兼容探测指纹和精确模型名
- 校验 Key 可用
- 校验域名、位置、queue/HTTP 预算、admission 和输入长度
- 禁止 extra headers 覆盖认证、`User-Agent` 或 hop-by-hop headers
- 禁止插件传入 endpoint、Authorization header 或任意模型名

### `OpenAIHostedWebSearchRequestLifecycle`

每个 request ID 只有一个 lifecycle owner，记录 phase、submission state、cancel owner、elapsed、
configured timeout、queue wait 和 provider request ID。取消和 worker 完成必须通过同一个原子
`settle()` 领取唯一终态，确保 callback 只投递一次。

### `OpenAIHostedWebSearchAdmissionController`

这是普通搜索与 compatibility probe 共用的进程内稳定 FIFO owner。配置变化原地更新并发和 RPM，
不会通过替换 semaphore 或 limiter 让新旧请求绕过同一门。queue timeout 独立于 HTTP timeout，
在排队超时时不创建 HTTP Call。

### `OpenAIHostedWebSearchRequestCompiler`

只编译搜索专用 Responses 请求，不处理：

- 聊天历史
- assistant reasoning 重放
- function_call follow-up
- background sequence
- previous response
- provider execution resume

### `OpenAIHostedWebSearchResponseParser`

解析：

- `response.id`
- output message text
- `output_text.annotations`
- `url_citation`
- `web_search_call`
- `action`
- `action.sources`
- usage

它不把 citation index 强行映射到主聊天模型改写后的正文。

### `OpenAIHostedWebSearchEvidenceRepository`

按工具调用身份保存：

- query
- 搜索模型
- mode
- answer
- citation span
- stable source ID
- source title 和 URL
- actions
- usage
- response ID
- 完成或失败状态

第一版可以先复用现有工具调用账本或增加轻量 sidecar。不能只把来源拼进一段长文本后丢失结构。

### `ToolPkgOpenAIWebSearchBridge`

桥接必须绑定当前 ToolPkg container identity。JavaScript 不允许传入其他 ToolPkg ID。

公开方法建议：

```text
ToolPkg.services.openAIWebSearch.getStatus()
ToolPkg.services.openAIWebSearch.search(request)
ToolPkg.services.openAIWebSearch.cancel(requestId)
ToolPkg.services.openAIWebSearch.validateLocalConfiguration()
ToolPkg.services.openAIWebSearch.runCompatibilityProbe()
ToolPkg.services.openAIWebSearch.openConfiguration()
```

兼容探测只能由当前 ToolPkg 的 `ui` runtime 发起。宿主在网络调度前校验 active execution
session 的 runtime kind；subpackage 或 sandbox runtime 不能调用付费探测。正常
`openai_web_search:search` 继续由 sandbox runtime 使用。

`openConfiguration()` 同样只允许绑定 ToolPkg 的 active `ui` runtime 调用。它没有参数，宿主固定
打开 `com.kiyori.openai_web_search` 的原生环境变量编辑器；JavaScript 不能提交任意 package ID
或变量名。

### `ToolPkgHostEnvironmentRepository`

负责读取：

- package-scoped 环境变量
- `sensitive=true` 的 Key
- `consumer=host_service` 的 endpoint、model、headers 和参数

它按当前 ToolPkg container identity 解析 namespace，不接受 JavaScript 传入任意 package ID。

现有 `EnvPreferences` 保留给普通脚本包。Web Search 正式版不通过全局 `getEnv()` 读取 Key。

## 2.5 ToolPkg 职责

当前包结构：

```text
openai_web_search/
	├─ manifest.json
	├─ dist/
	│	├─ main.js
	│	├─ packages/
	│	│	└─ openai_web_search.js
	│	├─ shared.js
	│	└─ ui/
	│		└─ index.ui.js
	└─ src/
		├─ main.ts
		├─ packages/
		│	└─ openai_web_search.ts
		├─ shared.ts
		└─ ui/
			└─ index.ui.ts
```

revision `6` manifest 摘要：

```json
{
  "schema_version": 1,
  "toolpkg_id": "com.kiyori.openai_web_search",
  "version": "1.0.5",
  "main": "dist/main.js",
  "enabled_by_default": false,
  "environment": [
    {
      "name": "OPENAI_WEB_SEARCH_PROVIDER_CONTRACT",
      "required": false,
      "scope": "package",
      "sensitive": false,
      "consumer": "host_service",
      "input_type": "enum",
      "allowed_values": [
        "RESPONSES_HOSTED_OFFICIAL",
        "RESPONSES_RELAY_STRICT"
      ]
    },
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

完整 manifest 有二十个 package-scoped host-service 环境变量。完整字段见
[中转站与环境变量配置](5_relay_and_environment_configuration.md)。

subpackage 内部 `ToolPackage.enabled_by_default=true`。容器默认关闭；用户主动开启容器后，搜索
工具默认随插件启用。

工具：

```text
openai_web_search:search
```

第一版只公开一个搜索工具。配置校验、状态、用量和测试属于设置 UI，不进入主模型工具列表。

## 2.6 搜索工具参数

建议模型可见参数：

```json
{
  "query": "string",
  "context_size": "low | medium | high",
  "allowed_domains": ["example.com"],
  "blocked_domains": ["example.net"],
  "use_configured_location": false
}
```

约束：

- `query` 必填，trim 后非空
- `context_size` 不填时使用插件配置
- 域名参数只能收紧插件配置，不能扩大管理员或用户已设限制
- `use_configured_location` 只能选择是否使用已保存的大致位置
- 主模型不能传 API Key、endpoint、model、reasoning、headers 或 `external_web_access`
- mode、model 和 reasoning 由固定插件绑定决定

## 2.7 设置页

设置页显示：

- 九项字段级 readiness：provider contract、endpoint、model、credential、auth、extra headers、
  search options、admission、compatibility
- 产品类型：ToolPkg 插件
- official 或 relay strict contract
- 搜索服务配置：固定 package environment
- 搜索模型：精确 GPT-5.6 模型名
- 模式：默认 live，可显式选择 indexed
- reasoning effort
- endpoint、认证方式和非秘密 header 名称
- 默认 `search_context_size`
- 默认 `return_token_budget`
- allowed/blocked domains
- 大致位置开关和字段
- 单绑定最大并发
- 每分钟调用限制
- 最近调用次数与 token usage
- 本地配置校验
- 显式付费 compatibility probe
- 打开固定原生配置界面

设置页必须明确：

- 搜索会产生独立 OpenAI 费用
- 当前主聊天模型不会改变搜索绑定
- 独立配置便于单独吊销和审计
- 中转站只有在严格探测返回 Web Search actions、citations 和 sources 后才显示为兼容

## 2.8 与当前 Kiyori 代码的连接点

当前可复用：

- `SharedHttpClient`
- `OpenAIResponsesPayloadAdapter.parseUsageCounts`
- provider/tool invocation 持久化
- ToolPkg container identity
- ToolPkg Compose DSL UI
- ToolPkg subpackage
- ToolPkg `privateData` 与 IPC

revision `6` 当前专用组件：

- `HostedWebSearchCapability`
- `WebSearchMode`
- `WebSearchContextSize`
- `OpenAIHostedWebSearchBinding`
- `OpenAIHostedWebSearchGateway`
- request compiler 和 response parser
- package-bound host service bridge
- ToolPkg container-level environment schema
- package-scoped sensitive host-only environment repository
- request lifecycle 与 ownership registry
- 稳定共享 admission controller
- 字段级 readiness evaluator
- relay compatibility probe and fingerprint
- tool result/provider ledger 结构化承载、evidence parser 和 UI
- 结果卡纯展示状态与 Provider 展示策略
- mock fixture 与架构门禁

## 2.9 为什么不直接调用 `OpenAIResponsesProvider`

`OpenAIResponsesProvider` 当前是聊天 Provider，负责：

- 聊天历史转换
- reasoning
- function tools
- Tool Search
- background response
- sequence resume
- provider execution 持久化
- 消息流投影

搜索工具是一次独立、无聊天历史的服务调用。直接伪造一个聊天回合会混淆状态所有权和错误语义。

正确方式是提取和复用纯协议组件：

- endpoint 校验
- header 和鉴权
- HTTP client
- usage 解析
- 通用 Responses 错误

搜索请求和搜索输出使用独立的强类型 compiler/parser。

## 2.10 后续原生模式

后续可以增加：

```text
NATIVE_CHAT_RESPONSES
```

启用条件：

- 当前主聊天 Provider 是官方 OpenAI Responses
- 当前模型明确支持 Web Search
- 用户显式选择原生模式

该模式直接给主 Responses 请求添加 `web_search`，减少一次模型请求并保留原生 citation span。

独立服务模式与原生模式必须明确选择。请求失败不能在两者之间自动切换。
