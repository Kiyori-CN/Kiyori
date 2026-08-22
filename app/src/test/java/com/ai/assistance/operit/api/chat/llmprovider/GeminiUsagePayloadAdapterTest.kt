package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiUsagePayloadAdapterTest {
    @Test
    fun `prompt total includes cached content while output includes thoughts`() {
        val parsed =
            GeminiUsagePayloadAdapter.parse(
                JSONObject()
                    .put("promptTokenCount", 100)
                    .put("cachedContentTokenCount", 75)
                    .put("candidatesTokenCount", 12)
                    .put("thoughtsTokenCount", 8)
                    .put("totalTokenCount", 120)
            )

        requireNotNull(parsed)
        assertEquals(100, parsed.totalInputTokens)
        assertEquals(25, parsed.uncachedInputTokens)
        assertEquals(75, parsed.cachedInputTokens)
        assertEquals(20, parsed.outputTokens)
        assertEquals(8, parsed.reasoningTokens)
        assertEquals(
            ProviderCacheMetricState.REPORTED,
            parsed.cacheMetricState,
        )
    }

    @Test
    fun `missing cached field remains unknown rather than reported zero`() {
        val parsed =
            GeminiUsagePayloadAdapter.parse(
                JSONObject()
                    .put("promptTokenCount", 10)
                    .put("candidatesTokenCount", 2)
            )

        requireNotNull(parsed)
        assertNull(parsed.cachedInputTokens)
        assertEquals(
            ProviderCacheMetricState.NOT_REPORTED,
            parsed.cacheMetricState,
        )
    }

    @Test
    fun `cached count above prompt total is invalid and bounded`() {
        val parsed =
            GeminiUsagePayloadAdapter.parse(
                JSONObject()
                    .put("promptTokenCount", 10)
                    .put("cachedContentTokenCount", 20)
            )

        requireNotNull(parsed)
        assertEquals(10, parsed.cachedInputTokens)
        assertEquals(0, parsed.uncachedInputTokens)
        assertEquals(
            ProviderCacheMetricState.INVALID,
            parsed.cacheMetricState,
        )
    }
}
