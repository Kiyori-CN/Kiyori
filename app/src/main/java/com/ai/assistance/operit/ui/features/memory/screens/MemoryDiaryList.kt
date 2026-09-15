package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.MemoryDiaryPolicy
import com.ai.assistance.operit.data.model.MemoryLibraryPolicy
import com.kiyori.design.theme.KiyoriUiShapes
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 内容类型的界面名称；详情、卡片与筛选共用同一份文案，避免三处各写一遍。 */
@Composable
internal fun memoryKindLabel(kind: String): String = stringResource(
    when (kind) {
        MemoryLibraryPolicy.KNOWLEDGE -> R.string.library_knowledge
        MemoryLibraryPolicy.DIARY -> R.string.library_diary
        else -> R.string.library_memories
    }
)

/** 阶段键渲染为当前语言的名称；无法识别的自定义标题按原文显示，不静默改写用户写下的内容。 */
@Composable
internal fun diaryPhaseLabel(phase: String, rawPhase: String? = null): String {
    val known = when (phase) {
        MemoryDiaryPolicy.PLAN -> R.string.library_phase_plan
        MemoryDiaryPolicy.PROGRESS -> R.string.library_phase_progress
        MemoryDiaryPolicy.DECISION -> R.string.library_phase_decision
        MemoryDiaryPolicy.EVIDENCE -> R.string.library_phase_evidence
        MemoryDiaryPolicy.RISK -> R.string.library_phase_risk
        MemoryDiaryPolicy.VALIDATION -> R.string.library_phase_validation
        MemoryDiaryPolicy.CLOSED -> R.string.library_phase_closed
        else -> R.string.library_phase_note
    }
    val fallback = rawPhase?.trim()?.takeIf { it.isNotEmpty() && phase == MemoryDiaryPolicy.NOTE && it.lowercase(Locale.US) != MemoryDiaryPolicy.NOTE }
    return fallback ?: stringResource(known)
}

@Composable
internal fun diaryStatusLabel(status: String): String = stringResource(
    if (status == MemoryDiaryPolicy.STATUS_CLOSED) R.string.library_diary_status_closed
    else R.string.library_diary_status_active
)

/**
 * 日期分组标签。今天与昨天按设备当前日期判断，其余按本地格式展示完整日期，
 * 不把“今天”写进正文，否则同一条记录隔天读起来就是错的。
 */
@Composable
internal fun diaryDayLabel(day: String): String {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }
    val keyFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val today = remember(day) { keyFormat.format(Calendar.getInstance().time) }
    val yesterday = remember(day) {
        keyFormat.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)
    }
    return when (day) {
        today -> stringResource(R.string.library_diary_today)
        yesterday -> stringResource(R.string.library_diary_yesterday)
        else -> remember(day, locale) { runCatching { dateFormat.format(requireNotNull(keyFormat.parse(day))) }.getOrDefault(day) }
    }
}

/** 一天可以写多篇日记，因此分组键是开篇日期而不是条目身份。 */
internal fun groupDiariesByDay(memories: List<Memory>): List<Pair<String, List<Memory>>> =
    memories.groupBy { MemoryDiaryPolicy.firstDay(it.content, it.createdAt) }
        .toList()
        .sortedByDescending { it.first }

internal fun LazyListScope.diaryDayGroups(
    memories: List<Memory>,
    dateFormat: DateFormat,
    onOpen: (Memory) -> Unit,
) {
    groupDiariesByDay(memories).forEach { (day, entries) ->
        item(key = "diary-day:$day") { DiaryDayHeader(day, entries.size) }
        items(entries.size, key = { entries[it].id }) { index ->
            MemoryDiaryCard(entries[index], dateFormat, showFolder = false, onClick = { onOpen(entries[index]) })
        }
    }
}

@Composable
private fun DiaryDayHeader(day: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            diaryDayLabel(day), Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.library_count, count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 日记卡片：先回答“进行到哪了”，再回答“写了什么”。
 * 摘要取最近一条有内容的记录，而不是开篇，否则长期续写的日记卡片会一直停在第一天。
 */
@Composable
internal fun MemoryDiaryCard(
    memory: Memory,
    dateFormat: DateFormat,
    showFolder: Boolean,
    onClick: () -> Unit,
) {
    val diary = remember(memory.id, memory.content) { MemoryDiaryPolicy.parse(memory.content) }
    val summary = remember(diary) {
        (diary.entries.lastOrNull { it.body.isNotBlank() }?.body ?: diary.preface).take(400)
    }
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = KiyoriUiShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    memory.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                DiaryStatusPill(diary.status)
            }
            if (summary.isNotBlank()) Text(
                summary, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            val phase = diary.lastEntry?.let { diaryPhaseLabel(it.phase, it.rawPhase) }
            Text(
                listOfNotNull(
                    stringResource(R.string.library_diary_entries, diary.entries.size),
                    phase,
                    dateFormat.format(memory.updatedAt),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val tags = memory.tags.map { it.name }.take(3)
            if (tags.isNotEmpty()) Text(
                tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (showFolder && !memory.folderPath.isNullOrBlank()) Text(
                memory.folderPath.orEmpty(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun DiaryStatusPill(status: String) {
    val closed = status == MemoryDiaryPolicy.STATUS_CLOSED
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (closed) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                if (closed) Icons.Outlined.CheckCircle else Icons.Outlined.PendingActions, null,
                Modifier.size(14.dp),
                tint = if (closed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                diaryStatusLabel(status), style = MaterialTheme.typography.labelSmall,
                color = if (closed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
