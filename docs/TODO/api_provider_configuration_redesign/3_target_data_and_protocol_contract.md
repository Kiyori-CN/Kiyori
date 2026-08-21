# 目标数据与协议契约

## ApiProtocol

新增 `@Serializable enum class ApiProtocol`：

- `OPENAI_CHAT_COMPLETIONS`
- `OPENAI_RESPONSES`
- `ANTHROPIC_MESSAGES`
- `PROVIDER_NATIVE`

`PROVIDER_NATIVE` 只表示由既有专用 provider 负责协议，不作为用户可选的伪协议名称。

`ApiProtocol` 需要提供：

- 从旧 `ApiProviderType` 推导协议
- 根据供应商读取显式默认协议和完整可选协议列表
- 根据协议生成端点补全规则
- 根据协议选择请求 provider
- 对不可组合的供应商/协议组合给出显式不可用结果

## ModelConfigData

在 `apiProviderTypeId` 后新增 `apiProtocol` 字段，默认值由旧
`apiProviderType` 推导，以兼容旧 JSON：

```text
OPENAI / OPENAI_GENERIC             -> OPENAI_CHAT_COMPLETIONS
OPENAI_RESPONSES*                   -> OPENAI_RESPONSES
ANTHROPIC*                          -> ANTHROPIC_MESSAGES
GOOGLE / GEMINI_GENERIC             -> PROVIDER_NATIVE
MNN / LLAMA_CPP                     -> PROVIDER_NATIVE
其余 HTTP provider                  -> OPENAI_CHAT_COMPLETIONS
```

`NOVITA` 的旧配置根据显式端点路径识别 `/anthropic/`，否则使用
`OPENAI_CHAT_COMPLETIONS`。这是对已有用户明确端点的迁移解析，不是运行时自动切换。

新保存的配置：

- `apiProviderTypeId` 使用统一供应商入口的 canonical ID
- `apiProviderType` 保留可被既有运行时和审计读取的枚举值
- `apiProtocol` 保存用户明确选择的协议

旧 `OPENAI_RESPONSES_GENERIC`、`ANTHROPIC_GENERIC` 等值继续可读，不再出现在供应商弹层。

## 集中的 Provider/协议 catalog

`ProviderApiConfig` 不再只保存一套默认 endpoint，而是保存：

```kotlin
data class ProviderProtocolConfig(
    val defaultModelName: String,
    val defaultApiEndpoint: String,
    val endpointOptions: List<ProviderEndpointOption>,
    val defaultModelListEndpoint: String,
)

data class ProviderApiConfig(
    val providerType: ApiProviderType,
    val defaultProtocol: ApiProtocol,
    val protocolConfigs: Map<ApiProtocol, ProviderProtocolConfig>,
    val requiresApiKey: Boolean,
)
```

约束：

- 每个可见内建供应商必须有非空 `protocolConfigs`。
- `defaultProtocol` 必须属于 `protocolConfigs.keys`。
- UI、默认端点、协议切换和测试统一读取该 catalog，不另建第二份协议清单。
- 旧兼容枚举也必须有精确配置，不能依赖缺省构造或未知组合。
- 不支持的供应商/协议组合直接拒绝，不自动改用另一协议。

## Provider/协议矩阵

