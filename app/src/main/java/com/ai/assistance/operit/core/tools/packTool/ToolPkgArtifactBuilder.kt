package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import com.kiyori.platform.storage.KiyoriPaths
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Locale
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class ToolPkgArtifactBuildResult(
    val archiveFile: File,
    val report: ToolPkgArtifactScanReport,
)

internal class ToolPkgArtifactBuilder(private val context: Context) {

    fun build(sourceDirectory: File): ToolPkgArtifactBuildResult {
        val sourceRoot = sourceDirectory.canonicalFile
        require(sourceRoot.isDirectory) {
            "ToolPkg source directory does not exist: ${sourceRoot.absolutePath}"
        }
        val manifestFile =
            listOf(
                File(sourceRoot, "manifest.json"),
                File(sourceRoot, "manifest.hjson"),
            ).filter(File::isFile)
                .singleOrNull()
                ?: throw IllegalArgumentException(
                    "ToolPkg source must contain exactly one manifest.json or manifest.hjson",
                )
        require(manifestFile.length() <= ToolPkgArtifactPolicy.MAX_MANIFEST_BYTES) {
            "ToolPkg manifest exceeds ${ToolPkgArtifactPolicy.MAX_MANIFEST_BYTES} bytes"
        }
        val manifest =
            ToolPkgArchiveParser.parseManifest(
                manifestFile.readText(),
                manifestFile.name,
            )
        require(manifest.toolpkgId.isNotBlank()) { "manifest.toolpkg_id is required" }
        val files = selectFiles(sourceRoot, manifestFile, manifest)
        require(files.size <= ToolPkgArtifactPolicy.MAX_ENTRY_COUNT) {
            "ToolPkg build selected too many files"
        }
        var selectedBytes = 0L
        files.forEach { file ->
            val relativePath = file.relativeTo(sourceRoot).invariantSeparatorsPath
            val maximumBytes = ToolPkgArtifactPolicy.maximumEntryBytes(relativePath)
            require(file.length() <= maximumBytes) {
                "ToolPkg source entry exceeds its size limit: $relativePath"
            }
            selectedBytes =
                if (Long.MAX_VALUE - selectedBytes < file.length()) {
                    Long.MAX_VALUE
                } else {
                    selectedBytes + file.length()
                }
            require(selectedBytes <= ToolPkgArtifactPolicy.MAX_UNPACKED_BYTES) {
                "ToolPkg build exceeds the total unpacked size limit"
            }
        }
        val transactionId = UUID.randomUUID().toString()
        val transactionDir = KiyoriPaths.toolPkgBuildTransactionDir(context, transactionId)
        val outputFile =
            File(
                transactionDir,
                "${safeArtifactFileStem(manifest.toolpkgId)}.toolpkg",
            )

        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { archive ->
            archive.setLevel(Deflater.BEST_COMPRESSION)
            files
                .sortedBy { file -> file.relativeTo(sourceRoot).invariantSeparatorsPath }
                .forEach { source ->
                    val entryName = source.relativeTo(sourceRoot).invariantSeparatorsPath
                    val entry = ZipEntry(entryName).apply { time = 0L }
                    archive.putNextEntry(entry)
                    source.inputStream().use { input -> input.copyTo(archive) }
                    archive.closeEntry()
                }
        }
        val report = ToolPkgArtifactScanner.scan(outputFile)
        report.requireAccepted()
        return ToolPkgArtifactBuildResult(
            archiveFile = outputFile,
            report = report,
        )
    }

