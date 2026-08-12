package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.data.model.ApiKeyAvailabilityStatus
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.parseModelNameInput
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.ToolPkgHostEnvironmentRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class OpenAIHostedWebSearchResolvedBinding(
    val binding: OpenAIHostedWebSearchBinding,
    val compatibilityRecord: OpenAIHostedWebSearchCompatibilityRecord?,
    val compatibilityFailureRecord: OpenAIHostedWebSearchCompatibilityFailureRecord?,
    val hasOtherCompatibilityRecords: Boolean,
)

internal object OpenAIHostedWebSearchBindingCompiler {
    private const val DEFAULT_CONFIG_SOURCE = "PACKAGE_ENV"
    private const val DEFAULT_PROVIDER_CONTRACT = "RESPONSES_HOSTED_OFFICIAL"
    private const val DEFAULT_MODEL = "gpt-5.6-luna"
    private const val DEFAULT_AUTH_HEADER_NAME = "Authorization"
    private const val DEFAULT_AUTH_SCHEME = "Bearer"
    private const val DEFAULT_REASONING_EFFORT = "medium"
    private const val DEFAULT_RETURN_TOKEN_BUDGET = "default"
    private const val DEFAULT_EXTERNAL_WEB_ACCESS = "true"
    private const val DEFAULT_CONTEXT_SIZE = "medium"
    private const val DEFAULT_TIMEOUT_SECONDS = "60"
    private const val DEFAULT_MAX_CONCURRENT_REQUESTS = "1"
    private const val DEFAULT_REQUESTS_PER_MINUTE = "0"

    fun configSource(environment: Map<String, String>): OpenAIHostedWebSearchConfigSource =
        OpenAIHostedWebSearchConfigSource.parse(
            environment.valueOrDefault(
                OpenAIHostedWebSearchContract.ENV_CONFIG_SOURCE,
                DEFAULT_CONFIG_SOURCE,
            )
        )

