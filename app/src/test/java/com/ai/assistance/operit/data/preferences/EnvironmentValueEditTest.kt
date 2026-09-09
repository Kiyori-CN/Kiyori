package com.ai.assistance.operit.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EnvironmentValueEditTest {
    @Test fun `batch validates every changed field before caller writes`() {
        val edits = linkedMapOf(
            "address" to EnvironmentValueEdit("original", "new"),
            "credential" to EnvironmentValueEdit("old", "changed"),
        )
        var written = false
        try {
            validateEnvironmentValueEdits(edits) { if (it == "address") "original" else "external" }
            written = true
        } catch (error: EnvironmentEditConflictException) {
            assertEquals("Environment configuration changed", error.message)
        }
        assertFalse(written)
    }

    @Test fun `same target can retry after partial save without overwriting a third value`() {
        validateEnvironmentValueEdits(mapOf("key" to EnvironmentValueEdit("old", "new"))) { "new" }
    }

    @Test fun `blank removal accepts missing and whitespace baseline`() {
        validateEnvironmentValueEdits(mapOf("key" to EnvironmentValueEdit(" ", ""))) { null }
    }

    @Test fun `values with surrounding spaces stay exact`() {
        var rejected = false
        try {
            validateEnvironmentValueEdits(mapOf("key" to EnvironmentValueEdit("value", "new"))) { " value " }
        } catch (_: EnvironmentEditConflictException) { rejected = true }
        org.junit.Assert.assertTrue(rejected)
    }
}
