package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderUsageSnapshotTest {
    private fun providerSnapshot(
        input: Long,
        cached: Long,
        output: Long = 0L,
        cacheMetricState: ProviderCacheMetricState = ProviderCacheMetricState.REPORTED,
    ): ProviderUsageSnapshot =
        ProviderUsageSnapshot(
            providerModel = "DEEPSEEK:deepseek-chat",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            totalInputTokens = input + cached,
            uncachedInputTokens = input,
            cacheReadTokens = cached,
            cacheWriteTokens = 0L,
            outputTokens = output,
            reasoningTokens = 0L,
            cacheMetricState = cacheMetricState,
            source = ProviderUsageSource.PROVIDER,
        )

    @Test
    fun `same hop replaces terminal usage instead of double counting`() {
        val accumulator = ProviderUsageAccumulator()
        val first = providerSnapshot(input = 32L, cached = 8L, output = 4L)
        val terminal = providerSnapshot(input = 32L, cached = 8L, output = 5L)

        assertTrue(accumulator.record("hop-1", first))
        assertFalse(accumulator.record("hop-1", first))
        assertTrue(accumulator.record("hop-1", terminal))

        val aggregate = accumulator.aggregate()
        assertEquals(1, aggregate.requestCount)
        assertEquals(1, aggregate.providerUsageRequestCount)
        assertEquals(40L, aggregate.providerTotalInputTokens)
        assertEquals(8L, aggregate.providerCacheReadTokens)
        assertEquals(5L, aggregate.providerOutputTokens)
        assertEquals(0.2, aggregate.cacheHitRate!!, 0.000001)
    }

    @Test
    fun `unavailable and local estimates never become provider cache hits`() {
        val accumulator = ProviderUsageAccumulator()
        val local = ProviderUsageSnapshot(
            providerModel = "OPENAI_GENERIC:model",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            totalInputTokens = 100L,
            uncachedInputTokens = 100L,
            cacheReadTokens = 0L,
            cacheWriteTokens = 0L,
            outputTokens = 4L,
            reasoningTokens = 0L,
            cacheMetricState = ProviderCacheMetricState.NOT_REPORTED,
            source = ProviderUsageSource.LOCAL_ESTIMATE,
        )

        assertTrue(accumulator.record("hop-local", local))
        assertTrue(
            accumulator.record(
                "hop-unavailable",
                ProviderUsageSnapshot.unavailable(
                    providerModel = "ANTHROPIC:claude",
                    protocol = ApiProtocol.ANTHROPIC_MESSAGES,
                )
            )
        )

        val aggregate = accumulator.aggregate()
        assertEquals(2, aggregate.requestCount)
        assertEquals(0, aggregate.providerUsageRequestCount)
        assertNull(aggregate.cacheHitRate)
    }

    @Test
    fun `reported zero prompt is known zero instead of unknown`() {
        val accumulator = ProviderUsageAccumulator()
        assertTrue(
            accumulator.record(
                "hop-zero",
                providerSnapshot(
                    input = 0L,
                    cached = 0L,
                    cacheMetricState = ProviderCacheMetricState.REPORTED,
                )
            )
        )

        val aggregate = accumulator.aggregate()
        assertEquals(1, aggregate.providerCacheMetricRequestCount)
        assertEquals(0.0, aggregate.cacheHitRate!!, 0.000001)
    }

    @Test
    fun `cache writes remain prompt misses in the hit rate denominator`() {
        val accumulator = ProviderUsageAccumulator()
        assertTrue(
            accumulator.record(
                "hop-anthropic",
                ProviderUsageSnapshot(
                    providerModel = "ANTHROPIC:claude",
                    protocol = ApiProtocol.ANTHROPIC_MESSAGES,
                    totalInputTokens = 100L,
                    uncachedInputTokens = 20L,
                    cacheReadTokens = 60L,
                    cacheWriteTokens = 20L,
                    outputTokens = 4L,
                    reasoningTokens = 0L,
                    cacheMetricState = ProviderCacheMetricState.REPORTED,
                    source = ProviderUsageSource.PROVIDER,
                )
            )
        )

        val aggregate = accumulator.aggregate()
        assertEquals(100L, aggregate.providerPromptTokens)
        assertEquals(0.6, aggregate.cacheHitRate!!, 0.000001)
    }
}
