package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptWebRequestEngineOwnershipTest {
    @Test
    fun `registration can only be removed by its owning userscript`() {
        val engine = UserscriptWebRequestEngine()
        val registrationId =
            engine.register(
                scriptId = 12L,
                sessionId = "session",
                rulesJson = "",
                source = "runtime",
            )

        assertFalse(engine.unregister(registrationId, scriptId = 13L))
        assertTrue(engine.unregister(registrationId, scriptId = 12L))
        assertFalse(engine.unregister(registrationId, scriptId = 12L))
    }
}
