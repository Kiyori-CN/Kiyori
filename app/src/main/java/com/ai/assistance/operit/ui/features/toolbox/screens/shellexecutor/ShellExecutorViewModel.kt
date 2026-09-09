package com.ai.assistance.operit.ui.features.toolbox.screens.shellexecutor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 页面重建沿用同一次执行；所有发送入口在启动协程前共用占位。 */
class ShellExecutorViewModel(
    private val execute: suspend (String) -> CommandRecord,
) : ViewModel() {
    constructor(context: Context) : this(ShellCommandManager(context.applicationContext)::executeCommand)

    var commandInput by mutableStateOf("")
    var isExecuting by mutableStateOf(false)
        private set
    var commandHistory by mutableStateOf(emptyList<CommandRecord>())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun executeCommand(command: String) {
        if (isExecuting || command.isBlank()) return
        val submittedDraft = commandInput
        val submittedCommand = command.trim()
        isExecuting = true
        errorMessage = null
        viewModelScope.launch {
            try {
                val record = execute(submittedCommand)
                commandHistory = (listOf(record) + commandHistory.filterNot { it.command == record.command }).take(100)
                // 历史重执行不能清空无关草稿，迟到结果也不能清空等待时的新输入。
                if (submittedDraft.trim() == submittedCommand && commandInput == submittedDraft) commandInput = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorMessage = error.message ?: error.javaClass.simpleName
            } finally {
                isExecuting = false
            }
        }.invokeOnCompletion { isExecuting = false }
    }

    fun clearHistory() {
        if (!isExecuting) commandHistory = emptyList()
    }

    fun clearError() { errorMessage = null }
}
