package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class LocalNoReplaceCopyTest {
    @get:Rule val folder = TemporaryFolder()
    private val root get() = folder.root.toPath()
    // JVM验证复制协议和故障路径；Android syscall 的真实文件系统能力另做设备验收。
    private val commit: (Path, Path) -> Unit = { source, destination -> Files.move(source, destination) }

    private fun source(name: String = "source.txt", body: String = "original"): Path =
        root.resolve(name).also { Files.write(it, body.toByteArray()) }

    private fun assertNoStaging() {
        Files.list(root).use { assertFalse(it.anyMatch { path -> path.fileName.toString().startsWith(".kiyori-copy-") }) }
    }

    @Test fun `empty unicode file commits only after payload is complete`() {
        val source = source("报告 📄.txt", "")
        val target = root.resolve("副本 📄.txt")
        val receipt = copyLocalNoReplace(source, target, false, { staged, destination ->
            assertFalse(Files.exists(destination))
            assertEquals(0L, Files.size(staged))
            commit(staged, destination)
        })
        assertEquals(0L, Files.size(target))
        assertTrue(Files.exists(source))
        assertNull(receipt.stagingPath)
        assertNoStaging()
    }

    @Test fun `nested directory copied in full without merging destinations`() {
        val source = Files.createDirectory(root.resolve("source"))
        Files.createDirectories(source.resolve("深层/empty"))
        val data = ByteArray(300_001) { (it % 251).toByte() }
        Files.write(source.resolve("深层/数据.bin"), data)
        val target = root.resolve("target")
        copyLocalNoReplace(source, target, true, commit)
        assertArrayEquals(data, Files.readAllBytes(target.resolve("深层/数据.bin")))
        assertTrue(Files.isDirectory(target.resolve("深层/empty")))
        assertArrayEquals(data, Files.readAllBytes(source.resolve("深层/数据.bin")))
        assertNoStaging()
    }

    @Test fun `existing file remains byte identical and never reaches commit`() {
        val source = source()
        val target = source("target.txt", "do not overwrite")
        val error = assertThrows(LocalCopyException::class.java) {
            copyLocalNoReplace(source, target, false, { _, _ -> fail("Must not commit") })
        }
        assertEquals(FileCopyErrorCode.CONFLICT, error.code)
        assertEquals("do not overwrite", Files.readString(target))
        assertEquals("original", Files.readString(source))
        assertNoStaging()
    }

    @Test fun `destination appearing at commit is a conflict and preserves competing data`() {
        val source = source()
        val target = root.resolve("target")
        val error = assertThrows(LocalCopyException::class.java) {
            copyLocalNoReplace(source, target, false, { staged, destination ->
                Files.write(destination, "concurrent writer".toByteArray())
                commit(staged, destination)
            })
        }
        assertEquals(FileCopyErrorCode.CONFLICT, error.code)
        assertEquals("concurrent writer", Files.readString(target))
        assertEquals("original", Files.readString(source))
        assertNoStaging()
    }

    @Test fun `existing directory is a conflict not a merge`() {
        val source = Files.createDirectory(root.resolve("source"))
        Files.write(source.resolve("new"), byteArrayOf(1))
        val target = Files.createDirectory(root.resolve("target"))
        Files.write(target.resolve("existing"), byteArrayOf(2))
        assertThrows(LocalCopyException::class.java) { copyLocalNoReplace(source, target, true, commit) }
        assertFalse(Files.exists(target.resolve("new")))
        assertArrayEquals(byteArrayOf(2), Files.readAllBytes(target.resolve("existing")))
    }

    @Test fun `descendant and self destinations cannot stage inside source`() {
        val source = Files.createDirectory(root.resolve("source"))
        assertThrows(IllegalArgumentException::class.java) { copyLocalNoReplace(source, source, true, commit) }
        assertThrows(IllegalArgumentException::class.java) { copyLocalNoReplace(source, source.resolve("child"), true, commit) }
        assertFalse(Files.exists(source.resolve("child")))
        assertNoStaging()
    }

    @Test fun `source changed during transfer does not publish target`() {
        val source = source()
        val target = root.resolve("target")
        var changed = false
        val error = assertThrows(LocalCopyException::class.java) {
            copyLocalNoReplace(source, target, false, commit, checkActive = {
                val stagedFile = Files.list(root).use { paths ->
                    paths.filter { it.fileName.toString().startsWith(".kiyori-copy-") }
                        .findFirst().orElse(null)?.resolve("payload")
                }
                if (!changed && stagedFile != null && Files.exists(stagedFile) && Files.size(stagedFile) > 0) {
                    Files.setLastModifiedTime(source, FileTime.fromMillis(1_000L))
                    changed = true
                }
            })
        }
        assertTrue(changed)
        assertEquals(FileCopyErrorCode.SOURCE_CHANGED, error.code)
        assertFalse(Files.exists(target))
        assertEquals("original", Files.readString(source))
        assertNoStaging()
    }

    @Test fun `commit error preserves source and cleans temporary payload`() {
        val source = source()
        val target = root.resolve("target")
        val error = assertThrows(LocalCopyException::class.java) {
            copyLocalNoReplace(source, target, false, { _, _ ->
                throw LocalCopyException(FileCopyErrorCode.UNSUPPORTED, "Unsupported filesystem")
            })
        }
        assertEquals(FileCopyErrorCode.UNSUPPORTED, error.code)
        assertFalse(Files.exists(target))
        assertTrue(Files.exists(source))
        assertNoStaging()
    }

    @Test fun `cancellation before commit cleans stage without publishing target`() {
        val source = source()
        val target = root.resolve("target")
        var checks = 0
        assertThrows(CancellationException::class.java) {
            copyLocalNoReplace(source, target, false, commit, checkActive = {
                if (++checks == 3) throw CancellationException("cancelled")
            })
        }
        assertFalse(Files.exists(target))
        assertTrue(Files.exists(source))
        assertNoStaging()
    }

    @Test fun `missing source and non recursive directory never create target`() {
        val target = root.resolve("target")
        assertThrows(IOException::class.java) { copyLocalNoReplace(root.resolve("missing"), target, false, commit) }
        val source = Files.createDirectory(root.resolve("source"))
        assertThrows(IOException::class.java) { copyLocalNoReplace(source, target, false, commit) }
        assertFalse(Files.exists(target))
        assertNoStaging()
    }

    @Test fun `symlink source is rejected and dangling destination is not replaced`() {
        val source = source()
        val link = root.resolve("link")
        Files.createSymbolicLink(link, source)
        assertThrows(IOException::class.java) { copyLocalNoReplace(link, root.resolve("target"), false, commit) }
        val dangling = root.resolve("dangling")
        Files.createSymbolicLink(dangling, root.resolve("absent"))
        val error = assertThrows(LocalCopyException::class.java) { copyLocalNoReplace(source, dangling, false, commit) }
        assertEquals(FileCopyErrorCode.CONFLICT, error.code)
        assertTrue(Files.isSymbolicLink(dangling))
        assertNoStaging()
    }

    @Test fun `unsupported environments are rejected before touching local paths or native library`() = runTest {
        listOf("linux", "repo:documents", "saf").forEach { env ->
            val result = executeNoReplaceCopyTool(AITool("copy_file", listOf(
                ToolParameter("source", "/source"), ToolParameter("destination", "/target"),
                ToolParameter("source_environment", env), ToolParameter("copy_mode", "no_replace"),
            )))
            assertFalse(result.success)
            assertEquals(FileCopyErrorCode.UNSUPPORTED, (result.result as FileOperationData).errorCode)
        }
    }

    @Test fun `strict tool rejects unknown mode before commit`() = runTest {
        val result = executeNoReplaceCopyTool(AITool("copy_file", listOf(
            ToolParameter("source", "/source"), ToolParameter("destination", "/target"),
            ToolParameter("copy_mode", "replace_by_accident"),
        )), commit = { _, _ -> fail("Must not commit unsupported mode") })
        assertFalse(result.success)
        assertEquals(FileCopyErrorCode.UNSUPPORTED, (result.result as FileOperationData).errorCode)
    }
}
