package com.ai.assistance.operit.ui.main.shell

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BROWSER_DOWNLOAD_CHUNK_SIZE_KB_OPTIONS
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BROWSER_DOWNLOAD_MAX_CONCURRENT_TASK_OPTIONS
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BROWSER_DOWNLOAD_M3U8_THREAD_OPTIONS
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BROWSER_DOWNLOAD_SEGMENT_THREAD_OPTIONS
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadManager
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadNetworkPolicy
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSettings
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSettingsStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.resolveBrowserDownloadMaxConcurrentTasksLimit
import com.ai.assistance.operit.util.AppLogger

internal enum class KiyoriDownloadSettingsDependency {
    ALWAYS,
    INTERNAL_ENGINE,
}

internal enum class KiyoriDownloadSettingsAction {
    SELECT_DOWNLOAD_DIRECTORY,
    SELECT_DOWNLOAD_ENGINE,
    SELECT_MAX_CONCURRENT_TASKS,
    SELECT_NORMAL_THREAD_COUNT,
    SELECT_M3U8_THREAD_COUNT,
    SELECT_NETWORK_POLICY,
    TOGGLE_M3U8_OFFLINE_PACKAGE,
    SELECT_CHUNK_SIZE,
    TOGGLE_AUTO_CLEAN_APK,
    TOGGLE_SKIP_CONFIRMATION,
    TOGGLE_RESULT_NOTIFICATIONS,
    TOGGLE_ALLOW_ROAMING,
    OPEN_NOTIFICATION_SETTINGS,
    SELECT_DOWNLOAD_PROTOCOL,
}

internal const val KIYORI_DOWNLOAD_SETTINGS_PAGE_TITLE = "文件下载器"

internal data class KiyoriDownloadSettingsEntrySpec(
    val title: String,
    val description: String,
    val kind: KiyoriSettingsRowKind,
    val action: KiyoriDownloadSettingsAction,
    val dependency: KiyoriDownloadSettingsDependency =
        KiyoriDownloadSettingsDependency.ALWAYS,
)

internal data class KiyoriDownloadSettingsGroupSpec(
    val title: String,
    val description: String,
    val entries: List<KiyoriDownloadSettingsEntrySpec>,
)

