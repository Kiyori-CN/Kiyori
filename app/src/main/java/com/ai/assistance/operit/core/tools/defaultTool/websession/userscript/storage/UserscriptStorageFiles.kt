package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.storage

import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class UserscriptStorageLayout(
    val rootDir: File,
) {
    val stateDir = File(rootDir, "state")
    val valuesDir = File(stateDir, "values")
    val revisionRootDir = File(rootDir, "revisions")
    val draftDir = File(rootDir, "drafts")
    val transactionRootDir = File(rootDir, "transactions")
    val journalDir = File(stateDir, "transaction-journal")

    val registryFile = File(stateDir, "registry.json")
    val logFile = File(stateDir, "logs.json")

    fun ensureDirectories() {
        listOf(
            rootDir,
            stateDir,
            valuesDir,
            revisionRootDir,
            draftDir,
            transactionRootDir,
            journalDir,
        ).forEach { directory ->
            require(directory.isDirectory || directory.mkdirs()) {
                "Unable to create userscript storage directory: ${directory.absolutePath}"
            }
        }
    }

    fun valuesFile(scriptId: Long): File =
        checked(File(valuesDir, "$scriptId.json"))

    fun draftFile(draftId: String): File {
        require(DRAFT_ID_REGEX.matches(draftId)) {
            "Invalid userscript draft ID: $draftId"
        }
        return checked(File(draftDir, "$draftId.json"))
    }

    fun journalFile(transactionId: String): File =
        checked(File(journalDir, "$transactionId.json"))

    fun transactionDir(transactionId: String): File =
        checked(File(transactionRootDir, transactionId))

    fun stagedRevisionDir(transactionId: String): File =
        checked(File(transactionDir(transactionId), "revision"))

    fun revisionScriptDir(scriptId: Long): File =
        checked(File(revisionRootDir, scriptId.toString()))

    fun revisionDir(
        scriptId: Long,
        revisionId: String,
    ): File =
        checked(File(revisionScriptDir(scriptId), revisionId))

    fun revisionSourceFile(
        scriptId: Long,
        revisionId: String,
    ): File =
        checked(File(revisionDir(scriptId, revisionId), "source.user.js"))

    fun revisionManifestFile(
        scriptId: Long,
        revisionId: String,
    ): File =
        checked(File(revisionDir(scriptId, revisionId), "manifest.json"))

    fun checked(file: File): File {
        val rootPath = rootDir.canonicalFile.toPath()
        val filePath = file.canonicalFile.toPath()
        require(filePath.startsWith(rootPath)) {
            "Userscript storage path escapes canonical root: ${file.absolutePath}"
        }
        return file
    }

    companion object {
        private val DRAFT_ID_REGEX = Regex("""[A-Za-z0-9_-]{1,128}""")

        fun draftIdForScript(scriptId: Long): String {
            require(scriptId > 0L) { "Userscript draft owner must be positive" }
            return "script-$scriptId"
        }
    }
}

internal fun writeAtomicText(
    file: File,
    content: String,
) {
    file.parentFile?.let { parent ->
        require(parent.isDirectory || parent.mkdirs()) {
            "Unable to create parent directory for ${file.absolutePath}"
        }
    }
    val atomicFile = AtomicFile(file)
    var output: FileOutputStream? = null
    try {
        output = atomicFile.startWrite()
        output.write(content.toByteArray(Charsets.UTF_8))
        output.fd.sync()
        atomicFile.finishWrite(output)
    } catch (error: Throwable) {
        output?.let(atomicFile::failWrite)
        throw error
    }
}

internal fun readAtomicText(file: File): String =
    AtomicFile(file).openRead().use { input ->
        input.readBytes().toString(Charsets.UTF_8)
    }

internal fun atomicFileExists(file: File): Boolean =
    file.exists() || File(file.parentFile, "${file.name}.bak").exists()

internal fun writeSyncedBytes(
    file: File,
    bytes: ByteArray,
) {
    file.parentFile?.let { parent ->
        require(parent.isDirectory || parent.mkdirs()) {
            "Unable to create parent directory for ${file.absolutePath}"
        }
    }
    FileOutputStream(file).use { output ->
        output.write(bytes)
        output.fd.sync()
    }
}

internal fun moveDirectoryAtomically(
    source: File,
    target: File,
) {
    require(source.isDirectory) {
        "Prepared userscript revision does not exist: ${source.absolutePath}"
    }
    require(!target.exists()) {
        "Userscript revision already exists: ${target.absolutePath}"
    }
    target.parentFile?.let { parent ->
        require(parent.isDirectory || parent.mkdirs()) {
            "Unable to create revision parent directory: ${parent.absolutePath}"
        }
    }
    Files.move(
        source.toPath(),
        target.toPath(),
        StandardCopyOption.ATOMIC_MOVE,
    )
}

internal enum class UserscriptTransactionRecoveryDecision {
    KEEP_COMMITTED_REVISION,
    REMOVE_UNCOMMITTED_REVISION,
}

internal fun decideUserscriptTransactionRecovery(
    activeRevisionId: String?,
    journalRevisionId: String,
): UserscriptTransactionRecoveryDecision =
    if (activeRevisionId == journalRevisionId) {
        UserscriptTransactionRecoveryDecision.KEEP_COMMITTED_REVISION
    } else {
        UserscriptTransactionRecoveryDecision.REMOVE_UNCOMMITTED_REVISION
    }

internal fun InputStream.readUserscriptLimitedBytes(maxBytes: Long): ByteArray {
    require(maxBytes > 0L) { "Userscript response limit must be positive" }
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) {
            break
        }
        total += count
        require(total <= maxBytes) {
            "Userscript response exceeds its size limit"
        }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
