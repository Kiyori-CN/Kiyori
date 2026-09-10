package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.tools.*
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class LocalFileSearchTest {
    @get:Rule val temporary = TemporaryFolder()
    private val root get() = temporary.root.toPath()
    private fun file(name: String, text: String = "abc"): Path = root.resolve(name).also {
        Files.createDirectories(it.parent); Files.write(it, text.toByteArray())
    }
    @Test fun `direct search includes matching folders but not their children`() {
        file("Report.txt"); file("Report folder/Report nested.txt")
        val result = searchLocalFiles(root, FileSearchOptions(name = "report"))
        assertEquals(2, result.entries.size)
        assertEquals(1, result.entries.count { it.directory })
    }
    @Test fun `recursive search controls hidden subtrees and never follows links`() {
        file("folder/note.txt"); file(".hidden/note.txt"); file("outside.txt")
        Files.createSymbolicLink(root.resolve("link"), root.resolve("folder"))
        val options = FileSearchOptions(name = "note", recursive = true)
        val result = searchLocalFiles(root, options)
        assertEquals(1, result.entries.size); assertEquals(1, result.skipped)
        assertEquals(2, searchLocalFiles(root, options.copy(includeHidden = true)).entries.size)
    }
    @Test fun `plain names treat regex metacharacters literally and case is respected`() {
        file("A[1]+.txt"); file("a11.txt")
        assertEquals(1, searchLocalFiles(root, FileSearchOptions(name = "a[1]+" )).entries.size)
        assertTrue(searchLocalFiles(root, FileSearchOptions(name = "a[1]+", caseSensitive = true)).entries.isEmpty())
    }
    @Test fun `glob is full filename matching while regex finds a fragment`() {
        file("report12.txt"); file("reportX.pdf")
        assertEquals(1, searchLocalFiles(root, FileSearchOptions(name = "*.txt", nameMode = FileSearchNameMode.GLOB)).entries.size)
        assertEquals(1, searchLocalFiles(root, FileSearchOptions(name = "\\d+", nameMode = FileSearchNameMode.REGEX)).entries.size)
    }
    @Test fun `size and time bounds are inclusive and exclude folders for size searches`() {
        val file = file("note", "12345"); Files.setLastModifiedTime(file, FileTime.fromMillis(10000))
        Files.createDirectory(root.resolve("folder"))
        val options = FileSearchOptions(minimumBytes = 5, maximumBytes = 5, modifiedAfter = 10000, modifiedBefore = 10000)
        assertEquals(listOf(file.toString()), searchLocalFiles(root, options).entries.map { it.path })
        assertTrue(searchLocalFiles(root, options.copy(minimumBytes = 6, maximumBytes = 6)).entries.isEmpty())
    }
    @Test fun `content search handles unicode line breaks and literal characters`() {
        file("yes", "first\n中文 A+B\nlast"); file("no", "中文 aaab")
        assertEquals(1, searchLocalFiles(root, FileSearchOptions(content = "中文 a+b")).entries.size)
        assertEquals(1, searchLocalFiles(root, FileSearchOptions(content = "(?s)first.*last", nameMode = FileSearchNameMode.REGEX)).entries.size)
    }
    @Test fun `content limits and unsupported encodings are visible not silent empty results`() {
        file("huge", "x".repeat(30)); file("binary", "a\u0000b")
        Files.write(root.resolve("invalid"), byteArrayOf(0xc3.toByte(), 0x28))
        val result = searchLocalFiles(root, FileSearchOptions(content = "x"), maxContentBytes = 20)
        assertEquals(3, result.skipped); assertEquals(2, result.limitations.size)
    }
    @Test fun `result count and scanning limits report partial completion`() {
        repeat(4) { file("file$it") }
        val result = searchLocalFiles(root, FileSearchOptions(name = "file"), maxResults = 2)
        assertEquals(2, result.entries.size); assertTrue(result.limitations.isNotEmpty())
        val scanned = searchLocalFiles(root, FileSearchOptions(name = "absent"), maxEntries = 2)
        assertEquals(2, scanned.scanned); assertTrue(scanned.limitations.isNotEmpty())
    }
    @Test fun `cancellation propagates and time limit stays observable`() {
        file("note")
        assertThrows(CancellationException::class.java) { searchLocalFiles(root, FileSearchOptions(name = "note"), { throw CancellationException() }) }
        assertTrue(searchLocalFiles(root, FileSearchOptions(name = "note"), maxDurationNanos = 0).limitations.isNotEmpty())
    }
    @Test fun `invalid regex and reversed bounds fail before traversal`() {
        assertThrows(IllegalArgumentException::class.java) { searchLocalFiles(root, FileSearchOptions(name = "[", nameMode = FileSearchNameMode.REGEX)) }
        assertThrows(IllegalArgumentException::class.java) { searchLocalFiles(root, FileSearchOptions(minimumBytes = 10, maximumBytes = 2)) }
    }
    @Test fun `pathological regex cannot monopolize content search`() {
        file("note", "a".repeat(10000) + "!")
        val start = System.nanoTime()
        val result = searchLocalFiles(root, FileSearchOptions(content = "(a+)+$", nameMode = FileSearchNameMode.REGEX))
        assertTrue(result.limitations.any { it.contains("正则") })
        assertTrue(System.nanoTime() - start < 3_000_000_000L)
    }
    @Test fun `blank name with glob still permits advanced only filtering`() {
        file("note")
        assertEquals(1, searchLocalFiles(root, FileSearchOptions(nameMode = FileSearchNameMode.GLOB, minimumBytes = 1)).entries.size)
    }
}
