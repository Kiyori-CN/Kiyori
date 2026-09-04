package com.kiyori.platform.logging

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.SyncFailedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class KiyoriLogMigrationResult(
    val migrated: Boolean,
    val legacyDeleted: Boolean,
    val failureReason: String? = null,
)

/**
 * Owns the one-time legacy-log -> kiyori.log transition. All work is streamed so a large log does
 * not create a second in-memory copy. The legacy file is removed only after the active file has
 * been atomically installed and byte-verified.
 */
internal object KiyoriLogFileMigration {
    const val ACTIVE_FILE_NAME = "kiyori.log"
    const val LEGACY_FILE_NAME = "operit.log"

    private const val TEMP_FILE_PREFIX = ".kiyori-log-migration-"
    private const val TEMP_FILE_SUFFIX = ".tmp"
    private const val BUFFER_SIZE = 64 * 1024
    private val lock = Any()

    fun resolve(logDirectory: File): File {
        require(logDirectory.isDirectory || logDirectory.mkdirs()) {
            "Unable to create log directory: ${logDirectory.absolutePath}"
        }
        val activeFile = File(logDirectory, ACTIVE_FILE_NAME)
        val legacyFile = File(logDirectory, LEGACY_FILE_NAME)
        if (legacyFile.exists()) {
            val result = migrate(logDirectory, activeFile, legacyFile)
            if (result.failureReason != null) {
                // This helper deliberately does not call KiyoriLogger: it may run while the
                // logger is resolving its first file and must never recurse into the writer.
                android.util.Log.w(
                    "KiyoriLogFileMigration",
                    "Legacy log migration retained the legacy file " +
                        "(reasonChars=${result.failureReason.length})",
                )
            }
        }
        return activeFile
    }

    fun migrate(
        logDirectory: File,
        activeFile: File = File(logDirectory, ACTIVE_FILE_NAME),
        legacyFile: File = File(logDirectory, LEGACY_FILE_NAME),
    ): KiyoriLogMigrationResult = synchronized(lock) {
        if (!legacyFile.exists()) {
            return@synchronized KiyoriLogMigrationResult(
                migrated = false,
                legacyDeleted = false,
            )
        }

        return@synchronized try {
            if (!activeFile.exists()) {
                installCopy(logDirectory, activeFile, legacyFile)
            } else if (filesEqual(activeFile, legacyFile) || startsWith(activeFile, legacyFile)) {
                deleteLegacyAfterVerification(activeFile, legacyFile)
            } else {
                installMerged(logDirectory, activeFile, legacyFile)
            }
        } catch (error: Exception) {
            KiyoriLogMigrationResult(
                migrated = false,
                legacyDeleted = false,
                failureReason = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun installCopy(
        logDirectory: File,
        activeFile: File,
        legacyFile: File,
    ): KiyoriLogMigrationResult {
        val temporaryFile = createTemporaryFile(logDirectory)
        return try {
            copyFile(legacyFile, temporaryFile)
            check(filesEqual(temporaryFile, legacyFile)) {
                "Copied legacy log bytes do not match"
            }
            atomicReplace(temporaryFile, activeFile)
            check(filesEqual(activeFile, legacyFile)) {
                "Installed active log bytes do not match legacy log"
            }
            deleteLegacyAfterVerification(activeFile, legacyFile)
        } finally {
            temporaryFile.delete()
        }
    }

    private fun installMerged(
        logDirectory: File,
        activeFile: File,
        legacyFile: File,
    ): KiyoriLogMigrationResult {
        val temporaryFile = createTemporaryFile(logDirectory)
        return try {
            mergeFiles(legacyFile, activeFile, temporaryFile)
            check(startsWith(temporaryFile, legacyFile)) {
                "Merged log does not preserve legacy content"
            }
            atomicReplace(temporaryFile, activeFile)
            check(startsWith(activeFile, legacyFile)) {
                "Installed merged log does not preserve legacy content"
            }
            deleteLegacyAfterVerification(activeFile, legacyFile)
        } finally {
            temporaryFile.delete()
        }
    }

    private fun deleteLegacyAfterVerification(
        activeFile: File,
        legacyFile: File,
    ): KiyoriLogMigrationResult {
        check(activeFile.exists()) { "Active log does not exist after migration" }
        if (!legacyFile.delete() && legacyFile.exists()) {
            return KiyoriLogMigrationResult(
                migrated = true,
                legacyDeleted = false,
                failureReason = "Unable to remove legacy log after verification",
            )
        }
        return KiyoriLogMigrationResult(migrated = true, legacyDeleted = true)
    }

    private fun createTemporaryFile(logDirectory: File): File =
        File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX, logDirectory)

    private fun atomicReplace(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: IOException) {
            throw IOException("Atomic log replacement is unavailable", error)
        }
    }

    private fun copyFile(source: File, target: File) {
        BufferedInputStream(FileInputStream(source), BUFFER_SIZE).use { input ->
            FileOutputStream(target).use { fileOutput ->
                BufferedOutputStream(fileOutput, BUFFER_SIZE).use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                    output.flush()
                    // BufferedOutputStream.close() also closes fileOutput. Sync while the
                    // descriptor is still open so Android durability is not best-effort.
                    syncForDurability(fileOutput)
                }
            }
        }
    }

