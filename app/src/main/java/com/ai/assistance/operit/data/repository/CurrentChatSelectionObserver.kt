package com.ai.assistance.operit.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed interface ChatSelectionReadState {
    data object Loading : ChatSelectionReadState
    data class Ready(val chatId: String?) : ChatSelectionReadState
    // 身份用于消费具体一次失败；错误正文不能进入 UI 或持久状态。
    class Failed(val errorType: String) : ChatSelectionReadState
}

/** 原 DataStore 的共享观察投影。读取失败保留最后有效 ID，只由显式恢复重新订阅。 */
internal class CurrentChatSelectionObserver(
    private val scope: CoroutineScope,
    private val source: () -> Flow<String?>,
) {
    private val mutableId = MutableStateFlow<String?>(null)
    val currentChatId = mutableId.asStateFlow()
    private val mutableState = MutableStateFlow<ChatSelectionReadState>(ChatSelectionReadState.Loading)
    val state = mutableState.asStateFlow()

    init { observe() }

    @Synchronized
    fun retry(expected: ChatSelectionReadState.Failed) {
        if (mutableState.value !== expected) return
        mutableState.value = ChatSelectionReadState.Loading
        observe()
    }

    private fun observe() {
        scope.launch {
            try {
                source().collect { chatId ->
                    mutableId.value = chatId
                    mutableState.value = ChatSelectionReadState.Ready(chatId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = ChatSelectionReadState.Failed(failure.javaClass.simpleName)
            }
        }
    }
}
