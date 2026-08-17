package com.ai.assistance.operit.core.ffmpeg.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class FFmpegRuntimeClient private constructor(context: Context) {
    companion object {
        @Volatile
        private var instance: FFmpegRuntimeClient? = null

        fun getInstance(context: Context): FFmpegRuntimeClient =
            instance
                ?: synchronized(this) {
                    instance
                        ?: FFmpegRuntimeClient(context.applicationContext).also {
                            instance = it
                        }
                }

        private const val TAG = "FFmpegRuntimeClient"
        private const val CONNECTION_TIMEOUT_MILLIS = 10_000L
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connectionLock = Any()
    private val pendingRequests = ConcurrentHashMap<String, PendingFFmpegRuntimeRequest>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var nextRuntimeGeneration = 0L
    private var bindingAttempt: FFmpegRuntimeBindingAttempt? = null
    private var connectedRuntime: ConnectedFFmpegRuntime? = null

    private val callback =
        object : IFFmpegRuntimeCallback.Stub() {
            override fun onRequestStarted(
                runtimeGeneration: Long,
                eventSequence: Long,
                requestId: String?,
                sessionId: Long,
                processId: Int,
            ) {
                if (requestId == null) {
                    return
                }
                postEvent(runtimeGeneration, eventSequence) {
                    pendingRequests[requestId]
                        ?.takeIf { pending -> pending.runtimeGeneration == runtimeGeneration }
                        ?.recordStarted(sessionId, processId)
                }
            }

            override fun onRequestCompleted(
                runtimeGeneration: Long,
                eventSequence: Long,
                result: FFmpegRuntimeResult?,
            ) {
                if (result == null) {
                    return
                }
                postEvent(runtimeGeneration, eventSequence) {
                    val pending =
                        pendingRequests[result.requestId]
                            ?.takeIf { request -> request.runtimeGeneration == runtimeGeneration }
                    if (pending == null) {
                        discardTerminalLog(result.outputLogPath)
                        return@postEvent
                    }
                    if (pending.request.operationWireValue != result.operationWireValue) {
                        discardTerminalLog(result.outputLogPath)
                        pending.terminal.completeExceptionally(
                            FFmpegRuntimeException(
                                "FFmpeg 运行时返回了不匹配的 operation",
                            ),
                        )
                        return@postEvent
                    }
                    pending.terminal.complete(FFmpegRuntimeTerminal.Completed(result))
                }
            }

            override fun onRequestFailed(
                runtimeGeneration: Long,
                eventSequence: Long,
                failure: FFmpegRuntimeFailure?,
            ) {
                if (failure == null) {
                    return
                }
                postEvent(runtimeGeneration, eventSequence) {
                    val pending =
                        pendingRequests[failure.requestId]
                            ?.takeIf { request -> request.runtimeGeneration == runtimeGeneration }
                    if (pending == null) {
                        discardTerminalLog(failure.outputLogPath)
                        return@postEvent
                    }
                    if (pending.request.operationWireValue != failure.operationWireValue) {
                        discardTerminalLog(failure.outputLogPath)
                        pending.terminal.completeExceptionally(
                            FFmpegRuntimeException(
                                "FFmpeg 运行时返回了不匹配的 failure operation",
                            ),
                        )
                        return@postEvent
                    }
                    pending.terminal.complete(FFmpegRuntimeTerminal.Failed(failure))
                }
            }
        }

    suspend fun execute(command: String): FFmpegRuntimeResponse =
        submit(
            FFmpegRuntimeRequest(
                requestId = newRequestId(),
                operationWireValue = FFmpegRuntimeOperation.EXECUTE_COMMAND.wireValue,
                command = command,
            ),
        )

    suspend fun executeArguments(arguments: List<String>): FFmpegRuntimeResponse =
        submit(
            FFmpegRuntimeRequest(
                requestId = newRequestId(),
                operationWireValue = FFmpegRuntimeOperation.EXECUTE_ARGUMENTS.wireValue,
                arguments = arguments.toList(),
            ),
        )

    suspend fun probeMedia(inputPath: String): FFmpegRuntimeResponse =
        submit(
            FFmpegRuntimeRequest(
                requestId = newRequestId(),
                operationWireValue = FFmpegRuntimeOperation.PROBE_MEDIA.wireValue,
                inputPath = inputPath,
            ),
        )

    suspend fun queryRuntimeInfo(): FFmpegRuntimeResponse =
        submit(
            FFmpegRuntimeRequest(
                requestId = newRequestId(),
                operationWireValue = FFmpegRuntimeOperation.RUNTIME_INFO.wireValue,
            ),
        )

    fun executeBlocking(command: String): FFmpegRuntimeResponse {
        requireBackgroundThread()
        return runBlocking { execute(command) }
    }

    fun executeArgumentsBlocking(arguments: List<String>): FFmpegRuntimeResponse {
        requireBackgroundThread()
        return runBlocking { executeArguments(arguments) }
    }

    fun probeMediaBlocking(inputPath: String): FFmpegRuntimeResponse {
        requireBackgroundThread()
        return runBlocking { probeMedia(inputPath) }
    }

    fun queryRuntimeInfoBlocking(): FFmpegRuntimeResponse {
        requireBackgroundThread()
        return runBlocking { queryRuntimeInfo() }
    }

    private suspend fun submit(request: FFmpegRuntimeRequest): FFmpegRuntimeResponse =
        withContext(Dispatchers.IO) {
            val runtime = awaitConnectedRuntime()
            val pending =
                PendingFFmpegRuntimeRequest(
                    runtimeGeneration = runtime.runtimeGeneration,
                    request = request,
                )
            check(pendingRequests.putIfAbsent(request.requestId, pending) == null) {
                "Duplicate local FFmpeg runtime request ID"
            }
            try {
                try {
                    check(runtime.remote.submit(runtime.runtimeGeneration, request)) {
                        "FFmpeg 运行时拒绝接收请求"
                    }
                } catch (error: DeadObjectException) {
                    pending.terminal.complete(FFmpegRuntimeTerminal.ProcessDied)
                    handleRuntimeDisconnected(runtime.runtimeGeneration)
                } catch (error: RemoteException) {
                    pending.terminal.complete(FFmpegRuntimeTerminal.ProcessDied)
                    handleRuntimeDisconnected(runtime.runtimeGeneration)
                }

                val terminal =
                    try {
                        pending.terminal.await()
                    } catch (cancelled: CancellationException) {
                        cancel(runtime, request.requestId)
                        throw cancelled
                    }
                when (terminal) {
                    is FFmpegRuntimeTerminal.Completed -> {
                        val output = readAndDeleteOutput(terminal.result.outputLogPath)
                        FFmpegRuntimeResponse(terminal.result, output)
                    }
                    is FFmpegRuntimeTerminal.Failed -> {
                        val output = readAndDeleteOutput(terminal.failure.outputLogPath)
                        throw FFmpegRuntimeRequestException(terminal.failure, output)
                    }
                    FFmpegRuntimeTerminal.ProcessDied -> {
                        val diagnosticLogPath =
                            ffmpegRuntimeLogFile(
                                appContext.cacheDir,
                                request.requestId,
                            ).absolutePath
                        val partialOutput =
                            boundFfmpegRuntimeDiagnostic(
                                readFfmpegRuntimeOutput(
                                    appContext.cacheDir,
                                    diagnosticLogPath,
                                ),
                            ).orEmpty()
                        pruneFfmpegRuntimeLogs(
                            cacheDir = appContext.cacheDir,
                            protectedLogPaths = setOf(diagnosticLogPath),
                        )
                        throw FFmpegRuntimeProcessDiedException(
                            requestId = request.requestId,
                            partialOutput = partialOutput,
                            diagnosticLogPath = diagnosticLogPath,
                        )
                    }
                }
            } finally {
                pendingRequests.remove(request.requestId, pending)
            }
        }

    private suspend fun awaitConnectedRuntime(): ConnectedFFmpegRuntime {
        val existingOrStale =
            synchronized(connectionLock) {
                connectedRuntime
            }
        if (existingOrStale != null) {
            if (existingOrStale.binder.isBinderAlive) {
                return existingOrStale
            }
            handleRuntimeDisconnected(existingOrStale.runtimeGeneration)
        }

        val attempt =
            synchronized(connectionLock) {
                bindingAttempt
                    ?: FFmpegRuntimeBindingAttempt().also { created ->
                        bindingAttempt = created
                        mainHandler.post { bind(created) }
                    }
            }
        return try {
            withTimeout(CONNECTION_TIMEOUT_MILLIS) {
                attempt.connected.await()
            }
        } catch (timeout: TimeoutCancellationException) {
            val error = FFmpegRuntimeException("连接 FFmpeg 运行时超时", timeout)
            abortBindingAttempt(attempt, error)
            throw error
        }
    }

    private fun bind(attempt: FFmpegRuntimeBindingAttempt) {
        if (
            synchronized(connectionLock) {
                bindingAttempt !== attempt || attempt.invalidated
            }
        ) {
            return
        }
        val bound =
            runCatching {
                appContext.bindService(
                    Intent(appContext, FFmpegRuntimeService::class.java),
                    attempt.connection,
                    Context.BIND_AUTO_CREATE,
                )
            }.onFailure { error ->
                Log.e(TAG, "Unable to bind FFmpeg runtime", error)
            }.getOrDefault(false)
        if (!bound) {
            failBindingAttempt(attempt, FFmpegRuntimeException("无法绑定 FFmpeg 运行时"))
            return
        }
        val shouldUnbind =
            synchronized(connectionLock) {
                attempt.bound = true
                attempt.invalidated ||
                    (
                        bindingAttempt !== attempt &&
                            connectedRuntime?.connection !== attempt.connection
                    )
            }
        if (shouldUnbind) {
            unbind(attempt.connection)
        }
    }

    private fun handleServiceConnected(
        attempt: FFmpegRuntimeBindingAttempt,
        service: IBinder?,
    ) {
        if (service == null) {
            failBindingAttempt(attempt, FFmpegRuntimeException("FFmpeg 运行时返回空 Binder"))
            return
        }
        val remote = IFFmpegRuntime.Stub.asInterface(service)
        val runtimeGeneration =
            synchronized(connectionLock) {
                if (bindingAttempt !== attempt || attempt.invalidated) {
                    return
                }
                ++nextRuntimeGeneration
            }
        val deathRecipient =
            IBinder.DeathRecipient {
                mainHandler.post { handleRuntimeDisconnected(runtimeGeneration) }
            }
        try {
            service.linkToDeath(deathRecipient, 0)
            check(remote.registerCallback(runtimeGeneration, callback)) {
                "FFmpeg 运行时拒绝注册回调"
            }
        } catch (error: Exception) {
            runCatching { service.unlinkToDeath(deathRecipient, 0) }
            failBindingAttempt(
                attempt,
                FFmpegRuntimeException("无法初始化 FFmpeg 运行时连接", error),
            )
            return
        }

        val runtime =
            ConnectedFFmpegRuntime(
                runtimeGeneration = runtimeGeneration,
                remote = remote,
                binder = service,
                connection = attempt.connection,
                deathRecipient = deathRecipient,
                eventCursor = FFmpegRuntimeEventCursor(runtimeGeneration),
            )
        val accepted =
            synchronized(connectionLock) {
                if (bindingAttempt !== attempt || attempt.invalidated) {
                    false
                } else {
                    bindingAttempt = null
                    connectedRuntime = runtime
                    true
                }
            }
        if (!accepted) {
            runCatching { service.unlinkToDeath(deathRecipient, 0) }
            unbind(attempt.connection)
            return
        }
        attempt.connected.complete(runtime)
    }

    private fun failBindingAttempt(
        attempt: FFmpegRuntimeBindingAttempt,
        error: FFmpegRuntimeException,
    ) {
        val shouldUnbind =
            synchronized(connectionLock) {
                if (bindingAttempt !== attempt) {
                    false
                } else {
                    bindingAttempt = null
                    attempt.invalidated = true
                    attempt.bound
                }
            }
        if (shouldUnbind) {
            unbind(attempt.connection)
        }
        attempt.connected.completeExceptionally(error)
    }

    private fun abortBindingAttempt(
        attempt: FFmpegRuntimeBindingAttempt,
        error: FFmpegRuntimeException,
    ) {
        val shouldUnbind =
            synchronized(connectionLock) {
                if (bindingAttempt !== attempt) {
                    false
                } else {
                    bindingAttempt = null
                    attempt.invalidated = true
                    attempt.bound
                }
            }
        // 一个 binding attempt 会被多个并发请求共享。失效时必须完成同一个 deferred，
        // 否则其他等待者只能分别耗尽自己的超时窗口。
        attempt.connected.completeExceptionally(error)
        if (shouldUnbind) {
            mainHandler.post {
                unbind(attempt.connection)
            }
        }
    }

    private fun handleRuntimeDisconnected(runtimeGeneration: Long) {
        val runtime =
            synchronized(connectionLock) {
                connectedRuntime
                    ?.takeIf { current -> current.runtimeGeneration == runtimeGeneration }
                    ?.also { connectedRuntime = null }
            } ?: return
        mainHandler.post {
            runCatching { runtime.binder.unlinkToDeath(runtime.deathRecipient, 0) }
            unbind(runtime.connection)
        }
        pendingRequests.values
            .filter { pending -> pending.runtimeGeneration == runtimeGeneration }
            .forEach { pending ->
                pending.terminal.complete(FFmpegRuntimeTerminal.ProcessDied)
            }
    }

    private fun postEvent(
        runtimeGeneration: Long,
        eventSequence: Long,
        action: () -> Unit,
    ) {
        mainHandler.post {
            val accepted =
                synchronized(connectionLock) {
                    connectedRuntime
                        ?.takeIf { runtime -> runtime.runtimeGeneration == runtimeGeneration }
                        ?.eventCursor
                        ?.accept(runtimeGeneration, eventSequence)
                        ?: false
                }
            if (accepted) {
                action()
            }
        }
    }

    private fun cancel(runtime: ConnectedFFmpegRuntime, requestId: String) {
        runCatching {
            runtime.remote.cancel(runtime.runtimeGeneration, requestId)
        }.onFailure { error ->
            if (error is DeadObjectException || error is RemoteException) {
                handleRuntimeDisconnected(runtime.runtimeGeneration)
            } else {
                Log.e(TAG, "Unable to cancel FFmpeg runtime request $requestId", error)
            }
        }
    }

    private suspend fun readAndDeleteOutput(outputLogPath: String): String =
        withContext(Dispatchers.IO) {
            val outputFile =
                resolveFfmpegRuntimeLogFile(appContext.cacheDir, outputLogPath)
            try {
                if (outputFile.isFile) outputFile.readText(Charsets.UTF_8) else ""
            } finally {
                runCatching { outputFile.delete() }
                    .onFailure { error ->
                        Log.w(TAG, "Unable to delete completed FFmpeg runtime log", error)
                    }
            }
        }

    private fun discardTerminalLog(outputLogPath: String) {
        cleanupScope.launch {
            val outputFile =
                runCatching {
                    resolveFfmpegRuntimeLogFile(appContext.cacheDir, outputLogPath)
                }.onFailure { error ->
                    Log.w(TAG, "Rejected late FFmpeg runtime log path", error)
                }.getOrNull() ?: return@launch
            if (outputFile.exists() && !outputFile.delete()) {
                Log.w(TAG, "Unable to delete late FFmpeg runtime log: ${outputFile.path}")
            }
        }
    }

    private fun unbind(connection: ServiceConnection) {
        runCatching { appContext.unbindService(connection) }
            .onFailure { error ->
                Log.d(TAG, "FFmpeg runtime connection was already unbound", error)
            }
    }

    private fun requireBackgroundThread() {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "FFmpeg 同步工具接口不能在 Android 主线程执行"
        }
    }

    private fun newRequestId(): String = UUID.randomUUID().toString().replace("-", "")

    private inner class FFmpegRuntimeBindingAttempt {
        val connected = CompletableDeferred<ConnectedFFmpegRuntime>()

        @Volatile
        var invalidated = false

        @Volatile
        var bound = false

        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    handleServiceConnected(this@FFmpegRuntimeBindingAttempt, service)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    val runtimeGeneration =
                        synchronized(connectionLock) {
                            connectedRuntime
                                ?.takeIf { runtime -> runtime.connection === this }
                                ?.runtimeGeneration
                        }
                    if (runtimeGeneration != null) {
                        handleRuntimeDisconnected(runtimeGeneration)
                    } else {
                        failBindingAttempt(
                            this@FFmpegRuntimeBindingAttempt,
                            FFmpegRuntimeException("FFmpeg 运行时连接在初始化前断开"),
                        )
                    }
                }

                override fun onBindingDied(name: ComponentName?) {
                    onServiceDisconnected(name)
                }

                override fun onNullBinding(name: ComponentName?) {
                    failBindingAttempt(
                        this@FFmpegRuntimeBindingAttempt,
                        FFmpegRuntimeException("FFmpeg 运行时不支持绑定"),
                    )
                }
            }
    }
}

private data class ConnectedFFmpegRuntime(
    val runtimeGeneration: Long,
    val remote: IFFmpegRuntime,
    val binder: IBinder,
    val connection: ServiceConnection,
    val deathRecipient: IBinder.DeathRecipient,
    val eventCursor: FFmpegRuntimeEventCursor,
)

private class PendingFFmpegRuntimeRequest(
    val runtimeGeneration: Long,
    val request: FFmpegRuntimeRequest,
) {
    val terminal = CompletableDeferred<FFmpegRuntimeTerminal>()

    @Volatile
    var sessionId: Long = 0L
        private set

    @Volatile
    var processId: Int = 0
        private set

    fun recordStarted(sessionId: Long, processId: Int) {
        if (sessionId > 0L && processId > 0) {
            this.sessionId = sessionId
            this.processId = processId
        }
    }
}

private sealed interface FFmpegRuntimeTerminal {
    data class Completed(val result: FFmpegRuntimeResult) : FFmpegRuntimeTerminal

    data class Failed(val failure: FFmpegRuntimeFailure) : FFmpegRuntimeTerminal

    data object ProcessDied : FFmpegRuntimeTerminal
}
