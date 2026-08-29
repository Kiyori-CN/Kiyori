package com.ai.assistance.operit.ui.features.websession.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.kiyori.design.theme.KiyoriUiShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmark
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmarkArchive
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmarkCollection
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmarkDraft
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmarkFolder
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmarkMutation
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.bookmarkCount
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.buildBookmarkArchive
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.buildWebSessionBookmarkFolderTree
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.canMoveFolder
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.descendantFolderIds
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.isValidWebSessionBookmarkArchive
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.normalizeWebSessionBookmarkUrl
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class BookmarkSortMode {
    MANUAL,
    TITLE,
    TIME,
}

private sealed interface BookmarkDrawerRow {
    val key: String

    data class Folder(val value: WebSessionBookmarkFolder, val count: Int) : BookmarkDrawerRow {
        override val key: String = "folder_${value.id}"
    }

    data class Bookmark(val value: WebSessionBookmark) : BookmarkDrawerRow {
        override val key: String = "bookmark_${value.id}_${value.url}"
    }
}

private data class BookmarkBreadcrumb(val folderId: Long?, val title: String)

private val BookmarkArchiveJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WebSessionBookmarkSheet(
    folders: List<WebSessionBookmarkFolder>,
    bookmarks: List<WebSessionBookmark>,
    systemBackEnabled: Boolean,
    onMutation: (WebSessionBookmarkMutation) -> Unit,
    onOpenBookmark: (String) -> Unit,
    onOpenBookmarkInTab: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val collection = remember(folders, bookmarks) { WebSessionBookmarkCollection(folders, bookmarks) }
    var currentFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var secretMode by rememberSaveable { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf(BookmarkSortMode.MANUAL) }
    var dragSortMode by rememberSaveable { mutableStateOf(false) }
    var topMenuExpanded by remember { mutableStateOf(false) }
    var folderMenuId by remember { mutableStateOf<Long?>(null) }
    var bookmarkMenuId by remember { mutableStateOf<Long?>(null) }
    var editDraft by remember { mutableStateOf<Pair<Long?, WebSessionBookmarkDraft>?>(null) }
    var textDialog by remember { mutableStateOf<BookmarkTextDialogState?>(null) }
    var optionDialog by remember { mutableStateOf<BookmarkOptionDialogState?>(null) }
    var confirmDialog by remember { mutableStateOf<BookmarkConfirmDialogState?>(null) }

    val visibleFolderIds = remember(folders, bookmarks, secretMode) {
        collectVisibleBookmarkFolderIds(folders, bookmarks, secretMode)
    }
    LaunchedEffect(currentFolderId, visibleFolderIds) {
        if (currentFolderId != null && currentFolderId !in visibleFolderIds) currentFolderId = null
    }
    val currentFolder = folders.firstOrNull { it.id == currentFolderId && it.id in visibleFolderIds }
    val breadcrumbs = remember(folders, currentFolderId) { buildBookmarkBreadcrumbs(currentFolderId, folders) }
    val folderOptions = remember(folders, visibleFolderIds) {
        buildWebSessionBookmarkFolderTree(
            folders = folders.filter { folder -> folder.id in visibleFolderIds },
            secret = secretMode,
        ).map { entry -> WebSessionBookmarkFolderOption(entry.id, entry.title, entry.depth) }
    }
    val normalizedQuery = searchQuery.trim()
    val shownFolders =
        remember(folders, currentFolderId, visibleFolderIds, normalizedQuery, sortMode) {
            val source =
                if (normalizedQuery.isBlank()) {
                    folders.filter { it.parentId == currentFolderId && it.id in visibleFolderIds }
                } else {
                    folders.filter { it.id in visibleFolderIds && it.title.contains(normalizedQuery, true) }
                }
            sortFolders(source, sortMode)
        }
    val shownBookmarks =
        remember(bookmarks, currentFolderId, secretMode, normalizedQuery, sortMode) {
            val source =
                bookmarks.filter { bookmark ->
                    bookmark.secret == secretMode &&
                        if (normalizedQuery.isBlank()) {
                            bookmark.folderId == currentFolderId
                        } else {
                            bookmark.title.contains(normalizedQuery, true) ||
                                bookmark.url.contains(normalizedQuery, true)
                        }
                }
            sortBookmarks(source, sortMode)
        }
    val sourceRows =
        remember(shownFolders, shownBookmarks, collection, secretMode) {
            buildList {
                shownFolders.forEach { folder ->
                    add(BookmarkDrawerRow.Folder(folder, collection.bookmarkCount(folder.id, secretMode)))
                }
                shownBookmarks.forEach { add(BookmarkDrawerRow.Bookmark(it)) }
            }
        }
    var orderedRows by remember(sourceRows) { mutableStateOf(sourceRows) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (!dragSortMode || normalizedQuery.isNotBlank()) return@rememberReorderableLazyListState
        val moving = orderedRows[from.index]
        val target = orderedRows[to.index]
        if (moving::class != target::class) return@rememberReorderableLazyListState
        orderedRows = orderedRows.toMutableList().apply { add(to.index, removeAt(from.index)) }
        when (moving) {
            is BookmarkDrawerRow.Folder ->
                onMutation(
                    WebSessionBookmarkMutation.SetFolderOrder(
                        parentId = currentFolderId,
                        secret = secretMode,
                        orderedIds = orderedRows.filterIsInstance<BookmarkDrawerRow.Folder>().map { it.value.id },
                    ),
                )
            is BookmarkDrawerRow.Bookmark ->
                onMutation(
                    WebSessionBookmarkMutation.SetBookmarkOrder(
                        folderId = currentFolderId,
                        secret = secretMode,
                        orderedIds = orderedRows.filterIsInstance<BookmarkDrawerRow.Bookmark>().map { it.value.id },
                    ),
                )
        }
    }

    BackHandler(
        enabled =
            systemBackEnabled &&
                (normalizedQuery.isNotBlank() || currentFolderId != null || dragSortMode),
    ) {
        when {
            normalizedQuery.isNotBlank() -> searchQuery = ""
            dragSortMode -> dragSortMode = false
            currentFolderId != null -> currentFolderId = currentFolder?.parentId
        }
    }

    val hasAnchoredPopup = topMenuExpanded || folderMenuId != null || bookmarkMenuId != null
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            WebSessionDrawerHeader(
                title = currentFolder?.title ?: "我的书签",
                leadingIcon = Icons.Filled.Bookmark,
                tone = WebSessionBrowserMenuTone.BOOKMARKS,
                titleTakesRemainingSpace = true,
                actions = {
                    Box {
                        IconButton(onClick = { topMenuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "书签更多操作")
                        }
                        DropdownMenu(
                            expanded = topMenuExpanded,
                            onDismissRequest = { topMenuExpanded = false },
                            shape = WebSessionBrowserPopupShape,
                            containerColor = MaterialTheme.colorScheme.surface,
                            shadowElevation = WebSessionBrowserPopupElevation,
                        ) {
                    BookmarkTopMenuItem("新建书签") {
                        topMenuExpanded = false
                        editDraft = null to WebSessionBookmarkDraft("", "", "", currentFolderId)
                    }
                    BookmarkTopMenuItem("新建文件夹") {
                        topMenuExpanded = false
                        textDialog = BookmarkTextDialogState.NewFolder
                    }
                    BookmarkTopMenuItem("排序方式") {
                        topMenuExpanded = false
                        optionDialog = BookmarkOptionDialogState.Sort
                    }
                    BookmarkTopMenuItem("拖拽排序") {
                        topMenuExpanded = false
                        searchQuery = ""
                        sortMode = BookmarkSortMode.MANUAL
                        dragSortMode = !dragSortMode
                    }
                    BookmarkTopMenuItem("书签导出") {
                        topMenuExpanded = false
                        val archive = collection.buildBookmarkArchive(currentFolderId, secretMode)
                        copyBookmarkText(context, "Kiyori bookmarks", BookmarkArchiveJson.encodeToString(archive))
                        Toast.makeText(context, "书签已导出到剪贴板", Toast.LENGTH_SHORT).show()
                    }
                    BookmarkTopMenuItem("书签导入") {
                        topMenuExpanded = false
                        textDialog = BookmarkTextDialogState.Import(readClipboardText(context))
                    }
                    BookmarkTopMenuItem("秘密空间") {
                        topMenuExpanded = false
                        secretMode = !secretMode
                        currentFolderId = null
                        searchQuery = ""
                        dragSortMode = false
                        Toast.makeText(
                            context,
                            if (secretMode) "已进入秘密空间" else "已返回普通书签",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    BookmarkTopMenuItem("保存成功") {
                        topMenuExpanded = false
                        Toast.makeText(context, "当前书签数据已保存", Toast.LENGTH_SHORT).show()
                    }
                    BookmarkTopMenuItem("失效检测") {
                        topMenuExpanded = false
                        val checked = bookmarks.filter { it.secret == secretMode }
                        val invalid = checked.count { normalizeWebSessionBookmarkUrl(it.url) == null }
                        Toast.makeText(context, "已检测 ${checked.size} 个书签，发现 $invalid 个失效地址", Toast.LENGTH_SHORT).show()
                    }
                        }
                    }
                },
            )

            BookmarkSearchField(searchQuery, onValueChange = { searchQuery = it }, onClear = { searchQuery = "" })
            BookmarkBreadcrumbRow(breadcrumbs) { folderId ->
                currentFolderId = folderId
                searchQuery = ""
                dragSortMode = false
            }

            if (orderedRows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        WebSessionBrowserMenuIconBadge(
                            imageVector = Icons.Filled.Bookmark,
                            tone = WebSessionBrowserMenuTone.BOOKMARKS,
                            contentDescription = null,
                            containerSize = 46.dp,
                            iconSize = 24.dp,
                        )
                        Text(
                            text = if (normalizedQuery.isBlank()) "当前没有书签" else "没有匹配的书签",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (dragSortMode) 84.dp else 24.dp),
                ) {
                    items(orderedRows, key = { it.key }) { row ->
                    ReorderableItem(reorderableState, key = row.key) { isDragging ->
                        val dragModifier = if (dragSortMode) Modifier.longPressDraggableHandle() else Modifier
                        Surface(
                            color =
                                if (isDragging) {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                } else {
                                    Color.Transparent
                                },
                        ) {
                            when (row) {
                                is BookmarkDrawerRow.Folder ->
                                    BookmarkFolderRow(
                                        folder = row.value,
                                        count = row.count,
                                        modifier = dragModifier,
                                        menuExpanded = folderMenuId == row.value.id,
                                        menuEnabled = !dragSortMode,
                                        onClick = {
                                            currentFolderId = row.value.id
                                            searchQuery = ""
                                        },
                                        onLongClick = {
                                            folderMenuId = row.value.id
                                            bookmarkMenuId = null
                                        },
                                        onDismissMenu = { folderMenuId = null },
                                        onAction = { action ->
                                            folderMenuId = null
                                            handleFolderAction(
                                                action = action,
                                                folder = row.value,
                                                collection = collection,
                                                secretMode = secretMode,
                                                context = context,
                                                onMutation = onMutation,
                                                onTextDialog = { textDialog = it },
                                                onOptionDialog = { optionDialog = it },
                                                onConfirmDialog = { confirmDialog = it },
                                            )
                                        },
                                    )
                                is BookmarkDrawerRow.Bookmark ->
                                    BookmarkItemRow(
                                        bookmark = row.value,
                                        modifier = dragModifier,
                                        menuExpanded = bookmarkMenuId == row.value.id,
                                        menuEnabled = !dragSortMode,
                                        onClick = { onOpenBookmark(row.value.url) },
                                        onLongClick = {
                                            bookmarkMenuId = row.value.id
                                            folderMenuId = null
                                        },
                                        onDismissMenu = { bookmarkMenuId = null },
                                        onAction = { action ->
                                            bookmarkMenuId = null
                                            when (action) {
                                                "后台打开" -> {
                                                    onOpenBookmarkInTab(row.value.url, false)
                                                    Toast.makeText(context, "已在后台打开", Toast.LENGTH_SHORT).show()
                                                }
                                                "新窗口打开" -> {
                                                    onOpenBookmarkInTab(row.value.url, true)
                                                }
                                                "编辑书签" ->
                                                    editDraft =
                                                        row.value.id to
                                                            WebSessionBookmarkDraft(
                                                                row.value.title,
                                                                row.value.url,
                                                                row.value.iconUrl,
                                                                row.value.folderId,
                                                            )
                                                "拖拽排序" -> {
                                                    searchQuery = ""
                                                    sortMode = BookmarkSortMode.MANUAL
                                                    dragSortMode = true
                                                }
                                                "加到主页" -> {
                                                    if (row.value.secret) {
                                                        Toast.makeText(context, "秘密书签不能发送到主页", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        onMutation(WebSessionBookmarkMutation.AddBookmarkToHome(row.value.id))
                                                        Toast.makeText(context, "已添加到主页导航", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                "删除书签" -> confirmDialog = BookmarkConfirmDialogState.DeleteBookmark(row.value)
                                                "批量操作" -> optionDialog = BookmarkOptionDialogState.BookmarkBatch(row.value)
                                                "复制分享" -> shareBookmark(context, row.value)
                                            }
                                        },
                                    )
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 66.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f),
                    )
                    }
                }
            }
        }

        if (hasAnchoredPopup) {
            WebSessionBrowserPopupScrim(
                onDismissRequest = {
                    topMenuExpanded = false
                    folderMenuId = null
                    bookmarkMenuId = null
                },
            )
        }
    }

    editDraft?.let { (bookmarkId, draft) ->
        WebSessionBookmarkEditorDialog(
            title = if (bookmarkId == null) "新增书签" else "编辑书签",
            tone = WebSessionBrowserMenuTone.BOOKMARKS,
            initialDraft = draft,
            folderOptions = folderOptions,
            onDismiss = { editDraft = null },
            onConfirm = { confirmed ->
                if (normalizeWebSessionBookmarkUrl(confirmed.url) == null) {
                    Toast.makeText(context, "请输入有效的 HTTP 或 HTTPS 网址", Toast.LENGTH_SHORT).show()
                } else {
                    onMutation(
                        if (bookmarkId == null) {
                            WebSessionBookmarkMutation.SaveBookmark(confirmed, secretMode)
                        } else {
                            WebSessionBookmarkMutation.UpdateBookmark(bookmarkId, confirmed)
                        },
                    )
                    editDraft = null
                    Toast.makeText(context, "书签已保存", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    textDialog?.let { state ->
        WebSessionBookmarkTextDialog(
            title = state.title,
            initialValue = state.initialValue,
            confirmText = state.confirmText,
            onDismiss = { textDialog = null },
            onConfirm = { value ->
                when (state) {
                    BookmarkTextDialogState.NewFolder -> {
                        if (value.isNotBlank()) {
                            onMutation(WebSessionBookmarkMutation.CreateFolder(value, currentFolderId, secretMode))
                            textDialog = null
                        }
                    }
                    is BookmarkTextDialogState.RenameFolder -> {
                        if (value.isNotBlank()) {
                            onMutation(WebSessionBookmarkMutation.RenameFolder(state.folder.id, value))
                            textDialog = null
                        }
                    }
                    is BookmarkTextDialogState.Import -> {
                        val archive = decodeBookmarkArchive(value)
                        if (archive == null) {
                            Toast.makeText(context, "书签数据格式无效", Toast.LENGTH_SHORT).show()
                        } else {
                            onMutation(WebSessionBookmarkMutation.ImportArchive(archive, secretMode))
                            textDialog = null
                            Toast.makeText(context, "书签导入完成", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
        )
    }

    optionDialog?.let { state ->
        WebSessionBookmarkOptionsDialog(
            title = state.title,
            options = state.options,
            onDismiss = { optionDialog = null },
            onSelect = { option ->
                optionDialog = null
                when (state) {
                    BookmarkOptionDialogState.Sort -> {
                        sortMode =
                            when (option) {
                                "标题排序" -> BookmarkSortMode.TITLE
                                "时间排序" -> BookmarkSortMode.TIME
                                else -> BookmarkSortMode.MANUAL
                            }
                        dragSortMode = false
                    }
                    is BookmarkOptionDialogState.MoveFolder -> {
                        val parentId = state.targets.first { it.second == option }.first
                        if (collection.canMoveFolder(state.folder.id, parentId)) {
                            onMutation(WebSessionBookmarkMutation.MoveFolder(state.folder.id, parentId))
                        }
                    }
                    is BookmarkOptionDialogState.FolderBatch ->
                        if (option == "删除文件夹内全部书签") {
                            confirmDialog = BookmarkConfirmDialogState.DeleteFolderBookmarks(state.folder)
                        }
                    is BookmarkOptionDialogState.BookmarkBatch -> {
                        when (option) {
                            "移动书签" ->
                                optionDialog =
                                    BookmarkOptionDialogState.MoveBookmark(
                                        bookmark = state.bookmark,
                                        targets =
                                            buildList {
                                                add(null to "/")
                                                buildWebSessionBookmarkFolderTree(
                                                    folders = collection.folders,
                                                    secret = state.bookmark.secret,
                                                ).forEach { entry ->
                                                    add(
                                                        entry.id to
                                                            bookmarkFolderPath(
                                                                entry.id,
                                                                collection.folders,
                                                            ),
                                                    )
                                                }
                                            },
                                    )
                            else ->
                                onMutation(
                                    WebSessionBookmarkMutation.SetBookmarkSecret(
                                        state.bookmark.id,
                                        !state.bookmark.secret,
                                    ),
                                )
                        }
                    }
                    is BookmarkOptionDialogState.MoveBookmark -> {
                        val folderId =
                            state.targets
                                .first { target -> target.second == option }
                                .first
                        onMutation(WebSessionBookmarkMutation.MoveBookmark(state.bookmark.id, folderId))
                    }
                }
            },
        )
    }

    confirmDialog?.let { state ->
        WebSessionBookmarkConfirmDialog(
            title = state.title,
            message = state.message,
            onDismiss = { confirmDialog = null },
            onConfirm = {
                when (state) {
                    is BookmarkConfirmDialogState.DeleteFolder ->
                        onMutation(WebSessionBookmarkMutation.DeleteFolder(state.folder.id, secretMode))
                    is BookmarkConfirmDialogState.DeleteBookmark ->
                        onMutation(WebSessionBookmarkMutation.DeleteBookmark(state.bookmark.id))
                    is BookmarkConfirmDialogState.DeleteFolderBookmarks ->
                        onMutation(WebSessionBookmarkMutation.DeleteBookmarksInFolder(state.folder.id, secretMode))
                }
                confirmDialog = null
            },
        )
    }
}

@Composable
private fun BookmarkTopMenuItem(title: String, onClick: () -> Unit) {
    WebSessionBrowserDropdownItem(title = title, onClick = onClick)
}

@Composable
private fun BookmarkSearchField(value: String, onValueChange: (String) -> Unit, onClear: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(44.dp),
        shape = KiyoriUiShapes.field,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                "搜索书签标题、链接",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                            )
                        }
                        inner()
                    }
                },
            )
            if (value.isNotBlank()) {
                IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "清除书签搜索", modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun BookmarkBreadcrumbRow(
    breadcrumbs: List<BookmarkBreadcrumb>,
    onSelect: (Long?) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        breadcrumbs.forEachIndexed { index, segment ->
            if (index > 0) {
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
            }
            Text(
                text = segment.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clickable { onSelect(segment.folderId) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkFolderRow(
    folder: WebSessionBookmarkFolder,
    count: Int,
    modifier: Modifier,
    menuExpanded: Boolean,
    menuEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onAction: (String) -> Unit,
) {
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(modifier)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = if (menuEnabled) onLongClick else null,
                    )
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WebSessionBrowserMenuIconBadge(
                imageVector = Icons.Outlined.Folder,
                tone = WebSessionBrowserMenuTone.BOOKMARKS,
                contentDescription = null,
                containerSize = 38.dp,
                iconSize = 22.dp,
                shape = CircleShape,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(folder.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${count}个书签", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) {
            DropdownMenu(
                expanded = menuEnabled && menuExpanded,
                onDismissRequest = onDismissMenu,
                shape = WebSessionBrowserPopupShape,
                shadowElevation = WebSessionBrowserPopupElevation,
            ) {
                listOf("重命名文件夹", "移动文件夹", "发送到主页", "删除文件夹", "批量操作").forEach { action ->
                    WebSessionBrowserDropdownItem(title = action, onClick = { onAction(action) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkItemRow(
    bookmark: WebSessionBookmark,
    modifier: Modifier,
    menuExpanded: Boolean,
    menuEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onAction: (String) -> Unit,
) {
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(modifier)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = if (menuEnabled) onLongClick else null,
                    )
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookmarkFavicon(bookmark.iconUrl)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(bookmark.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    bookmark.url,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) {
            DropdownMenu(
                expanded = menuEnabled && menuExpanded,
                onDismissRequest = onDismissMenu,
                shape = WebSessionBrowserPopupShape,
                shadowElevation = WebSessionBrowserPopupElevation,
            ) {
                listOf("后台打开", "新窗口打开", "编辑书签", "拖拽排序", "加到主页", "删除书签", "批量操作", "复制分享")
                    .forEach { action ->
                        WebSessionBrowserDropdownItem(title = action, onClick = { onAction(action) })
                    }
                }
        }
    }
}

@Composable
private fun BookmarkFavicon(iconUrl: String) {
    val colors = WebSessionBrowserMenuTone.BOOKMARKS.resolveColors()
    Surface(modifier = Modifier.size(38.dp), shape = CircleShape, color = colors.container) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.Language,
                contentDescription = null,
                tint = colors.icon,
                modifier = Modifier.size(21.dp),
            )
            if (iconUrl.isNotBlank()) {
                val painter = rememberAsyncImagePainter(iconUrl)
                if (painter.state !is AsyncImagePainter.State.Error) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.size(25.dp).clip(RoundedCornerShape(5.dp)),
                    )
                }
            }
        }
    }
}

private fun handleFolderAction(
    action: String,
    folder: WebSessionBookmarkFolder,
    collection: WebSessionBookmarkCollection,
    secretMode: Boolean,
    context: Context,
    onMutation: (WebSessionBookmarkMutation) -> Unit,
    onTextDialog: (BookmarkTextDialogState) -> Unit,
    onOptionDialog: (BookmarkOptionDialogState) -> Unit,
    onConfirmDialog: (BookmarkConfirmDialogState) -> Unit,
) {
    when (action) {
        "重命名文件夹" -> onTextDialog(BookmarkTextDialogState.RenameFolder(folder))
        "移动文件夹" -> {
            val invalidIds = collection.descendantFolderIds(folder.id) + folder.id
            val targets =
                buildList {
                    add(null to "根目录")
                    collection.folders
                        .filter { it.secret == secretMode && it.id !in invalidIds }
                        .forEach { add(it.id to bookmarkFolderPath(it.id, collection.folders)) }
                }
            onOptionDialog(BookmarkOptionDialogState.MoveFolder(folder, targets))
        }
        "发送到主页" -> {
            if (secretMode) {
                Toast.makeText(context, "秘密文件夹不能发送到主页", Toast.LENGTH_SHORT).show()
            } else {
                onMutation(WebSessionBookmarkMutation.AddFolderToHome(folder.id))
                Toast.makeText(context, "文件夹书签已发送到主页导航", Toast.LENGTH_SHORT).show()
            }
        }
        "删除文件夹" -> onConfirmDialog(BookmarkConfirmDialogState.DeleteFolder(folder))
        "批量操作" -> onOptionDialog(BookmarkOptionDialogState.FolderBatch(folder))
    }
}

private fun sortFolders(
    folders: List<WebSessionBookmarkFolder>,
    mode: BookmarkSortMode,
): List<WebSessionBookmarkFolder> =
    when (mode) {
        BookmarkSortMode.MANUAL -> folders.sortedBy { it.order }
        BookmarkSortMode.TITLE -> folders.sortedBy { it.title }
        BookmarkSortMode.TIME -> folders.sortedByDescending { it.createdAt }
    }

private fun sortBookmarks(bookmarks: List<WebSessionBookmark>, mode: BookmarkSortMode): List<WebSessionBookmark> =
    when (mode) {
        BookmarkSortMode.MANUAL -> bookmarks.sortedBy { it.order }
        BookmarkSortMode.TITLE -> bookmarks.sortedBy { it.title }
        BookmarkSortMode.TIME -> bookmarks.sortedByDescending { it.createdAt }
    }

private fun collectVisibleBookmarkFolderIds(
    folders: List<WebSessionBookmarkFolder>,
    bookmarks: List<WebSessionBookmark>,
    secret: Boolean,
): Set<Long> {
    val folderById = folders.associateBy { it.id }
    val ids = folders.filter { it.secret == secret }.mapTo(mutableSetOf()) { it.id }
    bookmarks.filter { it.secret == secret }.mapNotNull { it.folderId }.forEach { folderId ->
        var current: Long? = folderId
        while (current != null) {
            ids += current
            current = folderById[current]?.parentId
        }
    }
    return ids
}

private fun buildBookmarkBreadcrumbs(
    folderId: Long?,
    folders: List<WebSessionBookmarkFolder>,
): List<BookmarkBreadcrumb> {
    val folderById = folders.associateBy { it.id }
    val result = mutableListOf<BookmarkBreadcrumb>()
    var current = folderId
    while (current != null) {
        val folder = folderById[current] ?: break
        result += BookmarkBreadcrumb(folder.id, folder.title)
        current = folder.parentId
    }
    return listOf(BookmarkBreadcrumb(null, "根目录")) + result.asReversed()
}

internal fun bookmarkFolderPath(folderId: Long, folders: List<WebSessionBookmarkFolder>): String =
    buildBookmarkBreadcrumbs(folderId, folders).joinToString(" › ") { it.title }

private fun decodeBookmarkArchive(raw: String): WebSessionBookmarkArchive? {
    if (raw.isBlank()) return null
    return try {
        BookmarkArchiveJson.decodeFromString<WebSessionBookmarkArchive>(raw).takeIf(::isValidWebSessionBookmarkArchive)
    } catch (error: IllegalArgumentException) {
        null
    }
}

private fun copyBookmarkText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun readClipboardText(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
        .orEmpty()
}

private fun shareBookmark(context: Context, bookmark: WebSessionBookmark) {
    val text = "${bookmark.title}\n${bookmark.url}"
    copyBookmarkText(context, bookmark.title, text)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, bookmark.title)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            "复制分享",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private sealed interface BookmarkTextDialogState {
    val title: String
    val initialValue: String
    val confirmText: String

    data object NewFolder : BookmarkTextDialogState {
        override val title = "新建文件夹"
        override val initialValue = ""
        override val confirmText = "确定"
    }

    data class RenameFolder(val folder: WebSessionBookmarkFolder) : BookmarkTextDialogState {
        override val title = "重命名文件夹"
        override val initialValue = folder.title
        override val confirmText = "确定"
    }

    data class Import(override val initialValue: String) : BookmarkTextDialogState {
        override val title = "书签导入"
        override val confirmText = "导入"
    }
}

private sealed interface BookmarkOptionDialogState {
    val title: String
    val options: List<String>

    data object Sort : BookmarkOptionDialogState {
        override val title = "排序方式"
        override val options = listOf("手动排序", "标题排序", "时间排序")
    }

    data class MoveFolder(
        val folder: WebSessionBookmarkFolder,
        val targets: List<Pair<Long?, String>>,
    ) : BookmarkOptionDialogState {
        override val title = "移动文件夹"
        override val options = targets.map { it.second }
    }

    data class FolderBatch(val folder: WebSessionBookmarkFolder) : BookmarkOptionDialogState {
        override val title = "批量操作"
        override val options = listOf("删除文件夹内全部书签")
    }

    data class BookmarkBatch(val bookmark: WebSessionBookmark) : BookmarkOptionDialogState {
        override val title = "批量操作"
        override val options =
            listOf("移动书签", if (bookmark.secret) "移出秘密空间" else "移入秘密空间")
    }

    data class MoveBookmark(
        val bookmark: WebSessionBookmark,
        val targets: List<Pair<Long?, String>>,
    ) : BookmarkOptionDialogState {
        override val title = "移动书签"
        override val options = targets.map { target -> target.second }
    }
}

private sealed interface BookmarkConfirmDialogState {
    val title: String
    val message: String

    data class DeleteFolder(val folder: WebSessionBookmarkFolder) : BookmarkConfirmDialogState {
        override val title = "删除文件夹"
        override val message = "确定删除“${folder.title}”和里面的全部书签吗？"
    }

    data class DeleteBookmark(val bookmark: WebSessionBookmark) : BookmarkConfirmDialogState {
        override val title = "删除书签"
        override val message = "确定删除“${bookmark.title}”吗？"
    }

    data class DeleteFolderBookmarks(val folder: WebSessionBookmarkFolder) : BookmarkConfirmDialogState {
        override val title = "批量操作"
        override val message = "确定删除“${folder.title}”内的全部书签吗？子文件夹会保留。"
    }
}