    private fun mergeFiles(legacyFile: File, activeFile: File, target: File) {
        FileOutputStream(target).use { fileOutput ->
            BufferedOutputStream(fileOutput, BUFFER_SIZE).use { output ->
                copyInto(legacyFile, output)
                if (needsSeparator(legacyFile, activeFile)) {
                    output.write('\n'.code)
                }
                copyInto(activeFile, output)
                output.flush()
                // Keep the underlying descriptor open until the durability barrier completes.
                syncForDurability(fileOutput)
            }
        }
    }

    private fun syncForDurability(fileOutput: FileOutputStream) {
        try {
            fileOutput.fd.sync()
        } catch (error: SyncFailedException) {
            // ART filesystems normally support fd.sync(). A host JVM used for contract tests may
            // report it as unsupported even after flush; keep the Android safety contract strict
            // while allowing the portable byte/atomicity checks to run on that host.
            if (System.getProperty("java.vm.name")?.contains("dalvik", ignoreCase = true) == true) {
                throw error
            }
        }
    }

    private fun copyInto(source: File, output: BufferedOutputStream) {
        BufferedInputStream(FileInputStream(source), BUFFER_SIZE).use { input ->
            input.copyTo(output, BUFFER_SIZE)
        }
    }

    private fun needsSeparator(first: File, second: File): Boolean {
        if (first.length() == 0L || second.length() == 0L) {
            return false
        }
        val lastByte = java.io.RandomAccessFile(first, "r").use { input ->
            input.seek(first.length() - 1)
            input.read()
        }
        return lastByte != '\n'.code && lastByte != '\r'.code
    }

    private fun filesEqual(first: File, second: File): Boolean {
        if (first.length() != second.length()) {
            return false
        }
        BufferedInputStream(FileInputStream(first), BUFFER_SIZE).use { left ->
            BufferedInputStream(FileInputStream(second), BUFFER_SIZE).use { right ->
                val leftBuffer = ByteArray(BUFFER_SIZE)
                val rightBuffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val leftRead = left.read(leftBuffer)
                    val rightRead = right.read(rightBuffer)
                    if (leftRead != rightRead) {
                        return false
                    }
                    if (leftRead < 0) {
                        return true
                    }
                    for (index in 0 until leftRead) {
                        if (leftBuffer[index] != rightBuffer[index]) {
                            return false
                        }
                    }
                }
            }
        }
    }

    private fun startsWith(file: File, prefix: File): Boolean {
        if (file.length() < prefix.length()) {
            return false
        }
        BufferedInputStream(FileInputStream(file), BUFFER_SIZE).use { input ->
            BufferedInputStream(FileInputStream(prefix), BUFFER_SIZE).use { expected ->
                val actualBuffer = ByteArray(BUFFER_SIZE)
                val expectedBuffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val expectedRead = expected.read(expectedBuffer)
                    if (expectedRead < 0) {
                        return true
                    }
                    var actualOffset = 0
                    while (actualOffset < expectedRead) {
                        val actualRead = input.read(actualBuffer, actualOffset, expectedRead - actualOffset)
                        if (actualRead < 0) {
                            return false
                        }
                        actualOffset += actualRead
                    }
                    for (index in 0 until expectedRead) {
                        if (actualBuffer[index] != expectedBuffer[index]) {
                            return false
                        }
                    }
                }
            }
        }
    }

}
