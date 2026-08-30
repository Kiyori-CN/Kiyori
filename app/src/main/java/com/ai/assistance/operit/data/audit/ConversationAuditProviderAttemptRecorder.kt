package com.ai.assistance.operit.data.audit

import android.content.Context
import com.ai.assistance.operit.api.chat.llmprovider.LlmLogPrivacy
import com.ai.assistance.operit.api.chat.llmprovider.LlmTransportDiagnostics
import com.ai.assistance.operit.api.chat.llmprovider.ProviderRequestContext
import com.ai.assistance.operit.data.model.ConversationAuditCompletenessStatus
import com.ai.assistance.operit.util.AppLogger
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Writes one bounded, content-free record for a real provider HTTP attempt.
 *
 * Semantic provider hops and transport attempts have different lifetimes. Keeping this boundary in
 * the existing audit repository prevents a retry or a transport reset from becoming an untraceable
 * UI-only notification while avoiding a second execution state owner.
 */
internal object ConversationAuditProviderAttemptRecorder {
    suspend fun record(
        context: Context,
        requestContext: ProviderRequestContext?,
        eventType: String,
        summary: String,
        provider: String,
        model: String,
        requestTraceId: String,
        endpointLabel: String,
        method: String,
        stream: Boolean,
        attemptNumber: Int,
        requestFingerprint: String? = null,
        submissionState: String? = null,
        retrySafety: String? = null,
        transport: LlmTransportDiagnostics? = null,
        providerCallId: String? = null,
        chunkCount: Int? = null,
        receivedCharacters: Int? = null,
        reasoningCharacters: Int? = null,
        visibleCharacters: Int? = null,
        rollbackCharacters: Int? = null,
        failureCode: String? = null,
        throwable: Throwable? = null,
        terminalState: String? = null,
        completeness: ConversationAuditCompletenessStatus =
            ConversationAuditCompletenessStatus.IN_PROGRESS,
    ) {
        if (requestContext == null) return

        val metadata =
            JSONObject()
                .put("schemaVersion", 1)
                .put("requestTraceId", bounded(requestTraceId, MAX_TRACE_ID_CHARACTERS))
                .put("provider", bounded(provider, MAX_PROVIDER_CHARACTERS))
                .put("model", bounded(model, MAX_MODEL_CHARACTERS))
                .put(
                    "endpoint",
                    bounded(
                        ConversationAuditRedactor.redactText(endpointLabel).value,
                        MAX_ENDPOINT_CHARACTERS,
                    ),
                )
                .put("method", bounded(method, MAX_METHOD_CHARACTERS))
                .put("stream", stream)
                .put("attemptNumber", attemptNumber)
                .put("hopOrdinal", requestContext.hopOrdinal)
                .put(
                    "requestFingerprint",
                    requestFingerprint?.let { bounded(it, MAX_FINGERPRINT_CHARACTERS) }
                        ?: JSONObject.NULL,
                )
                .put("submissionState", submissionState ?: JSONObject.NULL)
                .put("retrySafety", retrySafety ?: JSONObject.NULL)
                .put(
                    "providerCallId",
                    providerCallId?.let { bounded(it, MAX_PROVIDER_CALL_ID_CHARACTERS) }
                        ?: JSONObject.NULL,
                )
                .put("chunkCount", chunkCount ?: JSONObject.NULL)
                .put("receivedCharacters", receivedCharacters ?: JSONObject.NULL)
                .put("reasoningCharacters", reasoningCharacters ?: JSONObject.NULL)
                .put("visibleCharacters", visibleCharacters ?: JSONObject.NULL)
                .put("rollbackCharacters", rollbackCharacters ?: JSONObject.NULL)

        transport?.let { diagnostics ->
            metadata
                .put("diagnosticCode", diagnostics.diagnosticCode)
                .put("transportStage", diagnostics.stage.name)
                .put("protocol", diagnostics.protocol ?: JSONObject.NULL)
                .put("tlsVersion", diagnostics.tlsVersion ?: JSONObject.NULL)
                .put("requestBodyBytes", diagnostics.requestBodyBytes)
                .put("requestBodyStarted", diagnostics.requestBodyStarted)
                .put("responseHeadersReceived", diagnostics.responseHeadersReceived)
                .put("responseBodyStarted", diagnostics.responseBodyStarted)
                .put("responseStatusCode", diagnostics.responseStatusCode ?: JSONObject.NULL)
                .put("connectionReused", diagnostics.connectionReused ?: JSONObject.NULL)
                .put("connectionFailed", diagnostics.connectionFailed)
                .put("failureType", diagnostics.failureType ?: JSONObject.NULL)
                .put("responseCorrelationId", diagnostics.responseCorrelationId ?: JSONObject.NULL)
        }
        throwable?.let { error ->
            metadata
                .put("exceptionType", error.javaClass.name)
                .put(
                    "exceptionMessage",
                    LlmLogPrivacy.sanitizeDiagnostic(error.message, 384) ?: JSONObject.NULL,
                )
                .put("causeChain", sanitizeCauseChain(error))
        }

        val repository = ConversationAuditRepository.from(context)
        try {
            repository.appendEvent(
                ConversationAuditEventRequest(
                    chatId = requestContext.chatId,
                    category = "PROVIDER",
                    eventType = eventType,
                    actor = "KIYORI",
                    summary = summary,
                    messageTimestamp = requestContext.messageTimestamp,
                    variantIndex = requestContext.variantIndex,
                    localExecutionId = requestContext.localExecutionId,
                    providerCallId = providerCallId,
                    terminalState = terminalState,
                    completeness = completeness,
                    failureCode = failureCode,
                    payloads =
                        listOf(
                            ConversationAuditPayloadInput.text(
                                label = "provider_attempt",
                                role = "metadata",
                                value = metadata.toString(),
                                mediaType = "application/json",
                            )
                        ),
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (auditError: Exception) {
            AppLogger.e(
                "AIService",
                "Provider attempt 审计写入失败: eventType=$eventType",
                auditError,
            )
            runCatching {
                repository.markRecordingInterrupted(
                    chatId = requestContext.chatId,
                    failureCode = "RECORDING_INTERRUPTED",
                )
            }.onFailure { markerError ->
                AppLogger.e(
                    "AIService",
                    "Provider attempt 审计中断标记写入失败: eventType=$eventType",
                    markerError,
                )
            }
        }
    }

    private fun sanitizeCauseChain(error: Throwable): JSONArray {
        val chain = JSONArray()
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH && seen.add(current)) {
            chain.put(
                JSONObject()
                    .put("type", current.javaClass.name)
                    .put(
                        "message",
                        LlmLogPrivacy.sanitizeDiagnostic(current.message, MAX_CAUSE_MESSAGE_CHARACTERS)
                            ?: JSONObject.NULL,
                    )
            )
            current = current.cause
            depth++
        }
        return chain
    }

    private fun bounded(value: String, maxCharacters: Int): String =
        LlmLogPrivacy.sanitizeDiagnostic(value, maxCharacters) ?: "unknown"

    private const val MAX_CAUSE_DEPTH = 8
    private const val MAX_CAUSE_MESSAGE_CHARACTERS = 384
    private const val MAX_TRACE_ID_CHARACTERS = 96
    private const val MAX_PROVIDER_CHARACTERS = 64
    private const val MAX_MODEL_CHARACTERS = 192
    private const val MAX_ENDPOINT_CHARACTERS = 384
    private const val MAX_METHOD_CHARACTERS = 16
    private const val MAX_FINGERPRINT_CHARACTERS = 96
    private const val MAX_PROVIDER_CALL_ID_CHARACTERS = 192
}
