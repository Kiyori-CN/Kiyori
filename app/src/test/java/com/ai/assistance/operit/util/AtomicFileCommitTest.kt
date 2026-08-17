package com.ai.assistance.operit.util

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicFileCommitTest {
    @Test
    fun sameDirectoryCommitMovesOneCompleteFileWithoutReplacement() {
        val root = Files.createTempDirectory("atomic-file-commit").toFile()
        try {
            val staged = root.resolve(".output.partial.mp4").apply { writeText("complete") }
            val target = root.resolve("output.mp4")

            commitFileAtomicallyWithoutReplacement(staged, target)

            assertFalse(staged.exists())
            assertTrue(target.isFile)
            assertEquals("complete", target.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun existingTargetIsNeverReplaced() {
        val root = Files.createTempDirectory("atomic-file-no-replace").toFile()
        try {
            val staged = root.resolve(".output.partial.mp4").apply { writeText("new") }
            val target = root.resolve("output.mp4").apply { writeText("existing") }

            assertThrows(IllegalArgumentException::class.java) {
                commitFileAtomicallyWithoutReplacement(staged, target)
            }

            assertTrue(staged.isFile)
            assertEquals("existing", target.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun concurrentSameProcessCommitsProduceExactlyOneTarget() {
        val root = Files.createTempDirectory("atomic-file-concurrent").toFile()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val stagedFiles =
                listOf(
                    root.resolve(".first.partial.mp4").apply { writeText("first") },
                    root.resolve(".second.partial.mp4").apply { writeText("second") },
                )
            val target = root.resolve("output.mp4")
            val ready = CountDownLatch(stagedFiles.size)
            val start = CountDownLatch(1)
            val successes = AtomicInteger(0)
            val failures = AtomicInteger(0)

            stagedFiles.forEach { staged ->
                executor.execute {
                    ready.countDown()
                    start.await()
                    runCatching {
                        commitFileAtomicallyWithoutReplacement(staged, target)
                    }.onSuccess {
                        successes.incrementAndGet()
                    }.onFailure {
                        failures.incrementAndGet()
                    }
                }
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            assertEquals(1, successes.get())
            assertEquals(1, failures.get())
            assertTrue(target.isFile)
            assertTrue(target.readText() in setOf("first", "second"))
            assertEquals(1, stagedFiles.count(File::isFile))
        } finally {
            executor.shutdownNow()
            root.deleteRecursively()
        }
    }
}
