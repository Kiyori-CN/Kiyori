package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProtocol
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData

/** 当前配置快照的纯投影；媒体开关与协议编码能力取交集，不另存模型能力。 */
data class ModelMediaInputCapabilities(val image: Boolean, val audio: Boolean, val video: Boolean) {
    companion object {
        fun resolve(config: ModelConfigData): ModelMediaInputCapabilities {
            val protocol = config.apiProtocol
            if (protocol == ApiProtocol.PROVIDER_NATIVE && config.apiProviderType == ApiProviderType.LLAMA_CPP) {
                return ModelMediaInputCapabilities(false, false, false)
            }
            val anthropic = protocol == ApiProtocol.ANTHROPIC_MESSAGES ||
                protocol == ApiProtocol.PROVIDER_NATIVE && config.apiProviderType in setOf(ApiProviderType.ANTHROPIC, ApiProviderType.ANTHROPIC_GENERIC)
            val responses = protocol == ApiProtocol.OPENAI_RESPONSES
            val officialOpenAi = OpenAiEndpointContract.resolve(
                if (responses) ApiProviderType.OPENAI_RESPONSES else ApiProviderType.OPENAI,
                config.apiEndpoint,
            ) == ProviderContractAuthority.OPENAI_OFFICIAL
            // Responses 视频尚无本机编码合同；官方音频输入使用 Chat Completions/Realtime。
            // compatible Responses 保留已有显式 input_audio 编码，但不推断服务端已实现。
            return ModelMediaInputCapabilities(
                image = config.enableDirectImageProcessing,
                audio = config.enableDirectAudioProcessing && !anthropic && !(responses && officialOpenAi),
                video = config.enableDirectVideoProcessing && !anthropic && !responses && !officialOpenAi &&
                    (config.apiProviderType != ApiProviderType.MNN || config.enableDirectImageProcessing || config.enableDirectAudioProcessing),
            )
        }
    }
}
