package com.ai.assistance.operit.core.tools.packTool

import org.junit.Assert.*
import org.junit.Test

class ToolPkgApiCompatibilityTest {
    @Test fun `host capability support is independent of Kiyori product version`() {
        assertEquals("1.0.0", ToolPkgApiCompatibility.requireSupported("1.0.0").toString())
        assertEquals("1.0.1", ToolPkgApiCompatibility.requireSupported("1.0.1").toString())
        assertEquals("1.0.0", ToolPkgManifest(toolpkgId = "test").apiVersion)
    }

    @Test fun `explicit invalid and unknown API versions never silently become legacy`() {
        listOf("", " ", "1", "1.0", "1.0.1+6", "1.0.2", "2.0.0", "-1.0.0", "99999999999999.0.0").forEach { value ->
            assertThrows("version=$value", IllegalArgumentException::class.java) {
                ToolPkgApiCompatibility.requireSupported(value)
            }
        }
    }

    @Test fun `versions compare numerically and constraints are inclusive`() {
        val requirement = ToolPkgManifestRequirement("dependency", "used by plugin", "1.2.3", "1.10.0")
        assertNull(requirement.targetVersionFailure("1.2.3"))
        assertNull(requirement.targetVersionFailure("1.10.0"))
        assertNotNull(requirement.targetVersionFailure("1.2.2"))
        assertNotNull(requirement.targetVersionFailure("1.11.0"))
        assertNotNull(requirement.targetVersionFailure(""))
    }
}
