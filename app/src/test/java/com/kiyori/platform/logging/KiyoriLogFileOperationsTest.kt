package com.kiyori.platform.logging

import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KiyoriLogFileOperationsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `snapshot follows queued writes and remains unchanged by later writes`() = runBlocking {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val source = temporary.newFile("application.log")
            val snapshot = temporary.newFile("snapshot")
            executor.submit { source.appendText("before\n") }
            assertTrue(awaitLogFileOperation(executor) { copyApplicationLogFile(source, snapshot) })
            executor.submit { source.appendText("after\n") }.get()
            assertEquals("before\n", snapshot.readText())
            assertEquals("before\nafter\n", source.readText())
        } finally { executor.shutdownNow() }
    }

    @Test fun `clearing affects only the current regular file and missing file is already clear`() {
        val source = temporary.newFile("application.log").apply { writeText("old") }
        val separate = temporary.newFile("package.log").apply { writeText("keep") }
        clearApplicationLogFile(source)
        clearApplicationLogFile(source)
        assertFalse(source.exists())
        assertEquals("keep", separate.readText())
        val directory = temporary.newFolder("not-a-log")
        assertThrows(IllegalArgumentException::class.java) { clearApplicationLogFile(directory) }
        assertTrue(directory.isDirectory)
    }

    @Test fun `queue preserves the actual operation failure instead of a future wrapper`() = runBlocking {
        val executor = Executors.newSingleThreadExecutor()
        val failure = IOException("denied")
        try {
            val error = runCatching { awaitLogFileOperation<Unit>(executor) { throw failure } }.exceptionOrNull()
            // 协程栈恢复可能复制IOException，但实际类型、消息和原始原因必须保留。
            assertTrue(error is IOException)
            assertEquals("denied", error?.message)
            assertTrue(generateSequence(error) { it.cause }.any { it === failure })
        } finally { executor.shutdownNow() }
    }
}
