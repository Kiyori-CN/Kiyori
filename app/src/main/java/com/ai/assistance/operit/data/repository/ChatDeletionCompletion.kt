package com.ai.assistance.operit.data.repository

import kotlinx.coroutines.CancellationException

/** 数据库删除已完成，不能把后续清理错误解释为可以重复删除。 */
class ChatDeletionCleanupException(val failureType: String) :
    IllegalStateException("Chat deleted, but post-deletion cleanup did not complete")

internal suspend fun completeChatDeletion(
    delete: suspend () -> Boolean,
    afterDelete: suspend () -> Unit,
): Boolean {
    if (!delete()) return false
    try {
        afterDelete()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        // 不附带原始message/cause，避免兼容调用方记录清理异常时泄露存储细节。
        throw ChatDeletionCleanupException(failure.javaClass.simpleName)
    }
    return true
}
