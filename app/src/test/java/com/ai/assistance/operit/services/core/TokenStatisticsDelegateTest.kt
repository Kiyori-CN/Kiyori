package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ProviderUsageAggregate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TokenStatisticsDelegateTest {
    private lateinit var scope: CoroutineScope
    private lateinit var delegate: TokenStatisticsDelegate

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        delegate = TokenStatisticsDelegate(scope) { null }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun providerUsageIsIsolatedByActiveChatAndRestoredOnSwitch() {
        val chatA =
            ProviderUsageAggregate(
                requestCount = 2,
                providerUsageRequestCount = 2,
                providerCacheMetricRequestCount = 1,
                providerCacheMetricPromptTokens = 100L,
                providerTotalInputTokens = 100L,
                providerUncachedInputTokens = 40L,
                providerCacheReadTokens = 60L,
                providerOutputTokens = 20L,
            )
        val chatB =
            ProviderUsageAggregate(
                requestCount = 1,
                providerUsageRequestCount = 1,
                providerCacheMetricRequestCount = 1,
                providerCacheMetricPromptTokens = 50L,
                providerTotalInputTokens = 50L,
                providerUncachedInputTokens = 10L,
                providerCacheReadTokens = 40L,
                providerOutputTokens = 8L,
            )

        delegate.setActiveChatId("chat-a")
        delegate.setProviderUsageAggregate("chat-a", chatA)
        delegate.setProviderUsageAggregate("chat-b", chatB)
        assertEquals(chatA, delegate.cumulativeProviderUsageFlow.value)

        delegate.setActiveChatId("chat-b")
        assertEquals(chatB, delegate.cumulativeProviderUsageFlow.value)
        assertEquals(chatA, delegate.getCumulativeProviderUsage("chat-a"))
    }
}
