# UI 信息架构与交互

## 供应商弹层

供应商弹层保留当前已有的搜索、可访问性语义和圆角 Surface，但改为以下顺序：

1. 国际供应商：OpenAI、Anthropic、Google、Mistral AI、OpenRouter、4Router、
   Nous Research、NVIDIA NIM、Novita AI
2. 国内供应商：DeepSeek、阿里云百炼、百度智能云、讯飞星火、智谱 AI、百川智能、
   月之暗面、小米 MiMo、硅基流动、iFlow、无问芯穹、支付宝百灵、火山方舟、派欧云 PPIO
3. 本地与自定义：LM Studio、Ollama、OpenAI 兼容本地服务、MNN、llama.cpp、自定义 API
4. ToolPkg：动态注册 provider

不显示“主流供应商”标题。OpenAI、Anthropic 直接固定在国际供应商最上方，DeepSeek 直接
固定在国内供应商最上方。

OpenAI/Anthropic 的协议变体不再作为独立 Option。搜索供应商名称、摘要和内部兼容 ID 仍可
命中，便于从旧配置定位问题，但搜索结果只显示 canonical provider row。

每个 row 显示：

- 供应商名称
- 一句职责摘要
- 协议数量或当前协议标签
- 统一语义色，不再用同一供应商不同协议的透明度伪造多个品牌

## 供应商配置卡

API 设置卡顺序固定为：

1. 供应商
2. API 协议（仅在该供应商有多个可选协议时显示）
3. API Endpoint
4. API Key
5. 模型列表与模型管理
6. 连接测试、保存和其他模型能力配置

协议选择改变时：

- 只在当前端点为空、仍等于上一个协议的默认值或属于当前供应商的可识别默认值时更新端点
- 用户手工填写的端点保持不动
- 模型默认值仅在模型列表为空或仍为供应商默认模型时更新
- 不创建第二份状态；当前 Compose 页面和 `ModelConfigManager` 仍是唯一 owner

## 协议选项

协议选项统一来自 `ApiProviderConfigs` 的显式 protocol catalog。多协议供应商显示选择器，
单协议供应商不显示无意义的选择器，但供应商摘要仍显示其协议能力。

多协议选择器第一项为“自动识别”。它是一次配置动作：

- 识别成功时提示“已识别：协议名称”，关闭弹层并把当前协议改为识别结果。
- 识别失败时保持弹层和当前协议不变，提示用户手动选择。
- 不发送网络探测请求，不在连接测试或正式聊天请求中尝试其他协议。

原生协议采用供应商专名：

- Google：`Gemini 原生 API`
- MNN：`MNN 端侧推理`
- llama.cpp：`llama.cpp 端侧推理`

其余协议统一命名：

- `OpenAI Chat Completions`
- `OpenAI Responses API`
- `Anthropic Messages API`

只有一个协议的供应商不显示选择器，而在端点摘要中显示当前协议。协议不可用、端点不合法、
API Key 缺失和连接测试失败都必须保留现有的可见错误状态和日志，不以静默改写配置解决。

## 资源与文档

新增 section、protocol、endpoint summary 和能力说明的 Android string resource，七份
当前语言资源保持同一键集合。文案不把 `OPENAI_RESPONSES_GENERIC` 等内部 ID 暴露为产品
供应商名称；内部 ID 只在诊断日志或明确的错误上下文出现。
