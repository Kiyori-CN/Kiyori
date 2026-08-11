package com.kiyori.platform.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ToolPkgStorageContractTest {
    @Test
    fun `package key is stable and collision resistant after sanitization`() {
        assertEquals(
            ToolPkgStorageService.packageKey("com.example.demo"),
            ToolPkgStorageService.packageKey(" COM.EXAMPLE.DEMO "),
        )
        assertNotEquals(
            ToolPkgStorageService.packageKey("com.example/a"),
            ToolPkgStorageService.packageKey("com.example_a"),
        )
    }

    @Test
    fun `relative path normalization rejects escape and platform paths`() {
        listOf(
            "",
            "/absolute.json",
            "../state.json",
            "state/../state.json",
            "state\\value.json",
            "C:/state.json",
            "content://authority/state.json",
            "state//value.json",
            "state/\u0000value.json",
        ).forEach { path ->
            assertThrows(path, IllegalArgumentException::class.java) {
                ToolPkgStorageService.normalizeRelativePath(path)
            }
        }
        assertEquals(
            "state/sidebar_analysis_state.json",
            ToolPkgStorageService.normalizeRelativePath(
                "state/sidebar_analysis_state.json",
            ),
        )
    }
}
