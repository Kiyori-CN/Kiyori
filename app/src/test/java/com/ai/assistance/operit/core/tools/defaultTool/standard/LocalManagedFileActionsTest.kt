package com.ai.assistance.operit.core.tools.defaultTool.standard

import java.nio.file.*
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Windows NIO 不提供 fileKey；只在故障注入中投影 Android 的稳定身份和 mtime 别名。
 * 真实目录遍历、读写、移动与删除继续执行，Linux 使用原 key，Windows 用稳定 birthtime 作为夹具身份。
 */
internal fun withAndroidManagedAttributes(block: () -> Unit) {
    fun androidAttributes(attributes: java.nio.file.attribute.BasicFileAttributes) =
        object : java.nio.file.attribute.BasicFileAttributes by attributes {
            override fun fileKey(): Any = attributes.fileKey() ?: attributes.creationTime()
            override fun creationTime() = attributes.lastModifiedTime()
        }
    var walking = false
    org.mockito.Mockito.mockStatic(Files::class.java) { invocation ->
        if (invocation.method.name == "walkFileTree" && invocation.arguments.size == 4 && !walking) {
            val visitor = invocation.getArgument<FileVisitor<Path>>(3)
            // Windows 目录流可直接提供缓存属性，绕过 Files.readAttributes；两种读取路径须一致。
            val androidVisitor = object : FileVisitor<Path> by visitor {
                override fun preVisitDirectory(dir: Path, attrs: java.nio.file.attribute.BasicFileAttributes) =
                    visitor.preVisitDirectory(dir, androidAttributes(attrs))
                override fun visitFile(file: Path, attrs: java.nio.file.attribute.BasicFileAttributes) =
                    visitor.visitFile(file, androidAttributes(attrs))
            }
            walking = true
            try { Files.walkFileTree(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), androidVisitor) }
            finally { walking = false }
        } else {
            val result = invocation.callRealMethod()
            if (invocation.method.name == "readAttributes" && result is java.nio.file.attribute.BasicFileAttributes)
                androidAttributes(result) else result
        }
    }.use { block() }
}

class LocalManagedFileActionsTest {

    @Test fun `Android directory creation time alias does not reject own child deletions`() {
        fun attributes(modified: Long, key: String, created: Long = modified): java.nio.file.attribute.BasicFileAttributes {
            val attributes = org.mockito.kotlin.mock<java.nio.file.attribute.BasicFileAttributes>()
            org.mockito.kotlin.whenever(attributes.isDirectory).thenReturn(true)
            org.mockito.kotlin.whenever(attributes.fileKey()).thenReturn(key)
            org.mockito.kotlin.whenever(attributes.lastModifiedTime()).thenReturn(java.nio.file.attribute.FileTime.fromMillis(modified))
            org.mockito.kotlin.whenever(attributes.creationTime()).thenReturn(java.nio.file.attribute.FileTime.fromMillis(created))
            return attributes
        }
        assertTrue(sameManagedDirectoryIdentity(attributes(10, "inode"), attributes(20, "inode")))
        assertFalse(sameManagedDirectoryIdentity(attributes(10, "inode"), attributes(20, "replacement")))
        assertFalse(sameManagedDirectoryIdentity(attributes(10, "inode", 1), attributes(20, "inode", 2)))
        assertTrue(sameManagedDirectoryIdentity(attributes(10, "inode", 1), attributes(20, "inode", 1)))
    }
    @get:Rule val folder = TemporaryFolder()
    private val root get() = folder.root.toPath()
    private val commit: (Path, Path) -> Unit = { from, to -> Files.move(from, to) }
    private fun attributes(directory: Boolean = true, key: String? = "inode", modified: Long = 10,
        created: Long = modified, size: Long = 0): java.nio.file.attribute.BasicFileAttributes =
        org.mockito.kotlin.mock<java.nio.file.attribute.BasicFileAttributes>().also {
            org.mockito.kotlin.whenever(it.isDirectory).thenReturn(directory)
            org.mockito.kotlin.whenever(it.isRegularFile).thenReturn(!directory)
            org.mockito.kotlin.whenever(it.fileKey()).thenReturn(key)
            org.mockito.kotlin.whenever(it.lastModifiedTime()).thenReturn(java.nio.file.attribute.FileTime.fromMillis(modified))
            org.mockito.kotlin.whenever(it.creationTime()).thenReturn(java.nio.file.attribute.FileTime.fromMillis(created))
            org.mockito.kotlin.whenever(it.size()).thenReturn(size)
        }

