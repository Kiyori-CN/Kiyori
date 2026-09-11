package com.ai.assistance.operit.ui.features.websession.browser

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description

import androidx.compose.material.icons.filled.Info

import androidx.compose.material.icons.filled.RadioButtonUnchecked

import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadBatchAction
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadCategory
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadDrawerTab
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadDrawerActionType
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadManager
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadRenameMode
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSection
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSettingsStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSortMode
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadStatusFilter
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadUiState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionDirectoryPickerCoordinator
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserDownloadCategory
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserDownloadBatchSelectionEligible
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserDownloadBatchEligibleTaskIds
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserDownloadDisplayUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserDownloadDrawerActions
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserDownloadRenameInput
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.buildBrowserDownloadRenameTarget
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.buildBrowserDownloadSections
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.extractBrowserDownloadSuffix
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.filterBrowserDownloadDrawerItems
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.formatBytes
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.isBrowserDownloadNetworkUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.sortBrowserDownloadDrawerItems
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.toggleBrowserDownloadSelection
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.toggleAllBrowserDownloadSelections
import com.ai.assistance.operit.util.AppLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import com.kiyori.design.theme.KiyoriUiShapes

private val DownloadAccentColor = Color(0xFF27A866)
private const val DOWNLOAD_DRAWER_TAG = "WebSessionDownloadDrawer"

private data class BrowserDownloadDeleteRequest(
    val items: List<BrowserDownloadItem>,
    val message: String,
)

