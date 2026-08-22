package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterValueType
import com.ai.assistance.operit.data.model.ToolParameterSchema
import com.ai.assistance.operit.data.model.ToolPrompt
import java.nio.charset.StandardCharsets
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.mock

class DeepseekCanonicalRequestTest {
    private val context: Context = mock()
    private lateinit var logMock: MockedStatic<Log>

    @Before
    fun setUpAndroidLog() {
        logMock = Mockito.mockStatic(Log::class.java)
    }

    @After
    fun tearDownAndroidLog() {
        logMock.close()
    }

    @Test
    fun `stream request includes usage and preserves reasoning call IDs and result order`() {
        val request =
            provider()
                .requestJson(
                    context = context,
                    history =
                        listOf(
                            PromptTurn(PromptTurnKind.SYSTEM, "system"),
                            PromptTurn(PromptTurnKind.USER, "question"),
                            PromptTurn(
                                PromptTurnKind.ASSISTANT,
                                "<think>reasoning</think>\n" +
                                    toolCallXml("read_file", "call_123", "path", "one.txt") +
                                    "\n" +
                                    toolCallXml("read_file", "call_456", "path", "two.txt"),
                            ),
                            PromptTurn(
                                PromptTurnKind.TOOL_RESULT,
                                toolResultXml("read_file", "first") +
                                    "\n" +
                                    toolResultXml("read_file", "second"),
                            ),
                        ),
                    stream = true,
                    tools = listOf(readFileTool()),
                )
        val json = JSONObject(request)

        assertTrue(json.getJSONObject("stream_options").getBoolean("include_usage"))
        val messages = json.getJSONArray("messages")
        val assistant = messages.getJSONObject(2)
        assertEquals("reasoning", assistant.getString("reasoning_content"))
        assertEquals("call_123", assistant.getJSONArray("tool_calls").getJSONObject(0).getString("id"))
        assertEquals("call_456", assistant.getJSONArray("tool_calls").getJSONObject(1).getString("id"))
        assertEquals("call_123", messages.getJSONObject(3).getString("tool_call_id"))
        assertEquals("call_456", messages.getJSONObject(4).getString("tool_call_id"))
        assertFalse(request.contains("User cancelled"))
        assertFalse(request.contains("[Empty]"))
    }

    @Test
    fun `non streaming request omits stream options`() {
        val request =
            provider(enableToolCall = false)
                .requestJson(
                    context = context,
                    history = listOf(PromptTurn(PromptTurnKind.USER, "hello")),
                    stream = false,
                    tools = null,
                )

        assertFalse(JSONObject(request).has("stream_options"))
    }

    @Test
    fun `same semantic tools and parameters produce identical request bytes`() {
        val alpha =
            ToolPrompt(
                name = "alpha",
                description = "Alpha",
                parametersStructured =
                    listOf(
                        ToolParameterSchema("zeta", description = "Z"),
                        ToolParameterSchema("beta", description = "B"),
                    ),
            )
        val alphaReordered =
            alpha.copy(parametersStructured = alpha.parametersStructured!!.reversed())
        val zeta = ToolPrompt(name = "zeta", description = "Zeta")
        val temperature = floatParameter("temperature", 0.5f)
        val topP = floatParameter("top_p", 0.9f)
        val history = listOf(PromptTurn(PromptTurnKind.USER, "stable"))

        val first =
            provider()
                .requestJson(
                    context = context,
                    history = history,
                    parameters = listOf(topP, temperature),
                    stream = true,
                    tools = listOf(zeta, alpha),
                )
        val second =
            provider()
                .requestJson(
                    context = context,
                    history = history,
                    parameters = listOf(temperature, topP),
                    stream = true,
                    tools = listOf(alphaReordered, zeta),
                )

        assertEquals(first.toByteArray(StandardCharsets.UTF_8).toList(), second.toByteArray(StandardCharsets.UTF_8).toList())
    }

