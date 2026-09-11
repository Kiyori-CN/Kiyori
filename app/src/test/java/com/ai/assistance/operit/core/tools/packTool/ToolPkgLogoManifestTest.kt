package com.ai.assistance.operit.core.tools.packTool

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ToolPkgLogoManifestTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `old manifest without logo remains valid`() {
        val result = parseManifest(baseManifest())

        assertNull(result.containerRuntime.logoResource)
    }

    @Test
    fun `logo must be a string resource key`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                parseManifest(baseManifest(logoJson = "{\"key\":\"logo\"}"))
            }

        assertEquals("manifest.logo must be a string resource key", error.message)
    }

    @Test
    fun `logo must reference a declared file image resource`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseManifest(baseManifest(logoJson = "\"missing\""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseManifest(
                baseManifest(
                    logoJson = "\"logo\"",
                    resourcesJson =
                        "[{\"key\":\"logo\",\"path\":\"icons\",\"mime\":\"inode/directory\"}]"
                ),
                mapOf("icons/logo.svg" to "<svg/>")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseManifest(
                baseManifest(
                    logoJson = "\"logo\"",
                    resourcesJson =
                        "[{\"key\":\"logo\",\"path\":\"logo.txt\",\"mime\":\"text/plain\"}]"
                ),
                mapOf("logo.txt" to "not an image")
            )
        }
    }

    @Test
    fun `logo accepts SVG PNG JPEG and WebP resources`() {
        listOf(
            "logo.svg" to "image/svg+xml",
            "logo.png" to "image/png",
            "logo.jpg" to "image/jpeg",
            "logo.webp" to "image/webp"
        ).forEach { (path, mime) ->
            val result =
                parseManifest(
                    baseManifest(
                        logoJson = "\"logo\"",
                        resourcesJson =
                            "[{\"key\":\"logo\",\"path\":\"$path\",\"mime\":\"$mime\"}]"
                    ),
                    mapOf(path to "image bytes")
                )

            assertEquals("logo", result.containerRuntime.logoResource?.key)
            assertEquals(path, result.containerRuntime.logoResource?.path)
            assertEquals(mime, result.containerRuntime.logoResource?.mime)
        }
    }

    private fun parseManifest(
        manifest: String,
        resourceFiles: Map<String, String> = emptyMap()
    ): ToolPkgLoadResult {
        val root = temporaryFolder.newFolder()
        File(root, "manifest.json").writeText(manifest)
        File(root, "index.js").writeText("export {}")
        resourceFiles.forEach { (path, content) ->
            File(root, path).apply {
                parentFile?.mkdirs()
                writeText(content)
            }
        }
        val index = ToolPkgArchiveParser.buildDirectoryEntryIndex(root)
        return ToolPkgArchiveParser.parseToolPkgFromIndexedEntries(
            entryIndex = index,
            readEntryText = { path -> File(root, path).takeIf(File::isFile)?.readText() },
            sourceType = ToolPkgSourceType.EXTERNAL,
            sourcePath = root.absolutePath,
            artifactSha256 = "test-sha256",
            isBuiltIn = false,
            parseJsPackage = { _, _ -> null },
            parseMainRegistration = { _, _, _, _ ->
                ToolPkgMainRegistrationParseResult.Success(ToolPkgMainRegistration())
            },
            reportPackageLoadError = { _, _ -> }
        )
    }

    private fun baseManifest(
        logoJson: String? = null,
        resourcesJson: String = "[]"
    ): String =
        """
        {
          "schema_version": 1,
          "toolpkg_id": "logo-test",
          "version": "1.0.0",
          "main": "index.js",
          "display_name": "Logo Test",
          "description": "Logo contract test",
          ${logoJson?.let { "\"logo\": $it," }.orEmpty()}
          "resources": $resourcesJson
        }
        """.trimIndent()
}
