package com.kiyori.app.startup

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.kiyori.platform.logging.KiyoriLogger

internal fun shouldRestoreRuntimeTaskVisibility(
    sdkInt: Int,
    isRuntimeForegroundServiceRunning: Boolean,
): Boolean =
    sdkInt >= Build.VERSION_CODES.LOLLIPOP &&
        !isRuntimeForegroundServiceRunning

/**
 * MainActivity 最近任务可见性恢复的唯一执行边界。
 *
 * 该对象不保存 Activity 或任务状态；它只在既有运行条件满足时，把当前应用任务重新标记为
 * 可出现在最近任务列表中。
 */
internal object KiyoriMainTaskVisibilityCoordinator {
    private const val TAG = "MainActivity"

    fun restoreIfNeeded(activity: Activity) {
        if (
            !shouldRestoreRuntimeTaskVisibility(
                sdkInt = Build.VERSION.SDK_INT,
                isRuntimeForegroundServiceRunning = AIForegroundService.isRunning.get(),
            )
        ) {
            return
        }

        try {
            val activityManager =
                activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.appTasks?.forEach { task ->
                try {
                    task.setExcludeFromRecents(false)
                } catch (error: Exception) {
                    KiyoriLogger.e(TAG, "恢复最近任务可见性失败", error)
                }
            }
        } catch (error: Exception) {
            KiyoriLogger.e(TAG, "恢复运行时任务视图可见性失败", error)
        }
    }
}
