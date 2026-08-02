package com.ai.assistance.operit.core.application

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.kiyori.platform.lifecycle.KiyoriActivityLifecycle

/**
 * 旧 Operit lifecycle FQCN 的兼容 facade。
 *
 * JavaScript bridge 与既有 Operit consumer 仍绑定这个对象及完整
 * [Application.ActivityLifecycleCallbacks] ABI；真实 lifecycle facts 和 Android callback 注册
 * 由 [KiyoriActivityLifecycle] 唯一持有，Operit 副作用由
 * [OperitActivityLifecycleIntegration] 唯一执行。
 */
object ActivityLifecycleManager : Application.ActivityLifecycleCallbacks {
    /**
     * 初始化 Operit integration，并把唯一 platform callback owner 注册到 Application。
     */
    fun initialize(application: Application) {
        OperitActivityLifecycleIntegration.initialize(application)
        KiyoriActivityLifecycle.initialize(
            application = application,
            observer = OperitActivityLifecycleIntegration,
        )
    }

    /**
     * 保留旧调用面；当前 Activity 事实只从 platform owner 读取。
     */
    fun getCurrentActivity(): Activity? = KiyoriActivityLifecycle.getCurrentActivity()

    fun checkAndApplyKeepScreenOn(enable: Boolean) {
        OperitActivityLifecycleIntegration.checkAndApplyKeepScreenOn(enable)
    }

    fun forceKeepScreenOn(enable: Boolean) {
        OperitActivityLifecycleIntegration.forceKeepScreenOn(enable)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        KiyoriActivityLifecycle.onActivityCreated(activity, savedInstanceState)
    }

    override fun onActivityStarted(activity: Activity) {
        KiyoriActivityLifecycle.onActivityStarted(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        KiyoriActivityLifecycle.onActivityResumed(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        KiyoriActivityLifecycle.onActivityPaused(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        KiyoriActivityLifecycle.onActivityStopped(activity)
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        KiyoriActivityLifecycle.onActivitySaveInstanceState(activity, outState)
    }

    override fun onActivityDestroyed(activity: Activity) {
        KiyoriActivityLifecycle.onActivityDestroyed(activity)
    }
}
