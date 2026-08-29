package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.data.preferences.ToolPkgHostEnvironmentRepository

internal data class OpenAIHostedWebSearchResolvedBinding(
    val binding: OpenAIHostedWebSearchBinding,
    val compatibilityRecord: OpenAIHostedWebSearchCompatibilityRecord?,
    val compatibilityFailureRecord: OpenAIHostedWebSearchCompatibilityFailureRecord?,
    val hasOtherCompatibilityRecords: Boolean,
)

internal data class OpenAIHostedWebSearchSearchSettings(
    val reasoningEffort: OpenAIHostedWebSearchReasoningEffort,
    val maxOutputTokens: Int?,
    val returnTokenBudget: OpenAIHostedWebSearchReturnTokenBudget,
    val additionalInstructions: String,
    val mode: OpenAIHostedWebSearchMode,
    val contextSize: OpenAIHostedWebSearchContextSize,
    val allowedDomains: List<String>,
    val blockedDomains: List<String>,
    val location: OpenAIHostedWebSearchApproximateLocation?,
)

internal data class OpenAIHostedWebSearchAdmissionSettings(
    val queueTimeoutSeconds: Int,
    val timeoutSeconds: Int,
    val maxConcurrentRequests: Int,
    val requestsPerMinute: Int,
)

internal object OpenAIHostedWebSearchBindingCompiler {
    private const val DEFAULT_PROVIDER_CONTRACT = "RESPONSES_HOSTED_OFFICIAL"
    private const val DEFAULT_MODEL = "gpt-5.6-luna"
    private const val DEFAULT_AUTH_HEADER_NAME = "Authorization"
    private const val DEFAULT_AUTH_SCHEME = "Bearer"
    private const val DEFAULT_REASONING_EFFORT = "medium"
    private const val DEFAULT_RETURN_TOKEN_BUDGET = "default"
    private const val DEFAULT_EXTERNAL_WEB_ACCESS = "true"
    private const val DEFAULT_CONTEXT_SIZE = "medium"
    private const val DEFAULT_QUEUE_TIMEOUT_SECONDS = "60"
    private const val DEFAULT_TIMEOUT_SECONDS = "300"
    private const val DEFAULT_MAX_CONCURRENT_REQUESTS = "1"
    private const val DEFAULT_REQUESTS_PER_MINUTE = "0"

    fun compilePackageEnvironment(
        environment: Map<String, String>,
    ): OpenAIHostedWebSearchBinding {
        val searchSettings = compileSearchSettings(environment)
        val admissionSettings = compileAdmissionSettings(environment)
        return OpenAIHostedWebSearchBinding(
            toolPkgId = OpenAIHostedWebSearchContract.TOOLPKG_ID,
            providerContract = providerContract(environment),
            endpoint = endpoint(environment),
            modelName = modelName(environment),
            apiKey = apiKey(environment),
            authHeaderName = authHeaderName(environment),
            authScheme = authScheme(environment),
            extraHeaders = extraHeaders(environment),
            reasoningEffort = searchSettings.reasoningEffort,
            maxOutputTokens = searchSettings.maxOutputTokens,
            returnTokenBudget = searchSettings.returnTokenBudget,
            additionalInstructions = searchSettings.additionalInstructions,
            mode = searchSettings.mode,
            contextSize = searchSettings.contextSize,
            allowedDomains = searchSettings.allowedDomains,
            blockedDomains = searchSettings.blockedDomains,
            location = searchSettings.location,
            queueTimeoutSeconds = admissionSettings.queueTimeoutSeconds,
            timeoutSeconds = admissionSettings.timeoutSeconds,
            maxConcurrentRequests = admissionSettings.maxConcurrentRequests,
            requestsPerMinute = admissionSettings.requestsPerMinute,
        )
    }

    fun providerContract(
        environment: Map<String, String>,
    ): OpenAIHostedWebSearchProviderContract =
        OpenAIHostedWebSearchProviderContract.parse(
            environment.valueOrDefault(
                OpenAIHostedWebSearchContract.ENV_PROVIDER_CONTRACT,
                DEFAULT_PROVIDER_CONTRACT,
            )
        )

