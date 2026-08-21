package com.ai.assistance.operit.data.collects

import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ApiProtocol
import java.net.URI

data class ProviderEndpointOption(
    val endpoint: String,
    val label: String,
    val modelListEndpoint: String = "",
)

data class ProviderProtocolConfig(
    val defaultModelName: String = "",
    val defaultApiEndpoint: String = "",
    val endpointOptions: List<ProviderEndpointOption> = emptyList(),
    val defaultModelListEndpoint: String = "",
)

data class ProviderApiConfig(
    val providerType: ApiProviderType,
    val defaultProtocol: ApiProtocol,
    val protocolConfigs: Map<ApiProtocol, ProviderProtocolConfig>,
    val requiresApiKey: Boolean = true,
) {
    init {
        require(protocolConfigs.isNotEmpty()) {
            "${providerType.name} must declare at least one API protocol"
        }
        require(defaultProtocol in protocolConfigs) {
            "${providerType.name} default protocol must be present in protocolConfigs"
        }
    }
}

object ApiProviderConfigs {
    private fun protocolConfig(
        defaultModelName: String = "",
        defaultApiEndpoint: String = "",
        endpointOptions: List<ProviderEndpointOption> = emptyList(),
        defaultModelListEndpoint: String = "",
    ): ProviderProtocolConfig =
        ProviderProtocolConfig(
            defaultModelName = defaultModelName,
            defaultApiEndpoint = defaultApiEndpoint,
            endpointOptions = endpointOptions,
            defaultModelListEndpoint = defaultModelListEndpoint,
        )

    private fun singleProtocolConfig(
        providerType: ApiProviderType,
        protocol: ApiProtocol,
        defaultModelName: String = "",
        defaultApiEndpoint: String = "",
        endpointOptions: List<ProviderEndpointOption> = emptyList(),
        defaultModelListEndpoint: String = "",
        requiresApiKey: Boolean = true,
    ): ProviderApiConfig =
        ProviderApiConfig(
            providerType = providerType,
            defaultProtocol = protocol,
            protocolConfigs =
                linkedMapOf(
                    protocol to
                        protocolConfig(
                            defaultModelName = defaultModelName,
                            defaultApiEndpoint = defaultApiEndpoint,
                            endpointOptions = endpointOptions,
                            defaultModelListEndpoint = defaultModelListEndpoint,
                        )
                ),
            requiresApiKey = requiresApiKey,
        )

