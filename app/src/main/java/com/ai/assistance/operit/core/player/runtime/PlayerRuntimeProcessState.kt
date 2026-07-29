package com.ai.assistance.operit.core.player.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.nio.charset.StandardCharsets

internal object PlayerRuntimeProcessState {
    fun update(
        context: Context,
        phase: String,
        runtimeGeneration: Long,
        surfaceGeneration: Long? = null,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val summary =
            buildString {
                append(phase.take(48))
                append(" runtime=")
                append(runtimeGeneration)
                surfaceGeneration?.let { append(" surface=").append(it) }
            }.toByteArray(StandardCharsets.UTF_8)
                .take(MAX_SUMMARY_BYTES)
                .toByteArray()
        val activityManager =
            context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.setProcessStateSummary(summary)
    }

    private const val MAX_SUMMARY_BYTES = 128
}
