package com.kiyori.platform.logging

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriLogFileMigrationTest {

    @Test
    fun copiesLegacyLogAndRemovesItOnlyAfterVerification() {
        val directory = Files.createTempDirectory("kiyori-log-migration").toFile()
        try {
            val legacy = directory.resolve(KiyoriLogFileMigration.LEGACY_FILE_NAME)
            val bytes = "legacy line 1\nlegacy line 2\n".toByteArray()
            legacy.writeBytes(bytes)

            val result = KiyoriLogFileMigration.migrate(directory)

            assertTrue("migration failed: ${result.failureReason}", result.migrated)
            assertTrue(result.legacyDeleted)
            assertArrayEquals(bytes, directory.resolve(KiyoriLogFileMigration.ACTIVE_FILE_NAME).readBytes())
            assertFalse(legacy.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun repeatedMigrationIsIdempotent() {
        val directory = Files.createTempDirectory("kiyori-log-migration").toFile()
        try {
            val active = directory.resolve(KiyoriLogFileMigration.ACTIVE_FILE_NAME)
            val legacy = directory.resolve(KiyoriLogFileMigration.LEGACY_FILE_NAME)
            active.writeText("same\n")
            legacy.writeText("same\n")

            val first = KiyoriLogFileMigration.migrate(directory)
            val second = KiyoriLogFileMigration.migrate(directory)

            assertTrue(first.legacyDeleted)
            assertFalse(legacy.exists())
            assertFalse(second.migrated)
            assertTrue(active.exists())
            assertEquals("same\n", active.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun mergePreservesLegacyPrefixAndCurrentContent() {
        val directory = Files.createTempDirectory("kiyori-log-migration").toFile()
        try {
            val active = directory.resolve(KiyoriLogFileMigration.ACTIVE_FILE_NAME)
            val legacy = directory.resolve(KiyoriLogFileMigration.LEGACY_FILE_NAME)
            active.writeText("new\n")
            legacy.writeText("old")

            val result = KiyoriLogFileMigration.migrate(directory)

            assertTrue("migration failed: ${result.failureReason}", result.migrated)
            assertTrue(result.legacyDeleted)
            assertEquals("old\nnew\n", active.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun migrationFailureKeepsLegacyFileIntact() {
        val directory = Files.createTempDirectory("kiyori-log-migration").toFile()
        try {
            val legacy = directory.resolve(KiyoriLogFileMigration.LEGACY_FILE_NAME)
            val invalidActiveTarget = directory.resolve("active-directory").apply { mkdir() }
            legacy.writeText("must survive\n")

            val result =
                KiyoriLogFileMigration.migrate(
                    logDirectory = directory,
                    activeFile = invalidActiveTarget,
                    legacyFile = legacy,
                )

            assertFalse(result.migrated)
            assertFalse(result.legacyDeleted)
            assertTrue(result.failureReason?.isNotBlank() == true)
            assertTrue(legacy.exists())
            assertEquals("must survive\n", legacy.readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}
