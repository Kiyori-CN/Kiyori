package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.tools.javascript.extractJsExecutionErrorMessage
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.platform.android.ApplicationContextAccess
import java.util.concurrent.TimeUnit

/**
 * One shared deadline for a synchronous ToolPkg Hook dispatch chain.
 *
 * Giving every Hook the full configured timeout would make several sequential Hooks multiply the
 * time spent before a message can continue. Each invocation therefore receives only the time
 * remaining from this chain-wide deadline.
 */
internal class ToolPkgHookExecutionBudget private constructor(
    private val startedAtNanos: Long,
    private val deadlineNanos: Long
) {
    companion object {
        fun create(): ToolPkgHookExecutionBudget {
            val context = ApplicationContextAccess.current
            val timeoutSeconds =
                DisplayPreferencesManager.getInstance(context).getToolPkgHookTimeoutSeconds()
            val startedAtNanos = System.nanoTime()
            return ToolPkgHookExecutionBudget(
                startedAtNanos = startedAtNanos,
                deadlineNanos = startedAtNanos + TimeUnit.SECONDS.toNanos(timeoutSeconds.toLong())
            )
        }
    }

    fun remainingMillis(): Long? {
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0L) {
            return null
        }
        return TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L)
    }

    fun elapsedMillis(): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)

    fun logDeadlineReached(
        tag: String,
        stage: String,
        containerPackageName: String,
        hookId: String
    ) {
        AppLogger.w(
            tag,
            "ToolPkg hook skipped after timeout: stage=$stage, container=$containerPackageName, hook=$hookId, elapsedMs=${elapsedMillis()}"
        )
    }

    fun logTimeoutIfPresent(
        result: Result<Any?>,
        tag: String,
        stage: String,
        containerPackageName: String,
        hookId: String
    ): Boolean {
        val failureMessage =
            result.getOrNull()?.let(::extractJsExecutionErrorMessage)
                ?: result.exceptionOrNull()?.message
        if (failureMessage?.contains("timed out", ignoreCase = true) != true) {
            return false
        }
        AppLogger.w(
            tag,
            "ToolPkg hook timed out: stage=$stage, container=$containerPackageName, hook=$hookId, elapsedMs=${elapsedMillis()}, reason=$failureMessage"
        )
        return true
    }
}
