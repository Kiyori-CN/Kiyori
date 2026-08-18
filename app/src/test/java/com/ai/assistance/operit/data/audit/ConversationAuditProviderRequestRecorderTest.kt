package com.ai.assistance.operit.data.audit

import com.ai.assistance.operit.api.chat.llmprovider.ProviderRequestContext
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import com.ai.assistance.operit.data.model.ToolParameterSchema
import com.ai.assistance.operit.data.model.ToolPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationAuditProviderRequestRecorderTest {
    @Test
    fun `snapshot preserves provider semantics and redacts credential parameter values`() {
        val snapshot =
            ConversationAuditProviderRequestRecorder.buildSnapshot(
                providerRequestContext =
                    ProviderRequestContext(
                        localExecutionId = "execution-1",
                        chatId = "chat-1",
                        messageTimestamp = 123L,
                        variantIndex = 2,
                        hopOrdinal = 1,
                    ),
                requestHistory =
                    listOf(
                        PromptTurn(PromptTurnKind.SYSTEM, "system"),
                        PromptTurn(
                            kind = PromptTurnKind.USER,
                            content = "question",
                            metadata = mapOf("attachment" to "notes.txt"),
                        ),
                    ),
                modelParameters =
                    listOf(
                        parameter(
                            id = "temperature",
                            apiName = "temperature",
                            value = 0.7f,
                        ),
                        parameter(
                            id = "custom_api_key",
                            apiName = "api_key",
                            value = "must-not-persist",
                        ),
                    ),
                availableTools =
                    listOf(
                        ToolPrompt(
                            name = "fetch",
                            description = "Fetch data",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "access_token",
                                        description = "Temporary access token",
                                        default = "tool-secret",
                                    )
                                ),
                        )
                    ),
                providerModel = "OPENAI_RESPONSES:gpt-test",
                modelConfig =
                    ModelConfigData(
                        id = "config-1",
                        name = "Test",
                        apiKey = "config-secret",
                        modelName = "gpt-test",
                        customHeaders = """{"Authorization":"Bearer header-secret"}""",
                    ),
                enableThinking = true,
                stream = true,
            )

        assertEquals("OPENAI_RESPONSES:gpt-test", snapshot["providerModel"])
        assertEquals(true, snapshot["enableThinking"])
        assertEquals(true, snapshot["stream"])

        val requestContext = snapshot["providerRequestContext"] as Map<*, *>
        assertEquals("execution-1", requestContext["localExecutionId"])
        assertEquals(1, requestContext["hopOrdinal"])

        val history = snapshot["requestHistory"] as List<*>
        assertEquals(2, history.size)
        assertEquals("question", (history[1] as Map<*, *>)["content"])

        val parameters = snapshot["modelParameters"] as List<*>
        assertEquals(0.7f, (parameters[0] as Map<*, *>)["currentValue"])
        assertEquals(
            "[REDACTED:credential]",
            (parameters[1] as Map<*, *>)["currentValue"],
        )

        val tools = snapshot["availableTools"] as List<*>
        val toolParameters = (tools.single() as Map<*, *>)["parametersStructured"] as List<*>
        assertEquals(
            "[REDACTED:credential]",
            (toolParameters.single() as Map<*, *>)["default"],
        )

        val modelConfig = snapshot["modelConfig"] as Map<*, *>
        assertEquals(true, modelConfig["apiKeyConfigured"])
        assertNull(modelConfig["apiKey"])
    }

    private fun parameter(
        id: String,
        apiName: String,
        value: Any,
    ): ModelParameter<Any> =
        ModelParameter(
            id = id,
            name = id,
            apiName = apiName,
            defaultValue = value,
            currentValue = value,
            isEnabled = true,
            valueType =
                when (value) {
                    is Float -> ParameterValueType.FLOAT
                    else -> ParameterValueType.STRING
                },
            category = ParameterCategory.OTHER,
        )
}
