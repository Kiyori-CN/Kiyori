package com.ai.assistance.operit.util.stream

import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.WeakHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.CoroutineContext

/**
 * 由异常类型提供的受控诊断身份。
 *
 * 这里只允许暴露本地执行关联、稳定诊断码和阶段；Provider 原文、请求内容和异常消息不能进入
 * 次级观察器日志。消息最终 owner 仍持有原始 Throwable，并负责唯一一次完整 cause chain。
 */
internal interface MessageFailureDiagnosticSource {
    val messageFailureExecutionId: String?
    val messageFailureDiagnosticCode: String
    val messageFailurePhase: String
}

internal data class MessageFailureDiagnostics(
    val executionRef: String,
    val diagnosticCode: String,
    val phase: String,
    val failureType: String,
    val causeFingerprint: String,
    val secondaryObserverCount: Int,
    val propagationBoundaryCount: Int,
    val primaryOwnerClaimed: Boolean,
) {
    fun format(): String =
        "executionRef=$executionRef, diagnosticCode=$diagnosticCode, phase=$phase, " +
            "failureType=$failureType, causeFingerprint=$causeFingerprint, " +
            "observerCount=$secondaryObserverCount, " +
            "propagationBoundaryCount=$propagationBoundaryCount, " +
            "primaryOwnerClaimed=$primaryOwnerClaimed"
}

internal data class SecondaryStreamObservation(
    val observerName: String,
    val phase: String,
    val terminalOutcome: String,
    val chunks: Int,
    val visibleChars: Int,
) {
    init {
        require(observerName.isNotBlank()) { "observerName must not be blank" }
        require(phase.isNotBlank()) { "phase must not be blank" }
        require(terminalOutcome.isNotBlank()) { "terminalOutcome must not be blank" }
        require(chunks >= 0) { "chunks must not be negative" }
        require(visibleChars >= 0) { "visibleChars must not be negative" }
    }
}

internal data class SecondaryStreamFailure(
    val observation: SecondaryStreamObservation,
    val diagnostics: MessageFailureDiagnostics,
) {
    fun format(): String =
        "secondary stream terminated: observer=${observation.observerName}, " +
            "phase=${observation.phase}, terminalOutcome=${observation.terminalOutcome}, " +
            "chunks=${observation.chunks}, visibleChars=${observation.visibleChars}, " +
            diagnostics.format()
}

internal data class PrimaryMessageFailureClaim(
    val shouldLogCause: Boolean,
    val diagnostics: MessageFailureDiagnostics,
)

private data class MutableMessageFailureRecord(
    val executionRef: String,
    val diagnosticCode: String,
    val phase: String,
    val failureType: String,
    val causeFingerprint: String,
    var secondaryObserverCount: Int = 0,
    var propagationBoundaryCount: Int = 0,
    var primaryOwnerClaimed: Boolean = false,
) {
    fun snapshot(): MessageFailureDiagnostics =
        MessageFailureDiagnostics(
            executionRef = executionRef,
            diagnosticCode = diagnosticCode,
            phase = phase,
            failureType = failureType,
            causeFingerprint = causeFingerprint,
            secondaryObserverCount = secondaryObserverCount,
            propagationBoundaryCount = propagationBoundaryCount,
            primaryOwnerClaimed = primaryOwnerClaimed,
        )
}

/**
 * 失败记录的逻辑键。
 *
 * 有完整诊断身份的异常按 localExecutionId、diagnosticCode 和 phase 归并；没有本地执行 ID
 * 的通用异常继续按异常对象身份记录，避免把不同任务的普通异常错误地合并。
 */
private sealed interface MessageFailureRecordKey

private data class DiagnosticMessageFailureRecordKey(
    val localExecutionId: String,
    val diagnosticCode: String,
    val phase: String,
) : MessageFailureRecordKey

/**
 * 同一逻辑失败键在一个消息回合内的日志所有权记录。
 *
 * 记录表使用显式的诊断键，而不是 Throwable 对象身份，因此不同包装层和不同异常实例也能
 * 正确归并。带执行 ID 的记录保留最近 256 条，避免 GC 时机改变仍在传播中的 owner，同时不把
 * 错误历史变成无上限缓存；无执行 ID 的普通异常继续由弱对象表管理。同步块只保护短小的计数
 * 更新，不参与流收集或日志 I/O。
 */
