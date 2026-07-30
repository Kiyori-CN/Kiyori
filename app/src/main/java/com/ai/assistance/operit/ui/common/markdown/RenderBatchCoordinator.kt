package com.ai.assistance.operit.ui.common.markdown

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 合并流式渲染更新，同时保证刷新期间到达的最后一次结构或内容变化不会丢失。
 *
 * 调用由同一个 Compose scope 串行触发，因此 revision 读取与任务收尾不会和另一请求交错。
 */
internal class RenderBatchCoordinator(
    private val scope: CoroutineScope,
    private val intervalMs: Long,
    private val onFlush: () -> Unit,
) {
    private var requestedRevision = 0L
    private var appliedRevision = 0L
    private var updateJob: Job? = null

    fun requestUpdate() {
        requestedRevision++
        if (updateJob?.isActive == true) {
            return
        }

        updateJob =
            scope.launch {
                try {
                    while (appliedRevision != requestedRevision) {
                        delay(intervalMs)
                        val revisionToApply = requestedRevision
                        onFlush()
                        appliedRevision = revisionToApply
                    }
                } finally {
                    updateJob = null
                }
            }
    }
}
