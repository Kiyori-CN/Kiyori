package com.ai.assistance.operit.ui.features.websession.browser.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

@Composable
internal fun WebSessionBrowserMenuDrawer(
    isVisible: Boolean,
    isBookmarked: Boolean,
    canAddBookmark: Boolean,
    onAddBookmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenUserscripts: () -> Unit,
    onOpenFloatingSniffer: () -> Unit,
    onOpenUserAgent: () -> Unit,
    onOpenNetworkLog: () -> Unit,
    onOpenAiDialogue: () -> Unit,
    onOpenToolbox: () -> Unit,
    onOpenIncognito: () -> Unit,
    onOpenReaderMode: () -> Unit,
    onOpenPageSource: () -> Unit,
    onOpenAdMarking: () -> Unit,
    onOpenSiteConfig: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    onExitBrowser: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val menuTitle = stringResource(R.string.web_session_browser_menu_title)

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isVisible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(120)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
                    .clickable(onClick = onCollapse),
            )
        }

        AnimatedVisibility(
            visible = isVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 4 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(140)) { it / 4 },
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = menuTitle
                    },
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(
                                start = WEB_SESSION_BROWSER_MENU_START_PADDING_DP.dp,
                                top = WEB_SESSION_BROWSER_MENU_TOP_PADDING_DP.dp,
                                end = WEB_SESSION_BROWSER_MENU_END_PADDING_DP.dp,
                                bottom = WEB_SESSION_BROWSER_MENU_BOTTOM_PADDING_DP.dp,
                            ),
                    verticalArrangement = Arrangement.spacedBy(WEB_SESSION_BROWSER_MENU_ROW_SPACING_DP.dp),
                ) {
                    MenuRow(
                        MenuAction(
                            title =
                                stringResource(
                                    if (isBookmarked) {
                                        R.string.web_session_remove_bookmark
                                    } else {
                                        R.string.web_session_add_bookmark_tool
                                    },
                                ),
                            iconResId = R.drawable.ic_kiyori_tool_bookmark_add,
                            onClick = onAddBookmark,
                            enabled = canAddBookmark,
                        ),
                        MenuAction(stringResource(R.string.web_session_bookmarks), R.drawable.ic_kiyori_tool_bookmarks, onOpenBookmarks),
                        MenuAction(stringResource(R.string.web_session_history), R.drawable.ic_kiyori_tool_history, onOpenHistory),
                        MenuAction(stringResource(R.string.web_session_downloads), R.drawable.ic_kiyori_tool_download, onOpenDownloads),
                        MenuAction(stringResource(R.string.web_session_userscripts), R.drawable.ic_kiyori_tool_plugin, onOpenUserscripts),
                    )
                    MenuRow(
                        MenuAction(stringResource(R.string.web_session_floating_sniffer), R.drawable.ic_kiyori_tool_sniffer, onOpenFloatingSniffer),
                        MenuAction(stringResource(R.string.web_session_user_agent), R.drawable.ic_kiyori_tool_ua, onOpenUserAgent),
                        MenuAction(stringResource(R.string.web_session_network_log), R.drawable.ic_kiyori_tool_network_log, onOpenNetworkLog),
                        MenuAction(stringResource(R.string.web_session_ai_dialogue), R.drawable.ic_kiyori_tool_ai_dialogue, onOpenAiDialogue),
                        MenuAction(stringResource(R.string.web_session_browser_toolbox), R.drawable.ic_kiyori_browser_bottom_toolbox, onOpenToolbox),
                    )
                    MenuRow(
                        MenuAction(stringResource(R.string.web_session_incognito_mode), R.drawable.ic_kiyori_tool_incognito, onOpenIncognito),
                        MenuAction(stringResource(R.string.web_session_reader_mode), R.drawable.ic_kiyori_tool_reader_mode, onOpenReaderMode),
                        MenuAction(stringResource(R.string.web_session_page_source), R.drawable.ic_kiyori_tool_view_source, onOpenPageSource),
                        MenuAction(stringResource(R.string.web_session_ad_marking), R.drawable.ic_kiyori_tool_ad_block, onOpenAdMarking),
                        MenuAction(stringResource(R.string.web_session_site_config), R.drawable.ic_kiyori_tool_site_config, onOpenSiteConfig),
                    )
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = WEB_SESSION_BROWSER_MENU_BOTTOM_ROW_HORIZONTAL_PADDING_DP.dp,
                                    top = WEB_SESSION_BROWSER_MENU_BOTTOM_ROW_TOP_PADDING_DP.dp,
                                    end = WEB_SESSION_BROWSER_MENU_BOTTOM_ROW_HORIZONTAL_PADDING_DP.dp,
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier.weight(
                                    WEB_SESSION_BROWSER_MENU_BOTTOM_SIDE_SLOT_WEIGHT.toFloat(),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            BottomMenuAction(
                                title = stringResource(R.string.web_session_exit_browser),
                                iconResId = R.drawable.ic_kiyori_tool_power,
                                onClick = onExitBrowser,
                            )
                        }
                        Box(
                            modifier =
                                Modifier.weight(
                                    WEB_SESSION_BROWSER_MENU_BOTTOM_CENTER_SLOT_WEIGHT.toFloat(),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            BottomMenuAction(
                                title = stringResource(R.string.collapse_verb),
                                iconResId = R.drawable.ic_kiyori_tool_collapse,
                                onClick = onCollapse,
                            )
                        }
                        Box(
                            modifier =
                                Modifier.weight(
                                    WEB_SESSION_BROWSER_MENU_BOTTOM_SIDE_SLOT_WEIGHT.toFloat(),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            BottomMenuAction(
                                title = stringResource(R.string.web_session_browser_settings),
                                iconResId = R.drawable.ic_kiyori_tool_settings,
                                onClick = onOpenBrowserSettings,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class MenuAction(
    val title: String,
    val iconResId: Int,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Composable
private fun MenuRow(vararg actions: MenuAction) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        actions.forEach { action ->
            MenuCell(action)
        }
    }
}

@Composable
private fun RowScope.MenuCell(action: MenuAction) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(
                horizontal = WEB_SESSION_BROWSER_MENU_CELL_HORIZONTAL_PADDING_DP.dp,
                vertical = WEB_SESSION_BROWSER_MENU_CELL_VERTICAL_PADDING_DP.dp,
            )
            .clickable(enabled = action.enabled, role = Role.Button, onClick = action.onClick)
            .semantics {
                contentDescription = action.title
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WEB_SESSION_BROWSER_MENU_ICON_LABEL_SPACING_DP.dp),
    ) {
        Box(
            modifier = Modifier.size(WEB_SESSION_BROWSER_MENU_ICON_CONTAINER_SIZE_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(action.iconResId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (action.enabled) 1f else 0.38f),
                modifier = Modifier.size(WEB_SESSION_BROWSER_MENU_ICON_SIZE_DP.dp),
            )
        }
        Text(
            text = action.title,
            style = MaterialTheme.typography.labelSmall,
            fontSize = WEB_SESSION_BROWSER_MENU_LABEL_SIZE_SP.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (action.enabled) 1f else 0.38f),
        )
    }
}

@Composable
private fun BottomMenuAction(
    title: String,
    iconResId: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(
                    width = WEB_SESSION_BROWSER_MENU_BOTTOM_ACTION_WIDTH_DP.dp,
                    height = WEB_SESSION_BROWSER_MENU_BOTTOM_ACTION_HEIGHT_DP.dp,
                )
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = title
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = null,
            modifier = Modifier.size(WEB_SESSION_BROWSER_MENU_BOTTOM_ICON_SIZE_DP.dp),
        )
    }
}
