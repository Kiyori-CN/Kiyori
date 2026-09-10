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
}
