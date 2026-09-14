package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.core.config.SystemToolPrompts
import com.ai.assistance.operit.data.model.ApiProtocol
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import org.junit.Assert.*
import org.junit.Test

class ModelMediaInputCapabilitiesTest {
    private fun config(provider: ApiProviderType, protocol: ApiProtocol, endpoint: String = "https://example.test/v1") = ModelConfigData(
        id = "test", name = "test", apiProviderType = provider, apiProtocol = protocol, apiEndpoint = endpoint,
        enableDirectImageProcessing = true, enableDirectAudioProcessing = true, enableDirectVideoProcessing = true)

    @Test fun officialAndCompatibleProtocolsUseTheirActualEncoders() {
        assertEquals(ModelMediaInputCapabilities(true, false, false), ModelMediaInputCapabilities.resolve(config(ApiProviderType.OPENAI, ApiProtocol.OPENAI_RESPONSES, "https://api.openai.com/v1")))
        assertEquals(ModelMediaInputCapabilities(true, true, false), ModelMediaInputCapabilities.resolve(config(ApiProviderType.OPENAI, ApiProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.openai.com/v1")))
        assertEquals(ModelMediaInputCapabilities(true, true, false), ModelMediaInputCapabilities.resolve(config(ApiProviderType.OPENAI_RESPONSES_GENERIC, ApiProtocol.OPENAI_RESPONSES)))
        assertEquals(ModelMediaInputCapabilities(true, true, true), ModelMediaInputCapabilities.resolve(config(ApiProviderType.OPENAI_GENERIC, ApiProtocol.OPENAI_CHAT_COMPLETIONS)))
    }

    @Test fun protocolSelectionTakesPrecedenceOverProviderName() {
        assertEquals(ModelMediaInputCapabilities(true, false, false), ModelMediaInputCapabilities.resolve(config(ApiProviderType.GOOGLE, ApiProtocol.ANTHROPIC_MESSAGES)))
        assertEquals(ModelMediaInputCapabilities(true, true, true), ModelMediaInputCapabilities.resolve(config(ApiProviderType.ANTHROPIC, ApiProtocol.OPENAI_CHAT_COMPLETIONS)))
    }

    @Test fun disabledSwitchesRemainDisabled() {
        val config = config(ApiProviderType.GOOGLE, ApiProtocol.PROVIDER_NATIVE).copy(
            enableDirectImageProcessing = false, enableDirectAudioProcessing = false, enableDirectVideoProcessing = false)
        assertEquals(ModelMediaInputCapabilities(false, false, false), ModelMediaInputCapabilities.resolve(config))
    }

    @Test fun nativeTextOnlyLlamaDoesNotAdvertiseConfiguredMediaSwitches() {
        assertEquals(ModelMediaInputCapabilities(false, false, false), ModelMediaInputCapabilities.resolve(config(ApiProviderType.LLAMA_CPP, ApiProtocol.PROVIDER_NATIVE)))
    }

    @Test fun mnnVideoNeedsAtLeastOneEnabledFrameOrAudioInput() {
        val config = config(ApiProviderType.MNN, ApiProtocol.PROVIDER_NATIVE).copy(enableDirectImageProcessing = false, enableDirectAudioProcessing = false)
        assertFalse(ModelMediaInputCapabilities.resolve(config).video)
        assertTrue(ModelMediaInputCapabilities.resolve(config.copy(enableDirectImageProcessing = true)).video)
    }

    @Test fun bothAgentSchemasExposeExactlyTheEnabledMediaParameters() {
        for (mask in 0..7) {
            val image = mask and 1 != 0; val audio = mask and 2 != 0; val video = mask and 4 != 0
            val groups = listOf(SystemToolPrompts.getAIAllCategoriesEn(chatModelHasDirectImage = image, chatModelHasDirectAudio = audio, chatModelHasDirectVideo = video),
                SystemToolPrompts.getAIAllCategoriesCn(chatModelHasDirectImage = image, chatModelHasDirectAudio = audio, chatModelHasDirectVideo = video))
            for (group in groups) {
                val tool = group.flatMap { it.tools }.single { it.name == "read_file" }
                val names = tool.parametersStructured!!.map { it.name }
                assertEquals(image, "direct_image" in names); assertEquals(audio, "direct_audio" in names); assertEquals(video, "direct_video" in names)
                if (mask != 0) assertTrue(tool.description.contains("direct_"))
                assertFalse("direct_media" in names)
            }
        }
    }
}
