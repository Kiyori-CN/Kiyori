package com.kiyori.platform.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal enum class ToolPkgLegacyConflictPolicy {
    REPLACE,
    MERGE,
}

internal data class ToolPkgLegacyInputDeclaration(
    val relativePath: String,
    val required: Boolean = true,
    val maximumBytes: Int = ToolPkgStorageService.MAX_TEXT_BYTES,
)

internal data class ToolPkgLegacySourceTextFile(
    val relativePath: String,
    val text: String,
    val sha256: String,
    val byteCount: Int,
)

internal data class ToolPkgLegacyMigrationInput(
    val files: Map<String, ToolPkgLegacySourceTextFile>,
)

internal data class ToolPkgLegacyMigrationOutput(
    val files: Map<String, String>,
)

/**
 * 迁移器由宿主代码注册，并且只服务一个明确的 ToolPkg 容器。
 *
 * 迁移器负责 JSON schema、字段类型、时间范围和领域数量校验；宿主继续负责路径、大小、配额、
 * generation 原子提交和重复 source digest 拒绝。
 */
internal interface ToolPkgLegacyMigrator {
    val containerPackageName: String
    val migratorId: String
    val migratorVersion: Int
    val targetSchemaVersion: Int
    val conflictPolicy: ToolPkgLegacyConflictPolicy
    val inputs: List<ToolPkgLegacyInputDeclaration>

    fun migrate(input: ToolPkgLegacyMigrationInput): ToolPkgLegacyMigrationOutput
}

internal class ToolPkgLegacyMigratorRegistry(
    migrators: Iterable<ToolPkgLegacyMigrator>,
) {
    private val entries: Map<ToolPkgLegacyMigratorKey, ToolPkgLegacyMigrator>

    init {
        val indexed = linkedMapOf<ToolPkgLegacyMigratorKey, ToolPkgLegacyMigrator>()
        migrators.forEach { migrator ->
            require(migrator.containerPackageName.isNotBlank()) {
                "ToolPkg legacy migrator container package name is required"
            }
            require(migrator.migratorId.matches(MIGRATOR_ID_PATTERN)) {
                "ToolPkg legacy migrator ID is invalid: ${migrator.migratorId}"
            }
            require(migrator.migratorVersion >= 1) {
                "ToolPkg legacy migrator version must be positive"
            }
            require(migrator.targetSchemaVersion >= 1) {
                "ToolPkg legacy migrator target schema version must be positive"
            }
            require(migrator.inputs.isNotEmpty()) {
                "ToolPkg legacy migrator must declare at least one input"
            }
            validateInputDeclarations(migrator.inputs)
            val key =
                ToolPkgLegacyMigratorKey(
                    containerPackageName = normalizePackageName(migrator.containerPackageName),
                    migratorId = migrator.migratorId,
                    migratorVersion = migrator.migratorVersion,
                )
            require(indexed.putIfAbsent(key, migrator) == null) {
                "Duplicate ToolPkg legacy migrator: $key"
            }
        }
        entries = indexed.toMap()
    }

    fun requireMigrator(contract: ToolPkgLegacyImportContract): ToolPkgLegacyMigrator {
        val key =
            ToolPkgLegacyMigratorKey(
                containerPackageName = normalizePackageName(contract.containerPackageName),
                migratorId = contract.migratorId,
                migratorVersion = contract.migratorVersion,
            )
        val migrator =
            entries[key]
                ?: throw IllegalArgumentException(
                    "No ToolPkg legacy migrator is registered for " +
                        "${contract.containerPackageName}/${contract.migratorId}/" +
                        contract.migratorVersion,
                )
        require(migrator.targetSchemaVersion == contract.targetSchemaVersion) {
            "ToolPkg legacy import target schema does not match the registered migrator"
        }
        require(migrator.conflictPolicy == contract.conflictPolicy) {
            "ToolPkg legacy import conflict policy does not match the registered migrator"
        }
        return migrator
    }

    private data class ToolPkgLegacyMigratorKey(
        val containerPackageName: String,
        val migratorId: String,
        val migratorVersion: Int,
    )

    companion object {
        private val MIGRATOR_ID_PATTERN = Regex("""[A-Za-z0-9._-]{1,96}""")

        private fun normalizePackageName(value: String): String {
            return value.trim().lowercase(Locale.ROOT)
        }

        private fun validateInputDeclarations(inputs: List<ToolPkgLegacyInputDeclaration>) {
            val normalizedPaths = linkedSetOf<String>()
            val lowercasePaths = linkedSetOf<String>()
            inputs.forEach { declaration ->
                val normalized =
                    ToolPkgStorageService.normalizeRelativePath(declaration.relativePath)
                require(declaration.maximumBytes in 1..ToolPkgStorageService.MAX_TEXT_BYTES) {
                    "ToolPkg legacy input size limit is invalid: $normalized"
                }
                require(normalizedPaths.add(normalized)) {
                    "Duplicate ToolPkg legacy input declaration: $normalized"
                }
                require(lowercasePaths.add(normalized.lowercase(Locale.ROOT))) {
                    "Case-conflicting ToolPkg legacy input declaration: $normalized"
                }
            }
        }
    }
}

