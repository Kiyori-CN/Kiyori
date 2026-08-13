package com.ai.assistance.operit.api.chat.llmprovider

import java.net.URI

internal data class OpenAIHostedWebSearchReadinessCheck(
    val state: String,
    val errorCode: String? = null,
    val message: String? = null,
)

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

internal data class OpenAIHostedWebSearchBindingInspection(
    val localValid: Boolean,
    val providerContract: OpenAIHostedWebSearchProviderContract?,
    val endpointHost: String?,
    val modelName: String?,
    val apiKeyConfigured: Boolean,
    val apiKeyRevision: String?,
    val authHeaderName: String?,
    val authScheme: String?,
    val extraHeaderNames: List<String>,
    val searchSettings: OpenAIHostedWebSearchSearchSettings?,
    val admissionSettings: OpenAIHostedWebSearchAdmissionSettings?,
    val resolvedBinding: OpenAIHostedWebSearchResolvedBinding?,
    val compatibilityStatus: OpenAIHostedWebSearchCompatibilityStatus,
    val readiness: Map<String, OpenAIHostedWebSearchReadinessCheck>,
)

internal object OpenAIHostedWebSearchReadinessEvaluator {
    fun evaluate(
        environment: Map<String, String>,
        recordSet: OpenAIHostedWebSearchCompatibilityRecordSet,
    ): OpenAIHostedWebSearchBindingInspection {
        var providerContract: OpenAIHostedWebSearchProviderContract? = null
        val providerCheck =
            check(
                readyState = {
                    when (providerContract) {
                        OpenAIHostedWebSearchProviderContract.RESPONSES_HOSTED_OFFICIAL ->
                            "official"

                        OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT -> "relay"
                        null -> error("Provider contract was not assigned")
                    }
                },
                invalidMessage = "Provider contract is invalid.",
            ) {
                providerContract =
                    OpenAIHostedWebSearchBindingCompiler.providerContract(environment)
            }

        val endpointRaw =
            environment[OpenAIHostedWebSearchContract.ENV_RESPONSES_ENDPOINT]
                ?.trim()
                .orEmpty()
        var endpointHost: String? = null
        val endpointCheck =
            if (endpointRaw.isEmpty()) {
                missing(
                    code = OpenAIHostedWebSearchErrorCode.PACKAGE_ENV_MISSING,
                    message = "Responses endpoint is missing.",
                )
            } else {
                check(
                    invalidMessage = "Responses endpoint is invalid.",
                ) {
                    val contract = providerContract
                    if (contract == null) {
                        OpenAIHostedWebSearchPolicy.validateResponsesEndpoint(endpointRaw)
                    } else {
                        OpenAIHostedWebSearchPolicy.validateEndpoint(contract, endpointRaw)
                    }
                    endpointHost = URI(endpointRaw).host
                }
            }

        val modelName = OpenAIHostedWebSearchBindingCompiler.modelName(environment)
        val modelCheck =
            check(
                invalidState = "not_allowed",
                invalidMessage = "Search model is not allowed by the selected provider contract.",
            ) {
                val contract = providerContract
                if (contract == null) {
                    if (modelName.isBlank()) {
                        throw OpenAIHostedWebSearchException(
                            code = OpenAIHostedWebSearchErrorCode.MODEL_NOT_ALLOWED,
                            message = "OpenAI Web Search model must not be blank.",
                        )
                    }
                } else {
                    OpenAIHostedWebSearchPolicy.validateModel(contract, modelName)
                }
            }

        val apiKeyRaw =
            environment[OpenAIHostedWebSearchContract.ENV_API_KEY]
                ?.trim()
                .orEmpty()
        val credentialCheck =
            if (apiKeyRaw.isEmpty()) {
                missing(
                    code = OpenAIHostedWebSearchErrorCode.API_KEY_MISSING,
                    message = "Search credential is missing.",
                )
            } else {
                check(
                    invalidMessage = "Search credential is invalid.",
                ) {
                    OpenAIHostedWebSearchPolicy.validateCredential(apiKeyRaw)
                }
            }

        var authHeaderName: String? = null
        var authScheme: String? = null
        val authCheck =
            check(
                invalidMessage = "Authentication configuration is invalid.",
            ) {
                authHeaderName = OpenAIHostedWebSearchBindingCompiler.authHeaderName(environment)
                authScheme = OpenAIHostedWebSearchBindingCompiler.authScheme(environment)
                OpenAIHostedWebSearchPolicy.validateAuthConfiguration(
                    headerName = requireNotNull(authHeaderName),
                    authScheme = requireNotNull(authScheme),
                )
            }

        var extraHeaderNames = emptyList<String>()
        val extraHeadersCheck =
            check(
                invalidMessage = "Additional header configuration is invalid.",
            ) {
                val headers = OpenAIHostedWebSearchBindingCompiler.extraHeaders(environment)
                OpenAIHostedWebSearchPolicy.validateExtraHeaders(
                    headers = headers,
                    authHeaderName =
                        authHeaderName
                            ?: OpenAIHostedWebSearchBindingCompiler.authHeaderName(environment),
                )
                extraHeaderNames = headers.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
            }

        var searchSettings: OpenAIHostedWebSearchSearchSettings? = null
        val searchOptionsCheck =
            check(
                invalidMessage = "Search option configuration is invalid.",
            ) {
                searchSettings =
                    OpenAIHostedWebSearchBindingCompiler
                        .compileSearchSettings(environment)
                        .also(OpenAIHostedWebSearchPolicy::validateSearchOptions)
            }

        var admissionSettings: OpenAIHostedWebSearchAdmissionSettings? = null
        val admissionCheck =
            check(
                invalidMessage = "Admission or timeout configuration is invalid.",
            ) {
                admissionSettings =
                    OpenAIHostedWebSearchBindingCompiler
                        .compileAdmissionSettings(environment)
                        .also(OpenAIHostedWebSearchPolicy::validateAdmission)
            }

        val localChecks =
            linkedMapOf(
                "provider_contract" to providerCheck,
                "endpoint" to endpointCheck,
                "model" to modelCheck,
                "credential" to credentialCheck,
                "auth" to authCheck,
                "extra_headers" to extraHeadersCheck,
                "search_options" to searchOptionsCheck,
                "admission" to admissionCheck,
            )
        val localValid =
            localChecks.values.all { readinessCheck ->
                readinessCheck.state in
                    setOf("official", "relay", "ready")
            }

        val resolvedBinding =
            if (localValid) {
                val binding =
                    OpenAIHostedWebSearchBindingCompiler.compilePackageEnvironment(environment)
                val fingerprintDigest = binding.compatibilityFingerprint().digest
                val compatibilityRecord = recordSet.successFor(fingerprintDigest)
                val compatibilityFailureRecord = recordSet.failureFor(fingerprintDigest)
                OpenAIHostedWebSearchPolicy.validateBinding(
                    binding = binding,
                    compatibilityRecord = compatibilityRecord,
                    requireRelayProbe = false,
                )
                OpenAIHostedWebSearchResolvedBinding(
                    binding = binding,
                    compatibilityRecord = compatibilityRecord,
                    compatibilityFailureRecord = compatibilityFailureRecord,
                    hasOtherCompatibilityRecords =
                        recordSet.hasAnyRecord() &&
                            compatibilityRecord == null &&
                            compatibilityFailureRecord == null,
                )
            } else {
                null
            }

        val compatibilityStatus =
            resolvedBinding?.let { resolved ->
                OpenAIHostedWebSearchCompatibilityStatusResolver.resolve(
                    binding = resolved.binding,
                    compatibilityRecord = resolved.compatibilityRecord,
                    compatibilityFailureRecord = resolved.compatibilityFailureRecord,
                    hasOtherCompatibilityRecords = resolved.hasOtherCompatibilityRecords,
                )
            } ?: OpenAIHostedWebSearchCompatibilityStatus(
                state =
                    if (
                        providerContract ==
                            OpenAIHostedWebSearchProviderContract.RESPONSES_HOSTED_OFFICIAL
                    ) {
                        "not_required"
                    } else {
                        "missing"
                    },
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

        return OpenAIHostedWebSearchBindingInspection(
            localValid = localValid,
            providerContract = providerContract,
            endpointHost = endpointHost,
            modelName = modelName.takeIf(String::isNotBlank),
            apiKeyConfigured = credentialCheck.state == "ready",
            apiKeyRevision =
                apiKeyRaw
                    .takeIf { credentialCheck.state == "ready" }
                    ?.let(::openAIHostedWebSearchCredentialRevision),
            authHeaderName = authHeaderName,
            authScheme = authScheme,
            extraHeaderNames = extraHeaderNames,
            searchSettings = searchSettings,
            admissionSettings = admissionSettings,
            resolvedBinding = resolvedBinding,
            compatibilityStatus = compatibilityStatus,
            readiness =
                localChecks +
                    (
                        "compatibility" to
                            OpenAIHostedWebSearchReadinessCheck(
                                state = compatibilityStatus.state,
                            )
                    ),
        )
    }

    private fun check(
        readyState: () -> String = { "ready" },
        invalidState: String = "invalid",
        invalidMessage: String,
        block: () -> Unit,
    ): OpenAIHostedWebSearchReadinessCheck =
        try {
            block()
            OpenAIHostedWebSearchReadinessCheck(state = readyState())
        } catch (error: OpenAIHostedWebSearchException) {
            OpenAIHostedWebSearchReadinessCheck(
                state = invalidState,
                errorCode = error.code.name,
                message = invalidMessage,
            )
        }

    private fun missing(
        code: OpenAIHostedWebSearchErrorCode,
        message: String,
    ): OpenAIHostedWebSearchReadinessCheck =
        OpenAIHostedWebSearchReadinessCheck(
            state = "missing",
            errorCode = code.name,
            message = message,
        )
}
