package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal data class OpenAIHostedWebSearchCompatibilityStatus(
    val state: String,
    val testedAtEpochMillis: Long?,
    val responseId: String?,
    val evidenceMode: String?,
    val schemaRevision: Int?,
    val errorCode: String?,
    val httpStatus: Int?,
    val message: String?,
    val providerErrorType: String?,
    val providerErrorCode: String?,
    val providerRequestId: String?,
)

internal object OpenAIHostedWebSearchCompatibilityStatusResolver {
    fun resolve(
        binding: OpenAIHostedWebSearchBinding,
        compatibilityRecord: OpenAIHostedWebSearchCompatibilityRecord?,
        compatibilityFailureRecord: OpenAIHostedWebSearchCompatibilityFailureRecord?,
        hasOtherCompatibilityRecords: Boolean = false,
    ): OpenAIHostedWebSearchCompatibilityStatus {
        val relayProbeRequired =
            binding.providerContract ==
                OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT
        val fingerprintDigest = binding.compatibilityFingerprint().digest
        val state =
            when {
                !relayProbeRequired -> "not_required"
                compatibilityRecord?.fingerprintDigest == fingerprintDigest -> "valid"
                compatibilityFailureRecord?.fingerprintDigest == fingerprintDigest -> "failed"
                hasOtherCompatibilityRecords -> "stale"
                else -> "missing"
            }
        return when (state) {
            "valid" ->
                OpenAIHostedWebSearchCompatibilityStatus(
                    state = state,
                    testedAtEpochMillis = compatibilityRecord?.testedAtEpochMillis,
                    responseId = compatibilityRecord?.responseId,
                    evidenceMode = compatibilityRecord?.evidenceMode?.wireValue,
                    schemaRevision = compatibilityRecord?.schemaRevision,
                    errorCode = null,
                    httpStatus = null,
                    message = null,
                    providerErrorType = null,
                    providerErrorCode = null,
                    providerRequestId = null,
                )

            "failed" ->
                OpenAIHostedWebSearchCompatibilityStatus(
                    state = state,
                    testedAtEpochMillis = compatibilityFailureRecord?.testedAtEpochMillis,
                    responseId = null,
                    evidenceMode = null,
                    schemaRevision = compatibilityFailureRecord?.schemaRevision,
                    errorCode = compatibilityFailureRecord?.errorCode?.name,
                    httpStatus = compatibilityFailureRecord?.httpStatus,
                    message = compatibilityFailureRecord?.sanitizedMessage,
                    providerErrorType = compatibilityFailureRecord?.providerErrorType,
                    providerErrorCode = compatibilityFailureRecord?.providerErrorCode,
                    providerRequestId = compatibilityFailureRecord?.providerRequestId,
                )

            else ->
                OpenAIHostedWebSearchCompatibilityStatus(
                    state = state,
                    testedAtEpochMillis = null,
                    responseId = null,
                    evidenceMode = null,
                    schemaRevision = null,
                    errorCode = null,
                    httpStatus = null,
                    message = null,
                    providerErrorType = null,
                    providerErrorCode = null,
                    providerRequestId = null,
                )
        }
    }
}

internal object OpenAIHostedWebSearchBridgePolicy {
    private val SEARCH_REQUEST_FIELDS =
        setOf(
            "query",
            "context_size",
            "allowed_domains",
            "blocked_domains",
            "use_configured_location",
        )

    fun ownsBridge(boundToolPkgContainerName: String?): Boolean =
        boundToolPkgContainerName == OpenAIHostedWebSearchContract.TOOLPKG_ID