    private val configs: Map<ApiProviderType, ProviderApiConfig> = listOf(
        ProviderApiConfig(
            providerType = ApiProviderType.OPENAI,
            defaultProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultModelName = "gpt-4o",
                            defaultApiEndpoint = "https://api.openai.com/v1/chat/completions",
                            defaultModelListEndpoint = "https://api.openai.com/v1/models",
                        ),
                    ApiProtocol.OPENAI_RESPONSES to
                        protocolConfig(
                            defaultModelName = "gpt-4o",
                            defaultApiEndpoint = "https://api.openai.com/v1/responses",
                            defaultModelListEndpoint = "https://api.openai.com/v1/models",
                        ),
                ),
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.OPENAI_RESPONSES,
            protocol = ApiProtocol.OPENAI_RESPONSES,
            defaultModelName = "gpt-4o",
            defaultApiEndpoint = "https://api.openai.com/v1/responses",
            defaultModelListEndpoint = "https://api.openai.com/v1/models",
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.OPENAI_RESPONSES_GENERIC,
            protocol = ApiProtocol.OPENAI_RESPONSES,
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.OPENAI_GENERIC,
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.ANTHROPIC,
            defaultProtocol = ApiProtocol.ANTHROPIC_MESSAGES,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.ANTHROPIC_MESSAGES to
                        protocolConfig(
                            defaultModelName = "claude-3-opus-20240229",
                            defaultApiEndpoint = "https://api.anthropic.com/v1/messages",
                            defaultModelListEndpoint = "https://api.anthropic.com/v1/models",
                        ),
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultModelName = "claude-3-opus-20240229",
                            defaultApiEndpoint = "https://api.anthropic.com/v1/chat/completions",
                            defaultModelListEndpoint = "https://api.anthropic.com/v1/models",
                        ),
                ),
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.ANTHROPIC_GENERIC,
            protocol = ApiProtocol.ANTHROPIC_MESSAGES,
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.XAI,
            defaultProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultModelName = "grok-4.6",
                            defaultApiEndpoint = "https://api.x.ai/v1/chat/completions",
                            defaultModelListEndpoint = "https://api.x.ai/v1/models",
                        ),
                    ApiProtocol.OPENAI_RESPONSES to
                        protocolConfig(
                            defaultModelName = "grok-4.6",
                            defaultApiEndpoint = "https://api.x.ai/v1/responses",
                            defaultModelListEndpoint = "https://api.x.ai/v1/models",
                        ),
                ),
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.GOOGLE,
            defaultProtocol = ApiProtocol.PROVIDER_NATIVE,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.PROVIDER_NATIVE to
                        protocolConfig(
                            defaultModelName = "gemini-2.0-flash",
                            defaultApiEndpoint =
                                "https://generativelanguage.googleapis.com/v1beta/models",
                            defaultModelListEndpoint =
                                "https://generativelanguage.googleapis.com/v1beta/models",
                        ),
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultModelName = "gemini-2.0-flash",
                            defaultApiEndpoint =
                                "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                            defaultModelListEndpoint =
                                "https://generativelanguage.googleapis.com/v1beta/openai/models",
                        ),
                ),
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.GEMINI_GENERIC,
            protocol = ApiProtocol.PROVIDER_NATIVE,
            defaultModelName = "gemini-2.0-flash",
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.DEEPSEEK,
            defaultProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultModelName = "deepseek-v4-flash",
                            defaultApiEndpoint =
                                "https://api.deepseek.com/v1/chat/completions",
                            defaultModelListEndpoint = "https://api.deepseek.com/v1/models",
                        ),
                    ApiProtocol.OPENAI_RESPONSES to
                        protocolConfig(
                            defaultModelName = "deepseek-v4-flash",
                            defaultApiEndpoint = "https://api.deepseek.com/v1/responses",
                            defaultModelListEndpoint = "https://api.deepseek.com/v1/models",
                        ),
                    ApiProtocol.ANTHROPIC_MESSAGES to
                        protocolConfig(
                            defaultModelName = "deepseek-v4-flash",
                            defaultApiEndpoint =
                                "https://api.deepseek.com/anthropic/v1/messages",
                            defaultModelListEndpoint = "https://api.deepseek.com/v1/models",
                        ),
                ),
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.BAIDU,
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultModelName = "ernie-bot-4",
            defaultApiEndpoint =
                "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions",
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.ALIYUN,
            defaultProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultModelName = "qwen-max",
                            defaultApiEndpoint =
                                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                            defaultModelListEndpoint =
                                "https://dashscope.aliyuncs.com/compatible-mode/v1/models",
                        ),
                    ApiProtocol.OPENAI_RESPONSES to
                        protocolConfig(
                            defaultModelName = "qwen-max",
                            defaultApiEndpoint =
                                "https://dashscope.aliyuncs.com/compatible-mode/v1/responses",
                            defaultModelListEndpoint =
                                "https://dashscope.aliyuncs.com/compatible-mode/v1/models",
                        ),
                ),
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.XUNFEI,
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultModelName = "spark3.5",
            defaultApiEndpoint = "https://spark-api-open.xf-yun.com/v2/chat/completions",
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.ZHIPU,
            defaultProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultModelName = "glm-4.5",
                            defaultApiEndpoint =
                                "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                            endpointOptions =
                                listOf(
                                    ProviderEndpointOption(
                                        endpoint =
                                            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                                        label = "CN standard",
                                        modelListEndpoint =
                                            "https://open.bigmodel.cn/api/paas/v4/models",
                                    ),
                                    ProviderEndpointOption(
                                        endpoint =
                                            "https://open.bigmodel.cn/api/coding/paas/v4/chat/completions",
                                        label = "CN coding",
                                        modelListEndpoint =
                                            "https://open.bigmodel.cn/api/coding/paas/v4/models",
                                    ),
                                    ProviderEndpointOption(
                                        endpoint =
                                            "https://api.z.ai/api/paas/v4/chat/completions",
                                        label = "International standard",
                                        modelListEndpoint =
                                            "https://api.z.ai/api/paas/v4/models",
                                    ),
                                    ProviderEndpointOption(
                                        endpoint =
                                            "https://api.z.ai/api/coding/paas/v4/chat/completions",
                                        label = "International coding",
                                        modelListEndpoint =
                                            "https://api.z.ai/api/coding/paas/v4/models",
                                    ),
                                ),
                            defaultModelListEndpoint =
                                "https://open.bigmodel.cn/api/paas/v4/models",
                        ),
                    ApiProtocol.ANTHROPIC_MESSAGES to
                        protocolConfig(
                            defaultModelName = "glm-4.5",
                            defaultApiEndpoint =
                                "https://open.bigmodel.cn/api/anthropic/v1/messages",
                            defaultModelListEndpoint =
                                "https://open.bigmodel.cn/api/paas/v4/models",
                        ),
                ),
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.BAICHUAN,
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultModelName = "baichuan4",
            defaultApiEndpoint = "https://api.baichuan-ai.com/v1/chat/completions",
            defaultModelListEndpoint = "https://api.baichuan-ai.com/v1/models",
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.MOONSHOT,
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultModelName = "moonshot-v1-128k",
            defaultApiEndpoint = "https://api.moonshot.cn/v1/chat/completions",
            endpointOptions = listOf(
                ProviderEndpointOption(
                    endpoint = "https://api.moonshot.cn/v1/chat/completions",
                    label = "China (moonshot.cn)",
                    modelListEndpoint = "https://api.moonshot.cn/v1/models",
                ),
                ProviderEndpointOption(
                    endpoint = "https://api.moonshot.ai/v1/chat/completions",
                    label = "International (moonshot.ai)",
                    modelListEndpoint = "https://api.moonshot.ai/v1/models",
                ),
                ProviderEndpointOption(
                    endpoint = "https://api.kimi.com/coding/v1/chat/completions",
                    label = "Kimi Code (api.kimi.com)",
                ),
            ),
            defaultModelListEndpoint = "https://api.moonshot.cn/v1/models",
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.MIMO,
            defaultProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultModelName = "mimo-v2.5-pro",
                            defaultApiEndpoint =
                                "https://api.xiaomimimo.com/v1/chat/completions",
                            defaultModelListEndpoint =
                                "https://api.xiaomimimo.com/v1/models",
                        ),
                    ApiProtocol.OPENAI_RESPONSES to
                        protocolConfig(
                            defaultModelName = "mimo-v2.5-pro",
                            defaultApiEndpoint = "https://api.xiaomimimo.com/v1/responses",
                            defaultModelListEndpoint =
                                "https://api.xiaomimimo.com/v1/models",
                        ),
                    ApiProtocol.ANTHROPIC_MESSAGES to
                        protocolConfig(
                            defaultModelName = "mimo-v2.5-pro",
                            defaultApiEndpoint =
                                "https://api.xiaomimimo.com/anthropic/v1/messages",
                            defaultModelListEndpoint =
                                "https://api.xiaomimimo.com/v1/models",
                        ),
                ),
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.MISTRAL,
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultModelName = "codestral-latest",
            defaultApiEndpoint = "https://codestral.mistral.ai/v1/chat/completions",
            defaultModelListEndpoint = "https://codestral.mistral.ai/v1/models",
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.SILICONFLOW,
            defaultProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultModelName = "yi-1.5-34b",
                            defaultApiEndpoint =
                                "https://api.siliconflow.cn/v1/chat/completions",
                            defaultModelListEndpoint =
                                "https://api.siliconflow.cn/v1/models",
                        ),
                    ApiProtocol.ANTHROPIC_MESSAGES to
                        protocolConfig(
                            defaultModelName = "yi-1.5-34b",
                            defaultApiEndpoint = "https://api.siliconflow.cn/v1/messages",
                            defaultModelListEndpoint =
                                "https://api.siliconflow.cn/v1/models",
                        ),
                ),
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.IFLOW,
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultModelName = "TBStars2-200B-A13B",
            defaultApiEndpoint = "https://apis.iflow.cn/v1/chat/completions",
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.OPENROUTER,
            defaultProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultModelName = "google/gemini-pro",
                            defaultApiEndpoint =
                                "https://openrouter.ai/api/v1/chat/completions",
                            defaultModelListEndpoint =
                                "https://openrouter.ai/api/v1/models",
                        ),
                    ApiProtocol.OPENAI_RESPONSES to
                        protocolConfig(
                            defaultModelName = "google/gemini-pro",
                            defaultApiEndpoint = "https://openrouter.ai/api/v1/responses",
                            defaultModelListEndpoint =
                                "https://openrouter.ai/api/v1/models",
                        ),
                ),
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.FOUR_ROUTER,
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultModelName = "gpt-5.4-mini",
            defaultApiEndpoint = "https://4router.net/v1/chat/completions",
            defaultModelListEndpoint = "https://4router.net/v1/models",
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.NOUS_PORTAL,
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultApiEndpoint = "https://inference-api.nousresearch.com/v1/chat/completions",
            defaultModelListEndpoint = "https://inference-api.nousresearch.com/v1/models",
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.INFINIAI,
            defaultProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultModelName = "infini-mini",
                            defaultApiEndpoint =
                                "https://cloud.infini-ai.com/maas/v1/chat/completions",
                            defaultModelListEndpoint =
                                "https://cloud.infini-ai.com/maas/v1/models",
                        ),
                    ApiProtocol.ANTHROPIC_MESSAGES to
                        protocolConfig(
                            defaultModelName = "infini-mini",
                            defaultApiEndpoint =
                                "https://cloud.infini-ai.com/maas/v1/messages",
                            defaultModelListEndpoint =
                                "https://cloud.infini-ai.com/maas/v1/models",
                        ),
                ),
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.ALIPAY_BAILING,
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultModelName = "Ling-1T",
            defaultApiEndpoint = "https://api.tbox.cn/api/llm/v1/chat/completions",
            defaultModelListEndpoint = "https://api.tbox.cn/api/llm/v1/models",
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.DOUBAO,
            defaultProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultModelName = "Doubao-pro-4k",
                            defaultApiEndpoint =
                                "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
                            endpointOptions =
                                listOf(
                                    ProviderEndpointOption(
                                        endpoint =
                                            "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
                                        label = "CN standard",
                                        modelListEndpoint =
                                            "https://ark.cn-beijing.volces.com/api/v3/models",
                                    ),
                                    ProviderEndpointOption(
                                        endpoint =
                                            "https://ark.cn-beijing.volces.com/api/coding/v3/chat/completions",
                                        label = "CN coding",
                                        modelListEndpoint =
                                            "https://ark.cn-beijing.volces.com/api/coding/v3/models",
                                    ),
                                ),
                            defaultModelListEndpoint =
                                "https://ark.cn-beijing.volces.com/api/v3/models",
                        ),
                    ApiProtocol.OPENAI_RESPONSES to
                        protocolConfig(
                            defaultModelName = "Doubao-pro-4k",
                            defaultApiEndpoint =
                                "https://ark.cn-beijing.volces.com/api/v3/responses",
                            defaultModelListEndpoint =
                                "https://ark.cn-beijing.volces.com/api/v3/models",
                        ),
                ),
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.NVIDIA,
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultModelName = "nvidia/nemotron-3-nano-30b-a3b",
            defaultApiEndpoint = "https://integrate.api.nvidia.com/v1/chat/completions",
            defaultModelListEndpoint = "https://integrate.api.nvidia.com/v1/models",
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.LMSTUDIO,
            defaultProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultModelName = "meta-llama-3.1-8b-instruct",
                            defaultApiEndpoint =
                                "http://localhost:1234/v1/chat/completions",
                            defaultModelListEndpoint =
                                "http://localhost:1234/v1/models",
                        ),
                    ApiProtocol.OPENAI_RESPONSES to
                        protocolConfig(
                            defaultModelName = "meta-llama-3.1-8b-instruct",
                            defaultApiEndpoint = "http://localhost:1234/v1/responses",
                            defaultModelListEndpoint =
                                "http://localhost:1234/v1/models",
                        ),
                    ApiProtocol.ANTHROPIC_MESSAGES to
                        protocolConfig(
                            defaultModelName = "meta-llama-3.1-8b-instruct",
                            defaultApiEndpoint = "http://localhost:1234/v1/messages",
                            defaultModelListEndpoint =
                                "http://localhost:1234/v1/models",
                        ),
                ),
            requiresApiKey = false,
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.OLLAMA,
            defaultProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultApiEndpoint =
                                "http://localhost:11434/v1/chat/completions",
                            defaultModelListEndpoint =
                                "http://localhost:11434/v1/models",
                        ),
                    ApiProtocol.OPENAI_RESPONSES to
                        protocolConfig(
                            defaultApiEndpoint = "http://localhost:11434/v1/responses",
                            defaultModelListEndpoint =
                                "http://localhost:11434/v1/models",
                        ),
                    ApiProtocol.ANTHROPIC_MESSAGES to
                        protocolConfig(
                            defaultApiEndpoint = "http://localhost:11434/v1/messages",
                            defaultModelListEndpoint =
                                "http://localhost:11434/v1/models",
                        ),
                ),
            requiresApiKey = false,
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.OPENAI_LOCAL,
            defaultProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultApiEndpoint =
                                "http://localhost:8000/v1/chat/completions",
                            defaultModelListEndpoint =
                                "http://localhost:8000/v1/models",
                        ),
                    ApiProtocol.OPENAI_RESPONSES to
                        protocolConfig(
                            defaultApiEndpoint = "http://localhost:8000/v1/responses",
                            defaultModelListEndpoint =
                                "http://localhost:8000/v1/models",
                        ),
                ),
            requiresApiKey = false,
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.MNN,
            protocol = ApiProtocol.PROVIDER_NATIVE,
            requiresApiKey = false,
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.LLAMA_CPP,
            protocol = ApiProtocol.PROVIDER_NATIVE,
            requiresApiKey = false,
        ),
        singleProtocolConfig(
            providerType = ApiProviderType.PPINFRA,
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultModelName = "gpt-4o-mini",
            defaultApiEndpoint = "https://api.ppinfra.com/openai/v1/chat/completions",
            defaultModelListEndpoint = "https://api.ppinfra.com/openai/v1/models",
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.NOVITA,
            defaultProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to
                        protocolConfig(
                            defaultModelName = "moonshotai/kimi-k2.5",
                            defaultApiEndpoint =
                                "https://api.novita.ai/openai/v1/chat/completions",
                            defaultModelListEndpoint =
                                "https://api.novita.ai/openai/v1/models",
                        ),
                    ApiProtocol.ANTHROPIC_MESSAGES to
                        protocolConfig(
                            defaultModelName = "moonshotai/kimi-k2.5",
                            defaultApiEndpoint =
                                "https://api.novita.ai/anthropic/v1/messages",
                            defaultModelListEndpoint =
                                "https://api.novita.ai/openai/v1/models",
                        ),
                ),
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.OTHER,
            defaultProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            protocolConfigs =
                linkedMapOf(
                    ApiProtocol.OPENAI_CHAT_COMPLETIONS to protocolConfig(),
                    ApiProtocol.OPENAI_RESPONSES to protocolConfig(),
                    ApiProtocol.ANTHROPIC_MESSAGES to protocolConfig(),
                ),
        ),
    ).associateBy(ProviderApiConfig::providerType)

    fun get(providerType: ApiProviderType): ProviderApiConfig {
        return requireNotNull(configs[providerType]) {
            "Provider API config is not declared: ${providerType.name}"
        }
    }

    fun getDefaultProtocol(providerType: ApiProviderType): ApiProtocol {
        return get(providerType).defaultProtocol
    }

    fun getSupportedProtocols(providerType: ApiProviderType): List<ApiProtocol> {
        return get(providerType).protocolConfigs.keys.toList()
    }

    fun getProtocolConfig(
        providerType: ApiProviderType,
        protocol: ApiProtocol,
    ): ProviderProtocolConfig {
        return requireNotNull(get(providerType).protocolConfigs[protocol]) {
            "${providerType.name} does not support ${protocol.name}"
        }
    }

    fun getDefaultModelName(providerType: ApiProviderType): String {
        val config = get(providerType)
        return getProtocolConfig(providerType, config.defaultProtocol).defaultModelName
    }

    fun getDefaultModelName(providerType: ApiProviderType, protocol: ApiProtocol): String {
        return getProtocolConfig(providerType, protocol).defaultModelName
    }

    fun getDefaultApiEndpoint(providerType: ApiProviderType): String {
        val config = get(providerType)
        return getProtocolConfig(providerType, config.defaultProtocol).defaultApiEndpoint
    }

    fun getDefaultApiEndpoint(providerType: ApiProviderType, protocol: ApiProtocol): String {
        return getProtocolConfig(providerType, protocol).defaultApiEndpoint
    }

    fun getEndpointOptions(providerType: ApiProviderType): List<ProviderEndpointOption>? {
        val config = get(providerType)
        return getEndpointOptions(providerType, config.defaultProtocol)
    }

    fun getEndpointOptions(
        providerType: ApiProviderType,
        protocol: ApiProtocol,
    ): List<ProviderEndpointOption>? {
        return getProtocolConfig(providerType, protocol)
            .endpointOptions
            .takeIf { it.isNotEmpty() }
    }

    fun getModelListEndpoint(
        providerType: ApiProviderType,
        protocol: ApiProtocol,
        apiEndpoint: String,
    ): String? {
        val protocolConfig = getProtocolConfig(providerType, protocol)
        val normalizedEndpoint = apiEndpoint.trim().removeSuffix("/")
        protocolConfig.endpointOptions
            .firstOrNull { option ->
                option.endpoint.trim().removeSuffix("/") == normalizedEndpoint
            }
            ?.modelListEndpoint
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        return protocolConfig.defaultModelListEndpoint.takeIf {
            it.isNotBlank() &&
                protocolConfig.defaultApiEndpoint.trim().removeSuffix("/") == normalizedEndpoint
        }
    }

    fun requiresApiKey(providerType: ApiProviderType, apiEndpoint: String = ""): Boolean {
        if (!get(providerType).requiresApiKey) {
            return false
        }

        return !isLoopbackEndpoint(apiEndpoint)
    }

    fun requiresApiKey(providerTypeId: String, apiEndpoint: String = ""): Boolean {
        val providerType = ApiProviderType.fromProviderTypeId(providerTypeId) ?: return !isLoopbackEndpoint(apiEndpoint)
        return requiresApiKey(providerType, apiEndpoint)
    }

    fun isDefaultModelName(modelName: String): Boolean {
        return configs.values.any { providerConfig ->
            providerConfig.protocolConfigs.values.any { protocolConfig ->
                protocolConfig.defaultModelName == modelName
            }
        }
    }

    fun isDefaultApiEndpoint(endpoint: String): Boolean {
        return configs.values.any { providerConfig ->
            providerConfig.protocolConfigs.values.any { protocolConfig ->
                protocolConfig.defaultApiEndpoint == endpoint ||
                    protocolConfig.endpointOptions.any { option -> option.endpoint == endpoint }
            }
        }
    }

    fun isLoopbackEndpoint(apiEndpoint: String): Boolean {
        if (apiEndpoint.isBlank()) {
            return false
        }

        val normalizedEndpoint = apiEndpoint.trim().lowercase()
        return try {
            val host = URI(apiEndpoint).host
            if (host != null) {
                isLoopbackHost(host)
            } else {
                isLoopbackEndpointText(normalizedEndpoint)
            }
        } catch (_: Exception) {
            isLoopbackEndpointText(normalizedEndpoint)
        }
    }

    private fun isLoopbackHost(host: String): Boolean {
        return when (host.lowercase().trim('[', ']')) {
            "localhost",
            "127.0.0.1",
            "::1",
            "0.0.0.0",
            "10.0.2.2" -> true
            else -> false
        }
    }

    private fun isLoopbackEndpointText(apiEndpoint: String): Boolean {
        return apiEndpoint.startsWith("localhost:") ||
            apiEndpoint.startsWith("127.0.0.1:") ||
            apiEndpoint.startsWith("[::1]:") ||
            apiEndpoint.startsWith("::1:") ||
            apiEndpoint.startsWith("0.0.0.0:") ||
            apiEndpoint.startsWith("10.0.2.2:")
    }
}
