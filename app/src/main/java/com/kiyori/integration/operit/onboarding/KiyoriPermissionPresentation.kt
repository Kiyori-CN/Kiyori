package com.kiyori.integration.operit.onboarding

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PermPhoneMsg
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.kiyori.design.theme.KiyoriSemanticTone

internal enum class KiyoriPermissionGroupId {
    DAILY,
    FILES_AND_BACKGROUND,
    DEVICE_INTEGRATION,
    SENSITIVE_DATA,
    ADVANCED_CAPABILITIES,
}

internal data class KiyoriPermissionGroupSpec(
    val id: KiyoriPermissionGroupId,
    val title: String,
    val description: String,
    val permissionIds: List<KiyoriPermissionId>,
    val initiallyExpanded: Boolean = false,
)

// 展示顺序服务使用场景；持久化仍使用稳定的 permission ID，不依赖枚举序号。
internal val kiyoriPermissionGroups = listOf(
    KiyoriPermissionGroupSpec(
        KiyoriPermissionGroupId.DAILY, "日常使用", "通知、影音与输入 · 只开启你会用到的功能",
        listOf(KiyoriPermissionId.NOTIFICATIONS, KiyoriPermissionId.MEDIA,
            KiyoriPermissionId.LEGACY_STORAGE, KiyoriPermissionId.CAMERA,
            KiyoriPermissionId.MICROPHONE, KiyoriPermissionId.LOCATION,
            KiyoriPermissionId.BLUETOOTH),
        initiallyExpanded = true,
    ),
    KiyoriPermissionGroupSpec(
        KiyoriPermissionGroupId.FILES_AND_BACKGROUND, "文件与后台", "跨目录管理、安装与长任务 · 由系统单独确认",
        listOf(KiyoriPermissionId.ALL_FILES, KiyoriPermissionId.INSTALL_PACKAGES,
            KiyoriPermissionId.BATTERY_OPTIMIZATION),
    ),
    KiyoriPermissionGroupSpec(
        KiyoriPermissionGroupId.DEVICE_INTEGRATION, "设备协作", "悬浮助手与设备工具 · 普通浏览无需开启",
        listOf(KiyoriPermissionId.OVERLAY, KiyoriPermissionId.DEFAULT_ASSISTANT,
            KiyoriPermissionId.WRITE_SETTINGS, KiyoriPermissionId.USAGE_ACCESS,
            KiyoriPermissionId.READ_INSTALLED_APPS),
    ),
    KiyoriPermissionGroupSpec(
        KiyoriPermissionGroupId.SENSITIVE_DATA, "通信与通知内容", "涉及他人信息、验证码或通信费用 · 请逐项确认用途",
        listOf(KiyoriPermissionId.PHONE, KiyoriPermissionId.SMS,
            KiyoriPermissionId.NOTIFICATION_LISTENER),
    ),
    KiyoriPermissionGroupSpec(
        KiyoriPermissionGroupId.ADVANCED_CAPABILITIES, "高级自动化", "可操作其他应用或受保护数据 · 仅在理解影响后启用",
        listOf(KiyoriPermissionId.REMOVE_RESTRICTED_SETTINGS,
            KiyoriPermissionId.ACCESSIBILITY, KiyoriPermissionId.SHIZUKU,
            KiyoriPermissionId.ROOT, KiyoriPermissionId.SCREEN_CAPTURE),
    ),
)

internal enum class KiyoriPermissionActionKind {
    REQUEST_RUNTIME,
    OPEN_APPLICATION_SETTINGS,
    OPEN_RESTRICTED_SETTINGS,
    OPEN_SYSTEM_SETTINGS,
    CONFIGURE_ACCESSIBILITY,
    CONFIGURE_SHIZUKU,
    REQUEST_ROOT,
    NONE,
}

internal data class KiyoriPermissionSummary(
    val readyCount: Int,
    val actionRequiredCount: Int,
    val onDemandCount: Int,
    val totalCount: Int,
    val notApplicableCount: Int,
)