    fun requireAuthorizedCaller(
        boundToolPkgContainerName: String?,
        callId: String,
        isExecutionCallActive: (String) -> Boolean,
    ): String {
        if (!ownsBridge(boundToolPkgContainerName)) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CALLER_NOT_AUTHORIZED,
                message =
                    "OpenAI Web Search is only available to its bound ToolPkg container.",
            )
        }
        val normalizedCallId = callId.trim()
        if (normalizedCallId.isEmpty() || !isExecutionCallActive(normalizedCallId)) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CALLER_NOT_AUTHORIZED,
                message =
                    "OpenAI Web Search requires an active bound ToolPkg execution call.",
            )
        }
        return normalizedCallId
    }

    fun requireCompatibilityProbeCaller(
        boundToolPkgContainerName: String?,
        callId: String,
        isExecutionCallActive: (String) -> Boolean,
        resolveExecutionRuntimeKind: (String) -> String?,
    ): String {
        val normalizedCallId =
            requireAuthorizedCaller(
                boundToolPkgContainerName = boundToolPkgContainerName,
                callId = callId,
                isExecutionCallActive = isExecutionCallActive,
            )
        val runtimeKind =
            resolveExecutionRuntimeKind(normalizedCallId)
                ?.trim()
                ?.lowercase()
        if (runtimeKind != "ui") {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CALLER_NOT_AUTHORIZED,
                message =
                    "OpenAI Web Search compatibility probing is only available from the " +
                        "plugin settings UI.",
            )
        }
        return normalizedCallId
    }

    fun parseSearchRequest(
        requestId: String,
        requestJson: String,
    ): OpenAIHostedWebSearchRequest {
        val parsed =
            runCatching { JSONTokener(requestJson.trim()).nextValue() }.getOrElse { error ->
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "OpenAI Web Search request must be a JSON object.",
                    cause = error,
                )
            }
        val requestObject =
            parsed as? JSONObject
                ?: throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "OpenAI Web Search request must be a JSON object.",
                )
        val keys = requestObject.keys().asSequence().toSet()
        val unsupportedFields = keys - SEARCH_REQUEST_FIELDS
        if (unsupportedFields.isNotEmpty()) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message =
                    "OpenAI Web Search request contains unsupported fields: " +
                        unsupportedFields.sorted().joinToString(", "),
            )
        }

        val query =
            requestObject.opt("query") as? String
                ?: throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "OpenAI Web Search request query must be a string.",
                )
        val contextSize =
            if (!requestObject.has("context_size") || requestObject.isNull("context_size")) {
                null
            } else {
                val rawContextSize =
                    requestObject.opt("context_size") as? String
                        ?: throw OpenAIHostedWebSearchException(
                            code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                            message =
                                "OpenAI Web Search request context_size must be a string.",
                        )
                OpenAIHostedWebSearchContextSize.parse(rawContextSize)
            }

        return OpenAIHostedWebSearchRequest(
            requestId = requestId,
            query = query,
            contextSize = contextSize,
            allowedDomains = requestObject.readStringArray("allowed_domains"),
            blockedDomains = requestObject.readStringArray("blocked_domains"),
            useConfiguredLocation =
                requestObject.readOptionalBoolean(
                    key = "use_configured_location",
                    defaultValue = false,
                ),
        )
    }

    private fun JSONObject.readStringArray(key: String): List<String> {
        if (!has(key) || isNull(key)) {
            return emptyList()
        }
        val array =
            opt(key) as? JSONArray
                ?: throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "OpenAI Web Search request $key must be a string array.",
                )
        return buildList {
            for (index in 0 until array.length()) {
                val value =
                    array.opt(index) as? String
                        ?: throw OpenAIHostedWebSearchException(
                            code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                            message =
                                "OpenAI Web Search request $key must contain only strings.",
                        )
                add(value)
            }
        }
    }

    private fun JSONObject.readOptionalBoolean(key: String, defaultValue: Boolean): Boolean {
        if (!has(key) || isNull(key)) {
            return defaultValue
        }
        return opt(key) as? Boolean
            ?: throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "OpenAI Web Search request $key must be a boolean.",
            )
    }
}

internal class OpenAIHostedWebSearchRequestOwnershipRegistry {
    private data class OwnedRequest(
        val callId: String,
        val cancel: (String) -> Unit,
    )

    private val requests = ConcurrentHashMap<String, OwnedRequest>()

    fun register(
        requestId: String,
        callId: String,
        cancel: (String) -> Unit,
    ) {
        val previous =
            requests.putIfAbsent(
                requestId,
                OwnedRequest(callId = callId, cancel = cancel),
            )
        check(previous == null) { "OpenAI Web Search request ID is already active" }
    }

    fun requestCancellation(requestId: String, reason: String): Boolean {
        val request = requests[requestId.trim()] ?: return false
        request.cancel(reason)
        return true
    }

    fun complete(requestId: String): Boolean = requests.remove(requestId.trim()) != null

    fun requestCancellationForCall(callId: String, reason: String): Int {
        val normalizedCallId = callId.trim()
        val owned =
            requests.entries
                .filter { entry -> entry.value.callId == normalizedCallId }
                .map { entry -> entry.value }
        owned.forEach { request -> request.cancel(reason) }
        return owned.size
    }

