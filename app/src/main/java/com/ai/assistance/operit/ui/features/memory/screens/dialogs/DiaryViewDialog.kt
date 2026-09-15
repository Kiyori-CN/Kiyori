package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.MemoryDiaryPolicy
import com.ai.assistance.operit.ui.features.memory.screens.DiaryStatusPill
import com.ai.assistance.operit.ui.features.memory.screens.diaryPhaseLabel
import com.kiyori.design.theme.KiyoriUiShapes
import java.text.SimpleDateFormat

/**
 * 日记详情：上半部是只读的时间线，下半部是固定可见的续写区。
 *
 * 续写和编辑是两件事。追加只增加新的一节，历史记录不被改写；要修改既有内容才走“编辑全文”，
 * 并明确告诉用户那会改写历史。把两者放在同一个输入框里会让人分不清刚才到底改了什么。
 */
@Composable
fun DiaryViewDialog(
    memory: Memory,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onAppend: (body: String, phase: String) -> Unit,
) {
    val diary = remember(memory.id, memory.content) { MemoryDiaryPolicy.parse(memory.content) }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("yyyy-MM-dd HH:mm", locale) }

    var draft by rememberSaveable(memory.id) { mutableStateOf("") }
    var phase by rememberSaveable(memory.id) { mutableStateOf(MemoryDiaryPolicy.PROGRESS) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var closingSummary by rememberSaveable(memory.id) { mutableStateOf("") }

    // 追加成功后正文会变长，草稿已经写进日记，保留会让人误以为还没提交。
    // 但旋转屏幕同样会重跑这个效应，所以只在条目数真的增加时清空，否则会吞掉恢复出来的草稿。
    val entryCount = diary.entries.size
    var lastEntryCount by rememberSaveable(memory.id) { mutableIntStateOf(entryCount) }
    LaunchedEffect(entryCount) {
        if (entryCount != lastEntryCount) {
            draft = ""
            lastEntryCount = entryCount
        }
    }

    val canWrite = !isSaving && !memory.archived
    val requestDismiss = { if (!isSaving) { if (draft.isBlank()) onDismiss() else confirmDiscard = true } }

    Dialog(onDismissRequest = requestDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(16.dp).widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight(0.92f).imePadding(),
            shape = KiyoriUiShapes.dialog,
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.library_diary),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    DiaryStatusPill(diary.status)
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !isSaving) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.library_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_diary_edit_full)) },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { menu = false; onEdit() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (memory.archived) R.string.library_restore else R.string.library_archive)) },
                                leadingIcon = { Icon(if (memory.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null) },
                                onClick = { menu = false; onArchive() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.memory_delete), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                    IconButton(onClick = requestDismiss, enabled = !isSaving) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.memory_close))
                    }
                }

                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SelectionContainer { Text(memory.title, style = MaterialTheme.typography.headlineSmall) }
                    Text(
                        listOf(
                            stringResource(R.string.library_diary_started, dateFormat.format(memory.createdAt)),
                            stringResource(R.string.library_diary_entries, diary.entries.size),
                            memory.folderPath.orEmpty().ifBlank { stringResource(R.string.library_folder_root) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val tags = memory.tags.map { it.name }
                    if (tags.isNotEmpty()) Text(
                        tags.joinToString("  ") { "#$it" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (memory.archived) DiaryNotice(stringResource(R.string.library_diary_archived_hint))
                    else if (diary.isClosed) DiaryNotice(stringResource(R.string.library_diary_reopen_hint))
                    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

                    if (diary.preface.isNotBlank()) {
                        Text(
                            stringResource(R.string.library_diary_preface),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SelectionContainer { Text(diary.preface, style = MaterialTheme.typography.bodyMedium) }
                        HorizontalDivider()
                    }

                    if (diary.entries.isEmpty()) Text(
                        stringResource(R.string.library_diary_no_entries),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) else diary.entries.forEach { entry ->
                        DiaryTimelineEntry(entry, last = entry.index == diary.entries.lastIndex)
                    }
                    Spacer(Modifier.height(4.dp))
                }

                HorizontalDivider()
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MemoryDiaryPolicy.appendablePhases.size) { index ->
                            val value = MemoryDiaryPolicy.appendablePhases[index]
                            FilterChip(
                                selected = phase == value,
                                onClick = { phase = value },
                                enabled = canWrite,
                                label = { Text(diaryPhaseLabel(value)) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp, max = 160.dp),
                        enabled = canWrite,
                        placeholder = { Text(stringResource(R.string.library_diary_append_hint)) },
                        shape = KiyoriUiShapes.field,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!diary.isClosed) TextButton(onClick = { confirmClose = true }, enabled = canWrite) {
                            Text(stringResource(R.string.library_diary_close), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { onAppend(draft, phase) },
                            enabled = canWrite && draft.isNotBlank(),
                        ) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (isSaving) R.string.library_saving else R.string.library_diary_append_action))
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.memory_delete)) },
        text = { Text(stringResource(R.string.library_delete_confirm)) },
        confirmButton = {
            TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !isSaving) {
                Text(stringResource(R.string.memory_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.memory_cancel)) } },
    )

    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text(stringResource(R.string.library_diary_close_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.library_diary_close_message), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = closingSummary,
                    onValueChange = { closingSummary = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.library_diary_close_summary)) },
                    shape = KiyoriUiShapes.field,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { confirmClose = false; onAppend(closingSummary, MemoryDiaryPolicy.CLOSED); closingSummary = "" },
                enabled = !isSaving,
            ) { Text(stringResource(R.string.library_diary_close)) }
        },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text(stringResource(R.string.memory_cancel)) } },
    )

    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.library_discard_title)) },
        text = { Text(stringResource(R.string.library_discard_message)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text(stringResource(R.string.library_discard)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.library_keep_editing)) } },
    )
}

@Composable
private fun DiaryNotice(text: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 时间线一节：左侧节点与连线表达先后顺序，右侧是那一刻写下的原文。 */
@Composable
private fun DiaryTimelineEntry(entry: MemoryDiaryPolicy.Entry, last: Boolean) {
    val closing = entry.phase == MemoryDiaryPolicy.CLOSED
    val accent = if (closing) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            Modifier.width(20.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape).background(accent))
            if (!last) VerticalDivider(Modifier.weight(1f).padding(vertical = 4.dp))
        }
        Column(
            Modifier.weight(1f).padding(start = 10.dp, bottom = if (last) 0.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    entry.stamp, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(shape = MaterialTheme.shapes.small, color = accent.copy(alpha = 0.12f)) {
                    Text(
                        diaryPhaseLabel(entry.phase, entry.rawPhase),
                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = accent,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (entry.body.isNotBlank()) SelectionContainer {
                Text(entry.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
