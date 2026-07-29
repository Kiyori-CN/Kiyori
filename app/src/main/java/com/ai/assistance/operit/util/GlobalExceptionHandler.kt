package com.ai.assistance.operit.util

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.ui.error.CrashReportActivity
import com.ai.assistance.operit.util.crash.CrashReportStore
import com.ai.assistance.operit.util.crash.CrashReportType
import kotlin.system.exitProcess

class GlobalExceptionHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        val report =
            runCatching { CrashReportStore.createFatalReport(context, thread, ex) }
                .onFailure { error ->
                    Log.e(TAG, "Unable to persist structured crash report", error)
                }
                .getOrNull()

        if (report?.type != CrashReportType.PLAYER_RUNTIME_FATAL) {
            runCatching {
                context.startActivity(
                    CrashReportActivity.createIntent(
                        context = context,
                        reportId = report?.reportId,
                        clearTask = true,
                    ),
                )
            }.onFailure { error ->
                Log.e(TAG, "Unable to open crash report activity", error)
            }
        }

        // 终止当前进程
        exitProcess(1)
    }

    private companion object {
        const val TAG = "GlobalExceptionHandler"
    }
}
