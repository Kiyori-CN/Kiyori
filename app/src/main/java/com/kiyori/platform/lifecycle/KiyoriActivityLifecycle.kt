package com.kiyori.platform.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * 接收 Android lifecycle facts 后执行产品集成。
 *
 * platform owner 只报告已经完成的状态迁移，避免重新持有 Operit plugin、AI、Player 或窗口
 * 副作用；否则 lifecycle facts 会再次与上层运行时实现绑定。
 */
internal interface KiyoriActivityLifecycleObserver {
    fun onActivityCreated(activity: Activity, activeActivityCount: Int)

    fun onActivityStarted(activity: Activity, enteredForeground: Boolean)

    fun onActivityResumed(activity: Activity)

    fun onActivityPaused(activity: Activity)

    fun onActivityStopped(activity: Activity, enteredBackground: Boolean)

    fun onActivityDestroyed(activity: Activity, activeActivityCount: Int)
}

/**
 * 当前进程的纯 Activity lifecycle facts。
 *
 * 该类不注册 Android callback，也不执行窗口、服务或 plugin 操作，便于独立验证状态边界。
 * 运行时只由 [KiyoriActivityLifecycle] 创建一个实例。
 */
internal class KiyoriActivityLifecycleFacts {
    private var currentActivity: WeakReference<Activity>? = null
    private var activityCount = 0
    private var startedActivityCount = 0
    @Volatile
    private var isAppInForeground = false

    fun getCurrentActivity(): Activity? = currentActivity?.get()

    fun isAppInForeground(): Boolean = isAppInForeground

    fun onActivityCreated(): Int {
        activityCount += 1
        return activityCount
    }

    fun onActivityStarted(): Boolean {
        startedActivityCount += 1
        if (!isAppInForeground && startedActivityCount > 0) {
            isAppInForeground = true
            return true
        }
        return false
    }

    fun onActivityResumed(activity: Activity) {
        currentActivity = WeakReference(activity)
    }

    fun onActivityPaused(activity: Activity) {
        if (currentActivity?.get() == activity) {
            currentActivity?.clear()
        }
    }

    fun onActivityStopped(): Boolean {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        if (isAppInForeground && startedActivityCount == 0) {
            isAppInForeground = false
            return true
        }
        return false
    }

    fun onActivityDestroyed(activity: Activity): Int {
        if (currentActivity?.get() == activity) {
            currentActivity?.clear()
        }
        activityCount -= 1
        return activityCount
    }
}

/**
 * 当前 Android 进程唯一的 Activity lifecycle fact owner 与 callback 注册 owner。
 *
 * 状态先更新，再把迁移结果交给 observer，保持旧实现中 plugin、服务与资源清理观察到的时序。
 */
object KiyoriActivityLifecycle : Application.ActivityLifecycleCallbacks {
    private val facts = KiyoriActivityLifecycleFacts()
    private lateinit var observer: KiyoriActivityLifecycleObserver

    internal fun initialize(
        application: Application,
        observer: KiyoriActivityLifecycleObserver,
    ) {
        this.observer = observer
        application.registerActivityLifecycleCallbacks(this)
    }

    fun getCurrentActivity(): Activity? = facts.getCurrentActivity()

    fun isAppInForeground(): Boolean = facts.isAppInForeground()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        observer.onActivityCreated(activity, facts.onActivityCreated())
    }

    override fun onActivityStarted(activity: Activity) {
        observer.onActivityStarted(activity, facts.onActivityStarted())
    }

    override fun onActivityResumed(activity: Activity) {
        facts.onActivityResumed(activity)
        observer.onActivityResumed(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        facts.onActivityPaused(activity)
        observer.onActivityPaused(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        observer.onActivityStopped(activity, facts.onActivityStopped())
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // 旧 owner 不使用该回调；保留接口实现和调用面。
    }

    override fun onActivityDestroyed(activity: Activity) {
        observer.onActivityDestroyed(
            activity,
            facts.onActivityDestroyed(activity),
        )
    }
}