private object MessageFailureOwnershipRegistry {
    private const val MAX_DIAGNOSTIC_RECORDS = 256
    private val records =
        object :
            LinkedHashMap<MessageFailureRecordKey, MutableMessageFailureRecord>(
                MAX_DIAGNOSTIC_RECORDS,
                0.75f,
                true,
            ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<MessageFailureRecordKey, MutableMessageFailureRecord>?,
            ): Boolean = size > MAX_DIAGNOSTIC_RECORDS
        }
    private val untrackedRecords = WeakHashMap<Throwable, MutableMessageFailureRecord>()

    @Synchronized
    fun recordPropagation(
        failure: Throwable,
        defaultPhase: String,
    ): MessageFailureDiagnostics {
        val record = recordFor(failure, defaultPhase)
        record.propagationBoundaryCount += 1
        return record.snapshot()
    }

    @Synchronized
    fun recordSecondaryObserver(
        failure: Throwable,
        defaultPhase: String,
    ): MessageFailureDiagnostics {
        val record = recordFor(failure, defaultPhase)
        record.secondaryObserverCount += 1
        return record.snapshot()
    }

    @Synchronized
    fun claimPrimaryOwner(
        failure: Throwable,
        defaultPhase: String,
    ): PrimaryMessageFailureClaim {
        val record = recordFor(failure, defaultPhase)
        val shouldLogCause = !record.primaryOwnerClaimed
        record.primaryOwnerClaimed = true
        return PrimaryMessageFailureClaim(
            shouldLogCause = shouldLogCause,
            diagnostics = record.snapshot(),
        )
    }

    @Synchronized
    fun snapshot(
        failure: Throwable,
        defaultPhase: String,
    ): MessageFailureDiagnostics {
        return recordFor(failure, defaultPhase).snapshot()
    }

    private fun recordFor(
        failure: Throwable,
        defaultPhase: String,
    ): MutableMessageFailureRecord {
        val identity = failureIdentity(failure, defaultPhase)
        val key = identity.key
        if (key != null) {
            return records.getOrPut(key) {
                createRecord(
                    failure = failure,
                    defaultPhase = defaultPhase,
                    anchor = identity.anchor,
                )
            }
        }
        return untrackedRecords.getOrPut(identity.anchor) {
            createRecord(
                failure = failure,
                defaultPhase = defaultPhase,
                anchor = identity.anchor,
            )
        }
    }

    private fun createRecord(
        failure: Throwable,
        defaultPhase: String,
        anchor: Throwable,
    ): MutableMessageFailureRecord {
        val diagnosticSource =
            generateSequence(failure) { it.cause }
                .filterIsInstance<MessageFailureDiagnosticSource>()
                .firstOrNull()
        val executionRef =
            diagnosticSource
                ?.messageFailureExecutionId
                ?.takeIf { it.isNotBlank() }
                ?.takeLast(12)
                ?: "none"
        return MutableMessageFailureRecord(
            executionRef = executionRef,
            diagnosticCode =
                diagnosticSource
                    ?.messageFailureDiagnosticCode
                    ?.takeIf { it.isNotBlank() }
                    ?: "provider_exception",
            phase =
                diagnosticSource
                    ?.messageFailurePhase
                    ?.takeIf { it.isNotBlank() }
                    ?: defaultPhase,
            failureType = failure.javaClass.simpleName,
            causeFingerprint =
                causeFingerprint(
                    generateSequence(failure) { it.cause }
                        .firstOrNull { it is MessageFailureDiagnosticSource }
                        ?: anchor
                ),
        )
    }

    private fun failureIdentity(
        failure: Throwable,
        defaultPhase: String,
    ): FailureIdentity {
        val diagnosticSource =
            generateSequence(failure) { it.cause }
                .filterIsInstance<MessageFailureDiagnosticSource>()
                .firstOrNull()
        val diagnosticCode =
            diagnosticSource
                ?.messageFailureDiagnosticCode
                ?.takeIf { it.isNotBlank() }
                ?: "provider_exception"
        val phase =
            diagnosticSource
                ?.messageFailurePhase
                ?.takeIf { it.isNotBlank() }
                ?: defaultPhase
        val localExecutionId =
            diagnosticSource
                ?.messageFailureExecutionId
                ?.takeIf { it.isNotBlank() }
        val anchor = diagnosticSource as? Throwable ?: failure
        val key =
            localExecutionId?.let {
                DiagnosticMessageFailureRecordKey(
                    localExecutionId = localExecutionId,
                    diagnosticCode = diagnosticCode,
                    phase = phase,
                )
            }
        return FailureIdentity(
            key = key,
            anchor = anchor,
        )
    }

    private data class FailureIdentity(
        val key: MessageFailureRecordKey?,
        val anchor: Throwable,
    )

    private fun causeFingerprint(failure: Throwable): String {
        val canonicalCauseChain =
            generateSequence(failure) { it.cause }
                .take(8)
                .joinToString("|") { cause ->
                    val firstFrame = cause.stackTrace.firstOrNull()
                    buildString {
                        append(cause.javaClass.name)
                        if (firstFrame != null) {
                            append('@')
                            append(firstFrame.className)
                            append('.')
                            append(firstFrame.methodName)
                            append(':')
                            append(firstFrame.lineNumber)
                        }
                    }
                }
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest(canonicalCauseChain.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { byte -> "%02x".format(byte) }
    }
}

internal fun recordPropagatedMessageFailure(
    boundaryName: String,
    phase: String,
    failure: Throwable,
): MessageFailureDiagnostics {
    require(boundaryName.isNotBlank()) { "boundaryName must not be blank" }
    require(phase.isNotBlank()) { "phase must not be blank" }
    return MessageFailureOwnershipRegistry.recordPropagation(
        failure = failure,
        defaultPhase = phase,
    )
}

internal fun claimPrimaryMessageFailureOwner(
    failure: Throwable,
    phase: String,
): PrimaryMessageFailureClaim {
    require(phase.isNotBlank()) { "phase must not be blank" }
    return MessageFailureOwnershipRegistry.claimPrimaryOwner(
        failure = failure,
        defaultPhase = phase,
    )
}

internal fun snapshotMessageFailure(
    failure: Throwable,
    phase: String,
): MessageFailureDiagnostics {
    require(phase.isNotBlank()) { "phase must not be blank" }
    return MessageFailureOwnershipRegistry.snapshot(
        failure = failure,
        defaultPhase = phase,
    )
}

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
    observation: () -> SecondaryStreamObservation,
    onFailure: (SecondaryStreamFailure) -> Unit,
    observer: suspend () -> Unit,
) {
    try {
        observer()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        val finalObservation = observation()
        val diagnostics =
            MessageFailureOwnershipRegistry.recordSecondaryObserver(
                failure = failure,
                defaultPhase = finalObservation.phase,
            )
        onFailure(
            SecondaryStreamFailure(
                observation = finalObservation,
                diagnostics = diagnostics,
            )
        )
    }
}