    fun cancelAll(reason: String): Int {
        val owned =
            requests.entries.mapNotNull { entry ->
                if (requests.remove(entry.key, entry.value)) {
                    entry.value
                } else {
                    null
                }
            }
        owned.forEach { request -> request.cancel(reason) }
        return owned.size
    }
}

internal class ToolPkgOpenAIWebSearchBridge(
    context: Context,
    private val boundToolPkgContainerName: String?,
    private val isExecutionCallActive: (String) -> Boolean,
    private val resolveExecutionRuntimeKind: (String) -> String?,
    private val bindingResolver: OpenAIHostedWebSearchBindingResolver =
        OpenAIHostedWebSearchBindingResolver(context.applicationContext),
    private val gateway: OpenAIHostedWebSearchGateway = OpenAIHostedWebSearchGateway(),
    private val compatibilityProbe: OpenAIHostedWebSearchCompatibilityProbe =
        OpenAIHostedWebSearchCompatibilityProbe(context.applicationContext),
    private val requestRegistry: OpenAIHostedWebSearchRequestOwnershipRegistry =
        OpenAIHostedWebSearchRequestOwnershipRegistry(),
    private val bridgeScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    fun getStatus(
        callId: String,
        deliverResult: (String) -> Unit,
    ) {
        launchLocalOperation(callId, deliverResult) {
            val resolved =
                bindingResolver.resolve(
                    requireRelayProbe = false,
                    advanceModelConfigKey = false,
                )
            buildStatusEnvelope(resolved)
        }
    }

    fun validateLocalConfiguration(
        callId: String,
        deliverResult: (String) -> Unit,
    ) {
        launchLocalOperation(callId, deliverResult) {
            val resolved =
                bindingResolver.resolve(
                    requireRelayProbe = false,
                    advanceModelConfigKey = false,
                )
            JSONObject()
                .put("success", true)
                .put("valid", true)
                .put("status", buildSanitizedStatus(resolved))
        }
    }

    fun search(
        callId: String,
        requestJson: String,
        deliverResult: (String) -> Unit,
    ): String {
        val normalizedCallId =
            runCatching {
                OpenAIHostedWebSearchBridgePolicy.requireAuthorizedCaller(
                    boundToolPkgContainerName = boundToolPkgContainerName,
                    callId = callId,
                    isExecutionCallActive = isExecutionCallActive,
                )
            }.getOrElse { error ->
                return error.toOpenAIWebSearchEnvelope(requestId = null).toString()
            }
        val requestId = nextRequestId("ows")
        val request =
            runCatching {
                OpenAIHostedWebSearchBridgePolicy.parseSearchRequest(
                    requestId = requestId,
                    requestJson = requestJson,
                )
            }.getOrElse { error ->
                return error.toOpenAIWebSearchEnvelope(requestId).toString()
            }

        lateinit var job: Job
        job =
            bridgeScope.launch(start = CoroutineStart.LAZY) {
                val resultJson =
                    try {
                        val resolved =
                            bindingResolver.resolve(
                                requireRelayProbe = true,
                                advanceModelConfigKey = true,
                            )
                        val effectiveRequest =
                            OpenAIHostedWebSearchPolicy.compileEffectiveRequest(
                                binding = resolved.binding,
                                request = request,
                            )
                        gateway.execute(
                            binding = resolved.binding,
                            request = effectiveRequest,
                        ).result.toJson()
                    } catch (error: Throwable) {
                        error.toOpenAIWebSearchEnvelope(requestId)
                    }
                val shouldDeliver = requestRegistry.complete(requestId)
                if (shouldDeliver && isExecutionCallActive(normalizedCallId)) {
                    deliverResult(resultJson.toString())
                }
            }
        requestRegistry.register(
            requestId = requestId,
            callId = normalizedCallId,
            cancel = { reason ->
                gateway.cancel(requestId)
                job.cancel(CancellationException(reason))
            },
        )
        if (!isExecutionCallActive(normalizedCallId)) {
            requestRegistry.requestCancellationForCall(
                callId = normalizedCallId,
                reason = "OpenAI Web Search execution call ended before request dispatch.",
            )
            return OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.REQUEST_CANCELLED,
                message = "OpenAI Web Search execution call ended before request dispatch.",
            ).toJson(requestId).toString()
        }
        job.start()
        return buildStartedEnvelope(requestId).toString()
    }

    fun runCompatibilityProbe(
        callId: String,
        deliverResult: (String) -> Unit,
    ): String {
        val normalizedCallId =
            runCatching {
                OpenAIHostedWebSearchBridgePolicy.requireCompatibilityProbeCaller(
                    boundToolPkgContainerName = boundToolPkgContainerName,
                    callId = callId,
                    isExecutionCallActive = isExecutionCallActive,
                    resolveExecutionRuntimeKind = resolveExecutionRuntimeKind,
                )
            }.getOrElse { error ->
                return error.toOpenAIWebSearchEnvelope(requestId = null).toString()
            }
        val requestId = nextRequestId("ows_probe")
        lateinit var job: Job
        job =
            bridgeScope.launch(start = CoroutineStart.LAZY) {
                val resultJson =
                    try {
                        compatibilityProbe.run(requestId).toJson()
                    } catch (error: Throwable) {
                        error.toOpenAIWebSearchEnvelope(requestId)
                    }
                val shouldDeliver = requestRegistry.complete(requestId)
                if (shouldDeliver && isExecutionCallActive(normalizedCallId)) {
                    deliverResult(resultJson.toString())
                }
            }
        requestRegistry.register(
            requestId = requestId,
            callId = normalizedCallId,
            cancel = { reason ->
                compatibilityProbe.cancel(requestId)
                job.cancel(CancellationException(reason))
            },
        )
        if (!isExecutionCallActive(normalizedCallId)) {
            requestRegistry.requestCancellationForCall(
                callId = normalizedCallId,
                reason = "OpenAI Web Search probe call ended before request dispatch.",
            )
            return OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.REQUEST_CANCELLED,
                message = "OpenAI Web Search probe call ended before request dispatch.",
            ).toJson(requestId).toString()
        }
        job.start()
        return buildStartedEnvelope(requestId).toString()
    }

    fun cancel(
        callId: String,
        requestId: String,
    ): String {
        return try {
            OpenAIHostedWebSearchBridgePolicy.requireAuthorizedCaller(
                boundToolPkgContainerName = boundToolPkgContainerName,
                callId = callId,
                isExecutionCallActive = isExecutionCallActive,
            )
            val normalizedRequestId = requestId.trim()
            if (normalizedRequestId.isEmpty()) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "OpenAI Web Search request ID must not be blank.",
                )
            }
            val cancelled =
                requestRegistry.requestCancellation(
                    requestId = normalizedRequestId,
                    reason = "OpenAI Web Search request was cancelled by its ToolPkg.",
                )
            JSONObject()
                .put("success", true)
                .put("request_id", normalizedRequestId)
                .put("cancelled", cancelled)
                .toString()
        } catch (error: Throwable) {
            error.toOpenAIWebSearchEnvelope(requestId.trim().ifEmpty { null }).toString()
        }
    }

    fun cancelForCall(callId: String, reason: String): Int =
        requestRegistry.requestCancellationForCall(callId = callId, reason = reason)

    fun close(reason: String) {
        requestRegistry.cancelAll(reason)
        bridgeScope.cancel(reason)
    }

    private fun launchLocalOperation(
        callId: String,
        deliverResult: (String) -> Unit,
        operation: suspend () -> JSONObject,
    ) {
        val normalizedCallId =
            runCatching {
                OpenAIHostedWebSearchBridgePolicy.requireAuthorizedCaller(
                    boundToolPkgContainerName = boundToolPkgContainerName,
                    callId = callId,
                    isExecutionCallActive = isExecutionCallActive,
                )
            }.getOrElse { error ->
                deliverResult(error.toOpenAIWebSearchEnvelope(requestId = null).toString())
                return
            }
        bridgeScope.launch {
            val resultJson =
                try {
                    operation()
                } catch (error: Throwable) {
                    error.toOpenAIWebSearchEnvelope(requestId = null)
                }
            if (isExecutionCallActive(normalizedCallId)) {
                deliverResult(resultJson.toString())
            }
        }
    }

    private fun buildStatusEnvelope(
        resolved: OpenAIHostedWebSearchResolvedBinding,
    ): JSONObject =
        JSONObject()
            .put("success", true)
            .put("status", buildSanitizedStatus(resolved))

    private fun buildSanitizedStatus(
        resolved: OpenAIHostedWebSearchResolvedBinding,
    ): JSONObject {
        val binding = resolved.binding
        val compatibilityRecord = resolved.compatibilityRecord
        val compatibilityFailureRecord = resolved.compatibilityFailureRecord
        val relayProbeRequired =
            binding.providerContract ==
                OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT
        val compatibilityStatus =
            OpenAIHostedWebSearchCompatibilityStatusResolver.resolve(
                binding = binding,
                compatibilityRecord = compatibilityRecord,
                compatibilityFailureRecord = compatibilityFailureRecord,
                hasOtherCompatibilityRecords = resolved.hasOtherCompatibilityRecords,
            )
        return JSONObject()
            .put("toolpkg_id", OpenAIHostedWebSearchContract.TOOLPKG_ID)
            .put("tool_name", OpenAIHostedWebSearchContract.TOOL_NAME)
            .put("config_source", binding.configSource.name)
            .put("provider_contract", binding.providerContract.name)
            .put("model_config_id", binding.modelConfigId ?: JSONObject.NULL)
            .put("endpoint", binding.endpoint)
            .put("model", binding.modelName)
            .put("mode", binding.mode.wireValue)
            .put("reasoning_effort", binding.reasoningEffort.name.lowercase())
            .put("context_size", binding.contextSize.wireValue)
            .put("return_token_budget", binding.returnTokenBudget.wireValue)
            .put("max_output_tokens", binding.maxOutputTokens ?: JSONObject.NULL)
            .put("timeout_seconds", binding.timeoutSeconds)
            .put("max_concurrent_requests", binding.maxConcurrentRequests)
            .put("requests_per_minute", binding.requestsPerMinute)
            .put(
                "header_names",
                JSONArray(
                    (listOf(binding.authHeaderName) + binding.extraHeaders.keys)
                        .distinctBy { headerName -> headerName.lowercase() }
                        .sortedWith(String.CASE_INSENSITIVE_ORDER)
                ),
            )
            .put("api_key_configured", binding.apiKey.isNotBlank())
            .put("api_key_revision", binding.credentialRevision())
            .put("auth_scheme_present", binding.authScheme.isNotBlank())
            .put("auth_scheme_kind", binding.authSchemeKind())
            .put("chat_provider_independent", true)
            .put(
                "compatibility",
                JSONObject()
                    .put("required", relayProbeRequired)
                    .put("state", compatibilityStatus.state)
                    .put(
                        "tested_at_epoch_millis",
                        compatibilityStatus.testedAtEpochMillis ?: JSONObject.NULL,
                    )
                    .put(
                        "response_id",
                        compatibilityStatus.responseId ?: JSONObject.NULL,
                    )
                    .put(
                        "evidence_mode",
                        compatibilityStatus.evidenceMode ?: JSONObject.NULL,
                    )
                    .put(
                        "schema_revision",
                        compatibilityStatus.schemaRevision ?: JSONObject.NULL,
                    )
                    .put(
                        "error_code",
                        compatibilityStatus.errorCode ?: JSONObject.NULL,
                    )
                    .put(
                        "http_status",
                        compatibilityStatus.httpStatus ?: JSONObject.NULL,
                    )
                    .put(
                        "message",
                        compatibilityStatus.message ?: JSONObject.NULL,
                    )
                    .put(
                        "provider_error_type",
                        compatibilityStatus.providerErrorType ?: JSONObject.NULL,
                    )
                    .put(
                        "provider_error_code",
                        compatibilityStatus.providerErrorCode ?: JSONObject.NULL,
                    )
                    .put(
                        "provider_request_id",
                        compatibilityStatus.providerRequestId ?: JSONObject.NULL,
                    ),
            )
    }

    private fun buildStartedEnvelope(requestId: String): JSONObject =
        JSONObject()
            .put("success", true)
            .put("request_id", requestId)

    private fun nextRequestId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "")}"
}

private fun Throwable.toOpenAIWebSearchEnvelope(requestId: String?): JSONObject {
    return when (this) {
        is OpenAIHostedWebSearchException -> toJson(requestId)
        is CancellationException ->
            OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.REQUEST_CANCELLED,
                message = "OpenAI Web Search request was cancelled.",
                cause = this,
            ).toJson(requestId)
        else ->
            OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.RESPONSE_SCHEMA_INVALID,
                message = "OpenAI Web Search host operation failed.",
                cause = this,
            ).toJson(requestId)
    }
}
