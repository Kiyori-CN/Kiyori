package com.ai.assistance.operit.ui.features.packages.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageEnvironmentEditsTest {
    @Test fun `only changed keys from the opened form are submitted`() {
        val global = PackageEnvironmentVariableKey.global("key")
        val host = PackageEnvironmentVariableKey.packageScoped("container", "key")
        val unrelated = PackageEnvironmentVariableKey.global("other")
        val result = packageEnvironmentEdits(
            mapOf(global to "old", host to "keep"),
            mapOf(global to "new", host to "keep", unrelated to "external"),
        )
        assertEquals(setOf(global), result.keys)
        assertEquals("old", result.getValue(global).expectedValue)
        assertEquals("new", result.getValue(global).value)
    }

    @Test fun `missing edited key preserves original and blank variants do not create writes`() {
        val first = PackageEnvironmentVariableKey.global("first")
        val second = PackageEnvironmentVariableKey.global("second")
        assertTrue(packageEnvironmentEdits(mapOf(first to "existing", second to ""), mapOf(second to "  ")).isEmpty())
    }
}
