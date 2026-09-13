package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSettings
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.resolveManualBrowserDownloadFileName
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.validateBrowserManualDownload
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@Composable
internal fun AddBrowserDownloadDialog(
    settings: BrowserDownloadSettings,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, BrowserDownloadEngine) -> Boolean,
) {
    val clipboard = LocalClipboard.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var fileName by rememberSaveable { mutableStateOf("") }
    var suffix by rememberSaveable { mutableStateOf("") }
    var engine by rememberSaveable { mutableStateOf(settings.defaultEngine) }
    var namingExpanded by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var submissionError by remember { mutableStateOf<String?>(null) }
    var clipboardMessage by remember { mutableStateOf<String?>(null) }
    var pasting by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val validation = remember(url, fileName, suffix) {
        validateBrowserManualDownload(url, fileName, suffix)
    }
    val proposedName = remember(url, fileName, suffix) {
        resolveManualBrowserDownloadFileName(fileName, url, suffix)
    }
    val submit: () -> Unit = {
        if (!submitting && !pasting) {
            val requestUrl = url.trim()
            val requestName = fileName.trim()
            val requestSuffix = suffix.trim()
            val requestEngine = engine
            val currentValidation = validateBrowserManualDownload(requestUrl, requestName, requestSuffix)
            attempted = true
            submissionError = null
            if (currentValidation.fileNameError != null || currentValidation.suffixError != null) namingExpanded = true
            if (currentValidation.isValid) {
                // 在任何挂起点之前锁定提交，按钮与键盘连续操作只能创建一次任务。
                submitting = true
                focusManager.clearFocus()
                scope.launch {
                    var accepted = false
                    try {
                        yield()
                        accepted = onConfirm(requestName, requestUrl, requestSuffix, requestEngine)
                        if (!accepted) submissionError = "未能创建下载任务。输入已保留，请根据提示处理后重试。"
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        // 链接可能含签名，界面日志只记录异常类型，不记录输入或异常正文。
                        AppLogger.e("AddBrowserDownloadDialog", "Manual submission failed (${error::class.java.simpleName})")
                        submissionError = "创建下载任务时出错。输入已保留，请重试。"
                    } finally {
                        if (!accepted) submitting = false
                    }
                    // 接受后保持锁定，等宿主关闭弹窗，避免重组前重复创建。
                }
            }
        }
    }

    WebSessionBrowserModalDialog(onDismissRequest = { if (!submitting) onDismiss() }) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Outlined.Download,
            tone = WebSessionBrowserMenuTone.DOWNLOADS,
            title = "新建下载",
            modifier = Modifier.imePadding().widthIn(max = 520.dp).fillMaxWidth(),
        ) {
            // 标题和主操作固定；表单独立滚动，键盘及大字体不能挤掉提交入口。
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "下载链接",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(
                            enabled = !submitting && !pasting,
                            onClick = {
                                pasting = true
                                clipboardMessage = null
                                val previousUrl = url
                                scope.launch {
                                    try {
                                        // 仅主动点击时读取；等待剪贴板期间手动编辑的链接不能被覆盖。
                                        val clip = clipboard.getClipEntry()?.clipData
                                        val pasted = if (clip != null && clip.itemCount > 0) {
                                            clip.getItemAt(0).text?.toString()?.trim()
                                        } else null
                                        if (url == previousUrl) {
                                            if (pasted.isNullOrBlank()) {
                                                clipboardMessage = "剪贴板中没有文本链接"
                                            } else {
                                                url = pasted
                                                submissionError = null
                                                attempted = true
                                            }
                                        }
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        AppLogger.e("AddBrowserDownloadDialog", "Clipboard read failed (${error::class.java.simpleName})")
                                        clipboardMessage = "无法读取剪贴板，请在输入框中粘贴链接"
                                    } finally {
                                        pasting = false
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (pasting) "读取中" else "粘贴链接")
                        }
                    }
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; clipboardMessage = null; submissionError = null },
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "下载链接" },
                        placeholder = { Text("https://example.com/file.zip") },
                        minLines = 2,
                        maxLines = 4,
                        shape = MaterialTheme.shapes.medium,
                        isError = attempted && validation.urlError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        supportingText = {
                            Text(if (attempted) validation.urlError ?: "支持 HTTP(S) 文件链接与 M3U8 播放列表" else "支持 HTTP(S) 文件链接与 M3U8 播放列表")
                        },
                    )
                    clipboardMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { namingExpanded = !namingExpanded },
                            enabled = !submitting,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("文件命名 · 可选", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (url.isBlank() && fileName.isBlank()) "默认从链接识别文件名" else proposedName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(if (namingExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = if (namingExpanded) "收起文件命名" else "展开文件命名")
                            }
                        }
                        AnimatedVisibility(visible = namingExpanded) {
                            Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = fileName,
                                    onValueChange = { fileName = it; submissionError = null },
                                    enabled = !submitting,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("文件名") },
                                    placeholder = { Text("留空，从链接识别") },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                    isError = attempted && validation.fileNameError != null,
                                    supportingText = if (attempted && validation.fileNameError != null) {
                                        { Text(validation.fileNameError.orEmpty()) }
                                    } else null,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                )
                                OutlinedTextField(
                                    value = suffix,
                                    onValueChange = { suffix = it; submissionError = null },
                                    enabled = !submitting,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("扩展名") },
                                    placeholder = { Text("留空，保留原扩展名") },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                    isError = attempted && validation.suffixError != null,
                                    supportingText = { Text(if (attempted) validation.suffixError ?: "如 mp4、zip；修改扩展名不会转换格式" else "如 mp4、zip；修改扩展名不会转换格式") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
                    Text("下载方式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    DownloadEngineChoice(
                        title = "内置下载器", description = "在此查看进度，支持 M3U8 分段下载",
                        selected = engine == BrowserDownloadEngine.INTERNAL, enabled = !submitting,
                        onClick = { engine = BrowserDownloadEngine.INTERNAL; submissionError = null },
                    )
                    DownloadEngineChoice(
                        title = "系统下载器", description = "在系统下载管理中查看；不合并 M3U8 分段",
                        selected = engine == BrowserDownloadEngine.SYSTEM, enabled = !submitting,
                        onClick = { engine = BrowserDownloadEngine.SYSTEM; submissionError = null },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "保存到：" + when {
                            engine == BrowserDownloadEngine.SYSTEM -> "Download/Kiyori/browser/downloads"
                            settings.customDirectoryUri.isNotBlank() -> settings.customDirectoryName
                            settings.autoTransferToPublicDirectory -> "公共下载目录（完成后转存）"
                            else -> "应用下载目录"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val footerError = submissionError ?: if (attempted && !validation.isValid) {
                    "请先修正标红的内容，再开始下载。"
                } else null
                footerError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !submitting, onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = submit,
                        enabled = !submitting && !pasting,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (submitting) "正在创建…" else "开始下载")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadEngineChoice(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        }
    }
}
