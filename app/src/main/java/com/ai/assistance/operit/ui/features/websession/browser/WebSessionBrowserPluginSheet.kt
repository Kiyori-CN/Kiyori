package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.core.browser.presentation.BrowserPresentationCoordinator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginAction
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginAvailability
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginCenterFacade
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginInstallationKind
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginKind
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginPageEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginPageStatus
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginProviderOverview
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginSummary
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserCookieUiState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserPluginRoute
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionUserscriptWorkbenchTab
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageMenuCommand
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors

private enum class BrowserPluginCenterTab {
    CURRENT_PAGE,
    INSTALLED,
}

@Composable
internal fun WebSessionBrowserPluginSheet(
    route: WebSessionBrowserPluginRoute,
    userscriptState: WebSessionUserscriptUiState,
    currentPageMenuCommands: List<UserscriptPageMenuCommand>,
    currentPageUrl: String,
    cookieState: BrowserCookieUiState,
    cookieReaderEnabled: Boolean,
    onOpenPlugin: (BrowserPluginKind, WebSessionUserscriptWorkbenchTab, String) -> Unit,
    onRefreshCookies: () -> Unit,
    onSetCookieReaderEnabled: (Boolean) -> Unit,
    onOpenPluginLibrarySource: (String) -> Unit,
    onNavigateToOverview: () -> Unit,
    onInstallUserscriptFromUrl: (String) -> Unit,
    onImportUserscript: () -> Unit,
    onConfirmUserscriptInstall: () -> Unit,
    onCancelUserscriptInstall: () -> Unit,
    onSetUserScriptsAllowed: (Boolean) -> Unit,
    onSetUserscriptEnabled: (Long, Boolean) -> Unit,
    onDeleteUserscript: (Long) -> Unit,
    onCheckUserscriptUpdate: (Long) -> Unit,
    onCheckAllUserscriptUpdates: () -> Unit,
    onApplyUserscriptUpdate: (Long) -> Unit,
    onApplyAllSafeUserscriptUpdates: () -> Unit,
    onSetUserscriptsEnabled: (Set<Long>, Boolean) -> Unit,
    onDeleteUserscripts: (Set<Long>) -> Unit,
    onOpenUserscriptDetail: (Long) -> Unit,
    onLoadUserscriptDetail: (Long) -> Unit,
    onOpenNewUserscriptEditor: () -> Unit,
    onOpenExistingUserscriptEditor: (Long) -> Unit,
    onOpenUserscriptDraftEditor: (String) -> Unit,
    onInvokeUserscriptMenu: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (route) {
        WebSessionBrowserPluginRoute.Overview ->
            BrowserPluginCenterOverview(
                userscriptState = userscriptState,
                currentPageMenuCommands = currentPageMenuCommands,
                currentPageUrl = currentPageUrl,
                cookieReaderEnabled = cookieReaderEnabled,
                onOpenPlugin = onOpenPlugin,
                onOpenPluginLibrarySource = onOpenPluginLibrarySource,
                onOpenNewUserscriptEditor = onOpenNewUserscriptEditor,
                onInstallUserscriptFromUrl = onInstallUserscriptFromUrl,
                onImportUserscript = onImportUserscript,
                onSetUserScriptsAllowed = onSetUserScriptsAllowed,
                onSetCookieReaderEnabled = onSetCookieReaderEnabled,
                modifier = modifier,
            )

        is WebSessionBrowserPluginRoute.Userscripts ->
            WebSessionUserscriptSheet(
                state = userscriptState,
                initialTab = route.initialTab,
                initialSearchQuery = route.initialSearchQuery,
                currentPageMenuCommands = currentPageMenuCommands,
                onInstallFromUrl = onInstallUserscriptFromUrl,
                onImportLocal = onImportUserscript,
                onConfirmInstall = onConfirmUserscriptInstall,
                onCancelInstall = onCancelUserscriptInstall,
                onSetUserScriptsAllowed = onSetUserScriptsAllowed,
                onSetScriptEnabled = onSetUserscriptEnabled,
                onDeleteScript = onDeleteUserscript,
                onCheckUpdate = onCheckUserscriptUpdate,
                onCheckAllUpdates = onCheckAllUserscriptUpdates,
                onApplyUpdate = onApplyUserscriptUpdate,
                onApplyAllSafeUpdates = onApplyAllSafeUserscriptUpdates,
                onSetScriptsEnabled = onSetUserscriptsEnabled,
                onDeleteScripts = onDeleteUserscripts,
                onOpenScriptDetail = onOpenUserscriptDetail,
                onOpenNewEditor = onOpenNewUserscriptEditor,
                onOpenExistingEditor = onOpenExistingUserscriptEditor,
                onOpenDraftEditor = onOpenUserscriptDraftEditor,
                onOpenPluginLibrarySource = onOpenPluginLibrarySource,
                onInvokeMenuCommand = onInvokeUserscriptMenu,
                onNavigateBack = onNavigateToOverview,
                modifier = modifier,
            )

        is WebSessionBrowserPluginRoute.UserscriptDetail ->
            WebSessionUserscriptDetail(
                scriptId = route.scriptId,
                state = userscriptState,
                onLoad = onLoadUserscriptDetail,
                onNavigateBack = onNavigateToOverview,
                onEdit = onOpenExistingUserscriptEditor,
                onSetEnabled = onSetUserscriptEnabled,
                onCheckUpdate = onCheckUserscriptUpdate,
                onDelete = onDeleteUserscript,
                modifier = modifier,
            )

        is WebSessionBrowserPluginRoute.UserscriptEditor -> Unit

        WebSessionBrowserPluginRoute.CookieReader ->
            WebSessionBrowserCookieSheet(
                state = cookieState,
                currentPageUrl = currentPageUrl,
                cookieReaderEnabled = cookieReaderEnabled,
                onRefresh = onRefreshCookies,
                onNavigateBack = onNavigateToOverview,
                modifier = modifier,
            )
    }
}

