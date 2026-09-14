package com.kiyori.platform.network

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** 临时探测资源在跨调度器取消时也必须交还并释放；主运行时不使用此临时作用域。 */
internal suspend fun <T : Any, R> withTemporaryMihomoResource(
    acquire: () -> T,
    release: (T) -> Unit,
    block: suspend (T) -> R,
): R {
    var resource: T? = null
    try {
        // 赋值必须发生在 IO 块内：及时取消可能丢弃 withContext 的返回值。
        withContext(Dispatchers.IO) { resource = acquire() }
        return block(checkNotNull(resource))
    } finally {
        withContext(NonCancellable + Dispatchers.IO) { resource?.let(release) }
    }
}

/**
 * Linux 的 PDEATHSIG 绑定创建子进程的父线程，而非整个 JVM。协程 IO worker 会在空闲时
 * 退出，直接由它启动核心会误发 SIGTERM，截断包括规则 DIRECT 在内的全部活动连接。
 * 创建线程必须等待核心真正退出；停止仍由既有 runtime 持有的 Process 负责。
 */
internal fun startMihomoParentBoundProcess(start: () -> Process): Process {
    val started = CompletableFuture<Process>()
    thread(name = "Kiyori-Mihomo-Parent", isDaemon = true) {
        val process = try {
            start()
        } catch (error: Throwable) {
            started.completeExceptionally(error)
            return@thread
        }
        started.complete(process)
        // 中断等待不是停止核心的授权；提前退出此线程会间接向核心发送 SIGTERM。
        awaitUninterruptibly { process.waitFor() }
    }
    return try {
        // 调用线程被中断时也须先交还唯一 Process 句柄，避免遗留无人管理的核心。
        awaitUninterruptibly { started.get() }
    } catch (error: ExecutionException) {
        throw error.cause ?: error
    }
}

private inline fun <T> awaitUninterruptibly(block: () -> T): T {
    var interrupted = false
    try {
        while (true) {
            try {
                return block()
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
    } finally {
        if (interrupted) Thread.currentThread().interrupt()
    }
}
