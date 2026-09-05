package com.kiyori.integration.operit.onboarding

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.RootAuthorizer
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.core.tools.system.ShizukuInstaller
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import com.kiyori.platform.logging.KiyoriLogger

internal fun kiyoriRuntimePermissionsForSdk(
    sdkInt: Int,
    selectedPermissionIds: Set<KiyoriPermissionId> = KiyoriPermissionId.entries.toSet(),
): List<String> =
    buildList {
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            if (KiyoriPermissionId.NOTIFICATIONS in selectedPermissionIds) {
                add(POST_NOTIFICATIONS_PERMISSION)
            }
            if (KiyoriPermissionId.MEDIA in selectedPermissionIds) {
                add(READ_MEDIA_AUDIO_PERMISSION)
                add(READ_MEDIA_VIDEO_PERMISSION)
            }
        } else {
            if (KiyoriPermissionId.LEGACY_STORAGE in selectedPermissionIds) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (sdkInt <= Build.VERSION_CODES.P &&
                KiyoriPermissionId.LEGACY_STORAGE in selectedPermissionIds
            ) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            KiyoriPermissionId.MEDIA in selectedPermissionIds
        ) {
            add(READ_MEDIA_VISUAL_USER_SELECTED_PERMISSION)
        }
        if (KiyoriPermissionId.CAMERA in selectedPermissionIds) {
            add(Manifest.permission.CAMERA)
        }
        if (KiyoriPermissionId.MICROPHONE in selectedPermissionIds) {
            add(Manifest.permission.RECORD_AUDIO)
        }
        if (KiyoriPermissionId.LOCATION in selectedPermissionIds) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (sdkInt >= Build.VERSION_CODES.S) {
            if (KiyoriPermissionId.BLUETOOTH in selectedPermissionIds) {
                add(BLUETOOTH_CONNECT_PERMISSION)
                add(BLUETOOTH_SCAN_PERMISSION)
            }
        }
        if (KiyoriPermissionId.PHONE in selectedPermissionIds) {
            add(Manifest.permission.CALL_PHONE)
        }
        if (KiyoriPermissionId.SMS in selectedPermissionIds) {
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.RECEIVE_SMS)
        }
    }.distinct()

internal fun isKiyoriRuntimePermission(
    permissionId: KiyoriPermissionId,
): Boolean =
    when (permissionId) {
        KiyoriPermissionId.NOTIFICATIONS,
        KiyoriPermissionId.MEDIA,
        KiyoriPermissionId.CAMERA,
        KiyoriPermissionId.MICROPHONE,
        KiyoriPermissionId.LOCATION,
        KiyoriPermissionId.BLUETOOTH,
        KiyoriPermissionId.PHONE,
        KiyoriPermissionId.SMS,
        KiyoriPermissionId.LEGACY_STORAGE,
        -> true

        KiyoriPermissionId.ALL_FILES,
        KiyoriPermissionId.OVERLAY,
        KiyoriPermissionId.WRITE_SETTINGS,
        KiyoriPermissionId.USAGE_ACCESS,
        KiyoriPermissionId.INSTALL_PACKAGES,
        KiyoriPermissionId.BATTERY_OPTIMIZATION,
        KiyoriPermissionId.NOTIFICATION_LISTENER,
        KiyoriPermissionId.DEFAULT_ASSISTANT,
        KiyoriPermissionId.ACCESSIBILITY,
        KiyoriPermissionId.SHIZUKU,
        KiyoriPermissionId.ROOT,
        KiyoriPermissionId.SCREEN_CAPTURE,
        KiyoriPermissionId.READ_INSTALLED_APPS,
        KiyoriPermissionId.REMOVE_RESTRICTED_SETTINGS,
        -> false
    }

