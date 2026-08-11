package com.ai.assistance.operit.core.tools.packTool

import java.util.Locale

internal object ToolPkgArtifactPolicy {
    const val SCANNER_VERSION = 1
    const val MAX_ARCHIVE_BYTES = 256L * 1024L * 1024L
    const val MAX_ENTRY_COUNT = 4096
    const val MAX_UNPACKED_BYTES = 512L * 1024L * 1024L
    const val MAX_MANIFEST_BYTES = 1L * 1024L * 1024L
    const val MAX_TEXT_ENTRY_BYTES = 4L * 1024L * 1024L
    const val MAX_GENERAL_ENTRY_BYTES = 128L * 1024L * 1024L
    const val MAX_PATH_DEPTH = 32
    const val MAX_NORMALIZED_PATH_LENGTH = 240
    const val MAX_COMPRESSION_RATIO = 200L

    val BLOCKED_DIRECTORY_NAMES =
        setOf(
            ".git",
            ".backup",
            ".history",
            "__pycache__",
            "node_modules",
            ".gradle",
            ".idea",
        )

    val BLOCKED_EXACT_FILE_NAMES =
        setOf(
            ".env",
            ".ds_store",
            "thumbs.db",
        )

    val BLOCKED_FILE_SUFFIXES =
        setOf(
            ".pem",
            ".key",
            ".p12",
            ".pfx",
            ".jks",
            ".keystore",
            ".swp",
            ".tmp",
        )

    val ACTIVE_TEXT_SUFFIXES =
        setOf(
            ".js",
            ".ts",
            ".json",
            ".hjson",
            ".html",
            ".css",
        )

    val LEGACY_OPERIT_PATH_PATTERNS =
        listOf(
            Regex(
                """${Regex.escape(androidAbsolutePath("sdcard", "Download", "Operit"))}(?:/|["'\s]|$)""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """/storage/emulated/\d+/Download/Operit(?:/|["'\s]|$)""",
                RegexOption.IGNORE_CASE,
            ),
        )

    val FIXED_APPLICATION_PATH_PATTERNS =
        listOf(
            Regex(
                """${Regex.escape(androidAbsolutePath("data", "user"))}/\d+/[A-Za-z0-9._-]+/""",
            ),
            Regex(
                """${Regex.escape(androidAbsolutePath("sdcard", "Android", "data"))}/[A-Za-z0-9._-]+/""",
            ),
        )

    val PRIVATE_KEY_MARKERS =
        listOf(
            "-----BEGIN PRIVATE KEY-----",
            "-----BEGIN RSA PRIVATE KEY-----",
            "-----BEGIN EC PRIVATE KEY-----",
            "-----BEGIN OPENSSH PRIVATE KEY-----",
        )

    fun maximumEntryBytes(entryName: String): Long {
        val lower = entryName.lowercase(Locale.ROOT)
        return when {
            isManifestEntry(lower) -> MAX_MANIFEST_BYTES
            ACTIVE_TEXT_SUFFIXES.any(lower::endsWith) -> MAX_TEXT_ENTRY_BYTES
            else -> MAX_GENERAL_ENTRY_BYTES
        }
    }

    fun isManifestEntry(entryName: String): Boolean {
        val fileName = entryName.substringAfterLast('/').lowercase(Locale.ROOT)
        return fileName == "manifest.json" || fileName == "manifest.hjson"
    }

    private fun androidAbsolutePath(vararg segments: String): String =
        "/" + segments.joinToString("/")
}
