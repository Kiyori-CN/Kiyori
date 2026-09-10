package com.ai.assistance.operit.core.tools.defaultTool.standard

import java.nio.file.Files
import java.nio.file.FileAlreadyExistsException
import java.nio.charset.CharacterCodingException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalTextDocumentTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun path(name: String) = temporary.root.toPath().resolve(name)
    @Test fun `UTF8 unicode BOM and line endings survive read and save copy`() {
        val original = path("original.txt")
        val copy = path("copy.txt")
        val text = "\uFEFF中文😀\r\nsecond\n"
        Files.write(original, text.toByteArray())
        assertEquals(text, readLocalUtf8Document(original))
        saveLocalTextCopy(copy, text) { source, target -> Files.move(source, target) }
        assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(copy))
        assertEquals(2, temporary.root.list()!!.size)
    }
    @Test fun `empty document is readable`() {
        val file = path("empty.txt")
        Files.createFile(file)
        assertEquals("", readLocalUtf8Document(file))
    }
    @Test fun `malformed encoding cannot become editable replacement characters`() {
        val file = path("bad.txt")
        Files.write(file, byteArrayOf(0xc3.toByte(), 0x28))
        assertThrows(CharacterCodingException::class.java) { readLocalUtf8Document(file) }
    }
    @Test fun `large or binary files are rejected`() {
        val file = path("big.txt")
        Files.write(file, ByteArray(FILE_MANAGER_TEXT_LIMIT + 1) { 65 })
        assertThrows(IllegalArgumentException::class.java) { readLocalUtf8Document(file) }
        Files.write(file, byteArrayOf(65, 0, 66))
        assertThrows(IllegalArgumentException::class.java) { readLocalUtf8Document(file) }
    }
    @Test fun `existing destination and original remain intact on conflict`() {
        val file = path("exists.txt")
        Files.write(file, "original".toByteArray())
        assertThrows(FileAlreadyExistsException::class.java) {
            saveLocalTextCopy(file, "changed") { source, target -> Files.move(source, target) }
        }
        assertEquals("original", Files.readString(file))
        assertEquals(listOf("exists.txt"), temporary.root.list()!!.toList())
    }
    @Test fun `commit failure cleans staging without publishing partial file`() {
        val file = path("new.txt")
        assertThrows(java.io.IOException::class.java) {
            saveLocalTextCopy(file, "changed") { _, _ -> throw java.io.IOException("injected") }
        }
        assertFalse(Files.exists(file))
        assertTrue(temporary.root.list()!!.isEmpty())
    }
    @Test fun `oversized save fails before staging or commit`() {
        var committed = false
        assertThrows(IllegalArgumentException::class.java) {
            saveLocalTextCopy(path("big.txt"), "中".repeat(FILE_MANAGER_TEXT_LIMIT / 3 + 1)) { _, _ -> committed = true }
        }
        assertFalse(committed)
        assertTrue(temporary.root.list()!!.isEmpty())
    }
    @Test fun `unpaired surrogate is rejected instead of silently replacing text`() {
        assertThrows(CharacterCodingException::class.java) {
            saveLocalTextCopy(path("bad.txt"), "\uD800") { _, _ -> fail("must not commit") }
        }
        assertTrue(temporary.root.list()!!.isEmpty())
    }
}
