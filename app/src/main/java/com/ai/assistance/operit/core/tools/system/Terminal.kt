package com.ai.assistance.operit.core.tools.system

import android.content.Context
import android.os.Build
import com.ai.assistance.operit.util.AppLogger
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.terminal.CommandExecutionEvent
import com.ai.assistance.operit.terminal.CommandCancellationResult
import com.ai.assistance.operit.terminal.SessionDirectoryEvent
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.data.TerminalState
import com.ai.assistance.operit.terminal.data.SessionInitState
import com.ai.assistance.operit.terminal.provider.type.HiddenExecResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withTimeout
import com.kiyori.platform.storage.KiyoriArtifactStoragePolicy
import com.kiyori.platform.storage.ArtifactPathRules
import com.ai.assistance.operit.terminal.provider.type.TerminalType
import java.util.UUID

/**
 * 终端管理器
 * 提供应用程序级别的终端服务管理和访问
 */
@RequiresApi(Build.VERSION_CODES.O)
class Terminal private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: Terminal? = null

        fun getInstance(context: Context): Terminal {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Terminal(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val TAG = "Terminal"
    }

    private val terminalManager = TerminalManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    // 从 TerminalManager 暴露状态和事件流
    val commandEvents: SharedFlow<CommandExecutionEvent> = terminalManager.commandExecutionEvents
    val directoryEvents: SharedFlow<SessionDirectoryEvent> = terminalManager.directoryChangeEvents
    val terminalState: StateFlow<TerminalState> = terminalManager.terminalState
    val sessions = terminalManager.sessions
    val currentSessionId = terminalManager.currentSessionId
    val currentDirectory = terminalManager.currentDirectory
    val isInteractiveMode = terminalManager.isInteractiveMode
    val interactivePrompt = terminalManager.interactivePrompt
    val isFullscreen = terminalManager.isFullscreen

    /**
     * 初始化终端管理器
     */
    suspend fun initialize(): Boolean {
        return terminalManager.initializeEnvironment()
    }

    /**
     * 销毁终端管理器
     */
    fun destroy() {
        terminalManager.cleanup()
    }

    /**
     * 创建新的终端会话 - 同步等待初始化完成
     */
    suspend fun createSession(title: String? = null): String {
        AppLogger.d(TAG, "Creating new terminal session and waiting for initialization")
        val newSession = terminalManager.createNewSession(title)
        // 只初始化 AI 新建的本地会话；已有会话和 SSH 的 cwd 属于各自真实终端。
        // READY 后等待目录命令完成，失败关闭本次新会话，不能在 /root 静默继续执行。
        if (newSession.terminalType == TerminalType.LOCAL) {
            try {
                val command = ArtifactPathRules.initialDirectoryCommand(KiyoriArtifactStoragePolicy.roots(context).linux)
                val result = withTimeout(30_000L) {
                    executeCommandFlow(newSession.id, command).filter { it.isCompleted }.last()
                }
                check(result.exitCode == 0) { "Cannot initialize AI artifact directory (exit=${result.exitCode})" }
            } catch (error: Exception) {
                terminalManager.closeSession(newSession.id)
                throw error
            }
        }
        AppLogger.d(TAG, "Session ${newSession.id} initialized successfully")
        return newSession.id
    }
    
    /**
     * 切换到指定会话
     */
    fun switchToSession(sessionId: String) {
        terminalManager.switchToSession(sessionId)
    }

    /**
     * 关闭终端会话
     */
    fun closeSession(sessionId: String) {
        terminalManager.closeSession(sessionId)
    }

    /**
     * 执行命令并等待其完成（不切换当前会话）
     */
    suspend fun executeCommand(sessionId: String, command: String): String? {
        val deferred = CompletableDeferred<String>()
        val output = StringBuilder()
        
        // 生成命令ID
        val commandId = java.util.UUID.randomUUID().toString()
        
        // 先开始订阅事件流，然后再发送命令
        // SharedFlow has no replay. UNDISPATCHED reaches its real subscription point before a fast
        // command can publish any event.
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            commandEvents
                .filter { it.sessionId == sessionId && it.commandId == commandId }
                .collect { event ->
                    if (!event.isCompleted) {
                        output.append(event.outputChunk)
                    }
                    if (event.isCompleted) {
                        deferred.complete(output.toString())
                    }
                }
        }

        return try {
            // 直接向指定会话发送命令，不切换当前会话
            terminalManager.sendCommandToSession(sessionId, command, commandId)
            deferred.await()
        } finally {
            // This collector belongs to a singleton scope rather than the caller. Always stop it
            // when submission, waiting, or the caller itself fails or is cancelled.
            job.cancel()
        }
    }

    suspend fun executeHiddenCommand(
        command: String,
        executorKey: String = "default",
        timeoutMs: Long = 120000L,
        localOnly: Boolean = false,
    ): HiddenExecResult {
        return terminalManager.executeHiddenCommand(
            command = command,
            executorKey = executorKey,
            timeoutMs = timeoutMs,
            localOnly = localOnly,
        )
    }

    /**
     * 执行命令 - Flow版本
     * 返回命令执行过程中的所有事件，直到命令完成
     */
    fun executeCommandFlow(
        sessionId: String,
        command: String,
        commandId: String = UUID.randomUUID().toString(),
    ): Flow<CommandExecutionEvent> {
        return channelFlow {
            // Start undispatched so the zero-replay SharedFlow subscription is active before send.
            val collectorJob = launch(start = CoroutineStart.UNDISPATCHED) {
                commandEvents
                    .filter { it.sessionId == sessionId && it.commandId == commandId }
                    .transformWhile { event ->
                        emit(event)
                        !event.isCompleted
                    }
                    .collect { sentEvent ->
                        send(sentEvent)
                    }
            }

            try {
                terminalManager.sendCommandToSession(sessionId, command, commandId)
                collectorJob.join()
            } finally {
                // A timeout cancels the downstream collector. Stop this subscription immediately;
                // the caller keeps a separate collector alive while command cancellation settles.
                collectorJob.cancel()
            }
        }
    }

    suspend fun cancelCommand(
        sessionId: String,
        commandId: String,
        settleTimeoutMs: Long,
    ): CommandCancellationResult =
        terminalManager.cancelCommand(sessionId, commandId, settleTimeoutMs)

    suspend fun ensureSessionReady(sessionId: String): Boolean =
        terminalManager.ensureSessionReady(sessionId)

    fun getSessionShellGeneration(sessionId: String): Long? =
        terminalState.value.sessions.find { it.id == sessionId }?.shellGeneration

    fun isSessionHealthy(sessionId: String): Boolean {
        val session = terminalState.value.sessions.find { it.id == sessionId } ?: return false
        return session.initState == SessionInitState.READY &&
            session.sessionWriter != null &&
            session.terminalSession?.process?.isAlive == true
    }
    
    /**
     * 发送输入到当前会话
     */
    fun sendInput(sessionId: String, input: String) {
        terminalManager.switchToSession(sessionId)
        terminalManager.sendInput(input)
    }

    /**
     * 发送中断信号 (Ctrl+C)
     */
    fun sendInterruptSignal(sessionId: String) {
        terminalManager.sendInterruptSignal(sessionId)
    }

    /** 固定目标并等待实际写入，不切换用户当前标签。 */
    suspend fun sendInputAndWait(sessionId: String, input: String) =
        terminalManager.sendInputToSession(sessionId, input)

    /**
     * 检查服务是否已连接 (现在总是返回 true)
     */
    fun isConnected(): Boolean {
        return true
    }
}