    private fun selectFiles(
        sourceRoot: File,
        manifestFile: File,
        manifest: ToolPkgManifest,
    ): List<File> {
        val allFiles = mutableListOf<File>()
        sourceRoot.walkTopDown().forEach { path ->
            require(!Files.isSymbolicLink(path.toPath())) {
                "ToolPkg source cannot contain symbolic links: ${path.absolutePath}"
            }
            if (path.isFile) {
                require(path.canonicalFile.toPath().startsWith(sourceRoot.toPath())) {
                    "ToolPkg source file escapes the source directory: ${path.absolutePath}"
                }
                val relativePath = path.relativeTo(sourceRoot).invariantSeparatorsPath
                require(ToolPkgArchiveParser.normalizeZipEntryPath(relativePath) != null) {
                    "ToolPkg source path is invalid: $relativePath"
                }
                rejectBlockedSourcePath(relativePath)
                allFiles += path
            }
        }

        val selected =
            if (manifest.schemaVersion >= 2) {
                val includes =
                    manifest.distribution
                        ?.include
                        .orEmpty()
                        .map(String::trim)
                        .filter(String::isNotBlank)
                require(includes.isNotEmpty()) {
                    "schema_version 2 requires distribution.include"
                }
                allFiles.filter { file ->
                    val relativePath = file.relativeTo(sourceRoot).invariantSeparatorsPath
                    includes.any { pattern -> toolPkgGlobMatches(pattern, relativePath) }
                }
            } else {
                val exactEntries =
                    buildSet {
                        add(manifestFile.name)
                        manifest.main.trim().takeIf(String::isNotBlank)?.let(::add)
                        manifest.subpackages
                            .map(ToolPkgManifestSubpackage::entry)
                            .map(String::trim)
                            .filter(String::isNotBlank)
                            .forEach(::add)
                        manifest.resources
                            .filterNot { resource ->
                                ToolPkgArchiveParser.isDirectoryResourceMime(resource.mime)
                            }
                            .map(ToolPkgManifestResource::path)
                            .map(String::trim)
                            .filter(String::isNotBlank)
                            .forEach(::add)
                        manifest.wasmModules
                            .map(ToolPkgManifestWasmModule::path)
                            .map(String::trim)
                            .filter(String::isNotBlank)
                            .forEach(::add)
                    }
                val directoryEntries =
                    buildSet {
                        addAll(
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
                            ),
                        )
                        manifest.resources
                            .filter { resource ->
                                ToolPkgArchiveParser.isDirectoryResourceMime(resource.mime)
                            }
                            .map(ToolPkgManifestResource::path)
                            .map(String::trim)
                            .filter(String::isNotBlank)
                            .forEach(::add)
                    }
                allFiles.filter { file ->
                    val relativePath = file.relativeTo(sourceRoot).invariantSeparatorsPath
                    val firstSegment = relativePath.substringBefore('/')
                    relativePath in exactEntries ||
                        firstSegment in directoryEntries ||
                        isRootDocumentationFile(relativePath)
                }
            }

        val selectedPaths =
            selected.map { file -> file.relativeTo(sourceRoot).invariantSeparatorsPath }.toSet()
        require(manifestFile.name in selectedPaths) { "ToolPkg manifest was not selected" }
        require(manifest.main in selectedPaths) { "ToolPkg main entry was not selected" }
        manifest.subpackages.forEach { subpackage ->
            require(subpackage.entry in selectedPaths) {
                "ToolPkg subpackage entry was not selected: ${subpackage.entry}"
            }
        }
        require(selected.isNotEmpty()) { "ToolPkg build selected no files" }
        return selected.distinctBy(File::getCanonicalPath)
    }

    private fun rejectBlockedSourcePath(relativePath: String) {
        val segments = relativePath.split('/').map { segment -> segment.lowercase(Locale.ROOT) }
        val fileName = segments.last()
        require(
            segments.none { segment ->
                segment in ToolPkgArtifactPolicy.BLOCKED_DIRECTORY_NAMES
            },
        ) {
            "ToolPkg source contains a blocked directory: $relativePath"
        }
        require(
            fileName !in ToolPkgArtifactPolicy.BLOCKED_EXACT_FILE_NAMES &&
                !fileName.startsWith(".env.") &&
                ToolPkgArtifactPolicy.BLOCKED_FILE_SUFFIXES.none(fileName::endsWith),
        ) {
            "ToolPkg source contains a blocked file: $relativePath"
        }
    }

    private fun isRootDocumentationFile(relativePath: String): Boolean {
        if ('/' in relativePath) {
            return false
        }
        val lower = relativePath.lowercase(Locale.ROOT)
        return lower.startsWith("readme") ||
            lower.startsWith("license") ||
            lower.startsWith("changelog")
    }

    private fun safeArtifactFileStem(packageName: String): String {
        return packageName
            .trim()
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
            .take(96)
    }
}
