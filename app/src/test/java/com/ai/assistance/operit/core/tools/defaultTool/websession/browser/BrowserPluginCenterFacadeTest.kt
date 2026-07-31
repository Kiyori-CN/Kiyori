package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptIconSet
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptInjectInto
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageMenuCommand
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeStatus
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptSupportState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptUnsafeWindowMode
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPluginCenterFacadeTest {
    @Test
    fun `userscript provider projects installed and current page facts`() {
        val state =
            WebSessionUserscriptUiState(
                supportState = UserscriptSupportState(isSupported = true),
                userScriptsAllowed = true,
                installedScripts =
                    listOf(
                        userscript(id = 1L, enabled = true),
                        userscript(id = 2L, enabled = false),
                    ),
                currentPageStatuses =
                    mapOf(
                        1L to UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.QUEUED),
                        2L to UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.RUNNING),
                        3L to UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.SUCCESS),
                        4L to UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.ERROR),
                        5L to UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.DISABLED),
                        6L to UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.UNSUPPORTED),
                        7L to UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.NOT_MATCHED),
                    ),
            )
        val menuCommands =
            listOf(
                UserscriptPageMenuCommand("first", "First", 1L),
                UserscriptPageMenuCommand("second", "Second", 2L),
            )

        val snapshot = BrowserPluginCenterFacade.project(state, menuCommands)
        val plugin = snapshot.installedPlugins.single()

        assertEquals(BUILT_IN_USERSCRIPT_PLUGIN_ID, plugin.id)
        assertEquals(BrowserPluginInstallationKind.BUILT_IN, plugin.installationKind)
        assertEquals(BrowserPluginAvailability.AVAILABLE, plugin.availability)
        assertTrue(plugin.runtimeAllowed)
        assertTrue(BrowserPluginAction.INSTALL_ITEM in plugin.supportedActions)
        assertEquals(2, plugin.installedItemCount)
        assertEquals(1, plugin.enabledItemCount)
        assertEquals(4, plugin.currentPageItemCount)
        assertEquals(2, plugin.currentPageMenuCommandCount)
        assertFalse(plugin.hasPendingInstall)
        assertEquals(listOf(plugin), snapshot.currentPagePlugins)
    }

    @Test
    fun `userscript provider remains installed but leaves current page when inactive`() {
        val snapshot =
            BrowserPluginCenterFacade.project(
                userscriptState =
                    WebSessionUserscriptUiState(
                        supportState =
                            UserscriptSupportState(
                                isSupported = false,
                                reason = "unsupported",
                            ),
                    ),
                currentPageMenuCommands = emptyList(),
            )

        assertTrue(snapshot.currentPagePlugins.isEmpty())
        assertEquals(
            BrowserPluginAvailability.UNSUPPORTED,
            snapshot.installedPlugins.single().availability,
        )
        assertEquals(
            setOf(BrowserPluginAction.OPEN_MANAGER),
            snapshot.installedPlugins.single().supportedActions,
        )
        assertFalse(snapshot.installedPlugins.single().runtimeAllowed)
    }

    @Test
    fun `available userscript provider exposes runtime permission while it is off`() {
        val snapshot =
            BrowserPluginCenterFacade.project(
                userscriptState =
                    WebSessionUserscriptUiState(
                        supportState = UserscriptSupportState(isSupported = true),
                        userScriptsAllowed = false,
                    ),
                currentPageMenuCommands = emptyList(),
            )
        val plugin = snapshot.installedPlugins.single()

        assertFalse(plugin.runtimeAllowed)
        assertTrue(BrowserPluginAction.SET_PLUGIN_PERMISSION in plugin.supportedActions)
        assertTrue(snapshot.currentPagePlugins.isEmpty())
    }

    @Test
    fun `library sources use unique stable identifiers and secure urls`() {
        val sources = BrowserPluginCenterFacade.librarySources

        assertEquals(sources.size, sources.map { source -> source.id }.distinct().size)
        assertTrue(sources.all { source -> source.url.startsWith("https://") })
        assertEquals(
            listOf("Greasy Fork", "ScriptCat", "OpenUserJS", "Userscript.Zone", "GitHub"),
            sources.map { source -> source.title },
        )
    }

    @Test
    fun `userscript local search covers metadata permissions and sites`() {
        val script =
            userscript(id = 1L, enabled = true).copy(
                namespace = "kiyori.search",
                description = "Translate selected text",
                sourceDisplay = "Greasy Fork",
                grants = listOf("GM.setValue"),
                matches = listOf("https://example.com/*"),
                connects = listOf("api.example.net"),
                tags = listOf("translation"),
            )

        assertTrue(BrowserPluginCenterFacade.matchesUserscriptSearch(script, "translate"))
        assertTrue(BrowserPluginCenterFacade.matchesUserscriptSearch(script, "gm.setvalue"))
        assertTrue(BrowserPluginCenterFacade.matchesUserscriptSearch(script, "api.example.net"))
        assertTrue(BrowserPluginCenterFacade.matchesUserscriptSearch(script, "KIYORI.SEARCH"))
        assertFalse(BrowserPluginCenterFacade.matchesUserscriptSearch(script, "unrelated"))
    }

    private fun userscript(
        id: Long,
        enabled: Boolean,
    ): UserscriptListItem =
        UserscriptListItem(
            id = id,
            name = "Script $id",
            namespace = null,
            version = "1.0.0",
            description = null,
            sourceDisplay = null,
            enabled = enabled,
            unknownGrants = emptyList(),
            blockedReasons = emptyList(),
            executionWorld = null,
            unsafeWindowMode = UserscriptUnsafeWindowMode.NONE,
            grants = emptyList(),
            matches = emptyList(),
            includes = emptyList(),
            excludes = emptyList(),
            excludeMatches = emptyList(),
            connects = emptyList(),
            requires = emptyList(),
            resources = emptyList(),
            homepage = null,
            website = null,
            supportUrl = null,
            icons = UserscriptIconSet(),
            tags = emptyList(),
            injectInto = UserscriptInjectInto.AUTO,
            sandbox = null,
            runIn = null,
            unwrap = false,
            webRequestRules = emptyList(),
            sourceUrl = null,
            updateUrl = null,
            downloadUrl = null,
            installedAt = 1L,
            updatedAt = 1L,
        )
}
