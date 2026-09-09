package com.ai.assistance.operit.api.chat.llmprovider

import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OpenAIResponsesPromptCacheKeyTest {
    private fun provider() = OpenAIResponsesProvider(
        responsesApiEndpoint = "https://api.openai.com/v1/responses",
        apiKeyProvider = object : ApiKeyProvider {
            override suspend fun getApiKey() = "local-test-key"
            override suspend fun getCandidateKeyCount() = 1
        },
        modelName = "gpt-5.6", client = OkHttpClient(),
    )
    private fun message(role: String, text: String) = JSONObject().put("role", role).put("content", text)

    @Test fun firstRequestAndToolFollowupKeepTheSameCacheRoutingKey() {
        val provider = provider()
        val initial = JSONArray().put(message("system", "stable instructions")).put(message("user", "run tool"))
        val followup = JSONArray(initial.toString()).put(message("assistant", "tool call")).put(message("tool", "result"))
        assertEquals(provider.buildPromptCacheKey(initial, null, "v1"), provider.buildPromptCacheKey(followup, null, "v1"))
    }

    @Test fun dynamicMessagesDoNotFragmentStablePrefixRouting() {
        val provider = provider()
        assertEquals(provider.buildPromptCacheKey(JSONArray().put(message("user", "first")), null, "v1"),
            provider.buildPromptCacheKey(JSONArray().put(message("user", "second")), null, "v1"))
    }

    @Test fun changedSystemToolsAndNamespaceRemainDistinct() {
        val provider = provider()
        val messages = JSONArray().put(message("system", "stable"))
        val key = provider.buildPromptCacheKey(messages, null, "v1")
        assertNotEquals(key, provider.buildPromptCacheKey(JSONArray().put(message("system", "changed")), null, "v1"))
        assertNotEquals(key, provider.buildPromptCacheKey(messages, "[]", "v1"))
        assertNotEquals(key, provider.buildPromptCacheKey(messages, null, "v2"))
    }
}