internal data class ToolPkgLegacyImportContract(
    val containerPackageName: String,
    val migratorId: String,
    val migratorVersion: Int,
    val targetSchemaVersion: Int,
    val conflictPolicy: ToolPkgLegacyConflictPolicy,
)

internal data class ToolPkgLegacyImportRequest(
    val contract: ToolPkgLegacyImportContract,
    val sourceTreeUri: Uri,
)

internal data class ToolPkgLegacyImportResult(
    val packageKey: String,
    val generationId: String,
    val sourceTreeDigest: String,
    val generationTreeDigest: String,
    val importedFileCount: Int,
)

@Serializable
private data class ToolPkgMigrationSourceFileDocument(
    val path: String,
    val sha256: String,
    val bytes: Int,
)

@Serializable
private data class ToolPkgGenerationMetadataDocument(
    val version: Int,
    val packageKey: String,
    val containerPackageName: String,
    val generationId: String,
    val migratorId: String,
    val migratorVersion: Int,
    val targetSchemaVersion: Int,
    val conflictPolicy: String,
    val sourceTreeDigest: String,
    val generationTreeDigest: String,
    val sourceFiles: List<ToolPkgMigrationSourceFileDocument>,
    val outputPaths: List<String>,
)

@Serializable
private data class ToolPkgMigrationAuditDocument(
    val version: Int,
    val status: String,
    val packageKey: String,
    val containerPackageName: String,
    val generationId: String,
    val migratorId: String,
    val migratorVersion: Int,
    val targetSchemaVersion: Int,
    val conflictPolicy: String,
    val sourceIdentitySha256: String,
    val sourceTreeDigest: String,
    val generationTreeDigest: String,
    val sourceFiles: List<ToolPkgMigrationSourceFileDocument>,
    val outputPaths: List<String>,
    val completedAt: Long,
)

internal class ToolPkgLegacyImportCoordinator(
    context: Context,
    private val registry: ToolPkgLegacyMigratorRegistry,
) {
    private val appContext = context.applicationContext

    fun importFromTree(request: ToolPkgLegacyImportRequest): ToolPkgLegacyImportResult {
        val packageKey = ToolPkgStorageService.packageKey(request.contract.containerPackageName)
        val sourceReader =
            SafToolPkgLegacySourceReader(
                context = appContext,
                treeUri = request.sourceTreeUri,
            )
        val engine =
            ToolPkgLegacyMigrationEngine(
                packageKey = packageKey,
                defaultDataDir = KiyoriPaths.toolPkgPrivateDataDir(appContext, packageKey),
                generationsDir = KiyoriPaths.toolPkgGenerationsDir(appContext, packageKey),
                activeGenerationFile = KiyoriPaths.toolPkgActiveGenerationFile(appContext, packageKey),
                migrationAuditDir = KiyoriPaths.toolPkgMigrationAuditDir(appContext, packageKey),
                registry = registry,
            )
        return ToolPkgStorageLocks.withPackageLock(packageKey) {
            engine.import(
                contract = request.contract,
                sourceIdentity = request.sourceTreeUri.toString(),
                sourceReader = sourceReader,
            )
        }
    }
}

