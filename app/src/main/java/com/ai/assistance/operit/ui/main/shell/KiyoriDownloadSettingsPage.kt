package com.ai.assistance.operit.ui.main.shell

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BROWSER_DOWNLOAD_CHUNK_SIZE_KB_OPTIONS
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BROWSER_DOWNLOAD_M3U8_THREAD_OPTIONS
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BROWSER_DOWNLOAD_SEGMENT_THREAD_OPTIONS
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSettings
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSettingsStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.resolveBrowserDownloadMaxConcurrentTasksLimit
import com.ai.assistance.operit.util.AppLogger

internal enum class KiyoriDownloadSettingsEntryKind {
    NAVIGATION,
    TOGGLE,
}

internal enum class KiyoriDownloadSettingsAction {
    SELECT_CUSTOM_DIRECTORY,
    SELECT_DOWNLOAD_ENGINE,
    SELECT_MAX_CONCURRENT_TASKS,
    SELECT_NORMAL_THREAD_COUNT,
    SELECT_M3U8_THREAD_COUNT,
    TOGGLE_AUTO_MERGE_M3U8,
    TOGGLE_AUTO_TRANSFER_TO_PUBLIC_DIRECTORY,
    SELECT_CHUNK_SIZE,
    TOGGLE_AUTO_CLEAN_APK,
    TOGGLE_SKIP_CONFIRMATION,
    TOGGLE_COMPLETION_TIP,
    SELECT_DOWNLOAD_PROTOCOL,
}

internal const val KIYORI_DOWNLOAD_SETTINGS_PAGE_TITLE = "文件下载器设置"

internal data class KiyoriDownloadSettingsEntrySpec(
    val title: String,
    val kind: KiyoriDownloadSettingsEntryKind,
    val action: KiyoriDownloadSettingsAction,
)

internal val kiyoriDownloadSettingsGroups =
    listOf(
        listOf(
            downloadNavigation("自定义下载器", KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_ENGINE),
            downloadNavigation("自定义下载目录", KiyoriDownloadSettingsAction.SELECT_CUSTOM_DIRECTORY),
            downloadNavigation("同时下载任务数", KiyoriDownloadSettingsAction.SELECT_MAX_CONCURRENT_TASKS),
            downloadNavigation("普通格式下载线程数", KiyoriDownloadSettingsAction.SELECT_NORMAL_THREAD_COUNT),
            downloadNavigation("M3U8下载线程数", KiyoriDownloadSettingsAction.SELECT_M3U8_THREAD_COUNT),
        ),
        listOf(
            downloadToggle("M3U8自动合并", KiyoriDownloadSettingsAction.TOGGLE_AUTO_MERGE_M3U8),
            downloadToggle(
                "自动转存公开目录",
                KiyoriDownloadSettingsAction.TOGGLE_AUTO_TRANSFER_TO_PUBLIC_DIRECTORY,
            ),
            downloadNavigation("自定义下载分块大小", KiyoriDownloadSettingsAction.SELECT_CHUNK_SIZE),
        ),
        listOf(
            downloadToggle("安装包自动清理", KiyoriDownloadSettingsAction.TOGGLE_AUTO_CLEAN_APK),
            downloadToggle("下载无需弹窗确认", KiyoriDownloadSettingsAction.TOGGLE_SKIP_CONFIRMATION),
            downloadToggle("下载完成强提示", KiyoriDownloadSettingsAction.TOGGLE_COMPLETION_TIP),
        ),
        listOf(downloadNavigation("切换下载协议", KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_PROTOCOL)),
    )

private fun downloadNavigation(
    title: String,
    action: KiyoriDownloadSettingsAction,
): KiyoriDownloadSettingsEntrySpec =
    KiyoriDownloadSettingsEntrySpec(title, KiyoriDownloadSettingsEntryKind.NAVIGATION, action)

private fun downloadToggle(
    title: String,
    action: KiyoriDownloadSettingsAction,
): KiyoriDownloadSettingsEntrySpec =
    KiyoriDownloadSettingsEntrySpec(
        title,
        KiyoriDownloadSettingsEntryKind.TOGGLE,
        action,
    )

