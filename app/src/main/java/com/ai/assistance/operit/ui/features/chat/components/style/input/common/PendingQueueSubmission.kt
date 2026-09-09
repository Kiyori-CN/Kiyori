package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 队列项暂时移出后只借给当前提交任务。未派发即结束时交回原队列；
 * 明确被插件消费或已开始派发后不恢复，避免把未知提交误当未发送而重做。
 */
internal fun launchPendingQueueSubmission(
    scope: CoroutineScope,
    restore: () -> Unit,
    onFailure: (Exception, dispatched: Boolean) -> Unit,
    submit: suspend (markDispatched: () -> Unit) -> Unit,
): Job {
    val dispatched = AtomicBoolean(false)
    val job = scope.launch {
        try {
            submit { dispatched.set(true) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            onFailure(failure, dispatched.get())
        }
    }
    // 也覆盖scope已经取消、协程正文尚未开始的情形；finally无法覆盖这一边界。
    job.invokeOnCompletion { if (!dispatched.get()) restore() }
    return job
}
