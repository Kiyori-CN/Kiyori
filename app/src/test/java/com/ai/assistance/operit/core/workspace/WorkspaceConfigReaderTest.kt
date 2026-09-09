package com.ai.assistance.operit.core.workspace

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class WorkspaceConfigReaderTest {
    @Test fun onlyAbsentConfigurationUsesTheExistingWebDefault() {
        val root = Files.createTempDirectory("workspace-config").toFile()
        try {
            assertTrue(WorkspaceConfigReader.readConfig(root.path).server.enabled)
            root.resolve(".operit").mkdir()
            root.resolve(".operit/config.json").writeText("""{"server":{"enabled":false},"commands":[{"id":"build","label":"Build","command":"make","workingDir":"src"}]}""")
            val config = WorkspaceConfigReader.readConfig(root.path)
            assertFalse(config.server.enabled)
            assertEquals("src", config.commands.single().workingDir)
        } finally { root.deleteRecursively() }
    }

    @Test fun malformedConfigurationDoesNotBecomeDefaultOrLeakItsContent() {
        val error = runCatching { WorkspaceConfigReader.parseConfig("{private-content: \"unfinished") }.exceptionOrNull()
        assertNotNull(error)
        assertFalse(error.toString().contains("private-content"))
    }

    @Test fun unreadableConfigurationObjectAndMissingWorkspaceFail() {
        val root = Files.createTempDirectory("workspace-config-invalid").toFile()
        try {
            root.resolve(".operit/config.json").mkdirs()
            assertTrue(runCatching { WorkspaceConfigReader.readConfig(root.path) }.isFailure)
            assertTrue(runCatching { WorkspaceConfigReader.readConfig(root.resolve("absent").path) }.isFailure)
        } finally { root.deleteRecursively() }
    }

    @Test fun danglingConfigLinkIsNotAnAbsentConfiguration() {
        val root = Files.createTempDirectory("workspace-config-link")
        val folder = Files.createDirectory(root.resolve(".operit"))
        val link = folder.resolve("config.json")
        try {
            Files.createSymbolicLink(link, root.resolve("missing.json"))
            assertTrue(runCatching { WorkspaceConfigReader.readConfig(root.toString()) }.isFailure)
        } finally {
            Files.deleteIfExists(link)
            root.toFile().deleteRecursively()
        }
    }
}
