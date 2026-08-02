package com.kiyori.platform.android

import android.content.Context

/**
 * 当前 Android 进程唯一的 application context。
 *
 * 这里只持有 Context 能力，不持有具体 Application 类型；否则 Operit AI 会再次依赖应用壳实现，
 * M-03 的包迁移也会重新变成全局替换。
 */
object ApplicationContextAccess {
    lateinit var current: Context
        private set

    internal fun installForProcess(context: Context) {
        current = context.applicationContext
    }
}