    private fun snapshot(vararg entries: Pair<String, java.nio.file.attribute.BasicFileAttributes>) =
        ManagedSnapshot(entries.map { ManagedEntry(it.first, it.second) }.sortedBy { it.relative }, "unused")

    @Test fun `moved root accepts Android mtime alias and stable true birth time`() {
        assertNull(managedTreeChangeAfterMove(snapshot("" to attributes()), snapshot("" to attributes(modified = 20))))
        assertNull(managedTreeChangeAfterMove(snapshot("" to attributes(created = 1)), snapshot("" to attributes(modified = 20, created = 1))))
    }

    @Test fun `moved root rejects replacement missing identity size type and true birth time changes`() {
        val before = snapshot("" to attributes())
        listOf(attributes(key = "replacement", modified = 20), attributes(size = 1, modified = 20),
            attributes(directory = false), attributes(created = 2, modified = 20)).forEach { changed ->
            assertNotNull(managedTreeChangeAfterMove(before, snapshot("" to changed)))
        }
        assertNotNull(managedTreeChangeAfterMove(snapshot("" to attributes(key = null)), snapshot("" to attributes(key = null, modified = 20))))
        // 无 fileKey 的兼容读取仍可完整比较；只有时间变化时不能套用根目录豁免。
        assertNull(managedTreeChangeAfterMove(snapshot("" to attributes(key = null)), snapshot("" to attributes(key = null))))
        assertNotNull(managedTreeChangeAfterMove(snapshot("" to attributes(created = 1)), snapshot("" to attributes(created = 2))))
    }

    @Test fun `root regular files and descendant directories retain strict timestamps`() {
        assertNotNull(managedTreeChangeAfterMove(snapshot("" to attributes(directory = false)),
            snapshot("" to attributes(directory = false, modified = 20))))
        assertNotNull(managedTreeChangeAfterMove(snapshot("" to attributes(), "child" to attributes()),
            snapshot("" to attributes(modified = 20), "child" to attributes(modified = 20))))
    }

    @Test fun `moved tree compares every relative path and entry count`() {
        val before = snapshot("" to attributes(), "old" to attributes(directory = false))
        assertNotNull(managedTreeChangeAfterMove(before, snapshot("" to attributes())))
        assertNotNull(managedTreeChangeAfterMove(before, snapshot("" to attributes(), "renamed" to attributes(directory = false))))
        assertNotNull(managedTreeChangeAfterMove(before, snapshot("" to attributes(), "old" to attributes(directory = false), "new" to attributes())))
    }

    private fun tree(): Path = Files.createDirectories(root.resolve("资料/empty")).parent.also {
        Files.write(it.resolve("内容.txt"), "abc".toByteArray())
    }
    @Test fun `permanent directory delete tolerates F2FS root mtime update during isolation`() = withAndroidManagedAttributes {
        val source = tree()
        deleteManagedEntry(source, inspectManagedTree(source).fingerprint) { from, to ->
            commit(from, to)
            Files.setLastModifiedTime(to, java.nio.file.attribute.FileTime.fromMillis(1234567890000))
        }
        assertFalse(Files.exists(source))
        assertTrue(folder.root.list()!!.isEmpty())
    }

    @Test fun `real descendant changes during directory isolation are restored without deleting bytes`() = withAndroidManagedAttributes {
        val source = tree()
        var first = true
        val error = assertThrows(java.io.IOException::class.java) {
            deleteManagedEntry(source, inspectManagedTree(source).fingerprint) { from, to ->
                commit(from, to)
                if (first) {
                    first = false
                    Files.writeString(to.resolve("内容.txt"), "changed bytes")
                    Files.setLastModifiedTime(to, java.nio.file.attribute.FileTime.fromMillis(1234567890000))
                }
            }
        }
        assertTrue(error.message!!.contains("内容.txt"))
        assertEquals("changed bytes", Files.readString(source.resolve("内容.txt")))
        assertTrue(Files.isDirectory(source.resolve("empty")))
        assertEquals(listOf("资料"), folder.root.list()!!.toList())
    }

    @Test fun `replacement created at original path survives successful directory deletion`() = withAndroidManagedAttributes {
        val source = tree()
        deleteManagedEntry(source, inspectManagedTree(source).fingerprint) { from, to ->
            commit(from, to)
            Files.createDirectory(source)
            Files.writeString(source.resolve("new.txt"), "new owner")
            Files.setLastModifiedTime(to, java.nio.file.attribute.FileTime.fromMillis(1234567890000))
        }
        assertEquals("new owner", Files.readString(source.resolve("new.txt")))
        assertEquals(listOf("new.txt"), source.toFile().list()!!.toList())
        assertEquals(listOf("资料"), folder.root.list()!!.toList())
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
