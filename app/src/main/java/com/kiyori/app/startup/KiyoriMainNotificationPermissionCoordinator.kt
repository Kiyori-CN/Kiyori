package com.kiyori.app.startup

import android.widget.Toast
import androidx.activity.ComponentActivity
import com.kiyori.integration.operit.permission.OperitNotificationPermissionResources
import com.kiyori.platform.logging.KiyoriLogger
import com.kiyori.platform.permission.KiyoriNotificationPermissionAction
import com.kiyori.platform.permission.KiyoriNotificationPermissionCapability

/**
 * MainActivity 启动阶段通知权限日志与 Toast 投影的唯一 owner。
 *
 * coordinator 继续作为 Activity 的直接字段构造，间接保证 platform launcher 与旧实现一样
 * 在 onCreate 前注册；系统 grant/rationale 与请求执行只由 platform capability 负责。
 */
internal class KiyoriMainNotificationPermissionCoordinator(
    private val activity: ComponentActivity,
) {
    private companion object {
        const val TAG = "MainActivity"
    }

    private val notificationPermissionCapability =
        KiyoriNotificationPermissionCapability(
            activity = activity,
            onPermissionResult = { isGranted ->
                if (isGranted) {
                    KiyoriLogger.d(TAG, "通知权限已授予")
                } else {
                    KiyoriLogger.d(TAG, "通知权限被拒绝")
                    Toast.makeText(
                        activity,
                        activity.getString(
                            OperitNotificationPermissionResources.notificationPermissionDenied,
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )

    fun checkAndRequest() {
        notificationPermissionCapability.checkAndRequest { action ->
            when (action) {
                KiyoriNotificationPermissionAction.NOT_REQUIRED -> {
                    KiyoriLogger.d(TAG, "Android 版本 < 13，无需请求通知权限")
                }

                KiyoriNotificationPermissionAction.ALREADY_GRANTED -> {
                    KiyoriLogger.d(TAG, "通知权限已授予")
                }

                KiyoriNotificationPermissionAction.SHOW_RATIONALE_AND_REQUEST -> {
                    KiyoriLogger.d(TAG, "需要显示通知权限说明")
                    Toast.makeText(
                        activity,
                        activity.getString(
                            OperitNotificationPermissionResources.notificationPermissionRationale,
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                }

                KiyoriNotificationPermissionAction.REQUEST -> {
                    KiyoriLogger.d(TAG, "请求通知权限")
                }
            }
        }
    }
}
