package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.util.Base64
import android.util.Log
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ToolParameterSchema
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.ChatMarkupRegex
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.mock

class GeminiCanonicalRequestTest {
    private val context: Context = mock()
    private lateinit var logMock: MockedStatic<Log>
    private lateinit var base64Mock: MockedStatic<Base64>

    @Before
    fun setUpAndroidStatics() {
        logMock = Mockito.mockStatic(Log::class.java)
        base64Mock = Mockito.mockStatic(Base64::class.java)
        base64Mock.`when`<String> {
            Base64.encodeToString(Mockito.any(ByteArray::class.java), Mockito.anyInt())
        }.thenAnswer { invocation ->
            java.util.Base64.getEncoder()
                .encodeToString(invocation.getArgument<ByteArray>(0))
        }
        base64Mock.`when`<ByteArray> {
            Base64.decode(Mockito.anyString(), Mockito.anyInt())
        }.thenAnswer { invocation ->
            java.util.Base64.getDecoder()
                .decode(invocation.getArgument<String>(0))
        }
    }

    @After
    fun tearDownAndroidStatics() {
        base64Mock.close()
        logMock.close()
    }

    @Test
    fun replaysParallelFunctionCallsAndResponsesWithoutSyntheticCancellation() {
        val request =
            provider()
                .createRequestJson(
                    context = context,
                    chatHistory =
                        listOf(
                            PromptTurn(PromptTurnKind.USER, "question"),
                            PromptTurn(
                                PromptTurnKind.TOOL_CALL,
                                toolCallXml("read_file", "one.txt") +
                                    thoughtSignatureMeta("sig-one") +
                                    toolCallXml("write_file", "two.txt") +
                                    thoughtSignatureMeta("sig-two"),
                            ),
                            PromptTurn(
                                PromptTurnKind.TOOL_RESULT,
                                toolResultXml("read_file", "one") +
                                    toolResultXml("write_file", "two"),
                            ),
                        ),
                    modelParameters = emptyList(),
                    enableThinking = true,
                    availableTools = listOf(writeFileTool(), readFileTool()),
                    preserveThinkInHistory = true,
                )

        val contents = request.getJSONArray("contents")
        val callParts = contents.getJSONObject(1).getJSONArray("parts")
        val resultParts = contents.getJSONObject(2).getJSONArray("parts")

        assertEquals("read_file", callParts.getJSONObject(0).getJSONObject("functionCall").getString("name"))
        assertEquals("sig-one", callParts.getJSONObject(0).getString("thoughtSignature"))
        assertEquals("write_file", callParts.getJSONObject(1).getJSONObject("functionCall").getString("name"))
        assertEquals("sig-two", callParts.getJSONObject(1).getString("thoughtSignature"))
        assertEquals("read_file", resultParts.getJSONObject(0).getJSONObject("functionResponse").getString("name"))
        assertEquals("write_file", resultParts.getJSONObject(1).getJSONObject("functionResponse").getString("name"))
        assertFalse(request.toString().contains("User cancelled"))
        assertFalse(request.toString().contains("[Empty]"))
    }

    @Test
    fun toolDeclarationsAndSchemaAreStableAcrossInputOrder() {
        val first =
            requestWithTools(listOf(writeFileTool(), readFileTool()))
                .getJSONArray("tools")
                .toString()
        val second =
            requestWithTools(listOf(readFileTool(), writeFileTool()))
                .getJSONArray("tools")
                .toString()

        assertEquals(first, second)
        val declarations =
            JSONArray(first)
                .getJSONObject(0)
                .getJSONArray("function_declarations")
        assertEquals("read_file", declarations.getJSONObject(0).getString("name"))
        assertEquals("write_file", declarations.getJSONObject(1).getString("name"))
    }

    @Test
    fun incompleteOrMismatchedToolHistoryFailsBeforeSubmission() {
        val missing =
            assertThrows(ProviderToolHistoryProtocolException::class.java) {
                request(
                    listOf(
                        PromptTurn(
                            PromptTurnKind.TOOL_CALL,
                            toolCallXml("read_file", "one.txt"),
                        )
                    )
                )
            }
        assertEquals(ProviderToolHistoryViolation.MISSING_TOOL_RESULT, missing.violation)

        val mismatched =
            assertThrows(ProviderToolHistoryProtocolException::class.java) {
                request(
                    listOf(
                        PromptTurn(
                            PromptTurnKind.TOOL_CALL,
                            toolCallXml("read_file", "one.txt"),
                        ),
                        PromptTurn(
                            PromptTurnKind.TOOL_RESULT,
                            toolResultXml("write_file", "wrong"),
                        ),
                    )
                )
            }
        assertEquals(
            ProviderToolHistoryViolation.TOOL_RESULT_NAME_MISMATCH,
            mismatched.violation,
        )
    }

    private fun requestWithTools(tools: List<ToolPrompt>) =
        provider()
            .createRequestJson(
                context = context,
                chatHistory = listOf(PromptTurn(PromptTurnKind.USER, "question")),
                modelParameters = emptyList(),
                enableThinking = false,
                availableTools = tools,
            )

    private fun request(history: List<PromptTurn>) =
        provider()
            .createRequestJson(
                context = context,
                chatHistory = history,
                modelParameters = emptyList(),
                enableThinking = false,
                availableTools = listOf(readFileTool(), writeFileTool()),
                preserveThinkInHistory = true,
            )

    private fun provider() =
        GeminiProvider(
            apiEndpoint = "https://generativelanguage.googleapis.com",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "gemini-2.5-pro",
            client = OkHttpClient(),
            providerType = ApiProviderType.GOOGLE,
            enableToolCall = true,
        )

    private fun readFileTool() =
        ToolPrompt(
            name = "read_file",
            description = "Read a file",
            parametersStructured =
                listOf(
                    ToolParameterSchema(name = "path", description = "Path")
                ),
        )

    private fun writeFileTool() =
        ToolPrompt(
            name = "write_file",
            description = "Write a file",
            parametersStructured =
                listOf(
                    ToolParameterSchema(name = "text", description = "Text"),
                    ToolParameterSchema(name = "path", description = "Path"),
                ),
        )

    private fun toolCallXml(name: String, path: String) =
        """
            <tool_A1 name="$name">
            <param name="path">$path</param>
            </tool_A1>
        """.trimIndent()

    private fun toolResultXml(name: String, content: String) =
        """<tool_result_A1 name="$name" status="success"><content>$content</content></tool_result_A1>"""

    private fun thoughtSignatureMeta(signature: String) =
        ChatMarkupRegex.geminiThoughtSignatureMetaTag(
            java.util.Base64.getEncoder()
                .encodeToString(signature.toByteArray(Charsets.UTF_8))
        )
}
