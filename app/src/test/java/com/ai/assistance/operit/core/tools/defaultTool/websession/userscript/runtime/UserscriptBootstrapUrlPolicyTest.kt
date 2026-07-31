package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserscriptBootstrapUrlPolicyTest {
    @Test
    fun `isolated world trusts its own current document URL`() {
        assertEquals(
            "https://example.com/current?query=1",
            UserscriptBootstrapUrlPolicy.resolve(
                runtimeHref = "https://example.com/current?query=1",
                sourceOrigin = "",
                isolatedWorld = true,
            ),
        )
    }

    @Test
    fun `page world accepts URL from the listener source origin`() {
        assertEquals(
            "https://example.com/current",
            UserscriptBootstrapUrlPolicy.resolve(
                runtimeHref = "https://example.com/current",
                sourceOrigin = "https://example.com",
                isolatedWorld = false,
            ),
        )
    }

    @Test
    fun `page world rejects a spoofed cross-origin URL`() {
        assertNull(
            UserscriptBootstrapUrlPolicy.resolve(
                runtimeHref = "https://private.example/script-path",
                sourceOrigin = "https://attacker.example",
                isolatedWorld = false,
            ),
        )
    }
}
