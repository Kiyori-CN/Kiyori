# sub2api 与 new-api 参考设计

## 参考范围

本文件只记录两个本地参考仓库在“供应商/平台身份、协议和渠道配置”上的结构事实，不把它们
的中转站后台、计费、配额、权重、自动封禁或多租户数据带入 Kiyori。

观察基线：

- `sub2api`：`main@3548256745c2168caf63e68963f2791834353eef`
- `new-api`：`main@f116414284162ad15d8925f7bca494c109b83e93`

## new-api 的分层

`new-api/constant/channel.go` 把 `ChannelType` 作为渠道/供应商类型目录，并为渠道类型维护
名称和基础 URL。`new-api/constant/api_type.go` 另有 `APITypeOpenAI`、
`APITypeAnthropic` 等适配器类型。`new-api/relay/constant/relay_mode.go` 再按请求路径区分
Chat Completions、Responses、Gemini、音频、图片等 relay mode。`new-api/relaykit/types/relay_format.go`
把 `openai`、`claude`、`gemini`、`openai_responses` 等格式定义为独立类型。

同一个渠道可以通过 `ChannelSpecialBases` 同时拥有 Claude base URL 和 OpenAI base URL。
这说明“渠道/供应商身份”和“协议/relay format”不应由同一个 UI 枚举值重复承担。

对 Kiyori 的可复用结论：

- 供应商身份负责名称、分组、默认值和展示。
- 协议负责请求路径、请求/响应编译器和协议能力。
- 端点是供应商与协议共同决定的配置，不是供应商名称的一部分。
- 适配器选择必须在运行时显式由协议决定，不能因为端点名称看起来像另一种协议而静默改变。

不采用的部分：

- 不新增渠道数据库、负载均衡、请求重试队列、渠道权重、自动封禁或后台管理表单。
- 不把 Kiyori 的单个用户模型配置扩展成中转站的租户渠道实体。

## sub2api 的分层

`sub2api/frontend/src/constants/platforms.ts` 维护 concrete platform catalog；平台选项是
账户、分组和可见模型的稳定身份。`sub2api` 的 channels 表把多个分组关联到一个 channel，
并把模型定价单独存放；可见渠道还可以按多个 concrete platform 组织。

对 Kiyori 的可复用结论：

- 先稳定平台/供应商身份，再在供应商内部组织协议和模型能力。
- 列表和筛选应从集中目录生成，避免新供应商只添加了枚举却没有展示分类。
- 供应商说明、协议标签和模型能力应是结构化字段，而不是把协议拼进显示名称。

不采用的部分：

- Kiyori 不引入 channel/group/pricing 数据库模型。
- Kiyori 不展示中转站运营指标、账单、额度、平台可用性和管理字段。

## 设计落点

Kiyori 的目标模型是：

```text
Provider identity
  -> supported protocol list
  -> protocol-specific endpoint options
  -> model capability profile
  -> one explicit runtime adapter
```

旧 `ApiProviderType` 作为兼容读取和运行时标签保留；新增 `ApiProtocol` 作为协议选择和
请求路由的权威字段。
