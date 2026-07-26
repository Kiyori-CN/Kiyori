package com.ai.assistance.operit.ui.main.shell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadAction
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadFilter
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadManager
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadRenameMode
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.buildBrowserDownloadUiState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.filterBrowserDownloadItems
import com.ai.assistance.operit.ui.features.websession.browser.BrowserDownloadTaskCard
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch

@Composable
internal fun KiyoriDownloadCenterPage(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { BrowserDownloadManager.getInstance(context) }
    val taskRecords by manager.taskSnapshots.collectAsState()
    var selectedFilter by remember { mutableStateOf(BrowserDownloadFilter.IN_PROGRESS) }
    val uiState =
        remember(taskRecords, selectedFilter) {
            buildBrowserDownloadUiState(taskRecords, selectedFilter)
        }
    val visibleTasks =
        remember(uiState.tasks, uiState.selectedFilter) {
            filterBrowserDownloadItems(uiState.tasks, uiState.selectedFilter)
        }
    val activeCount =
        remember(uiState.tasks) {
            uiState.tasks.count { item ->
                item.status == "queued" ||
                    item.status == "connecting" ||
                    item.status == "downloading"
            }
        }

    Column(
        modifier = modifier.fillMaxSize().background(Color(0xFFEFF5FA)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "我的下载",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "进行中 $activeCount",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "下载设置")
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrowserDownloadFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(downloadFilterLabel(filter)) },
                )
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (visibleTasks.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "暂无${downloadFilterLabel(selectedFilter)}任务",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            } else {
                val columnCount = if (maxWidth >= 720.dp) 2 else 1
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visibleTasks, key = { item -> item.id }) { item ->
                        BrowserDownloadTaskCard(
                            item = item,
                            onPauseDownload = { taskId ->
                                manager.performAction(taskId, BrowserDownloadAction.PAUSE)
                            },
                            onResumeDownload = { taskId ->
                                manager.performAction(taskId, BrowserDownloadAction.RESUME)
                            },
                            onCancelDownload = { taskId ->
                                manager.performAction(taskId, BrowserDownloadAction.CANCEL)
                            },
                            onRetryDownload = { taskId ->
                                manager.performAction(taskId, BrowserDownloadAction.RETRY)
                            },
                            onDeleteDownload = { taskId, deleteFile ->
                                manager.performAction(
                                    taskId,
                                    if (deleteFile) {
                                        BrowserDownloadAction.DELETE_WITH_FILE
                                    } else {
                                        BrowserDownloadAction.DELETE_RECORD
                                    },
                                )
                            },
                            onOpenDownloadedFile = { taskId ->
                                if (!manager.openDownloadedFile(taskId)) {
                                    Toast.makeText(context, "无法打开下载文件", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onOpenDownloadLocation = { taskId ->
                                if (!manager.openDownloadLocation(taskId)) {
                                    Toast.makeText(context, "无法打开下载目录", Toast.LENGTH_SHORT).show()
                                }
                            },
                            showFileActions = true,
                            onRenameDownload = { taskId, targetFileName, renameMode ->
                                scope.launch {
                                    manager.renameDownloadedFile(taskId, targetFileName)
                                        .onSuccess {
                                            Toast.makeText(
                                                context,
                                                if (renameMode == BrowserDownloadRenameMode.SUFFIX) {
                                                    "后缀修改成功"
                                                } else {
                                                    "重命名成功"
                                                },
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        .onFailure { error ->
                                            Toast.makeText(
                                                context,
                                                error.message ?: "重命名失败",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                }
                            },
                            onMoveDownload = { taskId, treeUriString ->
                                val permissionGranted = try {
                                    context.contentResolver.takePersistableUriPermission(
                                        Uri.parse(treeUriString),
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                    )
                                    true
                                } catch (error: Exception) {
                                    AppLogger.e(
                                        "KiyoriDownloadCenter",
                                        "Failed to persist download directory permission",
                                        error,
                                    )
                                    Toast.makeText(context, "无法使用该目录", Toast.LENGTH_SHORT).show()
                                    false
                                }
                                if (permissionGranted) {
                                    val folderName =
                                        DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))?.name
                                            ?: "目标目录"
                                    scope.launch {
                                        manager.moveDownloadedFileToDirectory(taskId, treeUriString)
                                            .onSuccess { moved ->
                                                Toast.makeText(
                                                    context,
                                                    "已移动到$folderName",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                            .onFailure { error ->
                                                Toast.makeText(
                                                    context,
                                                    error.message ?: "移动失败",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                    }
                                }
                            },
                            onCopyDownloadUrl = { taskId ->
                                manager.copyDownloadUrl(taskId)?.let { url ->
                                    copyDownloadText(context, "download_url", url)
                                    Toast.makeText(context, "已复制下载链接", Toast.LENGTH_SHORT).show()
                                } ?: Toast.makeText(context, "当前下载链接不可用", Toast.LENGTH_SHORT).show()
                            },
                            onShareDownload = { taskId ->
                                if (!manager.shareDownloadedFile(taskId)) {
                                    Toast.makeText(context, "当前文件暂时无法分享", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onCopyDownloadLocation = { taskId ->
                                manager.copyDownloadLocation(taskId)?.let { location ->
                                    copyDownloadText(context, "download_path", location)
                                    Toast.makeText(context, "已复制文件路径", Toast.LENGTH_SHORT).show()
                                } ?: Toast.makeText(context, "当前文件路径不可用", Toast.LENGTH_SHORT).show()
                            },
                            onTransferDownload = { taskId ->
                                scope.launch {
                                    manager.transferDownloadedFileToPublicDirectory(taskId)
                                        .onSuccess {
                                            Toast.makeText(context, "已转存到公开目录", Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure { error ->
                                            Toast.makeText(
                                                context,
                                                error.message ?: "转存失败",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun downloadFilterLabel(filter: BrowserDownloadFilter): String =
    when (filter) {
        BrowserDownloadFilter.IN_PROGRESS -> "下载中"
        BrowserDownloadFilter.COMPLETED -> "已完成"
        BrowserDownloadFilter.FAILED -> "失败"
    }

private fun copyDownloadText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}
