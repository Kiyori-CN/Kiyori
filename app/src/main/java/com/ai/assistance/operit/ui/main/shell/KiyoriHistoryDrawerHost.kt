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
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
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
    val scope = rememberCoroutineScope()
    val store = remember(context) { WebSessionHistoryStore.getInstance(context) }
    val browserTools =
        remember(context) { StandardBrowserSessionTools.getSharedInstance(context.applicationContext) }
    val entries by store.historyFlow.collectAsState(initial = emptyList<WebSessionHistoryEntry>())

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
                            -> browserTools.playHistoryMedia(entry)
                        }
                    if (accepted) {
                        onDismissRequest()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.web_session_history_replay_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    accepted
                },
                onDeleteHistory = { category, cutoffTimeMillis ->
                    scope.launch {
                        store.deleteHistory(category, cutoffTimeMillis)
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
