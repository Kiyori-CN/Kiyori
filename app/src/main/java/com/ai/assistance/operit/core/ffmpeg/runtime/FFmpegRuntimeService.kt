package com.ai.assistance.operit.core.ffmpeg.runtime

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeSession
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.Session
import com.arthenica.ffmpegkit.Statistics
import com.arthenica.ffmpegkit.StatisticsCallback
import java.io.BufferedOutputStream
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
    private val reservedRequestIds = ConcurrentHashMap.newKeySet<String>()
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
                return cancelRequest(active)
            }
        }

    override fun onCreate() {
        super.onCreate()
        val retention = pruneFfmpegRuntimeLogs(cacheDir)
        if (retention.deletedFiles > 0) {
            Log.i(TAG, "Pruned ${retention.deletedFiles} stale FFmpeg runtime logs")
        }
        val deletedProbeFiles = pruneFfmpegRuntimeProbeFiles(cacheDir)
        if (deletedProbeFiles > 0) {
            Log.i(TAG, "Pruned $deletedProbeFiles stale FFmpeg runtime probe files")
        }
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
        reservedRequestIds.clear()
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
        if (!reservedRequestIds.add(request.requestId)) {
            return false
        }
        val logFile =
            try {
                createLogFile(request.requestId)
            } catch (_: FFmpegRuntimeLogCollisionException) {
                reservedRequestIds.remove(request.requestId)
                return false
            } catch (error: Exception) {
                reservedRequestIds.remove(request.requestId)
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
        val accepted =
            synchronized(registrationLock) {
                if (callbackRegistration !== registration) {
                    false
                } else {
                    check(activeRequests.putIfAbsent(request.requestId, active) == null) {
                        "Reserved FFmpeg request ID was already active"
                    }
                    true
                }
            }
        if (!accepted) {
            active.logWriter.close()
            reservedRequestIds.remove(request.requestId)
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
                    val section = request.informationSection
                    active.runtimeInformation =
                        FFmpegRuntimeInformation(
                            sectionWireValue = section.wireValue,
                            executionPlane = EXECUTION_PLANE,
                            processName = "$packageName:ffmpeg",
                            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: Build.UNKNOWN,
                            androidApi = Build.VERSION.SDK_INT,
                            qualifiedProfile = QUALIFIED_CONVERSION_PROFILE,
                            wrapperVersion = FFmpegKitConfig.getVersion(),
                            ffmpegVersion = FFmpegKitConfig.getFFmpegVersion(),
                            buildDate = FFmpegKitConfig.getBuildDate(),
                        )
                    executeFfmpegSession(
                        active = active,
                        arguments = section.arguments.toTypedArray(),
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
        val probeFile = createProbeFile(active.request.requestId)
        active.probeOutputFile.set(probeFile)
        val arguments =
            arrayOf(
                "-v",
                "error",
                "-hide_banner",
                "-print_format",
                "json",
                "-show_format",
                "-show_streams",
                "-show_entries",
                FFMPEG_RUNTIME_PROBE_SHOW_ENTRIES,
                "-o",
                probeFile.absolutePath,
                "-i",
                inputPath,
            )
        val session =
            FFprobeSession.create(
                arguments,
                null,
                LogCallback { log ->
                    active.logWriter.append(log.message)
                },
            )
        try {
            if (!startSession(active, session)) {
                return
            }
            executeNativeSession(active, session) {
                FFmpegKitConfig.ffprobeExecute(session)
            }
            completeMediaInformationRequest(active, session, probeFile)
        } finally {
            active.probeOutputFile.compareAndSet(probeFile, null)
            if (probeFile.exists() && !probeFile.delete()) {
                Log.w(TAG, "Unable to delete private FFprobe output ${probeFile.name}")
            }
        }
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
        session: FFprobeSession,
        probeFile: File,
    ) {
        val returnCode =
            session.returnCode?.value
                ?: run {
                    failActiveRequest(
                        active = active,
                        failureCode = FFmpegRuntimeFailureCode.NATIVE_RETURN_CODE_MISSING,
                        message = "Android FFprobe completed without a native return code",
                    )
                    return
                }
        val mediaInformation =
            if (resolveFfmpegRuntimeTerminalState(returnCode) == FFmpegRuntimeTerminalState.SUCCEEDED) {
                try {
                    FFmpegRuntimeMediaInformationParser.parse(probeFile)
                } catch (error: Exception) {
                    val diagnostic =
                        error.message?.take(MAX_FAILURE_MESSAGE_CHARS)
                            ?: error.javaClass.simpleName
                    active.logWriter.append(
                        "\n[Kiyori] Android FFprobe metadata validation failed: $diagnostic\n",
                    )
                    failActiveRequest(
                        active = active,
                        failureCode = FFmpegRuntimeFailureCode.MEDIA_INFORMATION_INVALID,
                        message =
                            "Android FFprobe native execution succeeded, but its private JSON " +
                                "metadata was invalid: $diagnostic",
                    )
                    return
                }
            } else {
                null
            }
        completeRequest(
            active = active,
            session = session,
            mediaInformation = mediaInformation,
        )
    }

    private fun completeRequest(
        active: ActiveFFmpegRuntimeRequest,
        session: Session,
        mediaInformation: FFmpegRuntimeMediaInformation?,
    ) {
        val returnCode =
            session.returnCode?.value
                ?: run {
                    failActiveRequest(
                        active = active,
                        failureCode = FFmpegRuntimeFailureCode.NATIVE_RETURN_CODE_MISSING,
                        message = "Android FFmpeg completed without a native return code",
                    )
                    return
                }
        synchronized(active.lifecycleLock) {
            if (!active.terminal.compareAndSet(false, true)) {
                return
            }
            activeRequests.remove(active.request.requestId, active)
            reservedRequestIds.remove(active.request.requestId)
            active.logWriter.close()
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
            reservedRequestIds.remove(active.request.requestId)
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
            reservedRequestIds.remove(active.request.requestId)
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

    private fun cancelRequest(active: ActiveFFmpegRuntimeRequest): Boolean =
        synchronized(active.lifecycleLock) {
            if (active.terminal.get()) {
                return@synchronized false
            }
            active.cancelRequested.set(true)
            val session = active.session.get()
            if (session != null) {
                session.cancel()
                return@synchronized true
            }
            if (!active.terminal.compareAndSet(false, true)) {
                return@synchronized false
            }
            activeRequests.remove(active.request.requestId, active)
            reservedRequestIds.remove(active.request.requestId)
            active.logWriter.append(
                "[Kiyori] FFmpeg request was cancelled before native session creation.\n",
            )
            active.logWriter.close()
            val result =
                FFmpegRuntimeResult(
                    requestId = active.request.requestId,
                    operationWireValue = active.request.operationWireValue,
                    processId = Process.myPid(),
                    sessionId = 0L,
                    terminalStateWireValue = FFmpegRuntimeTerminalState.CANCELLED.wireValue,
                    returnCode = FFMPEG_RUNTIME_CANCEL_RETURN_CODE,
                    durationMillis = 0L,
                    outputLogPath = active.logWriter.file.absolutePath,
                    failStackTrace = null,
                    statistics = null,
                    mediaInformation = null,
                    runtimeInformation = null,
                )
            emit(active.registration) { currentCallback, generation, sequence ->
                currentCallback.onRequestCompleted(generation, sequence, result)
            }
            true
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
        val protectedLogPaths =
            activeRequests.values
                .mapTo(mutableSetOf()) { active -> active.logWriter.file.absolutePath }
        reservedRequestIds.forEach { reservedRequestId ->
            protectedLogPaths += ffmpegRuntimeLogFile(cacheDir, reservedRequestId).absolutePath
        }
        pruneFfmpegRuntimeLogs(
            cacheDir = cacheDir,
            protectedLogPaths = protectedLogPaths,
        )
        val directory = ffmpegRuntimeLogDirectory(cacheDir)
        require(directory.exists() || directory.mkdirs()) {
            "Unable to create FFmpeg runtime log directory"
        }
        val file = ffmpegRuntimeLogFile(cacheDir, requestId)
        if (!file.createNewFile()) {
            throw FFmpegRuntimeLogCollisionException()
        }
        try {
            FileOutputStream(file, true).use { output ->
                output.fd.sync()
            }
        } catch (error: Exception) {
            runCatching { file.delete() }
            throw error
        }
        return file
    }

    private fun createProbeFile(requestId: String): File {
        val directory = ffmpegRuntimeProbeDirectory(cacheDir)
        require(directory.exists() || directory.mkdirs()) {
            "Unable to create FFmpeg runtime probe directory"
        }
        val file = ffmpegRuntimeProbeFile(cacheDir, requestId)
        require(!file.exists()) { "FFmpeg runtime probe output already exists" }
        return file
    }

    private companion object {
        const val TAG = "FFmpegRuntimeService"
        const val RUNTIME_THREAD_PREFIX = "KiyoriFFmpegRuntime"
        const val CALLBACK_THREAD_NAME = "KiyoriFFmpegCallback"
        const val CALLBACK_DRAIN_TIMEOUT_MILLIS = 5_000L
        const val CALLBACK_DRAIN_POLL_MILLIS = 10L
        const val MAX_FAILURE_MESSAGE_CHARS = 4_096
        const val EXECUTION_PLANE = "android_ffmpegkit"
        const val QUALIFIED_CONVERSION_PROFILE = "h264_aac_mp4"
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
    val probeOutputFile = AtomicReference<File?>(null)
    val cancelRequested = AtomicBoolean(false)
    val terminal = AtomicBoolean(false)

    @Volatile
    var runtimeInformation: FFmpegRuntimeInformation? = null

    fun cancel() {
        cancelRequested.set(true)
        session.get()?.cancel()
        probeOutputFile.get()?.let { file ->
            runCatching { file.delete() }
        }
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

private class FFmpegRuntimeLogCollisionException : IllegalStateException()

internal class FFmpegRuntimeLogWriter(val file: File) {
    private val lock = Any()
    private val truncationMarkerBytes =
        FFMPEG_RUNTIME_LOG_TRUNCATION_MARKER.toByteArray(Charsets.UTF_8)
    private val contentByteLimit =
        FFMPEG_RUNTIME_MAX_LOG_BYTES - truncationMarkerBytes.size
    private var writer: BufferedOutputStream? =
        FileOutputStream(file, true).buffered()
    private var writtenBytes = file.length().toInt()
    private var truncated = false

    init {
        require(contentByteLimit > 0) { "FFmpeg runtime log marker exceeds the log limit" }
        require(writtenBytes in 0..contentByteLimit) {
            "FFmpeg runtime log file is not empty or already exceeds its limit"
        }
    }

    fun append(message: String?) {
        if (message.isNullOrEmpty() || truncated) {
            return
        }
        synchronized(lock) {
            val currentWriter = writer ?: return
            if (truncated) {
                return
            }
            val messageBytes = message.toByteArray(Charsets.UTF_8)
            val remainingContentBytes = contentByteLimit - writtenBytes
            if (messageBytes.size <= remainingContentBytes) {
                currentWriter.write(messageBytes)
                writtenBytes += messageBytes.size
            } else {
                var acceptedBytes = remainingContentBytes.coerceAtLeast(0)
                if (acceptedBytes in 1 until messageBytes.size) {
                    while (
                        acceptedBytes > 0 &&
                            (messageBytes[acceptedBytes].toInt() and UTF8_CONTINUATION_MASK) ==
                            UTF8_CONTINUATION_PREFIX
                    ) {
                        acceptedBytes -= 1
                    }
                }
                if (acceptedBytes > 0) {
                    currentWriter.write(messageBytes, 0, acceptedBytes)
                    writtenBytes += acceptedBytes
                }
                currentWriter.write(truncationMarkerBytes)
                writtenBytes += truncationMarkerBytes.size
                truncated = true
            }
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

    private companion object {
        const val UTF8_CONTINUATION_MASK = 0xC0
        const val UTF8_CONTINUATION_PREFIX = 0x80
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
