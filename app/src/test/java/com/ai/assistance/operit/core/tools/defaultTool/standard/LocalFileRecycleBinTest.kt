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
