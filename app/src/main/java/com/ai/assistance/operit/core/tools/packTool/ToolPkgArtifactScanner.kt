package com.ai.assistance.operit.core.tools.packTool

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal enum class ToolPkgArtifactFindingSeverity {
    ERROR,
    WARNING,
}

internal data class ToolPkgArtifactFinding(
    val code: String,
    val severity: ToolPkgArtifactFindingSeverity,
    val entry: String?,
    val message: String,
)

@Serializable
private data class ToolPkgArtifactFindingDocument(
    val code: String,
    val severity: String,
    val entry: String?,
    val message: String,
)

@Serializable
private data class ToolPkgArtifactScanDocument(
    @SerialName("scanner_version")
    val scannerVersion: Int,
    @SerialName("artifact_sha256")
    val artifactSha256: String,
    @SerialName("toolpkg_id")
    val toolPkgId: String?,
    @SerialName("toolpkg_version")
    val toolPkgVersion: String?,
    @SerialName("archive_bytes")
    val archiveBytes: Long,
    @SerialName("entry_count")
    val entryCount: Int,
    @SerialName("unpacked_bytes")
    val unpackedBytes: Long,
    @SerialName("tree_digest")
    val treeDigest: String,
    val findings: List<ToolPkgArtifactFindingDocument>,
)

internal data class ToolPkgArtifactScanReport(
    val artifactSha256: String,
    val toolPkgId: String?,
    val toolPkgVersion: String?,
    val archiveBytes: Long,
    val entryCount: Int,
    val unpackedBytes: Long,
    val treeDigest: String,
    val findings: List<ToolPkgArtifactFinding>,
) {
    val accepted: Boolean
        get() = findings.none { finding -> finding.severity == ToolPkgArtifactFindingSeverity.ERROR }

    fun requireAccepted() {
        if (!accepted) {
            val summary =
                findings
                    .filter { finding ->
                        finding.severity == ToolPkgArtifactFindingSeverity.ERROR
                    }
                    .joinToString("; ") { finding ->
                        buildString {
                            append(finding.code)
                            finding.entry?.let { entry -> append("[").append(entry).append("]") }
                            append(": ").append(finding.message)
                        }
                    }
            throw IllegalArgumentException("ToolPkg artifact rejected: $summary")
        }
    }

    fun toJson(): String {
        return artifactReportJson.encodeToString(
            ToolPkgArtifactScanDocument(
                scannerVersion = ToolPkgArtifactPolicy.SCANNER_VERSION,
                artifactSha256 = artifactSha256,
                toolPkgId = toolPkgId,
                toolPkgVersion = toolPkgVersion,
                archiveBytes = archiveBytes,
                entryCount = entryCount,
                unpackedBytes = unpackedBytes,
                treeDigest = treeDigest,
                findings =
                    findings.map { finding ->
                        ToolPkgArtifactFindingDocument(
                            code = finding.code,
                            severity = finding.severity.name.lowercase(Locale.ROOT),
                            entry = finding.entry,
                            message = finding.message,
                        )
                    },
            )
        )
    }

    companion object {
        private val artifactReportJson =
            Json {
                encodeDefaults = true
                explicitNulls = true
            }
    }
}

internal object ToolPkgArtifactScanner {

