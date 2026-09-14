package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import org.junit.Assert.*
import org.junit.Test

class FileMediaReadPolicyTest {
    private fun tool(vararg params: Pair<String, String>) = AITool("read_file", params.map { ToolParameter(it.first, it.second) })

    @Test fun ordinaryReadNeverPromotesTextOrMediaImplicitly() {
        assertNull(FileMediaReadPolicy.resolve(tool(), "/sdcard/picture.png"))
        assertNull(FileMediaReadPolicy.resolve(tool("direct_image" to "false"), "/sdcard/picture.png"))
    }

    @Test fun directInputRecognizesCaseAndConcreteMimeTypes() {
        for ((path, kind, mime) in listOf(
            Triple("/root/图.JPG", "image", "image/jpeg"), Triple("/a.HEIC", "image", "image/heic"),
            Triple("/a.m4a", "audio", "audio/mp4"), Triple("/a.opus", "audio", "audio/opus"),
            Triple("/a.MKV", "video", "video/x-matroska"), Triple("/a.3gp", "video", "video/3gpp"))) {
            assertEquals(FileMediaReadPolicy.Format(kind, mime), FileMediaReadPolicy.resolve(tool("direct_$kind" to "true"), path))
        }
    }

    @Test fun safMimeCanIdentifyExtensionlessMediaAndAudioWebm() {
        assertEquals("audio", FileMediaReadPolicy.resolve(tool("direct_audio" to "true"), "/clip.webm", "audio/webm")!!.kind)
        assertEquals("image/png", FileMediaReadPolicy.resolve(tool("direct_image" to "true"), "/document/42", "image/png")!!.mimeType)
        assertEquals("audio/webm", FileMediaReadPolicy.resolve(tool("direct_audio" to "true"), "/clip.webm")!!.mimeType)
        assertEquals("video/ogg", FileMediaReadPolicy.resolve(tool("direct_video" to "true"), "/clip.ogg")!!.mimeType)
        assertThrows(IllegalArgumentException::class.java) { FileMediaReadPolicy.resolve(tool("direct_audio" to "true"), "/clip.webm", "video/webm") }
    }

    @Test fun conflictingTextModeAndMultipleMediaModesFail() {
        for (params in listOf(arrayOf("direct_image" to "true", "text_only" to "true"),
            arrayOf("direct_image" to "true", "read_mode" to "head"),
            arrayOf("direct_image" to "true", "direct_audio" to "true"))) {
            assertThrows(IllegalArgumentException::class.java) { FileMediaReadPolicy.resolve(tool(*params), "/image.png") }
        }
    }

    @Test fun wrongKindAndDocumentsDoNotFallBackToText() {
        for (path in listOf("/clip.mp3", "/paper.pdf", "/book.docx", "/unknown.bin")) {
            assertThrows(IllegalArgumentException::class.java) { FileMediaReadPolicy.resolve(tool("direct_image" to "true"), path) }
        }
    }

    @Test fun oversizedOrEmptySafStreamFailsAndAlwaysCloses() = kotlinx.coroutines.test.runTest {
        for (length in listOf(0, FileMediaReadPolicy.MAX_TRANSFER_BYTES + 1)) {
            var closed = false
            var consumed = 0
            val stream = object : java.io.InputStream() {
                override fun read(): Int = if (consumed++ < length) 1 else -1
                override fun read(bytes: ByteArray, offset: Int, count: Int): Int {
                    if (consumed >= length) return -1
                    val size = minOf(count, length - consumed)
                    bytes.fill(1, offset, offset + size)
                    consumed += size
                    return size
                }
                override fun close() { closed = true }
            }
            var failure: IllegalArgumentException? = null
            try {
                FileMediaReader.fromStream(tool("direct_audio" to "true"), "/clip.mp3",
                    FileMediaReadPolicy.Format("audio", "audio/mpeg"), "repo:test", stream)
            } catch (error: IllegalArgumentException) { failure = error }
            assertNotNull(failure)
            assertTrue(closed)
            assertTrue(consumed <= FileMediaReadPolicy.MAX_TRANSFER_BYTES + 1)
        }
    }
}
