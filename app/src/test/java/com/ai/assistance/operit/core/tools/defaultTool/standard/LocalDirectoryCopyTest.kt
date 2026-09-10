package com.ai.assistance.operit.core.tools.defaultTool.standard

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalDirectoryCopyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `copies nested directories empty files and unicode names`() {
        val source = temporary.newFolder("source")
        val child = File(source, "报告").apply { mkdir() }
        File(child, "data.bin").writeBytes(byteArrayOf(0, 1, 2, -1))
        File(child, "empty").writeBytes(byteArrayOf())
        val destination = File(temporary.root, "destination")
        copyLocalDirectory(source, destination)
        assertArrayEquals(byteArrayOf(0, 1, 2, -1), File(destination, "报告/data.bin").readBytes())
        assertEquals(0L, File(destination, "报告/empty").length())
        assertTrue(File(child, "data.bin").exists())
    }

    @Test fun `nested destination failure propagates and preserves all source data`() {
        val source = temporary.newFolder("source")
        File(source, "nested").mkdir()
        File(source, "nested/valuable.txt").writeText("original")
        val destination = temporary.newFolder("destination")
        File(destination, "nested").writeText("existing destination")
        assertThrows(Exception::class.java) { copyLocalDirectory(source, destination) }
        assertEquals("original", File(source, "nested/valuable.txt").readText())
        assertEquals("existing destination", File(destination, "nested").readText())
    }

    @Test fun `cannot copy a directory onto itself or a canonical descendant`() {
        val source = temporary.newFolder("source")
        File(source, "valuable").writeText("original")
        assertThrows(IllegalArgumentException::class.java) { copyLocalDirectory(source, source) }
        assertThrows(IllegalArgumentException::class.java) { copyLocalDirectory(source, File(source, "child")) }
        assertThrows(IllegalArgumentException::class.java) { copyLocalDirectory(source, File(source, "child/../nested")) }
        assertEquals("original", File(source, "valuable").readText())
        assertEquals(listOf("valuable"), source.listFiles()!!.map { it.name })
    }

    @Test fun `missing directory cannot report successful empty copy`() {
        val destination = File(temporary.root, "destination")
        assertThrows(Exception::class.java) { copyLocalDirectory(File(temporary.root, "missing"), destination) }
        assertFalse(destination.exists())
    }

    @Test fun `file cannot replace an existing empty directory`() {
        val source = temporary.newFolder("source")
        File(source, "collision").writeText("data")
        val destination = temporary.newFolder("destination")
        File(destination, "collision").mkdir()
        assertThrows(Exception::class.java) { copyLocalDirectory(source, destination) }
        assertTrue(File(destination, "collision").isDirectory)
        assertEquals("data", File(source, "collision").readText())
    }
}
