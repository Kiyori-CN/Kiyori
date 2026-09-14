package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CrossProtocolReasoningTest {
    @Test fun knownNonReasoningAndFourLevelGptModelsUseTheirActualCapabilities() {
        for (model in listOf("gpt-4.1", "gpt-4.1-mini", "gpt-4o", "gpt-4o-mini", "gpt-3.5-turbo")) {
            for (enabled in listOf(false, true)) {
                assertNull(ModelRequestCompiler.compile(profile(model = model), UserExecutionIntent(enabled, 5)).reasoningEffort)
            }
        }
        for (model in listOf("gpt-5.4", "gpt-5.5", "gpt-5.5-2026-04-23")) {
            assertEquals(listOf("low", "medium", "high", "xhigh", "xhigh"), (1..5).map {
                ModelRequestCompiler.compile(profile(model = model), UserExecutionIntent(true, it)).reasoningEffort!!.wireValue
            })
        }
    }

    @Test fun deepSeekPlainReasoningReplaysBeforeCallsAndDoesNotModifySourceOrLeakToOpenAi() {
        val messages = JSONArray("""[
          {"role":"user","content":"read"},
          {"role":"assistant","content":"<think> reasoning\n</think>checking","tool_calls":[{"id":"call_a","type":"function","function":{"name":"read_file","arguments":"{}"}}]},
          {"role":"tool","tool_call_id":"call_a","content":"file data"},
          {"role":"assistant","content":"<think>done</think>answer"},
          {"role":"user","content":"continue"}]
        """)
        val original = messages.toString()
        val source = JSONObject().put("messages", messages)
        val input = OpenAIResponsesPayloadAdapter.toResponsesRequest(source, true).getJSONArray("input")
        assertEquals("reasoning", input.getJSONObject(1).getString("type"))
        assertEquals(" reasoning\n", input.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("text"))
        assertEquals("checking", input.getJSONObject(2).getString("content"))
        assertEquals("call_a", input.getJSONObject(3).getString("call_id"))
        assertEquals("function_call_output", input.getJSONObject(4).getString("type"))
        assertEquals("reasoning", input.getJSONObject(5).getString("type"))
        assertEquals(original, messages.toString())
        val openai = OpenAIResponsesPayloadAdapter.toResponsesRequest(source).getJSONArray("input")
        assertFalse((0 until openai.length()).any { openai.getJSONObject(it).optString("type") == "reasoning" })
    }

    @Test fun deepSeekNonStreamingReasoningContentIsNotLostAndTerminalMustBeComplete() {
        val response = JSONObject("""{"status":"completed","output":[{"type":"reasoning","content":[{"type":"reasoning_text","text":"reason"}],"summary":[]},{"type":"message","content":[{"type":"output_text","text":"answer"}]}]}""")
        val parsed = OpenAIResponsesPayloadAdapter.parseNonStreamingResponse(response)
        assertEquals(listOf("reason"), parsed.reasoningChunks)
        assertEquals(listOf("answer"), parsed.textChunks)
        OpenAIResponseTerminalPolicy.requireComplete(response, true)
        for (status in listOf("incomplete", "failed", "cancelled", "in_progress", "")) {
            response.put("status", status)
            assertThrows(IllegalArgumentException::class.java) { OpenAIResponseTerminalPolicy.requireComplete(response, true) }
        }
    }

    private fun profile(responses: Boolean = true, official: Boolean = true, model: String = "gpt-6-astra") =
        ModelCapabilityResolver.resolve(
            if (responses) ApiProviderType.OPENAI_RESPONSES else ApiProviderType.OPENAI,
            model,
            "https://${if (official) "api.openai.com" else "relay.example"}/v1/${if (responses) "responses" else "chat/completions"}",
        )

    @Test fun astraAllLevelsAndProtocolsCompileWithoutInventingUltraOrNone() {
        for (responses in listOf(false, true)) for (official in listOf(false, true)) {
            val p = profile(responses, official)
            assertEquals(listOf("low", "medium", "high", "xhigh", "max"), (1..5).map {
                ModelRequestCompiler.compile(p, UserExecutionIntent(true, it)).reasoningEffort!!.wireValue
            })
            assertThrows(IllegalArgumentException::class.java) {
                ModelRequestCompiler.compile(p, UserExecutionIntent(false, 3))
            }
            val compiled = ModelRequestCompiler.compile(p, UserExecutionIntent(true, 4))
            assertEquals(responses, compiled.reasoningSummaryEnabled)
            assertEquals(responses && official, compiled.background)
            assertEquals(responses && official, compiled.encryptedReasoningContentEnabled)
        }
        assertEquals(3, profile(model = "gpt-6-astra-unverified").qualityLevelToReasoningEffort.distinct().size)
    }

    @Test fun astraRemovesUnsupportedSamplingAndPreservesUnrelatedIncludeAndCacheMode() {
        val request = JSONObject("""{"temperature":1,"top_p":0.9,"top_logprobs":2,"logprobs":true,
            "include":["message.output_text.logprobs","web_search_call.action.sources"],
            "prompt_cache_retention":"24h","prompt_cache_options":{"mode":"explicit"}}""")
        OpenAIResponsesRequestFeatureCompiler.apply(request,
            ModelRequestCompiler.compile(profile(), UserExecutionIntent(true, 5)), true)
        for (key in listOf("temperature", "top_p", "top_logprobs", "logprobs", "prompt_cache_retention")) {
            assertFalse(key, request.has(key))
        }
        assertEquals("30m", request.getJSONObject("prompt_cache_options").getString("ttl"))
        assertEquals("explicit", request.getJSONObject("prompt_cache_options").getString("mode"))
        assertEquals("auto", request.getJSONObject("reasoning").getString("summary"))
        assertEquals("max", request.getJSONObject("reasoning").getString("effort"))
        assertEquals("web_search_call.action.sources", request.getJSONArray("include").getString(0))
        assertEquals("reasoning.encrypted_content", request.getJSONArray("include").getString(1))
    }

    @Test fun astraChatRejectsNativeToolsBeforeSubmissionButAllowsPlainChat() {
        val p = profile(responses = false)
        val request = JSONObject().put("tools", JSONArray().put(JSONObject().put("type", "function")))
        assertThrows(IllegalArgumentException::class.java) { OpenAIModelRequestConstraints.apply(request, p) }
        val plain = JSONObject().put("max_tokens", 2000)
        OpenAIModelRequestConstraints.apply(plain, p)
        assertEquals(2000, plain.getInt("max_completion_tokens"))
        assertFalse(plain.has("max_tokens"))
    }

    @Test fun relayDoesNotAcquireOfficialCacheOrExecutionFields() {
        val request = JSONObject()
        OpenAIResponsesRequestFeatureCompiler.apply(request,
            ModelRequestCompiler.compile(profile(official = false), UserExecutionIntent(true, 4)), true)
        assertEquals("auto", request.getJSONObject("reasoning").getString("summary"))
        for (key in listOf("background", "store", "include", "prompt_cache_options")) assertFalse(request.has(key))
    }

    @Test fun deepSeekAnthropicAndResponsesUseTheSameEffortAndExplicitOff() {
        val p = ModelCapabilityResolver.resolve(ApiProviderType.OPENAI_RESPONSES_GENERIC,
            "deepseek-flash", "https://api.deepseek.com/responses", ApiProviderType.DEEPSEEK)
        for (enabled in listOf(false, true)) for (level in 1..5) {
            val anthropic = JSONObject()
            DeepSeekAnthropicReasoningCompiler.apply(anthropic, enabled, level)
            val responses = JSONObject()
            OpenAIResponsesRequestFeatureCompiler.apply(responses,
                ModelRequestCompiler.compile(p, UserExecutionIntent(enabled, level)), true)
            assertEquals(if (enabled) "enabled" else "disabled", anthropic.getJSONObject("thinking").getString("type"))
            assertEquals(if (enabled) DeepSeekReasoningPolicy.effort(level) else "none",
                responses.getJSONObject("reasoning").getString("effort"))
            if (enabled) assertEquals(DeepSeekReasoningPolicy.effort(level), anthropic.getJSONObject("output_config").getString("effort"))
            else assertFalse(anthropic.has("output_config"))
            for (key in listOf("background", "store", "include", "prompt_cache_options")) assertFalse(responses.has(key))
        }
    }

    @Test fun cacheWritesAreDisjointFromUncachedInputAndOverflowIsMarkedInvalid() {
        val usage = JSONObject("""{"input_tokens":1000,"input_tokens_details":{"cached_tokens":600,"cache_write_tokens":300},"output_tokens":50}""")
        val parsed = OpenAIResponsesPayloadAdapter.parseUsageCounts(usage)!!
        assertEquals(100, parsed.actualInputTokens)
        assertEquals(600, parsed.cachedInputTokens)
        assertEquals(300, parsed.cacheWriteTokens)
        assertEquals(ProviderCacheMetricState.REPORTED, parsed.cacheMetricState)
        usage.getJSONObject("input_tokens_details").put("cache_write_tokens", Int.MAX_VALUE)
        val invalid = OpenAIResponsesPayloadAdapter.parseUsageCounts(usage)!!
        assertEquals(ProviderCacheMetricState.INVALID, invalid.cacheMetricState)
        assertEquals(invalid.totalInputTokens, invalid.actualInputTokens + invalid.cachedInputTokens + invalid.cacheWriteTokens)
    }
}
