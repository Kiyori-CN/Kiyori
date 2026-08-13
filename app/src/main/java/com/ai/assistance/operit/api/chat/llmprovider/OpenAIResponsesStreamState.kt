package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.MessageProviderStateEntity
import com.ai.assistance.operit.data.model.ProviderExecutionEntity
import com.ai.assistance.operit.data.model.ProviderExecutionStatus
import com.ai.assistance.operit.util.stream.MessageFailureDiagnosticSource
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

internal data class OpenAIResponsesStreamEvent(
    val type: String,
    val sequenceNumber: Long,
    val remoteResponseId: String,
    val payloadJson: String,
)

internal class OpenAIResponsesExecutionState private constructor(
    val localExecutionId: String,
    val chatId: String,
    val messageTimestamp: Long,
    val variantIndex: Int,
    val provider: String,
    val modelName: String,
    val createdAt: Long,
    private val outputItems: MutableMap<Int, JSONObject>,
    initialRemoteResponseId: String?,
    initialLastAppliedSequence: Long,
    initialStatus: ProviderExecutionStatus,
    initialTerminalEventType: String?,
    initialUsageJson: String?,
    initialLastErrorCode: String?,
    initialLastErrorMessage: String?,
) {
    var remoteResponseId: String? = initialRemoteResponseId
        private set
    var lastAppliedSequence: Long = initialLastAppliedSequence
        private set
    var status: ProviderExecutionStatus = initialStatus
        private set
    var terminalEventType: String? = initialTerminalEventType
        private set
    var usageJson: String? = initialUsageJson
        private set
    var lastErrorCode: String? = initialLastErrorCode
        private set
    var lastErrorMessage: String? = initialLastErrorMessage
        private set
    val isTerminal: Boolean
        get() =
            status == ProviderExecutionStatus.COMPLETED ||
                status == ProviderExecutionStatus.FAILED ||
                status == ProviderExecutionStatus.INCOMPLETE ||
                status == ProviderExecutionStatus.CANCELLED ||
                status == ProviderExecutionStatus.EXPIRED

    fun parseEvent(payload: JSONObject): OpenAIResponsesStreamEvent {
        val type = payload.optString("type", "").trim()
        if (type.isEmpty()) {
            throw OpenAIResponsesProtocolException("Responses stream event has no type")
        }
        if (!payload.has("sequence_number") || payload.isNull("sequence_number")) {
            throw OpenAIResponsesProtocolException(
                "Responses stream event $type has no sequence_number"
            )
        }
        val sequenceNumber = payload.optLong("sequence_number", Long.MIN_VALUE)
        if (sequenceNumber < 0L) {
            throw OpenAIResponsesProtocolException(
                "Responses stream event $type has invalid sequence_number=$sequenceNumber"
            )
        }

        val responseObject = payload.optJSONObject("response")
        val eventResponseId =
            responseObject
                ?.optString("id", "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: payload.optString("response_id", "").trim().takeIf { it.isNotEmpty() }
                ?: remoteResponseId
                ?: throw OpenAIResponsesProtocolException(
                    "Responses stream event $type arrived before a response ID was established"
                )

        if (remoteResponseId != null && remoteResponseId != eventResponseId) {
            throw OpenAIResponsesProtocolException(
                "Responses stream switched from $remoteResponseId to $eventResponseId"
            )
        }

        return OpenAIResponsesStreamEvent(
            type = type,
            sequenceNumber = sequenceNumber,
            remoteResponseId = eventResponseId,
            payloadJson = payload.toString(),
        )
    }

    fun applyNewEvent(
        event: OpenAIResponsesStreamEvent,
        payload: JSONObject,
    ) {
        val expectedSequence =
            ProviderExecutionEntity.expectedNextSequence(lastAppliedSequence)
        if (event.sequenceNumber != expectedSequence) {
            throw OpenAIResponsesProtocolException(
                "Responses event sequence gap: expected $expectedSequence, " +
                    "received ${event.sequenceNumber}"
            )
        }
        if (remoteResponseId != null && remoteResponseId != event.remoteResponseId) {
            throw OpenAIResponsesProtocolException(
                "Responses stream switched from $remoteResponseId to ${event.remoteResponseId}"
            )
        }

        remoteResponseId = event.remoteResponseId
        applyOutputState(event.type, payload)
        applyLifecycle(event.type, payload)
        lastAppliedSequence = event.sequenceNumber
    }

    fun toMessageProviderState(updatedAt: Long): MessageProviderStateEntity =
        MessageProviderStateEntity(
            chatId = chatId,
            messageTimestamp = messageTimestamp,
            variantIndex = variantIndex,
            latestExecutionId = localExecutionId,
            provider = provider,
            modelName = modelName,
            remoteResponseId = remoteResponseId,
            status = status.name,
            lastAppliedSequence = lastAppliedSequence,
            terminalEventType = terminalEventType,
            outputItemsJson = outputItemsJson(),
            functionCallOutputsJson = "[]",
            usageJson = usageJson,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun applyOutputState(
        eventType: String,
        payload: JSONObject,
    ) {
        val responseObject = payload.optJSONObject("response")
        val responseOutput = responseObject?.optJSONArray("output")
        if (responseOutput != null) {
            replaceOutputItems(responseOutput)
        }

        when (eventType) {
            "response.output_item.added",
            "response.output_item.done",
            -> {
                val outputIndex = payload.optInt("output_index", -1)
                val item = payload.optJSONObject("item")
                if (outputIndex >= 0 && item != null) {
                    outputItems[outputIndex] = JSONObject(item.toString())
                }
            }

            "response.function_call_arguments.delta" -> {
                val outputIndex = payload.optInt("output_index", -1)
                if (outputIndex < 0) {
                    return
                }
                val item = outputItems[outputIndex] ?: JSONObject().apply {
                    put("type", "function_call")
                    payload.optString("item_id", "").takeIf { it.isNotBlank() }?.let {
                        put("id", it)
                    }
                    payload.optString("call_id", "").takeIf { it.isNotBlank() }?.let {
                        put("call_id", it)
                    }
                    payload.optString("name", "").takeIf { it.isNotBlank() }?.let {
                        put("name", it)
                    }
                    put("arguments", "")
                }
                val existingArguments = item.optString("arguments", "")
                val delta = payload.optString("delta", "")
                if (delta.isNotEmpty()) {
                    item.put("arguments", mergeArguments(existingArguments, delta))
                }
                outputItems[outputIndex] = item
            }

            "response.function_call_arguments.done" -> {
                val outputIndex = payload.optInt("output_index", -1)
                if (outputIndex < 0) {
                    return
                }
                val arguments = payload.optString("arguments", "")
                if (arguments.isNotEmpty()) {
                    val item = outputItems[outputIndex] ?: JSONObject().apply {
                        put("type", "function_call")
                    }
                    item.put("arguments", arguments)
                    payload.optString("call_id", "").takeIf { it.isNotBlank() }?.let {
                        item.put("call_id", it)
                    }
                    payload.optString("name", "").takeIf { it.isNotBlank() }?.let {
                        item.put("name", it)
                    }
                    outputItems[outputIndex] = item
                }
            }
        }
    }

    private fun applyLifecycle(
        eventType: String,
        payload: JSONObject,
    ) {
        val responseObject = payload.optJSONObject("response")
        usageJson = responseObject?.optJSONObject("usage")?.toString() ?: usageJson

        when (eventType) {
            "response.created" -> {
                status =
                    when (responseObject?.optString("status", "")?.trim()) {
                        "queued" -> ProviderExecutionStatus.QUEUED
                        else -> ProviderExecutionStatus.IN_PROGRESS
                    }
            }

            "response.queued" -> status = ProviderExecutionStatus.QUEUED
            "response.in_progress" -> status = ProviderExecutionStatus.IN_PROGRESS
            "response.completed" -> {
                status = ProviderExecutionStatus.COMPLETED
                terminalEventType = eventType
            }

            "response.failed",
            "response.error",
            -> {
                status = ProviderExecutionStatus.FAILED
                terminalEventType = eventType
                captureError(payload)
            }

            "response.incomplete" -> {
                status = ProviderExecutionStatus.INCOMPLETE
                terminalEventType = eventType
                captureError(payload)
            }

            "response.cancelled" -> {
                status = ProviderExecutionStatus.CANCELLED
                terminalEventType = eventType
                captureError(payload)
            }
        }
    }

    private fun captureError(payload: JSONObject) {
        val responseObject = payload.optJSONObject("response")
        val error =
            payload.optJSONObject("error")
                ?: responseObject?.optJSONObject("error")
                ?: responseObject?.optJSONObject("incomplete_details")
        lastErrorCode =
            error?.opt("code")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: responseObject?.optString("status", "")?.trim()?.takeIf { it.isNotEmpty() }
        lastErrorMessage =
            error?.optString("message", "")?.trim()?.takeIf { it.isNotEmpty() }
                ?: error?.optString("reason", "")?.trim()?.takeIf { it.isNotEmpty() }
                ?: responseObject?.optString("status", "")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun replaceOutputItems(output: JSONArray) {
        outputItems.clear()
        for (index in 0 until output.length()) {
            val item = output.optJSONObject(index) ?: continue
            outputItems[index] = JSONObject(item.toString())
        }
    }

    private fun outputItemsJson(): String {
        val output = JSONArray()
        outputItems.toSortedMap().values.forEach { item ->
            output.put(JSONObject(item.toString()))
        }
        return output.toString()
    }

    private fun mergeArguments(existing: String, incoming: String): String {
        if (incoming.isEmpty()) return existing
        if (existing.isEmpty()) return incoming
        return if (incoming.startsWith(existing)) incoming else existing + incoming
    }

    companion object {
        fun create(
            requestContext: ProviderRequestContext,
            provider: String,
            modelName: String,
            createdAt: Long,
        ): OpenAIResponsesExecutionState =
            OpenAIResponsesExecutionState(
                localExecutionId = requestContext.localExecutionId,
                chatId = requestContext.chatId,
                messageTimestamp = requestContext.messageTimestamp,
                variantIndex = requestContext.variantIndex,
                provider = provider,
                modelName = modelName,
                createdAt = createdAt,
                outputItems = mutableMapOf(),
                initialRemoteResponseId = null,
                initialLastAppliedSequence = ProviderExecutionEntity.NO_APPLIED_SEQUENCE,
                initialStatus = ProviderExecutionStatus.SUBMITTING,
                initialTerminalEventType = null,
                initialUsageJson = null,
                initialLastErrorCode = null,
                initialLastErrorMessage = null,
            )
    }
}

internal class OpenAIResponsesProtocolException(message: String) :
    IllegalStateException(message)

internal class OpenAIResponsesTransportInterruptedException(
    val responseId: String,
    val lastAppliedSequence: Long,
    cause: IOException,
) : IOException(
    "Responses stream interrupted for $responseId after sequence $lastAppliedSequence",
    cause,
)

internal class OpenAIResponsesSubmissionUnknownException(
    val localExecutionId: String?,
    cause: IOException,
    val transportDiagnostics: LlmTransportDiagnostics? = null,
) : IOException(
    buildSubmissionUnknownMessage(
        localExecutionId = localExecutionId,
        cause = cause,
        transportDiagnostics = transportDiagnostics,
    ),
    cause,
), MessageFailureDiagnosticSource {
    override val messageFailureExecutionId: String?
        get() = localExecutionId

    override val messageFailureDiagnosticCode: String
        get() =
            transportDiagnostics?.diagnosticCode
                ?: "OPENAI_RESPONSES_SUBMISSION_UNKNOWN"

    override val messageFailurePhase: String
        get() =
            transportDiagnostics?.stage?.name
                ?: "SUBMISSION_UNKNOWN"
}

private fun buildSubmissionUnknownMessage(
    localExecutionId: String?,
    cause: IOException,
    transportDiagnostics: LlmTransportDiagnostics?,
): String {
    val executionText =
        localExecutionId
            ?.takeIf { it.isNotBlank() }
            ?.let { " for local execution $it" }
            .orEmpty()
    val diagnosticText =
        transportDiagnostics
            ?.let { " [${it.diagnosticCode}, stage=${it.stage.name}]" }
            .orEmpty()
    return "Responses submission state is unknown$executionText$diagnosticText: " +
        (cause.message?.take(512) ?: "transport failure")
}

internal class OpenAIResponsesEventProcessingException(
    message: String,
    cause: Throwable,
) : IOException(message, cause)
