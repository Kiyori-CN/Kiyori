package com.ai.assistance.operit.data.audit

import java.io.File
import java.nio.file.FileAlreadyExistsException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConversationAuditExportFileTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun `failed writer removes partial output and staging`() {
        val target = File(directory.root, "audit.md")
        try {
            writeConversationAuditExportFile(target) {
                it.writeText("partial")
                error("disk failure")
            }
            fail("Write must fail")
        } catch (_: IllegalStateException) { }
        assertFalse(target.exists())
        assertTrue(directory.root.listFiles()!!.isEmpty())
    }

    @Test fun `successful writer publishes complete output only`() {
        val target = File(directory.root, "audit.md")
        writeConversationAuditExportFile(target) { it.writeText("complete") }
        assertEquals("complete", target.readText())
        assertEquals(listOf("audit.md"), directory.root.list()!!.toList())
    }

    @Test fun `name collision preserves previous export`() {
        val target = directory.newFile("audit.md").apply { writeText("previous") }
        try {
            writeConversationAuditExportFile(target) { it.writeText("new") }
            fail("Existing export must not be overwritten")
        } catch (_: FileAlreadyExistsException) { }
        assertEquals("previous", target.readText())
        assertEquals(listOf("audit.md"), directory.root.list()!!.toList())
    }
}
