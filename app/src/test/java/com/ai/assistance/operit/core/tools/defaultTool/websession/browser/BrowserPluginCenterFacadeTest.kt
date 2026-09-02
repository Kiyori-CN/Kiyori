package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptIconSet
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptInjectInto
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageMenuCommand
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeStatus
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptRunAt
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
        val plugin = snapshot.installedPlugins.userscriptPlugin()
        val currentPageProvider = snapshot.currentPageProviders.single()

        assertEquals(BUILT_IN_USERSCRIPT_PLUGIN_ID, plugin.id)
        assertEquals(BrowserPluginInstallationKind.BUILT_IN, plugin.installationKind)
        assertEquals(BrowserPluginAvailability.AVAILABLE, plugin.availability)
        assertTrue(plugin.runtimeAllowed)
        assertTrue(BrowserPluginAction.INSTALL_ITEM in plugin.supportedActions)
        assertEquals(2, plugin.installedItemCount)
        assertEquals(1, plugin.enabledItemCount)
        assertEquals(2, plugin.currentPageItemCount)
        assertEquals(2, plugin.currentPageMenuCommandCount)
        assertFalse(plugin.hasPendingInstall)
        assertEquals(plugin, currentPageProvider.summary)
        assertEquals(
            listOf("userscript:2", "userscript:1"),
            currentPageProvider.currentPageEntries.map { entry -> entry.id },
        )
        assertEquals(
            listOf("First"),
            currentPageProvider.currentPageEntries
                .single { entry -> entry.sourceItemId == 1L }
                .commands
                .map { command -> command.title },
        )
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

        assertTrue(snapshot.currentPageProviders.isEmpty())
        assertEquals(
            BrowserPluginAvailability.UNSUPPORTED,
            snapshot.installedPlugins.userscriptPlugin().availability,
        )
        assertEquals(
            setOf(BrowserPluginAction.OPEN_MANAGER),
            snapshot.installedPlugins.userscriptPlugin().supportedActions,
        )
        assertFalse(snapshot.installedPlugins.userscriptPlugin().runtimeAllowed)
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
        val plugin = snapshot.installedPlugins.userscriptPlugin()

        assertFalse(plugin.runtimeAllowed)
        assertTrue(BrowserPluginAction.SET_PLUGIN_PERMISSION in plugin.supportedActions)
        assertTrue(snapshot.currentPageProviders.isEmpty())
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

    @Test
    fun `current page projection groups commands by userscript and preserves command order`() {
        val state =
            WebSessionUserscriptUiState(
                supportState = UserscriptSupportState(isSupported = true),
                userScriptsAllowed = true,
                installedScripts =
                    listOf(
                        userscript(id = 1L, enabled = true).copy(name = "东方永夜机"),
                        userscript(id = 2L, enabled = true).copy(name = "网页增强"),
                    ),
                currentPageStatuses =
                    mapOf(
                        1L to UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.SUCCESS),
                        2L to UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.SUCCESS),
                    ),
            )
        val snapshot =
            BrowserPluginCenterFacade.project(
                state,
                listOf(
                    UserscriptPageMenuCommand("1:first", "第一项", 1L),
                    UserscriptPageMenuCommand("2:only", "网页增强", 2L),
                    UserscriptPageMenuCommand("1:second", "第二项", 1L),
                ),
            )

        val entries = snapshot.currentPageProviders.single().currentPageEntries

        assertEquals(listOf("东方永夜机", "网页增强"), entries.map { entry -> entry.title })
        assertEquals(
            listOf("第一项", "第二项"),
            entries.single { entry -> entry.sourceItemId == 1L }.commands.map { command -> command.title },
        )
    }

    @Test
    fun `current page search keeps all commands for script matches and only matching commands otherwise`() {
        val entries =
            BrowserPluginCenterFacade
                .project(
                    WebSessionUserscriptUiState(
                        supportState = UserscriptSupportState(isSupported = true),
                        userScriptsAllowed = true,
                        installedScripts =
                            listOf(
                                userscript(id = 1L, enabled = true).copy(
                                    name = "东方永夜机",
                                    description = "页面控制器",
                                ),
                            ),
                        currentPageStatuses =
                            mapOf(
                                1L to UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.SUCCESS),
                            ),
                    ),
                    listOf(
                        UserscriptPageMenuCommand("1:translate", "翻译页面", 1L),
                        UserscriptPageMenuCommand("1:settings", "打开设置", 1L),
                    ),
                )
                .currentPageProviders
                .single()
                .currentPageEntries

        assertEquals(
            2,
            BrowserPluginCenterFacade
                .filterCurrentPageEntries(entries, "东方")
                .single()
                .commands
                .size,
        )
        assertEquals(
            listOf("翻译页面"),
            BrowserPluginCenterFacade
                .filterCurrentPageEntries(entries, "翻译")
                .single()
                .commands
                .map { command -> command.title },
        )
        assertTrue(BrowserPluginCenterFacade.filterCurrentPageEntries(entries, "不存在").isEmpty())
    }

    @Test
    fun `current page overview keeps provider summary without exposing script rows`() {
        val snapshot =
            BrowserPluginCenterFacade.project(
                WebSessionUserscriptUiState(
                    supportState = UserscriptSupportState(isSupported = true),
                    userScriptsAllowed = true,
                    installedScripts =
                        listOf(
                            userscript(1L, true).copy(name = "轻小说文库+"),
                        ),
                    currentPageStatuses =
                        mapOf(
                            1L to UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.SUCCESS),
                        ),
                ),
                listOf(UserscriptPageMenuCommand("menu", "打开设置", 1L)),
            )

        val overview =
            BrowserPluginCenterFacade
                .projectCurrentPageOverview(snapshot, "轻小说")
                .single()

        assertEquals(BUILT_IN_USERSCRIPT_PLUGIN_ID, overview.summary.id)
        assertEquals(1, overview.currentPageItemCount)
        assertEquals(1, overview.currentPageMenuCommandCount)
    }

    @Test
    fun `current page projection rejects stale commands without an installed script owner`() {
        val snapshot =
            BrowserPluginCenterFacade.project(
                WebSessionUserscriptUiState(
                    supportState = UserscriptSupportState(isSupported = true),
                    userScriptsAllowed = true,
                ),
                listOf(UserscriptPageMenuCommand("missing", "Ghost", 99L)),
            )

        assertTrue(snapshot.currentPageProviders.isEmpty())
        assertEquals(0, snapshot.installedPlugins.userscriptPlugin().currentPageMenuCommandCount)
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
            runAt = UserscriptRunAt.DOCUMENT_END,
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
            noFrames = false,
            unwrap = false,
            webRequestRules = emptyList(),
            sourceUrl = null,
            updateUrl = null,
            downloadUrl = null,
            installedAt = 1L,
            updatedAt = 1L,
        )

    private fun List<BrowserPluginSummary>.userscriptPlugin(): BrowserPluginSummary =
        single { plugin -> plugin.id == BUILT_IN_USERSCRIPT_PLUGIN_ID }
}
