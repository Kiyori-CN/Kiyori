package com.ai.assistance.operit.ui.features.chat.components.part

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchContract
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchEvidence
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchEvidenceAction
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchEvidenceActionType
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchEvidenceMode
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchEvidenceParseResult
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchEvidenceSource
import com.ai.assistance.operit.util.AppLogger
import java.net.URI

@Composable
internal fun OpenAIWebSearchToolResultDisplay(
    evidence: OpenAIHostedWebSearchEvidence,
    renderInstanceKey: Any?,
    modifier: Modifier = Modifier,
) {
    val presentationKey =
        OpenAIWebSearchResultPresentationKey(
            renderInstanceKey = renderInstanceKey,
            requestId = evidence.requestId,
            schemaRevision = OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION,
        )
    var state by remember(presentationKey) {
        mutableStateOf(
            OpenAIWebSearchResultPresentationPolicy.initial(
                sourceCount = evidence.allSources.size
            )
        )
    }

    OpenAIWebSearchCard(modifier = modifier) {
        OpenAIWebSearchSummaryHeader(
            title = stringResource(R.string.openai_web_search_evidence_title),
            expanded = state.summaryExpanded,
            icon = Icons.Default.Search,
            trailing = {
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
            },
            onClick = {
                state =
                    OpenAIWebSearchResultPresentationPolicy.toggleSummary(state)
            },
        )
        Text(
            text = evidence.query,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                stringResource(
                    R.string.openai_web_search_summary_counts,
                    evidence.sourceSummary.citedSourceCount,
                    evidence.sourceSummary.allSourceCount,
                    evidence.sourceSummary.uncitedSourceCount,
                    evidence.searchActions.size,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.summaryExpanded) {
            if (evidence.evidenceMode == OpenAIHostedWebSearchEvidenceMode.NONE) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.openai_web_search_no_web_evidence),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            SelectionContainer {
                Text(
                    text = evidence.answerWithSourceMarkers,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            OpenAIWebSearchSection(
                title =
                    stringResource(
                        R.string.openai_web_search_sources_count,
                        evidence.allSources.size,
                    ),
                expanded = state.sourcesExpanded,
                onClick = {
                    state =
                        OpenAIWebSearchResultPresentationPolicy.toggleSources(
                            state = state,
                            sourceCount = evidence.allSources.size,
                        )
                },
            ) {
                OpenAIWebSearchSources(
                    sources =
                        evidence.allSources.take(state.visibleSourceCount),
                )
                if (state.visibleSourceCount < evidence.allSources.size) {
                    val hiddenSourceCount = evidence.allSources.size - state.visibleSourceCount
                    TextButton(
                        onClick = {
                            state =
                                OpenAIWebSearchResultPresentationPolicy
                                    .showMoreSources(
                                        state = state,
                                        sourceCount = evidence.allSources.size,
                                    )
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.openai_web_search_show_more_sources,
                                hiddenSourceCount,
                                hiddenSourceCount,
                            )
                        )
                    }
                }
            }

            OpenAIWebSearchSection(
                title =
                    stringResource(
                        R.string.openai_web_search_actions_count,
                        evidence.searchActions.size,
                    ),
                expanded = state.searchTraceExpanded,
                onClick = {
                    state =
                        OpenAIWebSearchResultPresentationPolicy
                            .toggleSearchTrace(state)
                },
            ) {
                evidence.searchActions.forEachIndexed { index, action ->
                    OpenAIWebSearchActionRow(
                        index = index,
                        action = action,
                    )
                }
            }

            OpenAIWebSearchSection(
                title = stringResource(R.string.openai_web_search_diagnostics),
                expanded = state.diagnosticsExpanded,
                onClick = {
                    state =
                        OpenAIWebSearchResultPresentationPolicy
                            .toggleDiagnostics(state)
                },
            ) {
                OpenAIWebSearchDiagnostics(evidence)
            }
        }
    }
}

@Composable
internal fun OpenAIWebSearchFailureDisplay(
    failure: OpenAIWebSearchFailurePresentation,
    renderInstanceKey: Any?,
    modifier: Modifier = Modifier,
) {
    var summaryExpanded by remember(
        renderInstanceKey,
        failure.code,
        failure.message,
    ) {
        mutableStateOf(false)
    }
    var diagnosticsExpanded by remember(
        renderInstanceKey,
        failure.code,
        failure.message,
    ) {
        mutableStateOf(false)
    }

    OpenAIWebSearchCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
    ) {
        OpenAIWebSearchSummaryHeader(
            title = stringResource(R.string.openai_web_search_failed),
            expanded = summaryExpanded,
            icon = Icons.Default.ErrorOutline,
            iconTint = MaterialTheme.colorScheme.error,
            onClick = { summaryExpanded = !summaryExpanded },
        )
        failure.code?.let { code ->
            Text(
                text = code,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (summaryExpanded) {
            SelectionContainer {
                Text(
                    text = failure.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            failure.code?.let { code ->
                OpenAIWebSearchSection(
                    title = stringResource(R.string.openai_web_search_diagnostics),
                    expanded = diagnosticsExpanded,
                    onClick = { diagnosticsExpanded = !diagnosticsExpanded },
                ) {
                    OpenAIWebSearchDetailLine(
                        label = stringResource(R.string.openai_web_search_error_code),
                        value = code,
                    )
                }
            }
        }
    }
}

@Composable
internal fun OpenAIWebSearchEvidenceInvalidDisplay(
    invalid: OpenAIHostedWebSearchEvidenceParseResult.Invalid,
    renderInstanceKey: Any?,
    modifier: Modifier = Modifier,
) {
    var summaryExpanded by remember(
        renderInstanceKey,
        invalid.code,
        invalid.schemaRevision,
        invalid.hasRequestId,
        invalid.fieldName,
    ) {
        mutableStateOf(false)
    }
    var diagnosticsExpanded by remember(
        renderInstanceKey,
        invalid.code,
        invalid.schemaRevision,
        invalid.hasRequestId,
        invalid.fieldName,
    ) {
        mutableStateOf(false)
    }

    OpenAIWebSearchCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
    ) {
        OpenAIWebSearchSummaryHeader(
            title = stringResource(R.string.openai_web_search_parse_failed),
            expanded = summaryExpanded,
            icon = Icons.Default.ErrorOutline,
            iconTint = MaterialTheme.colorScheme.error,
            onClick = { summaryExpanded = !summaryExpanded },
        )
        Text(
            text = invalid.sanitizedSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (summaryExpanded) {
            OpenAIWebSearchSection(
                title = stringResource(R.string.openai_web_search_diagnostics),
                expanded = diagnosticsExpanded,
                onClick = { diagnosticsExpanded = !diagnosticsExpanded },
            ) {
                OpenAIWebSearchDetailLine(
                    label = stringResource(R.string.openai_web_search_error_code),
                    value = invalid.code.name,
                )
                OpenAIWebSearchDetailLine(
                    label = stringResource(R.string.openai_web_search_schema_revision),
                    value =
                        invalid.schemaRevision?.toString()
                            ?: stringResource(R.string.openai_web_search_not_present),
                )
                OpenAIWebSearchDetailLine(
                    label = stringResource(R.string.openai_web_search_request_id_present),
                    value =
                        if (invalid.hasRequestId) {
                            stringResource(R.string.yes)
                        } else {
                            stringResource(R.string.no)
                        },
                )
                invalid.fieldName?.let { fieldName ->
                    OpenAIWebSearchDetailLine(
                        label = stringResource(R.string.openai_web_search_invalid_field),
                        value = fieldName,
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenAIWebSearchCard(
    modifier: Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun OpenAIWebSearchSummaryHeader(
    title: String,
    expanded: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
        Icon(
            imageVector =
                if (expanded) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                },
            contentDescription =
                stringResource(
                    if (expanded) R.string.collapse else R.string.expand
                ),
        )
    }
}

@Composable
private fun OpenAIWebSearchSection(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector =
                        if (expanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                    contentDescription =
                        stringResource(
                            if (expanded) R.string.collapse else R.string.expand
                        ),
                )
            }
            if (expanded) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun OpenAIWebSearchSources(
    sources: List<OpenAIHostedWebSearchEvidenceSource>,
) {
    val uriHandler = LocalUriHandler.current
    sources.forEach { source ->
        val sourceUrl = source.url
        OpenAIWebSearchSourceRow(
            source = source,
            onOpen =
                sourceUrl?.let {
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
                    onOpen?.let { action ->
                        Modifier.clickable(onClick = action)
                    } ?: Modifier
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${source.sourceId} · ${source.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                source.url?.let { sourceUrl ->
                    Text(
                        text = requireNotNull(URI(sourceUrl).host),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    SelectionContainer {
                        Text(
                            text = sourceUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (source.url == null) {
                    Text(
                        text =
                            stringResource(
                                R.string.openai_web_search_structured_feed,
                                source.type,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenAIWebSearchActionRow(
    index: Int,
    action: OpenAIHostedWebSearchEvidenceAction,
) {
    val icon =
        when (action.type) {
            OpenAIHostedWebSearchEvidenceActionType.SEARCH -> Icons.Default.Search
            OpenAIHostedWebSearchEvidenceActionType.OPEN_PAGE -> Icons.Default.Language
            OpenAIHostedWebSearchEvidenceActionType.FIND_IN_PAGE ->
                Icons.Default.FindInPage
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.openai_web_search_action_title,
                        index + 1,
                        action.type.wireValue,
                    ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            action.query?.let { query ->
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            action.url?.let { url ->
                SelectionContainer {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            action.pattern?.let { pattern ->
                Text(
                    text = pattern,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OpenAIWebSearchDiagnostics(
    evidence: OpenAIHostedWebSearchEvidence,
) {
    OpenAIWebSearchDetailLine(
        label = stringResource(R.string.openai_web_search_model),
        value = evidence.model,
    )
    OpenAIWebSearchDetailLine(
        label = stringResource(R.string.openai_web_search_evidence_mode),
        value = evidence.evidenceMode.wireValue,
    )
    OpenAIWebSearchDetailLine(
        label = stringResource(R.string.openai_web_search_source_summary),
        value =
            stringResource(
                R.string.openai_web_search_source_summary_value,
                evidence.sourceSummary.citedSourceCount,
                evidence.sourceSummary.allSourceCount,
                evidence.sourceSummary.uncitedSourceCount,
                evidence.sourceSummary.urlSourceCount,
                evidence.sourceSummary.structuredSourceCount,
            ),
    )
    OpenAIWebSearchDetailLine(
        label = stringResource(R.string.openai_web_search_domain_policy_state),
        value = evidence.sourceDiagnostics.domainPolicyState.wireValue,
    )
    OpenAIWebSearchDetailLine(
        label = stringResource(R.string.openai_web_search_total_elapsed),
        value =
            stringResource(
                R.string.openai_web_search_milliseconds,
                evidence.executionDiagnostics.totalElapsedMs,
            ),
    )
    evidence.executionDiagnostics.queueWaitMs?.let { elapsedMs ->
        OpenAIWebSearchDetailLine(
            label = stringResource(R.string.openai_web_search_queue_wait),
            value =
                stringResource(
                    R.string.openai_web_search_milliseconds,
                    elapsedMs,
                ),
        )
    }
    evidence.executionDiagnostics.httpElapsedMs?.let { elapsedMs ->
        OpenAIWebSearchDetailLine(
            label = stringResource(R.string.openai_web_search_http_elapsed),
            value =
                stringResource(
                    R.string.openai_web_search_milliseconds,
                    elapsedMs,
                ),
        )
    }
    evidence.executionDiagnostics.responseHeaderWaitMs?.let { elapsedMs ->
        OpenAIWebSearchDetailLine(
            label = stringResource(R.string.openai_web_search_response_header_wait),
            value =
                stringResource(
                    R.string.openai_web_search_milliseconds,
                    elapsedMs,
                ),
        )
    }
    evidence.executionDiagnostics.responseBodyReadMs?.let { elapsedMs ->
        OpenAIWebSearchDetailLine(
            label = stringResource(R.string.openai_web_search_response_body_read),
            value =
                stringResource(
                    R.string.openai_web_search_milliseconds,
                    elapsedMs,
                ),
        )
    }
    evidence.executionDiagnostics.parseMs?.let { elapsedMs ->
        OpenAIWebSearchDetailLine(
            label = stringResource(R.string.openai_web_search_parse_elapsed),
            value =
                stringResource(
                    R.string.openai_web_search_milliseconds,
                    elapsedMs,
                ),
        )
    }
    evidence.executionDiagnostics.callbackDeliveryMs?.let { elapsedMs ->
        OpenAIWebSearchDetailLine(
            label = stringResource(R.string.openai_web_search_callback_delivery),
            value =
                stringResource(
                    R.string.openai_web_search_milliseconds,
                    elapsedMs,
                ),
        )
    }
    OpenAIWebSearchDetailLine(
        label = stringResource(R.string.openai_web_search_submission_state),
        value = evidence.executionDiagnostics.submissionState.wireValue,
    )
    OpenAIWebSearchDetailLine(
        label = stringResource(R.string.openai_web_search_location_precision),
        value = evidence.executionDiagnostics.location.precision,
    )
    evidence.executionDiagnostics.providerRequestId?.let { providerRequestId ->
        OpenAIWebSearchDetailLine(
            label = stringResource(R.string.openai_web_search_provider_request_id),
            value = providerRequestId,
        )
    }
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
    OpenAIWebSearchDetailLine(
        label = stringResource(R.string.openai_web_search_citations),
        value = evidence.citations.size.toString(),
    )
    evidence.warnings.forEach { warning ->
        OpenAIWebSearchWarningRow(warning)
    }
}

@Composable
private fun OpenAIWebSearchWarningRow(
    warning: String,
) {
    val severity =
        OpenAIWebSearchResultPresentationPolicy.warningSeverity(warning)
    val (icon, color) =
        when (severity) {
            OpenAIWebSearchWarningSeverity.INFO ->
                Icons.Default.Info to MaterialTheme.colorScheme.primary

            OpenAIWebSearchWarningSeverity.WARNING ->
                Icons.Default.Warning to MaterialTheme.colorScheme.tertiary

            OpenAIWebSearchWarningSeverity.ERROR ->
                Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        SelectionContainer(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = warning,
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
    }
}

@Composable
private fun OpenAIWebSearchDetailLine(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        SelectionContainer(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
