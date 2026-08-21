# 当前架构与供应商清单

## 权威状态与持久化

当前模型配置由 `ModelConfigManager` 统一持有，使用 DataStore 中的 JSON 字符串保存
`ModelConfigData`。关键字段为：

- `apiProviderType: ApiProviderType`
- `apiProviderTypeId: String`
- `apiEndpoint: String`
- `apiKey`、API Key 池和轮换设置
- `modelName`
- 自定义参数、请求头、工具、图片/音频/视频和模型参数

本轮不复制 `ModelConfigManager`，也不改变配置列表、配置 ID、API Key 池、模型绑定和
导入导出 owner。

## 当前供应商枚举

当前 `ApiProviderType` 共包含以下类别：

- OpenAI 与协议/端点变体：`OPENAI`、`OPENAI_RESPONSES`、`OPENAI_GENERIC`、
  `OPENAI_RESPONSES_GENERIC`
- Anthropic 与兼容端点：`ANTHROPIC`、`ANTHROPIC_GENERIC`
- Google：`GOOGLE`、`GEMINI_GENERIC`
- 国内服务商：`BAIDU`、`ALIYUN`、`XUNFEI`、`ZHIPU`、`BAICHUAN`、`MOONSHOT`、
  `MIMO`、`DEEPSEEK`、`SILICONFLOW`、`IFLOW`、`INFINIAI`、`ALIPAY_BAILING`、
  `DOUBAO`、`PPINFRA`
- 国际服务商与聚合：`MISTRAL`、`OPENROUTER`、`FOUR_ROUTER`、`NOUS_PORTAL`、
  `NVIDIA`、`NOVITA`
- 本地服务：`LMSTUDIO`、`OLLAMA`、`OPENAI_LOCAL`、`MNN`、`LLAMA_CPP`
- 其他：`OTHER`

ToolPkg provider 不在这个枚举中，由 `ToolPkgAiProviderRegistry` 以字符串 provider ID
注册和展示。

## 关键调用链

```text
ModelApiSettingsSection
  -> ModelConfigManager.updateApiSettingsFull
  -> ModelConfigData JSON/DataStore
  -> AIServiceFactory.createService
  -> provider implementation
  -> EndpointCompleter / ModelCapabilityResolver / ModelRequestCompiler
  -> request body and response parser
```

连接测试复用 `AIServiceFactory`；模型列表由 `ModelListFetcher` 根据旧
`ApiProviderType` 做认证、URL 和响应解析分支；审计和 token 统计使用
`providerTypeId:modelName` 形式的稳定字符串。

## 已确认问题

1. UI 当前直接遍历 `ApiProviderType.entries`，因此 OpenAI Chat、OpenAI Responses、兼容
   Chat、兼容 Responses 和 Anthropic generic 以多个同名/近同名入口出现。
2. `ApiProviderType` 同时表达供应商身份、协议、官方端点和兼容端点，导致 UI 分组、端点补全、
   请求工厂和能力判断互相耦合。
3. `NOVITA` UI 已提供 `/openai/v1/chat/completions` 和 `/anthropic/v1/messages` 两个端点，
   但 `AIServiceFactory` 当前所有 `NOVITA` 分支都创建 `OpenAIProvider`。
4. `EndpointCompleter.completeEndpoint(endpoint, providerType)` 只能根据旧枚举判断
   `/chat/completions`、`/responses` 或 `/messages`，不能表达同一供应商下的协议切换。
5. `ModelCapabilityResolver` 已为 `gpt-5.6` 变体提供五档 reasoning profile；未登记的
   OpenAI 模型在 `ModelRequestCompiler` 中没有 reasoning effort，因此思考开关可能只影响
   UI/历史处理，不向 OpenAI 请求体表达。
6. `ModelApiProviderPresentationPolicy` 当前只把 OpenAI 拆为 Chat/Responses 两组，并没有
   国内/国际/本地的完整信息架构，也没有把供应商身份和协议选择放进同一配置流程。

## 供应商展示目标

| 展示区 | 顺序 |
| --- | --- |
| 国际供应商 | OpenAI、Anthropic、Google、Mistral AI、OpenRouter、4Router、Nous Research、NVIDIA NIM、Novita AI |
| 国内供应商 | 深度求索、阿里云百炼、百度智能云、讯飞星火、智谱 AI、百川智能、月之暗面、小米 MiMo、硅基流动、iFlow、无问芯穹、支付宝百灵、火山方舟、派欧云 PPIO |
| 本地与自定义 | LM Studio、Ollama、OpenAI 兼容本地服务、MNN、llama.cpp、自定义 API |
| ToolPkg | 动态注册的 ToolPkg AI provider |

不存在“主流供应商”独立分组。国际供应商区必须以 OpenAI、Anthropic 开头，国内供应商区
必须以 DeepSeek 开头；Google 仍属于国际供应商，不因知名度另建分组。

隐藏的 UI 兼容别名为 `OPENAI_RESPONSES`、`OPENAI_GENERIC`、
`OPENAI_RESPONSES_GENERIC`、`ANTHROPIC_GENERIC` 和 `GEMINI_GENERIC`；它们仍参与读取、
协议推导、运行时和历史数据审计。
