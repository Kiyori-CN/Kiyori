package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import java.util.UUID

internal class OpenAIHostedWebSearchCompatibilityProbe(
    context: Context,
    private val bindingResolver: OpenAIHostedWebSearchBindingResolver =
        OpenAIHostedWebSearchBindingResolver(context.applicationContext),
    private val gateway: OpenAIHostedWebSearchGateway = OpenAIHostedWebSearchGateway(),
    private val repository: OpenAIHostedWebSearchCompatibilityRepository =
        OpenAIHostedWebSearchCompatibilityRepository.getInstance(context.applicationContext),
) {
    suspend fun run(
        requestId: String = "ows_probe_${UUID.randomUUID().toString().replace("-", "")}",
    ): OpenAIHostedWebSearchResult {
        val resolved =
            bindingResolver.resolve(
                requireRelayProbe = false,
                advanceModelConfigKey = true,
            )
        val binding = resolved.binding
        if (
            binding.providerContract !=
                OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT
        ) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "Compatibility probing is only required for relay strict mode.",
            )
        }
        val request =
            OpenAIHostedWebSearchEffectiveRequest(
                requestId = requestId,
                query =
                    "Find the official OpenAI Web Search guide at developers.openai.com and " +
                        "answer with the exact guide title in one concise cited sentence.",
                contextSize = binding.contextSize,
                allowedDomains = listOf("developers.openai.com"),
                blockedDomains = emptyList(),
                location = null,
            )
        val testedAtEpochMillis = System.currentTimeMillis()
        val execution =
            try {
                gateway.execute(binding, request)
            } catch (error: OpenAIHostedWebSearchException) {
                val rewritten =
                    OpenAIHostedWebSearchCompatibilityProbePolicy.rewriteEvidenceFailure(error)
                repository.writeFailure(
                    OpenAIHostedWebSearchCompatibilityFailureRecord(
                        fingerprintDigest = binding.compatibilityFingerprint().digest,
                        testedAtEpochMillis = testedAtEpochMillis,
                        errorCode = rewritten.code,
                        httpStatus = rewritten.httpStatus,
                        sanitizedMessage =
                            OpenAIHostedWebSearchCompatibilityProbePolicy
                                .sanitizeFailureMessage(rewritten.message),
                        providerErrorType = rewritten.providerErrorType,
                        providerErrorCode = rewritten.providerErrorCode,
                        providerRequestId = rewritten.providerRequestId,
                        schemaRevision =
                            OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION,
                    )
                )
                throw rewritten
            }
        try {
            OpenAIHostedWebSearchCompatibilityProbePolicy.requireCompatible(execution)
        } catch (error: OpenAIHostedWebSearchException) {
            repository.writeFailure(
                OpenAIHostedWebSearchCompatibilityFailureRecord(
                    fingerprintDigest = binding.compatibilityFingerprint().digest,
                    testedAtEpochMillis = testedAtEpochMillis,
                    errorCode = error.code,
                    httpStatus = error.httpStatus,
                    sanitizedMessage =
                        OpenAIHostedWebSearchCompatibilityProbePolicy
                            .sanitizeFailureMessage(error.message),
                    providerErrorType = error.providerErrorType,
                    providerErrorCode = error.providerErrorCode,
                    providerRequestId = error.providerRequestId,
                    schemaRevision = OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION,
                )
            )
            throw error
        }
        repository.write(
            OpenAIHostedWebSearchCompatibilityRecord(
                fingerprintDigest = binding.compatibilityFingerprint().digest,
                testedAtEpochMillis = testedAtEpochMillis,
                responseId = execution.result.responseId,
                evidenceMode = execution.result.evidenceMode,
                schemaRevision = OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION,
            )
        )
        return execution.result
    }

    fun cancel(requestId: String): Boolean = gateway.cancel(requestId)
}

internal object OpenAIHostedWebSearchCompatibilityProbePolicy {
    private val EVIDENCE_FAILURE_CODES =
        setOf(
            OpenAIHostedWebSearchErrorCode.RESPONSE_SCHEMA_INVALID,
            OpenAIHostedWebSearchErrorCode.SEARCH_TOOL_NOT_CALLED,
            OpenAIHostedWebSearchErrorCode.SEARCH_OUTPUT_EMPTY,
            OpenAIHostedWebSearchErrorCode.CITATION_INVALID,
            OpenAIHostedWebSearchErrorCode.SOURCE_INVALID,
            OpenAIHostedWebSearchErrorCode.RELAY_RESPONSE_TEXT_ONLY,
        )

    fun rewriteEvidenceFailure(
        error: OpenAIHostedWebSearchException,
    ): OpenAIHostedWebSearchException {
        if (error.code !in EVIDENCE_FAILURE_CODES) {
            return error
        }
        return OpenAIHostedWebSearchException(
            code = OpenAIHostedWebSearchErrorCode.RELAY_INCOMPATIBLE,
            message =
                "The relay returned an incompatible OpenAI Responses Web Search evidence " +
                    "schema. [${error.code.name}] ${error.message}",
            cause = error,
        )
    }

    fun requireCompatible(execution: OpenAIHostedWebSearchExecution) {
        val diagnostics = execution.diagnostics
        if (diagnostics.urlCitationCount <= 0) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.RELAY_INCOMPATIBLE,
                message =
                    "The relay compatibility probe did not return a URL citation for the " +
                        "fixed public web-page target " +
                        "(action_sources_total=${diagnostics.totalActionSourceCount}, " +
                        "valid_action_url=${diagnostics.validActionSourceUrlCount}, " +
                        "structured_feed=${diagnostics.structuredFeedSourceCount}, " +
                        "invalid=${diagnostics.invalidActionSourceCount}).",
            )
        }
    }

    fun sanitizeFailureMessage(message: String): String =
        message
            .replace(Regex("""[\u0000-\u001F\u007F]+"""), " ")
            .trim()
            .take(MAX_FAILURE_MESSAGE_CHARACTERS)

    private const val MAX_FAILURE_MESSAGE_CHARACTERS = 512
}
