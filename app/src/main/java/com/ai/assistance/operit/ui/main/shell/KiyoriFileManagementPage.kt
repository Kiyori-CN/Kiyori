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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_ACTION_SIZE_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_BOTTOM_PADDING_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_TOP_PADDING_DP
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ai.assistance.operit.data.preferences.FileManagerPreferences
import com.ai.assistance.operit.data.preferences.FileManagerSettings
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerStorageEntry
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.storageId
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.KiyoriUiShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/** 四个内置候选的身份与文件侧栏一致；不合并网络、书签或授权目录。 */
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

internal fun kiyoriFileStorageRowVisible(item: KiyoriFileStorageRowItem, settings: FileManagerSettings): Boolean =
    item.fixed || (item.storageId !in settings.drawerHidden && item.storageId !in settings.drawerRemoved &&
        (item.kind != KiyoriFileStorageKind.WORKSPACE || settings.showWorkspaces))

/** 与侧栏使用相同的隐藏、移除和分类偏好，避免首页显示已被移除的入口。 */
internal fun kiyoriVisibleFileStorageRowItems(
    items: List<KiyoriFileStorageRowItem>,
    settings: FileManagerSettings,
): List<KiyoriFileStorageRowItem> = items.filter { kiyoriFileStorageRowVisible(it, settings) }

internal fun FileManagerSettings.withKiyoriStorageVisibility(
    item: KiyoriFileStorageRowItem,
    visible: Boolean,
): FileManagerSettings {
    if (item.fixed) return this
    // 显式重新显示必须同时解除旧的移除标记及工作区分类隐藏，否则开关会显示开启却没有入口。
    return copy(
        drawerHidden = if (visible) drawerHidden - item.storageId else drawerHidden + item.storageId,
        drawerRemoved = if (visible) drawerRemoved - item.storageId else drawerRemoved,
        showWorkspaces = showWorkspaces || (visible && item.kind == KiyoriFileStorageKind.WORKSPACE),
    )
}

/**
 * 首页与真实文件管理器共享同一个默认工作区路径来源，管理页开关才会同时对两处生效。
 * [fallbackPath] 由调用方解析（Android 路径 API 不便于纯单测），未配置时才会用到。
 */
internal fun kiyoriDefaultWorkspacePath(configuredPath: String, fallbackPath: String): String =
    configuredPath.ifBlank { fallbackPath }

/**
 * 悬浮底部导航条自身高度（不含系统导航栏）。用于滚动内容末尾预留净空，
 * 避免"工作区/回收站"等末行被悬浮条遮挡；系统导航栏高度改由 [Modifier.navigationBarsPadding] 动态处理，
 * 不再用一个固定猜测值同时覆盖两者，减少不必要的空白。
 */
