package com.kiyori.platform.storage

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

enum class ToolPkgStorageNamespace(
    internal val wireName: String,
    internal val quotaBytes: Long,
) {
    PRIVATE_DATA("privateData", 64L * 1024L * 1024L),
    CACHE("cache", 128L * 1024L * 1024L);

    companion object {
        fun fromWireName(value: String): ToolPkgStorageNamespace {
            return entries.firstOrNull { namespace -> namespace.wireName == value.trim() }
                ?: throw IllegalArgumentException("Unsupported ToolPkg storage namespace: $value")
        }
    }
}

data class ToolPkgStorageReadResult(
    val exists: Boolean,
    val text: String?,
)

/**
 * 一个实例只绑定一个宿主确认过的 ToolPkg 容器身份。
 *
 * 调用方不能传入 package ID；如果把身份放回 JavaScript 参数，恶意包就能请求其他包的目录。
 */
class ToolPkgStorageService(
    context: Context,
    containerPackageName: String,
) {
    private val appContext = context.applicationContext
    val packageKey: String = packageKey(containerPackageName)

    fun readText(
        namespace: ToolPkgStorageNamespace,
        relativePath: String,
    ): ToolPkgStorageReadResult {
        return ToolPkgStorageLocks.withPackageLock(packageKey) {
            val target = resolveFile(namespace, relativePath)
            if (!target.exists()) {
                return@withPackageLock ToolPkgStorageReadResult(exists = false, text = null)
            }
            require(target.isFile) { "ToolPkg storage path is not a file" }
            require(target.length() <= MAX_TEXT_BYTES) {
                "ToolPkg storage file exceeds the text read limit"
            }
            ToolPkgStorageReadResult(
                exists = true,
                text = target.readText(StandardCharsets.UTF_8),
            )
        }
    }

    fun writeText(
        namespace: ToolPkgStorageNamespace,
        relativePath: String,
        text: String,
    ) {
        ToolPkgStorageLocks.withPackageLock(packageKey) {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            require(bytes.size <= MAX_TEXT_BYTES) {
                "ToolPkg storage text exceeds the write limit"
            }
            val target = resolveFile(namespace, relativePath)
            val root = rootDir(namespace)
            val currentSize = directorySize(root)
            val existingSize = target.takeIf(File::isFile)?.length() ?: 0L
            val projectedSize = currentSize - existingSize + bytes.size
            require(projectedSize <= namespace.quotaBytes) {
                "ToolPkg ${namespace.wireName} quota exceeded"
            }
            val parent = requireNotNull(target.parentFile) { "ToolPkg storage parent is unavailable" }
            require(parent.isDirectory || parent.mkdirs()) {
                "Unable to create ToolPkg storage directory"
            }
            val atomicFile = AtomicFile(target)
            val output = atomicFile.startWrite()
            try {
                output.write(bytes)
                output.flush()
                atomicFile.finishWrite(output)
            } catch (error: Exception) {
                atomicFile.failWrite(output)
                throw error
            }
        }
    }

    fun exists(
        namespace: ToolPkgStorageNamespace,
        relativePath: String,
    ): Boolean {
        return ToolPkgStorageLocks.withPackageLock(packageKey) {
            resolveFile(namespace, relativePath).isFile
        }
    }

    fun delete(
        namespace: ToolPkgStorageNamespace,
        relativePath: String,
    ): Boolean {
        return ToolPkgStorageLocks.withPackageLock(packageKey) {
            val target = resolveFile(namespace, relativePath)
            if (!target.exists()) {
                return@withPackageLock false
            }
            require(target.isFile) { "ToolPkg storage delete only accepts files" }
            check(target.delete()) { "Unable to delete ToolPkg storage file" }
            removeEmptyParents(target.parentFile, rootDir(namespace))
            true
        }
    }

    fun namespaceSize(namespace: ToolPkgStorageNamespace): Long {
        return ToolPkgStorageLocks.withPackageLock(packageKey) {
            directorySize(rootDir(namespace))
        }
    }

    private fun rootDir(namespace: ToolPkgStorageNamespace): File {
        return when (namespace) {
            ToolPkgStorageNamespace.PRIVATE_DATA ->
                ToolPkgPrivateDataLayout.resolvePrivateDataDir(appContext, packageKey)
            ToolPkgStorageNamespace.CACHE ->
                KiyoriPaths.toolPkgCacheDir(appContext, packageKey)
        }
    }

    private fun resolveFile(
        namespace: ToolPkgStorageNamespace,
        relativePath: String,
    ): File {
        val normalizedPath = normalizeRelativePath(relativePath)
        val root = rootDir(namespace).canonicalFile
        val target = File(root, normalizedPath).canonicalFile
        val rootPrefix = root.path.trimEnd(File.separatorChar) + File.separator
        require(target.path.startsWith(rootPrefix)) {
            "ToolPkg storage path escapes its namespace"
        }
        return target
    }

    private fun directorySize(root: File): Long {
        if (!root.exists()) {
            return 0L
        }
        var total = 0L
        root.walkTopDown()
            .filter(File::isFile)
            .forEach { file ->
                val length = file.length()
                total =
                    if (Long.MAX_VALUE - total < length) {
                        Long.MAX_VALUE
                    } else {
                        total + length
                    }
            }
        return total
    }

    private fun removeEmptyParents(
        start: File?,
        root: File,
    ) {
        val canonicalRoot = root.canonicalFile
        var current = start?.canonicalFile
        while (current != null && current != canonicalRoot) {
            if (current.listFiles()?.isNotEmpty() == true || !current.delete()) {
                return
            }
            current = current.parentFile
        }
    }

    companion object {
        internal const val MAX_TEXT_BYTES = 4 * 1024 * 1024
        internal const val MAX_PATH_DEPTH = 32
        internal const val MAX_NORMALIZED_PATH_LENGTH = 240
        private val WINDOWS_DRIVE_PATH = Regex("""^[A-Za-z]:""")

        internal fun normalizeRelativePath(rawPath: String): String {
            require(rawPath.isNotBlank()) { "ToolPkg storage relative path is required" }
            require(rawPath == rawPath.trim()) {
                "ToolPkg storage path must not have surrounding whitespace"
            }
            require(!rawPath.startsWith('/')) { "ToolPkg storage path must be relative" }
            require('\\' !in rawPath) { "ToolPkg storage path must use forward slashes" }
            require('\u0000' !in rawPath) { "ToolPkg storage path contains NUL" }
            require(!WINDOWS_DRIVE_PATH.containsMatchIn(rawPath)) {
                "ToolPkg storage path must not contain a drive prefix"
            }
            require(!rawPath.contains("://")) { "ToolPkg storage path must not be a URI" }
            val segments = rawPath.split('/')
            require(segments.size <= MAX_PATH_DEPTH) {
                "ToolPkg storage path is too deep"
            }
            require(
                segments.all { segment ->
                    segment.isNotEmpty() &&
                        segment != "." &&
                        segment != ".." &&
                        segment == segment.trimEnd() &&
                        segment.none { character -> character.code < 32 }
                },
            ) {
                "ToolPkg storage path contains an invalid segment"
            }
            val normalized = segments.joinToString("/")
            require(normalized.length <= MAX_NORMALIZED_PATH_LENGTH) {
                "ToolPkg storage path is too long"
            }
            return normalized
        }

        fun packageKey(containerPackageName: String): String {
            val normalized = containerPackageName.trim().lowercase(Locale.ROOT)
            require(normalized.isNotBlank()) { "ToolPkg container package name is required" }
            val safePrefix =
                normalized
                    .map { character ->
                        if (
                            character.isLetterOrDigit() ||
                                character == '.' ||
                                character == '_' ||
                                character == '-'
                        ) {
                            character
                        } else {
                            '_'
                        }
                    }
                    .joinToString("")
                    .trim('.', '_', '-')
                    .ifBlank { "toolpkg" }
                    .take(64)
            val digest =
                MessageDigest.getInstance("SHA-256")
                    .digest(normalized.toByteArray(StandardCharsets.UTF_8))
                    .joinToString("") { byte -> "%02x".format(byte) }
                    .take(16)
            return "$safePrefix-$digest"
        }
    }
}