internal fun readKiyoriPermissionSnapshot(
    context: Context,
): KiyoriPermissionSnapshot {
    val sdkInt = Build.VERSION.SDK_INT
    val statuses =
        buildMap {
            put(
                KiyoriPermissionId.NOTIFICATIONS,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionGroupStatus(
                        context = context,
                        permissions = notificationPermissionsApi33(),
                    )
                } else {
                    KiyoriPermissionStatus.NOT_APPLICABLE
                },
            )
            put(
                KiyoriPermissionId.MEDIA,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionGroupStatus(
                        context = context,
                        permissions = mediaPermissionsApi33(),
                    )
                } else {
                    KiyoriPermissionStatus.NOT_APPLICABLE
                },
            )
            put(
                KiyoriPermissionId.CAMERA,
                permissionGroupStatus(
                    context = context,
                    permissions = listOf(Manifest.permission.CAMERA),
                ),
            )
            put(
                KiyoriPermissionId.MICROPHONE,
                permissionGroupStatus(
                    context = context,
                    permissions = listOf(Manifest.permission.RECORD_AUDIO),
                ),
            )
            put(
                KiyoriPermissionId.LOCATION,
                permissionGroupStatus(
                    context = context,
                    permissions =
                        listOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                ),
            )
            put(
                KiyoriPermissionId.BLUETOOTH,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissionGroupStatus(
                        context = context,
                        permissions = bluetoothPermissionsApi31(),
                    )
                } else {
                    KiyoriPermissionStatus.NOT_APPLICABLE
                },
            )
            put(
                KiyoriPermissionId.PHONE,
                permissionGroupStatus(
                    context = context,
                    permissions = listOf(Manifest.permission.CALL_PHONE),
                ),
            )
            put(
                KiyoriPermissionId.SMS,
                permissionGroupStatus(
                    context = context,
                    permissions =
                        listOf(
                            Manifest.permission.SEND_SMS,
                            Manifest.permission.READ_SMS,
                            Manifest.permission.RECEIVE_SMS,
                        ),
                ),
            )
            put(
                KiyoriPermissionId.LEGACY_STORAGE,
                if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
                    permissionGroupStatus(
                        context = context,
                        permissions =
                            listOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                            ) +
                                if (sdkInt <= Build.VERSION_CODES.P) {
                                    listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } else {
                                    emptyList()
                                },
                    )
                } else {
                    KiyoriPermissionStatus.NOT_APPLICABLE
                },
            )
            put(
                KiyoriPermissionId.READ_INSTALLED_APPS,
                if (sdkInt >= Build.VERSION_CODES.R) {
                    grantedStatus(hasInstalledApplicationsAccess(context))
                } else {
                    KiyoriPermissionStatus.GRANTED
                },
            )
            put(
                KiyoriPermissionId.REMOVE_RESTRICTED_SETTINGS,
                if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                    KiyoriPermissionStatus.REQUIRES_SETUP
                } else {
                    KiyoriPermissionStatus.NOT_APPLICABLE
                },
            )
            put(
                KiyoriPermissionId.ALL_FILES,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    allFilesAccessStatusApi30()
                } else {
                    KiyoriPermissionStatus.NOT_APPLICABLE
                },
            )
            put(
                KiyoriPermissionId.OVERLAY,
                if (sdkInt >= Build.VERSION_CODES.M) {
                    grantedStatus(Settings.canDrawOverlays(context))
                } else {
                    KiyoriPermissionStatus.NOT_APPLICABLE
                },
            )
            put(
                KiyoriPermissionId.WRITE_SETTINGS,
                if (sdkInt >= Build.VERSION_CODES.M) {
                    grantedStatus(Settings.System.canWrite(context))
                } else {
                    KiyoriPermissionStatus.NOT_APPLICABLE
                },
            )
            put(
                KiyoriPermissionId.USAGE_ACCESS,
                grantedStatus(hasUsageStatsAccess(context)),
            )
            put(
                KiyoriPermissionId.INSTALL_PACKAGES,
                if (sdkInt >= Build.VERSION_CODES.O) {
                    grantedStatus(context.packageManager.canRequestPackageInstalls())
                } else {
                    KiyoriPermissionStatus.NOT_APPLICABLE
                },
            )
            put(
                KiyoriPermissionId.BATTERY_OPTIMIZATION,
                if (sdkInt >= Build.VERSION_CODES.M) {
                    val powerManager =
                        context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    grantedStatus(
                        powerManager.isIgnoringBatteryOptimizations(context.packageName),
                    )
                } else {
                    KiyoriPermissionStatus.NOT_APPLICABLE
                },
            )
            put(
                KiyoriPermissionId.NOTIFICATION_LISTENER,
                grantedStatus(hasNotificationListenerAccess(context)),
            )
            put(
                KiyoriPermissionId.DEFAULT_ASSISTANT,
                grantedStatus(isKiyoriDefaultAssistant(context)),
            )
            put(
                KiyoriPermissionId.ACCESSIBILITY,
                when {
                    !UIHierarchyManager.isProviderAppInstalled(context) ->
                        KiyoriPermissionStatus.REQUIRES_SETUP

                    UIHierarchyManager.isUpdateNeeded(context) ->
                        KiyoriPermissionStatus.REQUIRES_SETUP

                    isKiyoriAccessibilityProviderEnabled(context) ->
                        KiyoriPermissionStatus.GRANTED

                    else -> KiyoriPermissionStatus.NOT_GRANTED
                },
            )
            put(
                KiyoriPermissionId.SHIZUKU,
                when {
                    !ShizukuAuthorizer.isShizukuInstalled(context) ->
                        KiyoriPermissionStatus.REQUIRES_SETUP

                    !ShizukuAuthorizer.isShizukuServiceRunning() ->
                        KiyoriPermissionStatus.REQUIRES_SETUP

                    ShizukuAuthorizer.hasShizukuPermission() ->
                        KiyoriPermissionStatus.GRANTED

                    else -> KiyoriPermissionStatus.NOT_GRANTED
                },
            )
            put(
                KiyoriPermissionId.ROOT,
                when {
                    RootAuthorizer.hasRootAccess.value -> KiyoriPermissionStatus.GRANTED
                    RootAuthorizer.isRooted.value -> KiyoriPermissionStatus.NOT_GRANTED
                    else -> KiyoriPermissionStatus.REQUIRES_SETUP
                },
            )
            put(
                KiyoriPermissionId.SCREEN_CAPTURE,
                KiyoriPermissionStatus.ON_DEMAND,
            )
        }
    return KiyoriPermissionSnapshot(statuses)
}

