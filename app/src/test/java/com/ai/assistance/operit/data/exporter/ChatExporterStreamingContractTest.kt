package com.ai.assistance.operit.data.exporter

import android.content.Context
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import java.io.StringWriter
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.stubbing.Answer

class ChatExporterStreamingContractTest {
    private val context: Context =
        Mockito.mock(
            Context::class.java,
            Answer { invocation ->
                "string-${invocation.arguments.joinToString(separator = ",")}"
            },
        )

    @Test
    fun `text writer output matches string export and counts source characters`() {
        val content = "prefix<&>" + "x".repeat(70_000)
        val history = history(
            id = "chat-1",
            title = "Text export",
            content = content,
        )
        val writer = StringWriter()
        var processedCharacters = 0L

        TextExporter.writeSingleToWriter(context, history, writer) {
            processedCharacters += it
        }

        assertEquals(TextExporter.exportSingle(context, history), writer.toString())
        assertEquals(content.length.toLong(), processedCharacters)
    }

    @Test
    fun `multiple writer APIs match string exports`() {
        val histories = listOf(
            history("chat-1", "First", "alpha"),
            history("chat-2", "Second", "beta"),
        )

        val textWriter = StringWriter()
        TextExporter.writeMultipleHeader(context, histories, 2, textWriter)
        histories.forEachIndexed { index, history ->
            TextExporter.writeConversationSeparator(textWriter, index)
            TextExporter.writeSingleToWriter(context, history, textWriter)
        }
        TextExporter.writeMultipleFooter(context, textWriter)

        val htmlWriter = StringWriter()
        HtmlExporter.writeMultipleHeader(context, histories, 2, htmlWriter)
        histories.forEachIndexed { index, history ->
            HtmlExporter.writeConversationSeparator(htmlWriter, index)
            HtmlExporter.writeConversationToWriter(context, htmlWriter, history)
        }
        HtmlExporter.writeMultipleFooter(context, htmlWriter)

        assertEquals(
            normalizeExportTime(TextExporter.exportMultiple(context, histories)),
            normalizeExportTime(textWriter.toString()),
        )
        assertEquals(
            normalizeExportTime(HtmlExporter.exportMultiple(context, histories)),
            normalizeExportTime(htmlWriter.toString()),
        )
    }

    @Test
    fun `html writer escapes metadata and mixed newline content while counting source`() {
        val content = "plain <tag>&\"'\r\n```\ncode <&\r```\nafter"
        val history = ChatHistory(
            id = "chat-html",
            title = "<unsafe & title>",
            messages = listOf(
                ChatMessage(
                    sender = "ai",
                    content = content,
                    modelName = "model<&>",
                )
            ),
            createdAt = FIXED_TIME,
            updatedAt = FIXED_TIME,
            group = "group<&>",
        )
        val writer = StringWriter()
        var processedCharacters = 0L

        HtmlExporter.writeSingleToWriter(context, history, writer) {
            processedCharacters += it
        }

        val output = writer.toString()
        assertTrue(output.contains("<title>&lt;unsafe &amp; title&gt;</title>"))
        assertTrue(output.contains("<h2>&lt;unsafe &amp; title&gt;</h2>"))
        assertTrue(output.contains("group&lt;&amp;&gt;"))
        assertTrue(output.contains("model&lt;&amp;&gt;"))
        assertTrue(output.contains("plain &lt;tag&gt;&amp;&quot;"))
        assertTrue(output.contains("<pre><code>code &lt;&amp;"))
        assertFalse(output.contains("<unsafe & title>"))
        assertFalse(output.contains("plain <tag>"))
        assertEquals(content.length.toLong(), processedCharacters)
    }

    private fun history(id: String, title: String, content: String): ChatHistory {
        return ChatHistory(
            id = id,
            title = title,
            messages = listOf(ChatMessage(sender = "user", content = content)),
            createdAt = FIXED_TIME,
            updatedAt = FIXED_TIME,
        )
    }

    private fun normalizeExportTime(value: String): String {
        return value.replace(
            Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
            "<export-time>",
        )
    }

    private companion object {
        val FIXED_TIME: LocalDateTime = LocalDateTime.of(2026, 8, 29, 12, 34, 56)
    }
}
