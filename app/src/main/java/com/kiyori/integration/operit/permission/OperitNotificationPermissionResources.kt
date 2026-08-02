package com.kiyori.integration.operit.permission

import androidx.annotation.StringRes
import com.ai.assistance.operit.R

/**
 * 启动通知权限的唯一 Operit 资源映射。
 *
 * 这里只桥接现有资源 ID，不复制文案或 Android 权限事实；因此 platform capability 不需要
 * 依赖应用 namespace，旧 coordinator 也不再需要 ARCH004 资源例外。
 */
internal object OperitNotificationPermissionResources {
    @StringRes
    val notificationPermissionDenied: Int =
        R.string.notification_permission_denied

    @StringRes
    val notificationPermissionRationale: Int =
        R.string.notification_permission_rationale
}