    fun endpoint(environment: Map<String, String>): String =
        OpenAIHostedWebSearchPolicy.normalizeResponsesEndpoint(
            environment.requiredValue(
                OpenAIHostedWebSearchContract.ENV_RESPONSES_ENDPOINT,
                OpenAIHostedWebSearchErrorCode.PACKAGE_ENV_MISSING,
            )
        )

    fun modelName(environment: Map<String, String>): String =
        environment.valueOrDefault(
            OpenAIHostedWebSearchContract.ENV_MODEL,
            DEFAULT_MODEL,
        ).trim()

    fun apiKey(environment: Map<String, String>): String =
        environment.requiredValue(
            OpenAIHostedWebSearchContract.ENV_API_KEY,
            OpenAIHostedWebSearchErrorCode.API_KEY_MISSING,
        )

    fun authHeaderName(environment: Map<String, String>): String =
        environment.valueOrDefault(
            OpenAIHostedWebSearchContract.ENV_AUTH_HEADER_NAME,
            DEFAULT_AUTH_HEADER_NAME,
        ).trim()

    fun authScheme(environment: Map<String, String>): String =
        environment.valueOrDefault(
            OpenAIHostedWebSearchContract.ENV_AUTH_SCHEME,
            DEFAULT_AUTH_SCHEME,
        ).trim()

    fun extraHeaders(environment: Map<String, String>): Map<String, String> =
        OpenAIHostedWebSearchPolicy.parseHeadersJson(
            environment[OpenAIHostedWebSearchContract.ENV_EXTRA_HEADERS_JSON].orEmpty()
        )

    fun compileSearchSettings(
        environment: Map<String, String>,
    ): OpenAIHostedWebSearchSearchSettings =
        OpenAIHostedWebSearchSearchSettings(
            reasoningEffort =
                OpenAIHostedWebSearchReasoningEffort.parse(
                    environment.valueOrDefault(
                        OpenAIHostedWebSearchContract.ENV_REASONING_EFFORT,
                        DEFAULT_REASONING_EFFORT,
                    )
                ),
            maxOutputTokens =
                environment.optionalPositiveInt(
                    OpenAIHostedWebSearchContract.ENV_MAX_OUTPUT_TOKENS
                ),
            returnTokenBudget =
                OpenAIHostedWebSearchReturnTokenBudget.parse(
                    environment.valueOrDefault(
                        OpenAIHostedWebSearchContract.ENV_RETURN_TOKEN_BUDGET,
                        DEFAULT_RETURN_TOKEN_BUDGET,
                    )
                ),
            additionalInstructions =
                environment[OpenAIHostedWebSearchContract.ENV_ADDITIONAL_INSTRUCTIONS]
                    ?.trim()
                    .orEmpty(),
            mode =
                OpenAIHostedWebSearchMode.fromExternalWebAccess(
                    environment.strictBoolean(
                        OpenAIHostedWebSearchContract.ENV_EXTERNAL_WEB_ACCESS,
                        DEFAULT_EXTERNAL_WEB_ACCESS,
                    )
                ),
            contextSize =
                OpenAIHostedWebSearchContextSize.parse(
                    environment.valueOrDefault(
                        OpenAIHostedWebSearchContract.ENV_CONTEXT_SIZE,
                        DEFAULT_CONTEXT_SIZE,
                    )
                ),
            allowedDomains =
                OpenAIHostedWebSearchPolicy.parseDomainArrayJson(
                    environment[OpenAIHostedWebSearchContract.ENV_ALLOWED_DOMAINS_JSON].orEmpty()
                ),
            blockedDomains =
                OpenAIHostedWebSearchPolicy.parseDomainArrayJson(
                    environment[OpenAIHostedWebSearchContract.ENV_BLOCKED_DOMAINS_JSON].orEmpty()
                ),
            location =
                OpenAIHostedWebSearchPolicy.parseLocationJson(
                    environment[OpenAIHostedWebSearchContract.ENV_LOCATION_JSON].orEmpty()
                ),
        )

