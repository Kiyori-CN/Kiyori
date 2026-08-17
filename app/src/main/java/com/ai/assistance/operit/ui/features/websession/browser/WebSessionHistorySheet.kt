package com.ai.assistance.operit.ui.features.websession.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmarkDraft
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmarkFolder
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmarkMutation
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryActionKind
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryCategory
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryDeleteRange
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryEntryKey
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryFilter
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryMediaOrigin
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.actionKind
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.buildWebSessionBookmarkFolderTree
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.entryKey
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.filterWebSessionHistoryEntries
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.normalizeWebSessionBookmarkUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.validSourcePageUrl
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
import java.text.DateFormat
import java.util.Date

private data class HistoryDeleteRequest(
    val entryTitle: String?,
    val entryKeys: Set<WebSessionHistoryEntryKey>,
)

@Composable
internal fun WebSessionHistorySheet(
    entries: List<WebSessionHistoryEntry>,
    bookmarkFolders: List<WebSessionBookmarkFolder>,
    onOpenEntry: (WebSessionHistoryEntry) -> Boolean,
    onOpenWebUrl: (String) -> Unit,
    onBookmarkMutation: (WebSessionBookmarkMutation) -> Unit,
    onDeleteHistory: (WebSessionHistoryCategory?, Long?) -> Unit,
    onDeleteHistoryEntries: (Set<WebSessionHistoryEntryKey>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val linkCopiedMessage = stringResource(R.string.web_session_history_link_copied)
    val titleCopiedMessage = stringResource(R.string.web_session_history_title_copied)
    val invalidBookmarkUrlMessage =
        stringResource(R.string.web_session_history_invalid_bookmark_url)
    val bookmarkSavedMessage = stringResource(R.string.web_session_history_bookmark_saved)
    var selectedFilter by rememberSaveable { mutableStateOf(WebSessionHistoryFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var showDeleteRangeSheet by remember { mutableStateOf(false) }
    var actionEntry by remember { mutableStateOf<WebSessionHistoryEntry?>(null) }
    var bookmarkDraft by remember { mutableStateOf<WebSessionBookmarkDraft?>(null) }
    var deleteRequest by remember { mutableStateOf<HistoryDeleteRequest?>(null) }
    var batchMode by rememberSaveable { mutableStateOf(false) }
    var selectedEntryKeys by remember { mutableStateOf(emptySet<WebSessionHistoryEntryKey>()) }
    val filteredEntries =
        remember(entries, selectedFilter, query) {
            filterWebSessionHistoryEntries(entries, selectedFilter, query)
        }
    val filteredEntryKeys = remember(filteredEntries) { filteredEntries.mapTo(mutableSetOf()) { it.entryKey() } }
    val allFilteredEntriesSelected =
        filteredEntryKeys.isNotEmpty() && filteredEntryKeys.all(selectedEntryKeys::contains)
    val selectedCategoryCount =
        remember(entries, selectedFilter) {
            entries.count { entry ->
                selectedFilter.category == null || entry.category == selectedFilter.category
            }
        }
    val bookmarkFolderOptions =
        remember(bookmarkFolders) {
            buildWebSessionBookmarkFolderTree(
                folders = bookmarkFolders,
                secret = false,
            ).map { entry ->
                WebSessionBookmarkFolderOption(
                    id = entry.id,
                    title = entry.title,
                    depth = entry.depth,
                )
            }
        }

    fun leaveBatchMode() {
        batchMode = false
        selectedEntryKeys = emptySet()
    }

    fun enterBatchMode(entry: WebSessionHistoryEntry) {
        actionEntry = null
        batchMode = true
        selectedEntryKeys = setOf(entry.entryKey())
    }

    BackHandler(enabled = batchMode) {
        leaveBatchMode()
    }

    LaunchedEffect(entries) {
        val availableKeys = entries.mapTo(mutableSetOf()) { entry -> entry.entryKey() }
        selectedEntryKeys = selectedEntryKeys.intersect(availableKeys)
        actionEntry = actionEntry?.takeIf { entry -> entry.entryKey() in availableKeys }
    }
    LaunchedEffect(batchMode, filteredEntryKeys) {
        if (batchMode) {
            // Batch controls describe the current search/filter result. Removing hidden selections
            // keeps the visible count, select-all state, and destructive confirmation consistent.
            selectedEntryKeys = selectedEntryKeys.intersect(filteredEntryKeys)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        WebSessionDrawerHeader(
            title =
                if (batchMode) {
                    pluralStringResource(
                        R.plurals.web_session_history_selected_count,
                        selectedEntryKeys.size,
                        selectedEntryKeys.size,
                    )
                } else {
                    stringResource(R.string.web_session_history_title)
                },
            leadingIcon = Icons.Filled.History,
            tone = WebSessionBrowserMenuTone.HISTORY,
            countText =
                if (batchMode) {
                    null
                } else {
                    pluralStringResource(
                        R.plurals.web_session_history_count,
                        entries.size,
                        entries.size,
                    )
                },
            navigationIcon =
                if (batchMode) {
                    {
                        IconButton(onClick = ::leaveBatchMode, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription =
                                    stringResource(R.string.web_session_history_batch_cancel),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    null
                },
            actions = {
                if (batchMode) {
                    HistoryHeaderAction(
                        title =
                            stringResource(
                                if (allFilteredEntriesSelected) {
                                    R.string.web_session_history_deselect_all
                                } else {
                                    R.string.web_session_history_select_all
                                }
                            ),
                        enabled = filteredEntryKeys.isNotEmpty(),
                        color = WebSessionBrowserMenuTone.HISTORY.resolveColors().icon,
                        onClick = {
                            selectedEntryKeys =
                                if (allFilteredEntriesSelected) {
                                    selectedEntryKeys - filteredEntryKeys
                                } else {
                                    selectedEntryKeys + filteredEntryKeys
                                }
                        },
                    )
                    HistoryHeaderAction(
                        title = stringResource(R.string.web_session_history_delete),
                        enabled = selectedEntryKeys.isNotEmpty(),
                        color = KiyoriSemanticTone.RED.resolveColors().icon,
                        onClick = {
                            deleteRequest =
                                HistoryDeleteRequest(
                                    entryTitle = null,
                                    entryKeys = selectedEntryKeys,
                                )
                        },
                    )
                } else {
                    HistoryHeaderAction(
                        title = stringResource(R.string.web_session_history_delete),
                        enabled = selectedCategoryCount > 0,
                        color = KiyoriSemanticTone.RED.resolveColors().icon,
                        onClick = { showDeleteRangeSheet = true },
                    )
                }
            },
        )

        HistorySearchField(
            value = query,
            onValueChange = { query = it },
            onClear = { query = "" },
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WebSessionHistoryFilter.entries.forEach { filter ->
                HistoryFilterChip(
                    label = historyFilterLabel(filter),
                    count =
                        entries.count { entry ->
                            filter.category == null || entry.category == filter.category
                        },
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                )
            }
        }

        if (filteredEntries.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        if (entries.isEmpty()) {
                            stringResource(R.string.web_session_history_empty)
                        } else {
                            stringResource(R.string.web_session_history_empty_filter)
                        },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentPadding =
                    PaddingValues(
                        start = 10.dp,
                        top = 8.dp,
                        end = 10.dp,
                        bottom = 18.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(
                    items = filteredEntries,
                    key = { entry -> "${entry.category}_${entry.url}_${entry.visitedAt}" },
                ) { entry ->
                    HistoryEntryRow(
                        entry = entry,
                        batchMode = batchMode,
                        selected = entry.entryKey() in selectedEntryKeys,
                        onClick = {
                            if (batchMode) {
                                val key = entry.entryKey()
                                selectedEntryKeys =
                                    if (key in selectedEntryKeys) {
                                        selectedEntryKeys - key
                                    } else {
                                        selectedEntryKeys + key
                                    }
                            } else {
                                onOpenEntry(entry)
                            }
                        },
                        onLongClick =
                            entry.actionKind()?.let {
                                {
                                    if (!batchMode) {
                                        actionEntry = entry
                                    }
                                }
                            },
                    )
                }
            }
        }
    }

    if (showDeleteRangeSheet) {
        HistoryDeleteRangeSheet(
            onDismiss = { showDeleteRangeSheet = false },
            onSelectRange = { range ->
                onDeleteHistory(
                    selectedFilter.category,
                    range.cutoffTimeMillis(System.currentTimeMillis()),
                )
                showDeleteRangeSheet = false
            },
        )
    }

    actionEntry?.let { entry ->
        val actionKind = entry.actionKind()
        if (actionKind != null) {
            val sourcePageUrl = entry.validSourcePageUrl()
            WebSessionHistoryActionDialog(
                entry = entry,
                actionKind = actionKind,
                sourcePageUrl = sourcePageUrl,
                onDismiss = { actionEntry = null },
                onOpenEntry = {
                    if (onOpenEntry(entry)) {
                        actionEntry = null
                    }
                },
                onOpenSourcePage = {
                    sourcePageUrl?.let(onOpenWebUrl)
                    actionEntry = null
                },
                onAddBookmark = {
                    bookmarkDraft =
                        WebSessionBookmarkDraft(
                            title = entry.title,
                            url = entry.url,
                            iconUrl = buildWebSessionFaviconUrl(entry.url),
                            folderId = null,
                        )
                    actionEntry = null
                },
                onCopyLink = {
                    copyHistoryText(context, entry.title, entry.url)
                    Toast.makeText(
                        context,
                        linkCopiedMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                    actionEntry = null
                },
                onCopyTitle = {
                    copyHistoryText(context, entry.title, entry.title)
                    Toast.makeText(
                        context,
                        titleCopiedMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                    actionEntry = null
                },
                onDeleteEntry = {
                    deleteRequest =
                        HistoryDeleteRequest(
                            entryTitle = entry.title,
                            entryKeys = setOf(entry.entryKey()),
                        )
                    actionEntry = null
                },
                onStartBatchDelete = { enterBatchMode(entry) },
            )
        }
    }

    bookmarkDraft?.let { draft ->
        WebSessionBookmarkEditorDialog(
            title = stringResource(R.string.web_session_history_add_bookmark),
            tone = WebSessionBrowserMenuTone.ADD_BOOKMARK,
            initialDraft = draft,
            folderOptions = bookmarkFolderOptions,
            onDismiss = { bookmarkDraft = null },
            onConfirm = { confirmed ->
                if (normalizeWebSessionBookmarkUrl(confirmed.url) == null) {
                    Toast.makeText(
                        context,
                        invalidBookmarkUrlMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    onBookmarkMutation(
                        WebSessionBookmarkMutation.SaveBookmark(
                            draft = confirmed,
                            secret = false,
                        )
                    )
                    bookmarkDraft = null
                    Toast.makeText(
                        context,
                        bookmarkSavedMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }

    deleteRequest?.let { request ->
        WebSessionHistoryDeleteConfirmationDialog(
            entryTitle = request.entryTitle,
            selectedCount = request.entryKeys.size,
            onDismiss = { deleteRequest = null },
            onConfirm = {
                onDeleteHistoryEntries(request.entryKeys)
                deleteRequest = null
                if (batchMode) {
                    leaveBatchMode()
                }
            },
        )
    }
}

@Composable
private fun HistoryHeaderAction(
    title: String,
    enabled: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color =
            if (enabled) {
                color
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
            },
        modifier =
            Modifier
                .height(44.dp)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 8.dp, vertical = 13.dp),
    )
}

@Composable
private fun HistorySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val orangeColors = WebSessionBrowserMenuTone.HISTORY.resolveColors()
    Surface(
        modifier = modifier.fillMaxWidth().height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
                cursorBrush = SolidColor(orangeColors.icon),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                text = stringResource(R.string.web_session_history_search_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (value.isNotBlank()) {
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.clear),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val orangeColors = WebSessionBrowserMenuTone.HISTORY.resolveColors()
    Surface(
        modifier = Modifier.height(36.dp).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color =
            if (selected) {
                orangeColors.container
            } else {
                MaterialTheme.colorScheme.surface
            },
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selected) orangeColors.icon else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "$label $count",
                color = if (selected) orangeColors.icon else MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryEntryRow(
    entry: WebSessionHistoryEntry,
    batchMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KiyoriSemanticIconBadge(
                imageVector = historyCategoryIcon(entry.category),
                tone = historyCategoryTone(entry.category),
                contentDescription = null,
                containerSize = 38.dp,
                iconSize = 20.dp,
                shape = RoundedCornerShape(10.dp),
            )
            Column(
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = entry.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.url,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = historyEntryKindLabel(entry),
                        color = historyCategoryTone(entry.category).resolveColors().icon,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    entry.sourcePageUrl.takeIf(String::isNotBlank)?.let { sourcePageUrl ->
                        Text(
                            text = sourcePageUrl,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = dateFormat.format(Date(entry.visitedAt)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.5.sp,
                    )
                }
            }
            if (batchMode) {
                Icon(
                    imageVector =
                        if (selected) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Filled.RadioButtonUnchecked
                        },
                    contentDescription =
                        stringResource(
                            if (selected) {
                                R.string.web_session_history_selected
                            } else {
                                R.string.web_session_history_not_selected
                            }
                        ),
                    tint =
                        if (selected) {
                            WebSessionBrowserMenuTone.HISTORY.resolveColors().icon
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.padding(start = 8.dp).size(22.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDeleteRangeSheet(
    onDismiss: () -> Unit,
    onSelectRange: (WebSessionHistoryDeleteRange) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            WebSessionBrowserDialogHeader(
                icon = Icons.Filled.History,
                tone = WebSessionBrowserMenuTone.HISTORY,
                title = stringResource(R.string.web_session_history_delete_range_title),
            )
            HorizontalDivider(
                color = WebSessionBrowserMenuTone.HISTORY.resolveColors().icon.copy(alpha = 0.16f),
            )
            WebSessionHistoryDeleteRange.entries.forEach { range ->
                Text(
                    text = historyDeleteRangeLabel(range),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onSelectRange(range) }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Text(
                text = stringResource(R.string.web_session_history_delete_cancel),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun historyFilterLabel(filter: WebSessionHistoryFilter): String =
    stringResource(
        when (filter) {
            WebSessionHistoryFilter.ALL -> R.string.web_session_history_filter_all
            WebSessionHistoryFilter.WEB -> R.string.web_session_history_filter_web
            WebSessionHistoryFilter.VIDEO -> R.string.web_session_history_filter_video
            WebSessionHistoryFilter.MUSIC -> R.string.web_session_history_filter_music
            WebSessionHistoryFilter.NOVEL -> R.string.web_session_history_filter_novel
            WebSessionHistoryFilter.OTHER -> R.string.web_session_history_filter_other
        }
    )

@Composable
private fun historyEntryKindLabel(entry: WebSessionHistoryEntry): String =
    when (entry.category) {
        WebSessionHistoryCategory.WEB -> stringResource(R.string.web_session_history_kind_web)
        WebSessionHistoryCategory.VIDEO ->
            if (entry.mediaOrigin == WebSessionHistoryMediaOrigin.LOCAL) {
                stringResource(R.string.web_session_history_kind_local_video)
            } else {
                stringResource(R.string.web_session_history_kind_online_video)
            }
        WebSessionHistoryCategory.MUSIC -> stringResource(R.string.web_session_history_filter_music)
        WebSessionHistoryCategory.NOVEL -> stringResource(R.string.web_session_history_filter_novel)
        WebSessionHistoryCategory.OTHER -> stringResource(R.string.web_session_history_filter_other)
    }

@Composable
private fun historyDeleteRangeLabel(range: WebSessionHistoryDeleteRange): String =
    stringResource(
        when (range) {
            WebSessionHistoryDeleteRange.PAST_HOUR ->
                R.string.web_session_history_delete_past_hour
            WebSessionHistoryDeleteRange.PAST_DAY ->
                R.string.web_session_history_delete_past_day
            WebSessionHistoryDeleteRange.PAST_WEEK ->
                R.string.web_session_history_delete_past_week
            WebSessionHistoryDeleteRange.ALL_TIME ->
                R.string.web_session_history_delete_all_time
        }
    )

private fun historyCategoryIcon(category: WebSessionHistoryCategory): ImageVector =
    when (category) {
        WebSessionHistoryCategory.WEB -> Icons.Filled.Web
        WebSessionHistoryCategory.VIDEO -> Icons.Filled.PlayArrow
        WebSessionHistoryCategory.MUSIC -> Icons.Filled.MusicNote
        WebSessionHistoryCategory.NOVEL -> Icons.AutoMirrored.Filled.LibraryBooks
        WebSessionHistoryCategory.OTHER -> Icons.Filled.History
    }

private fun historyCategoryTone(category: WebSessionHistoryCategory): KiyoriSemanticTone =
    when (category) {
        WebSessionHistoryCategory.WEB -> KiyoriSemanticTone.BLUE
        WebSessionHistoryCategory.VIDEO -> KiyoriSemanticTone.CYAN
        WebSessionHistoryCategory.MUSIC -> KiyoriSemanticTone.PINK
        WebSessionHistoryCategory.NOVEL -> KiyoriSemanticTone.PURPLE
        WebSessionHistoryCategory.OTHER -> KiyoriSemanticTone.ORANGE
    }

private fun copyHistoryText(
    context: Context,
    label: String,
    text: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
