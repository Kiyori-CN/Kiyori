package com.ai.assistance.operit.core.tools

import android.content.Context
import com.ai.assistance.operit.data.db.AppDatabase
import com.kiyori.platform.storage.KiyoriArtifactStoragePolicy
import com.kiyori.platform.storage.ArtifactPathRules
import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.provider.type.TerminalType
import com.ai.assistance.operit.terminal.utils.SSHConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** 只投影已存在的工作区绑定，不建立第二份对话或路径状态。 */
internal object ArtifactStorageAccess {
    fun currentChatId(): String? = ToolExecutionManager.currentToolRuntimeContext()?.callerChatId

    private fun workspace(context: Context, chatId: String?) =
        if (chatId.isNullOrBlank()) null else runBlocking(Dispatchers.IO) {
            AppDatabase.getDatabase(context).chatDao().getChatById(chatId)
        }

    fun root(context: Context, environment: String, chatId: String? = currentChatId()): String {
        val chat = workspace(context, chatId)
        return ArtifactPathRules.projectRoot(
            { KiyoriArtifactStoragePolicy.root(context, environment) }, environment, chat?.workspace, chat?.workspaceEnv,
        )
    }

    fun paths(context: Context, chatId: String? = currentChatId()): KiyoriArtifactStoragePolicy.Roots {
        val chat = workspace(context, chatId)
        fun resolve(environment: String) = ArtifactPathRules.projectRoot(
            { KiyoriArtifactStoragePolicy.root(context, environment) }, environment, chat?.workspace, chat?.workspaceEnv,
        )
        return KiyoriArtifactStoragePolicy.Roots(resolve("android"), resolve("linux"))
    }

    fun linuxIsLocal(context: Context): Boolean {
        // provider 才是实际执行环境；仅在它尚未初始化时读取当前会话/已保存连接偏好。
        val manager = TerminalManager.getInstance(context)
        val type = manager.activeEnvironmentType.value
            ?: manager.terminalState.value.currentSession?.terminalType
            ?: if (SSHConfigManager(context).isEnabled()) TerminalType.SSH else TerminalType.LOCAL
        return type != TerminalType.SSH
    }
}
