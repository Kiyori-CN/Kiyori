package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.util.exceptions.UserCancellationException
import com.kiyori.platform.logging.KiyoriLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NativeProtocolAttemptBoundaryTest {
    private fun context(): Context = mock<Context>().also {
        whenever(it.getString(any())).thenReturn("provider error")
        whenever(it.getString(any(), any())).thenReturn("provider error")
    }

    private fun gemini(server: MockWebServer) = GeminiProvider(
        server.url("/relay/v1beta").toString(), SingleApiKeyProvider("test"), "gemini-2.5-flash",
        OkHttpClient.Builder().retryOnConnectionFailure(false).build()
    ).also { it.executionDispatcher = Dispatchers.Unconfined }

    private fun claude(server: MockWebServer) = ClaudeProvider(
        server.url("/v1/messages").toString(), SingleApiKeyProvider("test"), "claude-sonnet-4-5",
        OkHttpClient.Builder().retryOnConnectionFailure(false).build()
    ).also { it.executionDispatcher = Dispatchers.Unconfined }

    private val history = listOf(PromptTurn(PromptTurnKind.USER, "hello"))

    @Test fun nativeProtocolCancellationDoesNotSubmitAReplacementRequest() = runTest {
        Mockito.mockStatic(KiyoriLogger::class.java).use {
            for (isGemini in listOf(false, true)) MockWebServer().use { server ->
                server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                    if (isGemini) "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"answer\"}]},\"finishReason\":\"STOP\"}]}\n\n"
                    else anthropicEvents("end_turn")))
                server.start()
                val provider: AIService = if (isGemini) gemini(server) else claude(server)
                val failure = runCatching {
                    provider.sendMessage(context(), history, emptyList(), false, true, null,
                        providerRequestContext = null).collect { throw UserCancellationException("cancel") }
                }.exceptionOrNull()
                assertTrue(failure is kotlinx.coroutines.CancellationException)
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test fun geminiNonStreamingReturnsTheSameTextAndUsageWithoutSseQuery() = runTest {
        Mockito.mockStatic(KiyoriLogger::class.java).use {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"answer"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":2}}"""))
                server.start()
                val provider = gemini(server)
                val chunks = mutableListOf<String>()
                provider.sendMessage(context(), history, emptyList(), false, false, null,
                    providerRequestContext = null).collect { chunks += it }
                assertEquals(listOf("answer"), chunks)
                assertEquals(10L, provider.consumeLatestProviderUsageSnapshot()!!.totalInputTokens)
                assertEquals("/relay/v1beta/models/gemini-2.5-flash:generateContent", server.takeRequest().path)
            }
        }
    }

    @Test fun geminiSseSupportsMultilineDataBomAndUsageOnlyEvents() = runTest {
        Mockito.mockStatic(KiyoriLogger::class.java).use {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody(
                    "\uFEFF: heartbeat\n\ndata:{\ndata: \"candidates\":[{\"content\":{\"parts\":[{\"text\":\"answer { ok }\"}]}}]}\n\n" +
                    "data: {\"usageMetadata\":{\"promptTokenCount\":20,\"cachedContentTokenCount\":12,\"candidatesTokenCount\":2}}\n\n" +
                    "data: {\"candidates\":[{\"finishReason\":\"STOP\"}]}\n\n"))
                server.start()
                val provider = gemini(server)
                val result = StringBuilder()
                provider.sendMessage(context(), history, emptyList(), false, true, null,
                    providerRequestContext = null).collect { result.append(it) }
                assertEquals("answer { ok }", result.toString())
                val request = server.takeRequest()
                assertEquals("/relay/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse", request.path)
                assertEquals("test", request.getHeader("x-goog-api-key"))
                assertFalse(request.path!!.contains("key="))
                val usage = provider.consumeLatestProviderUsageSnapshot()!!
                assertEquals(20L, usage.totalInputTokens)
                assertEquals(12L, usage.cacheReadTokens)
                assertEquals(2L, usage.outputTokens)
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test fun geminiMissingTerminalBlockedTruncatedAndMalformedResponsesFailWithoutRetry() = runTest {
        Mockito.mockStatic(KiyoriLogger::class.java).use {
            val payloads = listOf(
                """{"candidates":[{"content":{"parts":[{"text":"partial"}]}}]}""",
                """{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"partial"}]}}]}""",
                """{"candidates":[{"finishReason":"SAFETY"}]}""",
                """{"promptFeedback":{"blockReason":"SAFETY"}}""",
                "{broken"
            )
            for (stream in listOf(false, true)) for (payload in payloads) {
                MockWebServer().use { server ->
                    server.enqueue(MockResponse().setBody(if (stream) "data: $payload\n\n" else payload))
                    server.start()
                    var retries = 0
                    val error = runCatching {
                        gemini(server).sendMessage(context(), history, emptyList(), false, stream, null,
                            providerRequestContext = null, onNonFatalError = { retries++ }).collect {}
                    }.exceptionOrNull()
                    assertNotNull("$stream $payload", error)
                    assertEquals(1, server.requestCount)
                    assertEquals(0, retries)
                    assertFalse(error is UserCancellationException)
                }
            }
        }
    }

    private fun anthropicEvents(reason: String?): String {
        val delta = JSONObject().put("type", "message_delta")
            .put("delta", JSONObject().apply { if (reason != null) put("stop_reason", reason) })
        return "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":3}}}\n\n" +
            "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n" +
            "data: {\ndata:\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"answer\"}}\n\n" +
            "data: {\"type\":\"content_block_stop\",\"index\":0}\n\n" +
            "data: $delta\n\n" +
            "data: {\"type\":\"message_stop\"}\n\n"
    }

    @Test fun anthropicGenerationReasonIsValidatedInBothDeliveryModes() = runTest {
        Mockito.mockStatic(KiyoriLogger::class.java).use {
            for (stream in listOf(false, true)) for (reason in listOf("end_turn", "max_tokens", "pause_turn", null)) {
                MockWebServer().use { server ->
                    val payload = JSONObject().put("type", "message")
                        .put("content", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", "answer")))
                    if (reason != null) payload.put("stop_reason", reason)
                    server.enqueue(MockResponse().setHeader("Content-Type", if (stream) "text/event-stream" else "application/json")
                        .setBody(if (stream) anthropicEvents(reason) else payload.toString()))
                    server.start()
                    val output = StringBuilder()
                    var retries = 0
                    val error = runCatching {
                        claude(server).sendMessage(context(), history, emptyList(), false, stream, null,
                            providerRequestContext = null, onNonFatalError = { retries++ }).collect { output.append(it) }
                    }.exceptionOrNull()
                    if (reason == "end_turn") {
                        assertNull(error)
                        assertTrue(output.toString().startsWith("answer"))
                    } else {
                        assertNotNull("$stream $reason", error)
                        assertTrue(error!!.message.orEmpty().contains("did not complete"))
                    }
                    assertEquals(1, server.requestCount)
                    assertEquals(0, retries)
                }
            }
        }
    }
}
