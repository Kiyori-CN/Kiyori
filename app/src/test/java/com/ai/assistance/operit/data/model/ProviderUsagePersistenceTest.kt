package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProviderUsagePersistenceTest {
    private val usage =
        ProviderUsageAggregate(
            requestCount = 3,
            providerUsageRequestCount = 2,
            providerCacheMetricRequestCount = 1,
            providerCacheMetricPromptTokens = 100L,
            providerTotalInputTokens = 100L,
            providerUncachedInputTokens = 20L,
            providerCacheReadTokens = 70L,
            providerCacheWriteTokens = 10L,
            providerOutputTokens = 30L,
            providerReasoningTokens = 8L,
        )

    @Test
    fun aggregateAdditionKeepsDisjointPromptBucketsAndUnknownCoverage() {
        val second =
            ProviderUsageAggregate(
                requestCount = 1,
                providerUsageRequestCount = 1,
                providerCacheMetricRequestCount = 1,
                providerCacheMetricPromptTokens = 50L,
                providerTotalInputTokens = 50L,
                providerUncachedInputTokens = 10L,
                providerCacheReadTokens = 40L,
                providerCacheWriteTokens = 0L,
                providerOutputTokens = 12L,
                providerReasoningTokens = 3L,
            )

        val result = usage + second

        assertEquals(4, result.requestCount)
        assertEquals(3, result.providerUsageRequestCount)
        assertEquals(2, result.providerCacheMetricRequestCount)
        assertEquals(150L, result.providerCacheMetricPromptTokens)
        assertEquals(150L, result.providerTotalInputTokens)
        assertEquals(30L, result.providerUncachedInputTokens)
        assertEquals(110L, result.providerCacheReadTokens)
        assertEquals(10L, result.providerCacheWriteTokens)
        assertEquals(42L, result.providerOutputTokens)
        assertEquals(11L, result.providerReasoningTokens)
        assertEquals(110.0 / 150.0, result.cacheHitRate!!, 0.000001)

        assertNull(ProviderUsageAggregate(requestCount = 1).cacheHitRate)
    }

    @Test
    fun cacheHitRateDenominatorExcludesProviderHopsWithoutCacheMetrics() {
        val reportedFirst =
            ProviderUsageAggregate(
                requestCount = 1,
                providerUsageRequestCount = 1,
                providerCacheMetricRequestCount = 1,
                providerCacheMetricPromptTokens = 100L,
                providerTotalInputTokens = 100L,
                providerUncachedInputTokens = 30L,
                providerCacheReadTokens = 70L,
            )
        val reportedSecond =
            ProviderUsageAggregate(
                requestCount = 1,
                providerUsageRequestCount = 1,
                providerCacheMetricRequestCount = 1,
                providerCacheMetricPromptTokens = 50L,
                providerTotalInputTokens = 50L,
                providerUncachedInputTokens = 10L,
                providerCacheReadTokens = 40L,
            )
        val unreported =
            ProviderUsageAggregate(
                requestCount = 1,
                providerUsageRequestCount = 1,
                providerTotalInputTokens = 100L,
                providerUncachedInputTokens = 100L,
            )

        val result = reportedFirst + reportedSecond + unreported

        assertEquals(3, result.requestCount)
        assertEquals(3, result.providerUsageRequestCount)
        assertEquals(2, result.providerCacheMetricRequestCount)
        assertEquals(150L, result.providerCacheMetricPromptTokens)
        assertEquals(110.0 / 150.0, result.cacheHitRate!!, 0.000001)
    }

    @Test
    fun aggregateAdditionSaturatesWithoutBreakingPromptBucketInvariant() {
        val saturated =
            ProviderUsageAggregate(
                requestCount = Int.MAX_VALUE,
                providerUsageRequestCount = Int.MAX_VALUE,
                providerCacheMetricRequestCount = Int.MAX_VALUE,
                providerCacheMetricPromptTokens = Long.MAX_VALUE,
                providerTotalInputTokens = Long.MAX_VALUE,
                providerUncachedInputTokens = Long.MAX_VALUE,
                providerOutputTokens = Long.MAX_VALUE,
                providerReasoningTokens = Long.MAX_VALUE,
            ) +
                ProviderUsageAggregate(
                    requestCount = 1,
                    providerUsageRequestCount = 1,
                    providerCacheMetricRequestCount = 1,
                    providerCacheMetricPromptTokens = 1L,
                    providerTotalInputTokens = 1L,
                    providerCacheReadTokens = 1L,
                    providerOutputTokens = 1L,
                    providerReasoningTokens = 1L,
                )

        assertEquals(Int.MAX_VALUE, saturated.requestCount)
        assertEquals(Int.MAX_VALUE, saturated.providerUsageRequestCount)
        assertEquals(Int.MAX_VALUE, saturated.providerCacheMetricRequestCount)
        assertEquals(Long.MAX_VALUE, saturated.providerCacheMetricPromptTokens)
        assertEquals(Long.MAX_VALUE, saturated.providerPromptTokens)
        assertEquals(Long.MAX_VALUE, saturated.providerTotalInputTokens)
        assertEquals(Long.MAX_VALUE, saturated.providerOutputTokens)
        assertEquals(Long.MAX_VALUE, saturated.providerReasoningTokens)
    }

    @Test
    fun chatAndMessagePersistenceMappingsKeepProviderUsageFields() {
        val history =
            ChatHistory(
                id = "chat-usage",
                title = "usage",
                messages = emptyList(),
                providerRequestCount = usage.requestCount,
                providerUsageRequestCount = usage.providerUsageRequestCount,
                providerCacheMetricRequestCount = usage.providerCacheMetricRequestCount,
                providerTotalInputTokens = usage.providerTotalInputTokens,
                providerUncachedInputTokens = usage.providerUncachedInputTokens,
                providerCacheReadTokens = usage.providerCacheReadTokens,
                providerCacheWriteTokens = usage.providerCacheWriteTokens,
                providerOutputTokens = usage.providerOutputTokens,
                providerReasoningTokens = usage.providerReasoningTokens,
            )

        val chatEntity = ChatEntity.fromChatHistory(history)
        val restoredHistory = chatEntity.toChatHistory(emptyList())
        assertEquals(history.providerRequestCount, restoredHistory.providerRequestCount)
        assertEquals(
            history.providerCacheMetricRequestCount,
            restoredHistory.providerCacheMetricRequestCount,
        )
        assertEquals(
            history.providerCacheMetricPromptTokens,
            restoredHistory.providerCacheMetricPromptTokens,
        )
        assertEquals(history.providerTotalInputTokens, restoredHistory.providerTotalInputTokens)
        assertEquals(history.providerCacheReadTokens, restoredHistory.providerCacheReadTokens)
        assertEquals(history.providerCacheWriteTokens, restoredHistory.providerCacheWriteTokens)

        val message =
            ChatMessage(
                sender = "ai",
                content = "answer",
                timestamp = 42L,
            ).withProviderUsageAggregate(usage)
        val messageEntity = MessageEntity.fromChatMessage("chat-usage", message, orderIndex = 0)
        val restoredMessage = messageEntity.toChatMessage()
        assertEquals(usage.requestCount, restoredMessage.providerRequestCount)
        assertEquals(usage.providerUsageRequestCount, restoredMessage.providerUsageRequestCount)
        assertEquals(
            usage.providerCacheMetricRequestCount,
            restoredMessage.providerCacheMetricRequestCount,
        )
        assertEquals(
            usage.providerCacheMetricPromptTokens,
            restoredMessage.providerCacheMetricPromptTokens,
        )
        assertEquals(usage.providerTotalInputTokens, restoredMessage.providerTotalInputTokens)
        assertEquals(usage.providerReasoningTokens, restoredMessage.providerReasoningTokens)

        val variant =
            MessageVariantEntity.fromChatMessage(
                chatId = "chat-usage",
                messageTimestamp = 42L,
                variantIndex = 1,
                message = message,
            )
        assertEquals(usage.providerCacheReadTokens, variant.providerCacheReadTokens)
        assertEquals(usage.providerCacheWriteTokens, variant.providerCacheWriteTokens)
        assertEquals(usage.providerOutputTokens, variant.providerOutputTokens)
    }

    @Test
    fun archiveRoundTripKeepsChatAndVariantProviderUsage() {
        val history =
            ChatHistory(
                id = "chat-archive",
                title = "archive",
                messages =
                    listOf(
                        ChatMessage(
                            sender = "ai",
                            content = "answer",
                            timestamp = 100L,
                        ).withProviderUsageAggregate(usage)
                    ),
                providerRequestCount = usage.requestCount,
                providerUsageRequestCount = usage.providerUsageRequestCount,
                providerCacheMetricRequestCount = usage.providerCacheMetricRequestCount,
                providerCacheMetricPromptTokens = usage.providerCacheMetricPromptTokens,
                providerTotalInputTokens = usage.providerTotalInputTokens,
                providerUncachedInputTokens = usage.providerUncachedInputTokens,
                providerCacheReadTokens = usage.providerCacheReadTokens,
                providerCacheWriteTokens = usage.providerCacheWriteTokens,
                providerOutputTokens = usage.providerOutputTokens,
                providerReasoningTokens = usage.providerReasoningTokens,
            )
        val archived =
            OperitArchivedChat.fromChatHistory(
                history = history,
                messages =
                    listOf(
                        OperitArchivedMessage(
                            baseMessage = history.messages.single(),
                            variants =
                                listOf(
                                    OperitArchivedMessageVariant(
                                        variantIndex = 1,
                                        content = "variant",
                                        providerRequestCount = usage.requestCount,
                                        providerUsageRequestCount =
                                            usage.providerUsageRequestCount,
                                        providerCacheMetricRequestCount =
                                            usage.providerCacheMetricRequestCount,
                                        providerCacheMetricPromptTokens =
                                            usage.providerCacheMetricPromptTokens,
                                        providerTotalInputTokens =
                                            usage.providerTotalInputTokens,
                                        providerUncachedInputTokens =
                                            usage.providerUncachedInputTokens,
                                        providerCacheReadTokens =
                                            usage.providerCacheReadTokens,
                                        providerCacheWriteTokens =
                                            usage.providerCacheWriteTokens,
                                        providerOutputTokens = usage.providerOutputTokens,
                                        providerReasoningTokens =
                                            usage.providerReasoningTokens,
                                    )
                                ),
                        )
                    ),
                conversationAudit = null,
            )

        val json = Json { encodeDefaults = true }
        val decoded =
            json.decodeFromString<OperitArchivedChat>(
                json.encodeToString(archived),
            )

        assertEquals(archived, decoded)
        assertEquals(usage.providerCacheReadTokens, decoded.providerCacheReadTokens)
        assertEquals(
            usage.providerReasoningTokens,
            decoded.messages.single().variants.single().providerReasoningTokens,
        )
    }
}
