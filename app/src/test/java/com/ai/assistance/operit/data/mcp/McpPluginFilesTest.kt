package com.ai.assistance.operit.data.mcp

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class McpPluginFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun archive(vararg names: String): File = temporary.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { output -> names.forEach { name ->
            output.putNextEntry(ZipEntry(name))
            if (!name.endsWith('/')) output.write("content".toByteArray())
            output.closeEntry()
        } }
    }

    @Test fun `zip is preflighted before writing any file`() {
        for (bad in listOf("../escape", "/absolute", "a/../../escape", "a\\escape", "C:/escape")) {
            val target = temporary.newFolder()
            try { extractMcpPluginZip(archive("valid.txt", bad), target); fail("Escaping entry accepted") }
            catch (_: IllegalArgumentException) { }
            assertTrue(target.listFiles()!!.isEmpty())
        }
    }

    @Test fun `complete archive preserves hierarchy and cancellation propagates`() {
        val target = temporary.newFolder()
        extractMcpPluginZip(archive("project/", "project/main.py"), target)
        assertEquals("content", File(target, "project/main.py").readText())
        try { extractMcpPluginZip(archive("file"), temporary.newFolder(), { throw CancellationException() }); fail("Cancellation swallowed") }
        catch (_: CancellationException) { }
    }

    @Test fun `replacement only publishes a complete prepared directory`() {
        val root = temporary.newFolder()
        val old = File(root, "plugin").apply { mkdir(); resolve("old").writeText("original") }
        val staging = Files.createTempDirectory(root.toPath(), ".mcp_install_").toFile().apply { resolve("new").writeText("complete") }
        assertEquals("original", old.resolve("old").readText())
        val published = publishMcpPluginDirectory(staging, root, "plugin")
        assertEquals("complete", published.resolve("new").readText())
        assertFalse(staging.exists())
        assertEquals(listOf("plugin"), root.list()!!.toList())
    }

    @Test fun `plugin IDs preserve nested compatibility but reject traversal`() {
        val root = temporary.newFolder()
        assertEquals(File(root, "owner/plugin"), resolveMcpPluginDirectory(root, "owner/plugin"))
        for (id in listOf("../other", "/other", "owner/../other", "owner\\other", ".mcp_install_hidden", "mcp_config.json", "server_status.json")) {
            try { resolveMcpPluginDirectory(root, id); fail("Invalid ID accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun `installation cannot replace an existing unrelated file`() {
        val root = temporary.newFolder()
        val existing = File(root, "keep").apply { writeText("original") }
        val staging = Files.createTempDirectory(root.toPath(), ".mcp_install_").toFile()
        try { publishMcpPluginDirectory(staging, root, "keep"); fail("File replaced") }
        catch (_: IllegalArgumentException) { }
        assertEquals("original", existing.readText())
    }

    @Test fun `deletion does not follow a link to other files`() {
        val root = temporary.newFolder()
        val outside = temporary.newFolder().apply { resolve("keep").writeText("original") }
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        deleteMcpPluginTree(root)
        assertEquals("original", outside.resolve("keep").readText())
    }
}
