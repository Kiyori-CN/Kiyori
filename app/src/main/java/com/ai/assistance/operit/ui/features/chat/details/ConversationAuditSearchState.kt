package com.ai.assistance.operit.ui.features.chat.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.data.audit.ConversationAuditSearchMatch
import com.ai.assistance.operit.data.audit.ConversationAuditSearchPage
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal class ConversationAuditSearchState {
    var matches by mutableStateOf(emptyList<ConversationAuditSearchMatch>())
    var scannedCount by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var hasMore by mutableStateOf(true)
    var error by mutableStateOf(false)
    var request by mutableIntStateOf(0)
    var cursor: Long? = null
}

@Composable
internal fun rememberConversationAuditSearch(
    query: String,
    search: suspend (String, Long?) -> ConversationAuditSearchPage,
): ConversationAuditSearchState {
    val state = remember(query) { ConversationAuditSearchState() }
    val currentSearch by rememberUpdatedState(search)
    LaunchedEffect(query, state.request) {
        if (query.isBlank()) return@LaunchedEffect
        state.loading = true
        state.error = false
        try {
            delay(350)
            val initialCount = state.matches.size
            do {
                val page = currentSearch(query, state.cursor)
                currentCoroutineContext().ensureActive()
                state.matches = (state.matches + page.matches).distinctBy { it.event.eventId }
                state.scannedCount += page.scannedCount
                state.cursor = page.nextBeforeSequence
                state.hasMore = page.nextBeforeSequence != null
                // 搜索可以遍历全历史，但每批只保留有限命中；后续由用户继续，避免一次铺开长对话。
            } while (state.hasMore && state.matches.size - initialCount < 100)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            state.error = true
            AppLogger.e("ConversationDetails", "审计全文搜索失败", failure)
        } finally {
            state.loading = false
        }
    }
    return state
}
