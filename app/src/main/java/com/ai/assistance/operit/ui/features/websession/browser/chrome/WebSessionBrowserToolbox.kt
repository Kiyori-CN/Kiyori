package com.ai.assistance.operit.ui.features.websession.browser.chrome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageMenuCommand

@Composable
internal fun WebSessionBrowserToolbox(
    isDesktopMode: Boolean,
    activeDownloadCount: Int,
    failedDownloadCount: Int,
    userscriptMenuCommands: List<UserscriptPageMenuCommand>,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenUserscripts: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onInvokeUserscriptMenu: (String) -> Unit,
    onCloseCurrentTab: () -> Unit,
    onCollapse: () -> Unit,
    onCloseAllTabs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, top = 2.dp, end = 14.dp, bottom = 10.dp),
    ) {
        Text(
            text = stringResource(R.string.toolbox),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text =
                stringResource(
                    R.string.web_session_current_mode,
                    stringResource(
                        if (isDesktopMode) {
                            R.string.web_session_desktop_mode
                        } else {
                            R.string.web_session_mobile_mode
                        },
                    ),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            BrowserToolboxGridItem(
                title = stringResource(R.string.web_session_history),
                icon = Icons.Filled.History,
                onClick = onOpenHistory,
            )
            BrowserToolboxGridItem(
                title = stringResource(R.string.web_session_bookmarks),
                icon = Icons.Filled.Bookmark,
                onClick = onOpenBookmarks,
            )
            BrowserToolboxGridItem(
                title = stringResource(R.string.web_session_downloads),
                subtitle =
                    stringResource(
                        R.string.web_session_downloads_summary,
                        activeDownloadCount,
                        failedDownloadCount,
                    ),
                icon = Icons.Filled.Download,
                onClick = onOpenDownloads,
            )
            BrowserToolboxGridItem(
                title = stringResource(R.string.web_session_userscripts),
                icon = Icons.Filled.Extension,
                onClick = onOpenUserscripts,
            )
            BrowserToolboxGridItem(
                title =
                    stringResource(
                        if (isDesktopMode) {
                            R.string.web_session_mobile_mode
                        } else {
                            R.string.web_session_desktop_mode
                        },
                    ),
                icon =
                    if (isDesktopMode) {
                        Icons.Filled.Smartphone
                    } else {
                        Icons.Filled.DesktopWindows
                    },
                onClick = onToggleDesktopMode,
            )
        }

        if (userscriptMenuCommands.isNotEmpty()) {
            Text(
                text = stringResource(R.string.web_session_userscript_page_menu),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 2.dp, top = 12.dp, bottom = 6.dp),
            )
            userscriptMenuCommands.forEach { command ->
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable(role = Role.Button) {
                                onInvokeUserscriptMenu(command.commandId)
                            },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = command.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(R.string.web_session_userscript_page_menu_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            BrowserToolboxBottomAction(
                title = stringResource(R.string.web_session_close_current_tab),
                icon = Icons.Filled.Close,
                onClick = onCloseCurrentTab,
            )
            BrowserToolboxBottomAction(
                title = stringResource(R.string.collapse_verb),
                icon = Icons.Filled.KeyboardArrowDown,
                onClick = onCollapse,
            )
            BrowserToolboxBottomAction(
                title = stringResource(R.string.web_session_close_all_tabs),
                icon = Icons.Filled.DeleteSweep,
                color = MaterialTheme.colorScheme.error,
                onClick = onCloseAllTabs,
            )
        }
    }
}

@Composable
private fun RowScope.BrowserToolboxGridItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Column(
        modifier =
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RowScope.BrowserToolboxBottomAction(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier =
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
