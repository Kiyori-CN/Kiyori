package com.ai.assistance.operit.core.ffmpeg.runtime

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.MediaInformation
import com.arthenica.ffmpegkit.MediaInformationSession
import com.arthenica.ffmpegkit.Session
import com.arthenica.ffmpegkit.Statistics
import com.arthenica.ffmpegkit.StatisticsCallback
import com.arthenica.ffmpegkit.StreamInformation
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class FFmpegRuntimeService : Service() {
    private lateinit var executor: ExecutorService
    private lateinit var callbackThread: HandlerThread
    private lateinit var callbackHandler: Handler
    private val registrationLock = Any()
    private val activeRequests = ConcurrentHashMap<String, ActiveFFmpegRuntimeRequest>()
    private val activeNativeRequest = AtomicReference<ActiveFFmpegRuntimeRequest?>(null)

    @Volatile
    private var callbackRegistration: FFmpegRuntimeCallbackRegistration? = null

    private val binder =
        object : IFFmpegRuntime.Stub() {
            override fun registerCallback(
                runtimeGeneration: Long,
                callback: IFFmpegRuntimeCallback?,
            ): Boolean {
                if (runtimeGeneration <= 0L || callback == null) {
                    return false
                }
                return registerRuntimeCallback(runtimeGeneration, callback)
            }

            override fun submit(
                runtimeGeneration: Long,
                request: FFmpegRuntimeRequest?,
            ): Boolean {
                if (request == null) {
                    return false
                }
                return enqueueRequest(runtimeGeneration, request)
            }

            override fun cancel(runtimeGeneration: Long, requestId: String?): Boolean {
                if (
                    requestId == null ||
                        !FFMPEG_RUNTIME_REQUEST_ID_PATTERN.matches(requestId)
                ) {
                    return false
                }
                val active =
                    synchronized(registrationLock) {
                        val registration =
                            callbackRegistration
                                ?.takeIf { current ->
                                    current.runtimeGeneration == runtimeGeneration
                                }
                                ?: return false
                        activeRequests[requestId]
                            ?.takeIf { request -> request.registration === registration }
                            ?: return false
                    }
                active.cancel()
                return true
            }
        }

    override fun onCreate() {
        super.onCreate()
        executor =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "$RUNTIME_THREAD_PREFIX-${RUNTIME_THREAD_COUNTER.incrementAndGet()}")
            }
        FFmpegKitConfig.enableLogCallback(
            LogCallback { log ->
                // FFmpeg's codec/filter threads do not inherit FFmpegKit's TLS session ID.
                // A single top-level native owner makes session 0 attributable without guessing.
                if (log.sessionId == 0L) {
                    activeNativeRequest.get()?.logWriter?.append(log.message)
                }
            },
        )
        callbackThread = HandlerThread(CALLBACK_THREAD_NAME)
        callbackThread.start()
        callbackHandler = Handler(callbackThread.looper)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        releaseCurrentRegistration(
            failureCode = FFmpegRuntimeFailureCode.CALLBACK_DISCONNECTED,
            message = "FFmpeg 运行时客户端已断开",
            emitFailure = true,
        )
        return false
    }

    override fun onDestroy() {
        releaseCurrentRegistration(
            failureCode = FFmpegRuntimeFailureCode.SERVICE_DESTROYED,
            message = "FFmpeg 运行时服务正在终止",
            emitFailure = true,
        )
        activeRequests.values.toList().forEach { active ->
            terminateActiveRequest(
                active = active,
                failureCode = FFmpegRuntimeFailureCode.SERVICE_DESTROYED,
                message = "FFmpeg 运行时服务正在终止",
                emitFailure = false,
            )
        }
        activeRequests.clear()
        executor.shutdownNow()
        FFmpegKitConfig.enableLogCallback(null)
        callbackThread.quitSafely()
        super.onDestroy()
    }

    private fun registerRuntimeCallback(
        runtimeGeneration: Long,
        callback: IFFmpegRuntimeCallback,
    ): Boolean {
        val callbackBinder = callback.asBinder()
        val current =
            synchronized(registrationLock) {
                callbackRegistration
            }
        if (
            current?.runtimeGeneration == runtimeGeneration &&
                current.callbackBinder === callbackBinder
        ) {
            return true
        }

        val deathRecipient =
            IBinder.DeathRecipient {
                callbackHandler.post {
                    handleCallbackDeath(runtimeGeneration, callbackBinder)
                }
            }
        try {
            callbackBinder.linkToDeath(deathRecipient, 0)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to monitor FFmpeg runtime callback owner", error)
            return false
        }
        if (!callbackBinder.isBinderAlive) {
            runCatching { callbackBinder.unlinkToDeath(deathRecipient, 0) }
            return false
        }

        val registration =
            FFmpegRuntimeCallbackRegistration(
                runtimeGeneration = runtimeGeneration,
                callback = callback,
                callbackBinder = callbackBinder,
                deathRecipient = deathRecipient,
            )
        val replaced =
            synchronized(registrationLock) {
                val previous = callbackRegistration
                callbackRegistration = registration
                DetachedFFmpegRuntimeRegistration(
                    registration = previous,
                    activeRequests =
                        previous
                            ?.let { owner ->
                                activeRequests.values
                                    .filter { active -> active.registration === owner }
                            }
                            .orEmpty(),
                )
            }
        replaced.registration?.let(::unlinkCallbackDeath)
        replaced.activeRequests.forEach { active ->
            terminateActiveRequest(
                active = active,
                failureCode = FFmpegRuntimeFailureCode.CALLBACK_REPLACED,
                message = "FFmpeg 运行时客户端所有权已更新，旧请求已终止",
                emitFailure = true,
            )
        }

        if (!callbackBinder.isBinderAlive) {
            handleCallbackDeath(runtimeGeneration, callbackBinder)
            return false
        }
        return synchronized(registrationLock) {
            callbackRegistration === registration
        }
    }

    private fun handleCallbackDeath(
        runtimeGeneration: Long,
        callbackBinder: IBinder,
    ) {
        val detached =
            synchronized(registrationLock) {
                val current =
                    callbackRegistration
                        ?.takeIf { registration ->
                            registration.runtimeGeneration == runtimeGeneration &&
                                registration.callbackBinder === callbackBinder
                        }
                        ?: return
                callbackRegistration = null
                DetachedFFmpegRuntimeRegistration(
                    registration = current,
                    activeRequests =
                        activeRequests.values
                            .filter { active -> active.registration === current },
                )
            }
        unlinkCallbackDeath(detached.registration)
        detached.activeRequests.forEach { active ->
            terminateActiveRequest(
                active = active,
                failureCode = FFmpegRuntimeFailureCode.CALLBACK_DISCONNECTED,
                message = "FFmpeg 运行时客户端进程已终止",
                emitFailure = false,
            )
        }
    }

    private fun releaseCurrentRegistration(
        failureCode: FFmpegRuntimeFailureCode,
        message: String,
        emitFailure: Boolean,
    ) {
        val detached =
            synchronized(registrationLock) {
                val current = callbackRegistration ?: return
                callbackRegistration = null
                DetachedFFmpegRuntimeRegistration(
                    registration = current,
                    activeRequests =
                        activeRequests.values
                            .filter { active -> active.registration === current },
                )
            }
        unlinkCallbackDeath(detached.registration)
        detached.activeRequests.forEach { active ->
            terminateActiveRequest(
                active = active,
                failureCode = failureCode,
                message = message,
                emitFailure = emitFailure,
            )
        }
    }

    private fun unlinkCallbackDeath(registration: FFmpegRuntimeCallbackRegistration?) {
        if (registration == null) {
            return
        }
        runCatching {
            registration.callbackBinder.unlinkToDeath(registration.deathRecipient, 0)
        }
    }

    private fun enqueueRequest(
        runtimeGeneration: Long,
        request: FFmpegRuntimeRequest,
    ): Boolean {
        val registration =
            synchronized(registrationLock) {
                callbackRegistration
                    ?.takeIf { current ->
                        current.runtimeGeneration == runtimeGeneration
                    }
            } ?: return false
        val logFile =
            try {
                createLogFile(request.requestId)
            } catch (error: Exception) {
                emitSetupFailure(
                    registration = registration,
                    request = request,
                    sessionId = 0L,
                    failureCode = FFmpegRuntimeFailureCode.DISPATCH_FAILURE,
                    message = "无法创建 FFmpeg 运行时日志：${error.message ?: error.javaClass.simpleName}",
                    outputLogPath = ffmpegRuntimeLogFile(cacheDir, request.requestId).absolutePath,
                )
                return true
            }
        val active =
            ActiveFFmpegRuntimeRequest(
                registration = registration,
                request = request,
                logWriter = FFmpegRuntimeLogWriter(logFile),
            )
        var duplicate = false
        val accepted =
            synchronized(registrationLock) {
                if (callbackRegistration !== registration) {
                    false
                } else {
                    duplicate = activeRequests.putIfAbsent(request.requestId, active) != null
                    !duplicate
                }
            }
        if (!accepted) {
            active.logWriter.close()
            if (duplicate) {
                emitSetupFailure(
                    registration = registration,
                    request = request,
                    sessionId = 0L,
                    failureCode = FFmpegRuntimeFailureCode.DUPLICATE_REQUEST,
                    message = "FFmpeg 运行时拒绝重复 request ID",
                    outputLogPath = logFile.absolutePath,
                )
                return true
            }
            runCatching { logFile.delete() }
            return false
        }

        try {
            // Binder 只确认请求已登记。FFmpegKit 对象创建、JNI 初始化和真正执行都必须
            // 留在 :ffmpeg worker；否则同步 Binder 调用本身仍可能拖住调用方线程。
            executor.execute {
                dispatchRequest(active)
            }
        } catch (error: Exception) {
            failActiveRequest(
                active = active,
                failureCode = FFmpegRuntimeFailureCode.DISPATCH_FAILURE,
                message = error.message ?: error.javaClass.simpleName,
            )
        }
        return true
    }

    private fun dispatchRequest(active: ActiveFFmpegRuntimeRequest) {
        if (active.terminal.get()) {
            return
        }
        try {
            val request = active.request
            when (request.operation) {
                FFmpegRuntimeOperation.EXECUTE_COMMAND ->
                    executeFfmpegSession(
                        active = active,
                        arguments = FFmpegKitConfig.parseArguments(requireNotNull(request.command)),
                    )
                FFmpegRuntimeOperation.EXECUTE_ARGUMENTS ->
                    executeFfmpegSession(
                        active = active,
                        arguments = request.arguments.toTypedArray(),
                    )
                FFmpegRuntimeOperation.PROBE_MEDIA ->
                    executeMediaInformationSession(
                        active = active,
                        inputPath = requireNotNull(request.inputPath),
                    )
                FFmpegRuntimeOperation.RUNTIME_INFO -> {
                    active.runtimeInformation =
                        FFmpegRuntimeInformation(
                            wrapperVersion = FFmpegKitConfig.getVersion(),
                            ffmpegVersion = FFmpegKitConfig.getFFmpegVersion(),
                            buildDate = FFmpegKitConfig.getBuildDate(),
                        )
                    executeFfmpegSession(
                        active = active,
                        arguments = arrayOf("-codecs"),
                    )
                }
            }
        } catch (error: Exception) {
            failActiveRequest(
                active = active,
                failureCode = FFmpegRuntimeFailureCode.INVALID_REQUEST,
                message = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun executeFfmpegSession(
        active: ActiveFFmpegRuntimeRequest,
        arguments: Array<String>,
    ) {
        val session =
            FFmpegSession.create(
                arguments,
                null,
                LogCallback { log ->
                    active.logWriter.append(log.message)
                },
                StatisticsCallback { statistics ->
                    active.statistics.set(statistics.toRuntimeStatistics())
                },
            )
        if (!startSession(active, session)) {
            return
        }
        executeNativeSession(active, session) {
            FFmpegKitConfig.ffmpegExecute(session)
        }
        completeFfmpegRequest(active, session)
    }

    private fun executeMediaInformationSession(
        active: ActiveFFmpegRuntimeRequest,
        inputPath: String,
    ) {
        val arguments =
            arrayOf(
                "-v",
                "error",
                "-hide_banner",
                "-print_format",
                "json",
                "-show_format",
                "-show_streams",
                "-show_chapters",
                "-i",
                inputPath,
            )
        val session =
            MediaInformationSession.create(
                arguments,
                null,
                LogCallback { log ->
                    active.logWriter.append(log.message)
                },
            )
        if (!startSession(active, session)) {
            return
        }
        executeNativeSession(active, session) {
            FFmpegKitConfig.getMediaInformationExecute(
                session,
                MEDIA_INFORMATION_TIMEOUT_MILLIS,
            )
        }
        completeMediaInformationRequest(active, session)
    }

    private fun executeNativeSession(
        active: ActiveFFmpegRuntimeRequest,
        session: Session,
        execute: () -> Unit,
    ) {
        check(activeNativeRequest.compareAndSet(null, active)) {
            "FFmpeg 运行时存在多个顶层 native owner"
        }
        try {
            active.cancelSessionIfRequested()
            execute()
            awaitNativeCallbacks(active, session.sessionId)
        } finally {
            check(activeNativeRequest.compareAndSet(active, null)) {
                "FFmpeg 运行时 native owner 已被意外替换"
            }
        }
    }

    private fun awaitNativeCallbacks(
        active: ActiveFFmpegRuntimeRequest,
        sessionId: Long,
    ) {
        val deadline = SystemClock.elapsedRealtime() + CALLBACK_DRAIN_TIMEOUT_MILLIS
        while (nativeCallbacksInTransit(sessionId)) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0L) {
                active.logWriter.append(
                    "\n[Kiyori] FFmpeg callback drain timed out; " +
                        "some late diagnostic messages may be unavailable.\n",
                )
                Log.w(TAG, "FFmpeg callback drain timed out for session $sessionId")
                return
            }
            try {
                Thread.sleep(minOf(CALLBACK_DRAIN_POLL_MILLIS, remaining))
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                active.logWriter.append(
                    "\n[Kiyori] FFmpeg callback drain was interrupted by runtime shutdown.\n",
                )
                return
            }
        }
    }

    private fun nativeCallbacksInTransit(sessionId: Long): Boolean =
        FFmpegKitConfig.messagesInTransmit(sessionId) != 0 ||
            FFmpegKitConfig.messagesInTransmit(0L) != 0

    private fun startSession(
        active: ActiveFFmpegRuntimeRequest,
        session: Session,
    ): Boolean =
        synchronized(active.lifecycleLock) {
            active.session.set(session)
            if (active.terminal.get()) {
                session.cancel()
                return@synchronized false
            }
            if (active.cancelRequested.get()) {
                session.cancel()
            }
            // started 与 terminal 必须在同一请求生命周期锁下入队。否则 callback owner
            // 替换可能先发送失败终态，再由仍在启动中的 worker 发送迟到的 started。
            emit(active.registration) { currentCallback, generation, sequence ->
                currentCallback.onRequestStarted(
                    generation,
                    sequence,
                    active.request.requestId,
                    session.sessionId,
                    Process.myPid(),
                )
            }
            true
        }

    private fun completeFfmpegRequest(
        active: ActiveFFmpegRuntimeRequest,
        session: FFmpegSession,
    ) {
        completeRequest(
            active = active,
            session = session,
            mediaInformation = null,
        )
    }

    private fun completeMediaInformationRequest(
        active: ActiveFFmpegRuntimeRequest,
        session: MediaInformationSession,
    ) {
        completeRequest(
            active = active,
            session = session,
            mediaInformation = session.mediaInformation?.toRuntimeMediaInformation(),
        )
    }

    private fun completeRequest(
        active: ActiveFFmpegRuntimeRequest,
        session: Session,
        mediaInformation: FFmpegRuntimeMediaInformation?,
    ) {
        synchronized(active.lifecycleLock) {
            if (!active.terminal.compareAndSet(false, true)) {
                return
            }
            activeRequests.remove(active.request.requestId, active)
            active.logWriter.close()
            val returnCode = session.returnCode?.value ?: UNKNOWN_RETURN_CODE
            val result =
                FFmpegRuntimeResult(
                    requestId = active.request.requestId,
                    operationWireValue = active.request.operationWireValue,
                    processId = Process.myPid(),
                    sessionId = session.sessionId,
                    terminalStateWireValue =
                        resolveFfmpegRuntimeTerminalState(returnCode).wireValue,
                    returnCode = returnCode,
                    durationMillis = session.duration.coerceAtLeast(0L),
                    outputLogPath = active.logWriter.file.absolutePath,
                    failStackTrace = boundFfmpegRuntimeDiagnostic(session.failStackTrace),
                    statistics = active.statistics.get(),
                    mediaInformation = mediaInformation,
                    runtimeInformation = active.runtimeInformation,
                )
            emit(active.registration) { currentCallback, generation, sequence ->
                currentCallback.onRequestCompleted(generation, sequence, result)
            }
        }
    }

    private fun failActiveRequest(
        active: ActiveFFmpegRuntimeRequest,
        failureCode: FFmpegRuntimeFailureCode,
        message: String,
    ) {
        synchronized(active.lifecycleLock) {
            if (!active.terminal.compareAndSet(false, true)) {
                return
            }
            activeRequests.remove(active.request.requestId, active)
            active.logWriter.close()
            emitSetupFailure(
                registration = active.registration,
                request = active.request,
                sessionId = active.session.get()?.sessionId ?: 0L,
                failureCode = failureCode,
                message = message,
                outputLogPath = active.logWriter.file.absolutePath,
            )
        }
    }

    private fun terminateActiveRequest(
        active: ActiveFFmpegRuntimeRequest,
        failureCode: FFmpegRuntimeFailureCode,
        message: String,
        emitFailure: Boolean,
    ) {
        synchronized(active.lifecycleLock) {
            if (!active.terminal.compareAndSet(false, true)) {
                return
            }
            activeRequests.remove(active.request.requestId, active)
            active.cancel()
            active.logWriter.close()
            if (emitFailure) {
                emitSetupFailure(
                    registration = active.registration,
                    request = active.request,
                    sessionId = active.session.get()?.sessionId ?: 0L,
                    failureCode = failureCode,
                    message = message,
                    outputLogPath = active.logWriter.file.absolutePath,
                )
            }
        }
    }

    private fun emitSetupFailure(
        registration: FFmpegRuntimeCallbackRegistration,
        request: FFmpegRuntimeRequest,
        sessionId: Long,
        failureCode: FFmpegRuntimeFailureCode,
        message: String,
        outputLogPath: String,
    ) {
        val failure =
            FFmpegRuntimeFailure(
                requestId = request.requestId,
                operationWireValue = request.operationWireValue,
                processId = Process.myPid(),
                sessionId = sessionId,
                failureCodeWireValue = failureCode.wireValue,
                message = message.take(MAX_FAILURE_MESSAGE_CHARS),
                outputLogPath = outputLogPath,
            )
        emit(registration) { currentCallback, generation, sequence ->
            currentCallback.onRequestFailed(generation, sequence, failure)
        }
    }

    private fun emit(
        registration: FFmpegRuntimeCallbackRegistration,
        callbackBlock: (IFFmpegRuntimeCallback, Long, Long) -> Unit,
    ) {
        callbackHandler.post {
            val sequence = registration.eventSequence.incrementAndGet()
            runCatching {
                callbackBlock(
                    registration.callback,
                    registration.runtimeGeneration,
                    sequence,
                )
            }
                .onFailure { error ->
                    Log.e(TAG, "Unable to dispatch FFmpeg runtime callback", error)
                }
        }
    }

    private fun createLogFile(requestId: String): File {
        val directory = ffmpegRuntimeLogDirectory(cacheDir)
        require(directory.exists() || directory.mkdirs()) {
            "Unable to create FFmpeg runtime log directory"
        }
        return ffmpegRuntimeLogFile(cacheDir, requestId).also { file ->
            FileOutputStream(file, false).use { output ->
                output.fd.sync()
            }
        }
    }

    private companion object {
        const val TAG = "FFmpegRuntimeService"
        const val RUNTIME_THREAD_PREFIX = "KiyoriFFmpegRuntime"
        const val CALLBACK_THREAD_NAME = "KiyoriFFmpegCallback"
        const val MEDIA_INFORMATION_TIMEOUT_MILLIS = 5_000
        const val CALLBACK_DRAIN_TIMEOUT_MILLIS = 5_000L
        const val CALLBACK_DRAIN_POLL_MILLIS = 10L
        const val UNKNOWN_RETURN_CODE = Int.MIN_VALUE
        const val MAX_FAILURE_MESSAGE_CHARS = 4_096
        val RUNTIME_THREAD_COUNTER = AtomicLong(0L)
    }
}