    fun scan(file: File): ToolPkgArtifactScanReport {
        require(file.isFile) { "ToolPkg artifact does not exist: ${file.absolutePath}" }
        val findings = mutableListOf<ToolPkgArtifactFinding>()
        val archiveBytes = file.length()
        if (archiveBytes > ToolPkgArtifactPolicy.MAX_ARCHIVE_BYTES) {
            findings.error(
                code = "TPKG-ARCHIVE-SIZE",
                entry = null,
                message = "Archive exceeds ${ToolPkgArtifactPolicy.MAX_ARCHIVE_BYTES} bytes",
            )
        }

        val artifactSha256 = sha256(file)
        val normalizedNames = linkedSetOf<String>()
        val lowercaseNames = linkedMapOf<String, String>()
        val treeDigest = MessageDigest.getInstance("SHA-256")
        var entryCount = 0
        var unpackedBytes = 0L
        var manifestEntryName: String? = null
        var manifestText: String? = null

        ZipFile(file).use { archive ->
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val normalizedName = ToolPkgArchiveParser.normalizeZipEntryPath(entry.name)
                if (normalizedName == null) {
                    findings.error(
                        code = "TPKG-ENTRY-PATH",
                        entry = entry.name,
                        message = "Archive entry path is invalid",
                    )
                    continue
                }
                inspectBlockedPath(normalizedName, findings)
                if (entry.isDirectory) {
                    continue
                }

                entryCount += 1
                if (entryCount > ToolPkgArtifactPolicy.MAX_ENTRY_COUNT) {
                    findings.error(
                        code = "TPKG-ENTRY-COUNT",
                        entry = normalizedName,
                        message = "Archive contains too many files",
                    )
                }
                if (!normalizedNames.add(normalizedName)) {
                    findings.error(
                        code = "TPKG-DUPLICATE-ENTRY",
                        entry = normalizedName,
                        message = "Archive contains a duplicate normalized path",
                    )
                }
                val lowercaseName = normalizedName.lowercase(Locale.ROOT)
                val casePrevious = lowercaseNames.putIfAbsent(lowercaseName, normalizedName)
                if (casePrevious != null && casePrevious != normalizedName) {
                    findings.error(
                        code = "TPKG-CASE-CONFLICT",
                        entry = normalizedName,
                        message = "Entry conflicts by case with $casePrevious",
                    )
                }

                val declaredSize = entry.size
                if (declaredSize > ToolPkgArtifactPolicy.maximumEntryBytes(normalizedName)) {
                    findings.error(
                        code = "TPKG-ENTRY-SIZE",
                        entry = normalizedName,
                        message = "Entry exceeds its size limit",
                    )
                }
                if (
                    declaredSize > 0L &&
                        entry.compressedSize > 0L &&
                        declaredSize / entry.compressedSize > ToolPkgArtifactPolicy.MAX_COMPRESSION_RATIO
                ) {
                    findings.error(
                        code = "TPKG-COMPRESSION-RATIO",
                        entry = normalizedName,
                        message = "Entry compression ratio exceeds the allowed limit",
                    )
                }

                val content =
                    archive.getInputStream(entry).use { input ->
                        readEntry(
                            input = input,
                            maximumBytes = ToolPkgArtifactPolicy.maximumEntryBytes(normalizedName),
                            entryName = normalizedName,
                            findings = findings,
                        )
                    }
                unpackedBytes = safeAdd(unpackedBytes, content.size.toLong())
                if (unpackedBytes > ToolPkgArtifactPolicy.MAX_UNPACKED_BYTES) {
                    findings.error(
                        code = "TPKG-UNPACKED-SIZE",
                        entry = normalizedName,
                        message = "Archive exceeds the total unpacked size limit",
                    )
                }

                treeDigest.update(normalizedName.toByteArray(StandardCharsets.UTF_8))
                treeDigest.update(0.toByte())
                treeDigest.update(MessageDigest.getInstance("SHA-256").digest(content))

                if (ToolPkgArtifactPolicy.isManifestEntry(normalizedName)) {
                    if (manifestEntryName != null) {
                        findings.error(
                            code = "TPKG-MANIFEST-DUPLICATE",
                            entry = normalizedName,
                            message = "Archive contains more than one manifest",
                        )
                    } else {
                        manifestEntryName = normalizedName
                        manifestText = content.toString(StandardCharsets.UTF_8)
                    }
                }
                inspectTextContent(normalizedName, content, findings)
            }
        }

        if (manifestEntryName == null || manifestText == null) {
            findings.error(
                code = "TPKG-MANIFEST-MISSING",
                entry = null,
                message = "manifest.json or manifest.hjson is required",
            )
        }

        val manifest =
            if (manifestEntryName != null && manifestText != null) {
                runCatching {
                    ToolPkgArchiveParser.parseManifest(manifestText, manifestEntryName)
                }.onFailure { error ->
                    findings.error(
                        code = "TPKG-MANIFEST-PARSE",
                        entry = manifestEntryName,
                        message = error.message ?: error.javaClass.simpleName,
                    )
                }.getOrNull()
            } else {
                null
            }

