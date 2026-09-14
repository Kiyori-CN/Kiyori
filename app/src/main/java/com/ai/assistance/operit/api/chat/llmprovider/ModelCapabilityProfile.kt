package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType
import java.net.URI

enum class ReasoningEffortValue(val wireValue: String) {
    NONE("none"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max"),
}

enum class ReasoningWireFormat {
    NONE,
    CHAT_COMPLETIONS,
    RESPONSES,
}

enum class ProviderContractAuthority {
    OPENAI_OFFICIAL,
    OPENAI_COMPATIBLE,
    NOT_APPLICABLE,
}

enum class ReasoningSummaryCapability {
    NONE,
    OPENAI_AUTO,
}

enum class ReasoningReplayCapability {
    NONE,
    OPENAI_ENCRYPTED_CONTENT,
}

enum class ExecutionPersistenceCapability {
    NONE,
    RESPONSES_AT_MOST_ONCE,
    OPENAI_BACKGROUND_SEQUENCE_RESUME,
}

enum class PromptCacheCapability {
    NONE,
    OPENAI_PROMPT_CACHE_KEY,
}

enum class ToolSchemaCapability {
    BASELINE,
    STRICT_WHEN_SCHEMA_COMPATIBLE,
}

enum class ToolDiscoveryCapability {
    EAGER_ONLY,
    OPENAI_TOOL_SEARCH,
}

data class ModelCapabilityProfile(
    val profileId: String,
    val modelFamily: String,
    val providerContractAuthority: ProviderContractAuthority,
    val reasoningWireFormat: ReasoningWireFormat,
    val supportedReasoningEfforts: Set<ReasoningEffortValue>,
    val qualityLevelToReasoningEffort: List<ReasoningEffortValue> = emptyList(),
    val reasoningSummary: ReasoningSummaryCapability,
    val reasoningReplay: ReasoningReplayCapability,
    val executionPersistence: ExecutionPersistenceCapability,
    val promptCache: PromptCacheCapability,
    val promptCacheNamespace: String?,
    val toolSchema: ToolSchemaCapability,
    val toolDiscovery: ToolDiscoveryCapability,
    val supportsDisabledThinking: Boolean = true,
) {
    init {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        require(modelFamily.isNotBlank()) { "modelFamily must not be blank" }
        require(
            reasoningWireFormat != ReasoningWireFormat.NONE ||
                supportedReasoningEfforts.isEmpty()
        ) {
            "A model without a reasoning wire format cannot declare reasoning efforts"
        }
        require(
            supportedReasoningEfforts.isEmpty() ||
                qualityLevelToReasoningEffort.size == UserExecutionIntent.MAX_QUALITY_LEVEL
        ) {
            "Reasoning profiles must define one wire mapping for every user quality level"
        }
        require(qualityLevelToReasoningEffort.all(supportedReasoningEfforts::contains)) {
            "Reasoning quality mapping contains an unsupported wire value"
        }
        require(
            reasoningSummary == ReasoningSummaryCapability.NONE ||
                reasoningWireFormat == ReasoningWireFormat.RESPONSES
        ) {
            "Reasoning summaries require the Responses wire format"
        }
        require(
            reasoningReplay == ReasoningReplayCapability.NONE ||
                reasoningWireFormat == ReasoningWireFormat.RESPONSES
        ) {
            "Encrypted reasoning replay requires the Responses wire format"
        }
        require(
            promptCache != PromptCacheCapability.OPENAI_PROMPT_CACHE_KEY ||
                !promptCacheNamespace.isNullOrBlank()
        ) {
            "Prompt-cache profiles must declare a namespace"
        }
    }

    fun reasoningEffortForQualityLevel(level: Int): ReasoningEffortValue {
        require(level in UserExecutionIntent.MIN_QUALITY_LEVEL..UserExecutionIntent.MAX_QUALITY_LEVEL) {
            "thinkingQualityLevel must be in ${UserExecutionIntent.MIN_QUALITY_LEVEL}..${UserExecutionIntent.MAX_QUALITY_LEVEL}"
        }
        return requireNotNull(qualityLevelToReasoningEffort.getOrNull(level - 1)) {
            "Profile $profileId does not declare reasoning mapping for quality level $level"
        }
    }
}

data class UserExecutionIntent(
    val enableThinking: Boolean,
    val thinkingQualityLevel: Int,
) {
    init {
        require(thinkingQualityLevel in MIN_QUALITY_LEVEL..MAX_QUALITY_LEVEL) {
            "thinkingQualityLevel must be in $MIN_QUALITY_LEVEL..$MAX_QUALITY_LEVEL"
        }
    }

    companion object {
        const val MIN_QUALITY_LEVEL = 1
        const val MAX_QUALITY_LEVEL = 5
    }
}

data class CompiledModelRequest(
    val profile: ModelCapabilityProfile,
    val reasoningEffort: ReasoningEffortValue?,
    val reasoningSummaryEnabled: Boolean,
    val encryptedReasoningContentEnabled: Boolean,
    val background: Boolean,
    val store: Boolean?,
    val promptCacheEnabled: Boolean,
    val promptCacheNamespace: String?,
    val strictToolSchemasWhenCompatible: Boolean,
    val toolSearchEnabled: Boolean,
)

object ModelCapabilityResolver {
    fun resolve(
        providerType: ApiProviderType,
        modelName: String,
        apiEndpoint: String,
        providerIdentityType: ApiProviderType = providerType,
    ): ModelCapabilityProfile {
        val normalizedModel = modelName.trim().lowercase()
        val reasoningWireFormat =
            when (providerType) {
                ApiProviderType.OPENAI_RESPONSES,
                ApiProviderType.OPENAI_RESPONSES_GENERIC,
                -> ReasoningWireFormat.RESPONSES

                ApiProviderType.OPENAI,
                ApiProviderType.OPENAI_GENERIC,
                -> ReasoningWireFormat.CHAT_COMPLETIONS

                else -> ReasoningWireFormat.NONE
            }
        val providerContractAuthority =
            OpenAiEndpointContract.resolve(
                providerType = providerType,
                apiEndpoint = apiEndpoint,
            )
        val isOfficialOpenAi =
            providerContractAuthority == ProviderContractAuthority.OPENAI_OFFICIAL
        val isResponses = reasoningWireFormat == ReasoningWireFormat.RESPONSES
        val isOfficialResponses = isOfficialOpenAi && isResponses
        val isDeepSeekResponses =
            providerIdentityType == ApiProviderType.DEEPSEEK && isResponses
        val isGpt56Family = normalizedModel.startsWith("gpt-5.6")
        val isAstra = normalizedModel == "gpt-6-astra" ||
            Regex("gpt-6-astra-\\d{4}-\\d{2}-\\d{2}").matches(normalizedModel)

        if ((isGpt56Family || isAstra) && !isDeepSeekResponses && reasoningWireFormat != ReasoningWireFormat.NONE) {
            val family = if (isAstra) "gpt-6-astra" else "gpt-5.6"
            val tier =
                when (normalizedModel) {
                    "gpt-5.6-sol" -> "sol"
                    "gpt-5.6-terra" -> "terra"
                    "gpt-5.6-luna" -> "luna"
                    else -> "family"
                }
            val contractId =
                when (providerContractAuthority) {
                    ProviderContractAuthority.OPENAI_OFFICIAL -> "openai-official"
                    ProviderContractAuthority.OPENAI_COMPATIBLE -> "openai-compatible"
                    ProviderContractAuthority.NOT_APPLICABLE -> "provider"
                }
            return ModelCapabilityProfile(
                profileId = "$contractId-$family-$tier-v2",
                modelFamily = family,
                supportsDisabledThinking = !isAstra,
                providerContractAuthority = providerContractAuthority,
                reasoningWireFormat = reasoningWireFormat,
                supportedReasoningEfforts = FIVE_LEVEL_REASONING.toSet(),
                qualityLevelToReasoningEffort = FIVE_LEVEL_REASONING,
                reasoningSummary =
                    if (isResponses) {
                        ReasoningSummaryCapability.OPENAI_AUTO
                    } else {
                        ReasoningSummaryCapability.NONE
                    },
                reasoningReplay =
                    if (isOfficialResponses) {
                        ReasoningReplayCapability.OPENAI_ENCRYPTED_CONTENT
                    } else {
                        ReasoningReplayCapability.NONE
                    },
                executionPersistence =
                    when {
                        isOfficialResponses ->
                            ExecutionPersistenceCapability.OPENAI_BACKGROUND_SEQUENCE_RESUME

                        isDeepSeekResponses ->
                            ExecutionPersistenceCapability.RESPONSES_AT_MOST_ONCE

                        isResponses ->
                            ExecutionPersistenceCapability.RESPONSES_AT_MOST_ONCE

                        else -> ExecutionPersistenceCapability.NONE
                    },
                promptCache =
                    if (isResponses) {
                        PromptCacheCapability.OPENAI_PROMPT_CACHE_KEY
                    } else {
                        PromptCacheCapability.NONE
                    },
                promptCacheNamespace =
                    if (isResponses) {
                        if (isOfficialResponses) "kiyori:openai:$family:$tier:v1"
                        else "kiyori:openai-compatible:$family:$tier:v1"
                    } else {
                        null
                    },
                toolSchema =
                    if (isOfficialOpenAi) {
                        ToolSchemaCapability.STRICT_WHEN_SCHEMA_COMPATIBLE
                    } else {
                        ToolSchemaCapability.BASELINE
                    },
                toolDiscovery =
                    if (isOfficialResponses) {
                        ToolDiscoveryCapability.OPENAI_TOOL_SEARCH
                    } else {
                        ToolDiscoveryCapability.EAGER_ONLY
                    },
            )
        }

        if (reasoningWireFormat != ReasoningWireFormat.NONE) {
            val isNonReasoningModel = Regex("gpt-(?:4(?:\\.1|o)?|3\\.5)(?:-|$).*").matches(normalizedModel)
            val isFourLevelGpt = Regex("gpt-5\\.[45](?:-\\d{4}-\\d{2}-\\d{2})?").matches(normalizedModel)
            val mapping = when {
                isNonReasoningModel -> emptyList()
                isFourLevelGpt -> listOf(ReasoningEffortValue.LOW, ReasoningEffortValue.MEDIUM,
                    ReasoningEffortValue.HIGH, ReasoningEffortValue.XHIGH, ReasoningEffortValue.XHIGH)
                else -> STANDARD_REASONING_QUALITY_MAPPING
            }
            val contractId =
                when (providerContractAuthority) {
                    ProviderContractAuthority.OPENAI_OFFICIAL -> "openai-official"
                    ProviderContractAuthority.OPENAI_COMPATIBLE -> "openai-compatible"
                    ProviderContractAuthority.NOT_APPLICABLE -> "provider"
                }
            if (isDeepSeekResponses) {
                return ModelCapabilityProfile(
                    profileId = "deepseek-responses-at-most-once-v2",
                    modelFamily = normalizedModel.ifBlank { "deepseek" },
                    providerContractAuthority = providerContractAuthority,
                    reasoningWireFormat = ReasoningWireFormat.RESPONSES,
                    supportedReasoningEfforts = DeepSeekReasoningPolicy.qualityMapping.toSet(),
                    qualityLevelToReasoningEffort = DeepSeekReasoningPolicy.qualityMapping,
                    reasoningSummary = ReasoningSummaryCapability.NONE,
                    reasoningReplay = ReasoningReplayCapability.NONE,
                    // DeepSeek does not expose a proven response resume contract, but a submitted
                    // Responses request must still be sent at most once when its outcome is unknown.
                    executionPersistence =
                        ExecutionPersistenceCapability.RESPONSES_AT_MOST_ONCE,
                    promptCache = PromptCacheCapability.NONE,
                    promptCacheNamespace = null,
                    toolSchema = ToolSchemaCapability.BASELINE,
                    toolDiscovery = ToolDiscoveryCapability.EAGER_ONLY,
                )
            }
            return ModelCapabilityProfile(
                profileId = "$contractId-passthrough-v2",
                modelFamily = normalizedModel.ifBlank { "openai-compatible" },
                providerContractAuthority = providerContractAuthority,
                reasoningWireFormat = reasoningWireFormat,
                supportedReasoningEfforts = mapping.toSet(),
                qualityLevelToReasoningEffort = mapping,
                reasoningSummary = if (isFourLevelGpt && isResponses) ReasoningSummaryCapability.OPENAI_AUTO
                    else ReasoningSummaryCapability.NONE,
                reasoningReplay = ReasoningReplayCapability.NONE,
                executionPersistence =
                    if (isResponses) {
                        ExecutionPersistenceCapability.RESPONSES_AT_MOST_ONCE
                    } else {
                        ExecutionPersistenceCapability.NONE
                    },
                promptCache =
                    if (isOfficialResponses) {
                        PromptCacheCapability.OPENAI_PROMPT_CACHE_KEY
                    } else {
                        PromptCacheCapability.NONE
                    },
                promptCacheNamespace =
                    if (isOfficialResponses) {
                        "kiyori:openai:responses:v1"
                    } else {
                        null
                    },
                toolSchema = ToolSchemaCapability.BASELINE,
                toolDiscovery = ToolDiscoveryCapability.EAGER_ONLY,
            )
        }

        return ModelCapabilityProfile(
            profileId = "provider-baseline-v1",
            modelFamily = normalizedModel.ifBlank { providerType.name.lowercase() },
            providerContractAuthority = ProviderContractAuthority.NOT_APPLICABLE,
            reasoningWireFormat = ReasoningWireFormat.NONE,
            supportedReasoningEfforts = emptySet(),
            reasoningSummary = ReasoningSummaryCapability.NONE,
            reasoningReplay = ReasoningReplayCapability.NONE,
            executionPersistence = ExecutionPersistenceCapability.NONE,
            promptCache = PromptCacheCapability.NONE,
            promptCacheNamespace = null,
            toolSchema = ToolSchemaCapability.BASELINE,
            toolDiscovery = ToolDiscoveryCapability.EAGER_ONLY,
        )
    }

