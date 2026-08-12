---
status: verification_pending
implementation: local_implementation_complete
last_updated: 2026-08-12
---

# OpenAI Hosted Web Search 架构

## 1. 文档职责

本文定义 Kiyori 将 OpenAI 官方 Web Search 作为独立工具提供给所有主聊天模型时的长期架构边界。
实施进度、分阶段任务与验证矩阵记录在
[OpenAI 官方联网搜索插件化接入](../../TODO/openai_hosted_web_search/index.md)。

当前本地实现正在完成 revision `5` 收口，但它不是已发布功能。用户先后用多版 APK 暴露旧 sources 逐项
fail-fast、revision `2` 双通道强制门禁，以及 revision `3` 在 Pixel 上的 HTTP `502` 和
Sub2api 上的 HTTP `401`。2026-08-12 的第二轮脱敏真实 API 协议矩阵已把共同请求根因锁定为
空 `blocked_domains` 序列化。revision `4` 修复该请求字段后，Sekirocloud 真实搜索进一步证明
`url_citation` 与 `action.sources` 可以独立、缺失或部分覆盖；旧 parser 的 citation 白名单假设
造成非确定性 `SOURCE_INVALID`。revision `5` 已修复该证据合同，ToolPkg 与 Debug APK 本地
封板已经完成，目标设备验收仍保持 `verification_pending`。

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

- `ToolPkgHostEnvironmentRepository` 持有插件环境中的配置来源、provider contract、固定模型配置
  ID、精确搜索模型名和搜索参数
- `ModelConfigManager` 在 `MODEL_CONFIG` 模式继续持有 endpoint、Key/Key pool、headers、
  provider type、模型列表和配置级请求限制
- `OpenAIHostedWebSearchBindingResolver` 严格按所选来源编译完整运行绑定
- `OpenAIHostedWebSearchCompatibilityRepository` 持有 relay strict 成功/失败 record-set、
  探测指纹、response schema revision 和成功 evidence mode；最多保留 16 个最近 fingerprint，
  不同 Key 互不覆盖

ToolPkg 不能保存另一份完整绑定，也不能从当前聊天状态推断绑定。

### 模型与 API Key

`MODEL_CONFIG` 来源由 `ModelConfigManager` 继续持有：

- OpenAI API Key
- Key pool
- endpoint
- provider type
- 模型列表

ToolPkg 只得到搜索结果，不得到 Key。

`PACKAGE_ENV` 来源由 `ToolPkgHostEnvironmentRepository` 持有：

- package-scoped endpoint
- model
- API Key
- auth mode
- extra headers
- reasoning 和搜索参数

Key 必须标记为 sensitive、password 和 `host_service` consumer。ToolPkg JavaScript 的 `getEnv()`
不能读取该值。

### 搜索执行

`OpenAIHostedWebSearchGateway` 是单次 OpenAI 搜索请求的唯一执行 owner。

它负责：

- policy validation
- request identity
- HTTP lifecycle
- cancellation
- response parsing
- usage
- error

### 证据

搜索 evidence 必须绑定工具调用身份。可见聊天正文不是唯一证据存储。

当前实现把完整结构化 JSON 同时保留在工具结果和 provider tool invocation ledger 中。聊天正文中的
工具结果使用 JSON Unicode escape 消除原始 `<`、`>` 和 `&`，解码后的 answer、URL 和 citation
offset 不变；只有精确 `openai_web_search:search` 且 schema 校验通过的结果进入专用来源卡。

## 4. 固定不变量

- 搜索插件不自动读取当前聊天 Provider
- 搜索插件不自动读取当前聊天模型
- 搜索插件是 ToolPkg 插件容器，不是普通 JS 脚本包
- container 默认关闭
- subpackage 在 container 启用后默认开启
- 搜索绑定只能由用户或明确的宿主配置改变
- 配置来源只能是 `PACKAGE_ENV` 或 `MODEL_CONFIG`
- 两种配置来源不能混合读取
- official contract 只允许官方 Responses 与 GPT-5.6 family
- relay contract 必须通过严格兼容探测
- compatibility probe 只能由插件设置页的 `ui` runtime 发起
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

MODEL_CONFIG
	固定 modelConfigId + exact modelName