    @Test
    fun `changing current user input preserves the complete request prefix before that input`() {
        val stableHistory =
            listOf(
                PromptTurn(PromptTurnKind.SYSTEM, "stable-system"),
                PromptTurn(PromptTurnKind.USER, "old-user"),
                PromptTurn(PromptTurnKind.ASSISTANT, "old-assistant"),
            )
        val first =
            provider(enableToolCall = false)
                .requestJson(
                    context = context,
                    history = stableHistory + PromptTurn(PromptTurnKind.USER, "tail-one"),
                    stream = true,
                    tools = null,
                )
        val second =
            provider(enableToolCall = false)
                .requestJson(
                    context = context,
                    history = stableHistory + PromptTurn(PromptTurnKind.USER, "tail-two"),
                    stream = true,
                    tools = null,
                )

        val firstTailIndex = first.indexOf("tail-one")
        val secondTailIndex = second.indexOf("tail-two")
        assertTrue(firstTailIndex > 0)
        assertEquals(first.substring(0, firstTailIndex), second.substring(0, secondTailIndex))
        assertEquals(
            first.substring(firstTailIndex + "tail-one".length),
            second.substring(secondTailIndex + "tail-two".length),
        )
    }

    @Test
    fun `missing extra orphan and non structured tool history fail before submission`() {
        val missing =
            assertThrows<ProviderToolHistoryProtocolException> {
                provider()
                    .requestJson(
                        context = context,
                        history =
                            listOf(
                                PromptTurn(
                                    PromptTurnKind.TOOL_CALL,
                                    toolCallXml("read_file", "call_123", "path", "one.txt"),
                                ),
                            ),
                        stream = true,
                        tools = listOf(readFileTool()),
                    )
            }
        assertEquals(ProviderToolHistoryViolation.MISSING_TOOL_RESULT, missing.violation)

        val extra =
            assertThrows<ProviderToolHistoryProtocolException> {
                provider()
                    .requestJson(
                        context = context,
                        history =
                            listOf(
                                PromptTurn(
                                    PromptTurnKind.TOOL_CALL,
                                    toolCallXml("read_file", "call_123", "path", "one.txt"),
                                ),
                                PromptTurn(
                                    PromptTurnKind.TOOL_RESULT,
                                    toolResultXml("read_file", "first") +
                                        toolResultXml("read_file", "second"),
                                ),
                            ),
                        stream = true,
                        tools = listOf(readFileTool()),
                    )
            }
        assertEquals(ProviderToolHistoryViolation.TOO_MANY_TOOL_RESULTS, extra.violation)

        val orphan =
            assertThrows<ProviderToolHistoryProtocolException> {
                provider()
                    .requestJson(
                        context = context,
                        history =
                            listOf(
                                PromptTurn(
                                    PromptTurnKind.TOOL_RESULT,
                                    toolResultXml("read_file", "orphan"),
                                ),
                            ),
                        stream = true,
                        tools = listOf(readFileTool()),
                    )
            }
        assertEquals(ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_CALL, orphan.violation)

        val unstructured =
            assertThrows<ProviderToolHistoryProtocolException> {
                provider()
                    .requestJson(
                        context = context,
                        history = listOf(PromptTurn(PromptTurnKind.TOOL_CALL, "plain text")),
                        stream = true,
                        tools = listOf(readFileTool()),
                    )
            }
        assertEquals(ProviderToolHistoryViolation.TOOL_CALL_WITHOUT_PAYLOAD, unstructured.violation)
    }

    @Test
    fun `duplicate or reserved model parameter fails explicitly`() {
        assertThrows<IllegalArgumentException> {
            provider(enableToolCall = false)
                .requestJson(
                    context = context,
                    history = listOf(PromptTurn(PromptTurnKind.USER, "hello")),
                    parameters =
                        listOf(
                            floatParameter("temperature", 0.2f),
                            floatParameter("temperature", 0.8f),
                        ),
                    stream = true,
                    tools = null,
                )
        }

        assertThrows<IllegalArgumentException> {
            provider(enableToolCall = false)
                .requestJson(
                    context = context,
                    history = listOf(PromptTurn(PromptTurnKind.USER, "hello")),
                    parameters = listOf(floatParameter("stream", 1f)),
                    stream = true,
                    tools = null,
                )
        }
    }

