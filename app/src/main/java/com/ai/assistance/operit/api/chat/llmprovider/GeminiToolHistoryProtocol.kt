package com.ai.assistance.operit.api.chat.llmprovider

import com.google.gson.JsonParser
import java.util.ArrayDeque

internal data class GeminiFunctionCallSnapshot(
    val name: String,
    val canonicalArguments: String,
    val thoughtSignature: String?,
) {
    init {
        require(name.isNotBlank()) { "Gemini function call name must not be blank" }
        require(canonicalArguments.isNotBlank()) {
            "Gemini function call arguments must not be blank"
        }
    }
}

internal data class GeminiFunctionResponseSnapshot(
    val name: String,
    val canonicalResponse: String,
) {
    init {
        require(name.isNotBlank()) { "Gemini function response name must not be blank" }
        require(canonicalResponse.isNotBlank()) {
            "Gemini function response payload must not be blank"
        }
    }
}

/**
 * Gemini does not expose the Chat Completions-style call ID in its function-call wire shape.
 * The ordered model-call/function-response pair is therefore the protocol identity. Names and
 * payloads are still checked before submission so a missing or shifted result cannot be silently
 * rewritten into another conversation.
 */
internal class GeminiToolHistoryState {
    private val pendingCalls = ArrayDeque<GeminiFunctionCallSnapshot>()

    fun acceptFunctionCalls(
        calls: List<GeminiFunctionCallSnapshot>,
        boundary: String,
    ) {
        if (calls.isEmpty()) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.TOOL_CALL_WITHOUT_PAYLOAD,
                detail = "Gemini function-call payload is empty at $boundary",
            )
        }
        if (pendingCalls.isNotEmpty()) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.MISSING_TOOL_RESULT,
                detail = "Gemini function calls arrived before previous results at $boundary",
            )
        }
        pendingCalls.addAll(calls)
    }

    fun acceptFunctionResponses(
        responses: List<GeminiFunctionResponseSnapshot>,
        boundary: String,
    ) {
        if (responses.isEmpty()) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_PAYLOAD,
                detail = "Gemini function-response payload is empty at $boundary",
            )
        }
        if (pendingCalls.isEmpty()) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_CALL,
                detail = "Gemini has no pending function calls at $boundary",
            )
        }
        if (responses.size != pendingCalls.size) {
            val violation =
                if (responses.size > pendingCalls.size) {
                    ProviderToolHistoryViolation.TOO_MANY_TOOL_RESULTS
                } else {
                    ProviderToolHistoryViolation.MISSING_TOOL_RESULT
                }
            throw ProviderToolHistoryProtocolException(
                violation = violation,
                detail =
                    "Gemini received ${responses.size} function responses for " +
                        "${pendingCalls.size} pending calls at $boundary",
            )
        }

        responses.forEachIndexed { index, response ->
            val expected = pendingCalls.elementAt(index)
            if (response.name != expected.name) {
                throw ProviderToolHistoryProtocolException(
                    violation = ProviderToolHistoryViolation.TOOL_RESULT_NAME_MISMATCH,
                    detail =
                        "Gemini function response '${response.name}' does not match " +
                            "pending call '${expected.name}' at position $index/$boundary",
                )
            }
        }

        repeat(responses.size) {
            pendingCalls.removeFirst()
        }
    }

    fun requireClosed(boundary: String) {
        if (pendingCalls.isEmpty()) {
            return
        }
        throw ProviderToolHistoryProtocolException(
            violation = ProviderToolHistoryViolation.MISSING_TOOL_RESULT,
            detail =
                "Gemini is missing ${pendingCalls.size} function responses at $boundary",
        )
    }

    fun pendingCalls(): List<GeminiFunctionCallSnapshot> = pendingCalls.toList()
}

internal fun canonicalGeminiJsonObject(json: String): String {
    return ProviderToolCallIdentityContract.canonicalJsonText(
        JsonParser.parseString(json).toString(),
    )
}