```

`MODEL_CONFIG` 只复用 endpoint、Key/Key pool、认证、extra headers 和配置级请求限制。聊天
`customParameters`、历史、Prompt Cache、background、reasoning replay、Tool Search 和 function
tools 不进入搜索请求。

`PACKAGE_ENV` 至少支持：

```text
OPENAI_WEB_SEARCH_RESPONSES_ENDPOINT
OPENAI_WEB_SEARCH_MODEL
OPENAI_WEB_SEARCH_API_KEY
OPENAI_WEB_SEARCH_AUTH_HEADER_NAME
OPENAI_WEB_SEARCH_AUTH_SCHEME
OPENAI_WEB_SEARCH_EXTRA_HEADERS_JSON
OPENAI_WEB_SEARCH_REASONING_EFFORT
OPENAI_WEB_SEARCH_EXTERNAL_WEB_ACCESS
OPENAI_WEB_SEARCH_CONTEXT_SIZE
OPENAI_WEB_SEARCH_RETURN_TOKEN_BUDGET
```

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

允许显式绑定已有官方 OpenAI Responses 配置。共用时只保存配置引用，不复制 Key。

relay strict 允许绑定经过当前指纹严格兼容探测的 `OPENAI_RESPONSES` 或
`OPENAI_RESPONSES_GENERIC`，也允许使用 `PACKAGE_ENV` 设置中转站。official contract 仍要求
固定 `OPENAI_RESPONSES` 配置和精确官方 endpoint。

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
当前为 `5`；revision 不一致时成功与失败记录都失效。成功与失败按 exact fingerprint digest
保存在最多 16 条的 record-set 中，同一 digest 互斥，不同 Key 记录互不删除。MODEL_CONFIG 的
Key 选择与游标推进在搜索和 probe bridge 实例之间共享互斥。

HTTP 非成功响应最多读取 64 KiB，只投影经过控制字符清理、凭据脱敏和长度限制的 provider
error type、code、message 与 request/trace ID。状态页仅显示短 credential revision 和
`bearer`、`direct`、`custom` 分类，不显示 Key、Authorization 值或自定义 scheme 原文。

只返回普通文本的 endpoint 不是等价 Web Search backend。

## 11. UI 证据

聊天 UI 的搜索证据卡至少展示：

- 查询
- 搜索模型
- live/indexed 模式
- 搜索动作摘要
- 来源列表
- OpenAI 搜索 answer
- token usage 与调用次数
- 错误或 warning

来源 URL 可点击。URL source 标题为空时显示 host；已知实时 feed 和 relay 命名 `api` feed 显示
稳定标题且不提供点击动作。详情区显示当前 evidence mode，避免把来源列表误当作精确 citation span。

主模型最终正文可以使用 `[S1]` source marker，但 evidence card 不依赖主模型保留标记。

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

- `ModelConfigManager`
- `SharedHttpClient`
- `OpenAIResponsesPayloadAdapter`
- ToolPkg container identity
- ToolPkg UI、subpackage、IPC 和 storage
- provider/tool execution 持久化

Web Search 复用 ModelConfig 的 Key、Key pool 与轮换索引数据，但使用搜索专用选择器。它不输出
Key 或 Key 前后缀，也不在所选来源无可用 Key 时读取另一来源。

需要新增：

- ToolPkg container-level environment schema
- package-scoped sensitive host-only environment repository
- official/relay contract 和 compatibility probe
- OpenAI Web Search package-bound host bridge
- Web Search JSON XML 安全承载和 evidence source card

不复用为错误角色：

- 不把 `OpenAIResponsesProvider` 当作搜索工具直接发送伪聊天
- 不用 `ToolPkg.registerAiProvider`
- 不用 Browser Runtime 抓取网页替代 OpenAI Web Search
- 不用全局 env 作为正式 Key

## 14. 兼容和发布

当前 Kiyori UI 是否已发布不影响第一版设计，因为新增工具不替换现有用户接口。

如果未来替换已有搜索包或改变工具名，必须先确认已发布状态，并保持已发布工具名、参数和结果合同
的向前兼容。

第一版完成后仍需分别验证：

- 本地单元和 mock：revision `5` 完整定向矩阵 `13` 个 suite、`91/91`，失败、错误和跳过均为 `0`
- ToolPkg 构建：revision `5` / `1.0.4` 为 `9864` bytes，SHA-256
  `558382BDDE9688F99395F703D3225C7DAB5452326F85A6660DDB557ACEA3B9FB`，归档结构与敏感信息扫描
  通过；revision `4` 的 `1.0.3` 制品为
  现场 `SOURCE_INVALID` 失败基线；revision `3` 的 `9679` bytes 与
  SHA-256 `58025CC245D055D76345F45C9DE67E20D5464240F5033695B4C77380C1B94E24`
  是失败历史基线
- Debug APK：revision `5` 为 `472480557` bytes，SHA-256
  `9F845CC0F71CEBB1929E42148F93C85A489C9FAD20160FAE5E8BFF13EE451536`；包名、版本和 SDK 为
  `com.kiyori / 0.1.0 (45) / 26 / 34 / 37`，唯一 launcher、arm64-only、Debug V2 单 signer、
  16 KB ZIP 对齐和 52 个 AArch64 ELF 的 `PT_LOAD >= 0x4000` 均通过；内嵌 ToolPkg 与生成制品
  逐字节相同。revision `4` APK 仍会出现 `SOURCE_INVALID`，revision `3` 的
  `472480557` bytes 与 SHA-256
  `5767522CDEC73096B42A63E6CEA415A704E7F9AE56EA026F8FC17D468B1DF27C`
  已被现场证明失败
- 修复版真实中转协议矩阵：已完成脱敏 smoke
- 修复版目标 Android 设备
- 用户验收

任何一级不能代替另一层证据。
