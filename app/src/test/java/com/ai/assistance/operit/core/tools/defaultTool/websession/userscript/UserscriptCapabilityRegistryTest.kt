package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptCapabilityRegistryTest {
    @Test
    fun `storage host messages map to their declared grants`() {
        assertEquals(
            setOf("GM.setValue"),
            UserscriptCapabilityRegistry.grantsForHostMessage("storage_set"),
        )
        assertEquals(
            setOf("GM.deleteValues"),
            UserscriptCapabilityRegistry.grantsForHostMessage("storage_delete_many"),
        )
    }

    @Test
    fun `tab controls retain all valid grant owners`() {
        assertEquals(
            setOf("GM.openInTab", "window.close"),
            UserscriptCapabilityRegistry.grantsForHostMessage("gm_close_tab"),
        )
        assertEquals(
            setOf("GM.openInTab", "window.focus"),
            UserscriptCapabilityRegistry.grantsForHostMessage("gm_focus_tab"),
        )
    }

    @Test
    fun `navigation and unknown messages are not host capabilities`() {
        assertTrue(UserscriptCapabilityRegistry.grantsForHostMessage("url_change").isEmpty())
        assertTrue(UserscriptCapabilityRegistry.grantsForHostMessage("unknown_operation").isEmpty())
    }
}
