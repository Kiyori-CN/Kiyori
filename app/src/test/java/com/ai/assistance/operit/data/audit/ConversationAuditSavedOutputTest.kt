package com.ai.assistance.operit.data.audit

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConversationAuditSavedOutputTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun result(path: File, windows: Boolean = false) = JSONObject()
        .put(if (windows) "outputSavedTo" else "output_saved_to", path.absolutePath).toString()

    @Test fun savedSshTextSurvivesDeletionOfTransientOutput() {
        val file = temporary.newFile("linux_ssh_exec_output_1.log").apply { writeText("full remote output\n尾部") }
        val capture = ConversationAuditSavedOutput.capture("linux_ssh:linux_ssh_exec", result(file), temporary.root)
        assertTrue(capture.complete)
        assertTrue(file.delete())
        assertEquals("full remote output\n尾部", capture.payloads.first().bytes.toString(Charsets.UTF_8))
    }

    @Test fun windowsStdoutAndStderrFileIsCaptured() {
        val file = temporary.newFile("windows_exec_output_1.log").apply { writeText("stdout\n--- stderr ---\nfailure") }
        val capture = ConversationAuditSavedOutput.capture("windows_control:windows_exec", result(file, true), temporary.root)
        assertTrue(capture.complete)
        assertTrue(capture.payloads.first().bytes.toString(Charsets.UTF_8).contains("stderr"))
    }

    @Test fun pathsOutsideManagedDirectoryAreNeverRead() {
        val root = temporary.newFolder("managed")
        val outside = temporary.newFile("linux_ssh_exec_output_private.log").apply { writeText("private content") }
        val capture = ConversationAuditSavedOutput.capture("linux_ssh:linux_ssh_exec", result(outside), root)
        assertFalse(capture.complete)
        assertEquals(1, capture.payloads.size)
        assertTrue(capture.payloads.single().bytes.toString(Charsets.UTF_8).contains("REJECTED_PATH"))
    }

    @Test fun missingOrOversizedOutputIsAnExplicitGap() {
        val missing = File(temporary.root, "linux_ssh_missing.log")
        assertFalse(ConversationAuditSavedOutput.capture("linux_ssh:linux_ssh_exec", result(missing), temporary.root).complete)
        val huge = temporary.newFile("linux_ssh_large.log")
        java.io.RandomAccessFile(huge, "rw").use { it.setLength(8L * 1024 * 1024 + 1) }
        val capture = ConversationAuditSavedOutput.capture("linux_ssh:linux_ssh_exec", result(huge), temporary.root)
        assertFalse(capture.complete)
        assertTrue(capture.payloads.single().bytes.toString(Charsets.UTF_8).contains("EXCEEDS_LIMIT"))
    }

    @Test fun unrelatedToolCannotRequestFileCapture() {
        val file = temporary.newFile("linux_ssh_fake.log")
        assertTrue(ConversationAuditSavedOutput.capture("other:tool", result(file), temporary.root).payloads.isEmpty())
        assertTrue(ConversationAuditSavedOutput.capture("linux_ssh:linux_ssh_exec", "normal output", temporary.root).payloads.isEmpty())
    }
}
