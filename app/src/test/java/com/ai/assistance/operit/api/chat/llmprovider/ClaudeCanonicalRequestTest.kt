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
import okhttp3.OkHttpClient
import org.json.JSONArray
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

class ClaudeCanonicalRequestTest {
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
    fun excludesAssistantsWithoutVisibleContentOrCompleteTools() {
        val request =
            provider(enableToolCall = false)
                .createRequestJson(
                    context = context,
                    chatHistory =
                        listOf(
                            PromptTurn(PromptTurnKind.USER, "question"),
                            PromptTurn(PromptTurnKind.ASSISTANT, ""),
                            PromptTurn(PromptTurnKind.ASSISTANT, "<THINK>reasoning</THINK>"),
                            PromptTurn(PromptTurnKind.ASSISTANT, "<status>working</status>"),
                            PromptTurn(PromptTurnKind.ASSISTANT, "answer"),
                        ),
                    enableThinking = false,
                    availableTools = null,
                    preserveThinkInHistory = true,
                )

        val messages = request.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        val assistant = messages.getJSONObject(1)
        assertEquals("assistant", assistant.getString("role"))
        assertEquals("answer", assistant.getJSONArray("content").getJSONObject(0).getString("text"))
    }

    @Test
    fun replaysOriginalThinkingAndToolBlocksWithProviderCallId() {
        val metadataTag =
            contentBlockMetadata(
                modelName = "claude-opus-4-6",
                blocks =
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "thinking")
                                .put("thinking", "reasoning")
                                .put("signature", "signed")
                        )
                        .put(
                            JSONObject()
                                .put("type", "tool_use")
                                .put("id", "toolu_123")
                                .put("name", "read_file")
                                .put("input", JSONObject().put("path", "one.txt"))
                        )
                        .put(
                            JSONObject()
                                .put("type", "text")
                                .put("text", "answer")
                        ),
            )
        val request =
            provider(modelName = "claude-opus-4-6")
                .createRequestJson(
                    context = context,
                    chatHistory =
                        listOf(
                            PromptTurn(PromptTurnKind.USER, "question"),
                            PromptTurn(
                                PromptTurnKind.ASSISTANT,
                                "<think>visible projection</think>\n" +
                                    toolCallXml(
                                        name = "read_file",
                                        callId = "toolu_123",
                                        path = "one.txt",
                                    ) +
                                    "\nanswer\n" +
                                    metadataTag,
                            ),
                            PromptTurn(
                                PromptTurnKind.TOOL_RESULT,
                                toolResultXml("read_file", "done"),
                            ),
                        ),
                    modelParameters = emptyList<ModelParameter<*>>(),
                    enableThinking = true,
                    stream = true,
                    availableTools = listOf(readFileTool()),
                    preserveThinkInHistory = true,
                )
        val messages = request.getJSONArray("messages")
        val assistantBlocks = messages.getJSONObject(1).getJSONArray("content")
        val resultBlocks = messages.getJSONObject(2).getJSONArray("content")

        assertEquals(
            listOf("thinking", "tool_use", "text"),
            blockTypes(assistantBlocks),
        )
        assertEquals("signed", assistantBlocks.getJSONObject(0).getString("signature"))
        assertEquals("toolu_123", assistantBlocks.getJSONObject(1).getString("id"))
        assertEquals("tool_result", resultBlocks.getJSONObject(0).getString("type"))
        assertEquals("toolu_123", resultBlocks.getJSONObject(0).getString("tool_use_id"))
        assertFalse(request.toString().contains("User cancelled"))
        assertFalse(request.toString().contains("[Empty]"))
    }

    @Test
    fun modelSwitchStripsThinkingButKeepsToolTransaction() {
        val metadataTag =
            contentBlockMetadata(
                modelName = "claude-opus-4-6",
                blocks =
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "thinking")
                                .put("thinking", "reasoning")
                                .put("signature", "signed")
                        )
                        .put(
                            JSONObject()
                                .put("type", "tool_use")
                                .put("id", "toolu_123")
                                .put("name", "read_file")
                                .put("input", JSONObject().put("path", "one.txt"))
                        ),
            )
        val request =
            provider(modelName = "claude-sonnet-4-6")
                .createRequestJson(
                    context = context,
                    chatHistory =
                        listOf(
                            PromptTurn(PromptTurnKind.USER, "question"),
                            PromptTurn(PromptTurnKind.TOOL_CALL, metadataTag),
                            PromptTurn(
                                PromptTurnKind.TOOL_RESULT,
                                toolResultXml("read_file", "done"),
                            ),
                        ),
                    modelParameters = emptyList<ModelParameter<*>>(),
                    enableThinking = true,
                    stream = true,
                    availableTools = listOf(readFileTool()),
                    preserveThinkInHistory = true,
                )
        val assistantBlocks =
            request.getJSONArray("messages")
                .getJSONObject(1)
                .getJSONArray("content")

        assertEquals(listOf("tool_use"), blockTypes(assistantBlocks))
        assertEquals("toolu_123", assistantBlocks.getJSONObject(0).getString("id"))
    }

    @Test
    fun legacyToolHistoryPreservesProviderIdAndRejectsIncompleteOrDisabledTransactions() {
        val complete =
            provider()
                .createRequestJson(
                    context = context,
                    chatHistory =
                        listOf(
                            PromptTurn(PromptTurnKind.USER, "question"),
                            PromptTurn(
                                PromptTurnKind.TOOL_CALL,
                                toolCallXml("read_file", "toolu_legacy", "one.txt"),
                            ),
                            PromptTurn(
                                PromptTurnKind.TOOL_RESULT,
                                toolResultXml("read_file", "done"),
                            ),
                        ),
                    modelParameters = emptyList<ModelParameter<*>>(),
                    enableThinking = false,
                    stream = true,
                    availableTools = listOf(readFileTool()),
                    preserveThinkInHistory = true,
                )
        assertEquals(
            "toolu_legacy",
            complete.getJSONArray("messages")
                .getJSONObject(1)
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("id"),
        )

        val missing =
            assertThrows(ProviderToolHistoryProtocolException::class.java) {
                provider()
                    .createRequestJson(
                        context = context,
                        chatHistory =
                            listOf(
                                PromptTurn(
                                    PromptTurnKind.TOOL_CALL,
                                    toolCallXml(
                                        "read_file",
                                        "toolu_missing",
                                        "one.txt",
                                    ),
                                )
                            ),
                        modelParameters = emptyList<ModelParameter<*>>(),
                        enableThinking = false,
                        stream = true,
                        availableTools = listOf(readFileTool()),
                        preserveThinkInHistory = true,
                    )
            }
        assertEquals(ProviderToolHistoryViolation.MISSING_TOOL_RESULT, missing.violation)

        val disabled =
            assertThrows(ProviderToolHistoryProtocolException::class.java) {
                provider(enableToolCall = false)
                    .createRequestJson(
                        context = context,
                        chatHistory =
                            listOf(
                                PromptTurn(
                                    PromptTurnKind.TOOL_CALL,
                                    toolCallXml(
                                        "read_file",
                                        "toolu_disabled",
                                        "one.txt",
                                    ),
                                )
                            ),
                        modelParameters = emptyList<ModelParameter<*>>(),
                        enableThinking = false,
                        stream = true,
                        availableTools = null,
                        preserveThinkInHistory = true,
                    )
            }
        assertEquals(ProviderToolHistoryViolation.TOOL_PROTOCOL_DISABLED, disabled.violation)
    }

    @Test
    fun thinkingFormatIsDeterministicAndInvalidBudgetFailsBeforeSubmission() {
        val adaptive =
            provider(modelName = "claude-opus-4-6", enableToolCall = false)
                .createRequestJson(
                    context = context,
                    chatHistory = listOf(PromptTurn(PromptTurnKind.USER, "hello")),
                    modelParameters = emptyList<ModelParameter<*>>(),
                    enableThinking = true,
                    stream = true,
                    availableTools = null,
                )
                .getJSONObject("thinking")
        assertEquals("adaptive", adaptive.getString("type"))
        assertEquals("summarized", adaptive.getString("display"))

        val enabled =
            provider(modelName = "claude-sonnet-4-5", enableToolCall = false)
                .createRequestJson(
                    context = context,
                    chatHistory = listOf(PromptTurn(PromptTurnKind.USER, "hello")),
                    modelParameters = emptyList<ModelParameter<*>>(),
                    enableThinking = true,
                    stream = true,
                    availableTools = null,
                )
                .getJSONObject("thinking")
        assertEquals("enabled", enabled.getString("type"))
        assertEquals(1024, enabled.getInt("budget_tokens"))

        assertThrows(AnthropicProtocolException::class.java) {
            provider(modelName = "claude-sonnet-4-5", enableToolCall = false)
                .createRequestJson(
                    context = context,
                    chatHistory = listOf(PromptTurn(PromptTurnKind.USER, "hello")),
                    modelParameters =
                        listOf(
                            integerParameter("max_tokens", 1024),
                            integerParameter("budget_tokens", 1024),
                        ),
                    enableThinking = true,
                    stream = true,
                    availableTools = null,
                )
        }
    }

    private fun provider(
        modelName: String = "claude-opus-4-6",
        enableToolCall: Boolean = true,
    ): ClaudeProvider {
        return ClaudeProvider(
            apiEndpoint = "https://api.anthropic.com/v1/messages",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = modelName,
            client = OkHttpClient(),
            providerType = ApiProviderType.ANTHROPIC,
            enableToolCall = enableToolCall,
        )
    }

    private fun contentBlockMetadata(
        modelName: String,
        blocks: JSONArray,
    ): String {
        return requireNotNull(
            AnthropicContentBlockReplayCodec.createMetadataTag(
                modelName = modelName,
                contentBlocks = blocks,
            )
        )
    }

    private fun readFileTool(): ToolPrompt {
        return ToolPrompt(
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
    }

    private fun integerParameter(
        apiName: String,
        value: Int,
    ): ModelParameter<Int> {
        return ModelParameter(
            id = apiName,
            name = apiName,
            apiName = apiName,
            defaultValue = value,
            currentValue = value,
            isEnabled = true,
            valueType = ParameterValueType.INT,
        )
    }

    private fun toolCallXml(
        name: String,
        callId: String,
        path: String,
    ): String {
        return """
            <tool_A1 name="$name" provider_name="ANTHROPIC" provider_call_id="$callId">
            <param name="path">$path</param>
            </tool_A1>
        """.trimIndent()
    }

    private fun toolResultXml(name: String, content: String): String {
        return """<tool_result_A1 name="$name" status="success"><content>$content</content></tool_result_A1>"""
    }

    private fun blockTypes(blocks: JSONArray): List<String> {
        return (0 until blocks.length()).map { index ->
            blocks.getJSONObject(index).getString("type")
        }
    }
}
