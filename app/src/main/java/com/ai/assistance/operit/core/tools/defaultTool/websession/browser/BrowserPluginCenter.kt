package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import androidx.compose.runtime.Immutable
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageMenuCommand
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeStatus
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageStatusPolicy
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState
import java.util.Locale

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

internal enum class BrowserPluginPageStatus {
    NO_ACTIVE_PAGE,
    DISABLED,
    PERMISSION_REQUIRED,
    UNSUPPORTED,
    NOT_MATCHED,
    MATCHED,
    QUEUED,
    RUNNING,
    SUCCESS,
    ERROR,
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
internal data class BrowserPluginPageCommand(
    val commandId: String,
    val title: String,
)

@Immutable
internal data class BrowserPluginPageEntry(
    val id: String,
    val providerId: String,
    val sourceItemId: Long?,
    val title: String,
    val subtitle: String?,
    val status: BrowserPluginPageStatus,
    val statusDetail: String?,
    val commands: List<BrowserPluginPageCommand>,
    val searchTerms: List<String>,
)

@Immutable
internal data class BrowserPluginProviderProjection(
    val summary: BrowserPluginSummary,
    val currentPageEntries: List<BrowserPluginPageEntry>,
)

@Immutable
internal data class BrowserPluginCenterSnapshot(
    val providerProjections: List<BrowserPluginProviderProjection>,
    val currentPageProviders: List<BrowserPluginProviderProjection>,
    val installedPlugins: List<BrowserPluginSummary>,
)

@Immutable
internal data class BrowserPluginProviderOverview(
    val summary: BrowserPluginSummary,
    val currentPageItemCount: Int,
    val currentPageMenuCommandCount: Int,
)

@Immutable
internal data class BrowserPluginLibrarySource(
    val id: String,
    val title: String,
    val url: String,
)

internal data class BrowserPluginProjectionInput(
    val userscriptState: WebSessionUserscriptUiState,
    val currentPageMenuCommands: List<UserscriptPageMenuCommand>,
)

internal interface BrowserPluginProvider {
    val id: String

    fun project(input: BrowserPluginProjectionInput): BrowserPluginProviderProjection
}

private object UserscriptBrowserPluginProvider : BrowserPluginProvider {
    override val id: String = BUILT_IN_USERSCRIPT_PLUGIN_ID

    override fun project(input: BrowserPluginProjectionInput): BrowserPluginProviderProjection {
        val userscriptState = input.userscriptState
        val availability =
            if (userscriptState.supportState.isSupported) {
                BrowserPluginAvailability.AVAILABLE
            } else {
                BrowserPluginAvailability.UNSUPPORTED
            }
        val commandsByScript =
            input.currentPageMenuCommands.groupBy(UserscriptPageMenuCommand::userscriptId)
        val installedById = userscriptState.installedScripts.associateBy(UserscriptListItem::id)
        val currentPageScriptIds =
            buildSet {
                userscriptState.currentPageStatuses.forEach { (scriptId, status) ->
                    if (status.state in currentPageVisibleStates) {
                        add(scriptId)
                    }
                }
                addAll(commandsByScript.keys)
            }
        val currentPageEntries =
            currentPageScriptIds
                .mapNotNull { scriptId ->
                    val script = installedById[scriptId] ?: return@mapNotNull null
                    val commands =
                        commandsByScript[scriptId]
                            .orEmpty()
                            .map { command ->
                                BrowserPluginPageCommand(
                                    commandId = command.commandId,
                                    title = command.title,
                                )
                            }
                    val runtimeStatus =
                        userscriptState.currentPageStatuses[scriptId]
                            ?: if (commands.isNotEmpty()) {
                                UserscriptPageRuntimeStatus(UserscriptPageRuntimeState.SUCCESS)
                            } else {
                                deriveUserscriptStatus(script, userscriptState)
                            }
                    BrowserPluginPageEntry(
                        id = "userscript:$scriptId",
                        providerId = id,
                        sourceItemId = scriptId,
                        title = script.name,
                        subtitle =
                            listOfNotNull(
                                    script.version.takeIf(String::isNotBlank)?.let { "v$it" },
                                    script.sourceDisplay,
                                )
                                .joinToString(" · ")
                                .ifBlank { null },
                        status = runtimeStatus.state.toPluginPageStatus(),
                        statusDetail = runtimeStatus.detail,
                        commands = commands,
                        searchTerms = script.searchTerms(),
                    )
                }
                .sortedWith(
                    compareBy<BrowserPluginPageEntry> { entry ->
                        currentPageStatusOrder.getValue(entry.status)
                    }.thenBy { entry -> entry.title.lowercase(Locale.ROOT) }
                        .thenBy(BrowserPluginPageEntry::id),
                )
        val summary =
            BrowserPluginSummary(
                id = id,
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
                enabledItemCount = userscriptState.installedScripts.count(UserscriptListItem::enabled),
                currentPageItemCount = currentPageEntries.size,
                currentPageMenuCommandCount = currentPageEntries.sumOf { entry -> entry.commands.size },
                hasPendingInstall = userscriptState.pendingInstall != null,
            )
        return BrowserPluginProviderProjection(
            summary = summary,
            currentPageEntries = currentPageEntries,
        )
    }
}

internal object BrowserPluginCenterFacade {
    private val providers: List<BrowserPluginProvider> =
        listOf(UserscriptBrowserPluginProvider)

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
        val input =
            BrowserPluginProjectionInput(
                userscriptState = userscriptState,
                currentPageMenuCommands = currentPageMenuCommands,
            )
        val projections = providers.map { provider -> provider.project(input) }
        return BrowserPluginCenterSnapshot(
            providerProjections = projections,
            currentPageProviders =
                projections.filter { projection -> projection.currentPageEntries.isNotEmpty() },
            installedPlugins = projections.map(BrowserPluginProviderProjection::summary),
        )
    }