    fun compilePackageEnvironment(
        environment: Map<String, String>,
    ): OpenAIHostedWebSearchBinding {
        val source = configSource(environment)
        if (source != OpenAIHostedWebSearchConfigSource.PACKAGE_ENV) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "OpenAI Web Search package environment is not the selected source.",
            )
        }
        return compile(
            environment = environment,
            configSource = source,
            modelConfigId = null,
            endpoint =
                environment.requiredValue(
                    OpenAIHostedWebSearchContract.ENV_RESPONSES_ENDPOINT,
                    OpenAIHostedWebSearchErrorCode.PACKAGE_ENV_MISSING,
                ),
            apiKey =
                environment.requiredValue(
                    OpenAIHostedWebSearchContract.ENV_API_KEY,
                    OpenAIHostedWebSearchErrorCode.API_KEY_MISSING,
                ),
            authHeaderName =
                environment.valueOrDefault(
                    OpenAIHostedWebSearchContract.ENV_AUTH_HEADER_NAME,
                    DEFAULT_AUTH_HEADER_NAME,
                ),
            authScheme =
                environment.valueOrDefault(
                    OpenAIHostedWebSearchContract.ENV_AUTH_SCHEME,
                    DEFAULT_AUTH_SCHEME,
                ),
            extraHeaders =
                OpenAIHostedWebSearchPolicy.parseHeadersJson(
                    environment[OpenAIHostedWebSearchContract.ENV_EXTRA_HEADERS_JSON].orEmpty()
                ),
            modelConfigMaxConcurrentRequests = 0,
            modelConfigRequestsPerMinute = 0,
        )
    }

    fun compileModelConfig(
        environment: Map<String, String>,
        modelConfig: ModelConfigData,
        selectedApiKey: String,
    ): OpenAIHostedWebSearchBinding {
        val source = configSource(environment)
        if (source != OpenAIHostedWebSearchConfigSource.MODEL_CONFIG) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "OpenAI Web Search model config is not the selected source.",
            )
        }
        val providerContract =
            OpenAIHostedWebSearchProviderContract.parse(
                environment.valueOrDefault(
                    OpenAIHostedWebSearchContract.ENV_PROVIDER_CONTRACT,
                    DEFAULT_PROVIDER_CONTRACT,
                )
            )
        val providerType =
            ApiProviderType.fromProviderTypeId(modelConfig.apiProviderTypeId)
                ?: modelConfig.apiProviderType
        when (providerContract) {
            OpenAIHostedWebSearchProviderContract.RESPONSES_HOSTED_OFFICIAL -> {
                if (providerType != ApiProviderType.OPENAI_RESPONSES) {
                    throw OpenAIHostedWebSearchException(
                        code = OpenAIHostedWebSearchErrorCode.PROVIDER_NOT_RESPONSES,
                        message =
                            "Official OpenAI Web Search requires an OPENAI_RESPONSES model config.",
                    )
                }
            }

            OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT -> {
                if (
                    providerType != ApiProviderType.OPENAI_RESPONSES &&
                        providerType != ApiProviderType.OPENAI_RESPONSES_GENERIC
                ) {
                    throw OpenAIHostedWebSearchException(
                        code = OpenAIHostedWebSearchErrorCode.PROVIDER_NOT_RESPONSES,
                        message =
                            "Relay Web Search requires an OPENAI_RESPONSES or " +
                                "OPENAI_RESPONSES_GENERIC model config.",
                    )
                }
            }
        }

        val modelName =
            environment.valueOrDefault(
                OpenAIHostedWebSearchContract.ENV_MODEL,
                DEFAULT_MODEL,
            )
        if (modelName !in parseModelNameInput(modelConfig.modelName)) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.MODEL_NOT_IN_CONFIG,
                message =
                    "The fixed OpenAI Web Search model is not present in the selected model config.",
            )
        }

        return compile(
            environment = environment,
            configSource = source,
            providerContract = providerContract,
            modelConfigId = modelConfig.id,
            endpoint = EndpointCompleter.completeEndpoint(modelConfig.apiEndpoint, providerType),
            modelName = modelName,
            apiKey = selectedApiKey,
            authHeaderName = DEFAULT_AUTH_HEADER_NAME,
            authScheme = DEFAULT_AUTH_SCHEME,
            extraHeaders =
                OpenAIHostedWebSearchPolicy.parseHeadersJson(modelConfig.customHeaders),
            modelConfigMaxConcurrentRequests = modelConfig.maxConcurrentRequests,
            modelConfigRequestsPerMinute = modelConfig.requestLimitPerMinute,
        )
    }

    private fun compile(
        environment: Map<String, String>,
        configSource: OpenAIHostedWebSearchConfigSource,
        modelConfigId: String?,
        endpoint: String,
        apiKey: String,
        authHeaderName: String,
        authScheme: String,
        extraHeaders: Map<String, String>,
        modelConfigMaxConcurrentRequests: Int,
        modelConfigRequestsPerMinute: Int,
        providerContract: OpenAIHostedWebSearchProviderContract =
            OpenAIHostedWebSearchProviderContract.parse(
                environment.valueOrDefault(
                    OpenAIHostedWebSearchContract.ENV_PROVIDER_CONTRACT,
                    DEFAULT_PROVIDER_CONTRACT,
                )
            ),
        modelName: String =
            environment.valueOrDefault(
                OpenAIHostedWebSearchContract.ENV_MODEL,
                DEFAULT_MODEL,
            ),
    ): OpenAIHostedWebSearchBinding =
        OpenAIHostedWebSearchBinding(
            toolPkgId = OpenAIHostedWebSearchContract.TOOLPKG_ID,
            configSource = configSource,
            providerContract = providerContract,
            modelConfigId = modelConfigId,
            endpoint = endpoint.trim(),
            modelName = modelName.trim(),
            apiKey = apiKey.trim(),
            authHeaderName = authHeaderName.trim(),
            authScheme = authScheme.trim(),
            extraHeaders = extraHeaders,
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
            modelConfigMaxConcurrentRequests = modelConfigMaxConcurrentRequests,
            modelConfigRequestsPerMinute = modelConfigRequestsPerMinute,
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

internal object OpenAIHostedWebSearchModelConfigKeySelector {
    data class Selection(
        val apiKey: String,
        val nextIndex: Int?,
    )

    fun select(modelConfig: ModelConfigData): Selection {
        if (!modelConfig.useMultipleApiKeys) {
            return Selection(apiKey = modelConfig.apiKey, nextIndex = null)
        }

        val enabledKeys = modelConfig.apiKeyPool.filter { key -> key.isEnabled }
        val hasAvailabilityMarks =
            enabledKeys.any { key ->
                key.availabilityStatus != ApiKeyAvailabilityStatus.UNTESTED
            }
        val candidates =
            if (hasAvailabilityMarks) {
                enabledKeys.filter { key ->
                    key.availabilityStatus == ApiKeyAvailabilityStatus.AVAILABLE
                }
            } else {
                enabledKeys
            }
        if (candidates.isEmpty()) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.API_KEY_MISSING,
                message = "The selected model config has no usable API key.",
            )
        }
        val currentIndex = Math.floorMod(modelConfig.currentKeyIndex, candidates.size)
        return Selection(
            apiKey = candidates[currentIndex].key,
            nextIndex = (currentIndex + 1) % candidates.size,
        )
    }
}

