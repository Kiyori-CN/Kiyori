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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onExitBrowser: () -> Unit,
    onCollapse: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
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
                    .height(324.dp)
                    .navigationBarsPadding()
                    .semantics {
                        contentDescription = menuTitle
                    },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MenuRow(
                        MenuAction(
                            title = stringResource(R.string.web_session_add_bookmark_tool),
                            icon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkAdd,
                            onClick = onAddBookmark,
                            enabled = canAddBookmark,
                        ),
                        MenuAction(stringResource(R.string.web_session_bookmarks), Icons.Filled.Bookmark, onOpenBookmarks),
                        MenuAction(stringResource(R.string.web_session_history), Icons.Filled.History, onOpenHistory),
                        MenuAction(stringResource(R.string.web_session_downloads), Icons.Filled.Download, onOpenDownloads),
                        MenuAction(stringResource(R.string.web_session_userscripts), Icons.Filled.Extension, onOpenUserscripts),
                    )
                    MenuRow(
                        MenuAction(stringResource(R.string.web_session_floating_sniffer), Icons.Filled.Visibility, onOpenFloatingSniffer),
                        MenuAction(stringResource(R.string.web_session_user_agent), Icons.Filled.Fingerprint, onOpenUserAgent),
                        MenuAction(stringResource(R.string.web_session_network_log), Icons.Filled.Terminal, onOpenNetworkLog),
                        MenuAction(stringResource(R.string.web_session_ai_dialogue), Icons.Filled.Chat, onOpenAiDialogue),
                        MenuAction(stringResource(R.string.web_session_browser_toolbox), Icons.Filled.Build, onOpenToolbox),
                    )
                    MenuRow(
                        MenuAction(stringResource(R.string.web_session_incognito_mode), Icons.Filled.VisibilityOff, onOpenIncognito),
                        MenuAction(stringResource(R.string.web_session_reader_mode), Icons.Filled.MenuBook, onOpenReaderMode),
                        MenuAction(stringResource(R.string.web_session_page_source), Icons.Filled.Code, onOpenPageSource),
                        MenuAction(stringResource(R.string.web_session_ad_marking), Icons.Filled.Block, onOpenAdMarking),
                        MenuAction(stringResource(R.string.web_session_site_config), Icons.Filled.Security, onOpenSiteConfig),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BottomMenuAction(
                            title = stringResource(R.string.web_session_exit_browser),
                            icon = Icons.Filled.ExitToApp,
                            onClick = onExitBrowser,
                        )
                        BottomMenuAction(
                            title = stringResource(R.string.collapse_verb),
                            icon = Icons.Filled.KeyboardArrowDown,
                            onClick = onCollapse,
                        )
                        BottomMenuAction(
                            title = stringResource(R.string.web_session_browser_settings),
                            icon = Icons.Filled.Settings,
                            onClick = onOpenBrowserSettings,
                        )
                    }
                }
            }
        }
    }
}

private data class MenuAction(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Composable
private fun MenuRow(vararg actions: MenuAction) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
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
            .clickable(enabled = action.enabled, role = Role.Button, onClick = action.onClick)
            .semantics {
                contentDescription = action.title
                role = Role.Button
            }
            .padding(horizontal = 1.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (action.enabled) 1f else 0.38f),
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = action.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (action.enabled) 1f else 0.38f),
        )
    }
}

@Composable
private fun RowScope.BottomMenuAction(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = title
                role = Role.Button
            }
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(25.dp))
    }
}
