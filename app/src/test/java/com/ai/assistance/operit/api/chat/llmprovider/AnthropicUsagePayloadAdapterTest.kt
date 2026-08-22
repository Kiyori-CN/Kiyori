package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnthropicUsagePayloadAdapterTest {
    @Test
    fun `native usage keeps uncached read and write buckets disjoint`() {
        val parsed =
            AnthropicUsagePayloadAdapter.parse(
                JSONObject()
                    .put("input_tokens", 20)
                    .put("cache_read_input_tokens", 60)
                    .put("cache_creation_input_tokens", 20)
                    .put("output_tokens", 7)
            )

        requireNotNull(parsed)
        assertEquals(20, parsed.uncachedInputTokens)
        assertEquals(60, parsed.cachedInputTokens)
        assertEquals(20, parsed.cacheCreationInputTokens)
        assertEquals(7, parsed.outputTokens)
        assertEquals(
            ProviderCacheMetricState.REPORTED,
            parsed.cacheMetricState,
        )
    }

    @Test
    fun `message delta output does not invent an input sample`() {
        val parsed =
            AnthropicUsagePayloadAdapter.parse(
                JSONObject().put("output_tokens", 9)
            )

        requireNotNull(parsed)
        assertNull(parsed.uncachedInputTokens)
        assertNull(parsed.cachedInputTokens)
        assertNull(parsed.cacheCreationInputTokens)
        assertEquals(9, parsed.outputTokens)
        assertEquals(
            ProviderCacheMetricState.NOT_REPORTED,
            parsed.cacheMetricState,
        )
    }

    @Test
    fun `openai shaped prompt total subtracts cache read and write`() {
        val parsed =
            AnthropicUsagePayloadAdapter.parse(
                JSONObject()
                    .put("prompt_tokens", 100)
                    .put("cached_tokens", 60)
                    .put("cache_creation_input_tokens", 20)
            )

        requireNotNull(parsed)
        assertEquals(20, parsed.uncachedInputTokens)
        assertEquals(60, parsed.cachedInputTokens)
        assertEquals(20, parsed.cacheCreationInputTokens)
        assertEquals(
            ProviderCacheMetricState.REPORTED,
            parsed.cacheMetricState,
        )
    }
}
