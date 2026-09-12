package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceImagePreview
import com.ai.assistance.operit.core.player.*
import com.ai.assistance.operit.ui.features.player.PlayerActivity
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.CodeEditor
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.LanguageDetector
import com.ai.assistance.operit.ui.features.chat.webview.workspace.workspaceMimeTypeForPath
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 内容页面只消费文件管理会话；媒体交给唯一 PlayerSession，文本复用现有 NativeCodeEditor。 */
@Composable
internal fun FileManagerContentHost(viewModel: FileManagerViewModel, onOpenBrowserUrl: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val request = viewModel.pendingOpen
    val share = viewModel.pendingShare
    LaunchedEffect(share) {
        val target = share ?: return@LaunchedEffect
        try {
            val uri = withContext(Dispatchers.IO) {
                val file = File(target.path)
                check(file.isFile && file.canRead())
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = workspaceMimeTypeForPath(target.path)
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newRawUri(target.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "分享 ${target.name}"))
            viewModel.finishShare()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: Exception) { viewModel.finishShare("无法分享，请检查文件、权限及可用应用") }
    }
    var imagePreview by remember { mutableStateOf<Pair<String, Uri>?>(null) }
    LaunchedEffect(request?.id) {
        val target = request ?: return@LaunchedEffect
        try {
            val uri = withContext(Dispatchers.IO) {
                val file = File(target.path)
                check(file.isFile && file.canRead()) { "文件不存在或没有读取权限" }
                // 应用内直接交给共享浏览器，保留 file 基址以加载同目录 CSS/图片；不经外部 Intent。
                if (target.kind == FileManagerOpenKind.BROWSER) Uri.fromFile(file)
                else FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
            when (target.kind) {
                FileManagerOpenKind.BROWSER -> {
                    val openBrowser = checkNotNull(onOpenBrowserUrl) { "浏览器入口不可用" }
                    viewModel.finishOpen(target.id)
                    openBrowser(uri.toString())
                }
                FileManagerOpenKind.MEDIA -> {
                    PlayerSession.getInstance(context).open(
                        PlayerMediaRequest("file-manager-${target.id}-${System.nanoTime()}", uri.toString(), target.name,
                            source = PlayerMediaSource.EXTERNAL_INTENT), PlayerPresentation.FULLSCREEN_PLAYER,
                    )
                    context.startActivity(PlayerActivity.createReuseSessionIntent(context))
                }
                FileManagerOpenKind.IMAGE -> { imagePreview = target.name to uri }
                FileManagerOpenKind.SYSTEM -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, workspaceMimeTypeForPath(target.path))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "打开方式"))
                FileManagerOpenKind.TEXT -> error("文本由文件管理会话加载")
            }
            viewModel.finishOpen(target.id)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: Exception) {
            viewModel.finishOpen(target.id, if (failure is android.content.ActivityNotFoundException)
                "没有可打开此类型的应用" else "无法打开文件，请检查文件和读取权限")
        }
    }
    viewModel.openError?.let { error ->
        AlertDialog(onDismissRequest = viewModel::dismissOpenError,
            title = { Text("无法打开") }, text = { Text(error) },
            confirmButton = { TextButton(onClick = viewModel::dismissOpenError) { Text("知道了") } })
    }
    imagePreview?.let { (name, uri) ->
        Dialog(onDismissRequest = { imagePreview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { imagePreview = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "关闭图片") }
                        Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    WorkspaceImagePreview(fileName = name, previewUri = uri, isSourceLoading = false, errorMessage = null,
                        modifier = Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
    viewModel.textDocument?.let { document ->
        FileManagerTextEditor(document, !viewModel.isWriting, viewModel::updateTextDocument, viewModel::saveTextCopy, viewModel::closeTextDocument)
    }
}

@Composable
private fun FileManagerTextEditor(
    document: FileManagerTextState, canSave: Boolean, onChange: (String) -> Unit, onSaveCopy: (String) -> Unit, onClose: () -> Unit,
) {
    var confirmClose by remember(document.id) { mutableStateOf(false) }
    var showSave by remember(document.id) { mutableStateOf(false) }
    var copyName by remember(document.id) { mutableStateOf(document.name.substringBeforeLast('.', document.name) + "_edited" +
        document.name.substringAfterLast('.', "").takeIf(String::isNotEmpty)?.let { ".$it" }.orEmpty()) }
    var softWrap by remember { mutableStateOf(true) }
    val close = { if (!document.saving) { if (document.dirty) confirmClose = true else onClose() } }
    LaunchedEffect(document.savedPath) { if (document.savedPath != null) showSave = false }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
        BackHandler(onBack = close)
        Surface(Modifier.fillMaxSize().imePadding()) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = close, enabled = !document.saving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回文件列表") }
                    Text(document.name + if (document.dirty) " · 未保存" else "", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconToggleButton(checked = softWrap, onCheckedChange = { softWrap = it }) { Icon(Icons.AutoMirrored.Rounded.WrapText, "自动换行") }
                    TextButton(onClick = { showSave = true }, enabled = document.readable && canSave && !document.unknown) { Text("另存副本") }
                }
                Text("UTF-8 · 原文件保留", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                document.savedPath?.let { Text("已保存：$it", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall) }
                document.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                if (!canSave && !document.saving) Text("等待当前文件操作完成后可保存副本", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                if (document.loading || document.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (document.readable) Box(Modifier.weight(1f)) {
                    CodeEditor(code = document.content, language = LanguageDetector.detectLanguage(document.name), onCodeChange = onChange,
                        readOnly = document.saving, softWrap = softWrap, enableCompletion = false)
                }
            }
        }
        if (showSave) AlertDialog(
            onDismissRequest = { if (!document.saving) showSave = false },
            title = { Text("另存文本副本") },
            text = { Column {
                Text("保存在原目录；同名文件不会被覆盖。")
                OutlinedTextField(copyName, { copyName = it }, singleLine = true, label = { Text("副本名称") }, enabled = !document.saving,
                    isError = fileManagerNameError(copyName) != null)
                document.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (document.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(onClick = { onSaveCopy(copyName) }, enabled = canSave && !document.unknown && fileManagerNameError(copyName) == null) { Text("保存副本") } },
            dismissButton = { TextButton(onClick = { showSave = false }, enabled = !document.saving) { Text("取消") } },
        )
        if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false }, title = { Text("保留未保存的修改？") },
            text = { Text("可以返回编辑器另存副本，或放弃本次修改。原文件不会改变。") },
            confirmButton = { TextButton(onClick = { confirmClose = false }) { Text("继续编辑") } },
            dismissButton = { TextButton(onClick = onClose) { Text("放弃修改") } })
    }
}
