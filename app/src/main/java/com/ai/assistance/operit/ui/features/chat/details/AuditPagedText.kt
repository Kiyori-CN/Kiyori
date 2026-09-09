package com.ai.assistance.operit.ui.features.chat.details

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.ai.assistance.operit.R

internal const val AUDIT_TEXT_PAGE_SIZE = 12_000

internal fun auditTextPage(text: String, page: Int): String {
    var start = (page.toLong() * AUDIT_TEXT_PAGE_SIZE).coerceIn(0L, text.length.toLong()).toInt()
    var end = ((page.toLong() + 1) * AUDIT_TEXT_PAGE_SIZE).coerceIn(start.toLong(), text.length.toLong()).toInt()
    // 页边界不能切开 UTF-16 代理对；相邻页面使用同一调整，既不丢字也不重复。
    if (start in 1 until text.length && text[start].isLowSurrogate() && text[start - 1].isHighSurrogate()) start--
    if (end in 1 until text.length && text[end].isLowSurrogate() && text[end - 1].isHighSurrogate()) end--
    return text.substring(start, end)
}

@Composable
internal fun AuditPagedText(text: String, searchQuery: String? = null) {
    val count = ((text.length.toLong() + AUDIT_TEXT_PAGE_SIZE - 1) / AUDIT_TEXT_PAGE_SIZE).toInt().coerceAtLeast(1)
    var page by rememberSaveable(text.length, searchQuery) {
        val firstMatch = searchQuery.orEmpty().split(Regex("\\s+")).filter(String::isNotBlank)
            .map { text.indexOf(it, ignoreCase = true) }.filter { it >= 0 }.minOrNull() ?: 0
        mutableIntStateOf(firstMatch / AUDIT_TEXT_PAGE_SIZE)
    }
    val visible = remember(text, page) { auditTextPage(text, page) }
    Column {
        Text(visible, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (count > 1) {
            Text(stringResource(R.string.conversation_audit_text_page, page + 1, count, text.length),
                style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { page-- }, enabled = page > 0) { Text(stringResource(R.string.conversation_audit_previous_page)) }
                TextButton(onClick = { page++ }, enabled = page + 1 < count) { Text(stringResource(R.string.conversation_audit_next_page)) }
            }
        }
    }
}
