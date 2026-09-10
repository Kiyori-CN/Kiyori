package com.ai.assistance.operit.core.tools.defaultTool.standard

import java.nio.file.*
import java.util.zip.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalArchiveExtractionTest {
    @get:Rule val folder = TemporaryFolder()
    private val root get() = folder.root.toPath()
    private val commit: (Path, Path) -> Unit = { from, to -> Files.move(from, to) }
    private fun archive(vararg entries: Pair<String, String>): Path = root.resolve("input.zip").also { path ->
        ZipOutputStream(Files.newOutputStream(path)).use { zip -> entries.forEach { (name, text) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
        } }
    }
    @Test fun `extracts unicode and empty folders while retaining original`() {
        val source = archive("目录/" to "", "目录/文字.txt" to "你好", "空/" to "")
        val target = root.resolve("out")
        extractManagedZip(source, target, commit)
        assertEquals("你好", Files.readString(target.resolve("目录/文字.txt")))
        assertTrue(Files.isDirectory(target.resolve("空"))); assertTrue(Files.exists(source))
    }
    @Test fun `traversal never publishes output or touches sibling`() {
        val keep = Files.writeString(root.resolve("keep"), "safe")
        val source = archive("../keep" to "overwrite")
        assertThrows(IllegalArgumentException::class.java) { extractManagedZip(source, root.resolve("out"), commit) }
        assertEquals("safe", Files.readString(keep)); assertFalse(Files.exists(root.resolve("out")))
        assertEquals(2, folder.root.list()!!.size)
    }
    @Test fun `size budget cleans partial extraction`() {
        val source = archive("large" to "0123456789")
        assertThrows(IllegalArgumentException::class.java) { extractManagedZip(source, root.resolve("out"), commit, maxBytes = 4) }
        assertEquals(listOf("input.zip"), folder.root.list()!!.toList())
    }
    @Test fun `target race preserves existing content and source`() {
        val source = archive("note" to "abc")
        val target = root.resolve("out")
        assertThrows(FileAlreadyExistsException::class.java) { extractManagedZip(source, target, { _, to -> Files.createDirectory(to); throw FileAlreadyExistsException(to.toString()) }) }
        assertTrue(Files.isDirectory(target)); assertTrue(Files.exists(source)); assertEquals(2, folder.root.list()!!.size)
    }
    @Test fun `truncated central directory is rejected`() {
        val source = archive("note" to "abc")
        val bytes = Files.readAllBytes(source)
        Files.write(source, bytes.copyOf(bytes.size - 22))
        assertThrows(ZipException::class.java) { extractManagedZip(source, root.resolve("out"), commit) }
        assertFalse(Files.exists(root.resolve("out")))
    }
    @Test fun `cancellation before publication cleans temporary directory`() {
        val source = archive("note" to "abc")
        var calls = 0
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            extractManagedZip(source, root.resolve("out"), commit, { if (++calls > 2) throw kotlinx.coroutines.CancellationException() })
        }
        assertFalse(Files.exists(root.resolve("out"))); assertEquals(1, folder.root.list()!!.size)
    }
}
