package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSessionHistoryItem
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.ai.assistance.operit.ui.theme.KiyoriSemanticTone
import com.ai.assistance.operit.ui.theme.resolveColors
import java.text.DateFormat
import java.util.Date

@Composable
internal fun WebSessionHistorySheet(
    sessionHistory: List<WebSessionSessionHistoryItem>,
    globalHistory: List<WebSessionHistoryEntry>,
    onSelectSessionHistory: (Int) -> Unit,
    onOpenHistoryUrl: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    WebSessionSheetScaffold(
        title = stringResource(R.string.web_session_history),
        modifier = modifier
    ) {
        WebSessionSectionLabel(
            text = stringResource(R.string.web_session_current_session_history),
            tone = KiyoriSemanticTone.ORANGE,
        )
        if (sessionHistory.isEmpty()) {
            WebSessionEmptyState(
                icon = Icons.Filled.History,
                title = stringResource(R.string.web_session_no_history),
                tone = KiyoriSemanticTone.ORANGE,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(0.42f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(items = sessionHistory, key = { "session-${it.index}" }) { item ->
                    WebSessionItemCard(
                        highlighted = item.isCurrent,
                        highlightTone = KiyoriSemanticTone.ORANGE,
                        onClick = { onSelectSessionHistory(item.index) }
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color =
                                    if (item.isCurrent) {
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.index.plus(1).toString(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                if (item.isCurrent) {
                                    Text(
                                        text = stringResource(R.string.web_session_current_page),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = KiyoriSemanticTone.ORANGE.resolveColors().icon,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = item.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = item.url,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WebSessionSectionLabel(
                text = stringResource(R.string.web_session_recent_history),
                tone = KiyoriSemanticTone.ORANGE,
            )
            if (globalHistory.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.web_session_clear_history),
                    style = MaterialTheme.typography.labelLarge,
                    color = KiyoriSemanticTone.RED.resolveColors().icon,
                    modifier =
                        Modifier
                            .clickable(onClick = onClearHistory)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (globalHistory.isEmpty()) {
            WebSessionEmptyState(
                icon = Icons.Filled.History,
                title = stringResource(R.string.web_session_no_history),
                tone = KiyoriSemanticTone.ORANGE,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(0.58f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(items = globalHistory, key = { "${it.url}-${it.visitedAt}" }) { entry ->
                    WebSessionItemCard(onClick = { onOpenHistoryUrl(entry.url) }) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            KiyoriSemanticIconBadge(
                                imageVector = Icons.Filled.History,
                                tone = KiyoriSemanticTone.ORANGE,
                                contentDescription = null,
                                containerSize = 40.dp,
                                iconSize = 21.dp,
                                shape = androidx.compose.foundation.shape.CircleShape,
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = entry.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = entry.url,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = dateFormat.format(Date(entry.visitedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
