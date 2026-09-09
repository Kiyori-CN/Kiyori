package com.ai.assistance.operit.core.subpack

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class WorkspaceExportTreeTest {
    @Test fun collectsFilesAndEmptyDirectoriesWithStableRelativeNames() {
        val root = Files.createTempDirectory("export-tree").toFile()
        try {
            root.resolve("empty").mkdir()
            root.resolve("index.html").writeText("content")
            assertEquals(listOf("empty", "index.html"), collectWorkspaceExportEntries(root).map { it.relativePath })
        } finally { root.deleteRecursively() }
    }

    @Test fun refusesExternalSymlinkAndDirectoryCycle() {
        val fixture = Files.createTempDirectory("export-links")
        val root = Files.createDirectory(fixture.resolve("workspace"))
        val outside = Files.writeString(fixture.resolve("outside.txt"), "outside")
        val link = root.resolve("link")
        try {
            Files.createSymbolicLink(link, outside)
            assertTrue(runCatching { collectWorkspaceExportEntries(root.toFile()) }.isFailure)
            Files.delete(link)
            Files.createSymbolicLink(link, root)
            assertTrue(runCatching { collectWorkspaceExportEntries(root.toFile()) }.isFailure)
        } finally {
            Files.deleteIfExists(link)
            fixture.toFile().deleteRecursively()
        }
    }

    @Test fun preservesAnInternalFileAliasAndPropagatesCancellation() {
        val root = Files.createTempDirectory("export-alias")
        val original = Files.writeString(root.resolve("index.html"), "content")
        val link = root.resolve("alias.html")
        try {
            Files.createSymbolicLink(link, original)
            assertEquals(listOf("alias.html", "index.html"), collectWorkspaceExportEntries(root.toFile()).map { it.relativePath })
            val cancelled = java.util.concurrent.CancellationException("cancelled")
            assertSame(cancelled, runCatching { collectWorkspaceExportEntries(root.toFile()) { throw cancelled } }.exceptionOrNull())
        } finally {
            Files.deleteIfExists(link)
            root.toFile().deleteRecursively()
        }
    }
}
