package com.ai.assistance.operit.ui.features.packages.screens

import com.ai.assistance.operit.ui.features.packages.components.PackageTab
import com.ai.assistance.operit.ui.theme.KiyoriSemanticTone
import org.junit.Assert.assertEquals
import org.junit.Test

class PackageManagerVisualPolicyTest {
    @Test
    fun `package manager tabs keep stable feature tones`() {
        assertEquals(
            mapOf(
                PackageTab.PLUGINS to KiyoriSemanticTone.PURPLE,
                PackageTab.PACKAGES to KiyoriSemanticTone.CYAN,
                PackageTab.SKILLS to KiyoriSemanticTone.ORANGE,
                PackageTab.MCP to KiyoriSemanticTone.BLUE,
            ),
            PackageTab.entries.associateWith(::packageManagerTabTone),
        )
    }
}
