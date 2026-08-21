package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class OpenAIReasoningRequestFeatureCompilerTest {
    @Test
    fun ordinaryChatModel_writesTheThreeLevelReasoningEffortContract() {
        val requestJson = JSONObject()

        OpenAIChatRequestFeatureCompiler.apply(
            requestJson = requestJson,
            compiledRequest =
                compile(
                    providerType = ApiProviderType.OPENAI,
                    modelName = "gpt-5.4-mini",
                    apiEndpoint = "https://api.openai.com/v1/chat/completions",
                    enableThinking = true,
                    qualityLevel = 5,
                ),
        )

        assertEquals("high", requestJson.getString("reasoning_effort"))
    }

    @Test
    fun gpt56Responses_writesTheCodexFiveLevelReasoningObject() {
        val requestJson = JSONObject()

        OpenAIResponsesRequestFeatureCompiler.apply(
            requestJson = requestJson,
            compiledRequest =
                compile(
                    providerType = ApiProviderType.OPENAI_RESPONSES,
                    modelName = "gpt-5.6-preview",
                    apiEndpoint = "https://api.openai.com/v1/responses",
                    enableThinking = true,
                    qualityLevel = 5,
                ),
            stream = false,
        )

        val reasoning = requestJson.optJSONObject("reasoning")
        assertNotNull(reasoning)
        assertEquals("max", reasoning?.getString("effort"))
        assertEquals("auto", reasoning?.getString("summary"))
        assertFalse(requestJson.has("background"))
    }

    @Test
    fun disabledOrdinaryResponses_writesNoneWithoutOfficialReasoningExtras() {
        val requestJson = JSONObject()

        OpenAIResponsesRequestFeatureCompiler.apply(
            requestJson = requestJson,
            compiledRequest =
                compile(
                    providerType = ApiProviderType.OPENAI_RESPONSES_GENERIC,
                    modelName = "gpt-5.4-mini",
                    apiEndpoint = "https://relay.example/v1/responses",
                    enableThinking = false,
                    qualityLevel = 4,
                ),
            stream = true,
        )

        val reasoning = requestJson.getJSONObject("reasoning")
        assertEquals("none", reasoning.getString("effort"))
        assertFalse(reasoning.has("summary"))
        assertFalse(requestJson.has("include"))
        assertFalse(requestJson.has("background"))
    }

    private fun compile(
        providerType: ApiProviderType,
        modelName: String,
        apiEndpoint: String,
        enableThinking: Boolean,
        qualityLevel: Int,
    ): CompiledModelRequest {
        return ModelRequestCompiler.compile(
            profile =
                ModelCapabilityResolver.resolve(
                    providerType = providerType,
                    modelName = modelName,
                    apiEndpoint = apiEndpoint,
                ),
            intent =
                UserExecutionIntent(
                    enableThinking = enableThinking,
                    thinkingQualityLevel = qualityLevel,
                ),
        )
    }
}
