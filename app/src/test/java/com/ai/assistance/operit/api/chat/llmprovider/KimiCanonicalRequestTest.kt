package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolParameterSchema
import com.ai.assistance.operit.data.model.ToolPrompt
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.mock

class KimiCanonicalRequestTest {
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
    fun preservesProviderCallIdsReasoningAndToolResultOrder() {
        val request =
            provider()
                .requestJson(
                    context = context,
                    history =
                        listOf(
                            PromptTurn(PromptTurnKind.USER, "question"),
                            PromptTurn(
                                PromptTurnKind.ASSISTANT,
                                "<think>reasoning</think>\n" +
                                    toolCallXml("read_file", "kimi_call_1", "one.txt") +
                                    "\n" +
                                    toolCallXml("read_file", "kimi_call_2", "two.txt"),
                            ),
                            PromptTurn(
                                PromptTurnKind.TOOL_RESULT,
                                toolResultXml("read_file", "first") +
                                    "\n" +
                                    toolResultXml("read_file", "second"),
                            ),
                        ),
                )
        val messages = JSONObject(request).getJSONArray("messages")
        val assistant = messages.getJSONObject(1)

        assertEquals("reasoning", assistant.getString("reasoning_content"))
        assertEquals(
            "kimi_call_1",
            assistant.getJSONArray("tool_calls").getJSONObject(0).getString("id"),
        )
        assertEquals(
            "kimi_call_2",
            assistant.getJSONArray("tool_calls").getJSONObject(1).getString("id"),
        )
        assertEquals("kimi_call_1", messages.getJSONObject(2).getString("tool_call_id"))
        assertEquals("kimi_call_2", messages.getJSONObject(3).getString("tool_call_id"))
        assertFalse(request.contains("User cancelled"))
        assertFalse(request.contains("[Empty]"))
    }

    @Test
    fun incompleteOrDisabledTypedToolHistoryFailsBeforeSubmission() {
        val missing =
            assertThrows(ProviderToolHistoryProtocolException::class.java) {
                provider()
                    .requestJson(
                        context = context,
                        history =
                            listOf(
                                PromptTurn(
                                    PromptTurnKind.TOOL_CALL,
                                    toolCallXml("read_file", "kimi_call_1", "one.txt"),
                                )
                            ),
                    )
            }
        assertEquals(ProviderToolHistoryViolation.MISSING_TOOL_RESULT, missing.violation)

        val disabled =
            assertThrows(ProviderToolHistoryProtocolException::class.java) {
                provider(enableToolCall = false)
                    .requestJson(
                        context = context,
                        history =
                            listOf(
                                PromptTurn(
                                    PromptTurnKind.TOOL_CALL,
                                    toolCallXml("read_file", "kimi_call_1", "one.txt"),
                                )
                            ),
                    )
            }
        assertEquals(ProviderToolHistoryViolation.TOOL_PROTOCOL_DISABLED, disabled.violation)
    }

    private fun provider(enableToolCall: Boolean = true): TestKimiProvider =
        TestKimiProvider(enableToolCall = enableToolCall)

    private fun toolCallXml(name: String, callId: String, path: String): String =
        """
        <tool_K1 name="$name" provider_name="MOONSHOT" provider_call_id="$callId">
        <param name="path">$path</param>
        </tool_K1>
        """.trimIndent()

    private fun toolResultXml(name: String, content: String): String =
        """<tool_result_K1 name="$name" status="success"><content>$content</content></tool_result_K1>"""

    private class TestKimiProvider(enableToolCall: Boolean) :
        KimiProvider(
            apiEndpoint = "https://api.moonshot.cn/v1/chat/completions",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "kimi-k2.5",
            client = OkHttpClient(),
            providerType = ApiProviderType.MOONSHOT,
            enableToolCall = enableToolCall,
        ) {
        fun requestJson(
            context: Context,
            history: List<PromptTurn>,
        ): String {
            val body: RequestBody =
                createRequestBody(
                    context = context,
                    chatHistory = history,
                    modelParameters = emptyList<ModelParameter<*>>(),
                    enableThinking = true,
                    stream = true,
                    availableTools =
                        listOf(
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
                        ),
                    preserveThinkInHistory = true,
                )
            return Buffer().use { buffer ->
                body.writeTo(buffer)
                buffer.readUtf8()
            }
        }
    }
}
