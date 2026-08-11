package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ToolPkgArtifactBuilderTest {
    @Test
    fun `builder is deterministic and excludes undeclared schema two files`() {
        val root = Files.createTempDirectory("toolpkg-builder-test").toFile()
        try {
            val source = File(root, "source").apply { mkdirs() }
            val cache = File(root, "cache").apply { mkdirs() }
            File(source, "manifest.json").writeText(schemaTwoManifest())
            File(source, "main.js").writeText("exports.main = () => true;")
            File(source, "packages").mkdirs()
            File(source, "packages/helper.js").writeText("exports.helper = () => true;")
            File(source, "notes.txt").writeText("not distributed")
            val context = mock<Context> { on { cacheDir } doReturn cache }
            val builder = ToolPkgArtifactBuilder(context)

            val first = builder.build(source)
            val second = builder.build(source)

            assertEquals(first.report.artifactSha256, second.report.artifactSha256)
            assertTrue(first.report.accepted)
            assertTrue(second.report.accepted)
            assertFalse(first.report.findings.any { it.entry == "notes.txt" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `builder rejects blocked source content before packaging`() {
        val root = Files.createTempDirectory("toolpkg-builder-blocked").toFile()
        try {
            val source = File(root, "source").apply { mkdirs() }
            val cache = File(root, "cache").apply { mkdirs() }
            File(source, "manifest.json").writeText(schemaTwoManifest())
            File(source, "main.js").writeText("exports.main = () => true;")
            File(source, ".env").writeText("TOKEN=secret")
            val context = mock<Context> { on { cacheDir } doReturn cache }

            assertThrows(IllegalArgumentException::class.java) {
                ToolPkgArtifactBuilder(context).build(source)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun schemaTwoManifest(): String {
        return """
            {
              "schema_version": 2,
              "toolpkg_id": "com.example.builder",
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