internal val kiyoriDownloadSettingsGroups =
    listOf(
        KiyoriDownloadSettingsGroupSpec(
            title = "下载器与性能",
            description = "选择任务执行方式，并控制内置下载器的并发和线程预算",
            entries =
                listOf(
                    downloadNavigation(
                        title = "默认下载器",
                        description = "选择 Kiyori 内置下载器或 Android 系统下载器",
                        action = KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_ENGINE,
                    ),
                    downloadNavigation(
                        title = "默认保存位置",
                        description = "选择应用目录、公开下载目录或 SAF 自定义目录",
                        action = KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_DIRECTORY,
                        dependency = KiyoriDownloadSettingsDependency.INTERNAL_ENGINE,
                    ),
                    downloadNavigation(
                        title = "并行下载任务",
                        description = "同时运行的内置下载任务数，受每任务线程预算限制",
                        action = KiyoriDownloadSettingsAction.SELECT_MAX_CONCURRENT_TASKS,
                        dependency = KiyoriDownloadSettingsDependency.INTERNAL_ENGINE,
                    ),
                    downloadNavigation(
                        title = "普通文件线程数",
                        description = "支持 Range 的普通文件每个任务最多使用的分段线程",
                        action = KiyoriDownloadSettingsAction.SELECT_NORMAL_THREAD_COUNT,
                        dependency = KiyoriDownloadSettingsDependency.INTERNAL_ENGINE,
                    ),
                    downloadNavigation(
                        title = "M3U8 线程数",
                        description = "M3U8 离线包每个任务最多并行下载的媒体分片数",
                        action = KiyoriDownloadSettingsAction.SELECT_M3U8_THREAD_COUNT,
                        dependency = KiyoriDownloadSettingsDependency.INTERNAL_ENGINE,
                    ),
                ),
        ),
        KiyoriDownloadSettingsGroupSpec(
            title = "网络与后台",
            description =
                "Android 14 及以上使用用户发起数据传输任务，旧版本使用前台服务；" +
                    "网络条件同时应用于内置和系统下载器",
            entries =
                listOf(
                    downloadNavigation(
                        title = "网络条件",
                        description = "允许任意网络，或只在系统判定为非计费网络时下载",
                        action = KiyoriDownloadSettingsAction.SELECT_NETWORK_POLICY,
                    ),
                    downloadToggle(
                        title = "允许漫游下载",
                        description = "关闭后，内置和系统下载器都不会在漫游网络开始新任务",
                        action = KiyoriDownloadSettingsAction.TOGGLE_ALLOW_ROAMING,
                    ),
                    downloadToggle(
                        title = "完成与失败通知",
                        description = "内置任务结束时发送系统通知；受系统通知权限和渠道设置控制",
                        action = KiyoriDownloadSettingsAction.TOGGLE_RESULT_NOTIFICATIONS,
                        dependency = KiyoriDownloadSettingsDependency.INTERNAL_ENGINE,
                    ),
                    downloadNavigation(
                        title = "系统通知设置",
                        description = "管理文件下载器进行中通知与下载结果通知渠道",
                        action = KiyoriDownloadSettingsAction.OPEN_NOTIFICATION_SETTINGS,
                    ),
                ),
        ),
        KiyoriDownloadSettingsGroupSpec(
            title = "M3U8 与存储",
            description = "控制流媒体离线包和普通文件的单次读写分块",
            entries =
                listOf(
                    downloadToggle(
                        title = "M3U8 离线包",
                        description = "下载媒体分片并生成本地可播放列表；关闭时只保存远程播放列表",
                        action = KiyoriDownloadSettingsAction.TOGGLE_M3U8_OFFLINE_PACKAGE,
                        dependency = KiyoriDownloadSettingsDependency.INTERNAL_ENGINE,
                    ),
                    downloadNavigation(
                        title = "下载分块大小",
                        description = "控制普通文件分段读写和网络缓冲的单次块大小",
                        action = KiyoriDownloadSettingsAction.SELECT_CHUNK_SIZE,
                        dependency = KiyoriDownloadSettingsDependency.INTERNAL_ENGINE,
                    ),
                ),
        ),
        KiyoriDownloadSettingsGroupSpec(
            title = "安装与确认",
            description = "管理安装包生命周期和网页下载请求的确认方式",
            entries =
                listOf(
                    downloadToggle(
                        title = "安装包自动清理",
                        description = "安装内置下载的 APK 后自动清理对应任务文件",
                        action = KiyoriDownloadSettingsAction.TOGGLE_AUTO_CLEAN_APK,
                        dependency = KiyoriDownloadSettingsDependency.INTERNAL_ENGINE,
                    ),
                    downloadToggle(
                        title = "跳过下载确认",
                        description = "识别到下载请求后直接交给当前默认下载器",
                        action = KiyoriDownloadSettingsAction.TOGGLE_SKIP_CONFIRMATION,
                    ),
                ),
        ),
        KiyoriDownloadSettingsGroupSpec(
            title = "网络协议",
            description = "选择新建内置下载任务使用的 HTTP 连接协议",
            entries =
                listOf(
                    downloadNavigation(
                        title = "HTTP 协议",
                        description = "优先使用 HTTP/2，或固定使用 HTTP/1.1",
                        action = KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_PROTOCOL,
                        dependency = KiyoriDownloadSettingsDependency.INTERNAL_ENGINE,
                    ),
                ),
        ),
    )

