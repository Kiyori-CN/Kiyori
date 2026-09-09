package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.ChatHistory
import kotlinx.coroutines.CancellationException

data class ChatGroupCreationResult(val chatId: String, val selected: Boolean)

class ChatCreationSelectionException(val createdChatId: String, val failureType: String) :
    IllegalStateException("Chat created, but selecting it did not complete")

/** 创建提交与当前选择不是共同事务，后者失败不能重复创建。 */
internal suspend fun completeChatCreation(
    create: suspend () -> ChatHistory,
    select: suspend (ChatHistory) -> Boolean,
): ChatGroupCreationResult {
    val created = create()
    return try {
        ChatGroupCreationResult(created.id, select(created))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        throw ChatCreationSelectionException(created.id, failure.javaClass.simpleName)
    }
}
