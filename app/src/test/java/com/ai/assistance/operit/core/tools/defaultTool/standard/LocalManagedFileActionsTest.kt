package com.ai.assistance.operit.core.tools.defaultTool.standard

import java.nio.file.*
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalManagedFileActionsTest {
    @get:Rule val folder = TemporaryFolder()
    private val root get() = folder.root.toPath()
    private val commit: (Path, Path) -> Unit = { from, to -> Files.move(from, to) }
    private fun tree(): Path = Files.createDirectories(root.resolve("资料/empty")).parent.also {
        Files.write(it.resolve("内容.txt"), "abc".toByteArray())
    }
    @Test fun `inspection counts full tree and metadata changes invalidate fingerprint`() {
        val source = tree()
        val before = inspectManagedTree(source)
        assertEquals(3L, before.bytes)
        assertEquals(3, before.entries.size)
        Files.write(source.resolve("new"), byteArrayOf(4))
        assertNotEquals(before.fingerprint, inspectManagedTree(source).fingerprint)
    }
    @Test fun `confirmed delete removes only chosen tree including empty directories`() {
        val source = tree(); val sibling = Files.write(root.resolve("keep"), byteArrayOf(9))
        deleteManagedEntry(source, inspectManagedTree(source).fingerprint, commit)
        assertFalse(Files.exists(source)); assertArrayEquals(byteArrayOf(9), Files.readAllBytes(sibling))
        assertEquals(listOf("keep"), folder.root.list()!!.toList())
    }
    @Test fun `changed tree is rejected before isolation or deletion`() {
        val source = tree(); val fingerprint = inspectManagedTree(source).fingerprint
        Files.write(source.resolve("late"), byteArrayOf(1))
        assertThrows(IllegalStateException::class.java) { deleteManagedEntry(source, fingerprint) { _, _ -> fail("must not commit") } }
        assertTrue(Files.exists(source.resolve("late")))
    }
    @Test fun `replacement during isolation restores source and does not delete it`() {
        val source = Files.write(root.resolve("note"), byteArrayOf(1))
        val fingerprint = inspectManagedTree(source).fingerprint
        var first = true
        assertThrows(java.io.IOException::class.java) {
            deleteManagedEntry(source, fingerprint) { from, to ->
                if (first) { Files.write(from, byteArrayOf(2, 3)); first = false }
                commit(from, to)
            }
        }
        assertArrayEquals(byteArrayOf(2, 3), Files.readAllBytes(source))
        assertEquals(1, folder.root.list()!!.size)
    }
    @Test fun `failed restore retains isolated data and never overwrites competing source`() {
        val source = Files.write(root.resolve("note"), byteArrayOf(1))
        val fingerprint = inspectManagedTree(source).fingerprint
        var first = true
        val error = assertThrows(LocalCopyException::class.java) {
            deleteManagedEntry(source, fingerprint) { from, to ->
                if (first) {
                    Files.write(from, byteArrayOf(2, 3)); commit(from, to)
                    Files.write(source, byteArrayOf(9)); first = false
                } else commit(from, to)
            }
        }
        assertNotNull(error.stagingPath)
        assertArrayEquals(byteArrayOf(9), Files.readAllBytes(source))
        assertArrayEquals(byteArrayOf(2, 3), Files.readAllBytes(Paths.get(error.stagingPath!!).resolve("payload")))
    }
    @Test fun `ZIP preserves root unicode empty folders and bytes`() {
        val source = tree(); val target = root.resolve("archive.zip")
        zipManagedEntry(source, target, commit)
        ZipFile(target.toFile()).use { zip ->
            assertNotNull(zip.getEntry("资料/empty/"))
            assertEquals("abc", zip.getInputStream(zip.getEntry("资料/内容.txt")).reader().readText())
        }
        assertTrue(Files.exists(source)); assertEquals(2, folder.root.list()!!.size)
    }
    @Test fun `ZIP target conflict preserves existing archive and cleans staging`() {
        val source = tree(); val target = Files.write(root.resolve("archive.zip"), byteArrayOf(7))
        assertThrows(FileAlreadyExistsException::class.java) { zipManagedEntry(source, target, commit) }
        assertArrayEquals(byteArrayOf(7), Files.readAllBytes(target)); assertEquals(2, folder.root.list()!!.size)
    }
    @Test fun `ZIP rejects destination inside source and linked inputs`() {
        val source = tree()
        assertThrows(IllegalArgumentException::class.java) { zipManagedEntry(source, source.resolve("archive.zip"), commit) }
        val external = Files.write(root.resolve("external"), byteArrayOf(9))
        Files.createSymbolicLink(source.resolve("link"), external)
        assertThrows(IllegalArgumentException::class.java) { inspectManagedTree(source) }
        assertArrayEquals(byteArrayOf(9), Files.readAllBytes(external))
    }
    @Test fun `SHA256 is content hash and cancelled inspection stops`() {
        val source = Files.write(root.resolve("abc"), "abc".toByteArray())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", managedSha256(source))
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { inspectManagedTree(source) { throw kotlinx.coroutines.CancellationException() } }
    }
    @Test fun `atomic move supports another directory without deleting or merging targets`() {
        val source = tree(); val target = Files.createDirectory(root.resolve("other")).resolve("moved")
        moveLocalNoReplace(source, target, commit = commit)
        assertFalse(Files.exists(source)); assertEquals("abc", Files.readString(target.resolve("内容.txt")))
    }
    @Test fun `atomic move rejects descendant and leaves source on cross filesystem failure`() {
        val source = tree()
        assertThrows(IllegalArgumentException::class.java) { moveLocalNoReplace(source, source.resolve("nested"), commit = commit) }
        val target = Files.createDirectory(root.resolve("other")).resolve("moved")
        assertThrows(java.io.IOException::class.java) { moveLocalNoReplace(source, target) { _, _ -> throw java.io.IOException("EXDEV") } }
        assertTrue(Files.exists(source.resolve("内容.txt"))); assertFalse(Files.exists(target))
    }
}