    fun compileAdmissionSettings(
        environment: Map<String, String>,
    ): OpenAIHostedWebSearchAdmissionSettings =
        OpenAIHostedWebSearchAdmissionSettings(
            queueTimeoutSeconds =
                environment.strictInt(
                    OpenAIHostedWebSearchContract.ENV_QUEUE_TIMEOUT_SECONDS,
                    DEFAULT_QUEUE_TIMEOUT_SECONDS,
                ),
            timeoutSeconds =
                environment.strictInt(
                    OpenAIHostedWebSearchContract.ENV_TIMEOUT_SECONDS,
                    DEFAULT_TIMEOUT_SECONDS,
                ),
            maxConcurrentRequests =
                environment.strictInt(
                    OpenAIHostedWebSearchContract.ENV_MAX_CONCURRENT_REQUESTS,
                    DEFAULT_MAX_CONCURRENT_REQUESTS,
                ),
            requestsPerMinute =
                environment.strictInt(
                    OpenAIHostedWebSearchContract.ENV_REQUESTS_PER_MINUTE,
                    DEFAULT_REQUESTS_PER_MINUTE,
                ),
        )

    private fun Map<String, String>.requiredValue(
        name: String,
        code: OpenAIHostedWebSearchErrorCode,
    ): String =
        this[name]?.trim()?.takeIf(String::isNotEmpty)
            ?: throw OpenAIHostedWebSearchException(
                code = code,
                message = "OpenAI Web Search configuration is missing $name.",
            )

    private fun Map<String, String>.valueOrDefault(name: String, defaultValue: String): String =
        this[name]?.trim()?.takeIf(String::isNotEmpty) ?: defaultValue

    private fun Map<String, String>.strictBoolean(name: String, defaultValue: String): Boolean =
        when (val value = valueOrDefault(name, defaultValue).lowercase()) {
            "true" -> true
            "false" -> false
            else ->
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "$name must be true or false.",
                )
        }

    private fun Map<String, String>.strictInt(name: String, defaultValue: String): Int =
        valueOrDefault(name, defaultValue).toIntOrNull()
            ?: throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "$name must be an integer.",
            )

    private fun Map<String, String>.optionalPositiveInt(name: String): Int? {
        val rawValue = this[name]?.trim().orEmpty()
        if (rawValue.isEmpty()) {
            return null
        }
        val value =
            rawValue.toIntOrNull()
                ?: throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "$name must be an integer.",
                )
        if (value <= 0) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "$name must be positive.",
            )
        }
        return value
    }
}

internal class OpenAIHostedWebSearchBindingResolver(
    context: Context,
) {
    private val environmentRepository =
        ToolPkgHostEnvironmentRepository.getInstance(context.applicationContext)
    private val compatibilityRepository =
        OpenAIHostedWebSearchCompatibilityRepository.getInstance(context.applicationContext)

    suspend fun inspect(): OpenAIHostedWebSearchBindingInspection =
        OpenAIHostedWebSearchReadinessEvaluator.evaluate(
            environment = readEnvironment(),
            recordSet = compatibilityRepository.readSet(),
        )

    suspend fun resolve(
        requireRelayProbe: Boolean,
    ): OpenAIHostedWebSearchResolvedBinding {
        val environment = readEnvironment()
        val binding = OpenAIHostedWebSearchBindingCompiler.compilePackageEnvironment(environment)
        val recordSet = compatibilityRepository.readSet()
        val fingerprintDigest = binding.compatibilityFingerprint().digest
        val compatibilityRecord = recordSet.successFor(fingerprintDigest)
        val compatibilityFailureRecord = recordSet.failureFor(fingerprintDigest)
        OpenAIHostedWebSearchPolicy.validateBinding(
            binding = binding,
            compatibilityRecord = compatibilityRecord,
            requireRelayProbe = requireRelayProbe,
        )
        return OpenAIHostedWebSearchResolvedBinding(
            binding = binding,
            compatibilityRecord = compatibilityRecord,
            compatibilityFailureRecord = compatibilityFailureRecord,
            hasOtherCompatibilityRecords =
                recordSet.hasAnyRecord() &&
                    compatibilityRecord == null &&
                    compatibilityFailureRecord == null,
        )
    }

    private fun readEnvironment(): Map<String, String> =
        OpenAIHostedWebSearchContract.ENVIRONMENT_NAMES.associateWith { name ->
            environmentRepository
                .getValue(OpenAIHostedWebSearchContract.TOOLPKG_ID, name)
                .orEmpty()
        }
}
