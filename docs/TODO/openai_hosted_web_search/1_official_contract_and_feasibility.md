# 1. 官方合同与可行性

## 1.1 研究范围

本设计区分四个容易混淆的官方能力：

1. Codex 产品配置中的 `web_search`
2. OpenAI Responses API 托管 `web_search`
3. Chat Completions 的 `gpt-5-search-api`
4. Codex 源码中的 standalone search endpoint

Kiyori 正式接入应选择具有公开 API 文档、稳定请求字段和可测试输出合同的路线，不能只因为某个
接口出现在 Codex 源码中就把它当成可长期依赖的公开产品合同。

## 1.2 Codex 产品配置

当前 Codex 文档定义三种模式：

```toml
web_search = "disabled"
web_search = "indexed"
web_search = "live"
```

语义是：

- `disabled`：不提供网页搜索
- `indexed`：只使用 OpenAI 索引或缓存的网页结果
- `live`：允许从公开互联网获取最新内容

旧资料中可能出现 `cached`。本设计按 2026-08-11 的当前官方命名使用 `indexed`，开发前仍需
重新核对。

Codex 产品配置是上层能力开关，不是 Kiyori 可以直接复制的公开网络协议。Kiyori 应对接其下层
公开 API，或者在未来官方稳定发布 standalone search 后再增加新的 backend。

官方资料：