        if (manifest != null) {
            inspectManifestDistribution(
                manifest = manifest,
                manifestEntryName = requireNotNull(manifestEntryName),
                entryNames = normalizedNames,
                findings = findings,
            )
        }

        return ToolPkgArtifactScanReport(
            artifactSha256 = artifactSha256,
            toolPkgId = manifest?.toolpkgId?.trim()?.takeIf(String::isNotBlank),
            toolPkgVersion = manifest?.version?.trim()?.takeIf(String::isNotBlank),
            archiveBytes = archiveBytes,
            entryCount = entryCount,
            unpackedBytes = unpackedBytes,
            treeDigest = treeDigest.digest().toHex(),
            findings = findings.toList(),
        )
    }

    private fun inspectManifestDistribution(
        manifest: ToolPkgManifest,
        manifestEntryName: String,
        entryNames: Set<String>,
        findings: MutableList<ToolPkgArtifactFinding>,
    ) {
        if (manifest.schemaVersion < 2) {
            return
        }
        val includes =
            manifest.distribution
                ?.include
                .orEmpty()
                .map(String::trim)
                .filter(String::isNotBlank)
        if (includes.isEmpty()) {
            findings.error(
                code = "TPKG-DISTRIBUTION-MISSING",
                entry = manifestEntryName,
                message = "schema_version 2 requires distribution.include",
            )
            return
        }
        includes.forEach { pattern ->
            if (!isSafeIncludePattern(pattern)) {
                findings.error(
                    code = "TPKG-DISTRIBUTION-PATTERN",
                    entry = manifestEntryName,
                    message = "Invalid distribution include pattern: $pattern",
                )
            }
        }
        entryNames.forEach { entryName ->
            if (includes.none { pattern -> toolPkgGlobMatches(pattern, entryName) }) {
                findings.error(
                    code = "TPKG-UNDECLARED-ENTRY",
                    entry = entryName,
                    message = "Entry is not declared by distribution.include",
                )
            }
        }
        buildList {
            add(manifestEntryName)
            add(manifest.main)
            addAll(manifest.subpackages.map(ToolPkgManifestSubpackage::entry))
            addAll(manifest.resources.map(ToolPkgManifestResource::path))
            addAll(manifest.wasmModules.map(ToolPkgManifestWasmModule::path))
        }.filter(String::isNotBlank)
            .forEach { requiredPath ->
                val normalized = ToolPkgArchiveParser.normalizeZipEntryPath(requiredPath)
                if (
                    normalized == null ||
                        includes.none { pattern -> toolPkgGlobMatches(pattern, normalized) }
                ) {
                    findings.error(
                        code = "TPKG-REQUIRED-ENTRY-NOT-INCLUDED",
                        entry = requiredPath,
                        message = "Manifest-referenced path is not covered by distribution.include",
                    )
                }
            }
    }

    private fun inspectBlockedPath(
        normalizedName: String,
        findings: MutableList<ToolPkgArtifactFinding>,
    ) {
        val segments = normalizedName.split('/')
        val lowercaseSegments = segments.map { segment -> segment.lowercase(Locale.ROOT) }
        val fileName = lowercaseSegments.last()
        if (
            lowercaseSegments.any { segment ->
                segment in ToolPkgArtifactPolicy.BLOCKED_DIRECTORY_NAMES
            }
        ) {
            findings.error(
                code = "TPKG-BLOCKED-DIRECTORY",
                entry = normalizedName,
                message = "Archive entry is inside a blocked directory",
            )
        }
        if (
            fileName in ToolPkgArtifactPolicy.BLOCKED_EXACT_FILE_NAMES ||
                fileName.startsWith(".env.") ||
                ToolPkgArtifactPolicy.BLOCKED_FILE_SUFFIXES.any(fileName::endsWith)
        ) {
            findings.error(
                code = "TPKG-BLOCKED-FILE",
                entry = normalizedName,
                message = "Archive entry is a blocked file type",
            )
        }
    }

    private fun inspectTextContent(
        normalizedName: String,
        content: ByteArray,
        findings: MutableList<ToolPkgArtifactFinding>,
    ) {
        val lowercaseName = normalizedName.lowercase(Locale.ROOT)
        val isText =
            ToolPkgArtifactPolicy.ACTIVE_TEXT_SUFFIXES.any(lowercaseName::endsWith) ||
                lowercaseName.substringAfterLast('/') in
                    setOf("readme", "readme.md", "license", "license.md")
        if (!isText || content.isEmpty()) {
            return
        }
        val text = content.toString(StandardCharsets.UTF_8)
        ToolPkgArtifactPolicy.PRIVATE_KEY_MARKERS.forEach { marker ->
            if (marker in text) {
                findings.error(
                    code = "TPKG-PRIVATE-KEY",
                    entry = normalizedName,
                    message = "Archive contains private key material",
                )
            }
        }
        if (isDocumentationEntry(lowercaseName)) {
            return
        }
        ToolPkgArtifactPolicy.LEGACY_OPERIT_PATH_PATTERNS.forEach { pattern ->
            if (pattern.containsMatchIn(text)) {
                findings.error(
                    code = "TPKG-LEGACY-ABSOLUTE-PATH",
                    entry = normalizedName,
                    message = "Executable content contains the old Operit public path",
                )
            }
        }
        ToolPkgArtifactPolicy.FIXED_APPLICATION_PATH_PATTERNS.forEach { pattern ->
            if (pattern.containsMatchIn(text)) {
                findings.error(
                    code = "TPKG-FIXED-APPLICATION-PATH",
                    entry = normalizedName,
                    message = "Executable content contains a fixed application sandbox path",
                )
            }
        }
    }

    private fun readEntry(
        input: InputStream,
        maximumBytes: Long,
        entryName: String,
        findings: MutableList<ToolPkgArtifactFinding>,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) {
                break
            }
            total = safeAdd(total, read.toLong())
            if (total > maximumBytes) {
                findings.error(
                    code = "TPKG-ENTRY-SIZE",
                    entry = entryName,
                    message = "Entry exceeds its size limit while reading",
                )
                break
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun isDocumentationEntry(lowercaseName: String): Boolean {
        val segments = lowercaseName.split('/')
        val fileName = segments.last()
        return "docs" in segments ||
            fileName.startsWith("readme") ||
            fileName.startsWith("license") ||
            fileName.startsWith("changelog")
    }

    private fun isSafeIncludePattern(pattern: String): Boolean {
        return pattern.isNotBlank() &&
            !pattern.startsWith('/') &&
            '\\' !in pattern &&
            '\u0000' !in pattern &&
            !pattern.contains("..") &&
            !pattern.contains("://") &&
            pattern.length <= ToolPkgArtifactPolicy.MAX_NORMALIZED_PATH_LENGTH
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun safeAdd(left: Long, right: Long): Long {
        return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun MutableList<ToolPkgArtifactFinding>.error(
        code: String,
        entry: String?,
        message: String,
    ) {
        add(
            ToolPkgArtifactFinding(
                code = code,
                severity = ToolPkgArtifactFindingSeverity.ERROR,
                entry = entry,
                message = message,
            ),
        )
    }
}

internal fun toolPkgGlobMatches(
    rawPattern: String,
    normalizedPath: String,
): Boolean {
    val pattern = rawPattern.trim().trimStart('/')
    if (pattern.isBlank()) {
        return false
    }
    val regex =
        buildString {
            append('^')
            var index = 0
            while (index < pattern.length) {
                val character = pattern[index]
                when {
                    character == '*' &&
                        index + 1 < pattern.length &&
                        pattern[index + 1] == '*' -> {
                        if (index + 2 < pattern.length && pattern[index + 2] == '/') {
                            append("(?:.*/)?")
                            index += 3
                        } else {
                            append(".*")
                            index += 2
                        }
                    }
                    character == '*' -> {
                        append("[^/]*")
                        index += 1
                    }
                    character == '?' -> {
                        append("[^/]")
                        index += 1
                    }
                    else -> {
                        append(Regex.escape(character.toString()))
                        index += 1
                    }
                }
            }
            append('$')
        }
    return Regex(regex).matches(normalizedPath)
}
