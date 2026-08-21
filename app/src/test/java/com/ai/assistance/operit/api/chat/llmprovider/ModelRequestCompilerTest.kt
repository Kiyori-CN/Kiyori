package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ApiProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRequestCompilerTest {
    @Test
    fun gpt56OfficialResponses_mapsFiveUserLevelsAndEnablesResumableNoStoreExecution() {
        val profile =
            ModelCapabilityResolver.resolve(
                providerType = ApiProviderType.OPENAI_RESPONSES,
                modelName = "gpt-5.6-sol",
                apiEndpoint = "https://api.openai.com/v1/responses",
            )
        val expected = listOf("low", "medium", "high", "xhigh", "max")

        val actual =
            (1..5).map { level ->
                ModelRequestCompiler
                    .compile(
                        profile = profile,
                        intent =
                            UserExecutionIntent(
                                enableThinking = true,
                                thinkingQualityLevel = level,
                            ),
                    )
                    .reasoningEffort
                    ?.wireValue
            }

        assertEquals(expected, actual)
        val compiled =
            ModelRequestCompiler.compile(
                profile = profile,
                intent = UserExecutionIntent(enableThinking = true, thinkingQualityLevel = 5),
            )
        assertTrue(compiled.background)
        assertEquals(false, compiled.store)
        assertTrue(compiled.reasoningSummaryEnabled)
        assertTrue(compiled.encryptedReasoningContentEnabled)
        assertTrue(compiled.promptCacheEnabled)
        assertTrue(compiled.strictToolSchemasWhenCompatible)
        assertTrue(compiled.toolSearchEnabled)
    }

    @Test
    fun disabledThinking_compilesToNoneWithoutEnablingSummary() {
        val profile =
            ModelCapabilityResolver.resolve(
                providerType = ApiProviderType.OPENAI_RESPONSES,
                modelName = "gpt-5.6-terra",
                apiEndpoint = "https://api.openai.com/v1/responses",
            )
        val compiled =
            ModelRequestCompiler.compile(
                profile = profile,
                intent = UserExecutionIntent(enableThinking = false, thinkingQualityLevel = 5),
            )

        assertEquals(ReasoningEffortValue.NONE, compiled.reasoningEffort)
        assertFalse(compiled.reasoningSummaryEnabled)
        assertFalse(compiled.encryptedReasoningContentEnabled)
    }

    @Test
    fun genericResponses_keepsReasoningMappingButUsesOnlyCompatibleRequestFeatures() {
        val profile =
            ModelCapabilityResolver.resolve(
                providerType = ApiProviderType.OPENAI_RESPONSES_GENERIC,
                modelName = "gpt-5.6-luna",
                apiEndpoint = "https://pipio.io/v1/responses",
            )
        val compiled =
            ModelRequestCompiler.compile(
                profile = profile,
                intent = UserExecutionIntent(enableThinking = true, thinkingQualityLevel = 4),
            )

        assertEquals(ReasoningEffortValue.XHIGH, compiled.reasoningEffort)
        assertEquals(
            ProviderContractAuthority.OPENAI_COMPATIBLE,
            profile.providerContractAuthority,
        )
        assertEquals(
            ExecutionPersistenceCapability.RESPONSES_AT_MOST_ONCE,
            profile.executionPersistence,
        )
        assertTrue(compiled.reasoningSummaryEnabled)
        assertFalse(compiled.encryptedReasoningContentEnabled)
        assertFalse(compiled.background)
        assertNull(compiled.store)
        assertFalse(compiled.promptCacheEnabled)
        assertFalse(compiled.strictToolSchemasWhenCompatible)
        assertFalse(compiled.toolSearchEnabled)
    }

    @Test
    fun officialProviderTypeWithCustomEndpoint_isCompiledAsCompatibleResponses() {
        val profile =
            ModelCapabilityResolver.resolve(
                providerType = ApiProviderType.OPENAI_RESPONSES,
                modelName = "gpt-5.6-sol",
                apiEndpoint = "https://pipio.io/v1/responses",
            )
        val compiled =
            ModelRequestCompiler.compile(
                profile = profile,
                intent = UserExecutionIntent(enableThinking = true, thinkingQualityLevel = 4),
            )

        assertEquals(
            ProviderContractAuthority.OPENAI_COMPATIBLE,
            profile.providerContractAuthority,
        )
        assertEquals(ReasoningEffortValue.XHIGH, compiled.reasoningEffort)
        assertEquals(
            ExecutionPersistenceCapability.RESPONSES_AT_MOST_ONCE,
            profile.executionPersistence,
        )
        assertTrue(compiled.reasoningSummaryEnabled)
        assertFalse(compiled.encryptedReasoningContentEnabled)
        assertFalse(compiled.background)
        assertNull(compiled.store)
        assertFalse(compiled.promptCacheEnabled)
        assertFalse(compiled.strictToolSchemasWhenCompatible)
        assertFalse(compiled.toolSearchEnabled)
    }

    @Test
    fun officialResponsesContract_requiresExactOpenAiEndpoint() {
        listOf(
            "https://api.openai.com.evil.example/v1/responses",
            "http://api.openai.com/v1/responses",
            "https://api.openai.com:8443/v1/responses",
            "https://user@api.openai.com/v1/responses",
            "https://api.openai.com/v1/responses?proxy=1",
        ).forEach { endpoint ->
            assertEquals(
                ProviderContractAuthority.OPENAI_COMPATIBLE,
                ModelCapabilityResolver.resolve(
                    providerType = ApiProviderType.OPENAI_RESPONSES,
                    modelName = "gpt-5.6-sol",
                    apiEndpoint = endpoint,
                ).providerContractAuthority,
            )
        }

        listOf(
            "https://api.openai.com",
            "https://api.openai.com/v1",
            "https://api.openai.com/v1/responses",
            "https://api.openai.com/v1/responses/",
        ).forEach { endpoint ->
            assertEquals(
                ProviderContractAuthority.OPENAI_OFFICIAL,
                ModelCapabilityResolver.resolve(
                    providerType = ApiProviderType.OPENAI_RESPONSES,
                    modelName = "gpt-5.6-sol",
                    apiEndpoint = endpoint,
                ).providerContractAuthority,
            )
        }

        assertEquals(
            ProviderContractAuthority.OPENAI_COMPATIBLE,
            ModelCapabilityResolver.resolve(
                providerType = ApiProviderType.OPENAI_RESPONSES_GENERIC,
                modelName = "gpt-5.6-sol",
                apiEndpoint = "https://api.openai.com/v1/responses",
            ).providerContractAuthority,
        )
    }

    @Test
    fun ordinaryOpenAiModels_useOnlyTheDeclaredThreeWireReasoningLevels() {
        val profile =
            ModelCapabilityResolver.resolve(
                providerType = ApiProviderType.OPENAI_RESPONSES,
                modelName = "gpt-5.4-mini",
                apiEndpoint = "https://api.openai.com/v1/responses",
            )
        val compiled =
            ModelRequestCompiler.compile(
                profile = profile,
                intent = UserExecutionIntent(enableThinking = true, thinkingQualityLevel = 5),
            )

        assertEquals(ReasoningEffortValue.HIGH, compiled.reasoningEffort)
        assertFalse(compiled.reasoningSummaryEnabled)
        assertFalse(compiled.encryptedReasoningContentEnabled)
        assertFalse(compiled.background)
        assertEquals(
            ExecutionPersistenceCapability.RESPONSES_AT_MOST_ONCE,
            profile.executionPersistence,
        )
        assertTrue(compiled.promptCacheEnabled)
        assertFalse(compiled.toolSearchEnabled)
    }

    @Test
    fun ordinaryOpenAiChatCompletions_mapFiveUserLevelsToThreeWireValues() {
        val profile =
            ModelCapabilityResolver.resolve(
                providerType = ApiProviderType.OPENAI,
                modelName = "gpt-5.4-mini",
                apiEndpoint = "https://api.openai.com/v1/chat/completions",
            )

        assertEquals(
            listOf(
                ReasoningEffortValue.LOW,
                ReasoningEffortValue.LOW,
                ReasoningEffortValue.MEDIUM,
                ReasoningEffortValue.HIGH,
                ReasoningEffortValue.HIGH,
            ),
            (1..5).map { level ->
                ModelRequestCompiler
                    .compile(
                        profile = profile,
                        intent =
                            UserExecutionIntent(
                                enableThinking = true,
                                thinkingQualityLevel = level,
                            ),
                    )
                    .reasoningEffort
            },
        )
    }

    @Test
    fun xAiChatUsesTheStandardNonGpt56ReasoningMapping() {
        val route =
            ProtocolServiceRoutingPolicy.resolve(
                providerType = ApiProviderType.XAI,
                apiProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            )
        val profile =
            ModelCapabilityResolver.resolve(
                providerType = route.capabilityProviderType,
                modelName = "grok-4.6",
                apiEndpoint = "https://api.x.ai/v1/chat/completions",
            )

        assertEquals(
            listOf(
                ReasoningEffortValue.LOW,
                ReasoningEffortValue.LOW,
                ReasoningEffortValue.MEDIUM,
                ReasoningEffortValue.HIGH,
                ReasoningEffortValue.HIGH,
            ),
            (1..5).map { level ->
                ModelRequestCompiler
                    .compile(
                        profile = profile,
                        intent =
                            UserExecutionIntent(
                                enableThinking = true,
                                thinkingQualityLevel = level,
                            ),
                    )
                    .reasoningEffort
            },
        )
    }

    @Test
    fun gpt56FamilyRecognition_acceptsSuffixedModelNames() {
        val profile =
            ModelCapabilityResolver.resolve(
                providerType = ApiProviderType.OPENAI,
                modelName = "gpt-5.6-preview-2026-08",
                apiEndpoint = "https://api.openai.com/v1/chat/completions",
            )

        assertEquals(
            listOf(
                ReasoningEffortValue.LOW,
                ReasoningEffortValue.MEDIUM,
                ReasoningEffortValue.HIGH,
                ReasoningEffortValue.XHIGH,
                ReasoningEffortValue.MAX,
            ),
            (1..5).map { level ->
                ModelRequestCompiler
                    .compile(
                        profile = profile,
                        intent =
                            UserExecutionIntent(
                                enableThinking = true,
                                thinkingQualityLevel = level,
                            ),
                    )
                    .reasoningEffort
            },
        )
    }
}