private fun downloadNavigation(
    title: String,
    description: String,
    action: KiyoriDownloadSettingsAction,
    dependency: KiyoriDownloadSettingsDependency =
        KiyoriDownloadSettingsDependency.ALWAYS,
): KiyoriDownloadSettingsEntrySpec =
    KiyoriDownloadSettingsEntrySpec(
        title = title,
        description = description,
        kind = KiyoriSettingsRowKind.NAVIGATION,
        action = action,
        dependency = dependency,
    )

private fun downloadToggle(
    title: String,
    description: String,
    action: KiyoriDownloadSettingsAction,
    dependency: KiyoriDownloadSettingsDependency =
        KiyoriDownloadSettingsDependency.ALWAYS,
): KiyoriDownloadSettingsEntrySpec =
    KiyoriDownloadSettingsEntrySpec(
        title = title,
        description = description,
        kind = KiyoriSettingsRowKind.TOGGLE,
        action = action,
        dependency = dependency,
    )

@Composable
internal fun KiyoriDownloadSettingsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsStore = remember(context) { BrowserDownloadSettingsStore.getInstance(context) }
    val settings by settingsStore.state.collectAsState()
    var selection by remember { mutableStateOf<KiyoriSettingsSelection?>(null) }
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
                BrowserDownloadManager.getInstance(context)
                    .releasePersistedDirectoryPermissionIfUnused(uri.toString())
                AppLogger.e(
                    "KiyoriDownloadSettings",
                    "Failed to persist custom download directory",
                    error,
                )
                Toast.makeText(
                    context,
                    error.message ?: "无法使用该目录",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

    KiyoriCollapsingSettingsPage(
        title = KIYORI_DOWNLOAD_SETTINGS_PAGE_TITLE,
        onBack = onBack,
        modifier = modifier,
    ) {
        items(kiyoriDownloadSettingsGroups, key = KiyoriDownloadSettingsGroupSpec::title) { group ->
            KiyoriSettingsGroupSection(
                title = group.title,
                description = group.description,
            ) {
                group.entries.forEachIndexed { index, entry ->
                    val enabled = isDownloadSettingEnabled(entry, settings)
                    val checked = downloadSettingToggleValue(entry, settings)
                    KiyoriSettingsRow(
                        title = entry.title,
                        description = entry.description,
                        kind = entry.kind,
                        value = downloadSettingValue(entry, settings),
                        checked = checked,
                        enabled = enabled,
                        onClick = {
                            when (entry.kind) {
                                KiyoriSettingsRowKind.NAVIGATION -> {
                                    if (
                                        entry.action ==
                                            KiyoriDownloadSettingsAction.OPEN_NOTIFICATION_SETTINGS
                                    ) {
                                        openDownloadNotificationSettings(context)
                                    } else {
                                        selection =
                                            downloadSettingSelection(
                                                entry = entry,
                                                settings = settings,
                                                settingsStore = settingsStore,
                                                onRequestDirectory = {
                                                    folderPickerLauncher.launch(
                                                        settings.customDirectoryUri
                                                            .takeIf(String::isNotBlank)
                                                            ?.let(Uri::parse),
                                                    )
                                                },
                                            )
                                    }
                                }
                                KiyoriSettingsRowKind.TOGGLE ->
                                    toggleDownloadSetting(entry.action, settings, settingsStore)
                            }
                        },
                    )
                    if (index != group.entries.lastIndex) {
                        KiyoriSettingsDivider()
                    }
                }
            }
        }
    }

    selection?.let { currentSelection ->
        KiyoriSettingsSelectionSheet(
            selection = currentSelection,
            onDismiss = { selection = null },
            onSelect = { option ->
                option.onSelect()
                selection = null
            },
        )
    }
}

internal fun isDownloadSettingEnabled(
    entry: KiyoriDownloadSettingsEntrySpec,
    settings: BrowserDownloadSettings,
): Boolean =
    when (entry.dependency) {
        KiyoriDownloadSettingsDependency.ALWAYS -> true
        KiyoriDownloadSettingsDependency.INTERNAL_ENGINE ->
            settings.defaultEngine == BrowserDownloadEngine.INTERNAL
    }

