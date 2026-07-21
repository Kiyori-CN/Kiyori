package com.ai.assistance.operit.data.security

import android.content.Context
import android.util.AtomicFile
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class PluginDenylistPayload(
    val schemaVersion: Int = 1,
    val version: Int = 0,
    val updatedAt: String? = null,
    val hashAlgorithm: String = "",
    val match: String = "",
    val action: String = "",
    val entries: List<PluginDenylistEntry> = emptyList()
)

@Serializable
data class PluginDenylistEntry(
    val sha256: String = "",
    val note: String? = null
)

class PluginDenylistRepository(
    context: Context
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    fun findDeniedImport(file: File): PluginDenylistEntry? {
        if (!file.isFile || !file.canRead()) return null

        return runCatching {
            val payload = readCachedPayload() ?: return@runCatching null
            val sha256 = sha256Hex(file)
            payload.entries.firstOrNull { entry -> entry.sha256 == sha256 }
        }.onFailure { error ->
            AppLogger.w(TAG, "Plugin denylist check failed for ${file.absolutePath}", error)
        }.getOrNull()
    }

    fun cacheSignature(): String {
        val file = cacheFile()
        val readableFile =
            when {
                file.exists() && file.isFile -> file
                atomicBackupFile(file).exists() -> atomicBackupFile(file)
                else -> null
            }
        return if (readableFile != null) {
            "${readableFile.length()}:${readableFile.lastModified()}"
        } else {
            "none"
        }
    }

    private fun readCachedPayload(): PluginDenylistPayload? {
        val file = cacheFile()
        if (!file.exists() && !atomicBackupFile(file).exists()) return null

        return runCatching {
            val content = AtomicFile(file).openRead().use { input -> input.readBytes().toString(Charsets.UTF_8) }
            val payload = json.decodeFromString<PluginDenylistPayload>(content)
            validatePayload(payload)
            payload
        }.onFailure { error ->
            AppLogger.w(TAG, "Ignoring invalid cached plugin denylist", error)
        }.getOrNull()
    }

    private fun validatePayload(payload: PluginDenylistPayload) {
        require(payload.schemaVersion == SCHEMA_VERSION) { "Unsupported denylist payload schema" }
        require(payload.version > 0) { "Denylist payload has no version" }
        require(payload.hashAlgorithm == HASH_ALGORITHM) { "Unsupported denylist hash algorithm" }
        require(payload.match == MATCH_MODE) { "Unsupported denylist match mode" }
        require(payload.action == ACTION) { "Unsupported denylist action" }

        val hashes = HashSet<String>()
        payload.entries.forEachIndexed { index, entry ->
            require(SHA256_PATTERN.matches(entry.sha256)) { "Invalid SHA-256 at denylist entry $index" }
            require(hashes.add(entry.sha256)) { "Duplicate SHA-256 at denylist entry $index" }
        }
    }

    private fun cacheFile(): File = File(File(appContext.filesDir, CACHE_DIRECTORY), CACHE_FILE_NAME)

    private fun atomicBackupFile(file: File): File = File(file.parentFile, "${file.name}.bak")

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            String.format(Locale.US, "%02x", byte.toInt() and 0xff)
        }
    }

    companion object {
        private const val TAG = "PluginDenylistRepo"
        private const val CACHE_DIRECTORY = "plugin_denylist"
        private const val CACHE_FILE_NAME = "denylist.json"
        private const val SCHEMA_VERSION = 1
        private const val HASH_ALGORITHM = "sha256"
        private const val MATCH_MODE = "raw_file_bytes"
        private const val ACTION = "reject_import"
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