internal fun summarizeKiyoriPermissions(
    snapshot: KiyoriPermissionSnapshot,
): KiyoriPermissionSummary =
    KiyoriPermissionSummary(
        readyCount =
            snapshot.statuses.values.count { status ->
                status == KiyoriPermissionStatus.GRANTED
            },
        actionRequiredCount = snapshot.actionableIncomplete.size,
        onDemandCount =
            snapshot.statuses.values.count { status ->
                status == KiyoriPermissionStatus.ON_DEMAND
            },
        totalCount = snapshot.totalCount,
        notApplicableCount = snapshot.statuses.values.count { it == KiyoriPermissionStatus.NOT_APPLICABLE },
    )

internal fun resolveKiyoriPermissionAction(
    permissionId: KiyoriPermissionId,
    status: KiyoriPermissionStatus,
): KiyoriPermissionActionKind {
    // Android 没有跨厂商可靠的受限设置查询接口。提供帮助入口，不声称用户尚未授权。
    if (permissionId == KiyoriPermissionId.REMOVE_RESTRICTED_SETTINGS &&
        status != KiyoriPermissionStatus.NOT_APPLICABLE
    ) return KiyoriPermissionActionKind.OPEN_RESTRICTED_SETTINGS
    if (
        status == KiyoriPermissionStatus.NOT_APPLICABLE ||
            status == KiyoriPermissionStatus.ON_DEMAND
    ) {
        return KiyoriPermissionActionKind.NONE
    }
    if (isKiyoriRuntimePermission(permissionId)) {
        return if (status == KiyoriPermissionStatus.GRANTED) {
            KiyoriPermissionActionKind.OPEN_APPLICATION_SETTINGS
        } else {
            KiyoriPermissionActionKind.REQUEST_RUNTIME
        }
    }
    return when (permissionId) {
        KiyoriPermissionId.READ_INSTALLED_APPS ->
            if (status == KiyoriPermissionStatus.GRANTED) {
                KiyoriPermissionActionKind.NONE
            } else {
                KiyoriPermissionActionKind.OPEN_APPLICATION_SETTINGS
            }

        KiyoriPermissionId.ACCESSIBILITY ->
            if (status == KiyoriPermissionStatus.GRANTED) {
                KiyoriPermissionActionKind.OPEN_SYSTEM_SETTINGS
            } else {
                KiyoriPermissionActionKind.CONFIGURE_ACCESSIBILITY
            }

        KiyoriPermissionId.SHIZUKU ->
            if (status == KiyoriPermissionStatus.GRANTED) {
                KiyoriPermissionActionKind.NONE
            } else {
                KiyoriPermissionActionKind.CONFIGURE_SHIZUKU
            }

        KiyoriPermissionId.ROOT ->
            if (status == KiyoriPermissionStatus.GRANTED) {
                KiyoriPermissionActionKind.NONE
            } else {
                KiyoriPermissionActionKind.REQUEST_ROOT
            }

        KiyoriPermissionId.REMOVE_RESTRICTED_SETTINGS ->
            KiyoriPermissionActionKind.OPEN_RESTRICTED_SETTINGS

        KiyoriPermissionId.SCREEN_CAPTURE -> KiyoriPermissionActionKind.NONE
        else -> KiyoriPermissionActionKind.OPEN_SYSTEM_SETTINGS
    }
}

internal data class KiyoriPermissionMetadata(
    val icon: ImageVector,
    val tone: KiyoriSemanticTone,
    val titleResource: Int,
    val descriptionResource: Int,
) {
    fun title(context: Context): String = context.getString(titleResource)

    fun description(context: Context): String = context.getString(descriptionResource)
}

