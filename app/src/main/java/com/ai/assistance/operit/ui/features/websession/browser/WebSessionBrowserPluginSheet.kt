package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginAction
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginAvailability
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginCenterFacade
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginInstallationKind
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginSummary
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserPluginPage
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageMenuCommand
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.ai.assistance.operit.ui.theme.KiyoriSemanticTone
import com.ai.assistance.operit.ui.theme.resolveColors

private enum class BrowserPluginCenterTab {
    CURRENT_PAGE,
    INSTALLED,
}

@Composable
internal fun WebSessionBrowserPluginSheet(
    page: WebSessionBrowserPluginPage,
    userscriptState: WebSessionUserscriptUiState,
    currentPageMenuCommands: List<UserscriptPageMenuCommand>,
    onOpenUserscriptManager: () -> Unit,
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
    onInvokeUserscriptMenu: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (page) {
        WebSessionBrowserPluginPage.OVERVIEW ->
            BrowserPluginCenterOverview(
                userscriptState = userscriptState,
                currentPageMenuCommands = currentPageMenuCommands,
                onOpenUserscriptManager = onOpenUserscriptManager,
                onOpenPluginLibrarySource = onOpenPluginLibrarySource,
                onSetUserScriptsAllowed = onSetUserScriptsAllowed,
                modifier = modifier,
            )

        WebSessionBrowserPluginPage.USERSCRIPTS ->
            WebSessionUserscriptSheet(
                state = userscriptState,
                currentPageMenuCommands = currentPageMenuCommands,
                onInstallFromUrl = onInstallUserscriptFromUrl,
                onImportLocal = onImportUserscript,
                onConfirmInstall = onConfirmUserscriptInstall,
                onCancelInstall = onCancelUserscriptInstall,
                onSetUserScriptsAllowed = onSetUserScriptsAllowed,
                onSetScriptEnabled = onSetUserscriptEnabled,
                onDeleteScript = onDeleteUserscript,
                onCheckUpdate = onCheckUserscriptUpdate,
                onInvokeMenuCommand = onInvokeUserscriptMenu,
                onNavigateBack = onNavigateToOverview,
                modifier = modifier,
            )
    }
}

