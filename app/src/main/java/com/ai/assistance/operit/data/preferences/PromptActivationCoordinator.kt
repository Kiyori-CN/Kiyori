package com.ai.assistance.operit.data.preferences

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 只协调原偏好写入，不持有角色或主题的第二份状态。 */
internal class PromptActivationCoordinator {
    private val revision = AtomicLong()
    private val mutex = Mutex()

    suspend fun activate(stillValid: () -> Boolean, write: suspend (() -> Unit) -> Unit): Boolean {
        val request = revision.incrementAndGet()
        val context = currentCoroutineContext()
        val checkCurrent = {
            context.ensureActive()
            if (revision.get() != request || !stillValid()) throw SupersededActivation()
        }
        return mutex.withLock {
            try {
                checkCurrent()
                write(checkCurrent)
                checkCurrent()
                true
            } catch (superseded: SupersededActivation) {
                // 可能已有单项写入；调用方不能把 false 当作跨存储回滚或成功。
                false
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    private class SupersededActivation : RuntimeException()
}
