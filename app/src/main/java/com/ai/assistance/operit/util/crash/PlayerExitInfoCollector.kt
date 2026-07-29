package com.ai.assistance.operit.util.crash

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.nio.charset.StandardCharsets

internal data class PlayerExitEvidence(
    val reason: Int? = null,
    val status: Int? = null,
    val description: String? = null,
    val importance: Int? = null,
    val processStateSummary: String? = null,
)

internal object PlayerExitInfoCollector {
    fun collect(context: Context, processId: Int): PlayerExitEvidence {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || processId <= 0) {
            return PlayerExitEvidence()
        }
        val activityManager =
            context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val exit =
            activityManager
                .getHistoricalProcessExitReasons(context.packageName, processId, 8)
                .filter { info ->
                    info.pid == processId && info.processName.endsWith(":player")
                }
                .maxByOrNull { info -> info.timestamp }
                ?: return PlayerExitEvidence()
        return PlayerExitEvidence(
            reason = exit.reason,
            status = exit.status,
            description = exit.description?.take(2_000),
            importance = exit.importance,
            processStateSummary =
                exit.processStateSummary
                    ?.toString(StandardCharsets.UTF_8)
                    ?.take(512),
        )
    }
}
