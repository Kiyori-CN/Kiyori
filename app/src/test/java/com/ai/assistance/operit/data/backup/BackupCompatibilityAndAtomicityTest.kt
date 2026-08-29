package com.ai.assistance.operit.data.backup

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupCompatibilityAndAtomicityTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `snapshot package whitelist accepts Kiyori and explicit Operit variants`() {
        assertTrue(isSupportedSnapshotPackageName("com.kiyori"))
        assertTrue(isSupportedSnapshotPackageName("com.ai.assistance.operit"))
        assertTrue(isSupportedSnapshotPackageName("com.ai.assistance.operit.debug"))
        assertFalse(isSupportedSnapshotPackageName("com.ai.assistance.operitmalicious"))
        assertFalse(isSupportedSnapshotPackageName("com.example.operit"))
    }

    @Test
    fun `atomic replacement overwrites an existing database in one move`() {
        val root = temporaryFolder.newFolder()
        val staged = File(root, "app_database.restore.tmp").apply { writeText("restored") }
        val current = File(root, "app_database").apply { writeText("existing") }

        atomicallyReplaceDatabaseFile(staged, current)

        assertEquals("restored", current.readText())
        assertFalse(staged.exists())
    }

    @Test
    fun `replacement failure does not delete current target`() {
        val root = temporaryFolder.newFolder()
        val staged = File(root, "app_database.restore.tmp").apply { writeText("restored") }
        val current = File(root, "app_database").apply {
            mkdirs()
            File(this, "sentinel").writeText("existing")
        }

        assertThrows(IOException::class.java) {
            atomicallyReplaceDatabaseFile(staged, current)
        }

        assertEquals("existing", File(current, "sentinel").readText())
    }
}
