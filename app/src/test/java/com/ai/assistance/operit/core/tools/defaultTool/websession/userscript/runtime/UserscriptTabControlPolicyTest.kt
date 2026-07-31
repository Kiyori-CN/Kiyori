package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptTabControlPolicyTest {
    @Test
    fun `window close only controls the current session`() {
        assertTrue(
            canControl(
                targetSessionId = "source",
                controlKind = UserscriptTabControlPolicy.CURRENT_SESSION,
                grants = setOf("window.close"),
                currentSessionGrant = "window.close",
            ),
        )
        assertFalse(
            canControl(
                targetSessionId = "other",
                controlKind = UserscriptTabControlPolicy.CURRENT_SESSION,
                grants = setOf("window.close"),
                currentSessionGrant = "window.close",
            ),
        )
    }

    @Test
    fun `open-in-tab handle requires matching session and script owner`() {
        assertTrue(
            canControl(
                targetSessionId = "opened",
                controlKind = UserscriptTabControlPolicy.OPENED_TAB,
                grants = setOf("GM.openInTab"),
                currentSessionGrant = "window.focus",
                ownerSessionId = "source",
                ownerUserscriptId = 9L,
            ),
        )
        assertFalse(
            canControl(
                targetSessionId = "opened",
                controlKind = UserscriptTabControlPolicy.OPENED_TAB,
                grants = setOf("GM.openInTab"),
                currentSessionGrant = "window.focus",
                ownerSessionId = "source",
                ownerUserscriptId = 10L,
            ),
        )
    }

    @Test
    fun `unknown control kind is rejected`() {
        assertFalse(
            canControl(
                targetSessionId = "source",
                controlKind = "unknown",
                grants = setOf("window.focus", "GM.openInTab"),
                currentSessionGrant = "window.focus",
            ),
        )
    }

    private fun canControl(
        targetSessionId: String,
        controlKind: String,
        grants: Set<String>,
        currentSessionGrant: String,
        ownerSessionId: String? = null,
        ownerUserscriptId: Long? = null,
    ): Boolean =
        UserscriptTabControlPolicy.canControl(
            sourceSessionId = "source",
            targetSessionId = targetSessionId,
            userscriptId = 9L,
            grants = grants,
            controlKind = controlKind,
            currentSessionGrant = currentSessionGrant,
            ownerSessionId = ownerSessionId,
            ownerUserscriptId = ownerUserscriptId,
        )
}
