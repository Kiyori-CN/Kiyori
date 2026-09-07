package com.ai.assistance.operit.core.tools.defaultTool.websession.extension

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

internal fun extensionFixture(version: String = "1.0.0", code: String = "kiyori.log('ok');") = BrowserExtensionPackage(
    BrowserExtensionManifest(3, "Marker", version, kiyori = BrowserExtensionIdentity(1, "com.example.marker"),
        content_scripts = listOf(BrowserExtensionContent(listOf("https://*.example.com/*"), js = listOf("main.js")))),
    mapOf("main.js" to code),
)

class BrowserExtensionPackageTest {
    @Test fun zipRoundTripRetainsIdentityAndCode() {
        val input = extensionFixture()
        val output = ByteArrayOutputStream()
        input.writeZip(output)
        assertEquals(input, BrowserExtensionPackage.readZip(ByteArrayInputStream(output.toByteArray())))
    }

    @Test fun unsupportedFieldsAndPermissionsCannotSilentlyInstall() {
        val raw = BrowserExtensionPackage.codec.encodeToString(extensionFixture())
        for (field in listOf("\"permissions\":[\"cookies\"]", "\"background\":{\"service_worker\":\"x.js\"}")) {
            assertThrows(Exception::class.java) {
                BrowserExtensionPackage.codec.decodeFromString<BrowserExtensionPackage>(raw.replace("\"manifest\":{", "\"manifest\":{$field,"))
            }
        }
    }

    @Test fun rejectsUnsafePackagePathsAndDuplicateCaseAliases() {
        for (path in listOf("../x.js", "/x.js", "C:/x.js", "a\\x.js", "a//x.js", "a/./x.js", "")) {
            assertThrows(IllegalArgumentException::class.java) { BrowserExtensionPackage.validatePath(path) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            extensionFixture().copy(files = mapOf("main.js" to "1", "MAIN.js" to "2")).validate()
        }
    }

    @Test fun zipBombAndMissingManifestAreRejectedBeforePublishing() {
        fun zip(name: String, bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { it.putNextEntry(ZipEntry(name)); it.write(bytes); it.closeEntry() }
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            BrowserExtensionPackage.readZip(ByteArrayInputStream(zip("main.js", ByteArray(BrowserExtensionPackage.MAX_BYTES + 1))))
        }
        assertThrows(IllegalStateException::class.java) {
            BrowserExtensionPackage.readZip(ByteArrayInputStream(zip("main.js", "1".toByteArray())))
        }
    }

    @Test fun rejectsUnsupportedWorldFramesTimingMissingFilesAndSyntax() {
        val base = extensionFixture()
        val script = base.manifest.content_scripts.single()
        for (entry in listOf(script.copy(world = "MAIN"), script.copy(all_frames = true),
            script.copy(run_at = "unknown"), script.copy(js = listOf("missing.js")), script.copy(matches = emptyList()))) {
            assertThrows(IllegalArgumentException::class.java) { base.copy(manifest = base.manifest.copy(content_scripts = listOf(entry))).validate() }
        }
        assertThrows(IllegalArgumentException::class.java) { base.validate { "Unexpected token" } }
    }

    @Test fun runtimeMatchingRequiresRealHostBoundaryAndHonorsExcludes() {
        val pattern = "https://*.example.com/*"
        assertTrue(BrowserExtensionBootstrap.matches(pattern, "https://example.com/a?b=c#d"))
        assertTrue(BrowserExtensionBootstrap.matches(pattern, "https://x.example.com:443/a"))
        assertFalse(BrowserExtensionBootstrap.matches(pattern, "https://example.com.evil.test/a"))
        assertFalse(BrowserExtensionBootstrap.matches(pattern, "http://example.com/a"))
        assertTrue(BrowserExtensionBootstrap.matches("https://*:8443/*", "https://example.com:8443/a"))
        assertFalse(BrowserExtensionBootstrap.matches("https://*:8443/*", "https://example.com/path:8443/a"))
        assertFalse(BrowserExtensionContent(listOf(pattern), exclude_matches = listOf("https://*.example.com/private/*"))
            .matchesUrl("https://example.com/private/a"))
        assertFalse(BrowserExtensionBootstrap.patternRegex("https://example.com/***").contains(".*.*"))
    }

    @Test fun rejectsOversizedMatchRulesAndInvalidExplicitPorts() {
        val base = extensionFixture()
        val script = base.manifest.content_scripts.single()
        for (pattern in listOf(
            "https://example.com/" + "a".repeat(BrowserExtensionPackage.MAX_MATCH_PATTERN_CHARACTERS),
            "https://example.com:65536/*",
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                base.copy(manifest = base.manifest.copy(content_scripts = listOf(script.copy(matches = listOf(pattern))))).validate()
            }
        }
    }

    @Test fun versionsUseNumericSegmentsAndEquivalentTrailingZeros() {
        assertTrue(BrowserExtensionPackage.compareVersions("1.10", "1.9") > 0)
        assertEquals(0, BrowserExtensionPackage.compareVersions("1.0", "1.0.0"))
        assertThrows(IllegalArgumentException::class.java) { extensionFixture("1.beta").validate() }
    }
}
