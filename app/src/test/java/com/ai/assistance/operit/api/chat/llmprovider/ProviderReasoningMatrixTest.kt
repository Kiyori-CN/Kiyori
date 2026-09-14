package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ApiProtocol
import com.kiyori.platform.logging.KiyoriLogger
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock

class ProviderReasoningMatrixTest {
    @Test fun nvidiaGptOssNeverReceivesUnsupportedMaxEffort() {
        val provider = NvidiaAIProvider("https://integrate.api.nvidia.com/v1/chat/completions",
            SingleApiKeyProvider("test"), "openai/gpt-oss-120b", OkHttpClient())
        assertEquals(listOf("low", "medium", "high", "high", "high"),
            (1..5).map(provider::gptOssEffortForQuality))
    }

    @Test fun localCancellationAndFailureCannotBeReportedAsSuccessfulGeneration() {
        for (provider in listOf("MNN", "llama.cpp")) {
            ProviderGenerationTerminalPolicy.requireLocalSuccess(provider, true, false)
            assertThrows(java.io.IOException::class.java) {
                ProviderGenerationTerminalPolicy.requireLocalSuccess(provider, false, false)
            }
            for (success in listOf(false, true)) {
                assertThrows(com.ai.assistance.operit.util.exceptions.UserCancellationException::class.java) {
                    ProviderGenerationTerminalPolicy.requireLocalSuccess(provider, success, true)
                }
            }
        }
    }