private class ActiveFFmpegRuntimeRequest(
    val registration: FFmpegRuntimeCallbackRegistration,
    val request: FFmpegRuntimeRequest,
    val logWriter: FFmpegRuntimeLogWriter,
) {
    val lifecycleLock = Any()
    val session = AtomicReference<Session?>(null)
    val statistics = AtomicReference<FFmpegRuntimeStatistics?>(null)
    val cancelRequested = AtomicBoolean(false)
    val terminal = AtomicBoolean(false)

    @Volatile
    var runtimeInformation: FFmpegRuntimeInformation? = null

    fun cancel() {
        cancelRequested.set(true)
        session.get()?.cancel()
    }

    fun cancelSessionIfRequested() {
        if (cancelRequested.get()) {
            session.get()?.cancel()
        }
    }
}

private class FFmpegRuntimeCallbackRegistration(
    val runtimeGeneration: Long,
    val callback: IFFmpegRuntimeCallback,
    val callbackBinder: IBinder,
    val deathRecipient: IBinder.DeathRecipient,
) {
    val eventSequence = AtomicLong(0L)
}

private data class DetachedFFmpegRuntimeRegistration(
    val registration: FFmpegRuntimeCallbackRegistration?,
    val activeRequests: List<ActiveFFmpegRuntimeRequest>,
)

