package com.ai.assistance.operit.ui.features.websession.browser.chrome

import com.kiyori.design.theme.kiyoriSurfaceColors

import com.kiyori.design.theme.KiyoriSurfaceTokens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuIconBadge
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuTone
import com.kiyori.design.theme.KiyoriBrowserTheme
import com.kiyori.design.theme.KiyoriUiShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class KiyoriToolboxAction(
    val label: String,
    val icon: ImageVector,
    val tone: WebSessionBrowserMenuTone,
    val enabled: Boolean = true,
    val unavailableReason: String? = null,
    val onClick: () -> Unit,
)

internal const val KIYORI_TOOLBOX_ROWS = 3
internal const val KIYORI_TOOLBOX_COLUMNS = 5
internal fun toolboxSlotRows(actionCount: Int): List<List<Int?>> {
    require(actionCount in 0..KIYORI_TOOLBOX_ROWS * KIYORI_TOOLBOX_COLUMNS)
    return List(KIYORI_TOOLBOX_ROWS) { row -> List(KIYORI_TOOLBOX_COLUMNS) { column ->
        (row * KIYORI_TOOLBOX_COLUMNS + column).takeIf { it < actionCount }
    } }
}

/** 二级菜单没有返回栈和第四行。空槽位保留三行高度，不提供可展开至全屏的手势。 */
@Composable
internal fun KiyoriToolboxDrawer(actions: List<KiyoriToolboxAction>, onDismiss: () -> Unit) {
    val rows = toolboxSlotRows(actions.size)
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismiss)
    fun close(action: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        visible = false
        scope.launch { delay(140); latestDismiss(); action?.invoke() }
    }
    LaunchedEffect(Unit) { visible = true }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false,
        // 全屏遮罩覆盖状态栏；菜单内容仍由 navigationBarsPadding 处理底部安全区。
        decorFitsSystemWindows = false,
    )) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        BackHandler { close() }
        KiyoriBrowserTheme {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                AnimatedVisibility(visible, enter = fadeIn(tween(140)), exit = fadeOut(tween(120))) {
                    Box(Modifier.fillMaxSize().background(kiyoriSurfaceColors().modalScrim).clickable { close() })
                }
                AnimatedVisibility(visible, Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 4 },
                    exit = fadeOut(tween(120)) + slideOutVertically(tween(140)) { it / 4 }) {
                    Surface(Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.85f), shape = KiyoriUiShapes.sheet,
                        color = kiyoriSurfaceColors().sheet, shadowElevation = KiyoriSurfaceTokens.sheetElevation) {
                        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(
                            start = WEB_SESSION_BROWSER_MENU_START_PADDING_DP.dp, end = WEB_SESSION_BROWSER_MENU_END_PADDING_DP.dp,
                            top = WEB_SESSION_BROWSER_MENU_TOP_PADDING_DP.dp, bottom = WEB_SESSION_BROWSER_MENU_BOTTOM_PADDING_DP.dp,
                        ), verticalArrangement = Arrangement.spacedBy(WEB_SESSION_BROWSER_MENU_ROW_SPACING_DP.dp)) {
                            rows.forEach { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    row.forEach { index ->
                                        val action = index?.let(actions::get)
                                        // 空位和实按钮使用同样的几何，不创建隐藏点击目标或无障碍节点。
                                        val cellHeight = (WEB_SESSION_BROWSER_MENU_CELL_VERTICAL_PADDING_DP * 2 +
                                            WEB_SESSION_BROWSER_MENU_ICON_CONTAINER_SIZE_DP + WEB_SESSION_BROWSER_MENU_ICON_LABEL_SPACING_DP +
                                            WEB_SESSION_BROWSER_MENU_LABEL_HEIGHT_DP).dp
                                        if (action == null) Spacer(Modifier.weight(1f).height(cellHeight))
                                        else Surface(onClick = { close(action.onClick) }, enabled = action.enabled && !closing,
                                            color = kiyoriSurfaceColors().sheet, shape = KiyoriUiShapes.control,
                                            modifier = Modifier.weight(1f).height(cellHeight).alpha(if (action.enabled) 1f else 0.38f)
                                                .semantics { contentDescription = action.label + (action.unavailableReason?.let { "，$it" } ?: "") }) {
                                            Column(Modifier.padding(horizontal = WEB_SESSION_BROWSER_MENU_CELL_HORIZONTAL_PADDING_DP.dp,
                                                vertical = WEB_SESSION_BROWSER_MENU_CELL_VERTICAL_PADDING_DP.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                WebSessionBrowserMenuIconBadge(action.icon, action.tone, null,
                                                    containerSize = WEB_SESSION_BROWSER_MENU_ICON_CONTAINER_SIZE_DP.dp,
                                                    iconSize = WEB_SESSION_BROWSER_MENU_ICON_SIZE_DP.dp)
                                                Spacer(Modifier.height(WEB_SESSION_BROWSER_MENU_ICON_LABEL_SPACING_DP.dp))
                                                Text(action.label, Modifier.fillMaxWidth().height(WEB_SESSION_BROWSER_MENU_LABEL_HEIGHT_DP.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = WEB_SESSION_BROWSER_MENU_LABEL_SIZE_SP.sp, lineHeight = 12.sp, maxLines = 2,
                                                    textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
