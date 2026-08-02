package com.kiyori.app.startup

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.kiyori.platform.logging.KiyoriLogger

internal data class KiyoriDisplayModeCandidate(
    val modeId: Int,
    val refreshRate: Float,
)

internal data class KiyoriDisplayModeSelection(
    val modeId: Int,
    val refreshRate: Float,
)

internal fun selectHighestRefreshRateMode(
    modes: Iterable<KiyoriDisplayModeCandidate>,
): KiyoriDisplayModeSelection {
    var selection =
        KiyoriDisplayModeSelection(
            modeId = 0,
            refreshRate = 60f,
        )
    for (mode in modes) {
        if (mode.refreshRate > selection.refreshRate) {
            selection =
                KiyoriDisplayModeSelection(
                    modeId = mode.modeId,
                    refreshRate = mode.refreshRate,
                )
        }
    }
    return selection
}

internal fun selectHighestRefreshRate(
    refreshRates: Iterable<Float>,
): Float {
    var selectedRefreshRate = 60f
    for (refreshRate in refreshRates) {
        if (refreshRate > selectedRefreshRate) {
            selectedRefreshRate = refreshRate
        }
    }
    return selectedRefreshRate
}

/**
 * MainActivity 窗口性能与刷新率配置的唯一执行边界。
 *
 * 该对象不保存状态；每次调用只操作传入 Activity 的当前 Window 和 Display。
 */
internal object KiyoriMainDisplayCoordinator {
    private const val TAG = "MainActivity"

    fun configure(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                activity.window.setSustainedPerformanceMode(true)
                KiyoriLogger.d(TAG, "已成功请求持续高性能模式。")
            } catch (error: Exception) {
                KiyoriLogger.w(TAG, "请求持续高性能模式失败。", error)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val selection = currentDisplayModeSelection(activity)
            if (selection != null) {
                KiyoriLogger.d(
                    TAG,
                    "Selected display mode with refresh rate: ${selection.refreshRate} Hz",
                )
                if (selection.modeId > 0) {
                    activity.window.attributes.preferredDisplayModeId = selection.modeId
                    KiyoriLogger.d(
                        TAG,
                        "设置窗口首选显示模式ID: ${selection.modeId}",
                    )
                }
            }
        } else {
            val refreshRate = currentDeviceRefreshRate(activity)
            KiyoriLogger.d(TAG, "Selected refresh rate: $refreshRate Hz")
            if (refreshRate > 60f) {
                activity.window.attributes.preferredRefreshRate = refreshRate
                KiyoriLogger.d(TAG, "设置窗口首选刷新率: $refreshRate Hz")
            }
        }

        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun currentDisplayModeSelection(
        activity: Activity,
    ): KiyoriDisplayModeSelection? {
        val currentDisplay = activity.display
        if (currentDisplay == null) {
            return null
        }
        return selectHighestRefreshRateMode(
            currentDisplay.supportedModes.map { mode ->
                KiyoriDisplayModeCandidate(
                    modeId = mode.modeId,
                    refreshRate = mode.refreshRate,
                )
            }
        )
    }

    private fun currentDeviceRefreshRate(activity: Activity): Float {
        val windowManager =
            activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val currentDisplay =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }

        if (currentDisplay == null) {
            return 60f
        }

        return try {
            @Suppress("DEPRECATION")
            selectHighestRefreshRate(
                currentDisplay.supportedModes.map { mode -> mode.refreshRate }
            )
        } catch (error: Exception) {
            KiyoriLogger.e(TAG, "Error getting refresh rate", error)
            60f
        }
    }
}
