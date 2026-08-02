package com.kiyori.platform.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

internal enum class KiyoriNotificationPermissionAction {
    NOT_REQUIRED,
    ALREADY_GRANTED,
    SHOW_RATIONALE_AND_REQUEST,
    REQUEST,
}

internal fun resolveKiyoriNotificationPermissionAction(
    sdkInt: Int,
    isGranted: Boolean,
    shouldShowRationale: Boolean,
): KiyoriNotificationPermissionAction =
    when {
        sdkInt < Build.VERSION_CODES.TIRAMISU ->
            KiyoriNotificationPermissionAction.NOT_REQUIRED
        isGranted ->
            KiyoriNotificationPermissionAction.ALREADY_GRANTED
        shouldShowRationale ->
            KiyoriNotificationPermissionAction.SHOW_RATIONALE_AND_REQUEST
        else ->
            KiyoriNotificationPermissionAction.REQUEST
    }

/**
 * MainActivity 启动阶段通知权限的唯一 Android capability。
 *
 * capability 只读取系统 grant/rationale 并拥有 RequestPermission launcher，不缓存权限
 * 事实，也不持有日志、Toast、资源或 Operit 业务。调用 action callback 返回后才发起请求，
 * 从而保持“先显示 rationale，再打开系统权限框”的既有顺序。
 */
internal class KiyoriNotificationPermissionCapability(
    private val activity: ComponentActivity,
    onPermissionResult: (Boolean) -> Unit,
) {
    private val permissionLauncher =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            onPermissionResult(isGranted)
        }

    fun checkAndRequest(
        onPermissionAction: (KiyoriNotificationPermissionAction) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onPermissionAction(KiyoriNotificationPermissionAction.NOT_REQUIRED)
            return
        }
        checkAndRequestAtLeastTiramisu(onPermissionAction)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkAndRequestAtLeastTiramisu(
        onPermissionAction: (KiyoriNotificationPermissionAction) -> Unit,
    ) {
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val isGranted =
            ContextCompat.checkSelfPermission(
                activity,
                permission,
            ) == PackageManager.PERMISSION_GRANTED
        val shouldShowRationale =
            activity.shouldShowRequestPermissionRationale(permission)
        val action =
            resolveKiyoriNotificationPermissionAction(
                sdkInt = Build.VERSION.SDK_INT,
                isGranted = isGranted,
                shouldShowRationale = shouldShowRationale,
            )

        onPermissionAction(action)
        when (action) {
            KiyoriNotificationPermissionAction.NOT_REQUIRED -> {
                error("Android 13+ notification permission path resolved as not required")
            }

            KiyoriNotificationPermissionAction.ALREADY_GRANTED -> Unit

            KiyoriNotificationPermissionAction.SHOW_RATIONALE_AND_REQUEST,
            KiyoriNotificationPermissionAction.REQUEST,
            -> permissionLauncher.launch(permission)
        }
    }
}
