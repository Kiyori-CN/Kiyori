package com.kiyori.platform.storage

import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TemporaryDirectoryCleanerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `cleanup preserves only the root marker and reports deleted files`() {
        val root = temporary.newFolder("owned")
        root.resolve(".nomedia").writeText("keep")
        root.resolve("nested").mkdir()
        root.resolve("nested/.nomedia").writeText("remove")
        root.resolve("nested/file").writeText("remove")
        assertEquals(2, TemporaryDirectoryCleaner.clean(root, true))
        assertEquals(listOf(".nomedia"), root.list()!!.toList())
        assertEquals("keep", root.resolve(".nomedia").readText())
        assertEquals(0, TemporaryDirectoryCleaner.clean(root, true))
        assertEquals(1, TemporaryDirectoryCleaner.clean(root, false))
        assertTrue(root.isDirectory)
        assertEquals(0, root.list()!!.size)
    }

    @Test fun `missing root stays absent and existing root receives marker`() {
        val absent = temporary.root.resolve("absent")
        assertEquals(0, TemporaryDirectoryCleaner.clean(absent, true))
        assertFalse(absent.exists())
        val root = temporary.newFolder("empty")
        assertEquals(0, TemporaryDirectoryCleaner.clean(root, true))
        assertTrue(root.resolve(".nomedia").isFile)
    }

    @Test fun `directory links and cycles are removed without touching their targets`() {
        val root = temporary.newFolder("owned").toPath()
        val external = temporary.newFolder("outside").toPath()
        val retained = Files.writeString(external.resolve("keep"), "original")
        try {
            Files.createSymbolicLink(root.resolve("external"), external)
        } catch (error: IOException) {
            assumeNoException("Host must allow symbolic link creation", error)
        }
        Files.createSymbolicLink(root.resolve("cycle"), root)
        assertEquals(2, TemporaryDirectoryCleaner.clean(root.toFile(), false))
        assertEquals("original", Files.readString(retained))
        assertEquals(0, root.toFile().list()!!.size)
    }

    @Test fun `symbolic root is refused instead of cleaning another directory`() {
        val target = temporary.newFolder("target").toPath()
        Files.writeString(target.resolve("keep"), "original")
        val link = temporary.root.toPath().resolve("link")
        try {
            Files.createSymbolicLink(link, target)
        } catch (error: IOException) {
            assumeNoException("Host must allow symbolic link creation", error)
        }
        assertThrows(IOException::class.java) { TemporaryDirectoryCleaner.clean(link.toFile(), true) }
        assertEquals("original", Files.readString(target.resolve("keep")))
        Files.delete(link)
    }
}