internal class OpenAIHostedWebSearchBindingResolver(
    context: Context,
) {
    private val environmentRepository =
        ToolPkgHostEnvironmentRepository.getInstance(context.applicationContext)
    private val modelConfigManager = ModelConfigManager(context.applicationContext)
    private val compatibilityRepository =
        OpenAIHostedWebSearchCompatibilityRepository.getInstance(context.applicationContext)

    suspend fun resolve(
        requireRelayProbe: Boolean,
        advanceModelConfigKey: Boolean,
    ): OpenAIHostedWebSearchResolvedBinding {
        val environment =
            OpenAIHostedWebSearchContract.ENVIRONMENT_NAMES.associateWith { name ->
                environmentRepository
                    .getValue(OpenAIHostedWebSearchContract.TOOLPKG_ID, name)
                    .orEmpty()
            }
        return when (OpenAIHostedWebSearchBindingCompiler.configSource(environment)) {
            OpenAIHostedWebSearchConfigSource.PACKAGE_ENV -> {
                resolveCompiledBinding(
                    binding =
                        OpenAIHostedWebSearchBindingCompiler
                            .compilePackageEnvironment(environment),
                    recordSet = compatibilityRepository.readSet(),
                    requireRelayProbe = requireRelayProbe,
                )
            }

            OpenAIHostedWebSearchConfigSource.MODEL_CONFIG ->
                modelConfigKeySelectionMutex.withLock {
                    val modelConfigId =
                        environment[OpenAIHostedWebSearchContract.ENV_MODEL_CONFIG_ID]
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                            ?: throw OpenAIHostedWebSearchException(
                                code =
                                    OpenAIHostedWebSearchErrorCode.MODEL_CONFIG_NOT_FOUND,
                                message =
                                    "OpenAI Web Search requires a fixed model config ID.",
                            )
                    val modelConfig =
                        modelConfigManager.getModelConfig(modelConfigId)
                            ?: throw OpenAIHostedWebSearchException(
                                code =
                                    OpenAIHostedWebSearchErrorCode.MODEL_CONFIG_NOT_FOUND,
                                message =
                                    "The selected OpenAI Web Search model config is missing.",
                            )
                    val selection =
                        OpenAIHostedWebSearchModelConfigKeySelector.select(modelConfig)
                    val resolved =
                        resolveCompiledBinding(
                            binding =
                                OpenAIHostedWebSearchBindingCompiler.compileModelConfig(
                                    environment = environment,
                                    modelConfig = modelConfig,
                                    selectedApiKey = selection.apiKey,
                                ),
                            recordSet = compatibilityRepository.readSet(),
                            requireRelayProbe = requireRelayProbe,
                        )
                    if (advanceModelConfigKey && selection.nextIndex != null) {
                        modelConfigManager.updateConfigKeyIndex(
                            modelConfig.id,
                            selection.nextIndex,
                        )
                    }
                    resolved
                }
        }
    }

    private fun resolveCompiledBinding(
        binding: OpenAIHostedWebSearchBinding,
        recordSet: OpenAIHostedWebSearchCompatibilityRecordSet,
        requireRelayProbe: Boolean,
    ): OpenAIHostedWebSearchResolvedBinding {
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

    private companion object {
        /**
         * Every bridge and compatibility-probe instance shares the same MODEL_CONFIG key cursor.
         *
         * An instance-local mutex allows a search and a paid probe created by different host
         * services to select and advance the same key concurrently. That can associate a request
         * with an unexpected key revision and make the exact-fingerprint probe status misleading.
         */
        val modelConfigKeySelectionMutex = Mutex()
    }
}
