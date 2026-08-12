package com.ai.assistance.operit.core.tools.packTool

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPkgArtifactScannerTest {
    @Test
    fun `double-star directory glob also matches zero directory levels`() {
        assertTrue(toolPkgGlobMatches("packages/**/*.js", "packages/main.js"))
        assertTrue(toolPkgGlobMatches("packages/**/*.js", "packages/nested/main.js"))
        assertFalse(toolPkgGlobMatches("packages/**/*.js", "packages/main.ts"))
    }

    @Test
    fun `scanner accepts a declared schema two artifact`() {
        withArchive(
            linkedMapOf(
                "manifest.json" to schemaTwoManifest(),
                "main.js" to "exports.main = () => true;",
                "packages/helper.js" to "exports.helper = () => true;",
            ),
        ) { archive ->
            val report = ToolPkgArtifactScanner.scan(archive)
            assertTrue(report.findings.toString(), report.accepted)
            assertTrue(report.artifactSha256.matches(Regex("""[0-9a-f]{64}""")))
            assertTrue(report.treeDigest.matches(Regex("""[0-9a-f]{64}""")))
        }
    }

    @Test
    fun `scanner rejects blocked history and old Operit paths`() {
        withArchive(
            linkedMapOf(
                "manifest.json" to schemaOneManifest(),
                "main.js" to
                    """
                    const dataDir = "/sdcard/Download/Operit/memory_system_data";
                    exports.main = () => dataDir;
                    """.trimIndent(),
                ".backup/main.js" to "exports.old = true;",
            ),
        ) { archive ->
            val codes = ToolPkgArtifactScanner.scan(archive).findings.mapTo(mutableSetOf()) { it.code }
            assertTrue("TPKG-BLOCKED-DIRECTORY" in codes)
            assertTrue("TPKG-LEGACY-ABSOLUTE-PATH" in codes)
        }
    }

    @Test
    fun `scanner rejects absolute and case-conflicting entries`() {
        withArchive(
            linkedMapOf(
                "manifest.json" to schemaOneManifest(),
                "main.js" to "exports.main = true;",
                "MAIN.JS" to "exports.other = true;",
                "/absolute.js" to "exports.absolute = true;",
            ),
        ) { archive ->
            val codes = ToolPkgArtifactScanner.scan(archive).findings.mapTo(mutableSetOf()) { it.code }
            assertTrue("TPKG-CASE-CONFLICT" in codes)
            assertTrue("TPKG-ENTRY-PATH" in codes)
        }
    }

    @Test
    fun `scanner accepts package-scoped host-only environment declarations`() {
        withArchive(
            linkedMapOf(
                "manifest.json" to
                    """
                    {
                      "schema_version": 1,
                      "toolpkg_id": "com.example.host-environment",
                      "version": "1.0.0",
                      "main": "main.js",
                      "environment": [
                        {
                          "name": "SERVICE_MODE",
                          "required": true,
                          "scope": "package",
                          "consumer": "host_service",
                          "input_type": "enum",
                          "allowed_values": ["PACKAGE_ENV", "MODEL_CONFIG"],
                          "default_value": "PACKAGE_ENV"
                        },
                        {
                          "name": "SERVICE_API_KEY",
                          "required": false,
                          "scope": "package",
                          "sensitive": true,
                          "consumer": "host_service",
                          "input_type": "password"
                        }
                      ]
                    }
                    """.trimIndent(),
                "main.js" to "exports.main = true;",
            ),
        ) { archive ->
            val report = ToolPkgArtifactScanner.scan(archive)
            assertTrue(report.findings.toString(), report.accepted)
        }
    }

    @Test
    fun `scanner rejects ToolPkg environment visible to JavaScript`() {
        withArchive(
            linkedMapOf(
                "manifest.json" to
                    """
                    {
                      "schema_version": 1,
                      "toolpkg_id": "com.example.invalid-environment",
                      "version": "1.0.0",
                      "main": "main.js",
                      "environment": [
                        {
                          "name": "SERVICE_API_KEY",
                          "scope": "global",
                          "consumer": "javascript"
                        }
                      ]
                    }
                    """.trimIndent(),
                "main.js" to "exports.main = true;",
            ),
        ) { archive ->
            val codes = ToolPkgArtifactScanner.scan(archive).findings.mapTo(mutableSetOf()) { it.code }
            assertTrue("TPKG-MANIFEST-PARSE" in codes)
        }
    }

    @Test
    fun `scanner rejects unknown ToolPkg environment input type`() {
        withArchive(
            linkedMapOf(
                "manifest.json" to
                    """
                    {
                      "schema_version": 1,
                      "toolpkg_id": "com.example.invalid-input",
                      "version": "1.0.0",
                      "main": "main.js",
                      "environment": [
                        {
                          "name": "SERVICE_VALUE",
                          "scope": "package",
                          "consumer": "host_service",
                          "input_type": "mystery"
                        }
                      ]
                    }
                    """.trimIndent(),
                "main.js" to "exports.main = true;",
            ),
        ) { archive ->
            val codes = ToolPkgArtifactScanner.scan(archive).findings.mapTo(mutableSetOf()) { it.code }
            assertTrue("TPKG-MANIFEST-PARSE" in codes)
        }
    }

    private fun withArchive(
        entries: LinkedHashMap<String, String>,
        block: (File) -> Unit,
    ) {
        val root = Files.createTempDirectory("toolpkg-scanner-test").toFile()
        try {
            val archive = File(root, "fixture.toolpkg")
            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                entries.forEach { (path, text) ->
                    val entry = ZipEntry(path).apply { time = 0L }
                    zip.putNextEntry(entry)
                    zip.write(text.toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }
            }
            block(archive)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun schemaOneManifest(): String {
        return """
            {
              "schema_version": 1,
              "toolpkg_id": "com.example.scanner",
              "version": "1.0.0",
              "main": "main.js"
            }
        """.trimIndent()
    }

    private fun schemaTwoManifest(): String {
        return """
            {
              "schema_version": 2,
              "toolpkg_id": "com.example.scanner",
              "version": "1.0.0",
              "main": "main.js",
              "distribution": {
                "include": [
                  "manifest.json",
                  "main.js",
                  "packages/**/*.js"
                ]
              }
            }
        """.trimIndent()
    }
}