private data class KiyoriDownloadSettingsSelectionOption(
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

private data class KiyoriDownloadSettingsSelection(
    val title: String,
    val currentValue: String,
    val showCurrentValue: Boolean = true,
    val options: List<KiyoriDownloadSettingsSelectionOption>,
)

@Composable
internal fun KiyoriDownloadSettingsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsStore = remember(context) { BrowserDownloadSettingsStore.getInstance(context) }
    val settings by settingsStore.state.collectAsState()
    var selection by remember { mutableStateOf<KiyoriDownloadSettingsSelection?>(null) }
    val folderPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                val displayName =
                    DocumentFile.fromTreeUri(context, uri)?.name
                        ?: throw IllegalStateException("无法读取所选目录名称")
                settingsStore.setCustomDirectory(uri.toString(), displayName)
            } catch (error: Exception) {
                AppLogger.e(
                    "KiyoriDownloadSettings",
                    "Failed to persist custom download directory",
                    error,
                )
                Toast.makeText(context, error.message ?: "无法使用该目录", Toast.LENGTH_SHORT).show()
            }
        }

    KiyoriDownloadSettingsRoot(
        settings = settings,
        onBack = onBack,
        onEntryClick = { entry ->
            when (entry.action) {
                        KiyoriDownloadSettingsAction.SELECT_CUSTOM_DIRECTORY ->
                            selection =
                                KiyoriDownloadSettingsSelection(
                                    title = "自定义下载目录",
                                    currentValue = settings.customDirectoryName.ifBlank { "应用下载目录" },
                                    showCurrentValue = false,
                                    options =
                                        buildList {
                                            add(
                                                KiyoriDownloadSettingsSelectionOption(
                                                    label = "选择目录",
                                                    selected = false,
                                                    onSelect = {
                                                        folderPickerLauncher.launch(
                                                            settings.customDirectoryUri
                                                                .takeIf { it.isNotBlank() }
                                                                ?.let(Uri::parse),
                                                        )
                                                    },
                                                ),
                                            )
                                            if (settings.customDirectoryUri.isNotBlank()) {
                                                add(
                                                    KiyoriDownloadSettingsSelectionOption(
                                                        label = "恢复默认目录",
                                                        selected = false,
                                                        onSelect = settingsStore::clearCustomDirectory,
                                                    ),
                                                )
                                            }
                                        },
                                )
                        KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_ENGINE ->
                            selection =
                                KiyoriDownloadSettingsSelection(
                                    title = "默认下载器",
                                    currentValue = downloadEngineLabel(settings.defaultEngine),
                                    options =
                                        BrowserDownloadEngine.entries.map { engine ->
                                            KiyoriDownloadSettingsSelectionOption(
                                                label = downloadEngineLabel(engine),
                                                selected = settings.defaultEngine == engine,
                                                onSelect = { settingsStore.setDefaultEngine(engine) },
                                            )
                                        },
                                )
                        KiyoriDownloadSettingsAction.SELECT_MAX_CONCURRENT_TASKS -> {
                            val maxConcurrentTasksLimit =
                                resolveBrowserDownloadMaxConcurrentTasksLimit(
                                    normalThreadCount = settings.segmentThreadCount,
                                    m3u8ThreadCount = settings.m3u8ThreadCount,
                                )
                            selection =
                                KiyoriDownloadSettingsSelection(
                                    title = "同时下载任务数",
                                    currentValue = settings.maxConcurrentTasks.toString(),
                                    options =
                                        (1..maxConcurrentTasksLimit).map { value ->
                                            KiyoriDownloadSettingsSelectionOption(
                                                label = value.toString(),
                                                selected = settings.maxConcurrentTasks == value,
                                                onSelect = { settingsStore.setMaxConcurrentTasks(value) },
                                            )
                                        },
                                )
                        }
                        KiyoriDownloadSettingsAction.SELECT_NORMAL_THREAD_COUNT ->
                            selection =
                                KiyoriDownloadSettingsSelection(
                                    title = "下载线程数",
                                    currentValue = settings.segmentThreadCount.toString(),
                                    options =
                                        BROWSER_DOWNLOAD_SEGMENT_THREAD_OPTIONS.map { value ->
                                            KiyoriDownloadSettingsSelectionOption(
                                                label = value.toString(),
                                                selected = settings.segmentThreadCount == value,
                                                onSelect = { settingsStore.setSegmentThreadCount(value) },
                                            )
                                        },
                                )
                        KiyoriDownloadSettingsAction.SELECT_M3U8_THREAD_COUNT ->
                            selection =
                                KiyoriDownloadSettingsSelection(
                                    title = "下载线程数",
                                    currentValue = settings.m3u8ThreadCount.toString(),
                                    options =
                                        BROWSER_DOWNLOAD_M3U8_THREAD_OPTIONS.map { value ->
                                            KiyoriDownloadSettingsSelectionOption(
                                                label = value.toString(),
                                                selected = settings.m3u8ThreadCount == value,
                                                onSelect = { settingsStore.setM3u8ThreadCount(value) },
                                            )
                                        },
                                )
                        KiyoriDownloadSettingsAction.TOGGLE_AUTO_MERGE_M3U8 ->
                            settingsStore.setAutoMergeM3u8(!settings.autoMergeM3u8)
                        KiyoriDownloadSettingsAction.TOGGLE_AUTO_TRANSFER_TO_PUBLIC_DIRECTORY ->
                            settingsStore.setAutoTransferToPublicDirectory(
                                !settings.autoTransferToPublicDirectory,
                            )
                        KiyoriDownloadSettingsAction.SELECT_CHUNK_SIZE ->
                            selection =
                                KiyoriDownloadSettingsSelection(
                                    title = "分块大小",
                                    currentValue = formatBrowserDownloadChunkSize(settings.chunkSizeKb),
                                    options =
                                        BROWSER_DOWNLOAD_CHUNK_SIZE_KB_OPTIONS.map { value ->
                                            KiyoriDownloadSettingsSelectionOption(
                                                label = formatBrowserDownloadChunkSize(value),
                                                selected = settings.chunkSizeKb == value,
                                                onSelect = { settingsStore.setChunkSizeKb(value) },
                                            )
                                        },
                                )
                        KiyoriDownloadSettingsAction.TOGGLE_AUTO_CLEAN_APK ->
                            settingsStore.setAutoCleanApk(!settings.autoCleanApk)
                        KiyoriDownloadSettingsAction.TOGGLE_SKIP_CONFIRMATION ->
                            settingsStore.setSkipConfirmation(!settings.skipConfirmation)
                        KiyoriDownloadSettingsAction.TOGGLE_COMPLETION_TIP ->
                            settingsStore.setShowCompletionTip(!settings.showCompletionTip)
                        KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_PROTOCOL ->
                            selection =
                                KiyoriDownloadSettingsSelection(
                                    title = "切换下载协议",
                                    currentValue = downloadProtocolLabel(settings.enableHttp2),
                                    showCurrentValue = false,
                                    options =
                                        listOf(
                                            KiyoriDownloadSettingsSelectionOption(
                                                label = "优先 HTTP/2",
                                                selected = settings.enableHttp2,
                                                onSelect = { settingsStore.setEnableHttp2(true) },
                                            ),
                                            KiyoriDownloadSettingsSelectionOption(
                                                label = "仅 HTTP/1.1",
                                                selected = !settings.enableHttp2,
                                                onSelect = { settingsStore.setEnableHttp2(false) },
                                            ),
                                        ),
                                )
            }
        },
        modifier = modifier,
    )

    selection?.let { currentSelection ->
        KiyoriDownloadSettingsSelectionSheet(
            selection = currentSelection,
            onDismiss = { selection = null },
            onSelect = { option ->
                option.onSelect()
                selection = null
            },
        )
    }
}

