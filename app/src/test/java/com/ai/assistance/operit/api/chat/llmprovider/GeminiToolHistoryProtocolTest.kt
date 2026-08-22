package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeminiToolHistoryProtocolTest {
    @Test
    fun orderedFunctionResponsesCloseThePendingCalls() {
        val state = GeminiToolHistoryState()
        state.acceptFunctionCalls(
            calls =
                listOf(
                    call("read_file", """{"path":"one.txt"}"""),
                    call("write_file", """{"path":"two.txt","text":"ok"}"""),
                ),
            boundary = "test_calls",
        )

        state.acceptFunctionResponses(
            responses =
                listOf(
                    response("read_file", """{"result":"one"}"""),
                    response("write_file", """{"result":"two"}"""),
                ),
            boundary = "test_results",
        )

        state.requireClosed("test_closed")
        assertEquals(emptyList<GeminiFunctionCallSnapshot>(), state.pendingCalls())
    }

    @Test
    fun shiftedFunctionResponseFailsWithoutRewritingTheName() {
        val state = GeminiToolHistoryState()
        state.acceptFunctionCalls(
            calls = listOf(call("read_file", """{"path":"one.txt"}""")),
            boundary = "test_calls",
        )

        val error =
            assertThrows(ProviderToolHistoryProtocolException::class.java) {
                state.acceptFunctionResponses(
                    responses = listOf(response("write_file", """{"result":"wrong"}""")),
                    boundary = "test_results",
                )
            }

        assertEquals(
            ProviderToolHistoryViolation.TOOL_RESULT_NAME_MISMATCH,
            error.violation,
        )
        assertEquals(1, state.pendingCalls().size)
    }

    @Test
    fun missingOrExtraFunctionResponsesFailsAtTheBoundary() {
        val missingState = GeminiToolHistoryState()
        missingState.acceptFunctionCalls(
            calls =
                listOf(
                    call("read_file", """{"path":"one.txt"}"""),
                    call("read_file", """{"path":"two.txt"}"""),
                ),
            boundary = "test_calls",
        )
        val missing =
            assertThrows(ProviderToolHistoryProtocolException::class.java) {
                missingState.acceptFunctionResponses(
                    responses = listOf(response("read_file", """{"result":"one"}""")),
                    boundary = "test_results",
                )
            }
        assertEquals(ProviderToolHistoryViolation.MISSING_TOOL_RESULT, missing.violation)

        val extraState = GeminiToolHistoryState()
        extraState.acceptFunctionCalls(
            calls = listOf(call("read_file", """{"path":"one.txt"}""")),
            boundary = "test_calls",
        )
        val extra =
            assertThrows(ProviderToolHistoryProtocolException::class.java) {
                extraState.acceptFunctionResponses(
                    responses =
                        listOf(
                            response("read_file", """{"result":"one"}"""),
                            response("read_file", """{"result":"two"}"""),
                        ),
                    boundary = "test_results",
                )
            }
        assertEquals(ProviderToolHistoryViolation.TOO_MANY_TOOL_RESULTS, extra.violation)
    }

    @Test
    fun thoughtSignaturesRemainAttachedToTheirCallPosition() {
        val state = GeminiToolHistoryState()
        state.acceptFunctionCalls(
            calls =
                listOf(
                    call("read_file", """{"path":"one.txt"}""", "sig-one"),
                    call("read_file", """{"path":"two.txt"}""", "sig-two"),
                ),
            boundary = "test_calls",
        )

        assertEquals(
            listOf("sig-one", "sig-two"),
            state.pendingCalls().map { it.thoughtSignature },
        )
    }

    private fun call(
        name: String,
        arguments: String,
        thoughtSignature: String? = null,
    ): GeminiFunctionCallSnapshot =
        GeminiFunctionCallSnapshot(
            name = name,
            canonicalArguments = canonicalGeminiJsonObject(arguments),
            thoughtSignature = thoughtSignature,
        )

    private fun response(
        name: String,
        response: String,
    ): GeminiFunctionResponseSnapshot =
        GeminiFunctionResponseSnapshot(
            name = name,
            canonicalResponse = canonicalGeminiJsonObject(response),
        )
}
