package com.kiyori.platform.android

import android.app.Application

/**
 * 当前 Android 进程唯一的 Application。
 *
 * 平台层暴露 Android 基类而不是 KiyoriApplication，因此 Operit AI 不依赖应用壳实现；
 * 同时类型本身明确这个进程级引用只能拥有 Application 生命周期。
 */
object ApplicationContextAccess {
    lateinit var current: Application
        private set

    internal fun installForProcess(application: Application) {
        current = application
    }

    internal fun isInstalled(): Boolean = ::current.isInitialized
}
