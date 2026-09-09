package com.ai.assistance.operit.ui.features.chat.components

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.GenerationSpeed
import com.ai.assistance.operit.data.model.ProviderUsageAggregate
import com.ai.assistance.operit.ui.components.KiyoriModalBottomDrawer
import com.ai.assistance.operit.ui.common.copyPlainTextToClipboard
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
import java.text.NumberFormat
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun ChatStatisticsButton(
    chatId: String?,
    currentTokens: Long,
    maxTokens: Long,
    usage: ProviderUsageAggregate,
    speedFlow: StateFlow<GenerationSpeed?>,
) {
    // 会话变化时关闭旧面板，避免把前一个会话的可见状态带到新会话。
    var expanded by rememberSaveable(chatId) { mutableStateOf(false) }
    val ratio = ChatStatisticsFormatter.contextUsageRatio(currentTokens, maxTokens)
    val percentage = ChatStatisticsFormatter.formatContextUsage(currentTokens, maxTokens)
        ?: stringResource(R.string.chat_stats_unavailable)
    val description = stringResource(R.string.chat_stats_open, percentage)
    val progress by animateFloatAsState(
        targetValue = (ratio ?: 0.0).toFloat().coerceIn(0f, 1f), label = "ChatContextUsage",
    )
    val tone = contextUsageColor(ratio)
    IconButton(
        onClick = { expanded = true },
        // 统计入口与顶栏其他控件保持紧凑高度，避免默认 48dp 槽位撑高整行。
        modifier = Modifier.requiredSize(36.dp).semantics {
        contentDescription = description
    }) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress }, modifier = Modifier.fillMaxSize(), color = tone,
                strokeWidth = 3.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = ratio?.let { (it * 100).coerceAtMost(999.0).toInt().toString() } ?: "—",
                style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
                fontWeight = FontWeight.Bold, color = tone,
            )
        }
    }
    if (expanded) {
        // 速度每个流分片都可能更新；仅打开面板时订阅，避免持续重组整个聊天栏。
        val speed by speedFlow.collectAsState()
        ChatStatisticsSheet(currentTokens, maxTokens, usage, speed) { expanded = false }
    }
}

@Composable
private fun contextUsageColor(ratio: Double?): Color = when {
    ratio == null -> MaterialTheme.colorScheme.onSurfaceVariant
    ratio > 0.90 -> KiyoriSemanticTone.RED.resolveColors().icon
    ratio > 0.75 -> KiyoriSemanticTone.ORANGE.resolveColors().icon
    else -> KiyoriSemanticTone.BLUE.resolveColors().icon
}

