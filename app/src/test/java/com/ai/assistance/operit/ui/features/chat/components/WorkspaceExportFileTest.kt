package com.ai.assistance.operit.ui.features.chat.components

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class WorkspaceExportFileTest {
    @Test fun onlyCompletedFileIsPublished() {
        val directory = Files.createTempDirectory("workspace-export").toFile()
        try {
            val export = WorkspaceExportFile(directory, "App", "zip")
            assertFalse(export.destination.exists())
            assertTrue(runCatching { export.publish() }.isFailure)
            export.temporary.writeText("complete")
            assertEquals("complete", export.publish().readText())
            assertFalse(export.temporary.exists())
        } finally { directory.deleteRecursively() }
    }
    @Test fun existingResultIsNeverReplaced() {
        val directory = Files.createTempDirectory("workspace-export").toFile()
        try {
            val export = WorkspaceExportFile(directory, "App", "zip")
            export.destination.writeText("original")
            export.temporary.writeText("new")
            assertTrue(runCatching { export.publish() }.isFailure)
            assertEquals("original", export.destination.readText())
            assertEquals("new", export.temporary.readText())
        } finally { directory.deleteRecursively() }
    }
}