internal fun kiyoriPermissionMetadata(
    permissionId: KiyoriPermissionId,
): KiyoriPermissionMetadata =
    when (permissionId) {
        KiyoriPermissionId.NOTIFICATIONS ->
            KiyoriPermissionMetadata(
                Icons.Default.Notifications,
                KiyoriSemanticTone.BLUE,
                R.string.kiyori_onboarding_permission_notifications_title,
                R.string.kiyori_onboarding_permission_notifications_desc,
            )

        KiyoriPermissionId.MEDIA ->
            KiyoriPermissionMetadata(
                Icons.Default.PhotoLibrary,
                KiyoriSemanticTone.PURPLE,
                R.string.kiyori_onboarding_permission_media_title,
                R.string.kiyori_onboarding_permission_media_desc,
            )

        KiyoriPermissionId.CAMERA ->
            KiyoriPermissionMetadata(
                Icons.Default.CameraAlt,
                KiyoriSemanticTone.ORANGE,
                R.string.kiyori_onboarding_permission_camera_title,
                R.string.kiyori_onboarding_permission_camera_desc,
            )

        KiyoriPermissionId.MICROPHONE ->
            KiyoriPermissionMetadata(
                Icons.Default.Mic,
                KiyoriSemanticTone.BLUE,
                R.string.kiyori_onboarding_permission_microphone_title,
                R.string.kiyori_onboarding_permission_microphone_desc,
            )

        KiyoriPermissionId.LOCATION ->
            KiyoriPermissionMetadata(
                Icons.Default.LocationOn,
                KiyoriSemanticTone.CYAN,
                R.string.kiyori_onboarding_permission_location_title,
                R.string.kiyori_onboarding_permission_location_desc,
            )

        KiyoriPermissionId.BLUETOOTH ->
            KiyoriPermissionMetadata(
                Icons.Default.Bluetooth,
                KiyoriSemanticTone.BLUE,
                R.string.kiyori_onboarding_permission_bluetooth_title,
                R.string.kiyori_onboarding_permission_bluetooth_desc,
            )

        KiyoriPermissionId.PHONE ->
            KiyoriPermissionMetadata(
                Icons.Default.Phone,
                KiyoriSemanticTone.GREEN,
                R.string.kiyori_onboarding_permission_phone_title,
                R.string.kiyori_onboarding_permission_phone_desc,
            )

        KiyoriPermissionId.SMS ->
            KiyoriPermissionMetadata(
                Icons.Default.PermPhoneMsg,
                KiyoriSemanticTone.GREEN,
                R.string.kiyori_onboarding_permission_sms_title,
                R.string.kiyori_onboarding_permission_sms_desc,
            )

        KiyoriPermissionId.LEGACY_STORAGE ->
            KiyoriPermissionMetadata(
                Icons.Default.Folder,
                KiyoriSemanticTone.ORANGE,
                R.string.kiyori_onboarding_permission_legacy_storage_title,
                R.string.kiyori_onboarding_permission_legacy_storage_desc,
            )

        KiyoriPermissionId.READ_INSTALLED_APPS ->
            KiyoriPermissionMetadata(
                Icons.Default.Apps,
                KiyoriSemanticTone.BLUE,
                R.string.kiyori_onboarding_permission_installed_apps_title,
                R.string.kiyori_onboarding_permission_installed_apps_desc,
            )

        KiyoriPermissionId.REMOVE_RESTRICTED_SETTINGS ->
            KiyoriPermissionMetadata(
                Icons.Default.LockOpen,
                KiyoriSemanticTone.ORANGE,
                R.string.kiyori_onboarding_permission_restricted_settings_title,
                R.string.kiyori_onboarding_permission_restricted_settings_desc,
            )

        KiyoriPermissionId.ALL_FILES ->
            KiyoriPermissionMetadata(
                Icons.Default.FolderSpecial,
                KiyoriSemanticTone.ORANGE,
                R.string.kiyori_onboarding_permission_all_files_title,
                R.string.kiyori_onboarding_permission_all_files_desc,
            )

        KiyoriPermissionId.OVERLAY ->
            KiyoriPermissionMetadata(
                Icons.Default.Layers,
                KiyoriSemanticTone.PURPLE,
                R.string.kiyori_onboarding_permission_overlay_title,
                R.string.kiyori_onboarding_permission_overlay_desc,
            )

        KiyoriPermissionId.WRITE_SETTINGS ->
            KiyoriPermissionMetadata(
                Icons.Default.SettingsApplications,
                KiyoriSemanticTone.RED,
                R.string.kiyori_onboarding_permission_write_settings_title,
                R.string.kiyori_onboarding_permission_write_settings_desc,
            )

        KiyoriPermissionId.USAGE_ACCESS ->
            KiyoriPermissionMetadata(
                Icons.Default.Visibility,
                KiyoriSemanticTone.CYAN,
                R.string.kiyori_onboarding_permission_usage_title,
                R.string.kiyori_onboarding_permission_usage_desc,
            )

        KiyoriPermissionId.INSTALL_PACKAGES ->
            KiyoriPermissionMetadata(
                Icons.Default.InstallMobile,
                KiyoriSemanticTone.ORANGE,
                R.string.kiyori_onboarding_permission_install_title,
                R.string.kiyori_onboarding_permission_install_desc,
            )

        KiyoriPermissionId.BATTERY_OPTIMIZATION ->
            KiyoriPermissionMetadata(
                Icons.Default.BatteryChargingFull,
                KiyoriSemanticTone.GREEN,
                R.string.kiyori_onboarding_permission_battery_title,
                R.string.kiyori_onboarding_permission_battery_desc,
            )

        KiyoriPermissionId.NOTIFICATION_LISTENER ->
            KiyoriPermissionMetadata(
                Icons.Default.NotificationsActive,
                KiyoriSemanticTone.RED,
                R.string.kiyori_onboarding_permission_notification_listener_title,
                R.string.kiyori_onboarding_permission_notification_listener_desc,
            )

        KiyoriPermissionId.DEFAULT_ASSISTANT ->
            KiyoriPermissionMetadata(
                Icons.Default.SmartToy,
                KiyoriSemanticTone.PURPLE,
                R.string.kiyori_onboarding_permission_assistant_title,
                R.string.kiyori_onboarding_permission_assistant_desc,
            )

        KiyoriPermissionId.ACCESSIBILITY ->
            KiyoriPermissionMetadata(
                Icons.Default.AccessibilityNew,
                KiyoriSemanticTone.GREEN,
                R.string.kiyori_onboarding_permission_accessibility_title,
                R.string.kiyori_onboarding_permission_accessibility_desc,
            )

        KiyoriPermissionId.SHIZUKU ->
            KiyoriPermissionMetadata(
                Icons.Default.Android,
                KiyoriSemanticTone.BLUE,
                R.string.kiyori_onboarding_permission_shizuku_title,
                R.string.kiyori_onboarding_permission_shizuku_desc,
            )

        KiyoriPermissionId.ROOT ->
            KiyoriPermissionMetadata(
                Icons.Default.Shield,
                KiyoriSemanticTone.RED,
                R.string.kiyori_onboarding_permission_root_title,
                R.string.kiyori_onboarding_permission_root_desc,
            )

        KiyoriPermissionId.SCREEN_CAPTURE ->
            KiyoriPermissionMetadata(
                Icons.Default.Widgets,
                KiyoriSemanticTone.CYAN,
                R.string.kiyori_onboarding_permission_screen_capture_title,
                R.string.kiyori_onboarding_permission_screen_capture_desc,
            )
    }

