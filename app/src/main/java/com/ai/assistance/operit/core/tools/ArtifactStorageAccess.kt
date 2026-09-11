package com.ai.assistance.operit.core.tools

import android.content.Context
import com.ai.assistance.operit.data.db.AppDatabase
import com.kiyori.platform.storage.KiyoriArtifactStoragePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** 只投影已存在的工作区绑定，不建立第二份对话或路径状态。 */
internal object ArtifactStorageAccess {
    fun paths(context: Context, chatId: String? = null): KiyoriArtifactStoragePolicy.Roots {
        val defaults = KiyoriArtifactStoragePolicy.roots(context)
        if (chatId.isNullOrBlank()) return defaults
        val chat = runBlocking(Dispatchers.IO) {
            AppDatabase.getDatabase(context).chatDao().getChatById(chatId)
        } ?: return defaults
        val workspace = chat.workspace?.takeIf { it.isNotBlank() } ?: return defaults
        return when (chat.workspaceEnv?.lowercase().orEmpty()) {
            "", "android" -> defaults.copy(android = workspace)
            "linux" -> defaults.copy(linux = workspace)
            else -> throw IllegalArgumentException(
                "The bound workspace uses ${chat.workspaceEnv}; supply an explicit output destination in the matching environment"
            )
        }
    }
}
