package com.ai.assistance.operit.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

internal data class UIHierarchyResolvedService(
    val packageName: String,
    val serviceName: String,
    val enabled: Boolean,
    val exported: Boolean,
    val permissionDeclared: Boolean,
)

internal interface UIHierarchyServiceConnection<ProviderT> {
    fun onConnected(provider: ProviderT?)

    fun onDisconnected()
}

internal interface UIHierarchyBindingRuntime<ProviderT> {
    val ownerDescription: String

    fun isProviderInstalled(): Boolean

    fun resolveService(): UIHierarchyResolvedService?

    fun bind(connection: UIHierarchyServiceConnection<ProviderT>): Boolean

    fun unbind(connection: UIHierarchyServiceConnection<ProviderT>)
}

internal enum class UIHierarchyBindingLogLevel {
    DEBUG,
    WARNING,
    ERROR,
}

internal fun interface UIHierarchyBindingLogSink {
    fun log(
        level: UIHierarchyBindingLogLevel,
        message: String,
        error: Throwable?,
    )
}

internal data class UIHierarchyBindingSnapshot<ProviderT>(
    val state: UIHierarchyBindingState,
    val isBound: Boolean,
    val provider: ProviderT?,
)

/**
 * 单个 applicationContext owner 的服务绑定状态机。
 *
 * Android 的 PackageManager、bindService 和 Binder 转换都由 runtime 适配层拥有；本类只维护
 * 单一登记、状态转换、等待连接和精确解绑合同，因此 JVM 测试可以覆盖所有失败路径而不依赖
 * Android stub 的实现细节。
 */
