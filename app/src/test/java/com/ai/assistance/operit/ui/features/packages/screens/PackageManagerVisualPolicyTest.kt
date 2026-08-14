package com.ai.assistance.operit.ui.features.packages.screens

import com.ai.assistance.operit.ui.features.packages.components.PackageTab
import com.kiyori.design.theme.KiyoriSemanticTone
import org.junit.Assert.assertEquals
import org.junit.Test

class PackageManagerVisualPolicyTest {
    @Test
    fun `ai extensions tabs keep script first`() {
        assertEquals(
            listOf(
                PackageTab.PACKAGES,
                PackageTab.PLUGINS,
                PackageTab.SKILLS,
                PackageTab.MCP,
            ),
            aiExtensionsTabOrder(),
        )
    }

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
    fun `script add menu keeps create before import`() {
        assertEquals(
            listOf(
                ScriptAddMenuAction.CREATE_SCRIPT,
                ScriptAddMenuAction.IMPORT_SCRIPT_PACKAGE,
            ),
            scriptAddMenuActions(),
        )
    }

    @Test
    fun `skill top bar keeps market add and refresh with conditional error first`() {
        val normalActions =
            listOf(
                PackageManagerTopBarAction.MARKET,
                PackageManagerTopBarAction.ADD,
                PackageManagerTopBarAction.REFRESH,
            )
        val errorActions =
            listOf(PackageManagerTopBarAction.ERROR) + normalActions

        assertEquals(normalActions, packageManagerTopBarActions(PackageTab.SKILLS))
        assertEquals(
            errorActions,
            packageManagerTopBarActions(
                tab = PackageTab.SKILLS,
                hasSkillLoadErrors = true,
            ),
        )
    }

    @Test
    fun `mcp top bar keeps start market add and refresh order`() {
        assertEquals(
            listOf(
                PackageManagerTopBarAction.START,
                PackageManagerTopBarAction.MARKET,
                PackageManagerTopBarAction.ADD,
                PackageManagerTopBarAction.REFRESH,
            ),
            packageManagerTopBarActions(PackageTab.MCP),
        )
    }

    @Test
    fun `skill import indicator covers every import tab`() {
        assertEquals(0..2, skillImportTabIndices())
    }

    @Test
    fun `mcp import indicator covers every import tab`() {
        assertEquals(0..3, mcpImportTabIndices())
    }
}