internal fun interface ToolPkgLegacySourceReader {
    fun read(
        relativePath: String,
        maximumBytes: Int,
    ): ByteArray?
}

internal class ToolPkgLegacyMigrationEngine(
    private val packageKey: String,
    private val defaultDataDir: File,
    private val generationsDir: File,
    private val activeGenerationFile: File,
    private val migrationAuditDir: File,
    private val registry: ToolPkgLegacyMigratorRegistry,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val generationIdFactory: () -> String = {
        "generation-${UUID.randomUUID()}"
    },
) {
    fun import(
        contract: ToolPkgLegacyImportContract,
        sourceIdentity: String,
        sourceReader: ToolPkgLegacySourceReader,
    ): ToolPkgLegacyImportResult {
        require(packageKey == ToolPkgStorageService.packageKey(contract.containerPackageName)) {
            "ToolPkg legacy import package key does not match its container"
        }
        val migrator = registry.requireMigrator(contract)
        val sourceFiles = readSourceFiles(migrator.inputs, sourceReader)
        val sourceTreeDigest = calculateSourceTreeDigest(sourceFiles.values)
        val previousActive =
            ToolPkgPrivateDataLayout.readActiveGeneration(
                file = activeGenerationFile,
                generationsDir = generationsDir,
            )
        require(previousActive?.sourceTreeDigest != sourceTreeDigest) {
            "ToolPkg legacy source tree was already imported by the active generation"
        }
        require(!hasCompletedAuditForSourceDigest(sourceTreeDigest)) {
            "ToolPkg legacy source tree was already imported"
        }

        val migrationOutput =
            normalizeMigrationOutput(
                migrator.migrate(
                    ToolPkgLegacyMigrationInput(files = sourceFiles),
                ),
            )
        val generationId = generationIdFactory()
        require(generationId.matches(GENERATION_ID_PATTERN)) {
            "ToolPkg generation ID is invalid"
        }
        require(generationsDir.isDirectory || generationsDir.mkdirs()) {
            "Unable to create ToolPkg generations directory"
        }
        val stagingDir = File(generationsDir, ".staging-$generationId")
        val finalDir = File(generationsDir, generationId)
        require(!stagingDir.exists() && !finalDir.exists()) {
            "ToolPkg generation already exists: $generationId"
        }
        require(stagingDir.mkdirs()) {
            "Unable to create ToolPkg staging generation"
        }

        var finalGenerationCommitted = false
        var activeGenerationCommitted = false
        try {
            val stagingDataDir = File(stagingDir, "data")
            require(stagingDataDir.mkdirs()) {
                "Unable to create ToolPkg staging data directory"
            }
            if (contract.conflictPolicy == ToolPkgLegacyConflictPolicy.MERGE) {
                val currentDataDir =
                    if (previousActive == null) {
                        defaultDataDir
                    } else {
                        File(File(generationsDir, previousActive.generationId), "data")
                    }
                copyPrivateDataTree(
                    sourceRoot = currentDataDir,
                    destinationRoot = stagingDataDir,
                )
            }
            migrationOutput.forEach { (relativePath, text) ->
                writeGenerationText(
                    dataRoot = stagingDataDir,
                    relativePath = relativePath,
                    text = text,
                )
            }
            val generationTree = inspectGenerationTree(stagingDataDir)
            writeGenerationMetadata(
                file = File(stagingDir, "generation.json"),
                contract = contract,
                generationId = generationId,
                sourceTreeDigest = sourceTreeDigest,
                generationTreeDigest = generationTree.digest,
                sourceFiles = sourceFiles.values,
                outputPaths = migrationOutput.keys,
            )
            Files.move(
                stagingDir.toPath(),
                finalDir.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
            finalGenerationCommitted = true

            val activeRecord =
                ToolPkgActiveGenerationRecord(
                    generationId = generationId,
                    targetSchemaVersion = contract.targetSchemaVersion,
                    sourceTreeDigest = sourceTreeDigest,
                    generationTreeDigest = generationTree.digest,
                    migratorId = contract.migratorId,
                    migratorVersion = contract.migratorVersion,
                    activatedAt = nowMillis(),
                )
            ToolPkgPrivateDataLayout.writeActiveGeneration(activeGenerationFile, activeRecord)
            activeGenerationCommitted = true
            try {
                writeCompletedAudit(
                    contract = contract,
                    generationId = generationId,
                    sourceIdentity = sourceIdentity,
                    sourceTreeDigest = sourceTreeDigest,
                    generationTreeDigest = generationTree.digest,
                    sourceFiles = sourceFiles.values,
                    outputPaths = migrationOutput.keys,
                    completedAt = activeRecord.activatedAt,
                )
            } catch (auditError: Exception) {
                restoreActiveGeneration(previousActive)
                activeGenerationCommitted = false
                throw auditError
            }
            return ToolPkgLegacyImportResult(
                packageKey = packageKey,
                generationId = generationId,
                sourceTreeDigest = sourceTreeDigest,
                generationTreeDigest = generationTree.digest,
                importedFileCount = migrationOutput.size,
            )
        } finally {
            if (!activeGenerationCommitted && finalGenerationCommitted && finalDir.exists()) {
                check(finalDir.deleteRecursively()) {
                    "Unable to remove uncommitted ToolPkg generation: $generationId"
                }
            }
            if (stagingDir.exists()) {
                check(stagingDir.deleteRecursively()) {
                    "Unable to remove ToolPkg staging generation: $generationId"
                }
            }
        }
    }

    private fun readSourceFiles(
        declarations: List<ToolPkgLegacyInputDeclaration>,
        sourceReader: ToolPkgLegacySourceReader,
    ): LinkedHashMap<String, ToolPkgLegacySourceTextFile> {
        val result = linkedMapOf<String, ToolPkgLegacySourceTextFile>()
        var totalBytes = 0L
        declarations.forEach { declaration ->
            val normalized =
                ToolPkgStorageService.normalizeRelativePath(declaration.relativePath)
            val bytes = sourceReader.read(normalized, declaration.maximumBytes)
            if (bytes == null) {
                require(!declaration.required) {
                    "Required ToolPkg legacy source file is missing: $normalized"
                }
                return@forEach
            }
            require(bytes.size <= declaration.maximumBytes) {
                "ToolPkg legacy source file exceeds its declared limit: $normalized"
            }
            totalBytes = safeAdd(totalBytes, bytes.size.toLong())
            require(totalBytes <= ToolPkgStorageNamespace.PRIVATE_DATA.quotaBytes) {
                "ToolPkg legacy source files exceed the private-data quota"
            }
            result[normalized] =
                ToolPkgLegacySourceTextFile(
                    relativePath = normalized,
                    text = decodeStrictUtf8(bytes, normalized),
                    sha256 = sha256(bytes),
                    byteCount = bytes.size,
                )
        }
        require(result.isNotEmpty()) {
            "ToolPkg legacy source contains none of the declared files"
        }
        return result
    }

    private fun normalizeMigrationOutput(
        output: ToolPkgLegacyMigrationOutput,
    ): LinkedHashMap<String, String> {
        require(output.files.isNotEmpty()) {
            "ToolPkg legacy migrator produced no files"
        }
        require(output.files.size <= MAX_GENERATION_FILE_COUNT) {
            "ToolPkg legacy migrator produced too many files"
        }
        val normalized = linkedMapOf<String, String>()
        val lowercasePaths = linkedSetOf<String>()
        var totalBytes = 0L
        output.files.forEach { (rawPath, text) ->
            val relativePath = ToolPkgStorageService.normalizeRelativePath(rawPath)
            require(lowercasePaths.add(relativePath.lowercase(Locale.ROOT))) {
                "ToolPkg legacy migrator produced case-conflicting paths: $relativePath"
            }
            val byteCount = text.toByteArray(StandardCharsets.UTF_8).size
            require(byteCount <= ToolPkgStorageService.MAX_TEXT_BYTES) {
                "ToolPkg legacy output exceeds the per-file limit: $relativePath"
            }
            totalBytes = safeAdd(totalBytes, byteCount.toLong())
            require(totalBytes <= ToolPkgStorageNamespace.PRIVATE_DATA.quotaBytes) {
                "ToolPkg legacy output exceeds the private-data quota"
            }
            normalized[relativePath] = text
        }
        return normalized
    }

    private fun copyPrivateDataTree(
        sourceRoot: File,
        destinationRoot: File,
    ) {
        if (!sourceRoot.exists()) {
            return
        }
        require(sourceRoot.isDirectory) {
            "ToolPkg current private-data root is not a directory"
        }
        val canonicalSourceRoot = sourceRoot.canonicalFile
        sourceRoot.walkTopDown()
            .filter { path -> path != sourceRoot }
            .forEach { source ->
                require(!Files.isSymbolicLink(source.toPath())) {
                    "ToolPkg private data cannot contain symbolic links"
                }
                require(source.canonicalFile.toPath().startsWith(canonicalSourceRoot.toPath())) {
                    "ToolPkg private data escapes its package root"
                }
                val relativePath = source.relativeTo(sourceRoot).invariantSeparatorsPath
                ToolPkgStorageService.normalizeRelativePath(relativePath)
                val destination = File(destinationRoot, relativePath)
                require(destination.canonicalFile.toPath().startsWith(destinationRoot.canonicalFile.toPath())) {
                    "ToolPkg private-data copy escapes the staging generation"
                }
                if (source.isDirectory) {
                    require(destination.isDirectory || destination.mkdirs()) {
                        "Unable to create ToolPkg generation directory"
                    }
                } else if (source.isFile) {
                    require(source.length() <= ToolPkgStorageService.MAX_TEXT_BYTES) {
                        "ToolPkg current private-data file exceeds the per-file limit: $relativePath"
                    }
                    copyFileWithSync(source, destination)
                }
            }
        inspectGenerationTree(destinationRoot)
    }

    private fun writeGenerationText(
        dataRoot: File,
        relativePath: String,
        text: String,
    ) {
        val normalized = ToolPkgStorageService.normalizeRelativePath(relativePath)
        val destination = File(dataRoot, normalized).canonicalFile
        val rootPrefix = dataRoot.canonicalFile.path.trimEnd(File.separatorChar) + File.separator
        require(destination.path.startsWith(rootPrefix)) {
            "ToolPkg migration output escapes the staging generation"
        }
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= ToolPkgStorageService.MAX_TEXT_BYTES) {
            "ToolPkg migration output exceeds the per-file limit: $normalized"
        }
        val parent = requireNotNull(destination.parentFile) {
            "ToolPkg migration output parent is unavailable"
        }
        require(parent.isDirectory || parent.mkdirs()) {
            "Unable to create ToolPkg migration output directory"
        }
        FileOutputStream(destination).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
    }

    private fun inspectGenerationTree(dataRoot: File): GenerationTreeInspection {
        require(dataRoot.isDirectory) { "ToolPkg generation data directory is missing" }
        val files =
            dataRoot.walkTopDown()
                .filter(File::isFile)
                .toList()
        require(files.size <= MAX_GENERATION_FILE_COUNT) {
            "ToolPkg generation contains too many files"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L
        files
            .sortedBy { file -> file.relativeTo(dataRoot).invariantSeparatorsPath }
            .forEach { file ->
                require(!Files.isSymbolicLink(file.toPath())) {
                    "ToolPkg generation cannot contain symbolic links"
                }
                val relativePath =
                    ToolPkgStorageService.normalizeRelativePath(
                        file.relativeTo(dataRoot).invariantSeparatorsPath,
                    )
                val length = file.length()
                require(length <= ToolPkgStorageService.MAX_TEXT_BYTES) {
                    "ToolPkg generation file exceeds the per-file limit: $relativePath"
                }
                totalBytes = safeAdd(totalBytes, length)
                require(totalBytes <= ToolPkgStorageNamespace.PRIVATE_DATA.quotaBytes) {
                    "ToolPkg generation exceeds the private-data quota"
                }
                digest.update(relativePath.toByteArray(StandardCharsets.UTF_8))
                digest.update(0.toByte())
                file.inputStream().use { input ->
                    val fileDigest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) {
                            break
                        }
                        fileDigest.update(buffer, 0, read)
                    }
                    digest.update(fileDigest.digest())
                }
            }
        return GenerationTreeInspection(
            digest = digest.digest().toHex(),
            fileCount = files.size,
            totalBytes = totalBytes,
        )
    }

    private fun writeGenerationMetadata(
        file: File,
        contract: ToolPkgLegacyImportContract,
        generationId: String,
        sourceTreeDigest: String,
        generationTreeDigest: String,
        sourceFiles: Collection<ToolPkgLegacySourceTextFile>,
        outputPaths: Collection<String>,
    ) {
        writeTextWithSync(
            file = file,
            text =
                migrationJson.encodeToString(
                    ToolPkgGenerationMetadataDocument(
                        version = MIGRATION_RECORD_VERSION,
                        packageKey = packageKey,
                        containerPackageName = contract.containerPackageName,
                        generationId = generationId,
                        migratorId = contract.migratorId,
                        migratorVersion = contract.migratorVersion,
                        targetSchemaVersion = contract.targetSchemaVersion,
                        conflictPolicy = contract.conflictPolicy.name.lowercase(Locale.ROOT),
                        sourceTreeDigest = sourceTreeDigest,
                        generationTreeDigest = generationTreeDigest,
                        sourceFiles = sourceFileDocuments(sourceFiles),
                        outputPaths = outputPaths.sorted(),
                    ),
                ),
        )
    }

    private fun writeCompletedAudit(
        contract: ToolPkgLegacyImportContract,
        generationId: String,
        sourceIdentity: String,
        sourceTreeDigest: String,
        generationTreeDigest: String,
        sourceFiles: Collection<ToolPkgLegacySourceTextFile>,
        outputPaths: Collection<String>,
        completedAt: Long,
    ) {
        require(migrationAuditDir.isDirectory || migrationAuditDir.mkdirs()) {
            "Unable to create ToolPkg migration audit directory"
        }
        val auditFile = File(migrationAuditDir, "$generationId.json")
        require(!auditFile.exists()) { "ToolPkg migration audit already exists: $generationId" }
        ToolPkgPrivateDataLayout.writeAtomicText(
            file = auditFile,
            text =
                migrationJson.encodeToString(
                    ToolPkgMigrationAuditDocument(
                        version = MIGRATION_RECORD_VERSION,
                        status = "completed",
                        packageKey = packageKey,
                        containerPackageName = contract.containerPackageName,
                        generationId = generationId,
                        migratorId = contract.migratorId,
                        migratorVersion = contract.migratorVersion,
                        targetSchemaVersion = contract.targetSchemaVersion,
                        conflictPolicy = contract.conflictPolicy.name.lowercase(Locale.ROOT),
                        sourceIdentitySha256 =
                            sha256(sourceIdentity.toByteArray(StandardCharsets.UTF_8)),
                        sourceTreeDigest = sourceTreeDigest,
                        generationTreeDigest = generationTreeDigest,
                        sourceFiles = sourceFileDocuments(sourceFiles),
                        outputPaths = outputPaths.sorted(),
                        completedAt = completedAt,
                    ),
                ),
        )
    }

    private fun hasCompletedAuditForSourceDigest(sourceTreeDigest: String): Boolean {
        if (!migrationAuditDir.exists()) {
            return false
        }
        require(migrationAuditDir.isDirectory) {
            "ToolPkg migration audit root is not a directory"
        }
        return migrationAuditDir.listFiles()
            .orEmpty()
            .filter { file -> file.isFile && file.name.endsWith(".json") }
            .any { file ->
                val audit =
                    runCatching {
                        migrationJson.decodeFromString<ToolPkgMigrationAuditDocument>(
                            file.readText(StandardCharsets.UTF_8),
                        )
                    }
                        .getOrElse { error ->
                            throw IllegalStateException(
                                "ToolPkg migration audit is invalid: ${file.absolutePath}",
                                error,
                            )
                        }
                require(audit.version == MIGRATION_RECORD_VERSION) {
                    "ToolPkg migration audit version is unsupported: ${file.absolutePath}"
                }
                require(audit.status == "completed") {
                    "ToolPkg migration audit status is invalid: ${file.absolutePath}"
                }
                val digest = audit.sourceTreeDigest.trim().lowercase(Locale.ROOT)
                require(digest.matches(SHA256_PATTERN)) {
                    "ToolPkg migration audit source digest is invalid: ${file.absolutePath}"
                }
                digest == sourceTreeDigest
            }
    }

    private fun restoreActiveGeneration(previous: ToolPkgActiveGenerationRecord?) {
        if (previous == null) {
            ToolPkgPrivateDataLayout.clearActiveGeneration(activeGenerationFile)
        } else {
            ToolPkgPrivateDataLayout.writeActiveGeneration(activeGenerationFile, previous)
        }
    }

    private fun calculateSourceTreeDigest(
        sourceFiles: Collection<ToolPkgLegacySourceTextFile>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        sourceFiles
            .sortedBy(ToolPkgLegacySourceTextFile::relativePath)
            .forEach { source ->
                digest.update(source.relativePath.toByteArray(StandardCharsets.UTF_8))
                digest.update(0.toByte())
                digest.update(source.sha256.hexToBytes())
            }
        return digest.digest().toHex()
    }

    private fun sourceFileDocuments(
        sourceFiles: Collection<ToolPkgLegacySourceTextFile>,
    ): List<ToolPkgMigrationSourceFileDocument> {
        return sourceFiles
            .sortedBy(ToolPkgLegacySourceTextFile::relativePath)
            .map { source ->
                ToolPkgMigrationSourceFileDocument(
                    path = source.relativePath,
                    sha256 = source.sha256,
                    bytes = source.byteCount,
                )
            }
    }

    private fun copyFileWithSync(
        source: File,
        destination: File,
    ) {
        val parent = requireNotNull(destination.parentFile) {
            "ToolPkg generation destination parent is unavailable"
        }
        require(parent.isDirectory || parent.mkdirs()) {
            "Unable to create ToolPkg generation destination directory"
        }
        source.inputStream().use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
        }
    }

    private fun writeTextWithSync(
        file: File,
        text: String,
    ) {
        val parent = requireNotNull(file.parentFile) {
            "ToolPkg migration metadata parent is unavailable"
        }
        require(parent.isDirectory || parent.mkdirs()) {
            "Unable to create ToolPkg migration metadata directory"
        }
        FileOutputStream(file).use { output ->
            output.write(text.toByteArray(StandardCharsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
    }

    private data class GenerationTreeInspection(
        val digest: String,
        val fileCount: Int,
        val totalBytes: Long,
    )

    companion object {
        private const val MIGRATION_RECORD_VERSION = 1
        private const val MAX_GENERATION_FILE_COUNT = 4096
        private val migrationJson =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = false
            }
        private val SHA256_PATTERN = Regex("""[0-9a-f]{64}""")
        private val GENERATION_ID_PATTERN = Regex("""generation-[0-9a-f-]{36}""")

        private fun decodeStrictUtf8(
            bytes: ByteArray,
            relativePath: String,
        ): String {
            return runCatching {
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            }.getOrElse { error ->
                throw IllegalArgumentException(
                    "ToolPkg legacy source is not valid UTF-8: $relativePath",
                    error,
                )
            }
        }

        private fun safeAdd(
            left: Long,
            right: Long,
        ): Long {
            return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
        }

        private fun sha256(bytes: ByteArray): String {
            return MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        }

        private fun ByteArray.toHex(): String {
            return joinToString("") { byte -> "%02x".format(byte) }
        }

        private fun String.hexToBytes(): ByteArray {
            require(length % 2 == 0) { "Hex text length must be even" }
            return ByteArray(length / 2) { index ->
                substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }
    }
}

private class SafToolPkgLegacySourceReader(
    context: Context,
    private val treeUri: Uri,
) : ToolPkgLegacySourceReader {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val authority =
        requireNotNull(treeUri.authority) {
            "ToolPkg legacy source tree URI has no authority"
        }
    private val treeDocumentId =
        runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
            .getOrElse { error ->
                throw IllegalArgumentException("ToolPkg legacy source must be a SAF tree URI", error)
            }
    private val normalizedTreeUri =
        DocumentsContract.buildTreeDocumentUri(authority, treeDocumentId)

    override fun read(
        relativePath: String,
        maximumBytes: Int,
    ): ByteArray? {
        val normalized = ToolPkgStorageService.normalizeRelativePath(relativePath)
        val segments = normalized.split('/')
        var currentDocumentId = treeDocumentId
        segments.forEachIndexed { index, segment ->
            val child =
                findUniqueChild(
                    parentDocumentId = currentDocumentId,
                    displayName = segment,
                ) ?: return null
            val finalSegment = index == segments.lastIndex
            if (!finalSegment) {
                require(child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    "ToolPkg legacy source path crosses a non-directory: $normalized"
                }
                currentDocumentId = child.documentId
                return@forEachIndexed
            }
            require(child.mimeType != DocumentsContract.Document.MIME_TYPE_DIR) {
                "ToolPkg legacy source path points to a directory: $normalized"
            }
            if (child.size >= 0L) {
                require(child.size <= maximumBytes) {
                    "ToolPkg legacy source exceeds its declared limit: $normalized"
                }
            }
            val documentUri =
                DocumentsContract.buildDocumentUriUsingTree(
                    normalizedTreeUri,
                    child.documentId,
                )
            val input =
                requireNotNull(resolver.openInputStream(documentUri)) {
                    "Unable to open ToolPkg legacy source: $normalized"
                }
            input.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) {
                        break
                    }
                    total += read
                    require(total <= maximumBytes) {
                        "ToolPkg legacy source exceeds its declared limit: $normalized"
                    }
                    output.write(buffer, 0, read)
                }
                return output.toByteArray()
            }
        }
        return null
    }

    private fun findUniqueChild(
        parentDocumentId: String,
        displayName: String,
    ): SafDocumentChild? {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                normalizedTreeUri,
                parentDocumentId,
            )
        val matches = mutableListOf<SafDocumentChild>()
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn =
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn =
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn =
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeColumn =
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == displayName) {
                    matches +=
                        SafDocumentChild(
                            documentId = cursor.getString(idColumn),
                            mimeType = cursor.getString(mimeColumn),
                            size = if (cursor.isNull(sizeColumn)) -1L else cursor.getLong(sizeColumn),
                        )
                }
            }
        } ?: throw IllegalStateException(
            "Unable to query the user-selected ToolPkg legacy source tree",
        )
        require(matches.size <= 1) {
            "ToolPkg legacy source contains duplicate sibling names: $displayName"
        }
        return matches.singleOrNull()
    }

    private data class SafDocumentChild(
        val documentId: String,
        val mimeType: String,
        val size: Long,
    )
}
