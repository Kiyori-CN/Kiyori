package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import com.kiyori.platform.storage.KiyoriPaths
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ToolPkgArtifactStoreTest {
    @Test
    fun `content addressed store activates one artifact and removes only unreferenced content`() {
        val root = Files.createTempDirectory("toolpkg-artifact-store").toFile()
        try {
            val filesDir = File(root, "files").apply { mkdirs() }
            val context = mock<Context> { on { this.filesDir } doReturn filesDir }
            val store = ToolPkgArtifactStore(context)
            val firstSource =
                createArchive(
                    root = root,
                    fileName = "first.toolpkg",
                    version = "1.0.0",
                    mainSource = "exports.main = () => 1;",
                )
            val secondSource =
                createArchive(
                    root = root,
                    fileName = "second.toolpkg",
                    version = "2.0.0",
                    mainSource = "exports.main = () => 2;",
                )
            val firstReport = ToolPkgArtifactScanner.scan(firstSource)
            val secondReport = ToolPkgArtifactScanner.scan(secondSource)

            val firstStored = store.storeArtifact(firstSource, firstReport)
            val secondStored = store.storeArtifact(secondSource, secondReport)
            store.activate(
                ToolPkgActiveArtifactRecord(
                    packageId = PACKAGE_ID,
                    artifactSha256 = firstReport.artifactSha256,
                    version = "1.0.0",
                    activatedAt = 10L,
                ),
            )

            assertEquals(firstReport.artifactSha256, store.readActive(PACKAGE_ID)?.artifactSha256)
            assertTrue(firstStored.isFile)
            assertTrue(secondStored.isFile)
            assertTrue(
                File(
                    KiyoriPaths.toolPkgAuditDir(context),
                    "${firstReport.artifactSha256}.json",
                ).isFile,
            )

            store.cleanupUnreferencedArtifacts()

            assertTrue(firstStored.isFile)
            assertFalse(secondStored.exists())
            assertEquals(listOf(firstStored.canonicalFile), store.activeArtifactFiles().map(File::getCanonicalFile))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createArchive(
        root: File,
        fileName: String,
        version: String,
        mainSource: String,
    ): File {
        val archive = File(root, fileName)
        val manifest =
            """
            {
              "schema_version": 1,
              "toolpkg_id": "$PACKAGE_ID",
              "version": "$version",
              "main": "main.js"
            }
            """.trimIndent()
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            linkedMapOf(
                "manifest.json" to manifest,
                "main.js" to mainSource,
            ).forEach { (path, text) ->
                zip.putNextEntry(ZipEntry(path).apply { time = 0L })
                zip.write(text.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
        }
        return archive
    }

    companion object {
        private const val PACKAGE_ID = "com.example.store"
    }
}