    fun filterCurrentPageEntries(
        entries: List<BrowserPluginPageEntry>,
        rawQuery: String,
    ): List<BrowserPluginPageEntry> {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            return entries
        }
        return entries.mapNotNull { entry ->
            val entryMatches =
                buildList {
                        add(entry.title)
                        entry.subtitle?.let(::add)
                        entry.statusDetail?.let(::add)
                        addAll(entry.searchTerms)
                    }
                    .any { value -> value.contains(query, ignoreCase = true) }
            if (entryMatches) {
                entry
            } else {
                val matchingCommands =
                    entry.commands.filter { command ->
                        command.title.contains(query, ignoreCase = true) ||
                            command.commandId.contains(query, ignoreCase = true)
                    }
                matchingCommands
                    .takeIf(List<BrowserPluginPageCommand>::isNotEmpty)
                    ?.let { commands -> entry.copy(commands = commands) }
            }
        }
    }

    fun projectCurrentPageOverview(
        snapshot: BrowserPluginCenterSnapshot,
        rawQuery: String,
    ): List<BrowserPluginProviderOverview> =
        snapshot.currentPageProviders.mapNotNull { projection ->
            val entries = filterCurrentPageEntries(projection.currentPageEntries, rawQuery)
            entries
                .takeIf(List<BrowserPluginPageEntry>::isNotEmpty)
                ?.let {
                    BrowserPluginProviderOverview(
                        summary = projection.summary,
                        currentPageItemCount = it.size,
                        currentPageMenuCommandCount = it.sumOf { entry -> entry.commands.size },
                    )
                }
        }

    fun matchesUserscriptSearch(
        script: UserscriptListItem,
        rawQuery: String,
    ): Boolean {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            return true
        }
        return script.searchTerms().any { value -> value.contains(query, ignoreCase = true) }
    }
}

private fun UserscriptListItem.searchTerms(): List<String> =
    buildList {
        add(name)
        namespace?.let(::add)
        description?.let(::add)
        sourceDisplay?.let(::add)
        sourceUrl?.let(::add)
        updateUrl?.let(::add)
        downloadUrl?.let(::add)
        addAll(grants)
        addAll(matches)
        addAll(includes)
        addAll(excludes)
        addAll(excludeMatches)
        addAll(connects)
        addAll(tags)
    }

private fun deriveUserscriptStatus(
    script: UserscriptListItem,
    state: WebSessionUserscriptUiState,
): UserscriptPageRuntimeStatus =
    UserscriptPageStatusPolicy.resolve(
        script = script,
        userScriptsAllowed = state.userScriptsAllowed,
        pageUrl = state.currentPageUrl,
        runtimeSupported = state.supportState.isSupported,
        runtimeUnsupportedReason = state.supportState.reason,
    )

private fun UserscriptPageRuntimeState.toPluginPageStatus(): BrowserPluginPageStatus =
    when (this) {
        UserscriptPageRuntimeState.NO_ACTIVE_PAGE -> BrowserPluginPageStatus.NO_ACTIVE_PAGE
        UserscriptPageRuntimeState.DISABLED -> BrowserPluginPageStatus.DISABLED
        UserscriptPageRuntimeState.PERMISSION_REQUIRED -> BrowserPluginPageStatus.PERMISSION_REQUIRED
        UserscriptPageRuntimeState.UNSUPPORTED -> BrowserPluginPageStatus.UNSUPPORTED
        UserscriptPageRuntimeState.NOT_MATCHED -> BrowserPluginPageStatus.NOT_MATCHED
        UserscriptPageRuntimeState.MATCHED -> BrowserPluginPageStatus.MATCHED
        UserscriptPageRuntimeState.QUEUED -> BrowserPluginPageStatus.QUEUED
        UserscriptPageRuntimeState.RUNNING -> BrowserPluginPageStatus.RUNNING
        UserscriptPageRuntimeState.SUCCESS -> BrowserPluginPageStatus.SUCCESS
        UserscriptPageRuntimeState.ERROR -> BrowserPluginPageStatus.ERROR
    }

private val currentPageVisibleStates =
    setOf(
        UserscriptPageRuntimeState.MATCHED,
        UserscriptPageRuntimeState.QUEUED,
        UserscriptPageRuntimeState.RUNNING,
        UserscriptPageRuntimeState.SUCCESS,
        UserscriptPageRuntimeState.ERROR,
    )

private val currentPageStatusOrder =
    mapOf(
        BrowserPluginPageStatus.ERROR to 0,
        BrowserPluginPageStatus.RUNNING to 1,
        BrowserPluginPageStatus.QUEUED to 2,
        BrowserPluginPageStatus.MATCHED to 3,
        BrowserPluginPageStatus.SUCCESS to 4,
        BrowserPluginPageStatus.PERMISSION_REQUIRED to 5,
        BrowserPluginPageStatus.UNSUPPORTED to 6,
        BrowserPluginPageStatus.DISABLED to 7,
        BrowserPluginPageStatus.NOT_MATCHED to 8,
        BrowserPluginPageStatus.NO_ACTIVE_PAGE to 9,
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
