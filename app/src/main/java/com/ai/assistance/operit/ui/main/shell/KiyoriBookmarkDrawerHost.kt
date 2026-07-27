package com.ai.assistance.operit.ui.main.shell

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
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmark
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmarkFolder
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryStore
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBookmarkSheet
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WebSessionBrowserBottomDrawer
import com.ai.assistance.operit.ui.features.websession.browser.chrome.resolveWebSessionBrowserChromeLayout
import kotlinx.coroutines.launch

@Composable
internal fun KiyoriBookmarkDrawerHost(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    onOpenBookmark: (String) -> Unit,
    onOpenBookmarkInTab: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var keepMountedUntilHidden by remember { mutableStateOf(isVisible) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            keepMountedUntilHidden = true
        }
    }
    if (!shouldComposeKiyoriBookmarkDrawer(isVisible, keepMountedUntilHidden)) {
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { WebSessionHistoryStore.getInstance(context) }
    val bookmarks by store.bookmarksFlow.collectAsState(initial = emptyList<WebSessionBookmark>())
    val folders by store.bookmarkFoldersFlow.collectAsState(initial = emptyList<WebSessionBookmarkFolder>())

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
            WebSessionBookmarkSheet(
                folders = folders,
                bookmarks = bookmarks,
                onMutation = { mutation ->
                    scope.launch { store.applyBookmarkMutation(mutation) }
                },
                onOpenBookmark = onOpenBookmark,
                onOpenBookmarkInTab = onOpenBookmarkInTab,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun shouldComposeKiyoriBookmarkDrawer(
    isVisible: Boolean,
    keepMountedUntilHidden: Boolean,
): Boolean = isVisible || keepMountedUntilHidden
