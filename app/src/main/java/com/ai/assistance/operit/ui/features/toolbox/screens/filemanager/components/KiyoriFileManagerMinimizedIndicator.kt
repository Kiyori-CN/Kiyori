package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionMinimizedIndicator
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionMinimizedCloseAction
import com.kiyori.design.theme.KiyoriBrowserTheme
import kotlin.math.roundToInt
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 与浏览器共用球体及手势；各自保存位置和会话，互不关闭或替换。 */
@Composable
internal fun KiyoriFileManagerMinimizedIndicator(
    onRestore: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var xFraction by rememberSaveable { mutableFloatStateOf(0.92f) }
    var yFraction by rememberSaveable { mutableFloatStateOf(0.62f) }
    var closeState by remember { mutableStateOf(BrowserMinimizedIndicatorCloseState()) }
    val scope = rememberCoroutineScope()
    var hideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun closeEvent(event: BrowserMinimizedIndicatorCloseEvent) {
        val transition = BrowserMinimizedIndicatorClosePolicy.reduce(closeState, event)
        closeState = transition.state
        if (transition.cancelPendingHide) hideJob?.cancel()
        transition.scheduleHideAfterMillis?.let { timeout -> hideJob = scope.launch {
            delay(timeout); closeState = BrowserMinimizedIndicatorClosePolicy.reduce(closeState, BrowserMinimizedIndicatorCloseEvent.HIDE_TIMEOUT).state
        } }
    }
    KiyoriBrowserTheme {
        BoxWithConstraints(modifier.statusBarsPadding().navigationBarsPadding()) {
            val density = LocalDensity.current
            val maxX = with(density) { (maxWidth - 40.dp).toPx().coerceAtLeast(0f) }
            val maxY = with(density) { (maxHeight - 40.dp).toPx().coerceAtLeast(0f) }
            Box(Modifier.offset { IntOffset((xFraction * maxX).roundToInt(), (yFraction * maxY).roundToInt()) }.size(40.dp)) {
                WebSessionMinimizedIndicator(
                    contentDescription = "恢复文件管理器", activeDownloadCount = 0,
                    hasFailedDownloads = false, downloadPrompt = null,
                    onOpenBrowser = { closeEvent(BrowserMinimizedIndicatorCloseEvent.RESET); onRestore() },
                    onDragBy = { dx, dy ->
                        if (maxX > 0) xFraction = (xFraction + dx / maxX).coerceIn(0f, 1f)
                        if (maxY > 0) yFraction = (yFraction + dy / maxY).coerceIn(0f, 1f)
                    },
                    onLongPress = { closeEvent(BrowserMinimizedIndicatorCloseEvent.LONG_PRESS_RECOGNIZED) },
                    onLongPressGestureFinished = { closeEvent(BrowserMinimizedIndicatorCloseEvent.GESTURE_FINISHED) },
                    onConfirmBrowserDownload = {}, onCancelBrowserDownload = {}, idleIcon = Icons.Outlined.Folder,
                    accentColor = KiyoriSemanticTone.PURPLE.resolveColors().icon,
                )
                if (closeState.isCloseActionVisible && !closeState.isLongPressGestureActive) Box(Modifier.offset(x = (-18).dp, y = (-18).dp).size(BROWSER_MINIMIZED_INDICATOR_CLOSE_ACTION_SIZE_DP.dp)) {
                    WebSessionMinimizedCloseAction("关闭文件管理器", onClose)
                }
            }
        }
    }
}
