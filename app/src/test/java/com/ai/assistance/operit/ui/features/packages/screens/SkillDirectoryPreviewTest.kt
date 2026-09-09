package com.ai.assistance.operit.ui.features.packages.screens

import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SkillDirectoryPreviewTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `preview sorts folders first and counts entries beyond the visible limit`() {
        val root = temporary.newFolder()
        val folder = root.resolve("folder").apply { mkdir() }
        repeat(20) { folder.resolve("file${it.toString().padStart(2, '0')}").writeText("") }
        root.resolve("SKILL.md").writeText("content")
        val preview = buildSkillDirectoryPreview(root)
        assertEquals(21, preview.fileCount)
        assertEquals(1, preview.folderCount)
        assertEquals(18, preview.text.lines().size)
        assertEquals(4, preview.hiddenEntryCount)
        assertEquals("folder/", preview.text.lineSequence().first())
    }

    @Test(expected = CancellationException::class)
    fun `closing a detail view can interrupt enumeration`() {
        buildSkillDirectoryPreview(temporary.newFolder()) { throw CancellationException() }
    }

    @Test(expected = IOException::class)
    fun `missing directory is an error instead of empty contents`() {
        buildSkillDirectoryPreview(temporary.root.resolve("missing"))
    }

    @Test fun `links remain entries and do not enumerate their target`() {
        val root = temporary.newFolder()
        val link = root.toPath().resolve("loop")
        try { Files.createSymbolicLink(link, root.toPath()) }
        catch (_: IOException) { org.junit.Assume.assumeTrue("Symbolic links unavailable", false) }
        catch (_: UnsupportedOperationException) { org.junit.Assume.assumeTrue("Symbolic links unavailable", false) }
        val preview = buildSkillDirectoryPreview(root)
        assertEquals(1, preview.fileCount)
        assertEquals(0, preview.folderCount)
        assertEquals("loop →", preview.text)
    }
}
