package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIResponsesTerminalSnapshotTest {
    @Test
    fun noIntermediateText_emitsCompleteTerminalText() {
        assertEquals(
            "complete response",
            OpenAIResponsesTerminalSnapshot.missingTextSuffix(
                streamedText = "",
                terminalText = "complete response",
            ),
        )
    }

    @Test
    fun partialIntermediateText_emitsOnlyMissingSuffix() {
        assertEquals(
            " response",
            OpenAIResponsesTerminalSnapshot.missingTextSuffix(
                streamedText = "complete",
                terminalText = "complete response",
            ),
        )
    }

    @Test
    fun terminalSnapshotNeverRetractsConfirmedText() {
        assertEquals(
            "",
            OpenAIResponsesTerminalSnapshot.missingTextSuffix(
                streamedText = "complete response",
                terminalText = "complete",
            ),
        )
    }

    @Test
    fun divergentTerminalSnapshotFailsProtocolValidation() {
        val failure =
            runCatching {
                OpenAIResponsesTerminalSnapshot.missingTextSuffix(
                    streamedText = "confirmed",
                    terminalText = "conflicted",
                )
            }.exceptionOrNull()

        assertTrue(failure is OpenAIResponsesProtocolException)
    }

    @Test
    fun terminalToolCallUsesStableCallIdInsteadOfFilteredArrayIndex() {
        assertEquals(
            2,
            OpenAIResponsesTerminalSnapshot.resolveToolCallIndex(
                terminalOutputIndex = 0,
                callId = "call-1",
                indexByCallId = mapOf("call-1" to 2),
                callIdByIndex = mapOf(2 to "call-1"),
            ),
        )
    }

    @Test
    fun terminalToolArgumentsCanExtendStreamedPrefixOnce() {
        assertEquals(
            """{"path":"notes.txt"}""",
            OpenAIResponsesTerminalSnapshot.toolArgumentsUpdate(
                callId = "call-1",
                streamedArguments = """{"path":""",
                terminalArguments = """{"path":"notes.txt"}""",
            ),
        )
    }

    @Test
    fun semanticallyEqualTerminalToolArgumentsDoNotEmitAgain() {
        assertEquals(
            "",
            OpenAIResponsesTerminalSnapshot.toolArgumentsUpdate(
                callId = "call-1",
                streamedArguments = """{"path":"notes.txt","line":1}""",
                terminalArguments = """{ "line": 1, "path": "notes.txt" }""",
            ),
        )
    }

    @Test
    fun conflictingTerminalToolArgumentsFailProtocolValidation() {
        val failure =
            runCatching {
                OpenAIResponsesTerminalSnapshot.toolArgumentsUpdate(
                    callId = "call-1",
                    streamedArguments = """{"path":"a.txt"}""",
                    terminalArguments = """{"path":"b.txt"}""",
                )
            }.exceptionOrNull()

        assertTrue(failure is OpenAIResponsesProtocolException)
    }

    @Test
    fun sameOutputIndexCannotChangeToolCallIdentity() {
        val failure =
            runCatching {
                OpenAIResponsesTerminalSnapshot.resolveToolCallIndex(
                    terminalOutputIndex = 1,
                    callId = "call-new",
                    indexByCallId = emptyMap(),
                    callIdByIndex = mapOf(1 to "call-old"),
                )
            }.exceptionOrNull()

        assertTrue(failure is OpenAIResponsesProtocolException)
    }
}
