package com.kiyori.platform.storage

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal data class ToolPkgActiveGenerationRecord(
    val generationId: String,
    val targetSchemaVersion: Int,
    val sourceTreeDigest: String,
    val generationTreeDigest: String,
    val migratorId: String,
    val migratorVersion: Int,
    val activatedAt: Long,
)

@Serializable
private data class ToolPkgActiveGenerationDocument(
    val version: Int,
    val generationId: String,
    val targetSchemaVersion: Int,
    val sourceTreeDigest: String,
    val generationTreeDigest: String,
    val migratorId: String,
    val migratorVersion: Int,
    val activatedAt: Long,
)

internal object ToolPkgStorageLocks {
    private val packageLocks = ConcurrentHashMap<String, Any>()

    inline fun <T> withPackageLock(
        packageKey: String,
        block: () -> T,
    ): T {
        val lock = packageLocks.computeIfAbsent(packageKey) { Any() }
        return synchronized(lock) { block() }
    }
}

internal object ToolPkgPrivateDataLayout {
    fun resolvePrivateDataDir(
        context: Context,
        packageKey: String,
    ): File {
        val active = readActiveGeneration(context, packageKey)
        if (active == null) {
            return KiyoriPaths.toolPkgPrivateDataDir(context, packageKey)
        }
        val dataDir =
            KiyoriPaths.toolPkgGenerationDataDir(
                context = context,
                packageKey = packageKey,
                generationId = active.generationId,
            )
        require(dataDir.isDirectory) {
            "ToolPkg active generation data directory is missing: ${active.generationId}"
        }
        return dataDir
    }

    fun readActiveGeneration(
        context: Context,
        packageKey: String,
    ): ToolPkgActiveGenerationRecord? {
        return readActiveGeneration(
            file = KiyoriPaths.toolPkgActiveGenerationFile(context, packageKey),
            generationsDir = KiyoriPaths.toolPkgGenerationsDir(context, packageKey),
        )
    }

    fun readActiveGeneration(
        file: File,
        generationsDir: File,
    ): ToolPkgActiveGenerationRecord? {
        if (!file.exists()) {
            return null
        }
        require(file.isFile) { "ToolPkg active generation record is not a file" }
        val record = decodeActiveGeneration(file.readText(StandardCharsets.UTF_8), file)
        val generationDir = File(generationsDir, record.generationId)
        require(generationDir.isDirectory) {
            "ToolPkg active generation directory is missing: ${record.generationId}"
        }
        return record
    }

    fun writeActiveGeneration(
        file: File,
        record: ToolPkgActiveGenerationRecord,
    ) {
        validateRecord(record, file)
        writeAtomicText(
            file = file,
            text = generationJson.encodeToString(record.toDocument()),
        )
    }

    fun clearActiveGeneration(file: File) {
        if (file.exists()) {
            require(file.isFile) { "ToolPkg active generation record is not a file" }
            check(file.delete()) { "Unable to clear ToolPkg active generation record" }
        }
    }

    fun writeAtomicText(
        file: File,
        text: String,
    ) {
        val parent = requireNotNull(file.parentFile) { "Atomic file parent is unavailable" }
        require(parent.isDirectory || parent.mkdirs()) {
            "Unable to create atomic file directory: ${parent.absolutePath}"
        }
        val temporary = File(parent, ".${file.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(text.toByteArray(StandardCharsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            if (temporary.exists()) {
                temporary.delete()
            }
        }
    }

    fun decodeActiveGeneration(
        text: String,
        sourceFile: File,
    ): ToolPkgActiveGenerationRecord {
        val document =
            runCatching {
                generationJson.decodeFromString<ToolPkgActiveGenerationDocument>(text)
            }
                .getOrElse { error ->
                    throw IllegalStateException(
                        "ToolPkg active generation record is invalid: ${sourceFile.absolutePath}",
                        error,
                    )
                }
        require(document.version == ACTIVE_RECORD_VERSION) {
            "ToolPkg active generation record version is unsupported: ${sourceFile.absolutePath}"
        }
        val record =
            ToolPkgActiveGenerationRecord(
                generationId = document.generationId.trim(),
                targetSchemaVersion = document.targetSchemaVersion,
                sourceTreeDigest = document.sourceTreeDigest.trim().lowercase(),
                generationTreeDigest = document.generationTreeDigest.trim().lowercase(),
                migratorId = document.migratorId.trim(),
                migratorVersion = document.migratorVersion,
                activatedAt = document.activatedAt,
            )
        validateRecord(record, sourceFile)
        return record
    }

    private fun validateRecord(
        record: ToolPkgActiveGenerationRecord,
        sourceFile: File,
    ) {
        require(record.generationId.matches(GENERATION_ID_PATTERN)) {
            "ToolPkg active generation ID is invalid: ${sourceFile.absolutePath}"
        }
        require(record.targetSchemaVersion >= 1) {
            "ToolPkg active generation schema version is invalid: ${sourceFile.absolutePath}"
        }
        require(record.sourceTreeDigest.matches(SHA256_PATTERN)) {
            "ToolPkg active generation source digest is invalid: ${sourceFile.absolutePath}"
        }
        require(record.generationTreeDigest.matches(SHA256_PATTERN)) {
            "ToolPkg active generation tree digest is invalid: ${sourceFile.absolutePath}"
        }
        require(record.migratorId.matches(MIGRATOR_ID_PATTERN)) {
            "ToolPkg active generation migrator ID is invalid: ${sourceFile.absolutePath}"
        }
        require(record.migratorVersion >= 1) {
            "ToolPkg active generation migrator version is invalid: ${sourceFile.absolutePath}"
        }
        require(record.activatedAt >= 0L) {
            "ToolPkg active generation activation time is invalid: ${sourceFile.absolutePath}"
        }
    }

    private const val ACTIVE_RECORD_VERSION = 1
    private val generationJson =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        }
    private val SHA256_PATTERN = Regex("""[0-9a-f]{64}""")
    private val GENERATION_ID_PATTERN = Regex("""generation-[0-9a-f-]{36}""")
    private val MIGRATOR_ID_PATTERN = Regex("""[A-Za-z0-9._-]{1,96}""")

    private fun ToolPkgActiveGenerationRecord.toDocument(): ToolPkgActiveGenerationDocument {
        return ToolPkgActiveGenerationDocument(
            version = ACTIVE_RECORD_VERSION,
            generationId = generationId,
            targetSchemaVersion = targetSchemaVersion,
            sourceTreeDigest = sourceTreeDigest,
            generationTreeDigest = generationTreeDigest,
            migratorId = migratorId,
            migratorVersion = migratorVersion,
            activatedAt = activatedAt,
        )
    }
}
