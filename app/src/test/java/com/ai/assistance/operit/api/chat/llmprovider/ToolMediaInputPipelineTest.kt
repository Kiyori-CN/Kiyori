package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.util.Base64
import com.ai.assistance.operit.api.chat.enhance.ConversationMarkupManager
import com.ai.assistance.operit.core.chat.AssistantReplayHistoryProjector
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.ToolResult
import com.kiyori.platform.logging.KiyoriLogger
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.MediaPoolManager
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.kotlin.mock

class ToolMediaInputPipelineTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun link(kind: String, id: String = kind) = "<link type=\"$kind\" id=\"$id\"></link>"
    private fun result(id: String, content: String) = ConversationMarkupManager.formatToolResultForMessage(
        ToolResult("read_file", true, FileContentData("/path/$id", content, content.length.toLong())),
        providerToolName = "read_file", providerCallId = id, providerResultTerminal = true)
    private fun call(id: String) = """<tool_a name="read_file" provider_call_id="$id"><param name="path">/path/$id</param></tool_a>"""

    @Test fun everyMediaKindSurvivesFormattingAndNativeToolFollowup() = withPools {
        val media = link("image") + link("video") + link("audio")
        val formatted = result("call-a", media)
        assertTrue(formatted.indexOf("</tool_result_") < formatted.indexOf(link("image")))
        val request = Compiler().request(mock(), listOf(
            PromptTurn(PromptTurnKind.USER, "inspect the files"),
            PromptTurn(PromptTurnKind.TOOL_CALL, call("call-a")),
            PromptTurn(PromptTurnKind.TOOL_RESULT, formatted)))
        val messages = request.getJSONArray("messages")
        val tool = (0 until messages.length()).map(messages::getJSONObject).single { it.getString("role") == "tool" }
        assertEquals("call-a", tool.getString("tool_call_id"))
        assertFalse(tool.getString("content").contains("<link"))
        val userParts = messages.getJSONObject(messages.length() - 1)
        assertEquals("user", userParts.getString("role"))
        val parts = userParts.getJSONArray("content")
        assertEquals(listOf("image_url", "video_url", "input_audio"),
            (0 until parts.length()).map { parts.getJSONObject(it).getString("type") }.filter { it != "text" })
        assertTrue(parts.getJSONObject(0).getJSONObject("image_url").getString("url").startsWith("data:image/png;base64,"))
        assertEquals("mp3", parts.getJSONObject(2).getJSONObject("input_audio").getString("format"))
    }

    @Test fun mixedAttachmentsMoveOnlyAfterEveryParallelToolHasClosed() {
        val raw = call("a") + call("b") + result("b", link("audio")) + result("a", link("video"))
        val projected = AssistantReplayHistoryProjector.requireClosed(raw, "mixed_media")
        val a = projected.indexOf("provider_call_id=\"a\"", projected.indexOf("<tool_result"))
        val b = projected.indexOf("provider_call_id=\"b\"", projected.indexOf("<tool_result"))
        assertTrue(a < b)
        assertTrue(projected.indexOf(link("audio")) > b)
        assertTrue(projected.contains(link("video")))
        assertEquals(projected, AssistantReplayHistoryProjector.requireClosed(projected, "replay"))
        assertTrue(AssistantReplayHistoryProjector.project(call("a") + call("b") + result("a", link("audio")) + "unrelated prose" + result("b", "ok")).truncated)
    }

    @Test fun disabledOrMissingMediaCannotBecomeSuccessfulTextOnlyInput() = withPools {
        assertThrows(IllegalArgumentException::class.java) { Compiler(audio = false).buildContentField(mock(), "listen " + link("audio")) }
        assertThrows(IllegalArgumentException::class.java) { Compiler().buildContentField(mock(), "look " + link("image", "missing-image")) }
        assertThrows(IllegalArgumentException::class.java) { Compiler().buildContentField(mock(), "watch " + link("video", "missing-video")) }
    }

    @Test fun escapedJsonAndLongToolTextKeepWholeOrderedMediaTags() {
        val raw = """{"text":"${"x".repeat(80000)}","video":"<link type=\"video\" id=\"v\"></link>","audio":"<link type=\"audio\" id=\"a\"></link>"}"""
        val formatted = ConversationMarkupManager.buildBoundedToolResultMessage(listOf(ToolResult("clip", true, FileContentData("/clip", raw, raw.length.toLong()))))
        assertTrue(formatted.contains(link("video", "v")))
        assertTrue(formatted.contains(link("audio", "a")))
        assertTrue(formatted.indexOf(link("video", "v")) < formatted.indexOf(link("audio", "a")))
    }

    @Test fun geminiKeepsMixedMediaOrderAndHonorsItsSwitches() = withPools {
        val provider = GeminiProvider("https://example.test", SingleApiKeyProvider("test"), "test", OkHttpClient())
        val build = GeminiProvider::class.java.getDeclaredMethod("buildPartsArray", String::class.java).apply { isAccessible = true }
        val parts = build.invoke(provider, link("video") + link("image") + link("audio")) as JSONArray
        assertEquals(listOf("video/mp4", "image/png", "audio/mpeg"), (0 until parts.length()).map { parts.getJSONObject(it).getJSONObject("inline_data").getString("mime_type") })
        val disabled = GeminiProvider("https://example.test", SingleApiKeyProvider("test"), "test", OkHttpClient(), supportsAudio = false)
        val error = assertThrows(java.lang.reflect.InvocationTargetException::class.java) { build.invoke(disabled, link("audio")) }
        assertTrue(error.cause is IllegalArgumentException)
    }

    private fun withPools(block: () -> Unit) {
        Mockito.mockStatic(KiyoriLogger::class.java).use {
            Mockito.mockStatic(Base64::class.java).use { base64 ->
                val bytes = byteArrayOf(1, 2, 3)
                val encoded = "AQID"
                base64.`when`<ByteArray> { Base64.decode(encoded, Base64.DEFAULT) }.thenReturn(bytes)
                base64.`when`<String> { Base64.encodeToString(bytes, Base64.NO_WRAP) }.thenReturn(encoded)
                val root = temporary.newFolder()
                ImagePoolManager.initialize(root, preloadNow = false)
                MediaPoolManager.initialize(root)
                File(OperitPaths.imagePoolDir(root), "image.dat").writeText(encoded)
                File(OperitPaths.imagePoolDir(root), "image.meta").writeText("""{"mimeType":"image/png","width":1,"height":1}""")
                for ((id, mime) in listOf("audio" to "audio/mpeg", "video" to "video/mp4")) {
                    File(OperitPaths.mediaPoolDir(root), "$id.b64").writeText(encoded)
                    File(OperitPaths.mediaPoolDir(root), "$id.meta").writeText(mime)
                }
                try { block() } finally { ImagePoolManager.clear(); MediaPoolManager.removeMedia("audio"); MediaPoolManager.removeMedia("video") }
            }
        }
    }

    private class Compiler(audio: Boolean = true) : OpenAIProvider(
        "https://example.test/v1/chat/completions", SingleApiKeyProvider("test"), "test", OkHttpClient(),
        providerType = ApiProviderType.OPENAI_GENERIC, supportsVision = true, supportsAudio = audio,
        supportsVideo = true, enableToolCall = true) {
        fun request(context: Context, history: List<PromptTurn>) = JSONObject(createRequestBodyInternal(context, history,
            availableTools = listOf(ToolPrompt(name = "read_file", description = "Read a media file"))))
    }
}
