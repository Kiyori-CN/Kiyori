package com.ai.assistance.operit.ui.features.chat.components.part

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchEvidence
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchEvidenceAction
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchEvidenceSource
import com.ai.assistance.operit.util.AppLogger

@Composable
internal fun OpenAIWebSearchToolResultDisplay(
    evidence: OpenAIHostedWebSearchEvidence,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var detailsExpanded by remember(evidence.requestId) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.openai_web_search_evidence_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            if (evidence.mode == "live") {
                                stringResource(R.string.openai_web_search_mode_live)
                            } else {
                                stringResource(R.string.openai_web_search_mode_indexed)
                            }
                        )
                    },
                )
            }

            Text(
                text = evidence.query,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SelectionContainer {
                Text(
                    text =
                        evidence.answerWithSourceMarkers.ifBlank {
                            evidence.answer
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text =
                    stringResource(
                        R.string.openai_web_search_sources_count,
                        evidence.sources.size,
                    ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            evidence.sources.forEach { source ->
                val sourceUrl = source.url
                OpenAIWebSearchSourceRow(
                    source = source,
                    onOpen =
                        if (sourceUrl == null) {
                            null
                        } else {
                            {
                                runCatching { uriHandler.openUri(sourceUrl) }
                                    .onFailure { error ->
                                        AppLogger.e(
                                            "OpenAIWebSearchEvidence",
                                            "Failed to open source URL",
                                            error,
                                        )
                                    }
                            }
                        },
                )
            }

            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { detailsExpanded = !detailsExpanded },
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.openai_web_search_details),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector =
                            if (detailsExpanded) Icons.Default.ExpandLess
                            else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            }

            if (detailsExpanded) {
                OpenAIWebSearchEvidenceDetails(evidence)
            }
        }
    }
}

@Composable
private fun OpenAIWebSearchSourceRow(
    source: OpenAIHostedWebSearchEvidenceSource,
    onOpen: (() -> Unit)?,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onOpen == null) {
                        Modifier
                    } else {
                        Modifier.clickable(onClick = onOpen)
                    }
                ),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector =
                    if (source.url == null) {
                        Icons.Default.Search
                    } else {
                        Icons.Default.Link
                    },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${source.sourceId} · ${source.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                source.url?.let { sourceUrl ->
                    Text(
                        text = sourceUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (source.url == null) {
                    Text(
                        text = source.type,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenAIWebSearchEvidenceDetails(evidence: OpenAIHostedWebSearchEvidence) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OpenAIWebSearchDetailLine(
            label = stringResource(R.string.openai_web_search_model),
            value = evidence.model,
        )
        OpenAIWebSearchDetailLine(
            label = stringResource(R.string.openai_web_search_evidence_mode),
            value = evidence.evidenceMode.wireValue,
        )
        OpenAIWebSearchDetailLine(
            label = stringResource(R.string.openai_web_search_usage),
            value =
                stringResource(
                    R.string.openai_web_search_usage_value,
                    evidence.usage.inputTokens,
                    evidence.usage.cachedInputTokens,
                    evidence.usage.outputTokens,
                    evidence.usage.webSearchCalls,
                ),
        )
        Text(
            text =
                stringResource(
                    R.string.openai_web_search_actions_count,
                    evidence.searchActions.size,
                ),
            style = MaterialTheme.typography.labelLarge,
        )
        evidence.searchActions.forEachIndexed { index, action ->
            Text(
                text = "${index + 1}. ${action.displayText()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (evidence.warnings.isNotEmpty()) {
            Text(
                text =
                    stringResource(
                        R.string.openai_web_search_warnings_value,
                        evidence.warnings.joinToString(", "),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text =
                stringResource(
                    R.string.openai_web_search_citations_count,
                    evidence.citations.size,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OpenAIWebSearchDetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun OpenAIHostedWebSearchEvidenceAction.displayText(): String =
    listOfNotNull(
        type,
        query?.let { "query=$it" },
        url?.let { "url=$it" },
        pattern?.let { "pattern=$it" },
    ).joinToString(" · ")
