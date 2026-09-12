package com.ai.assistance.operit.ui.main.shell

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description

import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.LocalOffer

import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Workspaces

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.FileManagerPreferences
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerStorageEntry
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.storageId
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.KiyoriUiShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

internal data class KiyoriFileEntryItem(
    val title: String,
    val count: String,
    val tone: KiyoriSemanticTone,
    val icon: ImageVector,
)

internal val kiyoriFileCategoryItems =
    listOf(
        KiyoriFileEntryItem("图片", "0项", KiyoriSemanticTone.PINK, Icons.Default.Image),
        KiyoriFileEntryItem("视频", "0项", KiyoriSemanticTone.RED, Icons.Default.PlayCircle),
        KiyoriFileEntryItem("音频", "0项", KiyoriSemanticTone.PURPLE, Icons.Default.MusicNote),
        KiyoriFileEntryItem("文档", "0项", KiyoriSemanticTone.ORANGE, Icons.Default.Description),
        KiyoriFileEntryItem("安装包", "0项", KiyoriSemanticTone.GREEN, Icons.Default.Android),
        KiyoriFileEntryItem("压缩包", "0项", KiyoriSemanticTone.ORANGE, Icons.Default.Folder),
        KiyoriFileEntryItem("标签", "0项", KiyoriSemanticTone.BLUE, Icons.Default.LocalOffer),
        KiyoriFileEntryItem("下载", "0项", KiyoriSemanticTone.GREEN, Icons.Outlined.Download),
    )

internal val kiyoriFileQuickAccessItems =
    listOf(
        KiyoriFileEntryItem("应用集", "0项", KiyoriSemanticTone.BLUE, Icons.Default.Apps),
        KiyoriFileEntryItem("WPS Office", "0项", KiyoriSemanticTone.RED, Icons.Default.Description),
        KiyoriFileEntryItem("QQ", "0项", KiyoriSemanticTone.BLUE, Icons.AutoMirrored.Filled.Chat),
        KiyoriFileEntryItem("微信", "0项", KiyoriSemanticTone.GREEN, Icons.Default.Forum),
        KiyoriFileEntryItem("截屏", "0项", KiyoriSemanticTone.CYAN, Icons.Default.Crop),
        KiyoriFileEntryItem("录音机", "0项", KiyoriSemanticTone.PURPLE, Icons.Default.GraphicEq),
        KiyoriFileEntryItem("蓝牙", "0项", KiyoriSemanticTone.BLUE, Icons.Default.Bluetooth),
    )

/** 首页"存储位置"四个入口的种类；内部存储与回收站固定常驻，Linux 与工作区可在管理页开关。 */
internal enum class KiyoriFileStorageKind { INTERNAL, LINUX, WORKSPACE, RECYCLE_BIN }

internal data class KiyoriFileStorageRowItem(
    val kind: KiyoriFileStorageKind,
    val title: String,
    val path: String,
    val environment: String?,
    val storageId: String,
    /** 固定入口不能被隐藏，也不在管理页展示开关。 */
    val fixed: Boolean,
)

/** 纯函数，不依赖 Compose/Context，便于单测覆盖固定入口与可选入口的取舍逻辑。 */
internal fun kiyoriFileStorageRowItems(
    internalStoragePath: String,
    workspacePath: String,
): List<KiyoriFileStorageRowItem> {
    val internalEntry = FileManagerStorageEntry("内部存储", internalStoragePath)
    val linuxEntry = FileManagerStorageEntry("Linux", "/", environment = "linux")
    val workspaceEntry = FileManagerStorageEntry("工作区", workspacePath, category = "工作区")
    return listOf(
        KiyoriFileStorageRowItem(
            KiyoriFileStorageKind.INTERNAL, internalEntry.title, internalEntry.path,
            internalEntry.environment, internalEntry.storageId, fixed = true,
        ),
        KiyoriFileStorageRowItem(
            KiyoriFileStorageKind.LINUX, linuxEntry.title, linuxEntry.path,
            linuxEntry.environment, linuxEntry.storageId, fixed = false,
        ),
        KiyoriFileStorageRowItem(
            KiyoriFileStorageKind.WORKSPACE, workspaceEntry.title, workspaceEntry.path,
            workspaceEntry.environment, workspaceEntry.storageId, fixed = false,
        ),
        KiyoriFileStorageRowItem(
            KiyoriFileStorageKind.RECYCLE_BIN, "回收站", "/回收站",
            "recycle", "kiyori-shell-recycle-bin", fixed = true,
        ),
    )
}