@Composable
private fun KiyoriDownloadSettingsRoot(
    settings: BrowserDownloadSettings,
    onBack: () -> Unit,
    onEntryClick: (KiyoriDownloadSettingsEntrySpec) -> Unit,
    modifier: Modifier,
) {
    KiyoriCollapsingSettingsPage(
        title = KIYORI_DOWNLOAD_SETTINGS_PAGE_TITLE,
        onBack = onBack,
        modifier = modifier,
    ) {
        itemsIndexed(kiyoriDownloadSettingsGroups) { _, group ->
            KiyoriSettingsGroupCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp),
            ) {
                group.forEachIndexed { index, entry ->
                    KiyoriDownloadSettingsRow(
                        entry = entry,
                        value = downloadSettingValue(entry, settings),
                        enabled = downloadSettingEnabled(entry, settings),
                        onClick = { onEntryClick(entry) },
                    )
                    if (index != group.lastIndex) {
                        HorizontalDivider(color = Color(0xFFF2F2EE), thickness = 0.6.dp)
                    }
                }
            }
        }
    }
}

private fun downloadSettingValue(
    entry: KiyoriDownloadSettingsEntrySpec,
    settings: BrowserDownloadSettings,
): String? =
    when (entry.title) {
        "自定义下载器" -> downloadEngineLabel(settings.defaultEngine)
        "自定义下载目录" -> settings.customDirectoryName.ifBlank { "应用下载目录" }
        "同时下载任务数" -> settings.maxConcurrentTasks.toString()
        "普通格式下载线程数" -> settings.segmentThreadCount.toString()
        "M3U8下载线程数" -> settings.m3u8ThreadCount.toString()
        "自定义下载分块大小" -> formatBrowserDownloadChunkSize(settings.chunkSizeKb)
        "切换下载协议" -> downloadProtocolLabel(settings.enableHttp2)
        else -> null
    }

