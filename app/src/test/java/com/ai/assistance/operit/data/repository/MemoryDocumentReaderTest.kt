package com.ai.assistance.operit.data.repository

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class MemoryDocumentReaderTest {

    @Test fun `utf8 text decodes unchanged and the byte order mark is dropped`() {
        val text = "记忆库 import — ok"
        assertEquals(text, MemoryDocumentReader.decodeText(text.toByteArray(Charsets.UTF_8)))
        assertEquals(text, MemoryDocumentReader.decodeText("﻿$text".toByteArray(Charsets.UTF_8)))
    }

    @Test fun `gb18030 text falls back instead of being stored as replacement characters`() {
        val text = "这是一份用国标编码保存的资料"
        val decoded = MemoryDocumentReader.decodeText(text.toByteArray(Charset.forName("GB18030")))
        assertEquals(text, decoded)
        assertFalse(decoded.contains('�'))
    }

    @Test fun `utf16 text is decoded through its byte order mark`() {
        val text = "UTF-16 资料"
        assertEquals(text, MemoryDocumentReader.decodeText(text.toByteArray(Charsets.UTF_16)))
    }

    @Test fun `undecodable bytes fail loudly rather than saving garbage`() {
        // 0x81 0x40 之外的孤立字节在 UTF-8 和 GB18030 下都不合法。
        val bytes = byteArrayOf(0xC3.toByte(), 0x28, 0xA0.toByte(), 0xA1.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val failure = runCatching { MemoryDocumentReader.decodeText(bytes) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }
}
