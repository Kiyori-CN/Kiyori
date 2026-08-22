package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolPkgUsagePayloadAdapterTest {
    @Test
    fun parsesDisjointProviderUsageBuckets() {
        val usage =
            ToolPkgUsagePayloadAdapter.parse(
                JSONObject()
                    .put(
                        "usage",
                        JSONObject()
                            .put("inputTokens", 20)
                            .put("cachedInputTokens", 60)
                            .put("cacheWriteTokens", 10)
                            .put("outputTokens", 9)
                            .put("reasoningTokens", 4),
                    )
            )

        requireNotNull(usage)
        assertEquals(20, usage.input)
        assertEquals(60, usage.cachedInput)
        assertEquals(10, usage.cacheWrite)
        assertEquals(9, usage.output)
        assertEquals(4, usage.reasoning)
        assertEquals(ProviderCacheMetricState.REPORTED, usage.cacheMetricState)
    }

    @Test
    fun explicitZeroCacheMetricRemainsReported() {
        val usage =
            ToolPkgUsagePayloadAdapter.parse(
                JSONObject()
                    .put("input", 10)
                    .put("cachedInput", 0)
            )

        requireNotNull(usage)
        assertEquals(0, usage.cachedInput)
        assertEquals(ProviderCacheMetricState.REPORTED, usage.cacheMetricState)
    }

    @Test
    fun malformedOrNegativeCountsAreMarkedInvalidInsteadOfBecomingReportedZero() {
        val negative =
            requireNotNull(
                ToolPkgUsagePayloadAdapter.parse(
                    JSONObject()
                        .put("input", -5)
                        .put("cachedInput", -1)
                )
            )
        assertEquals(0, negative.input)
        assertEquals(0, negative.cachedInput)
        assertEquals(ProviderCacheMetricState.INVALID, negative.cacheMetricState)

        val malformed =
            requireNotNull(
                ToolPkgUsagePayloadAdapter.parse(
                    JSONObject().put("cachedInput", JSONObject())
                )
            )
        assertEquals(0, malformed.cachedInput)
        assertEquals(ProviderCacheMetricState.INVALID, malformed.cacheMetricState)
    }

    @Test
    fun absentUsageFieldsRemainUnavailable() {
        assertNull(ToolPkgUsagePayloadAdapter.parse(JSONObject().put("text", "answer")))
    }
}
