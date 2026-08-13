package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import android.os.SystemClock
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

internal object ToolPkgLoader {
    fun loadToolPkgFromExternalFile(
        file: File,
        jsEngine: JsEngine,
        parseJsPackage: (String, (String, String) -> Unit) -> ToolPackage?,
        reportPackageLoadError: (key: String, error: String) -> Unit
    ): ToolPkgLoadResult {
        val scanReport = ToolPkgArtifactScanner.scan(file)
        scanReport.requireAccepted()
        ZipFile(file).use { archive ->
            var registrationObservation: ToolPkgRegistrationExecutionObservation? = null
            val entryIndex = ToolPkgArchiveParser.buildZipEntryIndex(archive)
            val readEntryText =
                { path: String ->
                    ToolPkgArchiveParser.readZipEntryText(
                        archive = archive,
                        entryIndex = entryIndex,
                        rawPath = path
                    )
                }
            return jsEngine
                .withTemporaryToolPkgTextResourceResolver(
                    resolver = { _, resourcePath -> readEntryText(resourcePath) }
                ) {
                    ToolPkgArchiveParser.parseToolPkgFromIndexedEntries(
                        entryIndex = entryIndex,
                        readEntryText = readEntryText,
                        sourceType = ToolPkgSourceType.EXTERNAL,
                        sourcePath = file.absolutePath,
                        artifactSha256 = scanReport.artifactSha256,
                        isBuiltIn = false,
                        parseJsPackage = parseJsPackage,
                        parseMainRegistration = { mainScriptText, toolPkgId, mainScriptPath ->
                            observeMainRegistration {
                                parseMainRegistration(
                                    mainScriptText,
                                    toolPkgId,
                                    mainScriptPath,
                                    jsEngine,
                                )
                            }.also { observed ->
                                registrationObservation = observed.observation
                            }.result
                        },
                        reportPackageLoadError = reportPackageLoadError
                    )
                }
                .copy(registrationObservation = registrationObservation)
        }
    }

    fun loadToolPkgFromAsset(
        context: Context,
        assetPath: String,
        jsEngine: JsEngine,
        parseJsPackage: (String, (String, String) -> Unit) -> ToolPackage?,
        prepareAssetCache: (ToolPkgManifestPreview) -> File,
        reportPackageLoadError: (key: String, error: String) -> Unit
    ): ToolPkgLoadResult {
        val artifactSha256 =
            context.assets.open(assetPath).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) {
                        break
                    }
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            }
        val manifestPreview =
            ToolPkgArchiveParser.readToolPkgManifestPreview(
                inputStreamFactory = { context.assets.open(assetPath) }
            ) ?: throw IllegalArgumentException("manifest.hjson or manifest.json not found")
        val extractedDir = prepareAssetCache(manifestPreview)
        var registrationObservation: ToolPkgRegistrationExecutionObservation? = null
        val entryIndex = ToolPkgArchiveParser.buildDirectoryEntryIndex(extractedDir)
        val readEntryText =
            { path: String ->
                ToolPkgArchiveParser.readDirectoryEntryText(
                    rootDir = extractedDir,
                    entryIndex = entryIndex,
                    rawPath = path
                )
            }
        return jsEngine
            .withTemporaryToolPkgTextResourceResolver(
                resolver = { _, resourcePath -> readEntryText(resourcePath) }
            ) {
                ToolPkgArchiveParser.parseToolPkgFromIndexedEntries(
                    entryIndex = entryIndex,
                    readEntryText = readEntryText,
                    sourceType = ToolPkgSourceType.ASSET,
                    sourcePath = assetPath,
                    artifactSha256 = artifactSha256,
                    isBuiltIn = true,
                    parseJsPackage = parseJsPackage,
                    parseMainRegistration = { mainScriptText, toolPkgId, mainScriptPath ->
                        observeMainRegistration {
                            parseMainRegistration(
                                mainScriptText,
                                toolPkgId,
                                mainScriptPath,
                                jsEngine,
                            )
                        }.also { observed ->
                            registrationObservation = observed.observation
                        }.result
                    },
                    reportPackageLoadError = reportPackageLoadError
                )
            }
            .copy(registrationObservation = registrationObservation)
    }

    private data class ObservedMainRegistration(
        val result: ToolPkgMainRegistrationParseResult,
        val observation: ToolPkgRegistrationExecutionObservation,
    )

    private inline fun observeMainRegistration(
        block: () -> ToolPkgMainRegistrationParseResult,
    ): ObservedMainRegistration {
        val startMs = SystemClock.elapsedRealtime()
        val threadName = Thread.currentThread().name
        val result = block()
        return ObservedMainRegistration(
            result = result,
            observation =
                ToolPkgRegistrationExecutionObservation(
                    elapsedMs = SystemClock.elapsedRealtime() - startMs,
                    threadName = threadName,
                ),
        )
    }

    private fun parseMainRegistration(
        mainScriptText: String,
        toolPkgId: String,
        mainScriptPath: String,
        jsEngine: JsEngine
    ): ToolPkgMainRegistrationParseResult {
        return ToolPkgMainRegistrationScriptParser.parse(
            script = mainScriptText,
            toolPkgId = toolPkgId,
            mainScriptPath = mainScriptPath,
            jsEngine = jsEngine
        )
    }
}
