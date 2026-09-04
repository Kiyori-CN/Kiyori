package com.ai.assistance.operit.ui.main.shell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadAction
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadManager
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadRenameMode
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.buildBrowserDownloadUiState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.startManualBrowserDownload
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionDownloadSheet
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WebSessionBrowserBottomDrawer
import com.ai.assistance.operit.ui.features.websession.browser.chrome.resolveWebSessionBrowserChromeLayout
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch

private const val KIYORI_DOWNLOAD_DRAWER_TAG = "KiyoriDownloadDrawerHost"

@Composable
internal fun KiyoriDownloadDrawerHost(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var keepMountedUntilHidden by remember { mutableStateOf(isVisible) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            keepMountedUntilHidden = true
        }
    }
    if (!shouldComposeKiyoriDownloadDrawer(isVisible, keepMountedUntilHidden)) {
        return
    }

    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { BrowserDownloadManager.getInstance(context) }
    val browserTools = remember(context) { StandardBrowserSessionTools.getSharedInstance(context) }
    val taskRecords by manager.taskSnapshots.collectAsState()
    val uiState = remember(taskRecords) { buildBrowserDownloadUiState(taskRecords) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val drawerLayout =
            remember(maxWidth, maxHeight) {
                resolveWebSessionBrowserChromeLayout(
                    widthDp = maxWidth.value,
                    heightDp = maxHeight.value,
                )
            }
        WebSessionBrowserBottomDrawer(
            isVisible = isVisible,
            layout = drawerLayout,
            onDismissRequest = onDismissRequest,
            onHidden = {
                // A hidden full-screen drawer still participates in hit testing above Software Home.
                // Remove the host only after its exit animation, or the transparent layer blocks
                // the pager and every button while an immediate unmount would cut off the animation.
                keepMountedUntilHidden = false
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            WebSessionDownloadSheet(
                uiState = uiState,
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
                        Toast.makeText(
                            context,
                            R.string.web_session_download_open_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onOpenDownloadFileManager = {
                    if (!manager.openDownloadLocation()) {
                        Toast.makeText(
                            context,
                            R.string.web_session_download_location_open_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onStartManualDownload = { fileName, url, suffix, engine ->
                    runCatching {
                        browserTools.startManualBrowserDownload(
                            url = url,
                            requestedFileName = fileName,
                            requestedSuffix = suffix,
                            engine = engine,
                        )
                    }.fold(
                        onSuccess = { accepted -> accepted },
                        onFailure = { error ->
                            showKiyoriDownloadFailure(
                                context = context,
                                logMessage = "Failed to start a manual browser download",
                                userMessage = "无法添加下载任务，请检查链接和文件名",
                                error = error,
                            )
                            false
                        },
                    )
                },
                onRedownload = { taskId ->
                    manager.redownloadCompletedTask(taskId)
                        .onSuccess { task ->
                            Toast.makeText(
                                context,
                                resources.getString(R.string.download_started, task.fileName),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        .onFailure { error ->
                            showKiyoriDownloadFailure(
                                context = context,
                                logMessage = "Failed to redownload completed task",
                                userMessage = "无法重新下载该文件",
                                error = error,
                            )
                        }
                },
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
                                showKiyoriDownloadFailure(
                                    context = context,
                                    logMessage = "Failed to rename downloaded file",
                                    userMessage = "无法修改文件名",
                                    error = error,
                                )
                            }
                    }
                },
                onMoveDownload = { taskId, treeUriString ->
                    scope.launch {
                        manager.moveDownloadedFileToDirectory(taskId, treeUriString)
                            .onSuccess {
                                Toast.makeText(context, "文件移动成功", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { error ->
                                showKiyoriDownloadFailure(
                                    context = context,
                                    logMessage = "Failed to move downloaded file",
                                    userMessage = "无法移动到所选文件夹",
                                    error = error,
                                )
                            }
                    }
                },
                onCopyDownloadUrl = { taskId ->
                    val url = manager.copyDownloadUrl(taskId)
                    if (url == null) {
                        Toast.makeText(context, "当前下载链接不可用", Toast.LENGTH_SHORT).show()
                    } else {
                        copyKiyoriDownloadText(context, "download_url", url)
                        Toast.makeText(context, "已复制下载链接", Toast.LENGTH_SHORT).show()
                    }
                },
                onShareDownload = { taskId ->
                    if (!manager.shareDownloadedFile(taskId)) {
                        Toast.makeText(context, "当前文件暂时无法分享", Toast.LENGTH_SHORT).show()
                    }
                },
                onCopyDownloadLocation = { taskId ->
                    val location = manager.copyDownloadLocation(taskId)
                    if (location == null) {
                        Toast.makeText(context, "当前文件路径不可用", Toast.LENGTH_SHORT).show()
                    } else {
                        copyKiyoriDownloadText(context, "download_path", location)
                        Toast.makeText(context, "已复制文件路径", Toast.LENGTH_SHORT).show()
                    }
                },
                onTransferDownload = { taskId ->
                    scope.launch {
                        manager.transferDownloadedFileToPublicDirectory(taskId)
                            .onSuccess {
                                Toast.makeText(context, "已转存到公开目录", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { error ->
                                showKiyoriDownloadFailure(
                                    context = context,
                                    logMessage = "Failed to transfer downloaded file",
                                    userMessage = "无法转存到公开目录",
                                    error = error,
                                )
                            }
                    }
                },
                onMergeDownloadToMp4 = { taskId ->
                    Toast.makeText(context, "正在合并为 MP4", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        manager.mergeM3u8PackageToMp4(taskId)
                            .onSuccess { task ->
                                Toast.makeText(
                                    context,
                                    "已合并为 ${task.fileName}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .onFailure { error ->
                                showKiyoriDownloadFailure(
                                    context = context,
                                    logMessage = "Failed to merge M3U8 package to MP4",
                                    userMessage = "无法合并为 MP4",
                                    error = error,
                                )
                            }
                    }
                },
                onOpenDownloadSettings = onOpenDownloadSettings,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun shouldComposeKiyoriDownloadDrawer(
    isVisible: Boolean,
    keepMountedUntilHidden: Boolean,
): Boolean = isVisible || keepMountedUntilHidden

private fun copyKiyoriDownloadText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun showKiyoriDownloadFailure(
    context: Context,
    logMessage: String,
    userMessage: String,
    error: Throwable,
) {
    AppLogger.e(KIYORI_DOWNLOAD_DRAWER_TAG, logMessage, error)
    Toast.makeText(context, userMessage, Toast.LENGTH_SHORT).show()
}
