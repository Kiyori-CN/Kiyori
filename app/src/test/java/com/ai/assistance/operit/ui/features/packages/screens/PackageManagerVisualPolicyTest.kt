package com.ai.assistance.operit.ui.features.packages.screens

import com.ai.assistance.operit.ui.features.packages.components.PackageTab
import com.kiyori.design.theme.KiyoriSemanticTone
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

    @Test
    fun `plugin and script tabs publish the requested top bar action order`() {
        val expected =
            listOf(
                PackageManagerTopBarAction.ENVIRONMENT,
                PackageManagerTopBarAction.MARKET,
                PackageManagerTopBarAction.ADD,
                PackageManagerTopBarAction.REFRESH,
            )

        assertEquals(expected, packageManagerTopBarActions(PackageTab.PLUGINS))
        assertEquals(expected, packageManagerTopBarActions(PackageTab.PACKAGES))
    }

    @Test
    fun `skill and mcp tabs keep their own page actions`() {
        assertEquals(emptyList<PackageManagerTopBarAction>(), packageManagerTopBarActions(PackageTab.SKILLS))
        assertEquals(emptyList<PackageManagerTopBarAction>(), packageManagerTopBarActions(PackageTab.MCP))
    }
}
