package com.ai.assistance.operit.ui.features.websession.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPluginCenterFacade
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionUserscriptWorkbenchTab
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.buildUserscriptLogEntryReport
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.buildUserscriptLogReport
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptInstallPreview
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptDraft
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptExecutionWorld
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptLogItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptMetadataEditorPolicy
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageMenuCommand
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeStatus
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageStatusPolicy
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptUpdateCandidate
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptUnsafeWindowMode
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
import kotlinx.coroutines.launch

private enum class UserscriptLogFilter {
    ALL,
    INFO,
    WARNING,
    ERROR,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WebSessionUserscriptSheet(
    state: WebSessionUserscriptUiState,
    modifier: Modifier = Modifier,
    initialTab: WebSessionUserscriptWorkbenchTab = WebSessionUserscriptWorkbenchTab.CURRENT_PAGE,
    initialSearchQuery: String = "",
    currentPageMenuCommands: List<UserscriptPageMenuCommand>,
    onInstallFromUrl: (String) -> Unit,
    onImportLocal: () -> Unit,
    onConfirmInstall: () -> Unit,
    onCancelInstall: () -> Unit,
    onSetUserScriptsAllowed: (Boolean) -> Unit,
    onSetScriptEnabled: (Long, Boolean) -> Unit,
    onDeleteScript: (Long) -> Unit,
    onCheckUpdate: (Long) -> Unit,
    onCheckAllUpdates: () -> Unit,
    onApplyUpdate: (Long) -> Unit,
    onApplyAllSafeUpdates: () -> Unit,
    onSetScriptsEnabled: (Set<Long>, Boolean) -> Unit,
    onDeleteScripts: (Set<Long>) -> Unit,
    onOpenScriptDetail: (Long) -> Unit,
    onOpenNewEditor: () -> Unit,
    onOpenExistingEditor: (Long) -> Unit,
    onOpenDraftEditor: (String) -> Unit,
    onOpenPluginLibrarySource: (String) -> Unit,
    onInvokeMenuCommand: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    var searchQuery by rememberSaveable { mutableStateOf(initialSearchQuery) }
    var settingsMenuExpanded by remember { mutableStateOf(false) }
    var installUrlDialogVisible by remember { mutableStateOf(false) }
    var installUrl by rememberSaveable { mutableStateOf("") }
    var selectedScriptIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var deleteSelectionPrompt by remember { mutableStateOf(false) }
    var pendingSingleDeleteId by remember { mutableStateOf<Long?>(null) }
    var logFilter by rememberSaveable { mutableStateOf(UserscriptLogFilter.ALL) }
    var exportingLogs by remember { mutableStateOf(false) }
    val currentPageEntries =
        remember(state, currentPageMenuCommands) {
            BrowserPluginCenterFacade
                .project(state, currentPageMenuCommands)
                .providerProjections
                .flatMap { projection -> projection.currentPageEntries }
        }
    val visibleCurrentPageEntries =
        remember(currentPageEntries, searchQuery) {
            BrowserPluginCenterFacade.filterCurrentPageEntries(currentPageEntries, searchQuery)
        }
    val visibleScripts =
        remember(state.installedScripts, searchQuery) {
            state.installedScripts.filter { script ->
                BrowserPluginCenterFacade.matchesUserscriptSearch(script, searchQuery)
            }
        }
    val visibleLogs =
        remember(state.recentLogs, searchQuery, logFilter) {
            state.recentLogs.filter { log ->
                val levelMatches =
                    when (logFilter) {
                        UserscriptLogFilter.ALL -> true
                        UserscriptLogFilter.INFO -> log.level.equals("info", ignoreCase = true)
                        UserscriptLogFilter.WARNING ->
                            log.level.equals("warning", ignoreCase = true) ||
                                log.level.equals("warn", ignoreCase = true)
                        UserscriptLogFilter.ERROR -> log.level.equals("error", ignoreCase = true)
                    }
                val scriptName =
                    log.userscriptId?.let { scriptId ->
                        state.installedScripts.firstOrNull { script -> script.id == scriptId }?.name
                    }.orEmpty()
                levelMatches &&
                    (
                        searchQuery.isBlank() ||
                            log.message.contains(searchQuery, ignoreCase = true) ||
                            log.pageUrl.orEmpty().contains(searchQuery, ignoreCase = true) ||
                            scriptName.contains(searchQuery, ignoreCase = true)
                        )
            }
        }

    LaunchedEffect(state.installedScripts) {
        val installedIds = state.installedScripts.mapTo(hashSetOf(), UserscriptListItem::id)
        selectedScriptIds = selectedScriptIds.intersect(installedIds)
    }
    LaunchedEffect(initialTab, initialSearchQuery) {
        selectedTab = initialTab
        searchQuery = initialSearchQuery
        selectedScriptIds = emptySet()
    }
    LaunchedEffect(state.pendingInstall) {
        if (state.pendingInstall != null) {
            selectedTab = WebSessionUserscriptWorkbenchTab.UPDATES
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        if (selectedScriptIds.isEmpty()) {
            WebSessionDrawerHeader(
                title = stringResource(R.string.web_session_userscript_manager_title),
                leadingIcon = Icons.Filled.Extension,
                tone = KiyoriSemanticTone.PURPLE,
                countText =
                    stringResource(
                        R.string.web_session_plugins_script_count,
                        state.installedScripts.size,
                    ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    BrowserPluginAddMenu(
                        onOpenNewUserscript = onOpenNewEditor,
                        onRequestInstallFromUrl = { installUrlDialogVisible = true },
                        onImportLocal = onImportLocal,
                        onOpenLibrarySource = onOpenPluginLibrarySource,
                    )
                    Box {
                        IconButton(onClick = { settingsMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.more),
                            )
                        }
                        DropdownMenu(
                            expanded = settingsMenuExpanded,
                            onDismissRequest = { settingsMenuExpanded = false },
                            shape = WebSessionBrowserPopupShape,
                            containerColor = MaterialTheme.colorScheme.surface,
                            shadowElevation = WebSessionBrowserPopupElevation,
                        ) {
                            WebSessionBrowserDropdownItem(
                                title =
                                    if (state.userScriptsAllowed) {
                                        stringResource(R.string.web_session_userscript_disable)
                                    } else {
                                        stringResource(R.string.web_session_userscript_enable)
                                    },
                                onClick = {
                                    settingsMenuExpanded = false
                                    onSetUserScriptsAllowed(!state.userScriptsAllowed)
                                },
                            )
                        }
                    }
                },
            )
        } else {
            UserscriptSelectionHeader(
                selectedCount = selectedScriptIds.size,
                onClose = { selectedScriptIds = emptySet() },
                onEnable = {
                    onSetScriptsEnabled(selectedScriptIds, true)
                    selectedScriptIds = emptySet()
                },
                onDisable = {
                    onSetScriptsEnabled(selectedScriptIds, false)
                    selectedScriptIds = emptySet()
                },
                onDelete = { deleteSelectionPrompt = true },
            )
        }

        WebSessionSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            onClear = { searchQuery = "" },
            placeholder = stringResource(R.string.web_session_userscript_search_hint),
            tone = KiyoriSemanticTone.PURPLE,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WebSessionUserscriptWorkbenchTab.entries.forEach { tab ->
                WebSessionFilterChip(
                    label =
                        when (tab) {
                            WebSessionUserscriptWorkbenchTab.CURRENT_PAGE ->
                                "${stringResource(R.string.web_session_plugins_tab_current_page)} ${currentPageEntries.size}"
                            WebSessionUserscriptWorkbenchTab.INSTALLED ->
                                "${stringResource(R.string.web_session_plugins_tab_installed)} ${state.installedScripts.size}"
                            WebSessionUserscriptWorkbenchTab.UPDATES ->
                                "${stringResource(R.string.web_session_userscript_tab_updates)} ${state.updateCandidates.size}"
                            WebSessionUserscriptWorkbenchTab.LOGS ->
                                "${stringResource(R.string.web_session_userscript_logs)} ${state.recentLogs.size}"
                        },
                    selected = selectedTab == tab,
                    tone = KiyoriSemanticTone.PURPLE,
                    onClick = {
                        selectedTab = tab
                        selectedScriptIds = emptySet()
                    },
                )
            }
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
            if (!state.userScriptsAllowed) {
                UserscriptPermissionBanner(onEnable = { onSetUserScriptsAllowed(true) })
            }

            when (selectedTab) {
                WebSessionUserscriptWorkbenchTab.CURRENT_PAGE -> {
                    if (!state.supportState.isSupported) {
                        WebSessionEmptyState(
                            icon = Icons.Filled.Warning,
                            title = stringResource(R.string.web_session_userscript_unsupported),
                            message =
                                state.supportState.reason
                                    ?: stringResource(
                                        R.string.web_session_userscript_unsupported_summary,
                                    ),
                            tone = KiyoriSemanticTone.RED,
                        )
                    } else if (visibleCurrentPageEntries.isEmpty()) {
                        WebSessionEmptyState(
                            icon = if (searchQuery.isBlank()) Icons.Filled.Extension else Icons.Filled.Search,
                            title =
                                if (searchQuery.isBlank()) {
                                    stringResource(R.string.web_session_plugins_current_page_empty)
                                } else {
                                    stringResource(R.string.web_session_userscript_search_empty)
                                },
                            tone = KiyoriSemanticTone.PURPLE,
                        )
                    } else {
                        BrowserPluginPageEntryList(
                            entries = visibleCurrentPageEntries,
                            searchActive = searchQuery.isNotBlank(),
                            onInvokeMenuCommand = onInvokeMenuCommand,
                        )
                    }
                }

                WebSessionUserscriptWorkbenchTab.INSTALLED -> {
                    val newDrafts =
                        state.drafts.filter { draft -> draft.userscriptId == null }
                    if (newDrafts.isNotEmpty()) {
                        WebSessionSectionLabel(
                            text = stringResource(R.string.web_session_userscript_drafts),
                            tone = KiyoriSemanticTone.PURPLE,
                        )
                        newDrafts.forEach { draft ->
                            UserscriptDraftRow(
                                draft = draft,
                                onClick = { onOpenDraftEditor(draft.draftId) },
                            )
                        }
                        WebSessionSectionLabel(
                            text = stringResource(R.string.web_session_userscript_library),
                            tone = KiyoriSemanticTone.PURPLE,
                        )
                    }
                    if (visibleScripts.isEmpty()) {
                        WebSessionEmptyState(
                            icon = if (searchQuery.isBlank()) Icons.Filled.Description else Icons.Filled.Search,
                            title =
                                if (searchQuery.isBlank()) {
                                    stringResource(R.string.web_session_userscript_none)
                                } else {
                                    stringResource(R.string.web_session_userscript_search_empty)
                                },
                            tone = KiyoriSemanticTone.PURPLE,
                        )
                    } else {
                        visibleScripts.forEach { script ->
                            UserscriptInstalledRow(
                                script = script,
                                status = state.currentPageStatuses[script.id],
                                currentPageUrl = state.currentPageUrl,
                                runtimeAllowed = state.userScriptsAllowed,
                                runtimeSupported = state.supportState.isSupported,
                                runtimeUnsupportedReason = state.supportState.reason,
                                selected = script.id in selectedScriptIds,
                                selectionMode = selectedScriptIds.isNotEmpty(),
                                checkingUpdate = script.id in state.checkingUpdateIds,
                                onClick = {
                                    if (selectedScriptIds.isEmpty()) {
                                        onOpenScriptDetail(script.id)
                                    } else {
                                        selectedScriptIds =
                                            toggleSelectedId(selectedScriptIds, script.id)
                                    }
                                },
                                onLongClick = {
                                    selectedScriptIds =
                                        toggleSelectedId(selectedScriptIds, script.id)
                                },
                                onSetEnabled = { enabled ->
                                    onSetScriptEnabled(script.id, enabled)
                                },
                                onEdit = { onOpenExistingEditor(script.id) },
                                onCheckUpdate = { onCheckUpdate(script.id) },
                                onDelete = { pendingSingleDeleteId = script.id },
                            )
                        }
                    }
                }

                WebSessionUserscriptWorkbenchTab.UPDATES -> {
                    UserscriptUpdateToolbar(
                        isChecking = state.isCheckingAllUpdates,
                        isApplying = state.isApplyingSafeUpdates,
                        safeCount =
                            state.updateCandidates.values.count(
                                UserscriptUpdateCandidate::safeToAutoApply,
                            ),
                        onCheckAll = onCheckAllUpdates,
                        onApplyAll = onApplyAllSafeUpdates,
                    )
                    state.pendingInstall?.let { preview ->
                        UserscriptPendingInstallCard(
                            preview = preview,
                            onConfirmInstall = onConfirmInstall,
                            onCancelInstall = onCancelInstall,
                        )
                    }
                    val candidates =
                        state.updateCandidates.values.filter { candidate ->
                            val script =
                                state.installedScripts.firstOrNull { item ->
                                    item.id == candidate.scriptId
                                }
                            searchQuery.isBlank() ||
                                script?.let {
                                    BrowserPluginCenterFacade.matchesUserscriptSearch(it, searchQuery)
                                } == true
                        }
                    if (candidates.isEmpty() && state.pendingInstall == null) {
                        WebSessionEmptyState(
                            icon = Icons.Filled.Refresh,
                            title = stringResource(R.string.web_session_userscript_updates_empty),
                            message = stringResource(R.string.web_session_userscript_updates_empty_summary),
                            tone = KiyoriSemanticTone.PURPLE,
                        )
                    } else {
                        candidates.forEach { candidate ->
                            UserscriptUpdateCard(
                                candidate = candidate,
                                script =
                                    state.installedScripts.firstOrNull { script ->
                                        script.id == candidate.scriptId
                                    },
                                onApply = { onApplyUpdate(candidate.scriptId) },
                            )
                        }
                    }
                }

                WebSessionUserscriptWorkbenchTab.LOGS -> {
                    UserscriptLogToolbar(
                        logCount = state.recentLogs.size,
                        exporting = exportingLogs,
                        onCopyAll = {
                            val report =
                                buildUserscriptLogReport(
                                    logs = state.recentLogs,
                                    scripts = state.installedScripts,
                                )
                            copyUserscriptText(context, "Kiyori 用户脚本日志", report)
                            Toast.makeText(
                                context,
                                resources.getString(
                                    R.string.web_session_userscript_logs_copied,
                                    state.recentLogs.size,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onExportAll = {
                            val report =
                                buildUserscriptLogReport(
                                    logs = state.recentLogs,
                                    scripts = state.installedScripts,
                                )
                            exportingLogs = true
                            coroutineScope.launch {
                                UserscriptLogExportHelper.export(context, report)
                                    .onSuccess { path ->
                                        Toast.makeText(
                                            context,
                                            resources.getString(
                                                R.string.web_session_userscript_logs_exported,
                                                path,
                                            ),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            resources.getString(
                                                R.string.web_session_userscript_logs_export_failed,
                                                error.message ?: error.javaClass.simpleName,
                                            ),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                exportingLogs = false
                            }
                        },
                    )
                    UserscriptLogFilters(
                        selected = logFilter,
                        onSelected = { logFilter = it },
                    )
                    if (visibleLogs.isEmpty()) {
                        WebSessionEmptyState(
                            icon = Icons.Filled.Description,
                            title = stringResource(R.string.web_session_userscript_logs_empty),
                            tone = KiyoriSemanticTone.PURPLE,
                        )
                    } else {
                        visibleLogs.forEach { log ->
                            UserscriptLogCard(
                                log = log,
                                scriptName =
                                    log.userscriptId?.let { scriptId ->
                                        state.installedScripts
                                            .firstOrNull { script -> script.id == scriptId }
                                            ?.name
                                    },
                            )
                        }
                    }
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
                onInstallFromUrl(normalizedUrl)
            },
        )
    }

    if (deleteSelectionPrompt) {
        AlertDialog(
            onDismissRequest = { deleteSelectionPrompt = false },
            title = { Text(stringResource(R.string.web_session_userscript_delete_selected_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.web_session_userscript_delete_selected_message,
                        selectedScriptIds.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteScripts(selectedScriptIds)
                        selectedScriptIds = emptySet()
                        deleteSelectionPrompt = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.web_session_userscript_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSelectionPrompt = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingSingleDeleteId?.let { scriptId ->
        val scriptName =
            state.installedScripts.firstOrNull { script -> script.id == scriptId }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingSingleDeleteId = null },
            title = { Text(stringResource(R.string.web_session_userscript_delete)) },
            text = {
                Text(
                    listOf(
                        scriptName,
                        stringResource(R.string.web_session_userscript_delete_detail_message),
                    ).filter(String::isNotBlank).joinToString("\n\n"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSingleDeleteId = null
                        onDeleteScript(scriptId)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.web_session_userscript_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSingleDeleteId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun UserscriptDraftRow(
    draft: UserscriptDraft,
    onClick: () -> Unit,
) {
    val draftName =
        remember(draft.sourceHash, draft.source) {
            runCatching {
                UserscriptMetadataEditorPolicy.read(draft.source).name
            }.getOrNull()
        }
    WebSessionItemCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KiyoriSemanticIconBadge(
                imageVector = Icons.Filled.Edit,
                tone = KiyoriSemanticTone.ORANGE,
                contentDescription = null,
                containerSize = 36.dp,
                iconSize = 19.dp,
                shape = CircleShape,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = draftName ?: stringResource(R.string.web_session_userscript_new),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.web_session_userscript_private_draft),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UserscriptSelectionHeader(
    selectedCount: Int,
    onClose: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onDelete: () -> Unit,
) {
    WebSessionDrawerHeader(
        title =
            stringResource(
                R.string.web_session_userscript_selected_count,
                selectedCount,
            ),
        leadingIcon = Icons.Filled.Check,
        tone = KiyoriSemanticTone.PURPLE,
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cancel),
                )
            }
        },
        actions = {
            TextButton(onClick = onEnable) {
                Text(stringResource(R.string.web_session_userscript_enable))
            }
            TextButton(onClick = onDisable) {
                Text(stringResource(R.string.web_session_userscript_disable))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.web_session_userscript_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun UserscriptPermissionBanner(
    onEnable: () -> Unit,
) {
    WebSessionItemCard(
        highlighted = true,
        highlightTone = KiyoriSemanticTone.PURPLE,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KiyoriSemanticIconBadge(
                imageVector = Icons.Filled.Extension,
                tone = KiyoriSemanticTone.PURPLE,
                contentDescription = null,
                containerSize = 34.dp,
                iconSize = 18.dp,
                shape = CircleShape,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.web_session_plugins_runtime_blocked),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.web_session_userscript_permission_install_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onEnable) {
                Text(stringResource(R.string.web_session_userscript_enable))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserscriptInstalledRow(
    script: UserscriptListItem,
    status: UserscriptPageRuntimeStatus?,
    currentPageUrl: String?,
    runtimeAllowed: Boolean,
    runtimeSupported: Boolean,
    runtimeUnsupportedReason: String?,
    selected: Boolean,
    selectionMode: Boolean,
    checkingUpdate: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val resolvedStatus =
        status
            ?: UserscriptPageStatusPolicy.resolve(
                script = script,
                userScriptsAllowed = runtimeAllowed,
                pageUrl = currentPageUrl,
                runtimeSupported = runtimeSupported,
                runtimeUnsupportedReason = runtimeUnsupportedReason,
            )
    WebSessionItemCard(
        highlighted = selected,
        highlightTone = KiyoriSemanticTone.PURPLE,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KiyoriSemanticIconBadge(
                imageVector = if (selected) Icons.Filled.Check else Icons.Filled.Description,
                tone = KiyoriSemanticTone.PURPLE,
                contentDescription = null,
                containerSize = 36.dp,
                iconSize = 19.dp,
                shape = CircleShape,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = script.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(7.dp)
                                .background(userscriptStatusColor(resolvedStatus.state), CircleShape),
                    )
                    Text(
                        text =
                            listOf(
                                userscriptStatusLabel(resolvedStatus.state),
                                "v${script.version}",
                                script.sourceDisplay.orEmpty(),
                            ).filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                resolvedStatus.detail?.takeIf(String::isNotBlank)?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (resolvedStatus.state == UserscriptPageRuntimeState.ERROR) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!selectionMode) {
                Switch(
                    checked = script.enabled,
                    onCheckedChange = onSetEnabled,
                    enabled = script.blockedReasons.isEmpty(),
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.more),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = WebSessionBrowserPopupShape,
                        containerColor = MaterialTheme.colorScheme.surface,
                        shadowElevation = WebSessionBrowserPopupElevation,
                    ) {
                        WebSessionBrowserDropdownItem(
                            title = stringResource(R.string.web_session_userscript_edit),
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        WebSessionBrowserDropdownItem(
                            title =
                                if (checkingUpdate) {
                                    stringResource(R.string.web_session_userscript_checking_update)
                                } else {
                                    stringResource(R.string.web_session_userscript_check_update)
                                },
                            onClick = {
                                menuExpanded = false
                                if (!checkingUpdate) {
                                    onCheckUpdate()
                                }
                            },
                        )
                        WebSessionBrowserDropdownItem(
                            title = stringResource(R.string.web_session_userscript_delete),
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserscriptUpdateToolbar(
    isChecking: Boolean,
    isApplying: Boolean,
    safeCount: Int,
    onCheckAll: () -> Unit,
    onApplyAll: () -> Unit,
) {
    WebSessionItemCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalButton(
                onClick = onCheckAll,
                enabled = !isChecking && !isApplying,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (isChecking) {
                        stringResource(R.string.web_session_userscript_checking_update)
                    } else {
                        stringResource(R.string.web_session_userscript_check_all_updates)
                    },
                )
            }
            Button(
                onClick = onApplyAll,
                enabled = safeCount > 0 && !isChecking && !isApplying,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (isApplying) {
                        stringResource(R.string.web_session_userscript_updating)
                    } else {
                        stringResource(R.string.web_session_userscript_update_all_safe, safeCount)
                    },
                )
            }
        }
    }
}

@Composable
private fun UserscriptUpdateCard(
    candidate: UserscriptUpdateCandidate,
    script: UserscriptListItem?,
    onApply: () -> Unit,
) {
    val diff = candidate.diff
    WebSessionItemCard(
        highlighted = !candidate.safeToAutoApply,
        highlightTone =
            if (candidate.safeToAutoApply) {
                KiyoriSemanticTone.GREEN
            } else {
                KiyoriSemanticTone.ORANGE
            },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KiyoriSemanticIconBadge(
                    imageVector = Icons.Filled.Refresh,
                    tone =
                        if (candidate.safeToAutoApply) {
                            KiyoriSemanticTone.GREEN
                        } else {
                            KiyoriSemanticTone.ORANGE
                        },
                    contentDescription = null,
                    containerSize = 36.dp,
                    iconSize = 19.dp,
                    shape = CircleShape,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = script?.name ?: candidate.preview.metadata.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            "${candidate.currentVersion} → ${candidate.preview.metadata.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text =
                        if (candidate.safeToAutoApply) {
                            stringResource(R.string.web_session_userscript_update_safe)
                        } else {
                            stringResource(R.string.web_session_userscript_update_confirmation_required)
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (candidate.safeToAutoApply) {
                            KiyoriSemanticTone.GREEN.resolveColors().icon
                        } else {
                            KiyoriSemanticTone.ORANGE.resolveColors().icon
                        },
                )
            }
            val changeSummary =
                run {
                    val grantsLabel = stringResource(R.string.web_session_userscript_grants)
                    val connectsLabel = stringResource(R.string.web_session_userscript_connects)
                    val matchesLabel = stringResource(R.string.web_session_userscript_matches)
                    val excludesLabel = stringResource(R.string.web_session_userscript_excludes)
                buildList {
                    if (diff.addedGrants.isNotEmpty()) {
                            add("$grantsLabel + ${diff.addedGrants.joinToString()}")
                    }
                    if (diff.addedConnects.isNotEmpty()) {
                            add("$connectsLabel + ${diff.addedConnects.joinToString()}")
                    }
                    if (diff.addedPageRules.isNotEmpty()) {
                            add("$matchesLabel + ${diff.addedPageRules.joinToString()}")
                        }
                        if (diff.removedPageRules.isNotEmpty()) {
                            add("$matchesLabel − ${diff.removedPageRules.joinToString()}")
                    }
                    if (diff.removedExclusions.isNotEmpty()) {
                            add("$excludesLabel − ${diff.removedExclusions.joinToString()}")
                    }
                    if (diff.sourceIdentityChanged) {
                        add(stringResource(R.string.web_session_userscript_update_source_changed))
                    }
                }
                }
            if (changeSummary.isNotEmpty()) {
                Text(
                    text = changeSummary.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            UserscriptPermissionSnapshot(preview = candidate.preview)
            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (candidate.safeToAutoApply) {
                        stringResource(R.string.web_session_userscript_update_action)
                    } else {
                        stringResource(R.string.web_session_userscript_review_update)
                    },
                )
            }
        }
    }
}

@Composable
private fun UserscriptPendingInstallCard(
    preview: UserscriptInstallPreview,
    onConfirmInstall: () -> Unit,
    onCancelInstall: () -> Unit,
) {
    WebSessionItemCard(
        highlighted = true,
        highlightTone = KiyoriSemanticTone.PURPLE,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = preview.metadata.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text =
                    listOf(
                        "v${preview.metadata.version}",
                        preview.sourceDisplay.orEmpty(),
                    ).filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            UserscriptPermissionSnapshot(preview = preview)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConfirmInstall,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (preview.isUpdate) {
                                R.string.web_session_userscript_update_action
                            } else {
                                R.string.web_session_userscript_confirm_install
                            },
                        ),
                    )
                }
                TextButton(
                    onClick = onCancelInstall,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun UserscriptPermissionSnapshot(
    preview: UserscriptInstallPreview,
) {
    val metadata = preview.metadata
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            UserscriptPermissionLine(
                label = stringResource(R.string.web_session_userscript_execution_world),
                value =
                    when (preview.executionWorld) {
                        UserscriptExecutionWorld.PAGE ->
                            stringResource(R.string.web_session_userscript_execution_world_page)
                        UserscriptExecutionWorld.ISOLATED ->
                            stringResource(R.string.web_session_userscript_execution_world_isolated)
                        null -> "-"
                    },
            )
            if (preview.unsafeWindowMode != UserscriptUnsafeWindowMode.NONE) {
                UserscriptPermissionLine(
                    label = stringResource(R.string.web_session_userscript_page_context_access),
                    value =
                        when (preview.unsafeWindowMode) {
                            UserscriptUnsafeWindowMode.DIRECT_PAGE ->
                                stringResource(R.string.web_session_userscript_page_context_direct)
                            UserscriptUnsafeWindowMode.ISOLATED_PAGE_BRIDGE ->
                                stringResource(R.string.web_session_userscript_page_context_bridge)
                            UserscriptUnsafeWindowMode.NONE -> "-"
                        },
                )
            }
            UserscriptPermissionLine(
                label = stringResource(R.string.web_session_userscript_grants),
                value = summarizeUserscriptValues(metadata.grants),
            )
            UserscriptPermissionLine(
                label = stringResource(R.string.web_session_userscript_connects),
                value = summarizeUserscriptValues(metadata.connects),
            )
            UserscriptPermissionLine(
                label = stringResource(R.string.web_session_userscript_matches),
                value = summarizeUserscriptValues(metadata.matches + metadata.includes),
            )
            if (preview.unknownGrants.isNotEmpty()) {
                UserscriptPermissionLine(
                    label = stringResource(R.string.web_session_userscript_unknown_grants_label),
                    value = preview.unknownGrants.joinToString(),
                    error = true,
                )
            }
            if (preview.blockedReasons.isNotEmpty()) {
                UserscriptPermissionLine(
                    label = stringResource(R.string.web_session_userscript_blocked_reasons_label),
                    value = preview.blockedReasons.joinToString(),
                    error = true,
                )
            }
        }
    }
}

@Composable
private fun UserscriptPermissionLine(
    label: String,
    value: String,
    error: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color =
                if (error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color =
                if (error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun summarizeUserscriptValues(
    values: List<String>,
    visibleCount: Int = 3,
): String {
    if (values.isEmpty()) {
        return "-"
    }
    val visible = values.take(visibleCount)
    val remaining = values.size - visible.size
    return buildString {
        append(visible.joinToString())
        if (remaining > 0) {
            append(" · +")
            append(remaining)
        }
    }
}

@Composable
private fun UserscriptLogToolbar(
    logCount: Int,
    exporting: Boolean,
    onCopyAll: () -> Unit,
    onExportAll: () -> Unit,
) {
    WebSessionItemCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.web_session_userscript_logs_retained_count,
                        logCount,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onCopyAll,
                    enabled = logCount > 0 && !exporting,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(stringResource(R.string.web_session_userscript_logs_copy_all))
                }
                Button(
                    onClick = onExportAll,
                    enabled = logCount > 0 && !exporting,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(
                            if (exporting) {
                                R.string.web_session_userscript_logs_exporting
                            } else {
                                R.string.web_session_userscript_logs_export_all
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun UserscriptLogFilters(
    selected: UserscriptLogFilter,
    onSelected: (UserscriptLogFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UserscriptLogFilter.entries.forEach { filter ->
            WebSessionFilterChip(
                label =
                    when (filter) {
                        UserscriptLogFilter.ALL -> stringResource(R.string.web_session_filter_all)
                        UserscriptLogFilter.INFO -> stringResource(R.string.web_session_userscript_log_info)
                        UserscriptLogFilter.WARNING -> stringResource(R.string.web_session_userscript_log_warning)
                        UserscriptLogFilter.ERROR -> stringResource(R.string.web_session_userscript_log_error)
                    },
                selected = filter == selected,
                tone = KiyoriSemanticTone.PURPLE,
                onClick = { onSelected(filter) },
            )
        }
    }
}

@Composable
internal fun UserscriptLogCard(
    log: UserscriptLogItem,
    scriptName: String?,
) {
    val context = LocalContext.current
    val logsTitle = stringResource(R.string.web_session_userscript_logs)
    var detailVisible by remember(log.id) { mutableStateOf(false) }
    val copyLog = {
        copyUserscriptText(
            context,
            scriptName ?: logsTitle,
            buildUserscriptLogEntryReport(log, scriptName),
        )
        Toast.makeText(
            context,
            R.string.web_session_userscript_log_copied,
            Toast.LENGTH_SHORT,
        ).show()
    }
    val tone =
        when {
            log.level.equals("error", ignoreCase = true) -> KiyoriSemanticTone.RED
            log.level.equals("warn", ignoreCase = true) ||
                log.level.equals("warning", ignoreCase = true) -> KiyoriSemanticTone.ORANGE
            else -> KiyoriSemanticTone.PURPLE
        }
    WebSessionItemCard {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { detailVisible = true },
                        onLongClick = copyLog,
                    )
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier.padding(top = 5.dp).size(8.dp).background(tone.resolveColors().icon, CircleShape),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text =
                        listOfNotNull(
                            scriptName,
                            log.level.uppercase(),
                        ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                log.pageUrl?.takeIf(String::isNotBlank)?.let { pageUrl ->
                    Text(
                        text = pageUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (detailVisible) {
        AlertDialog(
            onDismissRequest = { detailVisible = false },
            title = {
                Text(
                    scriptName
                        ?: stringResource(R.string.web_session_userscript_log_details),
                )
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    UserscriptLogDetailLine(
                        label = stringResource(R.string.web_session_userscript_log_level),
                        value = log.level.uppercase(),
                    )
                    UserscriptLogDetailLine(
                        label = stringResource(R.string.web_session_userscript_log_time),
                        value = formatUserscriptLogTimestamp(log.createdAt),
                    )
                    log.pageUrl?.takeIf(String::isNotBlank)?.let { pageUrl ->
                        UserscriptLogDetailLine(
                            label = stringResource(R.string.web_session_userscript_log_page),
                            value = pageUrl,
                        )
                    }
                    Text(
                        text = stringResource(R.string.web_session_userscript_log_message),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    SelectionContainer {
                        Text(
                            text = log.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = copyLog) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(stringResource(R.string.copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { detailVisible = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
private fun UserscriptLogDetailLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun copyUserscriptText(
    context: Context,
    label: String,
    text: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun formatUserscriptLogTimestamp(timestamp: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))

@Composable
internal fun userscriptStatusLabel(state: UserscriptPageRuntimeState): String =
    when (state) {
        UserscriptPageRuntimeState.NO_ACTIVE_PAGE ->
            stringResource(R.string.web_session_userscript_status_no_active_page)
        UserscriptPageRuntimeState.DISABLED ->
            stringResource(R.string.web_session_userscript_status_disabled)
        UserscriptPageRuntimeState.PERMISSION_REQUIRED ->
            stringResource(R.string.web_session_userscript_status_permission_required)
        UserscriptPageRuntimeState.UNSUPPORTED ->
            stringResource(R.string.web_session_userscript_status_unsupported)
        UserscriptPageRuntimeState.NOT_MATCHED ->
            stringResource(R.string.web_session_userscript_status_not_matched)
        UserscriptPageRuntimeState.MATCHED ->
            stringResource(R.string.web_session_userscript_status_matched)
        UserscriptPageRuntimeState.QUEUED ->
            stringResource(R.string.web_session_userscript_status_queued)
        UserscriptPageRuntimeState.RUNNING ->
            stringResource(R.string.web_session_userscript_status_running)
        UserscriptPageRuntimeState.SUCCESS ->
            stringResource(R.string.web_session_userscript_status_success)
        UserscriptPageRuntimeState.ERROR ->
            stringResource(R.string.web_session_userscript_status_error)
    }

@Composable
internal fun userscriptStatusColor(state: UserscriptPageRuntimeState) =
    when (state) {
        UserscriptPageRuntimeState.NO_ACTIVE_PAGE,
        UserscriptPageRuntimeState.DISABLED,
        UserscriptPageRuntimeState.NOT_MATCHED -> MaterialTheme.colorScheme.outline
        UserscriptPageRuntimeState.PERMISSION_REQUIRED,
        UserscriptPageRuntimeState.MATCHED,
        UserscriptPageRuntimeState.QUEUED -> MaterialTheme.colorScheme.tertiary
        UserscriptPageRuntimeState.UNSUPPORTED,
        UserscriptPageRuntimeState.ERROR -> MaterialTheme.colorScheme.error
        UserscriptPageRuntimeState.RUNNING -> MaterialTheme.colorScheme.primary
        UserscriptPageRuntimeState.SUCCESS -> MaterialTheme.colorScheme.secondary
    }

@Composable
internal fun UserscriptInstallFromUrlDialog(
    value: String,
    onValueChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val normalizedUrl = value.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.web_session_userscript_install_from_url)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChanged,
                singleLine = true,
                placeholder = { Text("https://example.com/script.user.js") },
                supportingText = {
                    if (value.isNotBlank() && !isSupportedUserscriptInstallUrl(value)) {
                        Text(stringResource(R.string.web_session_userscript_invalid_url))
                    }
                },
                isError = value.isNotBlank() && !isSupportedUserscriptInstallUrl(value),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(normalizedUrl) },
                enabled = isSupportedUserscriptInstallUrl(normalizedUrl),
            ) {
                Text(stringResource(R.string.web_session_userscript_install_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun toggleSelectedId(
    selected: Set<Long>,
    scriptId: Long,
): Set<Long> =
    if (scriptId in selected) {
        selected - scriptId
    } else {
        selected + scriptId
    }
