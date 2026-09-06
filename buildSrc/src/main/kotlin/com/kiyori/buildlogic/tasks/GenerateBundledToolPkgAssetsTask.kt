package com.kiyori.buildlogic.tasks

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateBundledToolPkgAssetsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val whitelistFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val examplesDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    private val blockedDirectoryNames =
        setOf(
            ".git",
            ".backup",
            ".history",
            "__pycache__",
            "node_modules",
            ".gradle",
            ".idea",
        )
    private val blockedExactFileNames = setOf(".env", ".ds_store", "thumbs.db")
    private val blockedFileSuffixes =
        setOf(".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".swp", ".tmp")
    private val activeTextSuffixes = setOf(".js", ".ts", ".json", ".hjson", ".html", ".css")
    private val privateKeyMarkers =
        listOf(
            "-----BEGIN PRIVATE KEY-----",
            "-----BEGIN RSA PRIVATE KEY-----",
            "-----BEGIN EC PRIVATE KEY-----",
            "-----BEGIN OPENSSH PRIVATE KEY-----",
        )
    private val legacyOperitPathPatterns =
        listOf(
            Regex("""/sdcard/Download/Operit(?:/|["'\s]|$)""", RegexOption.IGNORE_CASE),
            Regex(
                """/storage/emulated/\d+/Download/Operit(?:/|["'\s]|$)""",
                RegexOption.IGNORE_CASE,
            ),
        )
    private val fixedApplicationPathPatterns =
        listOf(
            Regex("""/data/user/\d+/[A-Za-z0-9._-]+/"""),
            Regex("""/sdcard/Android/data/[A-Za-z0-9._-]+/"""),
        )

    @TaskAction
    fun generate() {
        val examplesRoot = examplesDirectory.get().asFile.canonicalFile
        val outputRoot = outputDirectory.get().asFile
        val outputPackages = outputRoot.resolve("packages")
        check(!outputRoot.exists() || outputRoot.deleteRecursively()) {
            "Unable to clear generated ToolPkg assets directory: $outputRoot"
        }
        check(outputPackages.mkdirs() || outputPackages.isDirectory) {
            "Unable to create generated ToolPkg assets directory: $outputPackages"
        }

        val items =
            whitelistFile.get().asFile.readLines(Charsets.UTF_8)
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }

        val outputNames = mutableSetOf<String>()
        items.forEach { item ->
            check('\\' !in item) {
                "Bundled package whitelist paths must use forward slashes: $item"
            }
            val normalized = item.trim().trim('/')
            check(
                normalized.isNotEmpty() &&
                    !item.trim().startsWith("/") &&
                    ".." !in normalized.split('/'),
            ) {
                "Bundled package path escapes examples/: $item"
            }
            val stem =
                when {
                    normalized.endsWith(".toolpkg", ignoreCase = true) -> normalized.dropLast(8)
                    normalized.endsWith(".js", ignoreCase = true) -> normalized.dropLast(3)
                    else -> normalized
                }.trimEnd('/')
            var unresolvedPackageRoot = examplesRoot
            stem.split('/').forEach { segment ->
                unresolvedPackageRoot = unresolvedPackageRoot.resolve(segment)
                check(!Files.isSymbolicLink(unresolvedPackageRoot.toPath())) {
                    "Bundled ToolPkg source path contains a symbolic link: $unresolvedPackageRoot"
                }
            }
            val packageRoot = unresolvedPackageRoot.canonicalFile
            check(packageRoot.toPath().startsWith(examplesRoot.toPath())) {
                "Bundled ToolPkg path escapes examples/: $item"
            }

            val manifestCandidates =
                listOf(packageRoot.resolve("manifest.hjson"), packageRoot.resolve("manifest.json"))
                    .filter(File::isFile)
            if (manifestCandidates.isEmpty()) {
                check(normalized.endsWith(".js", ignoreCase = true)) {
                    "Bundled ToolPkg has no manifest: $item"
                }
                return@forEach
            }
            check(manifestCandidates.size == 1) {
                "Bundled ToolPkg must contain exactly one manifest: $packageRoot"
            }
            val manifest = manifestCandidates.single()

            check(packageRoot.isDirectory) {
                "Bundled ToolPkg source is not a regular directory: $packageRoot"
            }
            check(
                packageRoot.resolve("dist/main.js").isFile ||
                    packageRoot.resolve("main.js").isFile
            ) {
                "Bundled ToolPkg has no built main runtime: $packageRoot"
            }

            val files = mutableListOf<File>()
            fun collect(path: File) {
                check(!Files.isSymbolicLink(path.toPath())) {
                    "Bundled ToolPkg cannot contain symbolic links: $path"
                }
                val relativePath = path.relativeTo(packageRoot).invariantSeparatorsPath
                validateBundledToolPkgPath(relativePath)
                if (path.isDirectory) {
                    path.listFiles().orEmpty().sortedBy(File::getName).forEach(::collect)
                } else if (path.isFile) {
                    validateBundledToolPkgFile(packageRoot, path)
                    files += path
                }
            }

            collect(manifest)
            listOf(
                "dist",
                "packages",
                "ui",
                "resources",
                "modules",
                "assets",
                "i18n",
                "workflow",
                "workspace",
            )
                .map(packageRoot::resolve)
                .filter(File::exists)
                .forEach(::collect)
            packageRoot.resolve("main.js").takeIf(File::isFile)?.let(::collect)
            val selectedFiles =
                files
                    .distinctBy { it.canonicalPath }
                    .sortedBy { it.relativeTo(packageRoot).invariantSeparatorsPath }
            check(selectedFiles.size <= MAX_TOOLPKG_ENTRY_COUNT) {
                "Bundled ToolPkg contains too many files: $packageRoot"
            }
            var unpackedBytes = 0L
            selectedFiles.forEach { source ->
                unpackedBytes = safeAdd(unpackedBytes, source.length())
                check(unpackedBytes <= MAX_TOOLPKG_UNPACKED_BYTES) {
                    "Bundled ToolPkg exceeds the total unpacked size limit: $packageRoot"
                }
            }

            val outputFile = outputPackages.resolve("${packageRoot.name}.toolpkg")
            check(outputNames.add(outputFile.name)) {
                "Bundled ToolPkg output name is duplicated: ${outputFile.name}"
            }
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { archive ->
                selectedFiles.forEach { source ->
                    val entry = ZipEntry(source.relativeTo(packageRoot).invariantSeparatorsPath)
                    entry.time = 0L
                    archive.putNextEntry(entry)
                    source.inputStream().use { input -> input.copyTo(archive) }
                    archive.closeEntry()
                }
            }
            check(outputFile.length() <= MAX_TOOLPKG_ARCHIVE_BYTES) {
                "Bundled ToolPkg exceeds the compressed size limit: $outputFile"
            }
            ZipFile(outputFile).use { archive ->
                val entries = archive.entries().asSequence().filterNot { it.isDirectory }.toList()
                check(entries.size == selectedFiles.size) {
                    "Bundled ToolPkg entry count changed during packaging: $outputFile"
                }
                entries.forEach { entry ->
                    check(
                        entry.size <= 0L ||
                            entry.compressedSize <= 0L ||
                            entry.size / entry.compressedSize <= MAX_TOOLPKG_COMPRESSION_RATIO,
                    ) {
                        "Bundled ToolPkg entry exceeds the compression-ratio limit: ${entry.name}"
                    }
                }
            }
        }
    }

    private fun validateBundledToolPkgPath(relativePath: String) {
        check(relativePath.isNotBlank() && relativePath == relativePath.trim()) {
            "Bundled ToolPkg path is blank or has surrounding whitespace: $relativePath"
        }
        check('\\' !in relativePath && '\u0000' !in relativePath && !relativePath.contains("://")) {
            "Bundled ToolPkg path is invalid: $relativePath"
        }
        val segments = relativePath.split('/')
        check(
            segments.size <= MAX_TOOLPKG_PATH_DEPTH &&
                segments.none { segment ->
                    segment.isBlank() ||
                        segment == "." ||
                        segment == ".." ||
                        segment != segment.trimEnd() ||
                        segment.any { character -> character.code < 32 }
                },
        ) {
            "Bundled ToolPkg path contains an invalid segment: $relativePath"
        }
        check(relativePath.length <= MAX_TOOLPKG_NORMALIZED_PATH_LENGTH) {
            "Bundled ToolPkg path is too long: $relativePath"
        }
        val lowercaseSegments = segments.map { segment -> segment.lowercase(Locale.ROOT) }
        val fileName = lowercaseSegments.last()
        check(lowercaseSegments.none(blockedDirectoryNames::contains)) {
            "Bundled ToolPkg contains a blocked directory: $relativePath"
        }
        check(
            fileName !in blockedExactFileNames &&
                !fileName.startsWith(".env.") &&
                blockedFileSuffixes.none(fileName::endsWith),
        ) {
            "Bundled ToolPkg contains a blocked file: $relativePath"
        }
    }

    private fun validateBundledToolPkgFile(
        packageRoot: File,
        source: File,
    ) {
        val relativePath = source.relativeTo(packageRoot).invariantSeparatorsPath
        val maximumBytes = maximumToolPkgEntryBytes(relativePath)
        check(source.length() <= maximumBytes) {
            "Bundled ToolPkg entry exceeds its size limit: $relativePath"
        }
        val lower = relativePath.lowercase(Locale.ROOT)
        if (
            activeTextSuffixes.none(lower::endsWith) &&
                lower.substringAfterLast('/') !in setOf("readme", "readme.md", "license", "license.md")
        ) {
            return
        }
        val text = source.readText(Charsets.UTF_8)
        privateKeyMarkers.forEach { marker ->
            check(marker !in text) {
                "Bundled ToolPkg contains private key material: $relativePath"
            }
        }
        if (isBundledDocumentationEntry(lower)) {
            return
        }
        legacyOperitPathPatterns.forEach { pattern ->
            check(!pattern.containsMatchIn(text)) {
                "Bundled ToolPkg executable content contains the old Operit path: $relativePath"
            }
        }
        fixedApplicationPathPatterns.forEach { pattern ->
            check(!pattern.containsMatchIn(text)) {
                "Bundled ToolPkg executable content contains a fixed application path: $relativePath"
            }
        }
    }

    private fun maximumToolPkgEntryBytes(relativePath: String): Long {
        val lower = relativePath.lowercase(Locale.ROOT)
        val fileName = lower.substringAfterLast('/')
        return when {
            fileName == "manifest.json" || fileName == "manifest.hjson" ->
                MAX_TOOLPKG_MANIFEST_BYTES
            activeTextSuffixes.any(lower::endsWith) -> MAX_TOOLPKG_TEXT_ENTRY_BYTES
            else -> MAX_TOOLPKG_GENERAL_ENTRY_BYTES
        }
    }

    private fun isBundledDocumentationEntry(lowercasePath: String): Boolean {
        val segments = lowercasePath.split('/')
        val fileName = segments.last()
        return "docs" in segments ||
            fileName.startsWith("readme") ||
            fileName.startsWith("license") ||
            fileName.startsWith("changelog")
    }

    private fun safeAdd(
        left: Long,
        right: Long,
    ): Long {
        return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }

    companion object {
        private const val MAX_TOOLPKG_ARCHIVE_BYTES = 256L * 1024L * 1024L
        private const val MAX_TOOLPKG_ENTRY_COUNT = 4096
        private const val MAX_TOOLPKG_UNPACKED_BYTES = 512L * 1024L * 1024L
        private const val MAX_TOOLPKG_MANIFEST_BYTES = 1L * 1024L * 1024L
        private const val MAX_TOOLPKG_TEXT_ENTRY_BYTES = 4L * 1024L * 1024L
        private const val MAX_TOOLPKG_GENERAL_ENTRY_BYTES = 128L * 1024L * 1024L
        private const val MAX_TOOLPKG_PATH_DEPTH = 32
        private const val MAX_TOOLPKG_NORMALIZED_PATH_LENGTH = 240
        private const val MAX_TOOLPKG_COMPRESSION_RATIO = 200L
    }
}
