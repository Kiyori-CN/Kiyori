package com.ai.assistance.operit.core.tools.defaultTool.standard

import java.nio.file.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalFileRecycleBinTest {
    @get:Rule val folder = TemporaryFolder()
    private val root get() = folder.root.toPath()
    private val trash get() = root.resolve("trash")
    private val commit: (Path, Path) -> Unit = { from, to -> Files.move(from, to) }

    @Test fun `directory recycle restore and purge tolerate F2FS root mtime update`() = withAndroidManagedAttributes {
        val source = Files.createDirectories(root.resolve("办公套件测试_20260914/空目录")).parent
        Files.writeString(source.resolve("文档.txt"), "keep these bytes")
        val moving: (Path, Path) -> Unit = { from, to ->
            commit(from, to)
            // F2FS 跨父目录 rename 更新 ..，会更新被移动目录自身的 mtime。
            Files.setLastModifiedTime(to, java.nio.file.attribute.FileTime.fromMillis(Files.getLastModifiedTime(to).toMillis() + 10000))
        }
        LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint, moving)
        assertFalse(Files.exists(source))
        LocalFileRecycleBin.restore(LocalFileRecycleBin.list(trash).single(), moving)
        assertEquals("keep these bytes", Files.readString(source.resolve("文档.txt")))
        assertTrue(Files.isDirectory(source.resolve("空目录")))
        LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint, moving)
        LocalFileRecycleBin.delete(LocalFileRecycleBin.list(trash).single(), moving)
        assertFalse(Files.exists(source))
        assertTrue(LocalFileRecycleBin.list(trash).isEmpty())
    }

    @Test fun `empty directory survives recycle restore and permanent deletion`() {
        val source = Files.createDirectory(root.resolve("空文件夹"))
        LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint, commit)
        LocalFileRecycleBin.restore(LocalFileRecycleBin.list(trash).single(), commit)
        assertTrue(Files.isDirectory(source))
        assertTrue(source.toFile().list()!!.isEmpty())
        LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint, commit)
        LocalFileRecycleBin.delete(LocalFileRecycleBin.list(trash).single(), commit)
        assertTrue(LocalFileRecycleBin.list(trash).isEmpty())
        assertFalse(Files.exists(source))
    }

    @Test fun `directory child mutations during recycle are detected despite root time change`() = withAndroidManagedAttributes {
        val mutations: List<(Path) -> Unit> = listOf(
            { Files.writeString(it.resolve("new.txt"), "new") },
            { Files.delete(it.resolve("child.txt")) },
            { Files.move(it.resolve("child.txt"), it.resolve("renamed.txt")) },
            { Files.writeString(it.resolve("child.txt"), "new content") },
            { Files.setLastModifiedTime(it.resolve("child.txt"), java.nio.file.attribute.FileTime.fromMillis(1234567890000)) }
        )
        mutations.forEachIndexed { index, mutate ->
            val source = Files.createDirectory(root.resolve("case-$index"))
            Files.writeString(source.resolve("child.txt"), "original")
            var first = true
            val failure = assertThrows(IllegalStateException::class.java) {
                LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint) { from, to ->
                    commit(from, to)
                    if (first) {
                        first = false
                        mutate(to)
                        Files.setLastModifiedTime(to, java.nio.file.attribute.FileTime.fromMillis(1234567890000))
                    }
                }
            }
            assertTrue(failure.message!!.contains("已恢复原位置"))
            assertTrue(Files.isDirectory(source))
            when (index) {
                0 -> assertEquals("new", Files.readString(source.resolve("new.txt")))
                1 -> assertTrue(source.toFile().list()!!.isEmpty())
                2 -> assertEquals("original", Files.readString(source.resolve("renamed.txt")))
                3 -> assertEquals("new content", Files.readString(source.resolve("child.txt")))
                4 -> assertEquals("original", Files.readString(source.resolve("child.txt")))
            }
            assertTrue(LocalFileRecycleBin.list(trash).isEmpty())
        }
    }

    @Test fun `failed directory rollback preserves recycled changes and competing original path`() {
        val source = Files.createDirectory(root.resolve("directory"))
        Files.writeString(source.resolve("child.txt"), "old")
        var first = true
        val error = assertThrows(LocalCopyException::class.java) {
            LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint) { from, to ->
                commit(from, to)
                if (first) {
                    first = false
                    Files.writeString(to.resolve("child.txt"), "changed")
                    Files.writeString(source, "competing file")
                }
            }
        }
        assertEquals(FileCopyErrorCode.SOURCE_CHANGED, error.code)
        assertEquals("competing file", Files.readString(source))
        val record = LocalFileRecycleBin.list(trash).single()
        assertEquals(trash.resolve(record.id).toString(), error.stagingPath)
        assertEquals("changed", Files.readString(Paths.get(error.stagingPath!!).resolve("payload/child.txt")))
    }

    @Test fun `post move inspection failure reports retained directory instead of hiding content`() {
        val source = Files.createDirectory(root.resolve("directory"))
        Files.writeString(source.resolve("child.txt"), "keep")
        val other = Files.writeString(root.resolve("outside.txt"), "outside")
        val error = assertThrows(LocalCopyException::class.java) {
            LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint) { from, to ->
                commit(from, to)
                Files.createSymbolicLink(to.resolve("link"), other)
            }
        }
        assertNotNull(error.stagingPath)
        assertEquals("keep", Files.readString(Paths.get(error.stagingPath!!).resolve("payload/child.txt")))
        assertEquals("outside", Files.readString(other))
        assertEquals(1, LocalFileRecycleBin.list(trash).size)
    }

    @Test fun `shared storage recycling stays outside Android data while legacy roots remain readable`() {
        val context = org.mockito.kotlin.mock<android.content.Context>()
        val internal = Files.createDirectories(root.resolve("private/files")).toFile()
        val external = Files.createDirectories(root.resolve("volume/Android/data/com.kiyori/files")).toFile()
        org.mockito.kotlin.whenever(context.filesDir).thenReturn(internal)
        org.mockito.kotlin.whenever(context.getExternalFilesDir(null)).thenReturn(external)
        org.mockito.kotlin.whenever(context.getExternalFilesDirs(null)).thenReturn(arrayOf(external))
        val source = Files.writeString(root.resolve("volume/photo.png"), "image")
        val chosen = fileRecycleRootForSource(context, source)
        assertEquals(root.resolve("volume/.kiyori-recycle-bin/com.kiyori"), chosen)
        assertFalse(chosen.startsWith(root.resolve("volume/Android")))
        LocalFileRecycleBin.recycle(source, chosen, inspectManagedTree(source).fingerprint) { from, to ->
            // 模拟 Android/data 绑定挂载：旧目标返回 EXDEV，新目标应一次成功而非失败后降级。
            if (to.startsWith(root.resolve("volume/Android/data"))) throw java.io.IOException("EXDEV")
            commit(from, to)
        }
        assertFalse(Files.exists(source))
        val roots = fileRecycleRoots(context)
        assertTrue(roots.contains(external.toPath().resolve("file-recycle-bin")))
        assertTrue(roots.contains(chosen))
        LocalFileRecycleBin.restore(LocalFileRecycleBin.list(chosen).single(), commit)
        assertEquals("image", Files.readString(source))
        val privateSource = Files.writeString(internal.toPath().resolve("private.txt"), "private")
        assertEquals(internal.toPath().resolve("file-recycle-bin"), fileRecycleRootForSource(context, privateSource))
    }

    @Test fun `shared recycle directory is derived from each volume without matching similar names`() {
        val external = root.resolve("sd/Android/data/com.kiyori/files")
        assertEquals(root.resolve("sd/.kiyori-recycle-bin/com.kiyori"), sharedFileRecycleRoot(external))
        assertNull(sharedFileRecycleRoot(root.resolve("sd/MyAndroid/data/com.kiyori/files")))
        assertNull(sharedFileRecycleRoot(root.resolve("sd/Android/data/com.kiyori/cache")))
    }

    @Test fun `corrupt metadata remains visible without blocking valid restore`() {
        val good = Files.writeString(root.resolve("good"), "keep")
        LocalFileRecycleBin.recycle(good, trash, inspectManagedTree(good).fingerprint, commit)
        val bad = Files.createDirectory(trash.resolve(java.util.UUID.randomUUID().toString()))
        Files.writeString(bad.resolve("payload"), "also keep")
        Files.writeString(bad.resolve("record.properties"), "invalid record")
        val records = LocalFileRecycleBin.list(trash)
        assertEquals(2, records.size)
        val broken = records.single { it.problem != null }
        assertThrows(IllegalArgumentException::class.java) { LocalFileRecycleBin.restore(broken, commit) }
        LocalFileRecycleBin.restore(records.single { it.problem == null }, commit)
        assertEquals("keep", Files.readString(good))
        assertEquals("also keep", Files.readString(bad.resolve("payload")))
    }

    @Test fun `incomplete purge record remains visible for recovery`() {
        val entry = Files.createDirectories(trash.resolve(java.util.UUID.randomUUID().toString()))
        val retained = Files.createDirectories(entry.resolve(".kiyori-delete-retained"))
        Files.writeString(retained.resolve("payload"), "remaining")
        assertNotNull(LocalFileRecycleBin.list(trash).single().problem)
        assertEquals("remaining", Files.readString(retained.resolve("payload")))
    }

    @Test fun `oversized shared metadata is rejected without loading or deleting payload`() {
        val entry = Files.createDirectories(trash.resolve(java.util.UUID.randomUUID().toString()))
        Files.writeString(entry.resolve("payload"), "keep")
        Files.write(entry.resolve("record.properties"), ByteArray(70 * 1024) { 65 })
        val record = LocalFileRecycleBin.list(trash).single()
        assertTrue(record.problem!!.contains("大小限制"))
        assertEquals("keep", Files.readString(entry.resolve("payload")))
    }

    @Test fun `restore refuses content changed after confirmation`() {
        val source = Files.writeString(root.resolve("note"), "old")
        LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint, commit)
        val record = LocalFileRecycleBin.list(trash).single()
        val payload = trash.resolve(record.id).resolve("payload")
        val fingerprint = inspectManagedTree(payload).fingerprint
        Files.writeString(payload, "changed")
        assertThrows(IllegalStateException::class.java) { LocalFileRecycleBin.restore(record, commit, fingerprint) }
        assertFalse(Files.exists(source)); assertEquals("changed", Files.readString(payload))
    }
    @Test fun `recycle survives reload and restore retains bytes`() {
        val source = Files.writeString(root.resolve("note"), "hello")
        LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint, commit)
        assertFalse(Files.exists(source))
        val entry = LocalFileRecycleBin.list(trash).single()
        LocalFileRecycleBin.restore(entry, commit)
        assertEquals("hello", Files.readString(source)); assertTrue(LocalFileRecycleBin.list(trash).isEmpty())
    }
    @Test fun `restore conflict keeps both files`() {
        val source = Files.writeString(root.resolve("note"), "old")
        LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint, commit)
        Files.writeString(source, "new")
        val entry = LocalFileRecycleBin.list(trash).single()
        assertThrows(FileAlreadyExistsException::class.java) { LocalFileRecycleBin.restore(entry, commit) }
        assertEquals("new", Files.readString(source))
        assertEquals("old", Files.readString(trash.resolve(entry.id).resolve("payload")))
    }
    @Test fun `cross filesystem failure never deletes source`() {
        val source = Files.writeString(root.resolve("note"), "old")
        assertThrows(java.io.IOException::class.java) { LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint) { _, _ -> throw java.io.IOException("EXDEV") } }
        assertEquals("old", Files.readString(source)); assertTrue(LocalFileRecycleBin.list(trash).isEmpty())
    }
    @Test fun `changed source during commit is restored and requires new confirmation`() {
        val source = Files.writeString(root.resolve("note"), "old")
        var first = true
        assertThrows(IllegalStateException::class.java) { LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint) { from, to ->
            if (first) { Files.writeString(from, "updated"); first = false }; commit(from, to)
        } }
        assertEquals("updated", Files.readString(source)); assertTrue(LocalFileRecycleBin.list(trash).isEmpty())
    }
    @Test fun `permanent removal affects only chosen record`() {
        val source = Files.writeString(root.resolve("note"), "old")
        LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint, commit)
        LocalFileRecycleBin.delete(LocalFileRecycleBin.list(trash).single(), commit)
        assertTrue(LocalFileRecycleBin.list(trash).isEmpty()); assertFalse(Files.exists(source))
    }

    @Test fun `same names from separate folders retain independent records`() {
        val first = Files.createDirectory(root.resolve("first")).resolve("same.txt")
        val second = Files.createDirectory(root.resolve("second")).resolve("same.txt")
        listOf(first, second).forEachIndexed { index, source ->
            Files.writeString(source, "content $index")
            LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint, commit)
        }
        val entries = LocalFileRecycleBin.list(trash)
        assertEquals(2, entries.map { it.id }.distinct().size)
        entries.forEach { LocalFileRecycleBin.restore(it, commit) }
        assertEquals("content 0", Files.readString(first)); assertEquals("content 1", Files.readString(second))
    }

    @Test fun `missing original parent preserves recycled content`() {
        val parent = Files.createDirectory(root.resolve("parent"))
        val source = Files.writeString(parent.resolve("note"), "old")
        LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint, commit)
        Files.delete(parent)
        val entry = LocalFileRecycleBin.list(trash).single()
        assertThrows(IllegalStateException::class.java) { LocalFileRecycleBin.restore(entry, commit) }
        assertEquals("old", Files.readString(trash.resolve(entry.id).resolve("payload")))
    }

    @Test fun `recycling recycle content is refused without moving it`() {
        val source = Files.writeString(root.resolve("note"), "old")
        LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint, commit)
        val entry = LocalFileRecycleBin.list(trash).single()
        val payload = trash.resolve(entry.id).resolve("payload")
        assertThrows(IllegalArgumentException::class.java) { LocalFileRecycleBin.recycle(payload, trash, inspectManagedTree(payload).fingerprint, commit) }
        assertEquals("old", Files.readString(payload))
    }

    @Test fun `changed metadata cannot redirect an existing restore confirmation`() {
        val source = Files.writeString(root.resolve("note"), "old")
        LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint, commit)
        val entry = LocalFileRecycleBin.list(trash).single()
        assertThrows(IllegalStateException::class.java) { LocalFileRecycleBin.restore(entry.copy(originalPath = root.resolve("elsewhere").toString()), commit) }
        assertFalse(Files.exists(root.resolve("elsewhere")))
    }

    @Test fun `multiple sources produce one archive with correct names and bytes`() {
        val a = Files.writeString(root.resolve("a.txt"), "alpha")
        val directory = Files.createDirectory(root.resolve("folder"))
        Files.writeString(directory.resolve("b.txt"), "beta")
        val target = root.resolve("archive.zip")
        zipManagedEntries(listOf(a, directory), target, commit)
        java.util.zip.ZipFile(target.toFile()).use { zip ->
            assertEquals(setOf("a.txt", "folder/", "folder/b.txt"), zip.entries().asSequence().map { it.name }.toSet())
            assertEquals("alpha", zip.getInputStream(zip.getEntry("a.txt")).reader().readText())
            assertEquals("beta", zip.getInputStream(zip.getEntry("folder/b.txt")).reader().readText())
        }
        assertTrue(Files.exists(a)); assertTrue(Files.exists(directory.resolve("b.txt")))
    }

    @Test fun `permanent deletion uses the confirmed fingerprint instead of a fresh one`() {
        val source = Files.writeString(root.resolve("note"), "old")
        LocalFileRecycleBin.recycle(source, trash, inspectManagedTree(source).fingerprint, commit)
        val record = LocalFileRecycleBin.list(trash).single()
        val payload = trash.resolve(record.id).resolve("payload")
        val confirmed = inspectManagedTree(payload).fingerprint
        Files.writeString(payload, "replacement with different bytes")
        assertThrows(IllegalStateException::class.java) { LocalFileRecycleBin.delete(record, commit, confirmed) }
        assertEquals("replacement with different bytes", Files.readString(payload))
    }
}
