package com.ai.assistance.operit.ui.features.websession.browser

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptExecutionWorld
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeStatus
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageStatusPolicy
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptUnsafeWindowMode
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.UserscriptDetailUiState
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private enum class UserscriptDetailTab {
    OVERVIEW,
    RULES,
    PERMISSIONS,
    DEPENDENCIES,
    SOURCE,
    LOGS,
    VERSIONS,
}

@Composable
internal fun WebSessionUserscriptDetail(
    scriptId: Long,
    state: com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState,
    onLoad: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onSetEnabled: (Long, Boolean) -> Unit,
    onCheckUpdate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val script = state.installedScripts.firstOrNull { item -> item.id == scriptId }
    val detail = state.details[scriptId]
    var selectedTab by rememberSaveable(scriptId) { mutableStateOf(UserscriptDetailTab.OVERVIEW) }
    var deletePromptVisible by remember { mutableStateOf(false) }
    var exportingSource by remember { mutableStateOf(false) }

    LaunchedEffect(scriptId) {
        onLoad(scriptId)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        WebSessionDrawerHeader(
            title = script?.name ?: stringResource(R.string.web_session_userscript_detail),
            leadingIcon = Icons.Filled.Description,
            tone = KiyoriSemanticTone.PURPLE,
            countText = script?.version?.let { version -> "v$version" },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { onEdit(scriptId) },
                    enabled = script != null,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.web_session_userscript_edit),
                    )
                }
                IconButton(
                    onClick = { onCheckUpdate(scriptId) },
                    enabled = script != null && scriptId !in state.checkingUpdateIds,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.web_session_userscript_check_update),
                    )
                }
            },
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserscriptDetailTab.entries.forEach { tab ->
                WebSessionFilterChip(
                    label = userscriptDetailTabLabel(tab),
                    selected = selectedTab == tab,
                    tone = KiyoriSemanticTone.PURPLE,
                    onClick = { selectedTab = tab },
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
            when {
                script == null -> {
                    WebSessionEmptyState(
                        icon = Icons.Filled.Warning,
                        title = stringResource(R.string.web_session_userscript_missing),
                        tone = KiyoriSemanticTone.RED,
                    )
                }
                detail?.isLoading == true || detail == null -> {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(24.dp),
                            color = KiyoriSemanticTone.PURPLE.resolveColors().icon,
                        )
                }
                detail.error != null -> {
                    WebSessionEmptyState(
                        icon = Icons.Filled.Warning,
                        title = stringResource(R.string.web_session_userscript_detail_load_failed),
                        message = detail.error,
                        tone = KiyoriSemanticTone.RED,
                    )
                }
                else -> {
                    UserscriptDetailContent(
                        tab = selectedTab,
                        script = script,
                        detail = detail,
                        runtimeStatus = state.currentPageStatuses[scriptId],
                        runtimeAllowed = state.userScriptsAllowed,
                        currentPageUrl = state.currentPageUrl,
                        runtimeSupported = state.supportState.isSupported,
                        runtimeUnsupportedReason = state.supportState.reason,
                        logs =
                            state.recentLogs.filter { log ->
                                log.userscriptId == scriptId
                            },
                        onSetEnabled = { enabled -> onSetEnabled(scriptId, enabled) },
                        onDelete = { deletePromptVisible = true },
                        exportingSource = exportingSource,
                        onExportSource = {
                            val currentScript = requireNotNull(script)
                            val source = requireNotNull(detail.activeSource)
                            exportingSource = true
                            coroutineScope.launch {
                                UserscriptSourceExportHelper
                                    .export(
                                        context = context,
                                        scriptName = currentScript.name,
                                        source = source,
                                    )
                                    .onSuccess { path ->
                                        Toast.makeText(
                                            context,
                                            "${context.getString(R.string.export_success)}\n$path",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                    .onFailure { error ->
                                        AppLogger.e(
                                            "WebSessionUserscriptDetail",
                                            "Failed to export userscript source for $scriptId",
                                            error,
                                        )
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.toast_operation_failed,
                                                error.toString(),
                                            ),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                exportingSource = false
                            }
                        },
                    )
                }
            }
        }
    }

    if (deletePromptVisible) {
        AlertDialog(
            onDismissRequest = { deletePromptVisible = false },
            title = { Text(stringResource(R.string.web_session_userscript_delete)) },
            text = {
                Text(stringResource(R.string.web_session_userscript_delete_detail_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletePromptVisible = false
                        onDelete(scriptId)
                        onNavigateBack()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.web_session_userscript_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletePromptVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun UserscriptDetailContent(
    tab: UserscriptDetailTab,
    script: UserscriptListItem,
    detail: UserscriptDetailUiState,
    runtimeStatus: UserscriptPageRuntimeStatus?,
    runtimeAllowed: Boolean,
    currentPageUrl: String?,
    runtimeSupported: Boolean,
    runtimeUnsupportedReason: String?,
    logs: List<com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptLogItem>,
    onSetEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
    exportingSource: Boolean,
    onExportSource: () -> Unit,
) {
    when (tab) {
        UserscriptDetailTab.OVERVIEW -> {
            val status =
                runtimeStatus
                    ?: UserscriptPageStatusPolicy.resolve(
                        script = script,
                        userScriptsAllowed = runtimeAllowed,
                        pageUrl = currentPageUrl,
                        runtimeSupported = runtimeSupported,
                        runtimeUnsupportedReason = runtimeUnsupportedReason,
                    )
            WebSessionItemCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = script.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = userscriptStatusLabel(status.state),
                                style = MaterialTheme.typography.bodySmall,
                                color = userscriptStatusColor(status.state),
                            )
                            status.detail?.takeIf(String::isNotBlank)?.let { detail ->
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = script.enabled,
                            onCheckedChange = onSetEnabled,
                            enabled = script.blockedReasons.isEmpty(),
                        )
                    }
                    script.description?.takeIf(String::isNotBlank)?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DetailValue(stringResource(R.string.web_session_userscript_version), script.version)
                    DetailValue(
                        stringResource(R.string.web_session_userscript_source),
                        script.sourceDisplay ?: script.sourceUrl ?: "-",
                    )
                    DetailValue(
                        stringResource(R.string.web_session_userscript_installed_at),
                        formatTimestamp(script.installedAt),
                    )
                    DetailValue(
                        stringResource(R.string.web_session_userscript_updated_at),
                        formatTimestamp(script.updatedAt),
                    )
                    detail.draft?.let { draft ->
                        Text(
                            text =
                                stringResource(
                                    R.string.web_session_userscript_unapplied_draft,
                                    formatTimestamp(draft.updatedAt),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text(stringResource(R.string.web_session_userscript_delete))
                    }
                }
            }
        }

        UserscriptDetailTab.RULES -> {
            DetailListCard(stringResource(R.string.web_session_userscript_matches), script.matches + script.includes)
            DetailListCard(stringResource(R.string.web_session_userscript_excludes), script.excludes + script.excludeMatches)
            DetailValueCard(
                stringResource(R.string.web_session_userscript_run_at),
                script.runAt.rawValue,
            )
            DetailValueCard(
                stringResource(R.string.web_session_userscript_inject_into),
                script.injectInto.rawValue,
            )
            script.runIn?.takeIf(String::isNotBlank)?.let { runIn ->
                DetailValueCard(
                    stringResource(R.string.web_session_userscript_run_in),
                    runIn,
                )
            }
            DetailValueCard(
                stringResource(R.string.web_session_userscript_no_frames),
                if (script.noFrames) {
                    stringResource(R.string.yes)
                } else {
                    stringResource(R.string.no)
                },
            )
        }

        UserscriptDetailTab.PERMISSIONS -> {
            script.executionWorld?.let { world ->
                DetailValueCard(
                    stringResource(R.string.web_session_userscript_execution_world),
                    when (world) {
                        UserscriptExecutionWorld.PAGE ->
                            stringResource(R.string.web_session_userscript_execution_world_page)
                        UserscriptExecutionWorld.ISOLATED ->
                            stringResource(R.string.web_session_userscript_execution_world_isolated)
                    },
                )
            }
            if (script.unsafeWindowMode != UserscriptUnsafeWindowMode.NONE) {
                DetailValueCard(
                    stringResource(R.string.web_session_userscript_page_context_access),
                    when (script.unsafeWindowMode) {
                        UserscriptUnsafeWindowMode.DIRECT_PAGE ->
                            stringResource(R.string.web_session_userscript_page_context_direct)
                        UserscriptUnsafeWindowMode.ISOLATED_PAGE_BRIDGE ->
                            stringResource(R.string.web_session_userscript_page_context_bridge)
                        UserscriptUnsafeWindowMode.NONE -> "-"
                    },
                )
            }
            DetailListCard(stringResource(R.string.web_session_userscript_grants), script.grants)
            DetailListCard(stringResource(R.string.web_session_userscript_connects), script.connects)
            if (script.unknownGrants.isNotEmpty()) {
                DetailListCard(
                    stringResource(R.string.web_session_userscript_unknown_grants_label),
                    script.unknownGrants,
                    error = true,
                )
            }
            if (script.blockedReasons.isNotEmpty()) {
                DetailListCard(
                    stringResource(R.string.web_session_userscript_blocked_reasons_label),
                    script.blockedReasons,
                    error = true,
                )
            }
        }

        UserscriptDetailTab.DEPENDENCIES -> {
            DetailListCard(
                stringResource(R.string.web_session_userscript_requires),
                script.requires.map { entry -> entry.url },
            )
            DetailListCard(
                stringResource(R.string.web_session_userscript_resources),
                script.resources.map { entry -> "${entry.name} → ${entry.url}" },
            )
        }

        UserscriptDetailTab.SOURCE -> {
            WebSessionItemCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = onExportSource,
                        enabled = detail.activeSource?.isNotBlank() == true && !exportingSource,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileDownload,
                            contentDescription = null,
                        )
                        Text(stringResource(R.string.export))
                    }
                    SelectionContainer {
                        Text(
                            text = detail.activeSource.orEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        UserscriptDetailTab.LOGS -> {
            if (logs.isEmpty()) {
                WebSessionEmptyState(
                    icon = Icons.Filled.Description,
                    title = stringResource(R.string.web_session_userscript_logs_empty),
                    tone = KiyoriSemanticTone.PURPLE,
                )
            } else {
                logs.forEach { log ->
                    UserscriptLogCard(log = log, scriptName = script.name)
                }
            }
        }

        UserscriptDetailTab.VERSIONS -> {
            if (detail.revisions.isEmpty()) {
                WebSessionEmptyState(
                    icon = Icons.Filled.Code,
                    title = stringResource(R.string.web_session_userscript_versions_empty),
                    tone = KiyoriSemanticTone.PURPLE,
                )
            } else {
                detail.revisions.forEach { revision ->
                    WebSessionItemCard(
                        highlighted = revision.active,
                        highlightTone = KiyoriSemanticTone.PURPLE,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.web_session_userscript_revision_title,
                                        revision.revisionNumber,
                                        revision.version,
                                    ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text =
                                    listOf(
                                        revision.sourceHash.take(12),
                                        formatTimestamp(revision.createdAt),
                                        if (revision.active) {
                                            stringResource(R.string.web_session_userscript_revision_active)
                                        } else {
                                            ""
                                        },
                                    ).filter(String::isNotBlank).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailListCard(
    title: String,
    values: List<String>,
    error: Boolean = false,
) {
    DetailValueCard(
        title = title,
        value = values.joinToString("\n").ifBlank { "-" },
        error = error,
    )
}

@Composable
private fun DetailValueCard(
    title: String,
    value: String,
    error: Boolean = false,
) {
    WebSessionItemCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            DetailValue(title, value, error)
        }
    }
}

@Composable
private fun DetailValue(
    title: String,
    value: String,
    error: Boolean = false,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
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
    )
}

@Composable
private fun userscriptDetailTabLabel(tab: UserscriptDetailTab): String =
    when (tab) {
        UserscriptDetailTab.OVERVIEW -> stringResource(R.string.web_session_userscript_detail_overview)
        UserscriptDetailTab.RULES -> stringResource(R.string.web_session_userscript_detail_rules)
        UserscriptDetailTab.PERMISSIONS -> stringResource(R.string.web_session_userscript_detail_permissions)
        UserscriptDetailTab.DEPENDENCIES -> stringResource(R.string.web_session_userscript_detail_dependencies)
        UserscriptDetailTab.SOURCE -> stringResource(R.string.web_session_userscript_detail_source)
        UserscriptDetailTab.LOGS -> stringResource(R.string.web_session_userscript_logs)
        UserscriptDetailTab.VERSIONS -> stringResource(R.string.web_session_userscript_detail_versions)
    }

private fun formatTimestamp(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
