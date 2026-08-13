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

    fun requireSettingsUiCaller(
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
                    "OpenAI Web Search settings actions are only available from the " +
                        "plugin settings UI.",
            )
        }
        return normalizedCallId
    }

    fun requireCompatibilityProbeCaller(
        boundToolPkgContainerName: String?,
        callId: String,
        isExecutionCallActive: (String) -> Boolean,
        resolveExecutionRuntimeKind: (String) -> String?,
    ): String =
        requireSettingsUiCaller(
            boundToolPkgContainerName = boundToolPkgContainerName,
            callId = callId,
            isExecutionCallActive = isExecutionCallActive,
            resolveExecutionRuntimeKind = resolveExecutionRuntimeKind,
        )

    fun parseSearchRequest(
        requestId: String,
        requestJson: String,
    ): OpenAIHostedWebSearchRequest {
        val parsed =
            runCatching { JSONTokener(requestJson.trim()).nextValue() }.getOrElse { error ->
                throw openAIHostedWebSearchInvalidArgument(
                    field = "request",
                    reason = OpenAIHostedWebSearchArgumentReason.INVALID_TYPE,
                    message = "OpenAI Web Search request must be a JSON object.",
                    cause = error,
                )
            }
        val requestObject =
            parsed as? JSONObject
                ?: throw openAIHostedWebSearchInvalidArgument(
                    field = "request",
                    reason = OpenAIHostedWebSearchArgumentReason.INVALID_TYPE,
                    message = "OpenAI Web Search request must be a JSON object.",
                )
        val keys = requestObject.keys().asSequence().toSet()
        val unsupportedFields = keys - SEARCH_REQUEST_FIELDS
        if (unsupportedFields.isNotEmpty()) {
            throw openAIHostedWebSearchInvalidArgument(
                field = "request",
                reason = OpenAIHostedWebSearchArgumentReason.INVALID_VALUE,
                message =
                    "OpenAI Web Search request contains unsupported fields: " +
                        unsupportedFields.sorted().joinToString(", "),
            )
        }

        if (!requestObject.has("query") || requestObject.isNull("query")) {
            throw openAIHostedWebSearchInvalidArgument(
                field = "query",
                reason = OpenAIHostedWebSearchArgumentReason.MISSING,
                message = "OpenAI Web Search request query is required.",
            )
        }
        val query =
            requestObject.opt("query") as? String
                ?: throw openAIHostedWebSearchInvalidArgument(
                    field = "query",
                    reason = OpenAIHostedWebSearchArgumentReason.INVALID_TYPE,
                    message = "OpenAI Web Search request query must be a string.",
                )
        val contextSize =
            if (!requestObject.has("context_size") || requestObject.isNull("context_size")) {
                null
            } else {
                val rawContextSize =
                    requestObject.opt("context_size") as? String
                        ?: throw openAIHostedWebSearchInvalidArgument(
                            field = "context_size",
                            reason = OpenAIHostedWebSearchArgumentReason.INVALID_TYPE,
                            message =
                                "OpenAI Web Search request context_size must be a string.",
                        )
                runCatching {
                    OpenAIHostedWebSearchContextSize.parse(rawContextSize)
                }.getOrElse { error ->
                    throw openAIHostedWebSearchInvalidArgument(
                        field = "context_size",
                        reason = OpenAIHostedWebSearchArgumentReason.INVALID_VALUE,
                        message =
                            "OpenAI Web Search request context_size must be low, medium, or high.",
                        cause = error,
                    )
                }
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
                ?: throw openAIHostedWebSearchInvalidArgument(
                    field = key,
                    reason = OpenAIHostedWebSearchArgumentReason.INVALID_TYPE,
                    message = "OpenAI Web Search request $key must be a string array.",
                )
        return buildList {
            for (index in 0 until array.length()) {
                val value =
                    array.opt(index) as? String
                        ?: throw openAIHostedWebSearchInvalidArgument(
                            field = key,
                            reason = OpenAIHostedWebSearchArgumentReason.INVALID_TYPE,
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
            ?: throw openAIHostedWebSearchInvalidArgument(
                field = key,
                reason = OpenAIHostedWebSearchArgumentReason.INVALID_TYPE,
                message = "OpenAI Web Search request $key must be a boolean.",
            )
    }
}

internal class OpenAIHostedWebSearchRequestOwnershipRegistry {
    private data class OwnedRequest(
        val callId: String,
        val lifecycle: OpenAIHostedWebSearchRequestLifecycle,
        val cancel: (String) -> Unit,
    )

    private val requests = ConcurrentHashMap<String, OwnedRequest>()

    fun register(
        requestId: String,
        callId: String,
        lifecycle: OpenAIHostedWebSearchRequestLifecycle,
        cancel: (String) -> Unit,
    ) {
        val previous =
            requests.putIfAbsent(
                requestId,
                OwnedRequest(
                    callId = callId,
                    lifecycle = lifecycle,
                    cancel = cancel,
                ),
            )
        check(previous == null) { "OpenAI Web Search request ID is already active" }
    }

    fun requestCancellation(
        requestId: String,
        reason: String,
        owner: OpenAIHostedWebSearchCancellationOwner =
            OpenAIHostedWebSearchCancellationOwner.TOOLPKG,
    ): Boolean {
        val request = requests[requestId.trim()] ?: return false
        if (!request.lifecycle.requestCancellation(owner = owner, reason = reason)) {
            return false
        }
        request.cancel(reason)
        return true
    }

    fun settle(
        requestId: String,
        proposedOutcome: OpenAIHostedWebSearchTerminalOutcome,
    ): OpenAIHostedWebSearchRequestSettlement? {
        val normalizedRequestId = requestId.trim()
        val request = requests[normalizedRequestId] ?: return null
        val settlement = request.lifecycle.settle(proposedOutcome) ?: return null
        requests.remove(normalizedRequestId, request)
        return settlement
    }

    fun requestCancellationForCall(callId: String, reason: String): Int {
        val normalizedCallId = callId.trim()
        val owned =
            requests.entries
                .filter { entry -> entry.value.callId == normalizedCallId }
                .map { entry -> entry.key }
        return owned.count { requestId ->
            requestCancellation(
                requestId = requestId,
                reason = reason,
                owner = OpenAIHostedWebSearchCancellationOwner.EXECUTION_OWNER,
            )
        }
    }

    fun cancelAll(reason: String): Int {
        val requestIds = requests.keys.toList()
        return requestIds.count { requestId ->
            requestCancellation(
                requestId = requestId,
                reason = reason,
                owner = OpenAIHostedWebSearchCancellationOwner.BRIDGE,
            )
        }
    }

    fun lifecycle(requestId: String): OpenAIHostedWebSearchRequestLifecycle? =
        requests[requestId.trim()]?.lifecycle
}

private data class OpenAIHostedWebSearchOperationResult(
    val json: JSONObject,
    val outcome: OpenAIHostedWebSearchTerminalOutcome,
)

private fun OpenAIHostedWebSearchRequestSettlement.applyTo(
    result: OpenAIHostedWebSearchOperationResult,
    requestId: String,
    lifecycle: OpenAIHostedWebSearchRequestLifecycle,
): JSONObject =
    if (outcome == OpenAIHostedWebSearchTerminalOutcome.CANCELLED) {
        cancellationException().toJson(requestId)
    } else {
        result.json.apply {
            if (result.outcome == OpenAIHostedWebSearchTerminalOutcome.SUCCESS) {
                put(
                    "execution_diagnostics",
                    lifecycle.executionDiagnostics().toJson(),
                )
            }
        }
    }

private fun Throwable.openAIWebSearchTerminalOutcome(): OpenAIHostedWebSearchTerminalOutcome =
    if (
        this is CancellationException ||
            (
                this is OpenAIHostedWebSearchException &&
                    code == OpenAIHostedWebSearchErrorCode.REQUEST_CANCELLED
            )
    ) {
        OpenAIHostedWebSearchTerminalOutcome.CANCELLED
    } else {
        OpenAIHostedWebSearchTerminalOutcome.FAILURE
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
    private val openConfigurationAction: () -> Unit = {},
) {
    fun getStatus(
        callId: String,
        deliverResult: (String) -> Unit,
    ) {
        launchLocalOperation(callId, deliverResult) {
            buildStatusEnvelope(bindingResolver.inspect())
        }
    }

    fun validateLocalConfiguration(
        callId: String,
        deliverResult: (String) -> Unit,
    ) {
        launchLocalOperation(callId, deliverResult) {
            val inspection = bindingResolver.inspect()
            JSONObject()
                .put("success", true)
                .put("valid", inspection.localValid)
                .put("status", buildSanitizedStatus(inspection))
        }
    }

    fun openConfiguration(callId: String): String =
        try {
            OpenAIHostedWebSearchBridgePolicy.requireSettingsUiCaller(
                boundToolPkgContainerName = boundToolPkgContainerName,
                callId = callId,
                isExecutionCallActive = isExecutionCallActive,
                resolveExecutionRuntimeKind = resolveExecutionRuntimeKind,
            )
            openConfigurationAction()
            JSONObject()
                .put("success", true)
                .put("opened", true)
                .toString()
        } catch (error: Throwable) {
            error.toOpenAIWebSearchEnvelope(requestId = null).toString()
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
        val lifecycle = OpenAIHostedWebSearchRequestLifecycle(requestId)
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
                val operationResult =
                    try {
                        val resolved = bindingResolver.resolve(requireRelayProbe = true)
                        val effectiveRequest =
                            OpenAIHostedWebSearchPolicy.compileEffectiveRequest(
                                binding = resolved.binding,
                                request = request,
                            )
                        gateway.execute(
                            binding = resolved.binding,
                            request = effectiveRequest,
                            lifecycle = lifecycle,
                        ).result.toJson().let { json ->
                            OpenAIHostedWebSearchOperationResult(
                                json = json,
                                outcome = OpenAIHostedWebSearchTerminalOutcome.SUCCESS,
                            )
                        }
                    } catch (error: Throwable) {
                        OpenAIHostedWebSearchOperationResult(
                            json = error.toOpenAIWebSearchEnvelope(requestId),
                            outcome = error.openAIWebSearchTerminalOutcome(),
                        )
                    }
                val callbackEligible = isExecutionCallActive(normalizedCallId)
                if (callbackEligible) {
                    lifecycle.markPhase(OpenAIHostedWebSearchRequestPhase.DELIVERING_CALLBACK)
                }
                val settlement =
                    requestRegistry.settle(
                        requestId = requestId,
                        proposedOutcome = operationResult.outcome,
                    )
                if (
                    settlement != null &&
                        callbackEligible &&
                        isExecutionCallActive(normalizedCallId)
                ) {
                    deliverResult(
                        settlement
                            .applyTo(operationResult, requestId, lifecycle)
                            .toString()
                    )
                }
            }
        requestRegistry.register(
            requestId = requestId,
            callId = normalizedCallId,
            lifecycle = lifecycle,
            cancel = { reason ->
                gateway.cancelTransport(requestId)
                job.cancel(CancellationException(reason))
            },
        )
        if (!isExecutionCallActive(normalizedCallId)) {
            requestRegistry.requestCancellationForCall(
                callId = normalizedCallId,
                reason = "OpenAI Web Search execution call ended before request dispatch.",
            )
            requestRegistry.settle(
                requestId = requestId,
                proposedOutcome = OpenAIHostedWebSearchTerminalOutcome.CANCELLED,
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
        val lifecycle = OpenAIHostedWebSearchRequestLifecycle(requestId)
        lateinit var job: Job
        job =
            bridgeScope.launch(start = CoroutineStart.LAZY) {
                val operationResult =
                    try {
                        compatibilityProbe.run(requestId, lifecycle).toJson().let { json ->
                            OpenAIHostedWebSearchOperationResult(
                                json = json,
                                outcome = OpenAIHostedWebSearchTerminalOutcome.SUCCESS,
                            )
                        }
                    } catch (error: Throwable) {
                        OpenAIHostedWebSearchOperationResult(
                            json = error.toOpenAIWebSearchEnvelope(requestId),
                            outcome = error.openAIWebSearchTerminalOutcome(),
                        )
                    }
                val callbackEligible = isExecutionCallActive(normalizedCallId)
                if (callbackEligible) {
                    lifecycle.markPhase(OpenAIHostedWebSearchRequestPhase.DELIVERING_CALLBACK)
                }
                val settlement =
                    requestRegistry.settle(
                        requestId = requestId,
                        proposedOutcome = operationResult.outcome,
                    )
                if (
                    settlement != null &&
                        callbackEligible &&
                        isExecutionCallActive(normalizedCallId)
                ) {
                    deliverResult(
                        settlement
                            .applyTo(operationResult, requestId, lifecycle)
                            .toString()
                    )
                }
            }
        requestRegistry.register(
            requestId = requestId,
            callId = normalizedCallId,
            lifecycle = lifecycle,
            cancel = { reason ->
                compatibilityProbe.cancelTransport(requestId)
                job.cancel(CancellationException(reason))
            },
        )
        if (!isExecutionCallActive(normalizedCallId)) {
            requestRegistry.requestCancellationForCall(
                callId = normalizedCallId,
                reason = "OpenAI Web Search probe call ended before request dispatch.",
            )
            requestRegistry.settle(
                requestId = requestId,
                proposedOutcome = OpenAIHostedWebSearchTerminalOutcome.CANCELLED,
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
                throw openAIHostedWebSearchInvalidArgument(
                    field = "request_id",
                    reason = OpenAIHostedWebSearchArgumentReason.MISSING,
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
        inspection: OpenAIHostedWebSearchBindingInspection,
    ): JSONObject =
        JSONObject()
            .put("success", true)
            .put("status", buildSanitizedStatus(inspection))

    private fun buildSanitizedStatus(
        inspection: OpenAIHostedWebSearchBindingInspection,
    ): JSONObject {
        val searchSettings = inspection.searchSettings
        val admissionSettings = inspection.admissionSettings
        val compatibilityStatus = inspection.compatibilityStatus
        val relayProbeRequired =
            inspection.providerContract ==
                OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT
        return JSONObject()
            .put("toolpkg_id", OpenAIHostedWebSearchContract.TOOLPKG_ID)
            .put("tool_name", OpenAIHostedWebSearchContract.TOOL_NAME)
            .put("local_valid", inspection.localValid)
            .put(
                "provider_contract",
                inspection.providerContract?.name ?: JSONObject.NULL,
            )
            .put("endpoint_host", inspection.endpointHost ?: JSONObject.NULL)
            .put("model", inspection.modelName ?: JSONObject.NULL)
            .put("mode", searchSettings?.mode?.wireValue ?: JSONObject.NULL)
            .put(
                "reasoning_effort",
                searchSettings?.reasoningEffort?.name?.lowercase() ?: JSONObject.NULL,
            )
            .put("context_size", searchSettings?.contextSize?.wireValue ?: JSONObject.NULL)
            .put(
                "return_token_budget",
                searchSettings?.returnTokenBudget?.wireValue ?: JSONObject.NULL,
            )
            .put("max_output_tokens", searchSettings?.maxOutputTokens ?: JSONObject.NULL)
            .put(
                "queue_timeout_seconds",
                admissionSettings?.queueTimeoutSeconds ?: JSONObject.NULL,
            )
            .put(
                "timeout_seconds",
                admissionSettings?.timeoutSeconds ?: JSONObject.NULL,
            )
            .put(
                "max_concurrent_requests",
                admissionSettings?.maxConcurrentRequests ?: JSONObject.NULL,
            )
            .put(
                "requests_per_minute",
                admissionSettings?.requestsPerMinute ?: JSONObject.NULL,
            )
            .put(
                "header_names",
                JSONArray(
                    (
                        listOfNotNull(inspection.authHeaderName) +
                            inspection.extraHeaderNames
                    )
                        .distinctBy { headerName -> headerName.lowercase() }
                        .sortedWith(String.CASE_INSENSITIVE_ORDER)
                ),
            )
            .put("api_key_configured", inspection.apiKeyConfigured)
            .put("api_key_revision", inspection.apiKeyRevision ?: JSONObject.NULL)
            .put(
                "auth_scheme_present",
                inspection.authScheme?.isNotBlank() ?: JSONObject.NULL,
            )
            .put(
                "auth_scheme_kind",
                inspection.authScheme?.let(::authSchemeKind) ?: JSONObject.NULL,
            )
            .put("chat_provider_independent", true)
            .put(
                "readiness",
                JSONObject().apply {
                    inspection.readiness.forEach { (name, readinessCheck) ->
                        put(
                            name,
                            JSONObject()
                                .put("state", readinessCheck.state)
                                .put(
                                    "error_code",
                                    readinessCheck.errorCode ?: JSONObject.NULL,
                                )
                                .put(
                                    "message",
                                    readinessCheck.message ?: JSONObject.NULL,
                                ),
                        )
                    }
                },
            )
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

    private fun authSchemeKind(authScheme: String): String =
        when {
            authScheme.isBlank() -> "direct"
            authScheme.equals("Bearer", ignoreCase = true) -> "bearer"
            else -> "custom"
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
