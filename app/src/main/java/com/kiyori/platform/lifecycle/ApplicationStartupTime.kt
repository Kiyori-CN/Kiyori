package com.kiyori.platform.lifecycle

/**
 * 当前 Android 进程的 Application 启动时间。
 *
 * 初始值保持为 0L，避免改变 AppLogger 在 Application.onCreate 之前被调用时的既有语义。
 */
object ApplicationStartupTime {
    @Volatile
    var epochMillis: Long = 0L
        private set

    internal fun recordForProcess(epochMillis: Long) {
        this.epochMillis = epochMillis
    }
}