/** 固定入口始终展示；其余入口按"隐藏"集合过滤，与文件管理器侧栏共用同一份持久化状态。 */
internal fun kiyoriVisibleFileStorageRowItems(
    items: List<KiyoriFileStorageRowItem>,
    hiddenStorageIds: Set<String>,
): List<KiyoriFileStorageRowItem> = items.filter { it.fixed || it.storageId !in hiddenStorageIds }

/**
 * 首页与真实文件管理器共享同一个默认工作区路径来源，管理页开关才会同时对两处生效。
 * [fallbackPath] 由调用方解析（Android 路径 API 不便于纯单测），未配置时才会用到。
 */
internal fun kiyoriDefaultWorkspacePath(configuredPath: String, fallbackPath: String): String =
    configuredPath.ifBlank { fallbackPath }

private fun kiyoriFileStorageIcon(kind: KiyoriFileStorageKind): ImageVector = when (kind) {
    KiyoriFileStorageKind.INTERNAL -> Icons.Filled.Smartphone
    KiyoriFileStorageKind.LINUX -> Icons.Rounded.Terminal
    KiyoriFileStorageKind.WORKSPACE -> Icons.Rounded.Workspaces
    KiyoriFileStorageKind.RECYCLE_BIN -> Icons.Rounded.RestoreFromTrash
}

private fun kiyoriFileStorageTone(kind: KiyoriFileStorageKind): KiyoriSemanticTone = when (kind) {
    KiyoriFileStorageKind.INTERNAL -> KiyoriSemanticTone.BLUE
    KiyoriFileStorageKind.LINUX -> KiyoriSemanticTone.GREEN
    KiyoriFileStorageKind.WORKSPACE -> KiyoriSemanticTone.PURPLE
    KiyoriFileStorageKind.RECYCLE_BIN -> KiyoriSemanticTone.RED
}

