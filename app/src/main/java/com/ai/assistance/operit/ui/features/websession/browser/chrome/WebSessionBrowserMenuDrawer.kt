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
import androidx.compose.foundation.layout.height
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
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuIconBadge
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuTone
import com.ai.assistance.operit.ui.features.websession.browser.resolveColors

@Composable
internal fun WebSessionBrowserMenuDrawer(
    isVisible: Boolean,
    isBookmarked: Boolean,
    canAddBookmark: Boolean,
    onAddBookmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenFloatingSniffer: () -> Unit,
    onOpenUserAgent: () -> Unit,
    onOpenNetworkLog: () -> Unit,
    onOpenDiagnosticLog: () -> Unit,
    onOpenAiDialogue: () -> Unit,
    onOpenToolbox: () -> Unit,
    incognitoEnabled: Boolean,
    onToggleIncognito: () -> Unit,
    onOpenPageSource: () -> Unit,
    onOpenAdMarking: () -> Unit,
    onOpenSiteConfig: () -> Unit,
    onOpenSettingsHome: () -> Unit,
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
                            tone = WebSessionBrowserMenuTone.ADD_BOOKMARK,
                        ),
                        MenuAction(stringResource(R.string.web_session_bookmarks), R.drawable.ic_kiyori_tool_bookmarks, onOpenBookmarks, tone = WebSessionBrowserMenuTone.BOOKMARKS),
                        MenuAction(stringResource(R.string.web_session_history), R.drawable.ic_kiyori_tool_history, onOpenHistory, tone = WebSessionBrowserMenuTone.HISTORY),
                        MenuAction(stringResource(R.string.web_session_downloads), R.drawable.ic_kiyori_tool_download, onOpenDownloads, tone = WebSessionBrowserMenuTone.DOWNLOADS),
                        MenuAction(stringResource(R.string.web_session_plugins), R.drawable.ic_kiyori_tool_plugin, onOpenPlugins, tone = WebSessionBrowserMenuTone.PLUGINS),
                    )
                    MenuRow(
                        MenuAction(stringResource(R.string.web_session_user_agent), R.drawable.ic_kiyori_tool_ua, onOpenUserAgent, tone = WebSessionBrowserMenuTone.USER_AGENT),
                        MenuAction(stringResource(R.string.web_session_floating_sniffer), R.drawable.ic_kiyori_tool_sniffer, onOpenFloatingSniffer, tone = WebSessionBrowserMenuTone.FLOATING_SNIFFER),
                        MenuAction(stringResource(R.string.web_session_network_log), R.drawable.ic_kiyori_tool_network_log, onOpenNetworkLog, tone = WebSessionBrowserMenuTone.NETWORK_LOG),
                        MenuAction(stringResource(R.string.web_session_diagnostics), R.drawable.ic_kiyori_tool_diagnostics, onOpenDiagnosticLog, tone = WebSessionBrowserMenuTone.DIAGNOSTICS),
                        MenuAction(stringResource(R.string.web_session_browser_toolbox), R.drawable.ic_kiyori_browser_bottom_toolbox, onOpenToolbox, tone = WebSessionBrowserMenuTone.TOOLBOX),
                    )
                    MenuRow(
                        MenuAction(
                            stringResource(R.string.web_session_incognito_mode),
                            R.drawable.ic_kiyori_tool_incognito,
                            onToggleIncognito,
                            enabled = incognitoEnabled,
                            tone = WebSessionBrowserMenuTone.INCOGNITO,
                        ),
                        MenuAction(stringResource(R.string.web_session_page_source), R.drawable.ic_kiyori_tool_view_source, onOpenPageSource, tone = WebSessionBrowserMenuTone.PAGE_SOURCE),
                        MenuAction(stringResource(R.string.web_session_ad_marking), R.drawable.ic_kiyori_tool_ad_block, onOpenAdMarking, tone = WebSessionBrowserMenuTone.AD_MARKING),
                        MenuAction(stringResource(R.string.web_session_site_config), R.drawable.ic_kiyori_tool_site_config, onOpenSiteConfig, tone = WebSessionBrowserMenuTone.SITE_CONFIG),
                        MenuAction(stringResource(R.string.web_session_ai_dialogue), R.drawable.ic_kiyori_tool_ai_dialogue, onOpenAiDialogue, tone = WebSessionBrowserMenuTone.AI_DIALOGUE),
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
                                tone = WebSessionBrowserMenuTone.EXIT_BROWSER,
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
                                tone = WebSessionBrowserMenuTone.COLLAPSE,
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
                                title = stringResource(R.string.settings),
                                iconResId = R.drawable.ic_kiyori_tool_settings,
                                onClick = onOpenSettingsHome,
                                tone = WebSessionBrowserMenuTone.SETTINGS,
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
    val tone: WebSessionBrowserMenuTone,
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
            .semantics(mergeDescendants = true) {
                contentDescription = action.title
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WEB_SESSION_BROWSER_MENU_ICON_LABEL_SPACING_DP.dp),
    ) {
        WebSessionBrowserMenuIconBadge(
            painter = painterResource(action.iconResId),
            tone = action.tone,
            contentDescription = null,
            containerSize = WEB_SESSION_BROWSER_MENU_ICON_CONTAINER_SIZE_DP.dp,
            iconSize = WEB_SESSION_BROWSER_MENU_ICON_SIZE_DP.dp,
            shape = RoundedCornerShape(10.dp),
            enabled = action.enabled,
        )
        Text(
            text = action.title,
            style = MaterialTheme.typography.labelSmall,
            fontSize = WEB_SESSION_BROWSER_MENU_LABEL_SIZE_SP.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (action.enabled) 1f else 0.38f),
            modifier = Modifier.height(WEB_SESSION_BROWSER_MENU_LABEL_HEIGHT_DP.dp),
        )
    }
}

@Composable
private fun BottomMenuAction(
    title: String,
    iconResId: Int,
    onClick: () -> Unit,
    tone: WebSessionBrowserMenuTone,
) {
    val colors = tone.resolveColors()
    Surface(
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
        shape = CircleShape,
        color = colors.container,
        contentColor = colors.icon,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
        contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                tint = colors.icon,
                modifier = Modifier.size(WEB_SESSION_BROWSER_MENU_BOTTOM_ICON_SIZE_DP.dp),
            )
        }
    }
}