private data class BrowserDownloadDrawerAction(
    val title: String,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WebSessionDownloadSheet(
    uiState: BrowserDownloadUiState,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onDeleteDownload: (String, Boolean) -> Unit,
    onOpenDownloadedFile: (String) -> Unit,
    onOpenDownloadFileManager: () -> Unit,
    onStartManualDownload: (String, String, String, BrowserDownloadEngine) -> Boolean,
    onRedownload: (String) -> Unit,
    onRenameDownload: (String, String, BrowserDownloadRenameMode) -> Unit,
    onMoveDownload: (String, String) -> Unit,
    onCopyDownloadUrl: (String) -> Unit,
    onShareDownload: (String) -> Unit,
    onCopyDownloadLocation: (String) -> Unit,
    onTransferDownload: (String) -> Unit,
    onMergeDownloadToMp4: (String) -> Unit,
    onOpenDownloadSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember(context) { BrowserDownloadSettingsStore.getInstance(context) }
    val settings by settingsStore.state.collectAsState()
    val tabs = remember { BrowserDownloadDrawerTab.entries.toList() }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val selectedTab = tabs[pagerState.currentPage]
    var sortMode by remember { mutableStateOf(BrowserDownloadSortMode.NEWEST) }
    var batchAction by remember { mutableStateOf<BrowserDownloadBatchAction?>(null) }
    val batchMode = batchAction != null
    var showTime by remember { mutableStateOf(false) }
    var classify by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(BrowserDownloadStatusFilter.ALL) }
    var selectedTaskIds by remember { mutableStateOf(emptySet<String>()) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteRequest by remember { mutableStateOf<BrowserDownloadDeleteRequest?>(null) }
    var cancelRequest by remember { mutableStateOf<List<BrowserDownloadItem>?>(null) }
    var actionItem by remember { mutableStateOf<BrowserDownloadItem?>(null) }
    var renameItem by remember { mutableStateOf<BrowserDownloadItem?>(null) }
    var renameMode by remember { mutableStateOf(BrowserDownloadRenameMode.RENAME) }
    val pageBackground = MaterialTheme.colorScheme.surfaceContainerLow
    val visibleItems =
        remember(uiState.tasks, selectedTab, sortMode, statusFilter, searchQuery) {
            sortBrowserDownloadDrawerItems(
                filterBrowserDownloadDrawerItems(
                    items = uiState.tasks,
                    tab = selectedTab,
                    statusFilter = statusFilter,
                    query = searchQuery,
                ),
                sortMode,
            )
        }
    val eligibleVisibleTaskIds =
        remember(visibleItems, batchAction) {
            batchAction
                ?.let { action ->
                    browserDownloadBatchEligibleTaskIds(visibleItems, action)
                }
                .orEmpty()
        }
    val allEligibleVisibleItemsSelected =
        eligibleVisibleTaskIds.isNotEmpty() &&
            eligibleVisibleTaskIds.all(selectedTaskIds::contains)
    val selectedVisibleItems =
        remember(visibleItems, selectedTaskIds, batchAction) {
            visibleItems.filter { item ->
                item.id in selectedTaskIds &&
                    batchAction?.let { action ->
                        browserDownloadBatchSelectionEligible(item, action)
                    } != false
            }
        }
    fun launchMoveDirectoryPicker(taskId: String) {
        WebSessionDirectoryPickerCoordinator.launch(context) { treeUriString ->
            if (treeUriString != null) {
                val uri = treeUriString.toUri()
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    onMoveDownload(taskId, treeUriString)
                } catch (error: Exception) {
                    BrowserDownloadManager.getInstance(context)
                        .releasePersistedDirectoryPermissionIfUnused(treeUriString)
                    AppLogger.e(DOWNLOAD_DRAWER_TAG, "Failed to persist selected download directory", error)
                    Toast.makeText(
                        context,
                        error.message ?: "无法使用所选目录",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        selectedTaskIds = emptySet()
        batchAction = null
        if (selectedTab == BrowserDownloadDrawerTab.DOWNLOADED) {
            statusFilter = BrowserDownloadStatusFilter.ALL
        }
    }

    LaunchedEffect(visibleItems, batchAction) {
        selectedTaskIds =
            selectedTaskIds.intersect(
                browserDownloadBatchEligibleTaskIds(
                    items = visibleItems,
                    action = batchAction ?: return@LaunchedEffect,
                ),
            )
    }

    fun enterBatchMode(
        action: BrowserDownloadBatchAction,
        item: BrowserDownloadItem? = null,
    ) {
        // Batch cancel must retain cancellation semantics. Reusing delete mode here would remove
        // records and partial files when the user explicitly selected the reference "批量取消" action.
        batchAction = action
        selectedTaskIds =
            item
                ?.takeIf { candidate -> browserDownloadBatchSelectionEligible(candidate, action) }
                ?.let { candidate -> setOf(candidate.id) }
                .orEmpty()
    }

    fun leaveBatchMode() {
        selectedTaskIds = emptySet()
        batchAction = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        ) {
            WebSessionDrawerHeader(
                title = if (batchMode) "已选 ${selectedVisibleItems.size} 项" else "我的下载",
                leadingIcon = Icons.Outlined.Download,
                tone = WebSessionBrowserMenuTone.DOWNLOADS,
                titleActions = {
                    Box(
                        modifier =
                            Modifier
                                .size(WEB_SESSION_DRAWER_TITLE_ACTION_SIZE_DP.dp)
                                .clip(WebSessionDrawerTitleActionShape)
                                .semantics { contentDescription = "下载管理菜单" }
                                .clickable { showTopMenu = true },
                        contentAlignment = WebSessionDrawerTitleActionContentAlignment,
                    ) {
                        DownloadMenuTrigger()
                        DownloadTopMenu(
                            expanded = showTopMenu,
                            tab = selectedTab,
                            batchAction = batchAction,
                            showTime = showTime,
                            classify = classify,
                            onDismiss = { showTopMenu = false },
                            onSort = {
                                showTopMenu = false
                                showSortDialog = true
                            },
                            onStartBatchDelete = {
                                showTopMenu = false
                                enterBatchMode(BrowserDownloadBatchAction.DELETE)
                            },
                            onStartBatchCancel = {
                                showTopMenu = false
                                enterBatchMode(BrowserDownloadBatchAction.CANCEL)
                            },
                            onExitBatch = {
                                showTopMenu = false
                                leaveBatchMode()
                            },
                            onOpenFileManager = {
                                showTopMenu = false
                                onOpenDownloadFileManager()
                            },
                            onToggleTime = {
                                showTopMenu = false
                                showTime = !showTime
                            },
                            onToggleClassify = {
                                showTopMenu = false
                                classify = !classify
                            },
                            onOpenSettings = {
                                showTopMenu = false
                                onOpenDownloadSettings()
                            },
                        )
                    }
                },
                actions = {
                    if (batchMode) {
                        DownloadOutlinedActionButton(
                            title = if (allEligibleVisibleItemsSelected) "取消全选" else "全选",
                            enabled = eligibleVisibleTaskIds.isNotEmpty(),
                            onClick = {
                                selectedTaskIds =
                                    toggleAllBrowserDownloadSelections(
                                        selectedTaskIds = selectedTaskIds,
                                        eligibleTaskIds = eligibleVisibleTaskIds,
                                    )
                            },
                        )
                    } else {
                        DownloadOutlinedActionButton(title = "新增", onClick = { showAddDialog = true })
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    DownloadOutlinedActionButton(
                        title =
                            when (batchAction) {
                                BrowserDownloadBatchAction.DELETE -> "删除"
                                BrowserDownloadBatchAction.CANCEL -> "取消"
                                null -> "清理"
                            },
                        enabled =
                            if (batchMode) {
                                selectedVisibleItems.isNotEmpty()
                            } else {
                                visibleItems.any { item ->
                                    selectedTab == BrowserDownloadDrawerTab.DOWNLOADED ||
                                        item.status == "failed" ||
                                        item.status == "canceled"
                                }
                            },
                        onClick = {
                            val targets =
                                if (batchMode) {
                                    selectedVisibleItems
                                } else if (selectedTab == BrowserDownloadDrawerTab.DOWNLOADED) {
                                    visibleItems
                                } else {
                                    visibleItems.filter { item ->
                                        item.status == "failed" || item.status == "canceled"
                                    }
                                }
                            if (targets.isNotEmpty()) {
                                when (batchAction) {
                                    BrowserDownloadBatchAction.CANCEL -> cancelRequest = targets
                                    BrowserDownloadBatchAction.DELETE,
                                    null ->
                                        deleteRequest =
                                            BrowserDownloadDeleteRequest(
                                                items = targets,
                                                message =
                                                    if (batchMode) {
                                                        "确认删除已选择的 ${targets.size} 项下载吗？"
                                                    } else if (selectedTab == BrowserDownloadDrawerTab.DOWNLOADED) {
                                                        "确认清理当前列表中的 ${targets.size} 个已下载文件吗？"
                                                    } else {
                                                        "确认清理 ${targets.size} 条失败或已取消任务吗？"
                                                    },
                                            )
                                }
                            }
                        },
                    )
                },
            )

            Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.Bottom,
        ) {
            tabs.forEachIndexed { index, tab ->
                DownloadTab(
                    label = if (tab == BrowserDownloadDrawerTab.DOWNLOADED) "已下载" else "下载中",
                    selected = selectedTab == tab,
                    modifier = Modifier.weight(1f),
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                )
            }
        }

            DownloadSearchAndFilterBar(
                query = searchQuery,
                onQueryChange = { value -> searchQuery = value },
                tab = selectedTab,
                statusFilter = statusFilter,
                onStatusFilterChange = { value -> statusFilter = value },
            )

            HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().background(pageBackground),
        ) { page ->
            val tab = tabs[page]
            val pageItems =
                remember(uiState.tasks, tab, sortMode, statusFilter, searchQuery) {
                    sortBrowserDownloadDrawerItems(
                        filterBrowserDownloadDrawerItems(
                            items = uiState.tasks,
                            tab = tab,
                            statusFilter = statusFilter,
                            query = searchQuery,
                        ),
                        sortMode,
                    )
                }
            val sections =
                remember(pageItems, classify) {
                    buildBrowserDownloadSections(pageItems, classify)
                }
            DownloadRecordsPage(
                tab = tab,
                sections = sections,
                batchAction = batchAction,
                selectedTaskIds = selectedTaskIds,
                showTime = showTime,
                hasActiveFilters =
                    searchQuery.isNotBlank() ||
                        (
                            tab == BrowserDownloadDrawerTab.DOWNLOADING &&
                                statusFilter != BrowserDownloadStatusFilter.ALL
                        ),
                pageBackground = pageBackground,
                onToggleSelection = { item ->
                    selectedTaskIds = toggleBrowserDownloadSelection(selectedTaskIds, item.id)
                },
                onOpenItem = onOpenDownloadedFile,
                onShowActions = { item -> actionItem = item },
                onPauseDownload = onPauseDownload,
                onResumeDownload = onResumeDownload,
                onCancelDownload = onCancelDownload,
                onRetryDownload = onRetryDownload,
                onDeleteItem = { item ->
                    deleteRequest =
                        BrowserDownloadDeleteRequest(
                            items = listOf(item),
                            message = "确认删除“${item.fileName}”吗？",
                        )
                },
            )
            }
        }

        if (showTopMenu) {
            WebSessionBrowserPopupScrim(onDismissRequest = { showTopMenu = false })
        }
    }

    if (showSortDialog) {
        DownloadSortDialog(
            currentMode = sortMode,
            onDismiss = { showSortDialog = false },
            onSelect = { selectedMode ->
                sortMode = selectedMode
                showSortDialog = false
            },
        )
    }

    if (showAddDialog) {
        AddBrowserDownloadDialog(
            initialEngine = settings.defaultEngine,
            onDismiss = { showAddDialog = false },
            onConfirm = { fileName, url, suffix, engine ->
                val accepted = onStartManualDownload(fileName, url, suffix, engine)
                if (accepted) {
                    showAddDialog = false
                    scope.launch {
                        pagerState.animateScrollToPage(
                            tabs.indexOf(BrowserDownloadDrawerTab.DOWNLOADING),
                        )
                    }
                }
                accepted
            },
        )
    }

    deleteRequest?.let { request ->
        DownloadDeleteManyDialog(
            message = request.message,
            canDeleteCompletedFiles = request.items.any { item -> item.status == "completed" && item.canDeleteFile },
            onDismiss = { deleteRequest = null },
            onDeleteRecordsOnly = {
                request.items.forEach { item -> onDeleteDownload(item.id, false) }
                deleteRequest = null
                leaveBatchMode()
            },
            onDeleteWithFiles = {
                request.items.forEach { item -> onDeleteDownload(item.id, true) }
                deleteRequest = null
                leaveBatchMode()
            },
        )
    }

    cancelRequest?.let { items ->
        DownloadDeleteManyDialog(
            message = "确认取消已选择的 ${items.size} 项下载任务吗？",
            canDeleteCompletedFiles = false,
            onDismiss = { cancelRequest = null },
            onDeleteRecordsOnly = { cancelRequest = null },
            onDeleteWithFiles = {
                items.forEach { item -> onCancelDownload(item.id) }
                cancelRequest = null
                leaveBatchMode()
            },
        )
    }

    actionItem?.let { item ->
        val actions =
            browserDownloadDrawerActions(item).map { actionType ->
                when (actionType) {
                    BrowserDownloadDrawerActionType.DELETE ->
                        BrowserDownloadDrawerAction("删除下载") {
                            actionItem = null
                            deleteRequest =
                                BrowserDownloadDeleteRequest(
                                    items = listOf(item),
                                    message = "确认删除“${item.fileName}”吗？",
                                )
                        }
                    BrowserDownloadDrawerActionType.BATCH_DELETE ->
                        BrowserDownloadDrawerAction("批量删除") {
                            actionItem = null
                            enterBatchMode(BrowserDownloadBatchAction.DELETE, item)
                        }
                    BrowserDownloadDrawerActionType.REDOWNLOAD ->
                        BrowserDownloadDrawerAction("重新下载") {
                            actionItem = null
                            onRedownload(item.id)
                        }
                    BrowserDownloadDrawerActionType.RENAME ->
                        BrowserDownloadDrawerAction("重命名") {
                            actionItem = null
                            renameItem = item
                            renameMode = BrowserDownloadRenameMode.RENAME
                        }
                    BrowserDownloadDrawerActionType.CHANGE_SUFFIX ->
                        BrowserDownloadDrawerAction("修改后缀") {
                            actionItem = null
                            renameItem = item
                            renameMode = BrowserDownloadRenameMode.SUFFIX
                        }
                    BrowserDownloadDrawerActionType.MOVE_FOLDER ->
                        BrowserDownloadDrawerAction("移动到文件夹") {
                            actionItem = null
                            launchMoveDirectoryPicker(item.id)
                        }
                    BrowserDownloadDrawerActionType.COPY_URL ->
                        BrowserDownloadDrawerAction("复制下载链接") {
                            actionItem = null
                            onCopyDownloadUrl(item.id)
                        }
                    BrowserDownloadDrawerActionType.SHARE_FILE ->
                        BrowserDownloadDrawerAction("分享本地文件") {
                            actionItem = null
                            onShareDownload(item.id)
                        }
                    BrowserDownloadDrawerActionType.COPY_LOCATION ->
                        BrowserDownloadDrawerAction("复制文件路径") {
                            actionItem = null
                            onCopyDownloadLocation(item.id)
                        }
                    BrowserDownloadDrawerActionType.TRANSFER_TO_PUBLIC ->
                        BrowserDownloadDrawerAction("转存到公开目录") {
                            actionItem = null
                            onTransferDownload(item.id)
                        }
                    BrowserDownloadDrawerActionType.MERGE_TO_MP4 ->
                        BrowserDownloadDrawerAction("合并为 MP4") {
                            actionItem = null
                            onMergeDownloadToMp4(item.id)
                        }
                    BrowserDownloadDrawerActionType.PAUSE ->
                        BrowserDownloadDrawerAction("暂停下载") {
                            actionItem = null
                            onPauseDownload(item.id)
                        }
                    BrowserDownloadDrawerActionType.RESUME ->
                        BrowserDownloadDrawerAction("继续下载") {
                            actionItem = null
                            onResumeDownload(item.id)
                        }
                    BrowserDownloadDrawerActionType.CANCEL ->
                        BrowserDownloadDrawerAction("取消下载") {
                            actionItem = null
                            onCancelDownload(item.id)
                        }
                    BrowserDownloadDrawerActionType.RETRY ->
                        BrowserDownloadDrawerAction("重试下载") {
                            actionItem = null
                            onRetryDownload(item.id)
                        }
                    BrowserDownloadDrawerActionType.BATCH_CANCEL ->
                        BrowserDownloadDrawerAction("批量取消") {
                            actionItem = null
                            enterBatchMode(BrowserDownloadBatchAction.CANCEL, item)
                        }
                }
            }
        DownloadActionDialog(
            actions = actions,
            columns = if (item.status == "completed") 2 else 1,
            onDismiss = { actionItem = null },
        )
    }

    renameItem?.let { item ->
        RenameBrowserDownloadDialog(
            item = item,
            mode = renameMode,
            onDismiss = { renameItem = null },
            onConfirm = { value ->
                val targetName = buildBrowserDownloadRenameTarget(item.fileName, renameMode, value)
                if (targetName.isBlank()) {
                    false
                } else {
                    onRenameDownload(item.id, targetName, renameMode)
                    renameItem = null
                    true
                }
            },
        )
    }
}

@Composable
private fun DownloadOutlinedActionButton(
    title: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = KiyoriUiShapes.control,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
        modifier = Modifier.defaultMinSize(minWidth = 0.dp).heightIn(min = 40.dp),
    ) {
        Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DownloadMenuTrigger(modifier: Modifier = Modifier) {
    val iconColor = WebSessionBrowserMenuTone.DOWNLOADS.resolveColors().icon
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(11.dp, 16.dp, 16.dp, 11.dp).forEach { height ->
            Box(
                modifier =
                    Modifier
                        .width(2.dp)
                        .height(height)
                        .background(iconColor, RoundedCornerShape(999.dp)),
            )
        }
    }
}

@Composable
private fun DownloadTab(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .height(32.dp)
                .clickable(onClick = onClick)
                .padding(top = 2.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (selected) DownloadAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier =
                Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .background(
                        if (selected) DownloadAccentColor else Color.Transparent,
                        RoundedCornerShape(999.dp),
                    ),
        )
    }
}

@Composable
private fun DownloadSearchAndFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    tab: BrowserDownloadDrawerTab,
    statusFilter: BrowserDownloadStatusFilter,
    onStatusFilterChange: (BrowserDownloadStatusFilter) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = KiyoriUiShapes.control,
            modifier = Modifier.fillMaxWidth().height(40.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 11.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isBlank()) {
                        Text(
                            text = "搜索文件名、来源或错误",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle =
                            TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                            ),
                        cursorBrush = SolidColor(DownloadAccentColor),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotBlank()) {
                    Box(
                        modifier = Modifier.size(40.dp).clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "清除搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        if (tab == BrowserDownloadDrawerTab.DOWNLOADING) {
            Spacer(modifier = Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                BrowserDownloadStatusFilter.entries.forEach { filter ->
                    val selected = statusFilter == filter
                    Surface(
                        color =
                            if (selected) {
                                DownloadAccentColor.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                        shape = KiyoriUiShapes.control,
                        border =
                            BorderStroke(
                                1.dp,
                                if (selected) {
                                    DownloadAccentColor.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                        modifier =
                            Modifier
                                .height(32.dp)
                                .clickable { onStatusFilterChange(filter) },
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = downloadStatusFilterLabel(filter),
                                color =
                                    if (selected) {
                                        DownloadAccentColor
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                fontSize = 12.sp,
                                fontWeight =
                                    if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRecordsPage(
    tab: BrowserDownloadDrawerTab,
    sections: List<BrowserDownloadSection>,
    batchAction: BrowserDownloadBatchAction?,
    selectedTaskIds: Set<String>,
    showTime: Boolean,
    hasActiveFilters: Boolean,
    pageBackground: Color,
    onToggleSelection: (BrowserDownloadItem) -> Unit,
    onOpenItem: (String) -> Unit,
    onShowActions: (BrowserDownloadItem) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onDeleteItem: (BrowserDownloadItem) -> Unit,
) {
    val batchMode = batchAction != null
    val hasItems = sections.any { section -> section.items.isNotEmpty() }
    if (!hasItems) {
        Box(
            modifier = Modifier.fillMaxSize().background(pageBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                    if (hasActiveFilters) {
                        "没有匹配的下载任务"
                    } else if (tab == BrowserDownloadDrawerTab.DOWNLOADING) {
                        "当前没有下载任务"
                    } else {
                        "当前没有已下载文件"
                    },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(pageBackground).padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        sections.forEach { section ->
            section.category?.let { category ->
                item(key = "header_${tab.name}_${category.name}") {
                    Text(
                        text = downloadCategoryLabel(category),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, bottom = 1.dp),
                    )
                }
            }
            items(section.items, key = { item -> "${tab.name}_${item.id}" }) { item ->
                val batchSelectionEnabled =
                    batchAction?.let { action ->
                        browserDownloadBatchSelectionEligible(item, action)
                    } == true
                DownloadDrawerTaskCard(
                    item = item,
                    batchMode = batchMode,
                    batchSelectionEnabled = batchSelectionEnabled,
                    selected = batchSelectionEnabled && item.id in selectedTaskIds,
                    showTime = showTime,
                    onClick = {
                        if (batchMode) {
                            if (batchSelectionEnabled) onToggleSelection(item)
                        } else if (item.status == "completed") {
                            onOpenItem(item.id)
                        }
                    },
                    onLongClick = {
                        if (batchMode) {
                            if (batchSelectionEnabled) onToggleSelection(item)
                        } else {
                            onShowActions(item)
                        }
                    },
                    onShowActions = { onShowActions(item) },
                    onPause = { onPauseDownload(item.id) },
                    onResume = { onResumeDownload(item.id) },
                    onCancel = { onCancelDownload(item.id) },
                    onRetry = { onRetryDownload(item.id) },
                    onDelete = { onDeleteItem(item) },
                )
            }
        }
        item { Spacer(modifier = Modifier.height(18.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadDrawerTaskCard(
    item: BrowserDownloadItem,
    batchMode: Boolean,
    batchSelectionEnabled: Boolean,
    selected: Boolean,
    showTime: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowActions: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (batchMode) {
                    Icon(
                        imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (selected) "已选择" else "未选择",
                        tint =
                            when {
                                selected -> DownloadAccentColor
                                batchSelectionEnabled -> MaterialTheme.colorScheme.outline
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                            },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = item.fileName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!batchMode) {
                    Text(
                        text = drawerStatusLabel(item.status),
                        color = drawerStatusColor(item.status),
                        fontSize = 11.sp,
                    )
                }
            }
            item.sourceUrl?.takeIf { value -> value.isNotBlank() }?.let { sourceUrl ->
                Text(
                    text = browserDownloadDisplayUrl(sourceUrl),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            val meta = drawerMetaText(item, showTime)
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (!item.errorMessage.isNullOrBlank()) {
                Text(
                    text = item.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (item.status in setOf("queued", "connecting", "downloading", "paused")) {
                if (item.progress == null) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(3.dp),
                        color = DownloadAccentColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(3.dp),
                        color = DownloadAccentColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
                Text(
                    text = drawerProgressText(item),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (!batchMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    when {
                        item.status == "completed" -> {
                            DownloadInlineAction("打开", onClick)
                            DownloadInlineAction("删除", onDelete)
                        }
                        item.canPause -> {
                            DownloadInlineAction("暂停", onPause)
                            DownloadInlineAction("取消", onCancel)
                        }
                        item.canResume -> {
                            DownloadInlineAction("继续", onResume)
                            DownloadInlineAction("取消", onCancel)
                        }
                        item.canRetry -> {
                            DownloadInlineAction("重试", onRetry)
                            DownloadInlineAction("删除", onDelete)
                        }
                        else -> DownloadInlineAction("删除", onDelete)
                    }
                    DownloadInlineAction("更多", onShowActions)
                }
            }
        }
    }
}

@Composable
private fun DownloadInlineAction(title: String, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = DownloadAccentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DownloadTopMenu(
    expanded: Boolean,
    tab: BrowserDownloadDrawerTab,
    batchAction: BrowserDownloadBatchAction?,
    showTime: Boolean,
    classify: Boolean,
    onDismiss: () -> Unit,
    onSort: () -> Unit,
    onStartBatchDelete: () -> Unit,
    onStartBatchCancel: () -> Unit,
    onExitBatch: () -> Unit,
    onOpenFileManager: () -> Unit,
    onToggleTime: () -> Unit,
    onToggleClassify: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = WebSessionBrowserPopupShape,
        containerColor = MaterialTheme.colorScheme.surface,
        shadowElevation = WebSessionBrowserPopupElevation,
    ) {
        WebSessionBrowserDropdownItem("排序方式", onSort)
        if (batchAction == null) {
            WebSessionBrowserDropdownItem("批量删除", onStartBatchDelete)
            if (tab == BrowserDownloadDrawerTab.DOWNLOADING) {
                WebSessionBrowserDropdownItem("批量取消", onStartBatchCancel)
            }
        } else {
            WebSessionBrowserDropdownItem("退出批量操作", onExitBatch)
        }
        WebSessionBrowserDropdownItem("文件管理", onOpenFileManager)
        WebSessionBrowserDropdownItem(if (showTime) "隐藏时间" else "显示时间", onToggleTime)
        WebSessionBrowserDropdownItem(if (classify) "关闭分类显示" else "分类显示", onToggleClassify)
        WebSessionBrowserDropdownItem("更多设置", onOpenSettings)
    }
}

@Composable
private fun DownloadSortDialog(
    currentMode: BrowserDownloadSortMode,
    onDismiss: () -> Unit,
    onSelect: (BrowserDownloadSortMode) -> Unit,
) {
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Outlined.Download,
            tone = WebSessionBrowserMenuTone.DOWNLOADS,
            title = "排序方式",
            modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                BrowserDownloadSortMode.entries.forEachIndexed { index, mode ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(WebSessionBrowserPopupItemHeight)
                                .clickable { onSelect(mode) }
                                .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = downloadSortModeLabel(mode),
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (mode == currentMode) {
                            Box(modifier = Modifier.size(8.dp).background(DownloadAccentColor, CircleShape))
                        }
                    }
                    if (index != BrowserDownloadSortMode.entries.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddBrowserDownloadDialog(
    initialEngine: BrowserDownloadEngine,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, BrowserDownloadEngine) -> Boolean,
) {
    val context = LocalContext.current
    var fileName by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var engine by remember(initialEngine) { mutableStateOf(initialEngine) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showFullLinkDialog by remember { mutableStateOf(false) }
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Outlined.Download,
            tone = WebSessionBrowserMenuTone.DOWNLOADS,
            title = "添加文件下载",
            modifier = Modifier.fillMaxWidth(0.86f),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
                DownloadDialogField(value = fileName, label = "文件名称", onValueChange = { fileName = it })
                Spacer(modifier = Modifier.height(8.dp))
                DownloadDialogField(
                    value = url,
                    label = "文件所在网址，支持 M3U8",
                    onValueChange = {
                        url = it
                        errorText = null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(modifier = Modifier.height(8.dp))
                DownloadDialogField(
                    value = suffix,
                    label = "文件后缀，留空自动识别",
                    onValueChange = { suffix = it },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.AutoFixHigh,
                            contentDescription = "提取文件后缀",
                            tint = WebSessionBrowserMenuTone.DOWNLOADS.resolveColors().icon,
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clickable {
                                        val extracted = extractBrowserDownloadSuffix(fileName, url)
                                        if (extracted == null) {
                                            Toast.makeText(context, "没有可提取的文件后缀", Toast.LENGTH_SHORT).show()
                                        } else {
                                            suffix = extracted
                                        }
                                    },
                        )
                    },
                )
                errorText?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DownloadEngineToggle(
                        title = "内置下载器",
                        selected = engine == BrowserDownloadEngine.INTERNAL,
                        onClick = { engine = BrowserDownloadEngine.INTERNAL },
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    DownloadEngineToggle(
                        title = "系统下载器",
                        selected = engine == BrowserDownloadEngine.SYSTEM,
                        onClick = { engine = BrowserDownloadEngine.SYSTEM },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "编辑完整链接",
                        tint = WebSessionBrowserMenuTone.NETWORK_LOG.resolveColors().icon,
                        modifier = Modifier.size(40.dp).clickable { showFullLinkDialog = true },
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = DownloadAccentColor, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(
                        onClick = {
                            val normalizedUrl = url.trim()
                            if (!isBrowserDownloadNetworkUrl(normalizedUrl)) {
                                errorText = "请输入 http 或 https 下载地址"
                            } else {
                                onConfirm(fileName, normalizedUrl, suffix, engine)
                            }
                        },
                    ) {
                        Text("下载", color = DownloadAccentColor, fontSize = 16.sp)
                    }
                }
            }
        }
    }
    if (showFullLinkDialog) {
        DownloadFullLinkDialog(
            initialValue = url,
            onDismiss = { showFullLinkDialog = false },
            onConfirm = { fullLink ->
                url = fullLink
                errorText = null
                showFullLinkDialog = false
            },
        )
    }
}

@Composable
private fun DownloadDialogField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val active = focused || value.isNotBlank()
    val lineColor = if (focused) DownloadAccentColor else MaterialTheme.colorScheme.outline
    val labelColor = if (focused) DownloadAccentColor else MaterialTheme.colorScheme.onSurfaceVariant
    val trailingPadding = if (trailingIcon == null) 0.dp else 30.dp
    Box(modifier = Modifier.fillMaxWidth().height(54.dp)) {
        if (active) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 4.dp, end = trailingPadding),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
            cursorBrush = SolidColor(DownloadAccentColor),
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(26.dp)
                    .padding(end = trailingPadding, bottom = 6.dp)
                    .onFocusChanged { state -> focused = state.isFocused },
        )
        if (!active) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(end = trailingPadding, bottom = 4.dp),
            )
        }
        trailingIcon?.let { content ->
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
        Box(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(lineColor),
        )
    }
}

@Composable
private fun DownloadEngineToggle(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = KiyoriUiShapes.control,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 2.dp),
        border = BorderStroke(1.dp, if (selected) DownloadAccentColor else MaterialTheme.colorScheme.outlineVariant),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        modifier = Modifier.defaultMinSize(minWidth = 0.dp).heightIn(min = 40.dp),
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DownloadFullLinkDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var fullLink by remember(initialValue) { mutableStateOf(initialValue) }
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Outlined.Download,
            tone = WebSessionBrowserMenuTone.DOWNLOADS,
            title = "完整链接",
            modifier = Modifier.fillMaxWidth(0.86f),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    TextField(
                        value = fullLink,
                        onValueChange = { fullLink = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = DownloadAccentColor,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                            ),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    DialogActionCell("取消", MaterialTheme.colorScheme.onSurfaceVariant, onDismiss, Modifier.weight(1f))
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    DialogActionCell("确定", MaterialTheme.colorScheme.onSurface, { onConfirm(fullLink.trim()) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DownloadDeleteManyDialog(
    message: String,
    canDeleteCompletedFiles: Boolean,
    onDismiss: () -> Unit,
    onDeleteRecordsOnly: () -> Unit,
    onDeleteWithFiles: () -> Unit,
) {
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Filled.Warning,
            tone = WebSessionBrowserMenuTone.DOWNLOADS,
            title = "温馨提示",
            modifier = Modifier.fillMaxWidth(0.86f),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(message, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    DialogActionCell(
                        if (canDeleteCompletedFiles) "仅删除记录" else "取消",
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        if (canDeleteCompletedFiles) onDeleteRecordsOnly else onDismiss,
                        Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    DialogActionCell(
                        if (canDeleteCompletedFiles) "删除文件" else "确定",
                        MaterialTheme.colorScheme.onSurface,
                        onDeleteWithFiles,
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogActionCell(
    title: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier = modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(title, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DownloadActionDialog(
    actions: List<BrowserDownloadDrawerAction>,
    columns: Int,
    onDismiss: () -> Unit,
) {
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Outlined.Download,
            tone = WebSessionBrowserMenuTone.DOWNLOADS,
            title = "请选择操作",
            modifier = Modifier.fillMaxWidth(if (columns == 1) 0.74f else 0.84f),
        ) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    actions.chunked(columns).forEach { rowActions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            rowActions.forEach { action ->
                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(WebSessionBrowserPopupItemHeight)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceContainer,
                                                RoundedCornerShape(12.dp),
                                            )
                                            .clickable(onClick = action.onClick),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(action.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            repeat(columns - rowActions.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameBrowserDownloadDialog(
    item: BrowserDownloadItem,
    mode: BrowserDownloadRenameMode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean,
) {
    var input by remember(item.id, mode) {
        mutableStateOf(browserDownloadRenameInput(item.fileName, mode))
    }
    var showError by remember(item.id, mode) { mutableStateOf(false) }
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Outlined.Download,
            tone = WebSessionBrowserMenuTone.DOWNLOADS,
            title = if (mode == BrowserDownloadRenameMode.SUFFIX) "修改后缀" else "重命名",
            modifier = Modifier.widthIn(min = 300.dp, max = 380.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                TextField(
                    value = input,
                    onValueChange = {
                        input = it
                        showError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (showError) {
                    Text("输入内容不能为空", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        onClick = {
                            if (!onConfirm(input)) {
                                showError = true
                            }
                        },
                    ) {
                        Text("确定", color = DownloadAccentColor)
                    }
                }
            }
        }
    }
}

private fun downloadSortModeLabel(mode: BrowserDownloadSortMode): String =
    when (mode) {
        BrowserDownloadSortMode.NEWEST -> "最新优先"
        BrowserDownloadSortMode.OLDEST -> "最早优先"
        BrowserDownloadSortMode.NAME -> "按名称"
    }

private fun downloadStatusFilterLabel(filter: BrowserDownloadStatusFilter): String =
    when (filter) {
        BrowserDownloadStatusFilter.ALL -> "全部"
        BrowserDownloadStatusFilter.ACTIVE -> "进行中"
        BrowserDownloadStatusFilter.QUEUED -> "等待中"
        BrowserDownloadStatusFilter.PAUSED -> "已暂停"
        BrowserDownloadStatusFilter.FAILED -> "失败"
        BrowserDownloadStatusFilter.CANCELED -> "已取消"
    }

private fun downloadCategoryLabel(category: BrowserDownloadCategory): String =
    when (category) {
        BrowserDownloadCategory.VIDEO -> "视频"
        BrowserDownloadCategory.AUDIO -> "音频"
        BrowserDownloadCategory.IMAGE -> "图片"
        BrowserDownloadCategory.APPLICATION -> "应用"
        BrowserDownloadCategory.ARCHIVE -> "压缩包"
        BrowserDownloadCategory.DOCUMENT -> "文档"
        BrowserDownloadCategory.OTHER -> "其他"
    }

private fun drawerStatusLabel(status: String): String =
    when (status) {
        "queued" -> "等待中"
        "connecting" -> "连接中"
        "downloading" -> "下载中"
        "paused" -> "已暂停"
        "completed" -> "已完成"
        "failed" -> "下载失败"
        "canceled" -> "已取消"
        else -> error("Unsupported browser download status: $status")
    }

@Composable
private fun drawerStatusColor(status: String): Color =
    when (status) {
        "completed" -> DownloadAccentColor
        "failed" -> MaterialTheme.colorScheme.error
        "downloading", "connecting" -> Color(0xFF1A73E8)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun drawerProgressText(item: BrowserDownloadItem): String {
    val sizeText =
        if (item.totalBytes > 0L) {
            "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
        } else {
            formatBytes(item.downloadedBytes)
        }
    val percentText =
        if (item.progress == null) {
            ""
        } else {
            "${(item.progress * 100f).toInt()}% · "
        }
    val speedText =
        if (item.speedBytesPerSecond > 0L) {
            " · ${formatBytes(item.speedBytesPerSecond)}/s"
        } else {
            ""
        }
    return "$percentText$sizeText$speedText"
}

private fun drawerMetaText(item: BrowserDownloadItem, showTime: Boolean): String {
    val values = mutableListOf<String>()
    val size = if (item.totalBytes > 0L) item.totalBytes else item.downloadedBytes
    if (size > 0L) {
        values += formatBytes(size)
    }
    values += downloadCategoryLabel(browserDownloadCategory(item))
    if (showTime) {
        values += formatBrowserDownloadTime(item.completedAt ?: item.createdAt)
    }
    return values.joinToString(" · ")
}

private fun formatBrowserDownloadTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