@Composable
internal fun KiyoriFileManagementPage(
    onOpenPhoneStorage: () -> Unit,
    onOpenFileManagerLocation: (path: String, environment: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember(context) { FileManagerPreferences.getInstance(context) }
    val settings by preferences.state.collectAsState()
    val deviceStorageCapacity = rememberDeviceStorageCapacityLabel()
    var showStorageLocationsPage by remember { mutableStateOf(false) }

    val internalStoragePath = remember { Environment.getExternalStorageDirectory().absolutePath }
    val fallbackWorkspacePath =
        remember {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .resolve("Kiyori/workspace").absolutePath
        }
    val workspacePath = kiyoriDefaultWorkspacePath(settings.defaultWorkspacePath, fallbackWorkspacePath)
    val storageRows = remember(internalStoragePath, workspacePath) {
        kiyoriFileStorageRowItems(internalStoragePath, workspacePath)
    }
    val visibleStorageRows = kiyoriVisibleFileStorageRowItems(storageRows, settings.drawerHidden)

    fun openStorageRow(item: KiyoriFileStorageRowItem) {
        when (item.kind) {
            KiyoriFileStorageKind.INTERNAL -> onOpenPhoneStorage()
            else -> onOpenFileManagerLocation(item.path, item.environment)
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        KiyoriFileManagementTopBar()
        Spacer(modifier = Modifier.height(28.dp))
        KiyoriFileEntryGrid(kiyoriFileCategoryItems)
        Spacer(modifier = Modifier.height(36.dp))
        KiyoriFileSectionHeader("快捷访问")
        Spacer(modifier = Modifier.height(14.dp))
        KiyoriFileEntryGrid(kiyoriFileQuickAccessItems)
        Spacer(modifier = Modifier.height(36.dp))
        KiyoriFileSectionHeader("存储位置", onSeeAllClick = { showStorageLocationsPage = true })
        Spacer(modifier = Modifier.height(14.dp))
        visibleStorageRows.forEachIndexed { index, item ->
            KiyoriFileStorageRow(
                item = item,
                value = if (item.kind == KiyoriFileStorageKind.INTERNAL) deviceStorageCapacity else null,
                onClick = { openStorageRow(item) },
            )
            if (index != visibleStorageRows.lastIndex) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        Spacer(modifier = Modifier.height(96.dp))
    }

    if (showStorageLocationsPage) {
        KiyoriFileStorageLocationsPage(
            entries = storageRows,
            hiddenStorageIds = settings.drawerHidden,
            onToggleHidden = { item, hidden ->
                preferences.update { current ->
                    current.copy(
                        drawerHidden =
                            if (hidden) current.drawerHidden + item.storageId
                            else current.drawerHidden - item.storageId,
                    )
                }
            },
            onOpenEntry = { item ->
                showStorageLocationsPage = false
                openStorageRow(item)
            },
            onAddOtherLocation = {
                showStorageLocationsPage = false
                onOpenPhoneStorage()
            },
            onDismiss = { showStorageLocationsPage = false },
        )
    }
}

@Composable
private fun KiyoriFileManagementTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(KiyoriUiShapes.field),
            shape = KiyoriUiShapes.field,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                    modifier = Modifier.size(19.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "搜索",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.KeyboardVoice,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.MoreVert,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun KiyoriFileSectionHeader(title: String, onSeeAllClick: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                if (onSeeAllClick != null) {
                    Modifier.clip(KiyoriUiShapes.field).clickable(onClick = onSeeAllClick)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                } else Modifier,
        ) {
            Text(
                "全部",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun KiyoriFileEntryGrid(items: List<KiyoriFileEntryItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(4).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { item ->
                    KiyoriFileEntryTile(item = item, modifier = Modifier.weight(1f))
                }
                repeat(4 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KiyoriFileEntryTile(item: KiyoriFileEntryItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KiyoriSemanticIconBadge(
            imageVector = item.icon,
            tone = item.tone,
            contentDescription = item.title,
            containerSize = 40.dp,
            iconSize = 18.dp,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            item.count,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun KiyoriFileStorageRow(
    item: KiyoriFileStorageRowItem,
    value: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(KiyoriUiShapes.card)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.kind == KiyoriFileStorageKind.INTERNAL) {
            Icon(
                painter = painterResource(R.drawable.ic_kiyori_file_storage_phone),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(22.dp),
            )
        } else {
            KiyoriSemanticIconBadge(
                imageVector = kiyoriFileStorageIcon(item.kind),
                tone = kiyoriFileStorageTone(item.kind),
                contentDescription = item.title,
                containerSize = 22.dp,
                iconSize = 14.dp,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        value?.let { storageValue ->
            Text(
                text = storageValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * "全部"打开的全屏存储位置管理页：固定入口常驻展示，Linux/工作区可随时开关；
 * 添加其他位置的完整流程（本地文件夹、网络存储、书签）复用文件管理器侧栏已有能力，
 * 避免在首页重复实现一套 SAF/网络凭据表单。
 */
@Composable
private fun KiyoriFileStorageLocationsPage(
    entries: List<KiyoriFileStorageRowItem>,
    hiddenStorageIds: Set<String>,
    onToggleHidden: (KiyoriFileStorageRowItem, Boolean) -> Unit,
    onOpenEntry: (KiyoriFileStorageRowItem) -> Unit,
    onAddOtherLocation: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, "关闭")
                    }
                    Text(
                        "存储位置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                ) {
                    Text(
                        "内部存储和回收站始终显示；Linux 和工作区可以随时开启或隐藏。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                    entries.forEach { entry ->
                        KiyoriFileStorageManagementRow(
                            entry = entry,
                            hidden = entry.storageId in hiddenStorageIds,
                            onToggleHidden = { hidden -> onToggleHidden(entry, hidden) },
                            onClick = { onOpenEntry(entry) },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "添加其他位置",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "本地文件夹、网络存储或书签都可以在文件管理器的存储侧栏中添加；新添加的入口会自动出现在这里，并可再次开关显示。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(KiyoriUiShapes.card)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable(onClick = onAddOtherLocation)
                                .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "打开文件管理器添加",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "跳转到存储侧栏，添加本地、网络或书签入口",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun KiyoriFileStorageManagementRow(
    entry: KiyoriFileStorageRowItem,
    hidden: Boolean,
    onToggleHidden: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(KiyoriUiShapes.card)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KiyoriSemanticIconBadge(
            imageVector = kiyoriFileStorageIcon(entry.kind),
            tone = kiyoriFileStorageTone(entry.kind),
            contentDescription = entry.title,
            containerSize = 36.dp,
            iconSize = 16.dp,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                when {
                    entry.fixed -> "固定入口 · 首页始终显示"
                    hidden -> "已隐藏 · 首页不显示"
                    else -> "首页显示中"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!entry.fixed) {
            Switch(checked = !hidden, onCheckedChange = { checked -> onToggleHidden(!checked) })
        }
    }
}

@Composable
private fun rememberDeviceStorageCapacityLabel(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var label by remember(context) { mutableStateOf(readDeviceStorageCapacityLabel(context)) }

    DisposableEffect(context, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    label = readDeviceStorageCapacityLabel(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return label
}

private fun readDeviceStorageCapacityLabel(context: Context): String {
    val storage = StatFs(context.filesDir.absolutePath)
    val available = Formatter.formatFileSize(context, storage.availableBytes)
    val total = Formatter.formatFileSize(context, storage.totalBytes)
    return "可用 $available / $total"
}