@Composable
private fun BrowserPluginCenterOverview(
    userscriptState: WebSessionUserscriptUiState,
    currentPageMenuCommands: List<UserscriptPageMenuCommand>,
    currentPageUrl: String,
    cookieReaderEnabled: Boolean,
    onOpenPlugin: (BrowserPluginKind, WebSessionUserscriptWorkbenchTab, String) -> Unit,
    onOpenPluginLibrarySource: (String) -> Unit,
    onOpenNewUserscriptEditor: () -> Unit,
    onInstallUserscriptFromUrl: (String) -> Unit,
    onImportUserscript: () -> Unit,
    onSetUserScriptsAllowed: (Boolean) -> Unit,
    onSetCookieReaderEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot =
        remember(userscriptState, currentPageMenuCommands, currentPageUrl, cookieReaderEnabled) {
            BrowserPluginCenterFacade.project(
                userscriptState = userscriptState,
                currentPageMenuCommands = currentPageMenuCommands,
                currentPageUrl = currentPageUrl,
                cookieReaderEnabled = cookieReaderEnabled,
            )
        }
    var selectedTab by rememberSaveable { mutableStateOf(BrowserPluginCenterTab.CURRENT_PAGE) }
    val context = LocalContext.current
    val extensionCoordinator = remember(context) { BrowserPresentationCoordinator.getInstance(context) }
    val extensions by extensionCoordinator.browserExtensions.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var installUrlDialogVisible by remember { mutableStateOf(false) }
    var installUrl by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = searchQuery.trim()
    val matchingExtensions = extensions.filter {
        normalizedQuery.isBlank() || it.bundle.manifest.name.contains(normalizedQuery, true) ||
            it.id.contains(normalizedQuery, true) || it.bundle.manifest.description.contains(normalizedQuery, true)
    }
    val currentExtensions = matchingExtensions.filter {
        it.enabled && it.bundle.manifest.content_scripts.any { entry -> entry.matchesUrl(currentPageUrl) }
    }
    val userscriptTitle = stringResource(R.string.web_session_userscript_manager_title)
    val userscriptSubtitle = stringResource(R.string.web_session_userscript_plugin_subtitle)
    val cookieTitle = stringResource(R.string.web_session_cookie_reader_title)
    val cookieSubtitle = stringResource(R.string.web_session_cookie_reader_subtitle)
    val installedScriptCount = snapshot.installedPlugins.sumOf(BrowserPluginSummary::installedItemCount)
    val currentPageProviders =
        remember(snapshot, normalizedQuery) {
            BrowserPluginCenterFacade.projectCurrentPageOverview(snapshot, normalizedQuery)
        }
    val installedPlugins =
        remember(
            snapshot.installedPlugins,
            userscriptState.installedScripts,
            normalizedQuery,
            userscriptTitle,
            userscriptSubtitle,
            cookieTitle,
            cookieSubtitle,
        ) {
            snapshot.installedPlugins.filter { plugin ->
                normalizedQuery.isBlank() ||
                    when (plugin.kind) {
                        BrowserPluginKind.USERSCRIPT_MANAGER ->
                            userscriptTitle.contains(normalizedQuery, ignoreCase = true) ||
                                userscriptSubtitle.contains(normalizedQuery, ignoreCase = true) ||
                                userscriptState.installedScripts.any { script ->
                                    BrowserPluginCenterFacade.matchesUserscriptSearch(script, normalizedQuery)
                                }
                        BrowserPluginKind.COOKIE_READER ->
                            cookieTitle.contains(normalizedQuery, ignoreCase = true) ||
                                cookieSubtitle.contains(normalizedQuery, ignoreCase = true) ||
                                "cookie".contains(normalizedQuery, ignoreCase = true)
                    }
            }
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        WebSessionDrawerHeader(
            title = stringResource(R.string.web_session_plugins),
            leadingIcon = Icons.Filled.Extension,
            tone = WebSessionBrowserMenuTone.PLUGINS,
            countText =
                pluralStringResource(
                    R.plurals.web_session_plugins_script_count,
                    installedScriptCount,
                    installedScriptCount,
                ),
            actions = {
                BrowserPluginAddMenu(
                    onOpenNewUserscript = onOpenNewUserscriptEditor,
                    onRequestInstallFromUrl = { installUrlDialogVisible = true },
                    onImportLocal = onImportUserscript,
                    onOpenLibrarySource = onOpenPluginLibrarySource,
                )
            },
        )

        WebSessionSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            onClear = { searchQuery = "" },
            placeholder = stringResource(R.string.web_session_plugins_search_hint),
            tone = WebSessionBrowserMenuTone.PLUGINS,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WebSessionFilterChip(
                label =
                    "${stringResource(R.string.web_session_plugins_tab_current_page)} " +
                        (currentPageProviders.size + currentExtensions.size),
                selected = selectedTab == BrowserPluginCenterTab.CURRENT_PAGE,
                tone = WebSessionBrowserMenuTone.PLUGINS,
                onClick = { selectedTab = BrowserPluginCenterTab.CURRENT_PAGE },
            )
            WebSessionFilterChip(
                label =
                    "${stringResource(R.string.web_session_plugins_tab_installed)} " +
                        (installedPlugins.size + matchingExtensions.size),
                selected = selectedTab == BrowserPluginCenterTab.INSTALLED,
                tone = WebSessionBrowserMenuTone.PLUGINS,
                onClick = { selectedTab = BrowserPluginCenterTab.INSTALLED },
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (selectedTab) {
                BrowserPluginCenterTab.CURRENT_PAGE -> {
                    if (currentPageProviders.isEmpty() && currentExtensions.isEmpty()) {
                        BrowserPluginCenterEmptyState(
                            searchActive = normalizedQuery.isNotBlank(),
                            currentPage = true,
                        )
                    } else {
                        currentPageProviders.forEach { projection ->
                            BrowserPluginCurrentPageProviderSection(
                                projection = projection,
                                onOpenProvider = {
                                    onOpenPlugin(
                                        projection.summary.kind,
                                        WebSessionUserscriptWorkbenchTab.CURRENT_PAGE,
                                        normalizedQuery,
                                    )
                                },
                            )
                        }
                    }
                    BrowserExtensionCards(currentExtensions, extensionCoordinator)
                }

                BrowserPluginCenterTab.INSTALLED -> {
                    if (installedPlugins.isEmpty() && matchingExtensions.isEmpty()) {
                        BrowserPluginCenterEmptyState(
                            searchActive = normalizedQuery.isNotBlank(),
                            currentPage = false,
                        )
                    } else {
                        installedPlugins.forEach { plugin ->
                            BrowserPluginCard(
                                plugin = plugin,
                                onClick = {
                                    onOpenPlugin(
                                        plugin.kind,
                                        WebSessionUserscriptWorkbenchTab.INSTALLED,
                                        normalizedQuery,
                                    )
                                },
                                onSetRuntimeAllowed = { enabled ->
                                    when (plugin.kind) {
                                        BrowserPluginKind.USERSCRIPT_MANAGER ->
                                            onSetUserScriptsAllowed(enabled)
                                        BrowserPluginKind.COOKIE_READER ->
                                            onSetCookieReaderEnabled(enabled)
                                    }
                                },
                            )
                        }
                    }
                    BrowserExtensionCards(matchingExtensions, extensionCoordinator)
                }
            }
        }
    }

    if (installUrlDialogVisible) {
        UserscriptInstallFromUrlDialog(
            value = installUrl,
            onValueChanged = { installUrl = it },
            onDismiss = { installUrlDialogVisible = false },
            onConfirm = { normalizedUrl ->
                installUrlDialogVisible = false
                installUrl = ""
                onInstallUserscriptFromUrl(normalizedUrl)
            },
        )
    }
}