private class FFmpegRuntimeLogWriter(val file: File) {
    private val lock = Any()
    private var writer: BufferedWriter? =
        FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8)

    fun append(message: String?) {
        if (message.isNullOrEmpty()) {
            return
        }
        synchronized(lock) {
            val currentWriter = writer ?: return
            currentWriter.write(message)
            // A native fatal signal can terminate the process without Java cleanup. Flushing each
            // callback preserves the latest attributable output for the main-process death report.
            currentWriter.flush()
        }
    }

    fun close() {
        synchronized(lock) {
            writer?.close()
            writer = null
        }
    }
}

private fun Statistics.toRuntimeStatistics(): FFmpegRuntimeStatistics =
    FFmpegRuntimeStatistics(
        videoFrameNumber = videoFrameNumber,
        videoFps = videoFps,
        videoQuality = videoQuality,
        sizeBytes = size,
        timeMillis = time,
        bitrateKbitsPerSecond = bitrate,
        speed = speed,
    )

private fun MediaInformation.toRuntimeMediaInformation(): FFmpegRuntimeMediaInformation =
    FFmpegRuntimeMediaInformation(
        format = format,
        duration = duration,
        bitrate = bitrate,
        streams = streams.orEmpty().map(StreamInformation::toRuntimeStreamInformation),
    )

private fun StreamInformation.toRuntimeStreamInformation(): FFmpegRuntimeStreamInformation =
    FFmpegRuntimeStreamInformation(
        index = index?.toInt() ?: 0,
        type = type,
        codec = codec,
        width = width?.toInt(),
        height = height?.toInt(),
        realFrameRate = realFrameRate,
        sampleRate = sampleRate,
        channels = getNumberProperty("channels")?.toInt(),
    )