@Composable
private fun BrowserPluginCenterOverview(
    userscriptState: WebSessionUserscriptUiState,
    currentPageMenuCommands: List<UserscriptPageMenuCommand>,
    onOpenUserscriptManager: () -> Unit,
    onOpenPluginLibrarySource: (String) -> Unit,
    onSetUserScriptsAllowed: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot =
        remember(userscriptState, currentPageMenuCommands) {
            BrowserPluginCenterFacade.project(userscriptState, currentPageMenuCommands)
        }
    var selectedTab by rememberSaveable { mutableStateOf(BrowserPluginCenterTab.CURRENT_PAGE) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var libraryExpanded by remember { mutableStateOf(false) }
    val userscriptTitle = stringResource(R.string.web_session_userscript_manager_title)
    val userscriptSubtitle = stringResource(R.string.web_session_userscript_plugin_subtitle)
    val sourcePlugins =
        when (selectedTab) {
            BrowserPluginCenterTab.CURRENT_PAGE -> snapshot.currentPagePlugins
            BrowserPluginCenterTab.INSTALLED -> snapshot.installedPlugins
        }
    val canInstallItem =
        snapshot.installedPlugins.any { plugin ->
            BrowserPluginAction.INSTALL_ITEM in plugin.supportedActions
        }
    val normalizedQuery = searchQuery.trim()
    val userscriptMatchesQuery =
        remember(userscriptState.installedScripts, normalizedQuery) {
            userscriptState.installedScripts.any { script ->
                BrowserPluginCenterFacade.matchesUserscriptSearch(script, normalizedQuery)
            }
        }
    val shownPlugins =
        if (
            normalizedQuery.isBlank() ||
                userscriptTitle.contains(normalizedQuery, ignoreCase = true) ||
                userscriptSubtitle.contains(normalizedQuery, ignoreCase = true) ||
                userscriptMatchesQuery
        ) {
            sourcePlugins
        } else {
            emptyList()
        }

    WebSessionSheetScaffold(
        title = stringResource(R.string.web_session_plugins),
        subtitle = stringResource(R.string.web_session_plugins_subtitle),
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                )
            },
            placeholder = {
                Text(text = stringResource(R.string.web_session_plugins_search_hint))
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalButton(
                onClick = onOpenUserscriptManager,
                enabled = canInstallItem,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.web_session_plugins_add),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { libraryExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.web_session_plugins_library),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                DropdownMenu(
                    expanded = libraryExpanded,
                    onDismissRequest = { libraryExpanded = false },
                    shape = WebSessionBrowserPopupShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    shadowElevation = WebSessionBrowserPopupElevation,
                ) {
                    BrowserPluginCenterFacade.librarySources.forEach { source ->
                        WebSessionBrowserDropdownItem(
                            title = source.title,
                            onClick = {
                                libraryExpanded = false
                                onOpenPluginLibrarySource(source.url)
                            },
                        )
                    }
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrowserPluginTab(
                title = stringResource(R.string.web_session_plugins_tab_current_page),
                count = snapshot.currentPagePlugins.size,
                selected = selectedTab == BrowserPluginCenterTab.CURRENT_PAGE,
                onClick = { selectedTab = BrowserPluginCenterTab.CURRENT_PAGE },
            )
            BrowserPluginTab(
                title = stringResource(R.string.web_session_plugins_tab_installed),
                count = snapshot.installedPlugins.size,
                selected = selectedTab == BrowserPluginCenterTab.INSTALLED,
                onClick = { selectedTab = BrowserPluginCenterTab.INSTALLED },
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (shownPlugins.isEmpty()) {
                WebSessionEmptyState(
                    icon =
                        if (normalizedQuery.isNotBlank()) {
                            Icons.Filled.Search
                        } else {
                            Icons.Filled.Extension
                        },
                    title =
                        when {
                            normalizedQuery.isNotBlank() ->
                                stringResource(R.string.web_session_plugins_search_empty)
                            selectedTab == BrowserPluginCenterTab.CURRENT_PAGE ->
                                stringResource(R.string.web_session_plugins_current_page_empty)
                            else ->
                                stringResource(R.string.web_session_plugins_installed_empty)
                        },
                    message =
                        if (
                            normalizedQuery.isBlank() &&
                                selectedTab == BrowserPluginCenterTab.CURRENT_PAGE
                        ) {
                            stringResource(R.string.web_session_plugins_current_page_empty_summary)
                        } else {
                            null
                        },
                    tone = KiyoriSemanticTone.PURPLE,
                )
            } else {
                shownPlugins.forEach { plugin ->
                    BrowserPluginCard(
                        plugin = plugin,
                        onClick = onOpenUserscriptManager,
                        onSetRuntimeAllowed = onSetUserScriptsAllowed,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserPluginTab(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = KiyoriSemanticTone.PURPLE.resolveColors()
    Surface(
        modifier =
            Modifier
                .height(36.dp)
                .clickable(role = Role.Tab, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.container else MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                width = 1.dp,
                color = if (selected) colors.icon else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$title $count",
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.icon else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
        highlightTone = KiyoriSemanticTone.PURPLE,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KiyoriSemanticIconBadge(
                imageVector =
                    if (plugin.availability == BrowserPluginAvailability.AVAILABLE) {
                        Icons.Filled.Extension
                    } else {
                        Icons.Filled.Warning
                    },
                tone =
                    if (plugin.availability == BrowserPluginAvailability.AVAILABLE) {
                        KiyoriSemanticTone.PURPLE
                    } else {
                        KiyoriSemanticTone.RED
                    },
                contentDescription = null,
                containerSize = 42.dp,
                iconSize = 22.dp,
                shape = CircleShape,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.web_session_userscript_manager_title),
                        style = MaterialTheme.typography.titleMedium,
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
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            if (plugin.availability == BrowserPluginAvailability.AVAILABLE) {
                                KiyoriSemanticTone.PURPLE.resolveColors().icon
                            } else {
                                KiyoriSemanticTone.RED.resolveColors().icon
                            },
                    )
                    Switch(
                        checked = plugin.runtimeAllowed,
                        onCheckedChange = onSetRuntimeAllowed,
                        enabled =
                            plugin.availability == BrowserPluginAvailability.AVAILABLE &&
                                BrowserPluginAction.SET_PLUGIN_PERMISSION in plugin.supportedActions,
                    )
                }
                Text(
                    text = stringResource(R.string.web_session_userscript_plugin_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        buildList {
                            add(
                                stringResource(
                                    R.string.web_session_plugins_script_count,
                                    plugin.installedItemCount,
                                ),
                            )
                            add(
                                stringResource(
                                    R.string.web_session_plugins_enabled_count,
                                    plugin.enabledItemCount,
                                ),
                            )
                            add(
                                stringResource(
                                    if (plugin.runtimeAllowed) {
                                        R.string.web_session_plugins_runtime_allowed
                                    } else {
                                        R.string.web_session_plugins_runtime_blocked
                                    },
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
                            if (plugin.currentPageMenuCommandCount > 0) {
                                add(
                                    stringResource(
                                        R.string.web_session_plugins_menu_count,
                                        plugin.currentPageMenuCommandCount,
                                    ),
                                )
                            }
                            if (plugin.hasPendingInstall) {
                                add(stringResource(R.string.web_session_plugins_pending_install))
                            }
                        }.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