@Composable
private fun BrowserPluginCenterEmptyState(
    searchActive: Boolean,
    currentPage: Boolean,
) {
    WebSessionEmptyState(
        icon = if (searchActive) Icons.Filled.Search else Icons.Filled.Extension,
        title =
            when {
                searchActive -> stringResource(R.string.web_session_plugins_search_empty)
                currentPage -> stringResource(R.string.web_session_plugins_current_page_empty)
                else -> stringResource(R.string.web_session_plugins_installed_empty)
            },
        message =
            if (!searchActive && currentPage) {
                stringResource(R.string.web_session_plugins_current_page_empty_summary)
            } else {
                null
            },
        tone = WebSessionBrowserMenuTone.PLUGINS,
    )
}

@Composable
private fun BrowserPluginCurrentPageProviderSection(
    projection: BrowserPluginProviderOverview,
    onOpenProvider: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenProvider),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WebSessionBrowserMenuIconBadge(
                imageVector = Icons.Filled.Extension,
                tone = WebSessionBrowserMenuTone.PLUGINS,
                contentDescription = null,
                containerSize = 34.dp,
                iconSize = 18.dp,
                shape = RoundedCornerShape(9.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = pluginTitle(projection.summary.kind),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text =
                        buildList {
                            add(
                                stringResource(
                                    R.string.web_session_plugins_page_count,
                                    projection.currentPageItemCount,
                                ),
                            )
                            if (projection.currentPageMenuCommandCount > 0) {
                                add(
                                    pluralStringResource(
                                        R.plurals.web_session_plugins_menu_count,
                                        projection.currentPageMenuCommandCount,
                                        projection.currentPageMenuCommandCount,
                                    ),
                                )
                            }
                        }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun BrowserPluginPageEntryList(
    entries: List<BrowserPluginPageEntry>,
    searchActive: Boolean,
    onInvokeMenuCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEach { entry ->
            BrowserPluginPageEntryCard(
                entry = entry,
                searchActive = searchActive,
                onInvokeMenuCommand = onInvokeMenuCommand,
            )
        }
    }
}

@Composable
private fun BrowserPluginPageEntryCard(
    entry: BrowserPluginPageEntry,
    searchActive: Boolean,
    onInvokeMenuCommand: (String) -> Unit,
) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val canExpand = entry.commands.isNotEmpty()
    val showCommands = canExpand && (searchActive || expanded)

    WebSessionItemCard(
        onClick =
            if (canExpand) {
                { expanded = !expanded }
            } else {
                null
            },
        highlighted = entry.status == BrowserPluginPageStatus.ERROR,
        highlightTone = KiyoriSemanticTone.RED,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WebSessionBrowserMenuIconBadge(
                    imageVector = Icons.Filled.Extension,
                    tone = WebSessionBrowserMenuTone.PLUGINS,
                    contentDescription = null,
                    containerSize = 34.dp,
                    iconSize = 18.dp,
                    shape = CircleShape,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BrowserPluginPageStatusLine(entry)
                }
                if (entry.commands.isNotEmpty()) {
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.web_session_plugins_menu_count,
                                entry.commands.size,
                                entry.commands.size,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector =
                            if (showCommands) {
                                Icons.Filled.KeyboardArrowUp
                            } else {
                                Icons.Filled.KeyboardArrowDown
                            },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (showCommands) {
                entry.commands.forEach { command ->
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 44.dp)
                                .heightIn(min = 44.dp)
                                .clickable {
                                    onInvokeMenuCommand(command.commandId)
                                },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = WebSessionBrowserMenuTone.PLUGINS.resolveColors().icon,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = command.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserPluginPageStatusLine(entry: BrowserPluginPageEntry) {
    val metadata =
        listOfNotNull(
            pluginPageStatusLabel(entry.status),
            entry.subtitle,
        ).joinToString(" · ")

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(7.dp)
                        .background(
                            color = pluginPageStatusColor(entry.status),
                            shape = CircleShape,
                        ),
            )
            Text(
                text = metadata,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        entry.statusDetail?.takeIf { it.isNotBlank() }?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (entry.status == BrowserPluginPageStatus.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun pluginPageStatusLabel(status: BrowserPluginPageStatus): String =
    when (status) {
        BrowserPluginPageStatus.NO_ACTIVE_PAGE ->
            stringResource(R.string.web_session_userscript_status_no_active_page)
        BrowserPluginPageStatus.DISABLED ->
            stringResource(R.string.web_session_userscript_status_disabled)
        BrowserPluginPageStatus.PERMISSION_REQUIRED ->
            stringResource(R.string.web_session_userscript_status_permission_required)
        BrowserPluginPageStatus.UNSUPPORTED ->
            stringResource(R.string.web_session_userscript_status_unsupported)
        BrowserPluginPageStatus.NOT_MATCHED ->
            stringResource(R.string.web_session_userscript_status_not_matched)
        BrowserPluginPageStatus.MATCHED ->
            stringResource(R.string.web_session_userscript_status_matched)
        BrowserPluginPageStatus.QUEUED ->
            stringResource(R.string.web_session_userscript_status_queued)
        BrowserPluginPageStatus.RUNNING ->
            stringResource(R.string.web_session_userscript_status_running)
        BrowserPluginPageStatus.SUCCESS ->
            stringResource(R.string.web_session_userscript_status_success)
        BrowserPluginPageStatus.ERROR ->
            stringResource(R.string.web_session_userscript_status_error)
    }

@Composable
private fun pluginPageStatusColor(status: BrowserPluginPageStatus) =
    when (status) {
        BrowserPluginPageStatus.NO_ACTIVE_PAGE,
        BrowserPluginPageStatus.DISABLED,
        BrowserPluginPageStatus.NOT_MATCHED -> MaterialTheme.colorScheme.outline
        BrowserPluginPageStatus.PERMISSION_REQUIRED,
        BrowserPluginPageStatus.MATCHED,
        BrowserPluginPageStatus.QUEUED -> MaterialTheme.colorScheme.tertiary
        BrowserPluginPageStatus.UNSUPPORTED,
        BrowserPluginPageStatus.ERROR -> MaterialTheme.colorScheme.error
        BrowserPluginPageStatus.RUNNING -> MaterialTheme.colorScheme.primary
        BrowserPluginPageStatus.SUCCESS -> MaterialTheme.colorScheme.secondary
    }

@Composable
private fun BrowserPluginCard(
    plugin: BrowserPluginSummary,
    onClick: () -> Unit,
    onSetRuntimeAllowed: (Boolean) -> Unit,
) {
    WebSessionItemCard(
        onClick =
            if (BrowserPluginAction.OPEN_MANAGER in plugin.supportedActions) {
                onClick
            } else {
                null
            },
        highlighted = plugin.hasPendingInstall,
        highlightTone = WebSessionBrowserMenuTone.PLUGINS,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WebSessionBrowserMenuIconBadge(
                imageVector = Icons.Filled.Extension,
                tone = WebSessionBrowserMenuTone.PLUGINS,
                contentDescription = null,
                containerSize = 38.dp,
                iconSize = 20.dp,
                shape = CircleShape,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = pluginTitle(plugin.kind),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text =
                            if (
                                plugin.installationKind == BrowserPluginInstallationKind.BUILT_IN
                            ) {
                                buildList {
                                    add(stringResource(R.string.web_session_plugins_builtin))
                                    if (
                                        plugin.availability ==
                                            BrowserPluginAvailability.UNSUPPORTED
                                    ) {
                                        add(
                                            stringResource(
                                                R.string.web_session_plugins_unsupported,
                                            ),
                                        )
                                    }
                                }.joinToString(" · ")
                            } else {
                                stringResource(R.string.web_session_plugins_unsupported)
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (plugin.availability == BrowserPluginAvailability.AVAILABLE) {
                                WebSessionBrowserMenuTone.PLUGINS.resolveColors().icon
                            } else {
                                KiyoriSemanticTone.RED.resolveColors().icon
                            },
                    )
                }
                Text(
                    text = pluginSubtitle(plugin.kind),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        if (plugin.kind == BrowserPluginKind.COOKIE_READER) {
                            stringResource(R.string.web_session_cookie_reader_card_summary)
                        } else {
                            buildList {
                                add(
                                    pluralStringResource(
                                        R.plurals.web_session_plugins_script_count,
                                        plugin.installedItemCount,
                                        plugin.installedItemCount,
                                    ),
                                )
                                add(
                                    pluralStringResource(
                                        R.plurals.web_session_plugins_enabled_count,
                                        plugin.enabledItemCount,
                                        plugin.enabledItemCount,
                                    ),
                                )
                                if (plugin.currentPageItemCount > 0) {
                                    add(
                                        stringResource(
                                            R.string.web_session_plugins_page_count,
                                            plugin.currentPageItemCount,
                                        ),
                                    )
                                }
                                if (plugin.hasPendingInstall) {
                                    add(stringResource(R.string.web_session_plugins_pending_install))
                                }
                            }.joinToString(" · ")
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (
                plugin.kind == BrowserPluginKind.USERSCRIPT_MANAGER ||
                    plugin.kind == BrowserPluginKind.COOKIE_READER
            ) {
                Switch(
                    checked = plugin.runtimeAllowed,
                    onCheckedChange = onSetRuntimeAllowed,
                    enabled =
                        plugin.availability == BrowserPluginAvailability.AVAILABLE &&
                            BrowserPluginAction.SET_PLUGIN_PERMISSION in plugin.supportedActions,
                )
            }
        }
    }
}

@Composable
private fun pluginTitle(kind: BrowserPluginKind): String =
    when (kind) {
        BrowserPluginKind.USERSCRIPT_MANAGER ->
            stringResource(R.string.web_session_userscript_manager_title)
        BrowserPluginKind.COOKIE_READER ->
            stringResource(R.string.web_session_cookie_reader_title)
    }

@Composable
private fun pluginSubtitle(kind: BrowserPluginKind): String =
    when (kind) {
        BrowserPluginKind.USERSCRIPT_MANAGER ->
            stringResource(R.string.web_session_userscript_plugin_subtitle)
        BrowserPluginKind.COOKIE_READER ->
            stringResource(R.string.web_session_cookie_reader_subtitle)
    }