internal fun downloadSettingValue(
    entry: KiyoriDownloadSettingsEntrySpec,
    settings: BrowserDownloadSettings,
): String? =
    when (entry.action) {
        KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_ENGINE ->
            downloadEngineLabel(settings.defaultEngine)
        KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_DIRECTORY ->
            downloadDirectoryLabel(settings)
        KiyoriDownloadSettingsAction.SELECT_MAX_CONCURRENT_TASKS ->
            settings.maxConcurrentTasks.toString()
        KiyoriDownloadSettingsAction.SELECT_NORMAL_THREAD_COUNT ->
            settings.segmentThreadCount.toString()
        KiyoriDownloadSettingsAction.SELECT_M3U8_THREAD_COUNT ->
            settings.m3u8ThreadCount.toString()
        KiyoriDownloadSettingsAction.SELECT_NETWORK_POLICY ->
            downloadNetworkPolicyLabel(settings.networkPolicy)
        KiyoriDownloadSettingsAction.SELECT_CHUNK_SIZE ->
            formatBrowserDownloadChunkSize(settings.chunkSizeKb)
        KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_PROTOCOL ->
            downloadProtocolLabel(settings.enableHttp2)
        KiyoriDownloadSettingsAction.OPEN_NOTIFICATION_SETTINGS -> "系统设置"
        else -> null
    }

private fun downloadSettingToggleValue(
    entry: KiyoriDownloadSettingsEntrySpec,
    settings: BrowserDownloadSettings,
): Boolean =
    when (entry.action) {
        KiyoriDownloadSettingsAction.TOGGLE_M3U8_OFFLINE_PACKAGE ->
            settings.packageM3u8Offline
        KiyoriDownloadSettingsAction.TOGGLE_AUTO_CLEAN_APK -> settings.autoCleanApk
        KiyoriDownloadSettingsAction.TOGGLE_SKIP_CONFIRMATION -> settings.skipConfirmation
        KiyoriDownloadSettingsAction.TOGGLE_RESULT_NOTIFICATIONS ->
            settings.showResultNotifications
        KiyoriDownloadSettingsAction.TOGGLE_ALLOW_ROAMING -> settings.allowRoaming
        else -> false
    }

private fun toggleDownloadSetting(
    action: KiyoriDownloadSettingsAction,
    settings: BrowserDownloadSettings,
    settingsStore: BrowserDownloadSettingsStore,
) {
    when (action) {
        KiyoriDownloadSettingsAction.TOGGLE_M3U8_OFFLINE_PACKAGE ->
            settingsStore.setPackageM3u8Offline(!settings.packageM3u8Offline)
        KiyoriDownloadSettingsAction.TOGGLE_AUTO_CLEAN_APK ->
            settingsStore.setAutoCleanApk(!settings.autoCleanApk)
        KiyoriDownloadSettingsAction.TOGGLE_SKIP_CONFIRMATION ->
            settingsStore.setSkipConfirmation(!settings.skipConfirmation)
        KiyoriDownloadSettingsAction.TOGGLE_RESULT_NOTIFICATIONS ->
            settingsStore.setShowResultNotifications(!settings.showResultNotifications)
        KiyoriDownloadSettingsAction.TOGGLE_ALLOW_ROAMING ->
            settingsStore.setAllowRoaming(!settings.allowRoaming)
        else -> error("Download setting action is not a toggle: $action")
    }
}