internal fun launchKiyoriPermissionSettings(
    context: Context,
    permissionId: KiyoriPermissionId,
) {
    val packageUri = "package:${context.packageName}".toUri()
    val intent =
        when (permissionId) {
            KiyoriPermissionId.ALL_FILES ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    allFilesAccessSettingsIntentApi30(packageUri)
                } else {
                    error("All-files access settings require Android 11 or newer")
                }

            KiyoriPermissionId.OVERLAY ->
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    packageUri,
                )

            KiyoriPermissionId.WRITE_SETTINGS ->
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    packageUri,
                )

            KiyoriPermissionId.USAGE_ACCESS ->
                Intent(
                    Settings.ACTION_USAGE_ACCESS_SETTINGS,
                    packageUri,
                )

            KiyoriPermissionId.INSTALL_PACKAGES ->
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    packageUri,
                )

            KiyoriPermissionId.BATTERY_OPTIMIZATION ->
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

            KiyoriPermissionId.NOTIFICATION_LISTENER ->
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

            KiyoriPermissionId.DEFAULT_ASSISTANT ->
                Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)

            KiyoriPermissionId.ACCESSIBILITY ->
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

            KiyoriPermissionId.NOTIFICATIONS,
            KiyoriPermissionId.MEDIA,
            KiyoriPermissionId.CAMERA,
            KiyoriPermissionId.MICROPHONE,
            KiyoriPermissionId.LOCATION,
            KiyoriPermissionId.BLUETOOTH,
            KiyoriPermissionId.PHONE,
            KiyoriPermissionId.SMS,
            KiyoriPermissionId.LEGACY_STORAGE,
            KiyoriPermissionId.READ_INSTALLED_APPS,
            KiyoriPermissionId.REMOVE_RESTRICTED_SETTINGS,
            KiyoriPermissionId.SHIZUKU,
            KiyoriPermissionId.ROOT,
            KiyoriPermissionId.SCREEN_CAPTURE,
            -> error("Permission does not have one Android settings intent: $permissionId")
        }
    context.startActivity(intent)
}

internal fun launchKiyoriApplicationPermissionSettings(
    context: Context,
) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri(),
        ),
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun notificationPermissionsApi33(): List<String> =
    listOf(Manifest.permission.POST_NOTIFICATIONS)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun mediaPermissionsApi33(): List<String> =
    buildList {
        add(Manifest.permission.READ_MEDIA_AUDIO)
        add(Manifest.permission.READ_MEDIA_VIDEO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(mediaVisualUserSelectedPermissionApi34())
        }
    }

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun mediaVisualUserSelectedPermissionApi34(): String =
    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED

@RequiresApi(Build.VERSION_CODES.S)
private fun bluetoothPermissionsApi31(): List<String> =
    listOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
    )

@RequiresApi(Build.VERSION_CODES.R)
private fun allFilesAccessStatusApi30(): KiyoriPermissionStatus =
    grantedStatus(Environment.isExternalStorageManager())

@RequiresApi(Build.VERSION_CODES.R)
private fun allFilesAccessSettingsIntentApi30(packageUri: Uri): Intent =
    Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        packageUri,
    )

internal fun performKiyoriAccessibilityAction(
    context: Context,
) {
    if (
        UIHierarchyManager.isProviderAppInstalled(context) &&
        !UIHierarchyManager.isUpdateNeeded(context)
    ) {
        launchKiyoriPermissionSettings(
            context = context,
            permissionId = KiyoriPermissionId.ACCESSIBILITY,
        )
    } else {
        UIHierarchyManager.launchProviderInstall(context)
    }
}

