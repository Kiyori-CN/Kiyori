package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalNoReplaceCreateTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `empty unicode file and directory are created exactly once`() {
        val root = temporary.root.toPath()
        // Windows 不允许末尾空格；名称策略测试另行验证 Android 合法空格不被裁剪。
        val file = root.resolve(" 报告 📄.txt")
        val directory = root.resolve("目录")
        createLocalNoReplace(file, false)
        createLocalNoReplace(directory, true)
        assertEquals(0L, Files.size(file))
        assertTrue(Files.isDirectory(directory))
        assertThrows(FileAlreadyExistsException::class.java) { createLocalNoReplace(file, false) }
        assertThrows(FileAlreadyExistsException::class.java) { createLocalNoReplace(directory, true) }
    }

    @Test fun `existing content is never truncated even on type mismatch`() {
        val file = temporary.root.toPath().resolve("keep")
        Files.write(file, "original".toByteArray())
        assertThrows(FileAlreadyExistsException::class.java) { createLocalNoReplace(file, false) }
        assertThrows(FileAlreadyExistsException::class.java) { createLocalNoReplace(file, true) }
        assertEquals("original", Files.readString(file))
    }

    @Test fun `concurrent exclusive creators have exactly one winner`() {
        val target = temporary.root.toPath().resolve("race")
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = executor.invokeAll((1..12).map { Callable {
                try { createLocalNoReplace(target, false); true }
                catch (_: FileAlreadyExistsException) { false }
            } }).map { it.get() }
            assertEquals(1, results.count { it })
            assertEquals(0L, Files.size(target))
        } finally { executor.shutdownNow() }
    }

    @Test fun `missing parent and relative paths cannot create unintended directories`() {
        val missing = temporary.root.toPath().resolve("missing/child")
        assertThrows(java.nio.file.NoSuchFileException::class.java) { createLocalNoReplace(missing, false) }
        assertFalse(Files.exists(missing.parent))
        assertThrows(IllegalArgumentException::class.java) { createLocalNoReplace(java.nio.file.Path.of("relative"), false) }
    }

    @Test fun `unsupported environments fail before writing`() {
        val target = temporary.root.toPath().resolve("untouched")
        listOf("linux", "repo:documents", "unknown").forEach { environment ->
            val tool = AITool("create_file", listOf(ToolParameter("path", target.toString()),
                ToolParameter("create_mode", "no_replace"), ToolParameter("environment", environment)))
            assertFalse(executeNoReplaceCreateTool(tool, false).success)
            assertFalse(Files.exists(target))
        }
    }
}