    private val FIVE_LEVEL_REASONING =
        listOf(
            ReasoningEffortValue.LOW,
            ReasoningEffortValue.MEDIUM,
            ReasoningEffortValue.HIGH,
            ReasoningEffortValue.XHIGH,
            ReasoningEffortValue.MAX,
        )

    private val STANDARD_REASONING_QUALITY_MAPPING =
        listOf(
            ReasoningEffortValue.LOW,
            ReasoningEffortValue.LOW,
            ReasoningEffortValue.MEDIUM,
            ReasoningEffortValue.HIGH,
            ReasoningEffortValue.HIGH,
        )
}

internal object OpenAiEndpointContract {
    fun resolve(
        providerType: ApiProviderType,
        apiEndpoint: String,
    ): ProviderContractAuthority =
        when (providerType) {
            ApiProviderType.OPENAI ->
                authorityForOfficialEndpoint(
                    providerType = providerType,
                    apiEndpoint = apiEndpoint,
                    expectedPath = "/v1/chat/completions",
                )

            ApiProviderType.OPENAI_RESPONSES ->
                authorityForOfficialEndpoint(
                    providerType = providerType,
                    apiEndpoint = apiEndpoint,
                    expectedPath = "/v1/responses",
                )

            ApiProviderType.OPENAI_GENERIC,
            ApiProviderType.OPENAI_RESPONSES_GENERIC,
            -> ProviderContractAuthority.OPENAI_COMPATIBLE

            else -> ProviderContractAuthority.NOT_APPLICABLE
        }

