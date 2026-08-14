package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryActionKind
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryEntry
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors

@Composable
internal fun WebSessionHistoryActionDialog(
    entry: WebSessionHistoryEntry,
    actionKind: WebSessionHistoryActionKind,
    sourcePageUrl: String?,
    onDismiss: () -> Unit,
    onOpenEntry: () -> Unit,
    onOpenSourcePage: () -> Unit,
    onAddBookmark: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyTitle: () -> Unit,
    onDeleteEntry: () -> Unit,
    onStartBatchDelete: () -> Unit,
) {
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon =
                when (actionKind) {
                    WebSessionHistoryActionKind.WEB_PAGE -> Icons.Filled.Language
                    WebSessionHistoryActionKind.VIDEO -> Icons.Filled.PlayArrow
                },
            tone = WebSessionBrowserMenuTone.HISTORY,
            title =
                stringResource(
                    when (actionKind) {
                        WebSessionHistoryActionKind.WEB_PAGE ->
                            R.string.web_session_history_web_actions_title
                        WebSessionHistoryActionKind.VIDEO ->
                            R.string.web_session_history_video_actions_title
                    }
                ),
            modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
        ) {
            HistoryActionTarget(entry = entry, sourcePageUrl = sourcePageUrl)
            when (actionKind) {
                WebSessionHistoryActionKind.WEB_PAGE -> {
                    HistoryActionRow(
                        icon = Icons.Filled.OpenInBrowser,
                        title = stringResource(R.string.web_session_history_open_web),
                        tone = KiyoriSemanticTone.BLUE,
                        onClick = onOpenEntry,
                    )
                    HistoryActionRow(
                        icon = Icons.Filled.Bookmark,
                        title = stringResource(R.string.web_session_history_add_bookmark),
                        tone = KiyoriSemanticTone.GREEN,
                        onClick = onAddBookmark,
                    )
                    HistoryActionRow(
                        icon = Icons.Filled.ContentCopy,
                        title = stringResource(R.string.web_session_history_copy_link),
                        tone = KiyoriSemanticTone.PURPLE,
                        onClick = onCopyLink,
                    )
                }
                WebSessionHistoryActionKind.VIDEO -> {
                    HistoryActionRow(
                        icon = Icons.Filled.PlayArrow,
                        title = stringResource(R.string.web_session_history_play_video),
                        tone = KiyoriSemanticTone.BLUE,
                        onClick = onOpenEntry,
                    )
                    if (sourcePageUrl != null) {
                        HistoryActionRow(
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            title = stringResource(R.string.web_session_history_open_source_page),
                            tone = KiyoriSemanticTone.GREEN,
                            onClick = onOpenSourcePage,
                        )
                    }
                    HistoryActionRow(
                        icon = Icons.Filled.ContentCopy,
                        title = stringResource(R.string.web_session_history_copy_video_link),
                        tone = KiyoriSemanticTone.PURPLE,
                        onClick = onCopyLink,
                    )
                }
            }
            HistoryActionRow(
                icon = Icons.Filled.TextFields,
                title = stringResource(R.string.web_session_history_copy_title),
                tone = KiyoriSemanticTone.CYAN,
                onClick = onCopyTitle,
            )
            HistoryActionRow(
                icon = Icons.Filled.Delete,
                title = stringResource(R.string.web_session_history_delete_entry),
                tone = KiyoriSemanticTone.RED,
                onClick = onDeleteEntry,
            )
            HistoryActionRow(
                icon = Icons.Filled.SelectAll,
                title = stringResource(R.string.web_session_history_batch_delete),
                tone = KiyoriSemanticTone.ORANGE,
                onClick = onStartBatchDelete,
                drawDivider = false,
            )
        }
    }
}

@Composable
private fun HistoryActionTarget(
    entry: WebSessionHistoryEntry,
    sourcePageUrl: String?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = entry.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.url,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        sourcePageUrl?.let { sourceUrl ->
            Text(
                text = stringResource(R.string.web_session_history_source_page_value, sourceUrl),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
}

@Composable
private fun HistoryActionRow(
    icon: ImageVector,
    title: String,
    tone: KiyoriSemanticTone,
    onClick: () -> Unit,
    drawDivider: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KiyoriSemanticIconBadge(
                imageVector = icon,
                tone = tone,
                contentDescription = null,
                containerSize = 30.dp,
                iconSize = 16.dp,
                shape = RoundedCornerShape(9.dp),
            )
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (drawDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
        }
    }
}

@Composable
internal fun WebSessionHistoryDeleteConfirmationDialog(
    entryTitle: String?,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val deleteColors = KiyoriSemanticTone.RED.resolveColors()
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Filled.Warning,
            tone = WebSessionBrowserMenuTone.HISTORY,
            title =
                stringResource(
                    if (entryTitle == null) {
                        R.string.web_session_history_batch_delete_confirm_title
                    } else {
                        R.string.web_session_history_delete_entry_confirm_title
                    }
                ),
            modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text =
                        if (entryTitle == null) {
                            stringResource(
                                R.string.web_session_history_batch_delete_confirm_message,
                                selectedCount,
                            )
                        } else {
                            stringResource(
                                R.string.web_session_history_delete_entry_confirm_message,
                                entryTitle,
                            )
                        },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.web_session_history_delete_cancel))
                    }
                    TextButton(onClick = onConfirm) {
                        Text(
                            text = stringResource(R.string.web_session_history_delete_confirm),
                            color = deleteColors.icon,
                        )
                    }
                }
            }
        }
    }
}