    @Test fun qwenOffIsPresentInTheActualWireRequestForBothDeliveryModes() {
        Mockito.mockStatic(KiyoriLogger::class.java).use {
            val provider = QwenAIProvider("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                SingleApiKeyProvider("test"), "qwen3-32b", OkHttpClient())
            val method = QwenAIProvider::class.java.getDeclaredMethod("createRequestBody",
                Context::class.java, List::class.java, List::class.java, Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType, List::class.java, Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            for (stream in listOf(false, true)) {
                val body = method.invoke(provider, mock<Context>(),
                    listOf(PromptTurn(PromptTurnKind.USER, "hello")), emptyList<Any>(), false, stream, null, false) as okhttp3.RequestBody
                val buffer = okio.Buffer()
                body.writeTo(buffer)
                val json = JSONObject(buffer.readUtf8())
                assertEquals(false, json.getBoolean("enable_thinking"))
                assertEquals(stream, json.getBoolean("stream"))
            }
        }
    }

    @Test fun everyRegisteredProviderAndProtocolHasAnExplicitRouteOrRejectsBeforeSubmission() {
        for (provider in ApiProviderType.entries) {
            val supported = ApiProviderConfigs.getSupportedProtocols(provider)
            assertTrue(provider.name, supported.isNotEmpty())
            for (protocol in ApiProtocol.entries) {
                if (protocol in supported) {
                    val route = ProtocolServiceRoutingPolicy.resolve(provider, protocol)
                    assertEquals(provider, route.identityProviderType)
                    if (protocol == ApiProtocol.OPENAI_RESPONSES) {
                        assertEquals(ProtocolServiceKind.OPENAI_RESPONSES, route.serviceKind)
                    }
                    if (protocol == ApiProtocol.ANTHROPIC_MESSAGES) {
                        assertEquals(ProtocolServiceKind.ANTHROPIC_MESSAGES, route.serviceKind)
                    }
                } else assertThrows(IllegalArgumentException::class.java) {
                    ProtocolServiceRoutingPolicy.resolve(provider, protocol)
                }
            }
        }
    }

    @Test fun geminiThinkingMatrixControlsReasoningRatherThanOnlySummaryVisibility() {
        val models = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro",
            "gemini-3-pro-preview", "gemini-3.1-pro-preview", "gemini-3-flash-preview",
            "gemini-3.8-flash", "gemini-3.1-flash-lite-image-preview")
        for (model in models) for (quality in 1..5) for (enabled in listOf(false, true)) {
            val config = JSONObject()
            if (!enabled && !(model.startsWith("gemini-2.5-flash"))) {
                assertThrows(IllegalArgumentException::class.java) {
                    GeminiReasoningCompiler.apply(config, model, enabled, quality)
                }
            } else {
                GeminiReasoningCompiler.apply(config, model, enabled, quality)
                val thinking = config.getJSONObject("thinkingConfig")
                assertEquals(enabled, thinking.getBoolean("includeThoughts"))
                if (!enabled) assertEquals(0, thinking.getInt("thinkingBudget"))
                else if (model.startsWith("gemini-2.5")) {
                    val ceiling = if (model.endsWith("pro")) 32768 else 24576
                    assertEquals(listOf(1024, 4096, 8192, 16384, ceiling)[quality - 1], thinking.getInt("thinkingBudget"))
                    assertFalse(thinking.has("thinkingLevel"))
                } else {
                    val expected = when {
                        model.contains("flash-lite-image") -> listOf("minimal", "minimal", "high", "high", "high")
                        model.startsWith("gemini-3-pro") -> listOf("low", "low", "high", "high", "high")
                        else -> listOf("low", "medium", "high", "high", "high")
                    }
                    assertEquals(expected[quality - 1], thinking.getString("thinkingLevel"))
                }
            }
        }
    }

    @Test fun geminiCustomControlsDoNotSilentlyConflictWithTheToggle() {
        val config = JSONObject("""{"thinkingConfig":{"thinkingBudget":4096}}""")
        assertThrows(IllegalArgumentException::class.java) {
            GeminiReasoningCompiler.apply(config, "gemini-2.5-flash", false, 1)
        }
        val legacy = JSONObject()
        GeminiReasoningCompiler.apply(legacy, "gemini-2.0-flash", true, 5)
        assertFalse(legacy.has("thinkingConfig"))
        for (custom in listOf("""{"thinkingLevel":"minimal"}""", """{"thinkingBudget":0}""",
            """{"thinkingBudget":4096,"thinkingLevel":"high"}""")) {
            assertThrows(IllegalArgumentException::class.java) {
                GeminiReasoningCompiler.apply(JSONObject().put("thinkingConfig", JSONObject(custom)), "gemini-3.8-flash", true, 1)
            }
        }
        val unknown = JSONObject()
        GeminiReasoningCompiler.apply(unknown, "gemini-3.99-pro", true, 5)
        assertFalse(unknown.has("thinkingConfig"))
    }

    @Test fun claudeAdaptiveModelsUseActualEffortAndExplicitOffAcrossStreamingModes() {
        Mockito.mockStatic(KiyoriLogger::class.java).use {
            val context = mock<Context>()
            for (model in listOf("claude-opus-4-6", "claude-sonnet-4-6", "claude-opus-4-7",
                "claude-opus-5", "claude-sonnet-5", "claude-fable-5", "claude-mythos-5")) {
                val provider = ClaudeProvider("https://api.anthropic.com/v1/messages", SingleApiKeyProvider("test"),
                    model, OkHttpClient())
                for (quality in 1..5) for (stream in listOf(false, true)) {
                    val request = provider.createRequestJson(context, listOf(PromptTurn(PromptTurnKind.USER, "hello")),
                        enableThinking = true, stream = stream, thinkingQualityLevel = quality)
                    val xhigh = model !in listOf("claude-opus-4-6", "claude-sonnet-4-6")
                    assertEquals(listOf("low", "medium", "high", if (xhigh) "xhigh" else "max", "max")[quality - 1],
                        request.getJSONObject("output_config").getString("effort"))
                    assertEquals(stream, request.getBoolean("stream"))
                    assertEquals("adaptive", request.getJSONObject("thinking").getString("type"))
                }
                if (model.contains("fable") || model.contains("mythos")) {
                    assertThrows(IllegalArgumentException::class.java) {
                        provider.createRequestJson(context, emptyList(), enableThinking = false)
                    }
                } else assertEquals("disabled", provider.createRequestJson(context,
                    listOf(PromptTurn(PromptTurnKind.USER, "hello")), enableThinking = false)
                    .getJSONObject("thinking").getString("type"))
            }
        }
    }

    @Test fun geminiEndpointPreservesRelayPrefixVersionAndQueryWithoutLeakingKey() {
        val url = GeminiRequestUrl.build("https://relay.example/gateway/v1beta/models/old:generateContent?tenant=one&key=old",
            "models/gemini-2.5-flash", true)
        assertEquals("/gateway/v1beta/models/gemini-2.5-flash:streamGenerateContent", url.encodedPath)
        assertEquals("one", url.queryParameter("tenant"))
        assertEquals("sse", url.queryParameter("alt"))
        assertNull(url.queryParameter("key"))
        assertEquals("/gateway/v1/models/gemini-2.5-flash:generateContent",
            GeminiRequestUrl.build("https://relay.example/gateway/v1", "gemini-2.5-flash", false).encodedPath)
    }
}
