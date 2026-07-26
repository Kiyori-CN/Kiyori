package com.ai.assistance.operit.ui.features.websession.browser

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadFilter
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadRenameMode
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSettingsStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadUiState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserDownloadRenameInput
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.buildBrowserDownloadRenameTarget
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.filterBrowserDownloadItems
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.formatBytes

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WebSessionDownloadSheet(
    uiState: BrowserDownloadUiState,
    onFilterChange: (BrowserDownloadFilter) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onDeleteDownload: (String, Boolean) -> Unit,
    onOpenDownloadedFile: (String) -> Unit,
    onOpenDownloadLocation: (String) -> Unit,
    modifier: Modifier = Modifier,
    showFileActions: Boolean = false,
    onRenameDownload: (String, String, BrowserDownloadRenameMode) -> Unit = { _, _, _ -> },
    onMoveDownload: (String, String) -> Unit = { _, _ -> },
    onCopyDownloadUrl: (String) -> Unit = {},
    onShareDownload: (String) -> Unit = {},
    onCopyDownloadLocation: (String) -> Unit = {},
    onTransferDownload: (String) -> Unit = {},
) {
    val activeCount = remember(uiState.tasks) { uiState.tasks.count { it.status in setOf("queued", "connecting", "downloading") } }
    val failedCount = remember(uiState.tasks) { uiState.tasks.count { it.status == "failed" } }
    val filteredTasks =
        remember(uiState.tasks, uiState.selectedFilter) {
            filterBrowserDownloadItems(uiState.tasks, uiState.selectedFilter)
        }

    WebSessionSheetScaffold(
        title = stringResource(R.string.web_session_downloads),
        subtitle = stringResource(R.string.web_session_downloads_summary, activeCount, failedCount),
        modifier = modifier
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BrowserDownloadFilter.entries.forEach { filter ->
                FilterChip(
                    selected = filter == uiState.selectedFilter,
                    onClick = { onFilterChange(filter) },
                    label = {
                        Text(
                            text =
                                when (filter) {
                                    BrowserDownloadFilter.IN_PROGRESS -> stringResource(R.string.web_session_downloads_filter_in_progress)
                                    BrowserDownloadFilter.COMPLETED -> stringResource(R.string.web_session_downloads_filter_completed)
                                    BrowserDownloadFilter.FAILED -> stringResource(R.string.web_session_downloads_filter_failed)
                                }
                        )
                    }
                )
            }
        }

        if (filteredTasks.isEmpty()) {
            WebSessionEmptyState(
                icon = Icons.Filled.Download,
                title = stringResource(R.string.web_session_downloads_empty_title),
                message = stringResource(R.string.web_session_downloads_empty_message)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(filteredTasks, key = { it.id }) { item ->
                    BrowserDownloadTaskCard(
                        item = item,
                        onPauseDownload = onPauseDownload,
                        onResumeDownload = onResumeDownload,
                        onCancelDownload = onCancelDownload,
                        onRetryDownload = onRetryDownload,
                        onDeleteDownload = onDeleteDownload,
                        onOpenDownloadedFile = onOpenDownloadedFile,
                        onOpenDownloadLocation = onOpenDownloadLocation,
                        showFileActions = showFileActions,
                        onRenameDownload = onRenameDownload,
                        onMoveDownload = onMoveDownload,
                        onCopyDownloadUrl = onCopyDownloadUrl,
                        onShareDownload = onShareDownload,
                        onCopyDownloadLocation = onCopyDownloadLocation,
                        onTransferDownload = onTransferDownload,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BrowserDownloadTaskCard(
    item: BrowserDownloadItem,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onDeleteDownload: (String, Boolean) -> Unit,
    onOpenDownloadedFile: (String) -> Unit,
    onOpenDownloadLocation: (String) -> Unit,
    showFileActions: Boolean = false,
    onRenameDownload: (String, String, BrowserDownloadRenameMode) -> Unit = { _, _, _ -> },
    onMoveDownload: (String, String) -> Unit = { _, _ -> },
    onCopyDownloadUrl: (String) -> Unit = {},
    onShareDownload: (String) -> Unit = {},
    onCopyDownloadLocation: (String) -> Unit = {},
    onTransferDownload: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsStore = remember(context) { BrowserDownloadSettingsStore.getInstance(context) }
    var showDeleteConfirmation by remember(item.id) { mutableStateOf(false) }
    var deleteFile by remember(item.id) { mutableStateOf(false) }
    var menuExpanded by remember(item.id) { mutableStateOf(false) }
    var renameDialogMode by remember(item.id) { mutableStateOf(BrowserDownloadRenameMode.RENAME) }
    var renameInput by remember(item.id) { mutableStateOf("") }
    var showRenameDialog by remember(item.id) { mutableStateOf(false) }
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            onMoveDownload(item.id, selectedUri.toString())
        }
    }

    WebSessionItemCard(
        highlighted = item.status == "downloading" || item.status == "connecting",
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = statusLabel(item),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(item)
            )
            Text(
                text = itemProgressText(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.progress != null) {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
            }
            Text(
                text = item.destinationPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.errorMessage.isNullOrBlank()) {
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (item.canPause) {
                    TextButton(onClick = { onPauseDownload(item.id) }) {
                        Text(stringResource(R.string.web_session_download_pause))
                    }
                }
                if (item.canResume) {
                    TextButton(onClick = { onResumeDownload(item.id) }) {
                        Text(stringResource(R.string.web_session_download_resume))
                    }
                }
                if (item.canCancel) {
                    TextButton(onClick = { onCancelDownload(item.id) }) {
                        Text(stringResource(R.string.web_session_download_cancel))
                    }
                }
                if (item.canRetry) {
                    TextButton(onClick = { onRetryDownload(item.id) }) {
                        Text(stringResource(R.string.web_session_download_retry))
                    }
                }
                if (item.canOpenFile) {
                    TextButton(onClick = { onOpenDownloadedFile(item.id) }) {
                        Text(stringResource(R.string.web_session_download_open_file))
                    }
                }
                if (item.canOpenLocation) {
                    TextButton(onClick = { onOpenDownloadLocation(item.id) }) {
                        Text(stringResource(R.string.web_session_download_open_location))
                    }
                }
                if (item.canDelete) {
                    TextButton(
                        onClick = {
                            deleteFile =
                                item.canDeleteFile && settingsStore.current.deleteFileByDefault
                            showDeleteConfirmation = true
                        },
                    ) {
                        Text(stringResource(R.string.web_session_download_delete_record))
                    }
                }
                if (showFileActions && item.status == "completed") {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                renameDialogMode = BrowserDownloadRenameMode.RENAME
                                renameInput = browserDownloadRenameInput(item.fileName, renameDialogMode)
                                showRenameDialog = true
                                menuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("修改后缀") },
                            onClick = {
                                renameDialogMode = BrowserDownloadRenameMode.SUFFIX
                                renameInput = browserDownloadRenameInput(item.fileName, renameDialogMode)
                                showRenameDialog = true
                                menuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("修改文件夹") },
                            onClick = {
                                menuExpanded = false
                                folderPickerLauncher.launch(null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("复制下载链接") },
                            onClick = {
                                menuExpanded = false
                                onCopyDownloadUrl(item.id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("分享本地文件") },
                            onClick = {
                                menuExpanded = false
                                onShareDownload(item.id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("复制文件路径") },
                            onClick = {
                                menuExpanded = false
                                onCopyDownloadLocation(item.id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("转存到公开目录") },
                            onClick = {
                                menuExpanded = false
                                onTransferDownload(item.id)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("删除下载") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("确定删除“${item.fileName}”的下载记录吗？")
                    if (item.canDeleteFile) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { deleteFile = !deleteFile },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = deleteFile,
                                onCheckedChange = { checked -> deleteFile = checked },
                            )
                            Text("同时删除下载文件")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsStore.setDeleteFileByDefault(deleteFile)
                        showDeleteConfirmation = false
                        onDeleteDownload(item.id, deleteFile)
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(if (renameDialogMode == BrowserDownloadRenameMode.SUFFIX) "修改后缀" else "重命名") },
            text = {
                TextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetName = buildBrowserDownloadRenameTarget(
                            item.fileName,
                            renameDialogMode,
                            renameInput,
                        )
                        if (targetName.isBlank()) {
                            return@TextButton
                        }
                        onRenameDownload(item.id, targetName, renameDialogMode)
                        showRenameDialog = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun statusLabel(item: BrowserDownloadItem): String =
    when (item.status) {
        "queued" -> stringResource(R.string.web_session_download_status_queued)
        "connecting" -> stringResource(R.string.web_session_download_status_connecting)
        "downloading" -> stringResource(R.string.web_session_download_status_downloading)
        "paused" -> stringResource(R.string.web_session_download_status_paused)
        "completed" -> stringResource(R.string.web_session_download_status_completed)
        "failed" -> stringResource(R.string.web_session_download_status_failed)
        "canceled" -> stringResource(R.string.web_session_download_status_canceled)
        else -> item.status
    }

@Composable
private fun statusColor(item: BrowserDownloadItem) =
    when (item.status) {
        "failed" -> MaterialTheme.colorScheme.error
        "completed" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun itemProgressText(item: BrowserDownloadItem): String {
    val base =
        if (item.totalBytes > 0L) {
            "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
        } else {
            formatBytes(item.downloadedBytes)
        }
    val progressText =
        item.progress?.let { progress ->
            "${(progress * 100f).toInt()}% $base"
        } ?: base
    return if (item.speedBytesPerSecond > 0L) {
        "$progressText  |  ${formatBytes(item.speedBytesPerSecond)}/s"
    } else {
        progressText
    }
}
