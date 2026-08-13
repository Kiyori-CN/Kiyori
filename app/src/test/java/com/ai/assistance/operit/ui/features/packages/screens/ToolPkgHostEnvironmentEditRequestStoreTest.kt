package com.ai.assistance.operit.ui.features.packages.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolPkgHostEnvironmentEditRequestStoreTest {
    @Test
    fun requestIsTrimmedAndConsumedExactlyOnce() {
        ToolPkgHostEnvironmentEditRequestStore.consume()

        ToolPkgHostEnvironmentEditRequestStore.request(
            " com.kiyori.openai_web_search "
        )

        assertEquals(
            "com.kiyori.openai_web_search",
            ToolPkgHostEnvironmentEditRequestStore.consume(),
        )
        assertNull(ToolPkgHostEnvironmentEditRequestStore.consume())
    }
}
