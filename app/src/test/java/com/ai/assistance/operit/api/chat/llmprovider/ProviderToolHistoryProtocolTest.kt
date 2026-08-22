package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ProviderToolHistoryProtocolTest {
    @Test
    fun `complete parallel tool transaction closes in original order`() {
        val state = ProviderToolHistoryState()

        state.acceptToolCalls(listOf("call_1", "call_2"), "assistant")
        assertEquals(2, state.pendingCount())

        state.acceptToolResults(resultCount = 1, boundary = "first_result")
        assertEquals(1, state.pendingCount())

        state.acceptToolResults(resultCount = 1, boundary = "second_result")
        state.requireClosed("history_end")
        assertEquals(0, state.pendingCount())
    }

    @Test
    fun `missing result fails at the next provider boundary`() {
        val state = ProviderToolHistoryState()
        state.acceptToolCalls(listOf("call_1"), "assistant")

        assertViolation(ProviderToolHistoryViolation.MISSING_TOOL_RESULT) {
            state.requireClosed("user_boundary")
        }
    }

    @Test
    fun `result without call fails`() {
        val state = ProviderToolHistoryState()

        assertViolation(ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_CALL) {
            state.acceptToolResults(resultCount = 1, boundary = "tool_result")
        }
    }

    @Test
    fun `too many results fail instead of being dropped`() {
        val state = ProviderToolHistoryState()
        state.acceptToolCalls(listOf("call_1"), "assistant")

        assertViolation(ProviderToolHistoryViolation.TOO_MANY_TOOL_RESULTS) {
            state.acceptToolResults(resultCount = 2, boundary = "tool_result")
        }
    }

    @Test
    fun `duplicate call IDs fail before a request can be submitted`() {
        val state = ProviderToolHistoryState()

        assertViolation(ProviderToolHistoryViolation.DUPLICATE_TOOL_CALL_ID) {
            state.acceptToolCalls(listOf("call_1", "call_1"), "assistant")
        }
    }

    @Test
    fun `named results preserve tool name and optional original call ID`() {
        val state = ProviderToolHistoryState()
        state.acceptToolCallDescriptors(
            calls = listOf(ProviderToolCallDescriptor(callId = "call_1", toolName = "shell")),
            boundary = "assistant",
        )

        state.acceptNamedToolResults(
            results =
                listOf(
                    ProviderToolResultDescriptor(
                        callId = "call_1",
                        toolName = "shell",
                    )
                ),
            boundary = "tool_result",
        )
        state.requireClosed("history_end")
    }

    @Test
    fun `named result with a different tool name fails`() {
        val state = ProviderToolHistoryState()
        state.acceptToolCallDescriptors(
            calls = listOf(ProviderToolCallDescriptor(callId = "call_1", toolName = "shell")),
            boundary = "assistant",
        )

        assertViolation(ProviderToolHistoryViolation.TOOL_RESULT_NAME_MISMATCH) {
            state.acceptNamedToolResults(
                results =
                    listOf(
                        ProviderToolResultDescriptor(
                            callId = null,
                            toolName = "browser",
                        )
                    ),
                boundary = "tool_result",
            )
        }
    }

    @Test
    fun `named result with a different original call ID fails`() {
        val state = ProviderToolHistoryState()
        state.acceptToolCallDescriptors(
            calls = listOf(ProviderToolCallDescriptor(callId = "call_1", toolName = "shell")),
            boundary = "assistant",
        )

        assertViolation(ProviderToolHistoryViolation.TOOL_RESULT_CALL_ID_MISMATCH) {
            state.acceptNamedToolResults(
                results =
                    listOf(
                        ProviderToolResultDescriptor(
                            callId = "call_2",
                            toolName = "shell",
                        )
                    ),
                boundary = "tool_result",
            )
        }
    }

    private fun assertViolation(
        expected: ProviderToolHistoryViolation,
        block: () -> Unit,
    ) {
        try {
            block()
            fail("Expected ProviderToolHistoryProtocolException")
        } catch (error: ProviderToolHistoryProtocolException) {
            assertEquals(expected, error.violation)
        }
    }
}
