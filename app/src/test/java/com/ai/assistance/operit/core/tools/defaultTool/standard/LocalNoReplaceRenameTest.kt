package com.ai.assistance.operit.core.tools.defaultTool.standard

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class LocalNoReplaceRenameTest {
    @get:Rule val folder = TemporaryFolder()
    private val root get() = folder.root.toPath()
    private val commit: (Path, Path) -> Unit = { source, target -> Files.move(source, target) }

    @Test fun `file rename preserves bytes and removes only old name`() {
        val source = root.resolve("原名 📄.txt")
        Files.write(source, byteArrayOf(0, 1, 2, 3))
        val target = root.resolve("新名 📄.txt")
        renameLocalNoReplace(source, target, commit)
        assertFalse(Files.exists(source))
        assertArrayEquals(byteArrayOf(0, 1, 2, 3), Files.readAllBytes(target))
    }

    @Test fun `directory rename preserves descendants`() {
        val source = Files.createDirectories(root.resolve("old/deep")).parent
        Files.write(source.resolve("deep/data"), byteArrayOf(5))
        val target = root.resolve("new")
        renameLocalNoReplace(source, target, commit)
        assertFalse(Files.exists(source))
        assertArrayEquals(byteArrayOf(5), Files.readAllBytes(target.resolve("deep/data")))
    }

    @Test fun `existing target preserves both source and destination`() {
        val source = root.resolve("source")
        val target = root.resolve("target")
        Files.write(source, byteArrayOf(1))
        Files.write(target, byteArrayOf(2))
        val error = assertThrows(LocalCopyException::class.java) { renameLocalNoReplace(source, target, commit) }
        assertEquals(FileCopyErrorCode.CONFLICT, error.code)
        assertArrayEquals(byteArrayOf(1), Files.readAllBytes(source))
        assertArrayEquals(byteArrayOf(2), Files.readAllBytes(target))
    }

    @Test fun `competing target at commit cannot trigger copy delete fallback`() {
        val source = root.resolve("source")
        val target = root.resolve("target")
        Files.write(source, byteArrayOf(1))
        assertThrows(java.nio.file.FileAlreadyExistsException::class.java) {
            renameLocalNoReplace(source, target) { from, to ->
                Files.write(to, byteArrayOf(2))
                commit(from, to)
            }
        }
        assertArrayEquals(byteArrayOf(1), Files.readAllBytes(source))
        assertArrayEquals(byteArrayOf(2), Files.readAllBytes(target))
    }

    @Test fun `different parent and identical name cannot invoke commit`() {
        val source = root.resolve("source")
        Files.write(source, byteArrayOf(1))
        val target = Files.createDirectory(root.resolve("elsewhere")).resolve("target")
        val rejected: (Path, Path) -> Unit = { _, _ -> fail("Must not commit") }
        assertThrows(IllegalArgumentException::class.java) { renameLocalNoReplace(source, target, rejected) }
        assertThrows(IllegalArgumentException::class.java) { renameLocalNoReplace(source, source, rejected) }
        assertTrue(Files.exists(source))
    }

    @Test fun `symlink source is rejected without renaming its target`() {
        val real = root.resolve("real")
        Files.write(real, byteArrayOf(1))
        val source = Files.createSymbolicLink(root.resolve("link"), real)
        assertThrows(java.io.IOException::class.java) { renameLocalNoReplace(source, root.resolve("new"), commit) }
        assertTrue(Files.isSymbolicLink(source))
        assertArrayEquals(byteArrayOf(1), Files.readAllBytes(real))
    }
}
