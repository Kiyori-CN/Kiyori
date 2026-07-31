package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import androidx.compose.runtime.Immutable
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageMenuCommand
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState

internal const val BUILT_IN_USERSCRIPT_PLUGIN_ID = "kiyori.browser.userscript"

internal enum class BrowserPluginKind {
    USERSCRIPT_MANAGER,
}

internal enum class BrowserPluginAvailability {
    AVAILABLE,
    UNSUPPORTED,
}

internal enum class BrowserPluginInstallationKind {
    BUILT_IN,
    USER_INSTALLED,
    MANAGED,
}

internal enum class BrowserPluginAction {
    OPEN_MANAGER,
    SET_PLUGIN_PERMISSION,
    INSTALL_ITEM,
    UPDATE_ITEM,
    SET_ITEM_ENABLED,
    DELETE_ITEM,
    INVOKE_PAGE_MENU,
}

@Immutable
internal data class BrowserPluginSummary(
    val id: String,
    val kind: BrowserPluginKind,
    val installationKind: BrowserPluginInstallationKind,
    val availability: BrowserPluginAvailability,
    val runtimeAllowed: Boolean,
    val supportedActions: Set<BrowserPluginAction>,
    val installedItemCount: Int,
    val enabledItemCount: Int,
    val currentPageItemCount: Int,
    val currentPageMenuCommandCount: Int,
    val hasPendingInstall: Boolean,
)

@Immutable
internal data class BrowserPluginCenterSnapshot(
    val currentPagePlugins: List<BrowserPluginSummary>,
    val installedPlugins: List<BrowserPluginSummary>,
)

@Immutable
internal data class BrowserPluginLibrarySource(
    val id: String,
    val title: String,
    val url: String,
)

internal object BrowserPluginCenterFacade {
    val librarySources: List<BrowserPluginLibrarySource> =
        listOf(
            BrowserPluginLibrarySource(
                id = "greasy_fork",
                title = "Greasy Fork",
                url = "https://greasyfork.org/",
            ),
            BrowserPluginLibrarySource(
                id = "script_cat",
                title = "ScriptCat",
                url = "https://scriptcat.org/",
            ),
            BrowserPluginLibrarySource(
                id = "open_user_js",
                title = "OpenUserJS",
                url = "https://openuserjs.org/",
            ),
            BrowserPluginLibrarySource(
                id = "userscript_zone",
                title = "Userscript.Zone",
                url = "https://www.userscript.zone/",
            ),
            BrowserPluginLibrarySource(
                id = "github_userscript",
                title = "GitHub",
                url = "https://github.com/topics/userscript",
            ),
        )

    fun project(
        userscriptState: WebSessionUserscriptUiState,
        currentPageMenuCommands: List<UserscriptPageMenuCommand>,
    ): BrowserPluginCenterSnapshot {
        val availability =
            if (userscriptState.supportState.isSupported) {
                BrowserPluginAvailability.AVAILABLE
            } else {
                BrowserPluginAvailability.UNSUPPORTED
            }
        val currentPageItemCount =
            userscriptState.currentPageStatuses.values.count { status ->
                status.state in currentPageVisibleStates
            }
        val summary =
            BrowserPluginSummary(
                id = BUILT_IN_USERSCRIPT_PLUGIN_ID,
                kind = BrowserPluginKind.USERSCRIPT_MANAGER,
                installationKind = BrowserPluginInstallationKind.BUILT_IN,
                availability = availability,
                runtimeAllowed = userscriptState.userScriptsAllowed,
                supportedActions =
                    if (availability == BrowserPluginAvailability.AVAILABLE) {
                        userscriptActions
                    } else {
                        setOf(BrowserPluginAction.OPEN_MANAGER)
                    },
                installedItemCount = userscriptState.installedScripts.size,
                enabledItemCount = userscriptState.installedScripts.count { script -> script.enabled },
                currentPageItemCount = currentPageItemCount,
                currentPageMenuCommandCount = currentPageMenuCommands.size,
                hasPendingInstall = userscriptState.pendingInstall != null,
            )
        val currentPagePlugins =
            if (currentPageItemCount > 0 || currentPageMenuCommands.isNotEmpty()) {
                listOf(summary)
            } else {
                emptyList()
            }
        return BrowserPluginCenterSnapshot(
            currentPagePlugins = currentPagePlugins,
            installedPlugins = listOf(summary),
        )
    }

    fun matchesUserscriptSearch(
        script: UserscriptListItem,
        rawQuery: String,
    ): Boolean {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            return true
        }
        return buildList {
            add(script.name)
            script.namespace?.let(::add)
            script.description?.let(::add)
            script.sourceDisplay?.let(::add)
            script.sourceUrl?.let(::add)
            script.updateUrl?.let(::add)
            script.downloadUrl?.let(::add)
            addAll(script.grants)
            addAll(script.matches)
            addAll(script.includes)
            addAll(script.connects)
            addAll(script.tags)
        }.any { value -> value.contains(query, ignoreCase = true) }
    }

    private val currentPageVisibleStates =
        setOf(
            UserscriptPageRuntimeState.QUEUED,
            UserscriptPageRuntimeState.RUNNING,
            UserscriptPageRuntimeState.SUCCESS,
            UserscriptPageRuntimeState.ERROR,
        )

    private val userscriptActions =
        setOf(
            BrowserPluginAction.OPEN_MANAGER,
            BrowserPluginAction.SET_PLUGIN_PERMISSION,
            BrowserPluginAction.INSTALL_ITEM,
            BrowserPluginAction.UPDATE_ITEM,
            BrowserPluginAction.SET_ITEM_ENABLED,
            BrowserPluginAction.DELETE_ITEM,
            BrowserPluginAction.INVOKE_PAGE_MENU,
        )
}
