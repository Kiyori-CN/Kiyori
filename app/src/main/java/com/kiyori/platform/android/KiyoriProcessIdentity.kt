package com.kiyori.platform.android

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File

/** Resolves the current process name without depending on the legacy Operit crash package. */
internal object KiyoriProcessIdentity {
    fun currentProcessName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName().also { name ->
                require(name.isNotBlank()) { "Application process name is blank" }
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
                .use { input ->
                    input.readBytes()
                        .takeWhile { byte -> byte.toInt() != 0 }
                        .toByteArray()
                        .toString(Charsets.UTF_8)
                        .trim()
                }
        require(procName.isNotBlank()) { "Unable to resolve application process name" }
        return procName
    }
}
