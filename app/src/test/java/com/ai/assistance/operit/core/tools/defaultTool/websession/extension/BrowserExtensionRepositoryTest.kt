package com.ai.assistance.operit.core.tools.defaultTool.websession.extension

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BrowserExtensionRepositoryTest {
    @Test fun agentLifecycleSurvivesRestartAndRejectsStaleUpdatesAndDeletion() {
        var persisted: String? = null
        fun open() = BrowserExtensionRepository(File("unused"), { null }, { persisted }, { _, text -> persisted = text })
        var repo = open()
        val first = repo.install(extensionFixture(), null)
        assertFalse(first.enabled)
        repo.setEnabled(first.id, true, first.revision)
        repo = open()
        assertTrue(repo.get(first.id).enabled)
        assertThrows(IllegalArgumentException::class.java) { repo.install(extensionFixture("1.1"), null) }
        val revised = repo.install(extensionFixture("1.1"), first.revision)
        assertFalse(revised.enabled)
        assertThrows(IllegalArgumentException::class.java) { repo.setEnabled(first.id, true, first.revision) }
        assertThrows(IllegalArgumentException::class.java) { repo.delete(first.id, first.revision) }
        assertThrows(IllegalArgumentException::class.java) { repo.install(extensionFixture("1.0"), revised.revision) }
        repo.delete(revised.id, revised.revision)
        assertTrue(open().state.value.isEmpty())
    }

    @Test fun persistenceFailureDoesNotPublishFalseSuccessOrReplacePreviousCode() {
        var fail = false
        var persisted: String? = null
        val repo = BrowserExtensionRepository(File("unused"), { null }, { persisted }, { _, text ->
            if (fail) error("disk full")
            persisted = text
        })
        val first = repo.install(extensionFixture(), null)
        val disk = persisted
        fail = true
        assertThrows(IllegalStateException::class.java) { repo.install(extensionFixture("1.1"), first.revision) }
        assertEquals(first, repo.get(first.id))
        assertEquals(disk, persisted)
    }

    @Test fun persistedRevisionIsVerifiedBeforeStateBecomesAvailable() {
        var persisted: String? = null
        fun open() = BrowserExtensionRepository(File("unused"), { null }, { persisted }, { _, text -> persisted = text })
        val installed = open().install(extensionFixture(), null)
        assertTrue(persisted.orEmpty().contains(installed.revision))
        val snapshot = persisted.orEmpty()
        persisted = snapshot.replace(",\"revision\":\"${installed.revision}\"", "")
        assertEquals(installed.revision, open().get(installed.id).revision)
        persisted = snapshot.replace(installed.revision, "0".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { open() }
    }

    @Test fun invalidBooleanAndRevisionInputsCannotBecomeAccidentalEnableOperations() {
        for (value in listOf("yes", "1", "TRUE", "false ")) {
            assertThrows(IllegalStateException::class.java) { BrowserDevelopmentArguments(mapOf("enabled" to value)).boolean("enabled") }
        }
        assertFalse(BrowserDevelopmentArguments(mapOf("enabled" to "false")).boolean("enabled"))
        assertThrows(IllegalArgumentException::class.java) { BrowserDevelopmentArguments(mapOf("limit" to "-1")).integer("limit", 10, 1..100) }
    }
}
