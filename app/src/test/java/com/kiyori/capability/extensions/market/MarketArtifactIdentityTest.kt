package com.kiyori.capability.extensions.market

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketArtifactIdentityTest {
    @Test
    fun `wire IDs retain separator and case normalization`() {
        mapOf(
            "  My.Package__v2  " to "my-package-v2",
            "---HELLO---world---" to "hello-world",
            "name / 123" to "name-123",
            "A\u4e2dB" to "a-b",
            "42" to "42",
        ).forEach { (raw, expected) ->
            assertEquals(raw, expected, normalizeMarketArtifactId(raw))
            assertEquals(expected, normalizeMarketArtifactId(expected))
        }
    }

    @Test
    fun `normalization is independent of device locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("install-id", normalizeMarketArtifactId("INSTALL_ID"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `unusable IDs keep the existing wire placeholder`() {
        listOf("", " \n\t", "...", "\u4e2d\u6587", "Artifact").forEach { raw ->
            assertEquals("artifact", normalizeMarketArtifactId(raw))
            assertTrue(isPlaceholderMarketArtifactId(raw))
        }
        assertFalse(isPlaceholderMarketArtifactId("artifact-tools"))
    }

    @Test
    fun `standalone publishing rejects unstable nonempty IDs`() {
        listOf("...", "\u4e2d\u6587", "-artifact-").forEach { raw ->
            assertTrue(requiresStandaloneArtifactIdUpgrade(raw))
            assertThrows(IllegalArgumentException::class.java) {
                validateStandaloneArtifactRuntimePackageId(raw)
            }
        }
        listOf("", "  ", "artifact", " ARTIFACT ", "tools.package_1").forEach { raw ->
            assertFalse(requiresStandaloneArtifactIdUpgrade(raw))
            validateStandaloneArtifactRuntimePackageId(raw)
        }
    }

    @Test
    fun `install matching retains normalization while rejecting blank operands`() {
        assertTrue(sameArtifactRuntimePackageId(" My_Package ", "my.package"))
        assertTrue(sameArtifactRuntimePackageId(" Name ", "name"))
        assertFalse(sameArtifactRuntimePackageId("package-one", "package-two"))
        assertFalse(sameArtifactRuntimePackageId("", ""))
        assertFalse(sameArtifactRuntimePackageId(" ", "artifact"))
        assertFalse(sameArtifactRuntimePackageId("artifact", " "))
    }
}
