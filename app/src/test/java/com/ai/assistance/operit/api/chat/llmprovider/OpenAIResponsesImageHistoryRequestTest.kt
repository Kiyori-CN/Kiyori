package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.api.chat.enhance.ConversationService
import com.ai.assistance.operit.core.chat.AssistantReplayHistoryProjector
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ApiProtocol
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import okhttp3.OkHttpClient
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.kotlin.mock

class OpenAIResponsesImageHistoryRequestTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun oldConversationWithInterleavedToolPreviewsCompilesForRepeatedFollowups() = runTest {
        Mockito.mockStatic(AppLogger::class.java).use {
            val directory = temporary.newFolder()
            ImagePoolManager.initialize(directory, preloadNow = false)
            val imageDirectory = OperitPaths.imagePoolDir(directory)
            try {
                // 使用图片池真实磁盘读取与请求编译；无需 Android 图像解码或网络服务。
                for (id in listOf("page-a", "page-b")) {
                    File(imageDirectory, "$id.dat").writeText(java.util.Base64.getEncoder().encodeToString(id.toByteArray()))
                    File(imageDirectory, "$id.meta").writeText("""{"mimeType":"image/png","width":1,"height":1}""")
                }
                val rawAssistant = call("call-a") + call("call-b") +
                    result("call-b") + image("page-b") + result("call-a") + image("page-a") +
                    "Final office report"
                val projected = AssistantReplayHistoryProjector.project(rawAssistant).content
                // JVM 不加载 Android native splitter；此夹具明确给出它的分段，随后执行真实角色准备。
                val segments = listOf(listOf("tool_A1", call("call-a")), listOf("tool_A1", call("call-b")),
                    listOf("tool_result_A1", result("call-a")), listOf("tool_result_A1", result("call-b")),
                    listOf("link", image("page-b")), listOf("link", image("page-a")), listOf("text", "Final office report"))
                assertEquals(segments.joinToString("") { it[1] }, projected)
                val service = Mockito.mock(ConversationService::class.java, Mockito.CALLS_REAL_METHODS)
                val provider = RequestCompiler()
                for (next in listOf("继续", "检查生成文件", "你好")) {
                    val history = mutableListOf(PromptTurn(PromptTurnKind.USER, "make a report"))
                    service.processChatMessageWithTools(projected, segments, history, 1, 3)
                    history.add(PromptTurn(PromptTurnKind.USER, next))
                    val historyBefore = history.toList()
                    val request = provider.request(mock(), history)
                    val input = request.getJSONArray("input")
                    val images = mutableListOf<String>()
                    val calls = mutableListOf<String>()
                    val results = mutableListOf<String>()
                    var finalAnswerFound = false
                    for (i in 0 until input.length()) {
                        val item = input.getJSONObject(i)
                        when (item.optString("type")) {
                            "function_call" -> calls.add(item.getString("call_id"))
                            "function_call_output" -> results.add(item.getString("call_id"))
                        }
                        if (item.optString("role") == "assistant" && item.opt("content") is String &&
                            item.getString("content").contains("Final office report")) finalAnswerFound = true
                        val parts = item.optJSONArray("content") ?: continue
                        for (j in 0 until parts.length()) {
                            val part = parts.getJSONObject(j)
                            if (part.optString("type") == "input_image") {
                                assertEquals("user", item.getString("role"))
                                images.add(part.getString("image_url"))
                            }
                            if (part.optString("text").contains("Final office report")) {
                                assertEquals("assistant", item.getString("role"))
                                finalAnswerFound = true
                            }
                        }
                    }
                    // 原始完成顺序可变，工具重放规范为调用顺序；两张图片不能丢失或重复。
                    assertEquals(listOf("call-a", "call-b"), calls)
                    assertEquals(calls, results)
                    assertEquals(2, images.size)
                    assertEquals(2, images.toSet().size)
                    assertTrue(finalAnswerFound)
                    assertEquals(next, input.getJSONObject(input.length() - 1).getString("content"))
                    assertEquals(historyBefore, history)
                    assertEquals(request.toString(), provider.request(mock(), history).toString())
                }
            } finally {
                ImagePoolManager.clear()
            }
        }
    }

    private fun call(id: String) = """<tool_A1 name="preview" provider_name="DEEPSEEK" provider_call_id="$id"><param name="page">1</param></tool_A1>"""
    private fun result(id: String) = """<tool_result_A1 name="preview" status="success" provider_tool_name="preview" provider_call_id="$id" provider_result_terminal="true"><content>rendered</content></tool_result_A1>"""
    private fun image(id: String) = """<link type="image" id="$id"></link>"""

    private class RequestCompiler : OpenAIProvider(
        apiEndpoint = "https://example.test/v1/responses", apiKeyProvider = SingleApiKeyProvider("local-test-key"),
        modelName = "deepseek-test", client = OkHttpClient(), providerType = ApiProviderType.DEEPSEEK,
        capabilityProviderType = ApiProviderType.OPENAI_RESPONSES_GENERIC,
        endpointProtocol = ApiProtocol.OPENAI_RESPONSES, supportsVision = true, enableToolCall = true,
    ) {
        override val useResponsesApi = true
        fun request(context: Context, history: List<PromptTurn>): JSONObject = JSONObject(
            createRequestBodyInternal(context, history, availableTools = listOf(ToolPrompt(name = "preview", description = "Render a page")))
        )
    }
}