private fun downloadSettingSelection(
    entry: KiyoriDownloadSettingsEntrySpec,
    settings: BrowserDownloadSettings,
    settingsStore: BrowserDownloadSettingsStore,
    onRequestDirectory: () -> Unit,
): KiyoriSettingsSelection {
    val options =
        when (entry.action) {
            KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_ENGINE ->
                BrowserDownloadEngine.entries.map { engine ->
                    KiyoriSettingsSelectionOption(
                        label = downloadEngineLabel(engine),
                        description =
                            when (engine) {
                                BrowserDownloadEngine.INTERNAL ->
                                    "支持并行分段、M3U8 离线包、SAF 目录和任务管理"
                                BrowserDownloadEngine.SYSTEM ->
                                    "交给 Android DownloadManager，高级下载设置不参与"
                            },
                        selected = settings.defaultEngine == engine,
                        onSelect = { settingsStore.setDefaultEngine(engine) },
                    )
                }
            KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_DIRECTORY ->
                listOf(
                    KiyoriSettingsSelectionOption(
                        label = "应用下载目录",
                        description = "保存到 Kiyori 应用专属目录，由下载抽屉统一管理",
                        selected =
                            settings.customDirectoryUri.isBlank() &&
                                !settings.autoTransferToPublicDirectory,
                        onSelect = {
                            settingsStore.setAutoTransferToPublicDirectory(false)
                            if (settings.customDirectoryUri.isNotBlank()) {
                                settingsStore.clearCustomDirectory()
                            }
                        },
                    ),
                    KiyoriSettingsSelectionOption(
                        label = "公开下载目录",
                        description =
                            "普通文件完成后转存到 Download/Kiyori/browser/downloads；" +
                                "M3U8 离线包保留在应用目录",
                        selected = settings.autoTransferToPublicDirectory,
                        onSelect = {
                            settingsStore.setAutoTransferToPublicDirectory(true)
                        },
                    ),
                    KiyoriSettingsSelectionOption(
                        label = "SAF 自定义目录",
                        description =
                            if (settings.customDirectoryUri.isBlank()) {
                                "通过 Android 系统目录选择器授权一个长期保存位置"
                            } else {
                                "${settings.customDirectoryName} · 点击可重新选择目录"
                            },
                        selected = settings.customDirectoryUri.isNotBlank(),
                        onSelect = onRequestDirectory,
                    ),
                )
            KiyoriDownloadSettingsAction.SELECT_MAX_CONCURRENT_TASKS -> {
                val maxConcurrentTasksLimit =
                    resolveBrowserDownloadMaxConcurrentTasksLimit(
                        normalThreadCount = settings.segmentThreadCount,
                        m3u8ThreadCount = settings.m3u8ThreadCount,
                    )
                BROWSER_DOWNLOAD_MAX_CONCURRENT_TASK_OPTIONS
                    .filter { value -> value <= maxConcurrentTasksLimit }
                    .map { value ->
                        KiyoriSettingsSelectionOption(
                            label = value.toString(),
                            description = "最多同时运行 $value 个内置下载任务",
                            selected = settings.maxConcurrentTasks == value,
                            onSelect = { settingsStore.setMaxConcurrentTasks(value) },
                        )
                    }
            }
            KiyoriDownloadSettingsAction.SELECT_NORMAL_THREAD_COUNT ->
                BROWSER_DOWNLOAD_SEGMENT_THREAD_OPTIONS.map { value ->
                    KiyoriSettingsSelectionOption(
                        label = value.toString(),
                        description = "每个普通文件最多使用 $value 个分段请求",
                        selected = settings.segmentThreadCount == value,
                        onSelect = { settingsStore.setSegmentThreadCount(value) },
                    )
                }
            KiyoriDownloadSettingsAction.SELECT_M3U8_THREAD_COUNT ->
                BROWSER_DOWNLOAD_M3U8_THREAD_OPTIONS.map { value ->
                    KiyoriSettingsSelectionOption(
                        label = value.toString(),
                        description = "每个 M3U8 离线包最多并行下载 $value 个媒体分片",
                        selected = settings.m3u8ThreadCount == value,
                        onSelect = { settingsStore.setM3u8ThreadCount(value) },
                    )
                }
            KiyoriDownloadSettingsAction.SELECT_NETWORK_POLICY ->
                BrowserDownloadNetworkPolicy.entries.map { policy ->
                    KiyoriSettingsSelectionOption(
                        label = downloadNetworkPolicyLabel(policy),
                        description =
                            when (policy) {
                                BrowserDownloadNetworkPolicy.ANY ->
                                    "Wi-Fi、以太网和移动数据均可开始下载"
                                BrowserDownloadNetworkPolicy.UNMETERED ->
                                    "只在 Android 判定为非计费网络时开始或继续下载"
                            },
                        selected = settings.networkPolicy == policy,
                        onSelect = { settingsStore.setNetworkPolicy(policy) },
                    )
                }
            KiyoriDownloadSettingsAction.SELECT_CHUNK_SIZE ->
                BROWSER_DOWNLOAD_CHUNK_SIZE_KB_OPTIONS.map { value ->
                    KiyoriSettingsSelectionOption(
                        label = formatBrowserDownloadChunkSize(value),
                        description = "用于普通文件分段读写和网络传输缓冲",
                        selected = settings.chunkSizeKb == value,
                        onSelect = { settingsStore.setChunkSizeKb(value) },
                    )
                }
            KiyoriDownloadSettingsAction.SELECT_DOWNLOAD_PROTOCOL ->
                listOf(
                    KiyoriSettingsSelectionOption(
                        label = "优先 HTTP/2",
                        description = "支持的 HTTPS 服务器使用 HTTP/2，其余连接使用 HTTP/1.1",
                        selected = settings.enableHttp2,
                        onSelect = { settingsStore.setEnableHttp2(true) },
                    ),
                    KiyoriSettingsSelectionOption(
                        label = "仅 HTTP/1.1",
                        description = "所有新建内置下载连接固定使用 HTTP/1.1",
                        selected = !settings.enableHttp2,
                        onSelect = { settingsStore.setEnableHttp2(false) },
                    ),
                )
            else ->
                error("Download setting action does not own a selection sheet: ${entry.action}")
        }
    return KiyoriSettingsSelection(
        title = entry.title,
        currentValue = requireNotNull(downloadSettingValue(entry, settings)),
        options = options,
    )
}