| 分组 | 供应商规范名称 | 默认协议 | 可选协议 | 运行时 owner |
| --- | --- | --- | --- | --- |
| 国际 | OpenAI（GPT 系列） | OpenAI Chat | OpenAI Chat、OpenAI Responses | `OpenAIProvider`、`OpenAIResponsesProvider` |
| 国际 | Anthropic（Claude 系列） | Anthropic Messages | Anthropic Messages、OpenAI Chat | `ClaudeProvider`、通用 `OpenAIProvider` |
| 国际 | Google（Gemini 系列） | Gemini 原生 API | Gemini 原生 API、OpenAI Chat | `GeminiProvider`、通用 `OpenAIProvider` |
| 国际 | xAI（Grok 系列） | OpenAI Chat | OpenAI Chat、OpenAI Responses | 通用 `OpenAIProvider`、`OpenAIResponsesProvider` |
| 国际 | Mistral AI（Mistral / Codestral 系列） | OpenAI Chat | OpenAI Chat | `MistralProvider` |
| 国际 | OpenRouter（多模型聚合） | OpenAI Chat | OpenAI Chat、OpenAI Responses | `OpenRouterProvider`、`OpenAIResponsesProvider` |
| 国际 | 4Router（多模型聚合） | OpenAI Chat | OpenAI Chat | `FourRouterProvider` |
| 国际 | Nous Research（Hermes 系列） | OpenAI Chat | OpenAI Chat | `NousPortalProvider` |
| 国际 | NVIDIA NIM（Nemotron / 多模型） | OpenAI Chat | OpenAI Chat | `NvidiaAIProvider` |
| 国际 | Novita AI（多模型） | OpenAI Chat | OpenAI Chat、Anthropic Messages | `OpenAIProvider`、`ClaudeProvider` |
| 国内 | 深度求索（DeepSeek 系列） | OpenAI Chat | OpenAI Chat、OpenAI Responses、Anthropic Messages | `DeepseekProvider`、`OpenAIResponsesProvider`、`ClaudeProvider` |
| 国内 | 阿里云百炼（Qwen 通义千问系列） | OpenAI Chat | OpenAI Chat、OpenAI Responses | `QwenAIProvider`、`OpenAIResponsesProvider` |
| 国内 | 百度智能云（ERNIE 文心系列） | OpenAI Chat | OpenAI Chat | 既有 `OpenAIProvider` |
| 国内 | 讯飞星火（Spark 系列） | OpenAI Chat | OpenAI Chat | 既有 `OpenAIProvider` |
| 国内 | 智谱 AI（GLM 系列） | OpenAI Chat | OpenAI Chat、Anthropic Messages | `OpenAIProvider`、`ClaudeProvider` |
| 国内 | 百川智能（Baichuan 系列） | OpenAI Chat | OpenAI Chat | 既有 `OpenAIProvider` |
| 国内 | 月之暗面（Kimi / Moonshot 系列） | OpenAI Chat | OpenAI Chat | `KimiProvider` |
| 国内 | 小米 MiMo（MiMo 系列） | OpenAI Chat | OpenAI Chat、OpenAI Responses、Anthropic Messages | `MimoProvider`、`OpenAIResponsesProvider`、`ClaudeProvider` |
| 国内 | 硅基流动（多模型） | OpenAI Chat | OpenAI Chat、Anthropic Messages | `QwenAIProvider`、`ClaudeProvider` |
| 国内 | iFlow（多模型） | OpenAI Chat | OpenAI Chat | 既有 `OpenAIProvider` |
| 国内 | 无问芯穹（Infini-AI 系列） | OpenAI Chat | OpenAI Chat、Anthropic Messages | `OpenAIProvider`、`ClaudeProvider` |
| 国内 | 支付宝百灵（Ling 系列） | OpenAI Chat | OpenAI Chat | 既有 `OpenAIProvider` |
| 国内 | 火山方舟（豆包系列） | OpenAI Chat | OpenAI Chat、OpenAI Responses | `DoubaoAIProvider`、`OpenAIResponsesProvider` |
| 国内 | 派欧云 PPIO（多模型） | OpenAI Chat | OpenAI Chat | 既有 `OpenAIProvider` |
| 本地 | LM Studio（本地模型服务） | OpenAI Chat | OpenAI Chat、OpenAI Responses、Anthropic Messages | 通用协议 owner |
| 本地 | Ollama（本地模型服务） | OpenAI Chat | OpenAI Chat、OpenAI Responses、Anthropic Messages | `OllamaProvider`、通用协议 owner |
| 本地 | OpenAI 兼容本地服务 | OpenAI Chat | OpenAI Chat、OpenAI Responses | 通用 OpenAI owner |
| 本地 | MNN（端侧本地推理） | Provider Native | Provider Native | `MNNProvider` |
| 本地 | llama.cpp（端侧本地推理） | Provider Native | Provider Native | `LlamaProvider` |
| 自定义 | 自定义 API | OpenAI Chat | OpenAI Chat、OpenAI Responses、Anthropic Messages | 通用协议 owner |

讯飞 MaaS Coding、智谱 Coding、Kimi Code 等具有独立产品、凭据或端点合同的协议，不与当前
通用供应商入口混为一谈；只有当前 catalog 中明确登记的 endpoint 才能显示为协议选项。
阿里云 Anthropic Messages 当前要求带 WorkspaceId 的独立地址，智谱 Responses 当前属于
独立 Coding Plan 地址合同，因此二者不作为现有通用供应商 row 的一键默认协议。

## 端点契约

端点补全新增以 `ApiProtocol` 为参数的入口：

- OpenAI Chat：`/v1/chat/completions`
- OpenAI Responses：`/v1/responses`
- Anthropic Messages：`/v1/messages`；`/anthropic` base 需要保留供应商约定的前缀
- `PROVIDER_NATIVE`：由现有 provider 逻辑处理

保留旧 provider-type 重载作为兼容入口，并由旧 ID 推导协议。端点文本带 `#` 时仍只移除
控制符而不补全路径。

## 显式协议选择

协议选择器不提供自动识别或 `ApiProtocol.AUTO`。多协议供应商始终由用户显式选择一个
`ApiProtocol` 并持久化；切换供应商时只应用该供应商 catalog 中声明的 `defaultProtocol`。
单协议供应商不显示无意义的选择器。

运行时不执行“先请求一个协议，失败后再请求另一个协议”的策略，避免重复计费、重复工具调用、
Responses 后台状态丢失和不可复现的协议切换。

## 请求路由

`AIServiceFactory.createService` 先读取 `apiProtocol`：

1. `OPENAI_RESPONSES` 创建 `OpenAIResponsesProvider`，保留供应商 identity。
2. `ANTHROPIC_MESSAGES` 创建 `ClaudeProvider`，认证方式按供应商显式决定。
3. `OPENAI_CHAT_COMPLETIONS` 默认进入当前供应商的专用 Chat adapter。
4. Anthropic、Google 和 xAI 的 OpenAI-compatible Chat 进入通用 `OpenAIProvider`，同时
   保留原供应商 identity；xAI 因此使用普通非 gpt-5.6 的 reasoning 映射。
5. `PROVIDER_NATIVE` 只允许 Google、MNN、llama.cpp 等 catalog 明确声明的原生 owner。

模型列表不能只根据 `ApiProtocol` 选择认证和 parser：官方 Anthropic 使用 Anthropic
认证/解析，DeepSeek、Novita、LM Studio、Ollama、智谱、MiMo、硅基流动和无问芯穹的
Anthropic-compatible 协议仍应根据供应商身份选择其模型列表 URL、认证和响应格式。
