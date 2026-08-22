package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ToolParameterSchema
import com.ai.assistance.operit.data.model.ToolPrompt
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StructuredToolCallBridgeTest {
    @Test
    fun `provider call ID and parallel result order are preserved`() {
        val messages =
            JSONArray(
                StructuredToolCallBridge.buildMessagesJson(
                    history =
                        listOf(
                            PromptTurn(
                                kind = PromptTurnKind.TOOL_CALL,
                                content =
                                    toolCallXml("read_file", "call_123", "path", "one.txt") +
                                        "\n" +
                                        toolCallXml("read_file", "call_456", "path", "two.txt"),
                            ),
                            PromptTurn(
                                kind = PromptTurnKind.TOOL_RESULT,
                                content =
                                    toolResultXml("read_file", "first") +
                                        "\n" +
                                        toolResultXml("read_file", "second"),
                            ),
                        ),
                    preserveThinkInHistory = true,
                )
            )

        val assistant = messages.getJSONObject(0)
        val calls = assistant.getJSONArray("tool_calls")
        assertEquals("call_123", calls.getJSONObject(0).getString("id"))
        assertEquals("call_456", calls.getJSONObject(1).getString("id"))
        assertEquals("call_123", messages.getJSONObject(1).getString("tool_call_id"))
        assertEquals("first", messages.getJSONObject(1).getString("content"))
        assertEquals("call_456", messages.getJSONObject(2).getString("tool_call_id"))
        assertEquals("second", messages.getJSONObject(2).getString("content"))
        assertFalse(messages.toString().contains("User cancelled"))
        assertFalse(messages.toString().contains("[Empty]"))
    }

    @Test
    fun `protocol result name is independent from UI display name`() {
        val messages =
            JSONArray(
                StructuredToolCallBridge.buildMessagesJson(
                    history =
                        listOf(
                            PromptTurn(
                                PromptTurnKind.TOOL_CALL,
                                toolCallXml("read_file", "call_123", "path", "one.txt"),
                            ),
                            PromptTurn(
                                PromptTurnKind.TOOL_RESULT,
                                "<tool_result_A1 name=\"Files: read\" " +
                                    "provider_tool_name=\"read_file\" status=\"success\">" +
                                    "<content>one</content></tool_result_A1>",
                            ),
                        ),
                    preserveThinkInHistory = true,
                )
            )

        assertEquals("read_file", messages.getJSONObject(1).getString("name"))
    }

    @Test
    fun `same provider identity with same payload is emitted once`() {
        val duplicateCall = toolCallXml("read_file", "call_123", "path", "same.txt")
        val messages =
            JSONArray(
                StructuredToolCallBridge.buildMessagesJson(
                    history =
                        listOf(
                            PromptTurn(
                                kind = PromptTurnKind.TOOL_CALL,
                                content = "$duplicateCall\n$duplicateCall",
                            ),
                            PromptTurn(
                                kind = PromptTurnKind.TOOL_RESULT,
                                content = toolResultXml("read_file", "done"),
                            ),
                        ),
                    preserveThinkInHistory = true,
                )
            )

        assertEquals(1, messages.getJSONObject(0).getJSONArray("tool_calls").length())
        assertEquals(2, messages.length())
    }

    @Test
    fun `same provider identity with conflicting payload fails`() {
        assertThrows<ProviderToolCallIdentityConflictException> {
            StructuredToolCallBridge.buildMessagesJson(
                history =
                    listOf(
                        PromptTurn(
                            kind = PromptTurnKind.TOOL_CALL,
                            content =
                                toolCallXml("read_file", "call_123", "path", "one.txt") +
                                    "\n" +
                                    toolCallXml("read_file", "call_123", "path", "two.txt"),
                        ),
                    ),
                preserveThinkInHistory = true,
            )
        }
    }

    @Test
    fun `legacy call ID is stable when XML parameter order changes`() {
        val first =
            compileLegacyCall(
                """
                <tool_A1 name="read_file">
                <param name="zeta">z</param>
                <param name="alpha">a</param>
                </tool_A1>
                """.trimIndent()
            )
        val second =
            compileLegacyCall(
                """
                <tool_A1 name="read_file">
                <param name="alpha">a</param>
                <param name="zeta">z</param>
                </tool_A1>
                """.trimIndent()
            )

        assertEquals(first, second)
    }

    @Test
    fun `missing result fails without fabricated cancellation`() {
        val error =
            assertThrows<ProviderToolHistoryProtocolException> {
                StructuredToolCallBridge.buildMessagesJson(
                    history =
                        listOf(
                            PromptTurn(
                                kind = PromptTurnKind.TOOL_CALL,
                                content = toolCallXml("read_file", "call_123", "path", "one.txt"),
                            ),
                        ),
                    preserveThinkInHistory = true,
                )
            }

        assertEquals(ProviderToolHistoryViolation.MISSING_TOOL_RESULT, error.violation)
        assertFalse(error.message.orEmpty().contains("User cancelled"))
    }

    @Test
    fun `typed result without pending call fails`() {
        val error =
            assertThrows<ProviderToolHistoryProtocolException> {
                StructuredToolCallBridge.buildMessagesJson(
                    history =
                        listOf(
                            PromptTurn(
                                kind = PromptTurnKind.TOOL_RESULT,
                                content = toolResultXml("read_file", "orphan"),
                            ),
                        ),
                    preserveThinkInHistory = true,
                )
            }

        assertEquals(ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_CALL, error.violation)
    }

    @Test
    fun `non structured typed call and result fail`() {
        val callError =
            assertThrows<ProviderToolHistoryProtocolException> {
                StructuredToolCallBridge.buildMessagesJson(
                    history = listOf(PromptTurn(PromptTurnKind.TOOL_CALL, "plain text")),
                    preserveThinkInHistory = true,
                )
            }
        assertEquals(ProviderToolHistoryViolation.TOOL_CALL_WITHOUT_PAYLOAD, callError.violation)

        val resultError =
            assertThrows<ProviderToolHistoryProtocolException> {
                StructuredToolCallBridge.buildMessagesJson(
                    history = listOf(PromptTurn(PromptTurnKind.TOOL_RESULT, "plain text")),
                    preserveThinkInHistory = true,
                )
            }
        assertEquals(ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_PAYLOAD, resultError.violation)
    }

    @Test
    fun `text cannot cross a partially completed parallel tool boundary`() {
        val error =
            assertThrows<ProviderToolHistoryProtocolException> {
                StructuredToolCallBridge.buildMessagesJson(
                    history =
                        listOf(
                            PromptTurn(
                                kind = PromptTurnKind.TOOL_CALL,
                                content =
                                    toolCallXml("read_file", "call_123", "path", "one.txt") +
                                        toolCallXml("read_file", "call_456", "path", "two.txt"),
                            ),
                            PromptTurn(
                                kind = PromptTurnKind.TOOL_RESULT,
                                content = toolResultXml("read_file", "first") + "\nnot-yet-complete",
                            ),
                        ),
                    preserveThinkInHistory = true,
                )
            }

        assertEquals(ProviderToolHistoryViolation.MISSING_TOOL_RESULT, error.violation)
    }

    @Test
    fun `tool definitions and schema parameters are byte stable`() {
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

        val first = StructuredToolCallBridge.buildToolsJson(listOf(zeta, alpha))
        val second = StructuredToolCallBridge.buildToolsJson(listOf(alphaReordered, zeta))

        assertEquals(first, second)
        val tools = JSONArray(first)
        assertEquals("alpha", tools.getJSONObject(0).getJSONObject("function").getString("name"))
        assertTrue(first!!.indexOf("\"beta\"") < first.indexOf("\"zeta\""))
    }

    @Test
    fun `duplicate tool and parameter names fail explicitly`() {
        val duplicateTool = ToolPrompt(name = "same", description = "Same")
        assertThrows<IllegalArgumentException> {
            StructuredToolCallBridge.buildToolsJson(listOf(duplicateTool, duplicateTool))
        }

        assertThrows<IllegalArgumentException> {
            StructuredToolCallBridge.buildToolsJson(
                listOf(
                    ToolPrompt(
                        name = "same",
                        description = "Same",
                        parametersStructured =
                            listOf(
                                ToolParameterSchema("value", description = "one"),
                                ToolParameterSchema("value", description = "two"),
                            ),
                    )
                )
            )
        }
    }

    @Test
    fun `blank assistant remains null instead of becoming protocol text`() {
        val messages =
            JSONArray(
                StructuredToolCallBridge.buildMessagesJson(
                    history = listOf(PromptTurn(PromptTurnKind.ASSISTANT, "")),
                    preserveThinkInHistory = true,
                )
            )

        assertTrue(messages.getJSONObject(0).isNull("content"))
        assertFalse(messages.toString().contains("[Empty]"))
    }

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

    private fun compileLegacyCall(toolCall: String): String {
        val messages =
            JSONArray(
                StructuredToolCallBridge.buildMessagesJson(
                    history =
                        listOf(
                            PromptTurn(PromptTurnKind.TOOL_CALL, toolCall),
                            PromptTurn(
                                PromptTurnKind.TOOL_RESULT,
                                toolResultXml("read_file", "done"),
                            ),
                        ),
                    preserveThinkInHistory = true,
                )
            )
        return messages
            .getJSONObject(0)
            .getJSONArray("tool_calls")
            .getJSONObject(0)
            .getString("id")
    }

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
}
