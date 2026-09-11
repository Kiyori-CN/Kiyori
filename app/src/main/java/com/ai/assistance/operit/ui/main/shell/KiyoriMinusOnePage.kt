package com.ai.assistance.operit.ui.main.shell

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadManager
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmark
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryStore
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuIconBadge
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuTone
import com.ai.assistance.operit.ui.features.websession.browser.resolveColors

internal enum class KiyoriMinusOneDataAction {
    NONE,
    OPEN_BOOKMARK_DRAWER,
    OPEN_HISTORY_DRAWER,
    OPEN_DOWNLOAD_DRAWER,
}

internal data class KiyoriMinusOneDataItem(
    val title: String,
    val count: Int,
    val icon: ImageVector,
    val tone: WebSessionBrowserMenuTone,
    val action: KiyoriMinusOneDataAction = KiyoriMinusOneDataAction.NONE,
)

internal data class KiyoriMinusOneQuickTool(
    val title: String,
    val iconResId: Int,
    val tone: WebSessionBrowserMenuTone,
)

internal val kiyoriMinusOneDataItems =
    listOf(
        KiyoriMinusOneDataItem(
            title = "收藏",
            count = 0,
            icon = Icons.Default.Favorite,
            tone = WebSessionBrowserMenuTone.ADD_BOOKMARK,
        ),
        KiyoriMinusOneDataItem(
            title = "书签",
            count = 0,
            icon = Icons.Default.Bookmark,
            tone = WebSessionBrowserMenuTone.BOOKMARKS,
            action = KiyoriMinusOneDataAction.OPEN_BOOKMARK_DRAWER,
        ),
        KiyoriMinusOneDataItem(
            title = "历史",
            count = 0,
            icon = Icons.Default.History,
            tone = WebSessionBrowserMenuTone.HISTORY,
            action = KiyoriMinusOneDataAction.OPEN_HISTORY_DRAWER,
        ),
        KiyoriMinusOneDataItem(
            title = "下载",
            count = 0,
            icon = Icons.Outlined.Download,
            tone = WebSessionBrowserMenuTone.DOWNLOADS,
            action = KiyoriMinusOneDataAction.OPEN_DOWNLOAD_DRAWER,
        ),
    )

internal val kiyoriMinusOneQuickTools =
    listOf(
        KiyoriMinusOneQuickTool(
            "新版",
            R.drawable.ic_kiyori_minus_one_new,
            WebSessionBrowserMenuTone.PAGE_SOURCE,
        ),
        KiyoriMinusOneQuickTool(
            "手册",
            R.drawable.ic_kiyori_minus_one_manual,
            WebSessionBrowserMenuTone.DIAGNOSTICS,
        ),
        KiyoriMinusOneQuickTool(
            "版本",
            R.drawable.ic_kiyori_minus_one_version,
            WebSessionBrowserMenuTone.PLUGINS,
        ),
        KiyoriMinusOneQuickTool(
            "搜索",
            R.drawable.ic_kiyori_minus_one_search,
            WebSessionBrowserMenuTone.AI_DIALOGUE,
        ),
        KiyoriMinusOneQuickTool(
            "工具箱",
            R.drawable.ic_kiyori_minus_one_toolbox,
            WebSessionBrowserMenuTone.TOOLBOX,
        ),
        KiyoriMinusOneQuickTool(
            "清理",
            R.drawable.ic_kiyori_minus_one_clean,
            WebSessionBrowserMenuTone.AD_MARKING,
        ),
        KiyoriMinusOneQuickTool(
            "备份",
            R.drawable.ic_kiyori_minus_one_backup,
            WebSessionBrowserMenuTone.DOWNLOADS,
        ),
        KiyoriMinusOneQuickTool(
            "退出",
            R.drawable.ic_kiyori_minus_one_exit,
            WebSessionBrowserMenuTone.EXIT_BROWSER,
        ),
    )

@Composable
internal fun KiyoriMinusOnePage(
    onOpenBookmarkDrawer: () -> Unit,
    onOpenHistoryDrawer: () -> Unit,
    onOpenDownloadDrawer: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val downloadManager = remember(context) { BrowserDownloadManager.getInstance(context) }
    val historyStore = remember(context) { WebSessionHistoryStore.getInstance(context) }
    val downloadTasks by downloadManager.taskSnapshots.collectAsState()
    val bookmarks by historyStore.bookmarksFlow.collectAsState(initial = emptyList<WebSessionBookmark>())
    val history by historyStore.historyFlow.collectAsState(initial = emptyList())
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
    ) {
        KiyoriMinusOneTopBar(onClose = onClose)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KiyoriMinusOneDataSection(
                bookmarkCount = bookmarks.count { bookmark -> !bookmark.secret },
                historyCount = history.size,
                downloadCount = downloadTasks.size,
                onOpenBookmarkDrawer = onOpenBookmarkDrawer,
                onOpenHistoryDrawer = onOpenHistoryDrawer,
                onOpenDownloadDrawer = onOpenDownloadDrawer,
            )
            KiyoriMinusOneQuickToolsSection()
        }
    }
}

@Composable
private fun KiyoriMinusOneTopBar(onClose: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(start = 20.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "负一屏",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "关闭负一屏",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun KiyoriMinusOneDataSection(
    bookmarkCount: Int,
    historyCount: Int,
    downloadCount: Int,
    onOpenBookmarkDrawer: () -> Unit,
    onOpenHistoryDrawer: () -> Unit,
    onOpenDownloadDrawer: () -> Unit,
) {
    Text(
        text = "我的数据",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            kiyoriMinusOneDataItems.forEach { item ->
                val colors = item.tone.resolveColors()
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        colors.container,
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                ),
                            )
                            .clickable {
                                when (item.action) {
                                    KiyoriMinusOneDataAction.NONE -> Unit
                                    KiyoriMinusOneDataAction.OPEN_BOOKMARK_DRAWER ->
                                        onOpenBookmarkDrawer()
                                    KiyoriMinusOneDataAction.OPEN_HISTORY_DRAWER ->
                                        onOpenHistoryDrawer()
                                    KiyoriMinusOneDataAction.OPEN_DOWNLOAD_DRAWER ->
                                        onOpenDownloadDrawer()
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            WebSessionBrowserMenuIconBadge(
                                imageVector = item.icon,
                                tone = item.tone,
                                contentDescription = null,
                                containerSize = 38.dp,
                                iconSize = 20.dp,
                            )
                            Text(
                                text = item.title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text =
                                when (item.action) {
                                    KiyoriMinusOneDataAction.OPEN_BOOKMARK_DRAWER -> bookmarkCount.toString()
                                    KiyoriMinusOneDataAction.OPEN_HISTORY_DRAWER -> historyCount.toString()
                                    KiyoriMinusOneDataAction.OPEN_DOWNLOAD_DRAWER -> downloadCount.toString()
                                    KiyoriMinusOneDataAction.NONE -> item.count.toString()
                                },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.icon,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KiyoriMinusOneQuickToolsSection() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "快捷工具",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                kiyoriMinusOneQuickTools.chunked(4).forEach { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowItems.forEach { tool ->
                            Column(
                                modifier =
                                    Modifier
                                        .width(64.dp)
                                        .alpha(0.58f)
                                        .semantics { disabled() },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                WebSessionBrowserMenuIconBadge(
                                    painter = painterResource(tool.iconResId),
                                    tone = tool.tone,
                                    contentDescription = tool.title,
                                    containerSize = 40.dp,
                                    iconSize = 24.dp,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                Text(
                                    text = tool.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
