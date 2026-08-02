package com.ai.assistance.operit.core.application

import android.app.Activity
import android.app.Application
import android.view.WindowManager
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.ai.assistance.operit.core.tools.agent.ShowerController
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.integrations.http.ExternalChatHttpAutoStarter
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleEvent
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookParams
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookPluginRegistry
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.crash.PlayerCrashCoordinator
import com.kiyori.platform.lifecycle.KiyoriActivityLifecycle
import com.kiyori.platform.lifecycle.KiyoriActivityLifecycleObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Operit runtime 对 Android Activity lifecycle 的唯一副作用适配。
 *
 * Android callback 注册、当前 Activity 与前后台计数由 platform owner 持有；这里仅保留偏好、
 * plugin、AI、Player、VirtualDisplay、Shower 与窗口操作，防止形成第二 lifecycle fact owner。
 */
internal object OperitActivityLifecycleIntegration : KiyoriActivityLifecycleObserver {
    private const val TAG = "ActivityLifecycleManager"
    private lateinit var apiPreferences: ApiPreferences
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var keepScreenOnPreferenceRequestCount = 0
    private var keepScreenOnForcedRequestCount = 0

    @Volatile
    private var lastMicEnsureAtMs: Long = 0L

    fun initialize(application: Application) {
        apiPreferences = ApiPreferences.getInstance(application.applicationContext)
    }

    fun checkAndApplyKeepScreenOn(enable: Boolean) {
        applyKeepScreenOnRequest(enable = enable, respectUserPreference = true)
    }

    fun forceKeepScreenOn(enable: Boolean) {
        applyKeepScreenOnRequest(enable = enable, respectUserPreference = false)
    }

    private fun applyKeepScreenOnRequest(enable: Boolean, respectUserPreference: Boolean) {
        scope.launch {
            try {
                if (enable && respectUserPreference && !apiPreferences.keepScreenOnFlow.first()) {
                    return@launch
                }

                if (respectUserPreference) {
                    if (enable) {
                        keepScreenOnPreferenceRequestCount += 1
                    } else if (keepScreenOnPreferenceRequestCount > 0) {
                        keepScreenOnPreferenceRequestCount -= 1
                    }
                } else {
                    if (enable) {
                        keepScreenOnForcedRequestCount += 1
                    } else if (keepScreenOnForcedRequestCount > 0) {
                        keepScreenOnForcedRequestCount -= 1
                    }
                }

                val activity = KiyoriActivityLifecycle.getCurrentActivity()
                if (activity == null) {
                    AppLogger.w(TAG, "Cannot apply screen on flag: current activity is null.")
                    return@launch
                }

                // Window operations must be done on the UI thread.
                activity.runOnUiThread {
                    val window = activity.window
                    if (keepScreenOnPreferenceRequestCount + keepScreenOnForcedRequestCount > 0) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        AppLogger.d(TAG, "FLAG_KEEP_SCREEN_ON added.")
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        AppLogger.d(TAG, "FLAG_KEEP_SCREEN_ON cleared.")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to apply screen on flag", e)
            }
        }
    }

    override fun onActivityCreated(activity: Activity, activeActivityCount: Int) {
        AppLogger.d(
            TAG,
            "Activity created: ${activity.javaClass.simpleName}, count=$activeActivityCount",
        )
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.ACTIVITY_CREATE,
            params =
                AppLifecycleHookParams(
                    context = activity.applicationContext,
                    extras =
                        mapOf(
                            "activityClassName" to activity.javaClass.name,
                        ),
                ),
        )
    }

    override fun onActivityStarted(activity: Activity, enteredForeground: Boolean) {
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.ACTIVITY_START,
            params =
                AppLifecycleHookParams(
                    context = activity.applicationContext,
                    extras =
                        mapOf(
                            "activityClassName" to activity.javaClass.name,
                        ),
                ),
        )
        if (enteredForeground) {
            ExternalChatHttpAutoStarter.ensureRunningIfEnabled(
                context = activity.applicationContext,
                reason = "application_foreground",
            )
            AppLifecycleHookPluginRegistry.dispatchAsync(
                event = AppLifecycleEvent.APPLICATION_FOREGROUND,
                params =
                    AppLifecycleHookParams(
                        context = activity.applicationContext,
                    ),
            )
        }
    }

    override fun onActivityResumed(activity: Activity) {
        PlayerCrashCoordinator.onActivityResumed(activity)

        try {
            val now = System.currentTimeMillis()
            if (now - lastMicEnsureAtMs >= 2500L) {
                lastMicEnsureAtMs = now
                AIForegroundService.ensureMicrophoneForeground(activity.applicationContext)
            }
        } catch (_: Exception) {
        }
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.ACTIVITY_RESUME,
            params =
                AppLifecycleHookParams(
                    context = activity.applicationContext,
                    extras =
                        mapOf(
                            "activityClassName" to activity.javaClass.name,
                        ),
                ),
        )
    }

    override fun onActivityPaused(activity: Activity) {
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.ACTIVITY_PAUSE,
            params =
                AppLifecycleHookParams(
                    context = activity.applicationContext,
                    extras =
                        mapOf(
                            "activityClassName" to activity.javaClass.name,
                        ),
                ),
        )
    }

    override fun onActivityStopped(activity: Activity, enteredBackground: Boolean) {
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.ACTIVITY_STOP,
            params =
                AppLifecycleHookParams(
                    context = activity.applicationContext,
                    extras =
                        mapOf(
                            "activityClassName" to activity.javaClass.name,
                        ),
                ),
        )
        if (enteredBackground) {
            AppLifecycleHookPluginRegistry.dispatchAsync(
                event = AppLifecycleEvent.APPLICATION_BACKGROUND,
                params =
                    AppLifecycleHookParams(
                        context = activity.applicationContext,
                    ),
            )
        }
    }

    override fun onActivityDestroyed(activity: Activity, activeActivityCount: Int) {
        AppLogger.d(
            TAG,
            "Activity destroyed: ${activity.javaClass.simpleName}, count=$activeActivityCount",
        )
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.ACTIVITY_DESTROY,
            params =
                AppLifecycleHookParams(
                    context = activity.applicationContext,
                    extras =
                        mapOf(
                            "activityClassName" to activity.javaClass.name,
                        ),
                ),
        )

        // 保持旧清理边界：最后一个 Activity 销毁后关闭虚拟屏幕和 Shower 连接。
        if (activeActivityCount <= 0) {
            AppLogger.d(TAG, "最后一个 Activity 被销毁，清理虚拟屏幕资源")
            try {
                VirtualDisplayOverlay.hideAll()
                AppLogger.d(TAG, "已关闭 VirtualDisplayOverlay")
            } catch (e: Exception) {
                AppLogger.e(TAG, "清理 VirtualDisplayOverlay 失败", e)
            }
            try {
                ShowerController.shutdown()
                AppLogger.d(TAG, "已关闭 ShowerController")
            } catch (e: Exception) {
                AppLogger.e(TAG, "清理 ShowerController 失败", e)
            }
        }
    }
}
