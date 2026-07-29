package com.ai.assistance.operit.util.crash

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File

internal object CrashProcessIdentity {
    fun currentProcessName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName().also {
                require(it.isNotBlank()) { "Application process name is blank" }
            }
        }

        val pid = Process.myPid()
        val activityManager =
            context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.runningAppProcesses
            ?.firstOrNull { process -> process.pid == pid }
            ?.processName
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        val procName =
            File("/proc/self/cmdline")
                .inputStream()
                .use { input -> input.readBytes() }
                .takeWhile { byte -> byte.toInt() != 0 }
                .toByteArray()
                .toString(Charsets.UTF_8)
                .trim()
        require(procName.isNotBlank()) { "Unable to resolve application process name" }
        return procName
    }

    fun isCrashProcess(context: Context): Boolean =
        currentProcessName(context).endsWith(CRASH_PROCESS_SUFFIX)

    fun reportTypeForCurrentProcess(context: Context): CrashReportType =
        if (currentProcessName(context).endsWith(PLAYER_PROCESS_SUFFIX)) {
            CrashReportType.PLAYER_RUNTIME_FATAL
        } else {
            CrashReportType.APP_FATAL
        }

    private const val CRASH_PROCESS_SUFFIX = ":crash"
    private const val PLAYER_PROCESS_SUFFIX = ":player"
}