private val KiyoriFloatingBottomBarOwnHeight =
    (WEB_SESSION_BROWSER_BOTTOM_TOP_PADDING_DP + WEB_SESSION_BROWSER_BOTTOM_ACTION_SIZE_DP +
        WEB_SESSION_BROWSER_BOTTOM_BOTTOM_PADDING_DP).dp

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
    onOpenFileManagerLocation: (path: String, environment: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember(context) { FileManagerPreferences.getInstance(context) }
    val settings by preferences.state.collectAsState()
    var showStorageLocationsPage by rememberSaveable { mutableStateOf(false) }
    var capacityRefresh by remember { mutableStateOf(0) }

    val internalStoragePath = remember { Environment.getExternalStorageDirectory().absolutePath }
    val capacity = rememberDeviceStorageCapacity(internalStoragePath, capacityRefresh, showStorageLocationsPage)
    val fallbackWorkspacePath =
        remember {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .resolve("Kiyori/workspace").absolutePath
        }
    val workspacePath = kiyoriDefaultWorkspacePath(settings.defaultWorkspacePath, fallbackWorkspacePath)
    val storageRows = remember(internalStoragePath, workspacePath) {
        kiyoriFileStorageRowItems(internalStoragePath, workspacePath)
    }
    val visibleStorageRows = kiyoriVisibleFileStorageRowItems(storageRows, settings)

    fun openStorageRow(item: KiyoriFileStorageRowItem) {
        // 每个位置都是显式导航；不走“恢复会话”回调，避免内部存储打开上次的 Linux/回收站。
        onOpenFileManagerLocation(item.path, item.environment)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
    ) {
        KiyoriFileManagementTopBar()
        Spacer(modifier = Modifier.height(12.dp))
        KiyoriFileEntryGrid(kiyoriFileCategoryItems)
        Spacer(modifier = Modifier.height(12.dp))
        KiyoriFileSectionHeader("快捷访问")
        Spacer(modifier = Modifier.height(8.dp))
        KiyoriFileEntryGrid(kiyoriFileQuickAccessItems)
        Spacer(modifier = Modifier.height(12.dp))
        KiyoriFileSectionHeader("存储位置", onSeeAllClick = { showStorageLocationsPage = true })
        Spacer(modifier = Modifier.height(8.dp))
        visibleStorageRows.forEachIndexed { index, item ->
            KiyoriFileStorageRow(
                item = item,
                value = if (item.kind == KiyoriFileStorageKind.INTERNAL) capacity.label(context) else null,
                onClick = { openStorageRow(item) },
            )
            if (index != visibleStorageRows.lastIndex) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
        Spacer(modifier = Modifier.height(KiyoriFloatingBottomBarOwnHeight + 12.dp))
    }

    if (showStorageLocationsPage) {
        KiyoriFileStorageLocationsPage(
            entries = storageRows,
            settings = settings,
            capacity = capacity,
            onRefreshCapacity = { capacityRefresh++ },
            onToggleVisible = { item, visible ->
                preferences.update { it.withKiyoriStorageVisibility(item, visible) }
            },
            onOpenEntry = { item ->
                showStorageLocationsPage = false
                openStorageRow(item)
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
    val actionColor = if (onSeeAllClick != null) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
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
                    Modifier.clip(KiyoriUiShapes.field)
                        .clickable(role = Role.Button, onClickLabel = "查看全部$title", onClick = onSeeAllClick)
                        .heightIn(min = 48.dp).padding(start = 12.dp, end = 4.dp)
                } else Modifier,
        ) {
            Text(
                "全部",
                style = MaterialTheme.typography.bodySmall,
                color = actionColor,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = actionColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun KiyoriFileEntryGrid(items: List<KiyoriFileEntryItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                .clickable(role = Role.Button, onClickLabel = "打开${item.title}", onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KiyoriSemanticIconBadge(
            imageVector = kiyoriFileStorageIcon(item.kind),
            tone = kiyoriFileStorageTone(item.kind),
            contentDescription = null,
            containerSize = 22.dp,
            iconSize = 14.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            value?.let { storageValue ->
                Text(
                    text = storageValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

/** 四个内置位置共用首页的模型和开关状态；此页面不持有另一份位置列表或文件会话。 */
@Composable
private fun KiyoriFileStorageLocationsPage(
    entries: List<KiyoriFileStorageRowItem>,
    settings: FileManagerSettings,
    capacity: KiyoriDeviceStorageCapacityState,
    onRefreshCapacity: () -> Unit,
    onToggleVisible: (KiyoriFileStorageRowItem, Boolean) -> Unit,
    onOpenEntry: (KiyoriFileStorageRowItem) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        val darkIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f
        SideEffect {
            // Dialog 是独立窗口。白底必须画到系统栏下方，不能依赖 Activity 的系统栏设置，
            // 也不能用 statusBarsPadding 代替窗口的 edge-to-edge；深色主题同步调整图标对比度。
            window.setDimAmount(0f)
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = darkIcons
                isAppearanceLightNavigationBars = darkIcons
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10–14 仍需要显式关闭系统衬底；新版由透明栏与背景绘制接管。
                @Suppress("DEPRECATION")
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // 背景先覆盖整个窗口，内容再避开状态栏、导航栏及横屏刘海。
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回文件管理首页")
                    }
                    Text(
                        "存储位置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "选择位置开始浏览",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "点击位置打开文件。显示开关只调整入口，不删除文件，也不关闭 Linux 环境。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    entries.forEach { entry ->
                        KiyoriFileStorageManagementRow(
                            entry = entry,
                            visible = kiyoriFileStorageRowVisible(entry, settings),
                            capacity = capacity,
                            onRefreshCapacity = onRefreshCapacity,
                            onToggleVisible = { onToggleVisible(entry, it) },
                            onClick = { onOpenEntry(entry) },
                        )
                    }
                    Text(
                        "内部存储和回收站始终保留。隐藏的位置仍可在本页打开；重新显示工作区时，也会显示侧栏的工作区分组。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun KiyoriFileStorageManagementRow(
    entry: KiyoriFileStorageRowItem,
    visible: Boolean,
    capacity: KiyoriDeviceStorageCapacityState,
    onRefreshCapacity: () -> Unit,
    onToggleVisible: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Surface(shape = KiyoriUiShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "打开${entry.title}", onClick = onClick)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KiyoriSemanticIconBadge(
                    imageVector = kiyoriFileStorageIcon(entry.kind),
                    tone = kiyoriFileStorageTone(entry.kind),
                    contentDescription = null,
                    containerSize = 40.dp,
                    iconSize = 22.dp,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (entry.kind) {
                            KiyoriFileStorageKind.INTERNAL -> "浏览手机中的文件与文件夹"
                            KiyoriFileStorageKind.LINUX -> "浏览 Linux 环境中的文件"
                            KiyoriFileStorageKind.WORKSPACE -> "打开默认工作目录"
                            KiyoriFileStorageKind.RECYCLE_BIN -> "恢复文件或永久删除"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 回收站是虚拟入口，不把内部路由伪装成真实磁盘路径。
                    if (entry.kind != KiyoriFileStorageKind.RECYCLE_BIN) {
                        Text(entry.path, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            if (entry.kind == KiyoriFileStorageKind.INTERNAL) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(capacity.label(LocalContext.current), modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = onRefreshCapacity, enabled = !capacity.loading) {
                            Icon(Icons.Outlined.Refresh, "刷新内部存储容量", modifier = Modifier.size(20.dp))
                        }
                    }
                    capacity.capacity?.let { storage ->
                        LinearProgressIndicator(
                            progress = { storage.usedFraction },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        Text("已用 ${Formatter.formatFileSize(LocalContext.current, storage.usedBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            if (entry.fixed) {
                Text("常驻入口 · 首页始终显示", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
            } else {
                // 打开位置和显示开关是两个独立命中区。开关只由整行持有，读屏不会重复播报两个控件。
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(value = visible, role = Role.Switch, onValueChange = onToggleVisible)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("在首页和侧栏显示", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = visible, onCheckedChange = null)
                }
            }
        }
    }
}

internal data class KiyoriDeviceStorageCapacity(val totalBytes: Long, val availableBytes: Long) {
    init {
        require(totalBytes > 0 && availableBytes in 0..totalBytes)
    }
    val usedBytes: Long get() = totalBytes - availableBytes
    val usedFraction: Float get() = (usedBytes.toDouble() / totalBytes).toFloat()
}

private data class KiyoriDeviceStorageCapacityState(
    val loading: Boolean = true,
    val capacity: KiyoriDeviceStorageCapacity? = null,
) {
    fun label(context: Context): String = when {
        loading -> "正在读取容量…"
        capacity == null -> "容量暂不可用"
        else -> "可用 ${Formatter.formatFileSize(context, capacity.availableBytes)} / 共 ${Formatter.formatFileSize(context, capacity.totalBytes)}"
    }
}

@Composable
private fun rememberDeviceStorageCapacity(
    path: String,
    refreshKey: Int,
    locationsPageVisible: Boolean,
): KiyoriDeviceStorageCapacityState {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 查询目标共享卷，而不是 filesDir 所在卷；在 IO 线程执行，重试/恢复/进页取消旧查询的发布。
    val state by produceState(KiyoriDeviceStorageCapacityState(), path, refreshKey, resumeKey, locationsPageVisible) {
        value = KiyoriDeviceStorageCapacityState()
        value = withContext(Dispatchers.IO) {
            try {
                val storage = StatFs(path)
                KiyoriDeviceStorageCapacityState(false, KiyoriDeviceStorageCapacity(storage.totalBytes, storage.availableBytes))
            } catch (failure: IllegalArgumentException) {
                AppLogger.e("KiyoriFileStorage", "无法读取内部存储容量", failure)
                KiyoriDeviceStorageCapacityState(loading = false)
            } catch (failure: SecurityException) {
                AppLogger.e("KiyoriFileStorage", "读取内部存储容量被拒绝", failure)
                KiyoriDeviceStorageCapacityState(loading = false)
            }
        }
    }
    return state
}
