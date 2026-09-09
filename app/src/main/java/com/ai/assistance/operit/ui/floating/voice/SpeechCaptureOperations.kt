package com.ai.assistance.operit.ui.floating.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 同一共享识别服务的 UI 操作串行交接。只管理操作 Job 和调用身份，不复制服务的录音状态。
 * 由主线程调用；旧页面清理只能释放自己的请求，新启动必须等待先前的资源释放。
 */
internal class SpeechCaptureOperations {
    private var activeOwner: Any? = null
    private var operation: Job? = null

    fun owns(owner: Any): Boolean = activeOwner === owner

    fun start(
        owner: Any,
        scope: CoroutineScope,
        releasePrevious: suspend () -> Boolean = { true },
        action: suspend () -> Unit,
    ): Job {
        val needsHandoff = activeOwner != null && activeOwner !== owner
        activeOwner = owner
        val previous = operation
        previous?.cancel()
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // 新页面可能先于旧页面的 dispose 启动，交接本身也必须释放旧捕获。
            val released = withContext(NonCancellable) {
                previous?.join()
                !needsHandoff || releasePrevious()
            }
            if (!released) return@launch
            currentCoroutineContext().ensureActive()
            action()
        }.also { operation = it }
    }

    fun stop(
        owner: Any,
        scope: CoroutineScope,
        releaseOwner: Boolean = true,
        release: suspend () -> Unit,
    ): Job? {
        if (!owns(owner)) return null
        if (releaseOwner) activeOwner = null
        val previous = operation
        previous?.cancel()
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // 离开组合时父 scope 可能已经取消，资源释放仍必须完成；此处禁止业务请求。
            withContext(NonCancellable) {
                previous?.join()
                release()
            }
        }.also { operation = it }
    }
}
