package com.ai.assistance.operit.ui.features.chat.webview.workspace

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceCreationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `existing workspace is never overwritten`() {
        val root = temporary.newFolder()
        val existing = File(root, "chat").apply { mkdir() }
        File(existing, "user.txt").writeText("user work")
        try {
            createWorkspaceDirectorySafely(root, "chat") { fail("Must not populate an existing workspace") }
            fail("Existing workspace must be rejected")
        } catch (_: IOException) { }
        assertEquals("user work", File(existing, "user.txt").readText())
    }

    @Test fun `failed template never publishes partial workspace`() {
        val root = temporary.newFolder()
        try {
            createWorkspaceDirectorySafely(root, "chat") {
                File(it, "partial").writeText("partial")
                throw IOException("template failed")
            }
            fail("Template failure must propagate")
        } catch (_: IOException) { }
        assertTrue(root.listFiles()!!.isEmpty())
    }

    @Test fun `successful template is published at final path`() {
        val root = temporary.newFolder()
        val workspace = createWorkspaceDirectorySafely(root, "chat") { File(it, "main.txt").writeText("ready") }
        assertEquals(File(root, "chat"), workspace)
        assertEquals("ready", File(workspace, "main.txt").readText())
        assertEquals(listOf("chat"), root.list()!!.toList())
    }
}
