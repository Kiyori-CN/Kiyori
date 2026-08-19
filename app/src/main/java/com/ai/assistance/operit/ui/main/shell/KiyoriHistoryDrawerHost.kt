package com.ai.assistance.operit.ui.main.shell

import android.widget.Toast
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserHistoryMediaLaunchMode
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryCategory
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.playHistoryMedia
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionHistorySheet
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WebSessionBrowserBottomDrawer
import com.ai.assistance.operit.ui.features.websession.browser.chrome.resolveWebSessionBrowserChromeLayout
import kotlinx.coroutines.launch

@Composable
internal fun KiyoriHistoryDrawerHost(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    onOpenWebHistory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var keepMountedUntilHidden by remember { mutableStateOf(isVisible) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            keepMountedUntilHidden = true
        }
    }
    if (!shouldComposeKiyoriHistoryDrawer(isVisible, keepMountedUntilHidden)) {
        return
    }

    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { WebSessionHistoryStore.getInstance(context) }
    val browserTools =
        remember(context) { StandardBrowserSessionTools.getSharedInstance(context.applicationContext) }
    val entries by store.historyFlow.collectAsState(initial = emptyList<WebSessionHistoryEntry>())
    val bookmarkFolders by store.bookmarkFoldersFlow.collectAsState(initial = emptyList())

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val drawerLayout =
            remember(maxWidth, maxHeight) {
                resolveWebSessionBrowserChromeLayout(
                    widthDp = maxWidth.value,
                    heightDp = maxHeight.value,
                )
            }
        WebSessionBrowserBottomDrawer(
            isVisible = isVisible,
            layout = drawerLayout,
            onDismissRequest = onDismissRequest,
            onHidden = { keepMountedUntilHidden = false },
            modifier = Modifier.fillMaxSize(),
        ) {
            WebSessionHistorySheet(
                entries = entries,
                bookmarkFolders = bookmarkFolders,
                // 关闭动画会继续挂载 Sheet；此时必须撤销内部 BackHandler，
                // 否则不可见的批量模式仍可能抢走下一次系统 Back。
                systemBackEnabled = isVisible,
                onOpenEntry = { entry ->
                    val accepted =
                        when (entry.category) {
                            WebSessionHistoryCategory.WEB,
                            WebSessionHistoryCategory.NOVEL,
                            WebSessionHistoryCategory.OTHER,
                            -> {
                                onOpenWebHistory(entry.url)
                                true
                            }
                            WebSessionHistoryCategory.VIDEO,
                            WebSessionHistoryCategory.MUSIC,
                            ->
                                browserTools.playHistoryMedia(
                                    entry = entry,
                                    launchMode =
                                        BrowserHistoryMediaLaunchMode.DIRECT_FULLSCREEN_ACTIVITY,
                                )
                        }
                    if (
                        shouldDismissKiyoriHistoryDrawerAfterEntryOpen(
                            category = entry.category,
                            accepted = accepted,
                        )
                    ) {
                        onDismissRequest()
                    } else if (!accepted) {
                        Toast.makeText(
                            context,
                            resources.getString(R.string.web_session_history_replay_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    accepted
                },
                onOpenWebUrl = onOpenWebHistory,
                onBookmarkMutation = { mutation ->
                    scope.launch {
                        store.applyBookmarkMutation(mutation)
                    }
                },
                onDeleteHistory = { category, cutoffTimeMillis ->
                    scope.launch {
                        store.deleteHistory(category, cutoffTimeMillis)
                    }
                },
                onDeleteHistoryEntries = { entryKeys ->
                    scope.launch {
                        store.deleteHistoryEntries(entryKeys)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun shouldComposeKiyoriHistoryDrawer(
    isVisible: Boolean,
    keepMountedUntilHidden: Boolean,
): Boolean = isVisible || keepMountedUntilHidden

internal fun shouldDismissKiyoriHistoryDrawerAfterEntryOpen(
    category: WebSessionHistoryCategory,
    accepted: Boolean,
): Boolean {
    if (!accepted) return false
    // 网页条目的 App Shell 路由已经在一次状态写入中关闭抽屉并进入 Browser Home。
    // 这里再次关闭会读取重组前的旧 Shell 状态，从而把刚完成的浏览器导航覆盖回负一屏。
    return category == WebSessionHistoryCategory.VIDEO ||
        category == WebSessionHistoryCategory.MUSIC
}