internal fun kiyoriPermissionStatusTone(
    status: KiyoriPermissionStatus,
): KiyoriSemanticTone =
    when (status) {
        KiyoriPermissionStatus.GRANTED -> KiyoriSemanticTone.GREEN
        KiyoriPermissionStatus.PARTIAL -> KiyoriSemanticTone.ORANGE
        KiyoriPermissionStatus.NOT_GRANTED -> KiyoriSemanticTone.BLUE
        KiyoriPermissionStatus.REQUIRES_SETUP -> KiyoriSemanticTone.PURPLE
        KiyoriPermissionStatus.NOT_APPLICABLE -> KiyoriSemanticTone.BLUE
        KiyoriPermissionStatus.ON_DEMAND -> KiyoriSemanticTone.CYAN
    }

@Composable
internal fun kiyoriPermissionStatusLabel(
    status: KiyoriPermissionStatus,
): String =
    when (status) {
        KiyoriPermissionStatus.GRANTED ->
            stringResource(R.string.kiyori_onboarding_permission_status_granted)

        KiyoriPermissionStatus.PARTIAL ->
            stringResource(R.string.kiyori_onboarding_permission_status_partial)

        KiyoriPermissionStatus.NOT_GRANTED ->
            stringResource(R.string.kiyori_onboarding_permission_status_not_granted)

        KiyoriPermissionStatus.REQUIRES_SETUP ->
            stringResource(R.string.kiyori_onboarding_permission_status_setup)

        KiyoriPermissionStatus.NOT_APPLICABLE ->
            stringResource(R.string.kiyori_onboarding_permission_status_not_applicable)

        KiyoriPermissionStatus.ON_DEMAND ->
            stringResource(R.string.kiyori_onboarding_permission_status_on_demand)
    }