internal fun performKiyoriShizukuAction(
    context: Context,
    onPermissionResult: (Boolean) -> Unit,
) {
    when {
        !ShizukuAuthorizer.isShizukuInstalled(context) -> {
            check(ShizukuInstaller.installBundledShizuku(context)) {
                "Bundled Shizuku installation could not be started"
            }
        }

        !ShizukuAuthorizer.isShizukuServiceRunning() -> {
            val launchIntent =
                checkNotNull(
                    context.packageManager.getLaunchIntentForPackage(
                        SHIZUKU_PACKAGE_NAME,
                    ),
                ) {
                    "Installed Shizuku does not expose a launch activity"
                }
            context.startActivity(launchIntent)
        }

        else -> ShizukuAuthorizer.requestShizukuPermission(onPermissionResult)
    }
}

/**
 * Applies the same execution-state transition for both onboarding and Settings permission entry
 * points. The Shizuku service grant is an Android capability; persisting DEBUGGER here makes the
 * shell route and the permission presentation converge without introducing a second state owner.
 */
internal suspend fun activateKiyoriShizukuExecution() {
    try {
        androidPermissionPreferences.savePreferredPermissionLevel(AndroidPermissionLevel.DEBUGGER)
        AndroidShellExecutor.clearPreferredPermissionLevelCache()
    } catch (error: Exception) {
        KiyoriLogger.e(
            "KiyoriPermissions",
            "Failed to persist Shizuku DEBUGGER execution state",
            error,
        )
        throw error
    }
}

private fun permissionGroupStatus(
    context: Context,
    permissions: List<String>,
): KiyoriPermissionStatus {
    if (permissions.isEmpty()) {
        return KiyoriPermissionStatus.NOT_APPLICABLE
    }
    val grantedCount =
        permissions.count { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission,
            ) == PackageManager.PERMISSION_GRANTED
        }
    return when (grantedCount) {
        0 -> KiyoriPermissionStatus.NOT_GRANTED
        permissions.size -> KiyoriPermissionStatus.GRANTED
        else -> KiyoriPermissionStatus.PARTIAL
    }
}

private fun hasInstalledApplicationsAccess(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.QUERY_ALL_PACKAGES,
    ) == PackageManager.PERMISSION_GRANTED

private fun grantedStatus(granted: Boolean): KiyoriPermissionStatus =
    if (granted) {
        KiyoriPermissionStatus.GRANTED
    } else {
        KiyoriPermissionStatus.NOT_GRANTED
    }

@Suppress("DEPRECATION")
private fun hasUsageStatsAccess(context: Context): Boolean {
    val appOpsManager =
        context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun hasNotificationListenerAccess(context: Context): Boolean {
    val enabledListeners =
        Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
    return enabledListeners
        .split(":")
        .asSequence()
        .mapNotNull(ComponentName::unflattenFromString)
        .any { componentName ->
            componentName.packageName == context.packageName
        }
}

private fun isKiyoriDefaultAssistant(context: Context): Boolean =
    Settings.Secure.getString(
        context.contentResolver,
        "voice_interaction_service",
    )?.let(ComponentName::unflattenFromString)
        ?.packageName == context.packageName

private fun isKiyoriAccessibilityProviderEnabled(context: Context): Boolean {
    val enabledServices =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
    return enabledServices
        .split(":")
        .asSequence()
        .mapNotNull(ComponentName::unflattenFromString)
        .any { componentName ->
            componentName.packageName == ACCESSIBILITY_PROVIDER_PACKAGE_NAME
        }
}

private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
private const val ACCESSIBILITY_PROVIDER_PACKAGE_NAME =
    "com.ai.assistance.operit.provider"

// 该策略函数接收显式 sdkInt 以覆盖多版本单测；稳定权限值避免把宿主 SDK 状态误当成传入版本。
private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
private const val READ_MEDIA_AUDIO_PERMISSION = "android.permission.READ_MEDIA_AUDIO"
private const val READ_MEDIA_VIDEO_PERMISSION = "android.permission.READ_MEDIA_VIDEO"
private const val READ_MEDIA_VISUAL_USER_SELECTED_PERMISSION =
    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
private const val BLUETOOTH_CONNECT_PERMISSION = "android.permission.BLUETOOTH_CONNECT"
private const val BLUETOOTH_SCAN_PERMISSION = "android.permission.BLUETOOTH_SCAN"