internal class UIHierarchyBindingSession<ProviderT>(
    private val runtime: UIHierarchyBindingRuntime<ProviderT>,
    private val timeoutMs: Long,
    private val logSink: UIHierarchyBindingLogSink,
    private val onSnapshotChanged: (UIHierarchyBindingSnapshot<ProviderT>) -> Unit,
) {
    init {
        require(timeoutMs > 0L) { "timeoutMs must be positive" }
    }

    private val bindingMutex = Mutex()
    private val stateLock = Any()

    @Volatile
    private var currentState = UIHierarchyBindingState.UNBOUND

    @Volatile
    private var currentIsBound = false

    @Volatile
    private var provider: ProviderT? = null

    @Volatile
    private var registrationActive = false

    @Volatile
    private var connectionContinuation: ((Boolean) -> Unit)? = null

    private val serviceConnection =
        object : UIHierarchyServiceConnection<ProviderT> {
            override fun onConnected(provider: ProviderT?) {
                synchronized(stateLock) {
                    if (!registrationActive) {
                        log(UIHierarchyBindingLogLevel.WARNING, "忽略未登记绑定的服务连接回调")
                        return
                    }
                    if (provider == null) {
                        publishLocked(
                            state = UIHierarchyBindingState.CONNECTION_FAILED,
                            isBound = false,
                            provider = null,
                        )
                        log(UIHierarchyBindingLogLevel.ERROR, "服务连接回调未提供有效 Provider")
                        resumeConnectionLocked(false)
                        return
                    }
                    publishLocked(
                        state = UIHierarchyBindingState.CONNECTED,
                        isBound = true,
                        provider = provider,
                    )
                    log(UIHierarchyBindingLogLevel.DEBUG, "无障碍服务提供者已连接")
                    resumeConnectionLocked(true)
                }
            }

            override fun onDisconnected() {
                synchronized(stateLock) {
                    if (!registrationActive) {
                        log(UIHierarchyBindingLogLevel.DEBUG, "忽略未登记绑定的服务断开回调")
                        return
                    }
                    publishLocked(
                        state = UIHierarchyBindingState.CONNECTION_FAILED,
                        isBound = false,
                        provider = null,
                    )
                    log(UIHierarchyBindingLogLevel.WARNING, "无障碍服务提供者已断开")
                    resumeConnectionLocked(false)
                }
            }
        }

    suspend fun bind(): Boolean =
        bindingMutex.withLock {
            log(
                UIHierarchyBindingLogLevel.DEBUG,
                "bindToService invoked. Thread=${Thread.currentThread().name}, " +
                    "contextOwner=${runtime.ownerDescription}",
            )

            if (currentIsBound && provider != null) {
                updateState(
                    UIHierarchyBindingState.CONNECTED,
                    "绑定已存在，复用当前 applicationContext owner",
                )
                return@withLock true
            }

            // onServiceDisconnected 后 Android 仍要求对原登记执行一次 unbind。重新绑定前先释放
            // 原登记，防止同一个 owner 对同一连接对象累计多次登记。
            if (registrationActive) {
                releaseRegistration(finalState = null)
            }

            if (!runtime.isProviderInstalled()) {
                updateState(
                    UIHierarchyBindingState.NOT_INSTALLED,
                    "无法绑定：提供者应用未安装",
                )
                return@withLock false
            }

            val resolvedService = runtime.resolveService()
            if (resolvedService == null) {
                updateState(
                    UIHierarchyBindingState.SERVICE_UNRESOLVED,
                    "无法解析无障碍服务",
                )
                return@withLock false
            }
            if (!resolvedService.enabled) {
                updateState(
                    UIHierarchyBindingState.DISABLED,
                    "无障碍服务已禁用: " +
                        "${resolvedService.packageName}/${resolvedService.serviceName}",
                )
                return@withLock false
            }
            if (!resolvedService.exported) {
                updateState(
                    UIHierarchyBindingState.NOT_EXPORTED_OR_PERMISSION_MISMATCH,
                    "无障碍服务未导出，当前应用无法绑定",
                )
                return@withLock false
            }

            log(
                UIHierarchyBindingLogLevel.DEBUG,
                "服务解析成功: ${resolvedService.packageName}/${resolvedService.serviceName}, " +
                    "exported=${resolvedService.exported}, " +
                    "permissionDeclared=${resolvedService.permissionDeclared}",
            )

            val result =
                try {
                    withTimeoutOrNull(timeoutMs) {
                        suspendCancellableCoroutine<Boolean> { continuation ->
                            val callback: (Boolean) -> Unit = { success ->
                                if (continuation.isActive) {
                                    continuation.resume(success)
                                }
                            }
                            synchronized(stateLock) {
                                connectionContinuation = callback
                                registrationActive = true
                                publishLocked(
                                    state = UIHierarchyBindingState.WAITING_FOR_CONNECTION,
                                    isBound = false,
                                    provider = null,
                                )
                            }
                            try {
                                val bound = runtime.bind(serviceConnection)
                                log(
                                    UIHierarchyBindingLogLevel.DEBUG,
                                    "applicationContext bindService 结果: $bound",
                                )
                                if (!bound) {
                                    synchronized(stateLock) {
                                        registrationActive = false
                                        publishLocked(
                                            state = UIHierarchyBindingState.BIND_RETURNED_FALSE,
                                            isBound = false,
                                            provider = null,
                                        )
                                        connectionContinuation = null
                                        if (continuation.isActive) {
                                            continuation.resume(false)
                                        }
                                    }
                                }
                            } catch (securityException: SecurityException) {
                                synchronized(stateLock) {
                                    registrationActive = false
                                    publishLocked(
                                        state =
                                            UIHierarchyBindingState
                                                .NOT_EXPORTED_OR_PERMISSION_MISMATCH,
                                        isBound = false,
                                        provider = null,
                                    )
                                    connectionContinuation = null
                                    if (continuation.isActive) {
                                        continuation.resume(false)
                                    }
                                }
                                log(
                                    UIHierarchyBindingLogLevel.WARNING,
                                    "绑定服务被系统拒绝: " +
                                        securityException.javaClass.simpleName,
                                )
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (error: Exception) {
                                synchronized(stateLock) {
                                    registrationActive = false
                                    publishLocked(
                                        state = UIHierarchyBindingState.CONNECTION_FAILED,
                                        isBound = false,
                                        provider = null,
                                    )
                                    connectionContinuation = null
                                    if (continuation.isActive) {
                                        continuation.resume(false)
                                    }
                                }
                                log(
                                    UIHierarchyBindingLogLevel.ERROR,
                                    "绑定服务调用失败: ${error.javaClass.simpleName}",
                                    error,
                                )
                            }
                            continuation.invokeOnCancellation {
                                synchronized(stateLock) {
                                    if (connectionContinuation === callback) {
                                        connectionContinuation = null
                                    }
                                }
                            }
                        }
                    }
                } catch (cancellation: CancellationException) {
                    releaseRegistration(finalState = null)
                    throw cancellation
                }

            if (result == null) {
                synchronized(stateLock) {
                    connectionContinuation = null
                    publishLocked(
                        state = UIHierarchyBindingState.TIMEOUT,
                        isBound = false,
                        provider = null,
                    )
                }
                log(
                    UIHierarchyBindingLogLevel.WARNING,
                    "绑定服务超时 (${timeoutMs}ms)",
                )
                releaseRegistration(finalState = null)
                return@withLock false
            }

            if (result) {
                updateState(
                    UIHierarchyBindingState.CONNECTED,
                    "bindToService 成功完成",
                )
            } else {
                releaseRegistration(finalState = null)
                log(
                    UIHierarchyBindingLogLevel.WARNING,
                    "bindToService 未建立连接，state=$currentState",
                )
            }
            result && currentIsBound && provider != null
        }

    fun unbind() {
        releaseRegistration(finalState = UIHierarchyBindingState.UNBOUND)
    }

    fun currentProvider(): ProviderT? = provider

    fun snapshot(): UIHierarchyBindingSnapshot<ProviderT> =
        synchronized(stateLock) {
            UIHierarchyBindingSnapshot(
                state = currentState,
                isBound = currentIsBound,
                provider = provider,
            )
        }

    private fun resumeConnectionLocked(success: Boolean) {
        val continuation = connectionContinuation
        connectionContinuation = null
        continuation?.invoke(success)
    }

    private fun updateState(
        state: UIHierarchyBindingState,
        message: String,
    ) {
        synchronized(stateLock) {
            publishLocked(
                state = state,
                isBound =
                    state == UIHierarchyBindingState.CONNECTED &&
                        provider != null,
                provider = provider,
            )
        }
        val level =
            when (state) {
                UIHierarchyBindingState.CONNECTED,
                UIHierarchyBindingState.WAITING_FOR_CONNECTION,
                UIHierarchyBindingState.UNBOUND,
                -> UIHierarchyBindingLogLevel.DEBUG
                else -> UIHierarchyBindingLogLevel.WARNING
            }
        log(level, message)
    }

    private fun releaseRegistration(finalState: UIHierarchyBindingState?) {
        val shouldUnbind: Boolean
        synchronized(stateLock) {
            shouldUnbind = registrationActive
            registrationActive = false
            connectionContinuation = null
            publishLocked(
                state = finalState ?: currentState,
                isBound = false,
                provider = null,
            )
        }
        if (shouldUnbind) {
            try {
                runtime.unbind(serviceConnection)
            } catch (error: Exception) {
                log(
                    UIHierarchyBindingLogLevel.ERROR,
                    "解绑服务失败: ${error.javaClass.simpleName}",
                    error,
                )
            }
        }
        if (finalState == UIHierarchyBindingState.UNBOUND) {
            log(UIHierarchyBindingLogLevel.DEBUG, "服务已解绑")
        }
    }

    private fun publishLocked(
        state: UIHierarchyBindingState,
        isBound: Boolean,
        provider: ProviderT?,
    ) {
        this.provider = provider
        currentIsBound = isBound
        currentState = state
        onSnapshotChanged(
            UIHierarchyBindingSnapshot(
                state = state,
                isBound = isBound,
                provider = provider,
            )
        )
    }

    private fun log(
        level: UIHierarchyBindingLogLevel,
        message: String,
        error: Throwable? = null,
    ) {
        logSink.log(level, message, error)
    }
}
