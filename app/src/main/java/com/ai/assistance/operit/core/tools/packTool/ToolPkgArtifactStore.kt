package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import com.kiyori.platform.storage.KiyoriPaths
import com.kiyori.platform.storage.ToolPkgPrivateDataLayout
import com.kiyori.platform.storage.ToolPkgStorageService
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal data class ToolPkgActiveArtifactRecord(
    val packageId: String,
    val artifactSha256: String,
    val version: String,
    val activatedAt: Long,
)

@Serializable
private data class ToolPkgActiveArtifactDocument(
    val version: Int,
    val packageId: String,
    val artifactSha256: String,
    val artifactVersion: String,
    val activatedAt: Long,
)

internal class ToolPkgArtifactStore(private val context: Context) {

    fun storeArtifact(
        sourceFile: File,
        report: ToolPkgArtifactScanReport,
    ): File {
        report.requireAccepted()
        require(sourceFile.isFile) { "ToolPkg source artifact does not exist" }
        require(report.artifactSha256.matches(SHA256_PATTERN)) {
            "ToolPkg artifact SHA-256 is invalid"
        }
        val target =
            File(
                KiyoriPaths.toolPkgArtifactsDir(context),
                "${report.artifactSha256}.toolpkg",
            )
        if (target.exists()) {
            require(target.isFile) { "ToolPkg artifact target is not a file" }
            require(ToolPkgArtifactScanner.scan(target).artifactSha256 == report.artifactSha256) {
                "Existing ToolPkg artifact content does not match its content address"
            }
            writeAudit(report)
            return target
        }
        val temporary = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            sourceFile.inputStream().use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            val storedReport = ToolPkgArtifactScanner.scan(temporary)
            storedReport.requireAccepted()
            require(storedReport.artifactSha256 == report.artifactSha256) {
                "Stored ToolPkg artifact SHA-256 changed during copy"
            }
            check(temporary.renameTo(target)) {
                "Unable to commit ToolPkg artifact into the content-addressed store"
            }
            writeAudit(storedReport)
            return target
        } finally {
            if (temporary.exists()) {
                temporary.delete()
            }
        }
    }

    fun writeAudit(report: ToolPkgArtifactScanReport) {
        val auditFile =
            File(
                KiyoriPaths.toolPkgAuditDir(context),
                "${report.artifactSha256}.json",
            )
        writeAtomicText(auditFile, report.toJson())
    }

    fun readActive(packageId: String): ToolPkgActiveArtifactRecord? {
        val activeFile = activeFile(packageId)
        if (!activeFile.exists()) {
            return null
        }
        require(activeFile.isFile) { "ToolPkg active record is not a file" }
        return decodeActiveRecord(
            activeFile.readText(StandardCharsets.UTF_8),
            activeFile,
        )
    }

    fun activate(record: ToolPkgActiveArtifactRecord) {
        require(record.packageId.isNotBlank()) { "ToolPkg active package ID is required" }
        require(record.artifactSha256.matches(SHA256_PATTERN)) {
            "ToolPkg active artifact SHA-256 is invalid"
        }
        val artifact = artifactFile(record.artifactSha256)
        require(artifact.isFile) {
            "ToolPkg active artifact does not exist: ${record.artifactSha256}"
        }
        writeAtomicText(
            activeFile(record.packageId),
            artifactJson.encodeToString(
                ToolPkgActiveArtifactDocument(
                    version = ACTIVE_RECORD_VERSION,
                    packageId = record.packageId,
                    artifactSha256 = record.artifactSha256,
                    artifactVersion = record.version,
                    activatedAt = record.activatedAt,
                ),
            ),
        )
    }

    fun restoreActive(
        packageId: String,
        previous: ToolPkgActiveArtifactRecord?,
    ) {
        if (previous == null) {
            deleteRecord(activeFile(packageId))
        } else {
            activate(previous)
        }
    }

    fun deactivate(packageId: String) {
        deleteRecord(activeFile(packageId))
    }

    fun activeArtifactFiles(): List<File> {
        val records =
            KiyoriPaths.toolPkgActiveDir(context)
                .listFiles()
                .orEmpty()
                .filter { file -> file.isFile && file.name.endsWith(".json") }
                .map { file ->
                    decodeActiveRecord(
                        file.readText(StandardCharsets.UTF_8),
                        file,
                    )
                }
        val duplicateIds =
            records
                .groupBy { record -> record.packageId.lowercase() }
                .filterValues { values -> values.size > 1 }
                .keys
        require(duplicateIds.isEmpty()) {
            "Duplicate ToolPkg active records: ${duplicateIds.joinToString()}"
        }
        return records.map { record ->
            artifactFile(record.artifactSha256).also { artifact ->
                require(artifact.isFile) {
                    "ToolPkg active artifact is missing for ${record.packageId}"
                }
            }
        }
    }

    fun isManagedArtifact(file: File): Boolean {
        val root = KiyoriPaths.toolPkgArtifactsDir(context).canonicalFile
        val candidate = file.canonicalFile
        return candidate.parentFile == root &&
            candidate.name.matches(MANAGED_ARTIFACT_FILE_PATTERN)
    }

    fun marketMetadataDir(packageId: String): File {
        return KiyoriPaths.toolPkgMarketDir(
            context,
            ToolPkgStorageService.packageKey(packageId),
        )
    }

    fun cleanupUnreferencedArtifacts() {
        val referenced =
            KiyoriPaths.toolPkgActiveDir(context)
                .listFiles()
                .orEmpty()
                .filter { file -> file.isFile && file.name.endsWith(".json") }
                .map { file ->
                    decodeActiveRecord(
                        file.readText(StandardCharsets.UTF_8),
                        file,
                    ).artifactSha256
                }
                .toSet()
        KiyoriPaths.toolPkgArtifactsDir(context)
            .listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                    file.name.matches(MANAGED_ARTIFACT_FILE_PATTERN) &&
                    file.nameWithoutExtension !in referenced
            }
            .forEach { file ->
                check(file.delete()) {
                    "Unable to delete unreferenced ToolPkg artifact: ${file.absolutePath}"
                }
            }
    }

    private fun artifactFile(sha256: String): File {
        require(sha256.matches(SHA256_PATTERN)) { "ToolPkg artifact SHA-256 is invalid" }
        return File(KiyoriPaths.toolPkgArtifactsDir(context), "$sha256.toolpkg")
    }

    private fun activeFile(packageId: String): File {
        val packageKey = ToolPkgStorageService.packageKey(packageId)
        return File(KiyoriPaths.toolPkgActiveDir(context), "$packageKey.json")
    }

    private fun decodeActiveRecord(
        json: String,
        sourceFile: File,
    ): ToolPkgActiveArtifactRecord {
        val document =
            runCatching {
                artifactJson.decodeFromString<ToolPkgActiveArtifactDocument>(json)
            }
                .getOrElse { error ->
                    throw IllegalStateException(
                        "ToolPkg active record is invalid: ${sourceFile.absolutePath}",
                        error,
                    )
                }
        require(document.version == ACTIVE_RECORD_VERSION) {
            "ToolPkg active record version is unsupported: ${sourceFile.absolutePath}"
        }
        val packageId = document.packageId.trim()
        val artifactSha256 = document.artifactSha256.trim().lowercase()
        val version = document.artifactVersion.trim()
        val activatedAt = document.activatedAt
        require(packageId.isNotBlank()) {
            "ToolPkg active record packageId is missing: ${sourceFile.absolutePath}"
        }
        require(artifactSha256.matches(SHA256_PATTERN)) {
            "ToolPkg active record SHA-256 is invalid: ${sourceFile.absolutePath}"
        }
        require(activatedAt >= 0L) {
            "ToolPkg active record activatedAt is invalid: ${sourceFile.absolutePath}"
        }
        return ToolPkgActiveArtifactRecord(
            packageId = packageId,
            artifactSha256 = artifactSha256,
            version = version,
            activatedAt = activatedAt,
        )
    }

    private fun writeAtomicText(
        file: File,
        text: String,
    ) {
        ToolPkgPrivateDataLayout.writeAtomicText(file, text)
    }

    private fun deleteRecord(file: File) {
        if (file.exists()) {
            require(file.isFile) { "ToolPkg active record is not a file" }
            check(file.delete()) {
                "Unable to delete ToolPkg active record: ${file.absolutePath}"
            }
        }
    }

    companion object {
        private const val ACTIVE_RECORD_VERSION = 1
        private val artifactJson =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = false
            }
        private val SHA256_PATTERN = Regex("""[0-9a-f]{64}""")
        private val MANAGED_ARTIFACT_FILE_PATTERN = Regex("""[0-9a-f]{64}\.toolpkg""")
    }
}