- [Codex Web Search](https://developers.openai.com/codex/web-search)
- [Codex Configuration Reference](https://developers.openai.com/codex/config-reference)

## 1.3 Responses 托管 Web Search

公开、稳定且最适合 Kiyori 正式接入的合同是：

```json
{
  "model": "gpt-5.6",
  "tools": [
    {
      "type": "web_search"
    }
  ],
  "input": "..."
}
```

它提供：

- `web_search_call` output item
- `search`、`open_page`、`find_in_page` 等 action
- assistant `output_text`
- `output_text.annotations[]`
- `url_citation`
- `include: ["web_search_call.action.sources"]` 请求 action 级来源信息
- `action.sources` 的数量可以多于 citation 数量；除 URL source 外，还可能包含无 URL 的
  `oai-sports`、`oai-weather` 和 `oai-finance` 实时第三方 feed
- `search_context_size`
- `return_token_budget`
- `filters.allowed_domains`
- `filters.blocked_domains`
- `user_location`
- `external_web_access`
- `tool_choice: "required"` 强制工具调用

live 与 indexed 可以映射为：

```text
LIVE
	external_web_access = true

INDEXED
	external_web_access = false
```

官方说明中 `external_web_access` 缺省值为 `true`。Kiyori 仍应显式写出该字段，避免默认语义
变化或日志难以判断实际模式。

`return_token_budget` 当前只支持：

```text
default
unlimited
```

它不是数值 token 上限。模型最终输出长度应由 `max_output_tokens` 等单独字段控制。

官方资料：

- [OpenAI Web Search Tool Guide](https://developers.openai.com/api/docs/guides/tools-web-search)
- [Responses API](https://developers.openai.com/api/docs/guides/responses-vs-chat-completions)

## 1.4 为什么它仍然是模型请求

Responses 请求必须指定 `model`。模型决定：

- 如何理解用户查询
- 是否拆分搜索词
- 是否继续打开页面或在页面中查找
- 如何筛选证据
- 如何生成带引用的答案

因此“搜索插件不用于大模型聊天”只能解释为产品职责分离：

```text
主聊天模型负责对话与最终任务
GPT-5.6 搜索模型只负责联网检索与证据整理
```

它不能解释为网络层没有模型推理。费用和用量仍可能包括：

- GPT-5.6 输入 token
- GPT-5.6 输出 token
- Web Search 工具调用
- 搜索内容 token 或对应工具计费

## 1.5 模型支持

“官方 Web Search 只支持 GPT 系列”不是完整的 OpenAI 全局结论。官方 Web Search 指南还列出
`o3`、`o4-mini` 等 capable models。Kiyori 第一阶段只支持 GPT-5.6 family，是产品范围选择，
不是对 OpenAI 全部模型能力的断言。

第一阶段通用搜索允许列表：

```text
gpt-5.6
gpt-5.6-sol
gpt-5.6-terra
gpt-5.6-luna
```

官方当前还列出 `gpt-5.6-cyber`，并标注支持 Responses Web Search。它是安全领域专用模型，
第一版不纳入通用搜索 allowlist。后续只有在产品明确增加安全研究模式、补齐 capability profile
和专项测试后才单独评估。

建议默认 `gpt-5.6-luna`：

- 适合成本敏感、日常检索和工具结果整理
- 与用户要求的 GPT-5.6 系列一致
- 用户仍可显式选择 Terra、Sol 或基础 `gpt-5.6`

不根据查询复杂度自动切换模型。模型选择变化会直接影响费用、质量、延迟和结果稳定性，必须由
用户配置或未来明确的产品策略控制。

中转站允许使用自定义模型别名，但不能从名称推断它支持 GPT-5.6、Responses 或 Web Search。中转
模型必须通过当前 endpoint、认证、参数和模型组合的严格兼容探测。

官方模型页：

- [GPT-5.6](https://developers.openai.com/api/docs/models/gpt-5.6)
- [GPT-5.6 Sol](https://developers.openai.com/api/docs/models/gpt-5.6-sol)
- [GPT-5.6 Terra](https://developers.openai.com/api/docs/models/gpt-5.6-terra)
- [GPT-5.6 Luna](https://developers.openai.com/api/docs/models/gpt-5.6-luna)
- [GPT-5.6 Cyber](https://developers.openai.com/api/docs/models/gpt-5.6-cyber)

## 1.6 `gpt-5-search-api`

OpenAI 还提供 Chat Completions 搜索模型：

```json
{
  "model": "gpt-5-search-api",
  "web_search_options": {},
  "messages": [
    {
      "role": "user",
      "content": "..."
    }
  ]
}
```

这个模型总会先搜索再回答，适合必须留在 Chat Completions 的应用。它不是本项目第一选择：

- 用户明确要求优先 GPT-5.6 系列
- Responses `web_search` 提供更多控制项
- Responses 更容易取得完整 sources 和 action
- Kiyori 已经有成熟的 Responses 能力边界

它可以作为官方路线对照，不作为失败后的自动切换目标。

## 1.7 Codex standalone search

当前 Codex 文档和源码存在独立搜索路线：

```text
POST /v1/alpha/search
```

Codex 的 extension-backed `web.run` 使用这条路线时，可以提供：

- 多个并行 query
- `search`
- 通过 `ref_id` 或 URL 执行 `open`
- 页面内 `find`
- 会话历史和 source reference

Codex 源码中的请求仍包含：

```text
model
queries[]
```

因此它同样不是无模型搜索。

这条路线更接近 Codex `web.run` 的工具交互，但当前存在三个正式接入风险：

- Codex 文档仍标注 standalone search 处于开发中
- 自定义 provider 支持默认关闭
- `/v1/alpha/search` 没有与 Responses Web Search 等价的公开稳定 API 参考和版本承诺

所以第一版不使用该 endpoint。只有当 OpenAI 提供公开稳定文档、认证、模型支持、计费、错误和
兼容承诺后，才评估新增 `CODEX_STANDALONE` backend。

源码参考：

- [Codex web search tool](https://github.com/openai/codex/blob/main/codex-rs/ext/web-search/src/tool.rs)
- [Codex Search API endpoint models](https://github.com/openai/codex/blob/main/codex-rs/codex-api/src/endpoint/search.rs)
- [Codex Search API client](https://github.com/openai/codex/blob/main/codex-rs/codex-api/src/endpoint/search_client.rs)

## 1.8 Responses-compatible 中转站

Kiyori 已经存在 `OPENAI_RESPONSES_GENERIC`，所以从传输层连接中转站是可行的。

但“能接受 `/v1/responses` 请求”不等于“支持 OpenAI hosted Web Search”。中转站可能：

- 删除 `tools[].type=web_search`
- 不支持 `external_web_access`
- 不支持 `reasoning`
- 返回普通文本而没有 `web_search_call`
- 丢失 annotations
- 不返回 `action.sources`
- 返回 URL source 与无 URL 官方实时 feed 的混合集合
- 使用其他搜索服务后伪装成模型回答

因此第一版定义两种明确合同：

```text
RESPONSES_HOSTED_OFFICIAL
RESPONSES_RELAY_STRICT
```

`RESPONSES_RELAY_STRICT` 必须由用户显式执行一次可能产生费用的兼容探测，并实际返回：

- Responses object schema
- 至少一个 `web_search_call`
- 非空 output text
- 至少一个 `url_citation`
- usage，或明确的中转缺失 warning

探测必须固定访问公开网页，不能使用当前日期、天气、体育或金融问题。后者可以合法路由到无 URL 的
结构化实时数据，因此不能证明或否定网页 URL citation 能力。

普通 relay 搜索接受下列真实 evidence mode：

```text
url_citations_and_action_sources
url_citations
action_sources
structured_feeds
```

`url_citation` annotation 是行内 citation span 的权威来源；`action.sources` 是独立的检索审计
通道。`url_citations` 和 `action_sources` 只保留远端实际返回的 URL 通道，不从答案文本推断
另一个通道。两个 URL 通道同时存在时也不要求 URL 集合完全相同：后续 `open_page` 可以产生不在
先前 search sources 中的 citation，relay 也可能返回部分 action sources。差异只进入结构化诊断
和 warning，不使有效 citation 失败。`structured_feeds` 保留官方实时 feed 与 relay 返回的命名
`api` feed，不生成 citation span。

中转站配置改变 endpoint、模型、认证方式、请求头或 reasoning 后，旧探测结果失效。

### 2026-08-12 本机 Codex 与中转实测

本机 Codex `0.147.0-alpha.6.6` 使用：

```text
wire_api = responses
web_search = live
model = gpt-5.6-sol
```

当前 `standalone_web_search` feature 关闭，因此本轮不能把二进制中存在的 `/alpha/search` 实验模型
当作当前会话的活跃路径。脱敏真实请求得到：

- 旧 UTC 日期 probe：`web_search_call + output_text + {type:"api", name:...}`，无 URL citation
- 固定公开网页、非流式且不加 actor header：有标准 `url_citation`，`action.sources` 为空
- 同一请求增加 `x-openai-actor-authorization: enabled`：有 citation 与 URL action sources
- 同一公开网页流式请求：完成响应可有 citation 而没有 action sources

这些结果证明当前中转支持 Web Search，但其证据通道受查询类型、请求头和流式形态影响。该结论是
本机 Codex 和指定中转的 2026-08-12 实测，不扩大为 OpenAI 官方对所有中转站的保证。

revision `3` 用户现场失败后，第二轮真实矩阵同时覆盖 Pixel 与 Sub2api。两个站点的普通 Responses
和 OpenAI 官方最小 `web_search` 请求均为 HTTP `200`。精确复刻 Kiyori revision `3` 请求时，
Pixel 返回 HTTP `502 upstream_error`，Sub2api 超时；保持所有其他字段不变，只省略空
`blocked_domains` 后两个站点均为 HTTP `200`，并返回 citation 与 action sources。

因此 actor header 与流式形态不是本轮成功的必要条件。revision `4` 的单一确定合同仍为非流式
Responses POST，只修正空过滤数组序列化。

### 2026-08-12 Sekirocloud 来源通道复现

用户在 revision `4` APK 上切换到 Sekirocloud 后，compatibility probe 和普通搜索可以成功到达
真实 Web Search，但现场 11 次搜索只有 2 次成功、9 次因旧 parser 抛出 `SOURCE_INVALID`。完全
相同的英文查询曾先成功、随后失败；有无 `allowed_domains`、中文或英文、`low` 或 `medium` 都
不是稳定决定因素。Python 和 NASA 官方站点同样失败，F1 查询在返回完整 action sources 时成功。

使用同一 relay 的脱敏原始非流式 Responses 请求精确复现：

```text
OpenAI 中文限域查询
	HTTP 200
	web_search_call = 3
	url_citation URL = 2
	action source URL = 18
	其中 1 个 citation URL 不在 action sources

OpenAI 英文查询
	HTTP 200
	web_search_call = 3
	url_citation URL = 2
	action source URL = 0
	open_page URL = 1
```

两者都是真实搜索成功响应。旧 revision `4` 只在 action source 集合非空但不能覆盖全部 citation
时失败，因此根因不是 allowed domains、模型、语言、站点或网络，而是把独立 action audit 列表
错误当成 citation 白名单。revision `5` 删除这项错误不变量，同时保留 citation URL、HTTP(S)、
span、source mapping 和 response schema 的硬校验。

## 1.9 可行性矩阵

| 目标 | 可行性 | 第一版 |
| --- | --- | --- |
| 让 DeepSeek、Gemini、本地模型调用 OpenAI 搜索 | 高 | 支持 |
| 搜索账号与主聊天模型完全分离 | 高 | 支持 |
| 独立 OpenAI API Key 与预算 | 高 | 推荐 |
| 使用环境变量配置中转 endpoint、模型、Key 和 reasoning | 高 | 支持 |
| 显式共用一个固定 Responses 配置 | 高 | 可选 |
| 自动跟随当前聊天 Provider | 技术上可拼接，产品风险高 | 不支持 |
| 使用 GPT-5.6 只承担搜索角色 | 高 | 支持 |
| 不发生任何 GPT 推理 | 不可行 | 不支持 |
| 返回答案、引用、来源和 usage | 高 | 支持 |
| 官方 OpenAI Responses | 高 | 支持 |
| 严格兼容中转站 | 中到高，取决于中转实现 | 探测通过后支持 |
| 只返回普通文本的中转站 | 无法证明 Web Search 等价 | 不支持 |
| 精确复刻 Codex `search/open/find/ref_id` | 中 | 第一版不支持 |
| 直接使用 `/v1/alpha/search` 正式发布 | 风险高 | 不支持 |
| 作为与现有 ToolPkg 同级的插件 | 高 | 推荐 |
| 作为普通 JS 脚本包 | 技术上可做 | 不作为正式产品形态 |
| 纯 JS + 环境变量直接请求 Responses | 高 | 只作为开发原型 |
| 使用 ToolPkg AI Provider 注册搜索 | 方向不匹配 | 不支持 |

## 1.10 与 Codex 的相似度边界

第一版能够复制的产品能力：

- live 搜索
- 模型驱动的查询规划
- 搜索、打开页面和页内查找 action 的证据记录
- 带 URL 的引用
- 完整来源集合
- 域名和大致位置控制
- 可被任意主模型作为工具调用

第一版不能宣称精确复制：

- Codex 工具的跨调用 `ref_id`
- Codex `open` 返回的行号视图
- Codex `find` 的指定页面匹配结果
- Codex standalone search 的 provider/history 协议
- Codex 内部排序、缓存、反滥用和 source lifecycle

如果未来确实需要 `web.run` 风格多步浏览，应建立新的明确 backend 和 session contract，不能把
Responses 的内部 action 伪装成可由主模型继续操作的稳定 `ref_id`。

## 1.11 定价边界

截至 2026-08-11，官方价格页将 Web Search 工具费、搜索内容 token 和模型 token 分开描述。
价格和计费方式可能调整，因此：

- 文档只记录研究日期，不在源码硬编码长期价格
- 设置页链接官方价格页
- 每次结果分别记录模型 token usage 和 `web_search_call` 数量
- 开发前和发布前重新核对官方价格
- 用户执行显式联网测试前显示可能产生费用

官方资料：

- [OpenAI API Pricing](https://developers.openai.com/api/docs/pricing)
