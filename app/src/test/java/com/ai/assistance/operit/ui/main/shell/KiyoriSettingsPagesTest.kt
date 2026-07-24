package com.ai.assistance.operit.ui.main.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class KiyoriSettingsPagesTest {
    @Test
    fun `settings home uses two columns only at expanded width`() {
        assertEquals(
            KiyoriSettingsHomeLayout.COMPACT,
            resolveKiyoriSettingsHomeLayout(839f),
        )
        assertEquals(
            KiyoriSettingsHomeLayout.EXPANDED,
            resolveKiyoriSettingsHomeLayout(840f),
        )
    }
}