    private fun authorityForOfficialEndpoint(
        providerType: ApiProviderType,
        apiEndpoint: String,
        expectedPath: String,
    ): ProviderContractAuthority {
        val completedEndpoint =
            runCatching {
                EndpointCompleter.completeEndpoint(apiEndpoint, providerType)
            }.getOrNull()
                ?: return ProviderContractAuthority.OPENAI_COMPATIBLE
        val uri =
            runCatching { URI(completedEndpoint) }.getOrNull()
                ?: return ProviderContractAuthority.OPENAI_COMPATIBLE
        val port = if (uri.port == -1) 443 else uri.port
        val normalizedPath = uri.path.orEmpty().removeSuffix("/")
        val isOfficial =
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("api.openai.com", ignoreCase = true) &&
                port == 443 &&
                uri.userInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                normalizedPath.equals(expectedPath, ignoreCase = true)
        return if (isOfficial) {
            ProviderContractAuthority.OPENAI_OFFICIAL
        } else {
            ProviderContractAuthority.OPENAI_COMPATIBLE
        }
    }
}

object ModelRequestCompiler {
    fun compile(
        profile: ModelCapabilityProfile,
        intent: UserExecutionIntent,
    ): CompiledModelRequest {
        val reasoningEffort =
            when {
                profile.reasoningWireFormat == ReasoningWireFormat.NONE -> null
                profile.supportedReasoningEfforts.isEmpty() -> null
                !intent.enableThinking -> {
                    require(profile.supportsDisabledThinking) {
                        "${profile.modelFamily} 不支持关闭思考（none）；请开启思考并选择 low 至 max。"
                    }
                    ReasoningEffortValue.NONE
                }
                else -> {
                    profile.reasoningEffortForQualityLevel(intent.thinkingQualityLevel)
                }
            }
        val resumableBackground =
            profile.executionPersistence ==
                ExecutionPersistenceCapability.OPENAI_BACKGROUND_SEQUENCE_RESUME
        val promptCacheEnabled =
            profile.promptCache == PromptCacheCapability.OPENAI_PROMPT_CACHE_KEY

        return CompiledModelRequest(
            profile = profile,
            reasoningEffort = reasoningEffort,
            reasoningSummaryEnabled =
                intent.enableThinking &&
                    reasoningEffort != null &&
                    profile.reasoningSummary == ReasoningSummaryCapability.OPENAI_AUTO,
            encryptedReasoningContentEnabled =
                intent.enableThinking &&
                    reasoningEffort != null &&
                    reasoningEffort != ReasoningEffortValue.NONE &&
                    profile.reasoningReplay ==
                        ReasoningReplayCapability.OPENAI_ENCRYPTED_CONTENT,
            background = resumableBackground,
            store = false.takeIf { resumableBackground },
            promptCacheEnabled = promptCacheEnabled,
            promptCacheNamespace = profile.promptCacheNamespace,
            strictToolSchemasWhenCompatible =
                profile.toolSchema == ToolSchemaCapability.STRICT_WHEN_SCHEMA_COMPATIBLE,
            toolSearchEnabled =
                profile.toolDiscovery == ToolDiscoveryCapability.OPENAI_TOOL_SEARCH,
        )
    }

}

/** 三种 DeepSeek 协议共用用户档位，协议适配器只负责字段形状。 */
internal object DeepSeekReasoningPolicy {
    val qualityMapping = listOf(
        ReasoningEffortValue.LOW, ReasoningEffortValue.HIGH, ReasoningEffortValue.MAX,
        ReasoningEffortValue.MAX, ReasoningEffortValue.MAX,
    )

    fun effort(level: Int): String {
        require(level in 1..5) { "thinkingQualityLevel must be in 1..5" }
        return qualityMapping[level - 1].wireValue
    }
}