private fun downloadEngineLabel(engine: BrowserDownloadEngine): String =
    when (engine) {
        BrowserDownloadEngine.INTERNAL -> "内置下载器"
        BrowserDownloadEngine.SYSTEM -> "系统下载器"
    }

private fun downloadProtocolLabel(enableHttp2: Boolean): String =
    if (enableHttp2) "优先 HTTP/2" else "仅 HTTP/1.1"

internal fun formatBrowserDownloadChunkSize(valueKb: Int): String =
    if (valueKb >= 1024 && valueKb % 1024 == 0) {
        "${valueKb / 1024}MB"
    } else {
        "${valueKb}KB"
    }

private fun downloadSettingEnabled(
    entry: KiyoriDownloadSettingsEntrySpec,
    settings: BrowserDownloadSettings,
): Boolean =
    when (entry.action) {
        KiyoriDownloadSettingsAction.TOGGLE_AUTO_MERGE_M3U8 -> settings.autoMergeM3u8
        KiyoriDownloadSettingsAction.TOGGLE_AUTO_TRANSFER_TO_PUBLIC_DIRECTORY ->
            settings.autoTransferToPublicDirectory
        KiyoriDownloadSettingsAction.TOGGLE_AUTO_CLEAN_APK -> settings.autoCleanApk
        KiyoriDownloadSettingsAction.TOGGLE_SKIP_CONFIRMATION -> settings.skipConfirmation
        KiyoriDownloadSettingsAction.TOGGLE_COMPLETION_TIP -> settings.showCompletionTip
        else -> false
    }

@Composable
private fun KiyoriDownloadSettingsRow(
    entry: KiyoriDownloadSettingsEntrySpec,
    value: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 18.dp, end = 14.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2B2B2B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        when (entry.kind) {
            KiyoriDownloadSettingsEntryKind.NAVIGATION -> {
                if (value != null) {
                    Text(
                        text = value,
                        fontSize = 13.sp,
                        color = Color(0xFF9A9895),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(7.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFFBDBDB8),
                    modifier = Modifier.size(18.dp),
                )
            }
            KiyoriDownloadSettingsEntryKind.TOGGLE -> KiyoriDownloadSettingsIndicator(enabled)
        }
    }
}

@Composable
private fun KiyoriDownloadSettingsIndicator(enabled: Boolean) {
    Box(
        modifier =
            Modifier
                .size(17.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (enabled) Color(0xFFCBBEFF) else Color.White)
                .border(
                    width = if (enabled) 0.dp else 1.dp,
                    color = if (enabled) Color.Transparent else Color(0xFFBAB4AE),
                    shape = RoundedCornerShape(3.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KiyoriDownloadSettingsSelectionSheet(
    selection: KiyoriDownloadSettingsSelection,
    onDismiss: () -> Unit,
    onSelect: (KiyoriDownloadSettingsSelectionOption) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White,
        scrimColor = Color(0x73000000),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                text =
                    if (selection.showCurrentValue) {
                        "${selection.title}，当前：${selection.currentValue}"
                    } else {
                        selection.title
                    },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            )
            HorizontalDivider(color = Color(0xFFEFEFEF))
            selection.options.forEach { option ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(option.label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (option.selected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFFEFEFEF))
            }
            Text(
                text = "取消",
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 16.dp),
            )
        }
    }
}