internal fun downloadEngineLabel(engine: BrowserDownloadEngine): String =
    when (engine) {
        BrowserDownloadEngine.INTERNAL -> "Kiyori 内置下载器"
        BrowserDownloadEngine.SYSTEM -> "Android 系统下载器"
    }

internal fun downloadDirectoryLabel(settings: BrowserDownloadSettings): String =
    when {
        settings.defaultEngine == BrowserDownloadEngine.SYSTEM -> "系统下载目录"
        settings.customDirectoryUri.isNotBlank() -> settings.customDirectoryName
        settings.autoTransferToPublicDirectory -> "公开下载目录"
        else -> "应用下载目录"
    }

internal fun downloadProtocolLabel(enableHttp2: Boolean): String =
    if (enableHttp2) "优先 HTTP/2" else "仅 HTTP/1.1"

internal fun downloadNetworkPolicyLabel(policy: BrowserDownloadNetworkPolicy): String =
    when (policy) {
        BrowserDownloadNetworkPolicy.ANY -> "任意网络"
        BrowserDownloadNetworkPolicy.UNMETERED -> "仅非计费网络"
    }

private fun openDownloadNotificationSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            },
        )
    }.onFailure { error ->
        AppLogger.e(
            "KiyoriDownloadSettings",
            "Failed to open download notification settings",
            error,
        )
        Toast.makeText(
            context,
            error.message ?: "无法打开系统通知设置",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

internal fun formatBrowserDownloadChunkSize(valueKb: Int): String =
    if (valueKb >= 1024 && valueKb % 1024 == 0) {
        "${valueKb / 1024}MB"
    } else {
        "${valueKb}KB"
    }