@Composable
private fun ChatStatisticsSheet(
    currentTokens: Long,
    maxTokens: Long,
    usage: ProviderUsageAggregate,
    speed: GenerationSpeed?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val numberFormat = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val unavailable = stringResource(R.string.chat_stats_unavailable)
    val title = stringResource(R.string.chat_stats_title)
    val ratio = ChatStatisticsFormatter.contextUsageRatio(currentTokens, maxTokens)
    val percentage = ChatStatisticsFormatter.formatContextUsage(currentTokens, maxTokens) ?: unavailable
    val contextLabel = stringResource(R.string.chat_stats_context_occupancy)
    val contextCount = stringResource(
        R.string.chat_stats_context_counts,
        numberFormat.format(currentTokens),
        if (maxTokens > 0) numberFormat.format(maxTokens) else unavailable,
    )
    val speedLabel = stringResource(R.string.chat_stats_generation_speed)
    val speedValue = ChatStatisticsFormatter.formatGenerationSpeed(speed?.tokensPerSecond)?.let {
        stringResource(R.string.chat_stats_speed_value, it)
    } ?: unavailable
    val speedSource = stringResource(
        when {
            speed == null -> R.string.chat_stats_speed_no_sample
            speed.isEstimated -> R.string.chat_stats_speed_estimated
            else -> R.string.chat_stats_speed_reported
        },
    )
    fun tokens(value: Long, reported: Boolean) = if (reported) numberFormat.format(value) else unavailable
    val hasUsage = usage.providerUsageRequestCount > 0
    val hasCache = usage.providerCacheMetricRequestCount > 0
    val usageRows = listOf(
        stringResource(R.string.chat_stats_input_label) to tokens(usage.providerTotalInputTokens, hasUsage),
        stringResource(R.string.chat_stats_output_label) to tokens(usage.providerOutputTokens, hasUsage),
        stringResource(R.string.chat_stats_total_label) to tokens(usage.providerTotalTokens, hasUsage),
        stringResource(R.string.chat_stats_reasoning_label) to tokens(usage.providerReasoningTokens, hasUsage),
    )
    val cacheRows = listOf(
        stringResource(R.string.chat_stats_cache_read_label) to tokens(usage.providerCacheReadTokens, hasCache),
        stringResource(R.string.chat_stats_cache_write_label) to tokens(usage.providerCacheWriteTokens, hasCache),
        stringResource(R.string.chat_stats_uncached_label) to tokens(usage.providerUncachedInputTokens, hasUsage),
        stringResource(R.string.chat_stats_cache_rate_label) to
            (ChatStatisticsFormatter.formatCacheHitRate(usage.cacheHitRate) ?: unavailable),
    )
    val coverageRows = listOf(
        stringResource(R.string.chat_stats_requests_label) to numberFormat.format(usage.requestCount),
        stringResource(R.string.chat_stats_usage_coverage_label) to ChatStatisticsFormatter.formatCoverage(
            usage.providerUsageRequestCount, usage.requestCount,
        ),
        stringResource(R.string.chat_stats_cache_coverage_label) to ChatStatisticsFormatter.formatCoverage(
            usage.providerCacheMetricRequestCount, usage.providerUsageRequestCount,
        ),
    )
    val usageNote = stringResource(R.string.chat_stats_usage_note)
    val coverageNote = stringResource(R.string.chat_stats_coverage_note)
    val speedNote = stringResource(R.string.chat_stats_speed_note)
    val contextNote = stringResource(R.string.chat_stats_context_note)
    val cacheNote = stringResource(R.string.chat_stats_cache_note)
    val report = buildString {
        appendLine(title)
        appendLine("$contextLabel: $percentage ($contextCount)")
        appendLine("$speedLabel: $speedValue · $speedSource")
        (usageRows + cacheRows + coverageRows).forEach { (label, value) -> appendLine("$label: $value") }
        appendLine(contextNote)
        appendLine(speedNote)
        appendLine(usageNote)
        appendLine(cacheNote)
        append(coverageNote)
    }

    KiyoriModalBottomDrawer(onDismissRequest = onDismiss,
        modifier = Modifier.semantics { paneTitle = title }) { dismissDrawer ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.chat_stats_current_chat),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                context.copyPlainTextToClipboard(title, report)
                Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.chat_stats_copy))
            }
            IconButton(onClick = dismissDrawer) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close))
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                StatisticsCard {
                    Text(contextLabel, style = MaterialTheme.typography.labelLarge)
                    Text(percentage, style = MaterialTheme.typography.headlineMedium,
                        color = contextUsageColor(ratio), fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(
                        progress = { (ratio ?: 0.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(), color = contextUsageColor(ratio),
                    )
                    Text(contextCount, style = MaterialTheme.typography.bodyMedium)
                    StatisticsNote(contextNote)
                    if (ratio != null && ratio >= 0.9) {
                        Text(stringResource(if (ratio >= 1) R.string.chat_stats_context_over_limit
                            else R.string.chat_stats_context_near_limit),
                            style = MaterialTheme.typography.bodySmall, color = contextUsageColor(ratio))
                    }
                }
            }
            item {
                StatisticsCard {
                    Text(speedLabel, style = MaterialTheme.typography.labelLarge)
                    Text(speedValue, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(speedSource, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    StatisticsNote(speedNote)
                }
            }
            item {
                StatisticsCard {
                    Text(stringResource(R.string.chat_stats_usage_section),
                        style = MaterialTheme.typography.titleSmall)
                    usageRows.forEachIndexed { index, (label, value) ->
                        StatisticsRow(label, value, highlighted = index == 2)
                    }
                    StatisticsNote(usageNote)
                }
            }
            item {
                StatisticsCard {
                    Text(stringResource(R.string.chat_stats_cache_section),
                        style = MaterialTheme.typography.titleSmall)
                    cacheRows.forEach { (label, value) -> StatisticsRow(label, value) }
                    StatisticsNote(cacheNote)
                }
            }
            item {
                StatisticsCard {
                    Text(stringResource(R.string.chat_stats_coverage_section),
                        style = MaterialTheme.typography.titleSmall)
                    coverageRows.forEach { (label, value) -> StatisticsRow(label, value) }
                    if (usage.providerUsageRequestCount < usage.requestCount) {
                        Text(stringResource(R.string.chat_stats_partial_usage),
                            style = MaterialTheme.typography.bodySmall,
                            color = KiyoriSemanticTone.ORANGE.resolveColors().icon)
                    }
                    StatisticsNote(coverageNote)
                }
            }
        }
    }
}

@Composable
private fun StatisticsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun StatisticsNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatisticsRow(label: String, value: String, highlighted: Boolean = false) {
    val valueColor = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 大字体或窄窗口时改成上下排布，完整保留标签与数字，不裁剪精确 token 数。
        if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.titleMedium, color = valueColor)
            }
        }
    }
}