    @Test
    fun `duplicate tool definitions and typed history with disabled tools fail explicitly`() {
        val duplicate = readFileTool()
        assertThrows<IllegalArgumentException> {
            provider()
                .requestJson(
                    context = context,
                    history = listOf(PromptTurn(PromptTurnKind.USER, "hello")),
                    stream = true,
                    tools = listOf(duplicate, duplicate),
                )
        }

        val disabledCall =
            assertThrows<ProviderToolHistoryProtocolException> {
                provider(enableToolCall = false)
                    .requestJson(
                        context = context,
                        history =
                            listOf(
                                PromptTurn(
                                    PromptTurnKind.TOOL_CALL,
                                    toolCallXml("read_file", "call_123", "path", "one.txt"),
                                )
                            ),
                        stream = true,
                        tools = null,
                    )
            }
        assertEquals(ProviderToolHistoryViolation.TOOL_PROTOCOL_DISABLED, disabledCall.violation)

        val disabledResult =
            assertThrows<ProviderToolHistoryProtocolException> {
                provider(enableToolCall = false)
                    .requestJson(
                        context = context,
                        history =
                            listOf(
                                PromptTurn(
                                    PromptTurnKind.TOOL_RESULT,
                                    toolResultXml("read_file", "done"),
                                )
                            ),
                        stream = true,
                        tools = null,
                    )
            }
        assertEquals(ProviderToolHistoryViolation.TOOL_PROTOCOL_DISABLED, disabledResult.violation)
    }

    private fun provider(enableToolCall: Boolean = true): TestDeepseekProvider =
        TestDeepseekProvider(enableToolCall)

    private fun readFileTool(): ToolPrompt =
        ToolPrompt(
            name = "read_file",
            description = "Read a file",
            parametersStructured =
                listOf(
                    ToolParameterSchema(
                        name = "path",
                        description = "Path",
                    )
                ),
        )

    private fun floatParameter(apiName: String, value: Float): ModelParameter<Float> =
        ModelParameter(
            id = apiName,
            name = apiName,
            apiName = apiName,
            defaultValue = value,
            currentValue = value,
            isEnabled = true,
            valueType = ParameterValueType.FLOAT,
        )

    private fun toolCallXml(
        name: String,
        callId: String,
        parameterName: String,
        parameterValue: String,
    ): String =
        """
        <tool_A1 name="$name" provider_name="DEEPSEEK" provider_call_id="$callId">
        <param name="$parameterName">$parameterValue</param>
        </tool_A1>
        """.trimIndent()

    private fun toolResultXml(name: String, content: String): String =
        """<tool_result_A1 name="$name" status="success"><content>$content</content></tool_result_A1>"""

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try {
            block()
            fail("Expected ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) {
                throw error
            }
            return error
        }
        error("unreachable")
    }

    private class TestDeepseekProvider(enableToolCall: Boolean) :
        DeepseekProvider(
            apiEndpoint = "https://api.deepseek.com/chat/completions",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "deepseek-chat",
            client = OkHttpClient(),
            providerType = ApiProviderType.DEEPSEEK,
            enableToolCall = enableToolCall,
        ) {
        fun requestJson(
            context: Context,
            history: List<PromptTurn>,
            parameters: List<ModelParameter<*>> = emptyList(),
            stream: Boolean,
            tools: List<ToolPrompt>?,
        ): String {
            val body: RequestBody =
                createRequestBody(
                    context = context,
                    chatHistory = history,
                    modelParameters = parameters,
                    enableThinking = false,
                    stream = stream,
                    availableTools = tools,
                    preserveThinkInHistory = true,
                )
            return Buffer().use { buffer ->
                body.writeTo(buffer)
                buffer.readUtf8()
            }
        }
    }
}
