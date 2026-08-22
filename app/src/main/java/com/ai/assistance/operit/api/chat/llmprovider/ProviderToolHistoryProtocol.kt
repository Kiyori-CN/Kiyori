package com.ai.assistance.operit.api.chat.llmprovider

import java.util.ArrayDeque

internal enum class ProviderToolHistoryViolation {
    MISSING_TOOL_RESULT,
    TOOL_RESULT_WITHOUT_CALL,
    TOO_MANY_TOOL_RESULTS,
    DUPLICATE_TOOL_CALL_ID,
    TOOL_CALL_WITHOUT_PAYLOAD,
    TOOL_RESULT_WITHOUT_PAYLOAD,
    TOOL_PROTOCOL_DISABLED,
    TOOL_RESULT_NAME_MISMATCH,
    TOOL_RESULT_CALL_ID_MISMATCH,
}

internal class ProviderToolHistoryProtocolException(
    val violation: ProviderToolHistoryViolation,
    detail: String,
) : IllegalStateException("Provider tool history protocol violation [$violation]: $detail")

internal data class ProviderToolCallDescriptor(
    val callId: String,
    val toolName: String?,
)

internal data class ProviderToolResultDescriptor(
    val callId: String?,
    val toolName: String?,
)

/**
 * Validates the ordered assistant-tool-result transaction required by Chat Completions-style
 * providers.
 *
 * The state machine deliberately has no repair path. A missing or ambiguous tool result cannot be
 * represented as a provider message without changing the conversation's meaning, so request
 * compilation must stop before network submission.
 */
internal class ProviderToolHistoryState {
    private val pendingCalls = ArrayDeque<ProviderToolCallDescriptor>()

    fun acceptToolCalls(callIds: List<String>, boundary: String) {
        acceptToolCallDescriptors(
            calls = callIds.map { ProviderToolCallDescriptor(callId = it, toolName = null) },
            boundary = boundary,
        )
    }

    fun acceptToolCallDescriptors(
        calls: List<ProviderToolCallDescriptor>,
        boundary: String,
    ) {
        if (calls.isEmpty()) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.TOOL_CALL_WITHOUT_PAYLOAD,
                detail = "Structured tool call payload is empty at $boundary",
            )
        }

        val duplicateCallId =
            calls
                .groupingBy { it.callId }
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
                ?.key
        if (duplicateCallId != null) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.DUPLICATE_TOOL_CALL_ID,
                detail = "Duplicate tool call ID $duplicateCallId in $boundary",
            )
        }

        if (pendingCalls.isNotEmpty()) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.MISSING_TOOL_RESULT,
                detail = "New tool calls arrived before previous results at $boundary",
            )
        }

        calls.forEach { call ->
            if (call.callId.isBlank()) {
                throw ProviderToolHistoryProtocolException(
                    violation = ProviderToolHistoryViolation.TOOL_CALL_WITHOUT_PAYLOAD,
                    detail = "Structured tool call has no stable call ID at $boundary",
                )
            }
            if (call.toolName != null && call.toolName.isBlank()) {
                throw ProviderToolHistoryProtocolException(
                    violation = ProviderToolHistoryViolation.TOOL_CALL_WITHOUT_PAYLOAD,
                    detail = "Structured tool call has no tool name at $boundary",
                )
            }
            pendingCalls.addLast(call)
        }
    }

    fun acceptToolResults(resultCount: Int, boundary: String) {
        if (resultCount <= 0) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_PAYLOAD,
                detail = "Structured tool result payload is empty at $boundary",
            )
        }
        if (pendingCalls.isEmpty()) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_CALL,
                detail = "No pending tool call exists at $boundary",
            )
        }
        if (resultCount > pendingCalls.size) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.TOO_MANY_TOOL_RESULTS,
                detail = "Received $resultCount tool results for ${pendingCalls.size} pending calls at $boundary",
            )
        }
        repeat(resultCount) {
            pendingCalls.removeFirst()
        }
    }

    fun acceptNamedToolResults(
        results: List<ProviderToolResultDescriptor>,
        boundary: String,
    ) {
        if (results.isEmpty()) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_PAYLOAD,
                detail = "Structured tool result payload is empty at $boundary",
            )
        }
        if (pendingCalls.isEmpty()) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_CALL,
                detail = "No pending tool call exists at $boundary",
            )
        }
        if (results.size > pendingCalls.size) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.TOO_MANY_TOOL_RESULTS,
                detail = "Received ${results.size} tool results for ${pendingCalls.size} pending calls at $boundary",
            )
        }

        results.forEach { result ->
            val pending = pendingCalls.first()
            val resultCallId = result.callId?.trim()?.takeIf { it.isNotEmpty() }
            if (resultCallId != null && resultCallId != pending.callId) {
                throw ProviderToolHistoryProtocolException(
                    violation = ProviderToolHistoryViolation.TOOL_RESULT_CALL_ID_MISMATCH,
                    detail =
                        "Tool result call ID $resultCallId does not match pending call " +
                            "${pending.callId} at $boundary",
                )
            }
            val resultToolName = result.toolName?.trim()?.takeIf { it.isNotEmpty() }
            if (resultToolName == null || pending.toolName != resultToolName) {
                throw ProviderToolHistoryProtocolException(
                    violation = ProviderToolHistoryViolation.TOOL_RESULT_NAME_MISMATCH,
                    detail =
                        "Tool result name ${result.toolName ?: "<missing>"} does not match " +
                            "pending tool ${pending.toolName ?: "<missing>"} at $boundary",
                )
            }
            pendingCalls.removeFirst()
        }
    }

    fun acceptToolResult(callId: String, boundary: String) {
        if (callId.isBlank()) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_PAYLOAD,
                detail = "Structured tool result has no stable call ID at $boundary",
            )
        }
        if (pendingCalls.isEmpty()) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_CALL,
                detail = "No pending tool call exists for $callId at $boundary",
            )
        }
        val pending = pendingCalls.firstOrNull { it.callId == callId }
        if (pending == null || !pendingCalls.removeFirstOccurrence(pending)) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_CALL,
                detail = "No pending tool call matches $callId at $boundary",
            )
        }
    }

    fun requireClosed(boundary: String) {
        if (pendingCalls.isEmpty()) {
            return
        }
        throw ProviderToolHistoryProtocolException(
            violation = ProviderToolHistoryViolation.MISSING_TOOL_RESULT,
            detail = "Missing ${pendingCalls.size} tool results at $boundary",
        )
    }

    fun pendingCount(): Int = pendingCalls.size
}
