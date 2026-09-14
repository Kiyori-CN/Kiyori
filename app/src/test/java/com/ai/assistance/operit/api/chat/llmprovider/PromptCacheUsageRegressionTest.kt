package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProtocol
import com.ai.assistance.operit.data.model.ApiProviderType
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PromptCacheUsageRegressionTest {
    @Test fun partialUsageDoesNotBecomeCompleteProviderCoverage() {
        val partial = ProviderUsageSnapshot("model", ApiProtocol.ANTHROPIC_MESSAGES,
            100, 20, 80, 0, 0, 0, ProviderCacheMetricState.REPORTED, ProviderUsageSource.PROVIDER,
            outputTokensReported = false)
        assertEquals(0, partial.toProviderUsageAggregate().providerUsageRequestCount)
        assertEquals(1, partial.toProviderUsageAggregate().requestCount)
        assertNull(partial.toProviderUsageAggregate().cacheHitRate)
    }

    @Test fun protocolAdaptersRejectMalformedAndFractionalCountsAndDoNotDoubleCountCreation() {
        for (raw in listOf("1.25", "2147483648", "\"bad\"")) {
            val invalidOutput = AnthropicUsagePayloadAdapter.parse(JSONObject("""{"input_tokens":20,"output_tokens":$raw}"""))!!
            assertNull(invalidOutput.outputTokens)
            val claude = AnthropicUsagePayloadAdapter.parse(JSONObject("""{"input_tokens":20,"cache_read_input_tokens":$raw}"""))!!
            assertEquals(ProviderCacheMetricState.INVALID, claude.cacheMetricState)
            val gemini = GeminiUsagePayloadAdapter.parse(JSONObject("""{"promptTokenCount":20,"cachedContentTokenCount":$raw}"""))!!
            assertEquals(ProviderCacheMetricState.INVALID, gemini.cacheMetricState)
            val toolPkg = ToolPkgUsagePayloadAdapter.parse(JSONObject("""{"input":20,"cachedInput":$raw}"""))!!
            assertEquals(ProviderCacheMetricState.INVALID, toolPkg.cacheMetricState)
        }
        val creation = AnthropicUsagePayloadAdapter.parse(JSONObject("""{"input_tokens":10,"cache_creation":{"ephemeral_5m_input_tokens":20,"ephemeral_1h_input_tokens":30,"total":50},"output_tokens":1}"""))!!
        assertEquals(50, creation.cacheCreationInputTokens)
        assertNull(GeminiUsagePayloadAdapter.parse(JSONObject("""{"promptTokenCount":null,"cachedContentTokenCount":null}""")))
        assertNull(AnthropicUsagePayloadAdapter.parse(JSONObject("""{"input_tokens":null,"cache_read_input_tokens":null}""")))
    }
    @Test fun deepseekResponsesPreservesSystemRoleInsteadOfDowngradingToUser() {
        val source = JSONObject("""{"messages":[{"role":"system","content":"system rules"},{"role":"user","content":"hello"}]}""")
        val deepseek = OpenAIResponsesPayloadAdapter.toResponsesRequest(source, plainReasoningReplay = true)
        assertEquals("system", deepseek.getJSONArray("input").getJSONObject(0).getString("role"))
        val openai = OpenAIResponsesPayloadAdapter.toResponsesRequest(source)
        assertEquals("developer", openai.getJSONArray("input").getJSONObject(0).getString("role"))
    }
    private fun profile(official: Boolean = true, model: String = "gpt-6-astra") = ModelCapabilityResolver.resolve(
        ApiProviderType.OPENAI_RESPONSES, model,
        if (official) "https://api.openai.com/v1/responses" else "https://relay.example/v1/responses",
    )

    @Test fun invalidSamplesCannotPolluteValidCacheRates() {
        fun sample(state: ProviderCacheMetricState) = ProviderUsageSnapshot("model", ApiProtocol.OPENAI_RESPONSES,
            100, 20, 80, 0, 10, 0, state, ProviderUsageSource.PROVIDER)
        val accumulator = ProviderUsageAccumulator()
        accumulator.record("valid", sample(ProviderCacheMetricState.REPORTED))
        accumulator.record("invalid", sample(ProviderCacheMetricState.INVALID))
        accumulator.record("missing", sample(ProviderCacheMetricState.NOT_REPORTED))
        val total = accumulator.aggregate()
        assertEquals(300, total.providerTotalInputTokens)
        assertEquals(80, total.providerCacheReadTokens)
        assertEquals(220, total.providerUncachedInputTokens)
        assertEquals(0.8, total.cacheHitRate!!, 0.000001)
        accumulator.record("invalid", sample(ProviderCacheMetricState.REPORTED))
        assertEquals(160, accumulator.aggregate().providerCacheReadTokens)
        accumulator.clear()
        assertEquals(0, accumulator.aggregate().requestCount)
    }

    @Test fun hugeCountersSaturateWithoutCrashingOrWrapping() {
        val sample = ProviderUsageSnapshot("model", ApiProtocol.OPENAI_RESPONSES,
            Long.MAX_VALUE, Long.MAX_VALUE, 0, 0, Long.MAX_VALUE, 0,
            ProviderCacheMetricState.REPORTED, ProviderUsageSource.PROVIDER)
        val accumulator = ProviderUsageAccumulator()
        repeat(3) { accumulator.record("$it", sample) }
        assertEquals(Long.MAX_VALUE, accumulator.aggregate().providerTotalTokens)
    }

    @Test fun malformedNullOverflowAndDeepseekContradictionsAreNotHits() {
        for (raw in listOf("-1", "2147483648", "1.5", "\"bad\"")) {
            val parsed = OpenAIResponsesPayloadAdapter.parseUsageCounts(JSONObject(
                """{"input_tokens":100,"input_tokens_details":{"cached_tokens":$raw},"output_tokens":5}"""))!!
            assertEquals(ProviderCacheMetricState.INVALID, parsed.cacheMetricState)
        }
        val absent = OpenAIResponsesPayloadAdapter.parseUsageCounts(JSONObject(
            """{"input_tokens":100,"input_tokens_details":{"cached_tokens":null}}"""))!!
        assertEquals(ProviderCacheMetricState.NOT_REPORTED, absent.cacheMetricState)
        assertNull(OpenAIResponsesPayloadAdapter.parseUsageCounts(JSONObject("""{"input_tokens":4294967296}""")))
        val mismatch = OpenAIResponsesPayloadAdapter.parseUsageCounts(JSONObject(
            """{"prompt_tokens":100,"prompt_cache_hit_tokens":80,"prompt_cache_miss_tokens":40}"""))!!
        assertEquals(ProviderCacheMetricState.INVALID, mismatch.cacheMetricState)
        val derived = OpenAIResponsesPayloadAdapter.parseUsageCounts(JSONObject(
            """{"prompt_cache_hit_tokens":80,"prompt_cache_miss_tokens":20,"completion_tokens":5}"""))!!
        assertEquals(100, derived.totalInputTokens)
        assertEquals(20, derived.actualInputTokens)
        assertEquals(ProviderCacheMetricState.REPORTED, derived.cacheMetricState)
        val invalidReasoning = OpenAIResponsesPayloadAdapter.parseUsageCounts(JSONObject(
            """{"input_tokens":100,"output_tokens":5,"output_tokens_details":{"reasoning_tokens":8}}"""))!!
        assertEquals(0, invalidReasoning.reasoningTokens)
        assertFalse(invalidReasoning.reasoningTokensReported)
    }

    @Test fun stablePrefixMarkerPreservesContentAndDoesNotLeakToRelayOrOlderModels() {
        val source = """{"input":[{"role":"developer","content":"stable instructions"},{"role":"user","content":"dynamic"}]}"""
        val request = JSONObject(source)
        OpenAIPromptCachePolicy.markStablePrefix(request, profile())
        val content = request.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(0)
        assertEquals("stable instructions", content.getString("text"))
        assertEquals("explicit", content.getJSONObject("prompt_cache_breakpoint").getString("mode"))
        assertFalse(request.has("prompt_cache_options"))
        assertEquals("dynamic", request.getJSONArray("input").getJSONObject(1).getString("content"))
        for (p in listOf(profile(false), profile(model = "gpt-5.4"))) {
            val untouched = JSONObject(source)
            OpenAIPromptCachePolicy.markStablePrefix(untouched, p)
            assertTrue(untouched.getJSONArray("input").getJSONObject(0).get("content") is String)
        }
        val explicit = JSONObject(source).put("prompt_cache_options", JSONObject().put("mode", "explicit"))
        OpenAIPromptCachePolicy.markStablePrefix(explicit, profile())
        assertTrue(explicit.getJSONArray("input").getJSONObject(0).get("content") is String)
    }

    @Test fun chatUsageIsRequestedWithoutOverridingOptOutOrAddingResponsesOptions() {
        val chat = ModelCapabilityResolver.resolve(ApiProviderType.OPENAI, "gpt-5.4", "https://api.openai.com/v1/chat/completions")
        val request = JSONObject().put("stream", true)
        OpenAIPromptCachePolicy.requestChatUsage(request, chat)
        assertTrue(request.getJSONObject("stream_options").getBoolean("include_usage"))
        request.getJSONObject("stream_options").put("include_usage", false)
        OpenAIPromptCachePolicy.requestChatUsage(request, chat)
        assertFalse(request.getJSONObject("stream_options").getBoolean("include_usage"))
        val responses = JSONObject().put("stream", true)
        OpenAIPromptCachePolicy.requestChatUsage(responses, profile())
        assertFalse(responses.has("stream_options"))
        assertEquals(PromptCacheCapability.OPENAI_PROMPT_CACHE_KEY, profile(false).promptCache)
        assertEquals(ExecutionPersistenceCapability.RESPONSES_AT_MOST_ONCE, profile(false).executionPersistence)
    }
}
