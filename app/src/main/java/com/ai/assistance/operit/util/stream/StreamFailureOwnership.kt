package com.ai.assistance.operit.util.stream

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.CoroutineContext

/**
 * 创建长期存在、但子任务失败彼此隔离的执行 scope。
 *
 * 每个 Deferred 仍由调用方 await 并取得原始异常；SupervisorJob 只阻止一次失败取消长期父
 * scope，从而避免后续独立回合继承已经结束的 Provider 错误。
 */
internal fun newFailureOwnedTaskScope(
    context: CoroutineContext,
): CoroutineScope =
    CoroutineScope(SupervisorJob() + context.minusKey(Job))

/**
 * 在结构化子作用域内运行消息主收集器，并把终止原因交给外层消息任务。
 *
 * 这里的完成结果是消息发送链继续处理错误的唯一通道；收集 Job 自身不能再成为第二个未捕获
 * 异常所有者，否则一次 Provider 失败会同时显示发送错误并触发进程级 APP_FATAL。
 */
internal suspend fun collectForMessageFailureOwner(
    completion: CompletableDeferred<Throwable?>,
    collector: suspend CoroutineScope.() -> Unit,
) {
    try {
        coroutineScope { collector() }
        completion.complete(null)
    } catch (cancellation: CancellationException) {
        completion.complete(cancellation)
        throw cancellation
    } catch (failure: Throwable) {
        completion.complete(failure)
    }
}

/**
 * 在独立执行 scope 中启动任务，但把失败所有权保留给当前调用链。
 *
 * `launch + join` 只等待结束，不会把子任务异常交还调用者；工具 follow-up 因而可能失败后被主发送
 * 链误判为正常完成。Deferred 只在这里 await，既避免异常进入全局处理器，也确保原异常继续沿
 * 消息主 owner 传播。
 */
internal suspend fun <T> awaitFailureOwnedTask(
    deferred: Deferred<T>,
): T {
    return try {
        deferred.await()
    } finally {
        if (!deferred.isCompleted) {
            deferred.cancel()
        }
    }
}

/**
 * 运行不拥有消息失败状态的次级观察器。
 *
 * 自动朗读、Waifu 分段、修订事件和 UI 首包探测都会观察主消息流。Provider 终止时主消息收集器
 * 已负责把原异常交给发送任务；次级观察器只能记录并结束，不能再次把相同异常升级到全局处理器。
 */
internal suspend fun observeSecondaryStream(
    onFailure: (Throwable) -> Unit,
    observer: suspend () -> Unit,
) {
    try {
        observer()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        onFailure(failure)
    }
}
