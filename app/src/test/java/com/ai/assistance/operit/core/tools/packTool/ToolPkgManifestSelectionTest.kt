package com.ai.assistance.operit.core.tools.packTool

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ToolPkgManifestSelectionTest {
    @Test fun backupHjsonCannotShadowTheRealWrappedJsonManifest() {
        val names = listOf("package/.backup/manifest.hjson", "package/manifest.json")
        assertEquals("package/manifest.json", ToolPkgArchiveParser.findManifestEntry(names))
        assertEquals("right", preview(names[0] to manifest("backup"), names[1] to manifest("right"))?.manifest?.toolpkgId)
    }
    @Test fun previewAndLoaderAgreeAcrossArchiveOrdersAndRootFormats() {
        val names = listOf("package/deep/manifest.json", "package/manifest.json", "z/manifest.json", "a/manifest.json")
        for (order in listOf(names, names.reversed())) {
            val preview = preview(*order.map { it to manifest(it) }.toTypedArray())
            assertEquals("a/manifest.json", preview?.entryName)
            assertEquals(ToolPkgArchiveParser.findManifestEntry(order), preview?.entryName)
        }
        assertEquals("manifest.json", preview("nested/manifest.hjson" to manifest("nested"), "manifest.json" to manifest("root"))?.entryName)
        assertEquals("manifest.hjson", preview("manifest.json" to manifest("json"), "manifest.hjson" to manifest("hjson"))?.entryName)
    }

    @Test fun wrappedPackagePrefersShallowManifestAndKeepsReadingAfterIt() {
        val result = preview("package/deep/manifest.json" to manifest("wrong"), "package/manifest.json" to manifest("right"), "package/main.js" to "// end")
        assertEquals("right", result?.manifest?.toolpkgId)
        assertNull(preview("main.js" to "// no manifest"))
    }

    @Test fun invalidAndCaseConflictingEntriesAfterManifestAreRejected() {
        for (bad in listOf("../escape.js", "/absolute.js", "MANIFEST.JSON", "./manifest.json")) {
            assertThrows(IllegalArgumentException::class.java) {
                preview("manifest.json" to manifest("valid"), bad to manifest("invalid"))
            }
        }
    }

    @Test fun oversizedManifestCannotBeReadEvenWhenItIsNotSelected() {
        assertThrows(IllegalArgumentException::class.java) {
            preview("manifest.json" to manifest("valid"), "nested/manifest.json" to " ".repeat(ToolPkgArtifactPolicy.MAX_MANIFEST_BYTES.toInt() + 1))
        }
    }

    @Test fun selectedManifestParseFailureDoesNotFallBackToAnotherPackage() {
        assertThrows(Exception::class.java) {
            preview("nested/manifest.json" to manifest("valid"), "manifest.json" to "{broken")
        }
    }

    private fun manifest(id: String) = """{"toolpkg_id":"$id","main":"main.js"}"""

    private fun preview(vararg entries: Pair<String, String>): ToolPkgManifestPreview? {
        val bytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                for ((name, content) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
        return ToolPkgArchiveParser.readToolPkgManifestPreview { ByteArrayInputStream(bytes) }
    }
}
