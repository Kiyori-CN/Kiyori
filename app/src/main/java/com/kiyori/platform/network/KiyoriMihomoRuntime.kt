package com.kiyori.platform.network

import android.content.Context
import com.kiyori.platform.android.KiyoriProcessIdentity
import com.kiyori.platform.logging.KiyoriLogger
import android.os.Process as AndroidProcess
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

enum class KiyoriMihomoRuntimePhase {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR,
}

enum class KiyoriMihomoRuntimeFailureKind {
    UNEXPECTED_PROCESS_EXIT,
    HEALTH_CHECK_FAILED,
    START_FAILED,
}

internal data class KiyoriMihomoProcessExitOutcome(
    val phase: KiyoriMihomoRuntimePhase,
    val logLevel: KiyoriNetworkProxyLogLevel,
    val message: String,
    val failureKind: KiyoriMihomoRuntimeFailureKind?,
)

internal fun classifyMihomoProcessExit(
    expectedStop: Boolean,
    exitCode: Int,
): KiyoriMihomoProcessExitOutcome =
    if (expectedStop) {
        KiyoriMihomoProcessExitOutcome(
            phase = KiyoriMihomoRuntimePhase.STOPPED,
            logLevel = KiyoriNetworkProxyLogLevel.INFO,
            message = "内嵌 Mihomo 已按请求停止，退出码 $exitCode",
            failureKind = null,
        )
    } else {
        KiyoriMihomoProcessExitOutcome(
            phase = KiyoriMihomoRuntimePhase.ERROR,
            logLevel = KiyoriNetworkProxyLogLevel.ERROR,
            message = "内嵌 Mihomo 进程意外退出，退出码 $exitCode",
            failureKind = KiyoriMihomoRuntimeFailureKind.UNEXPECTED_PROCESS_EXIT,
        )
    }

internal fun isCurrentMihomoProcess(
    activeProcess: Process?,
    observedProcess: Process,
): Boolean = activeProcess === observedProcess

internal fun computeMihomoProbeFingerprint(
    subscriptionId: String,
    sanitizedYaml: String,
    testUrl: String,
): String =
    sha256Utf8 {
        append(subscriptionId)
        append('\u0000')
        append(sanitizedYaml)
        append('\u0000')
        append(testUrl)
    }

internal fun computeMihomoRuntimeFingerprint(
    subscriptionId: String,
    sanitizedYaml: String,
    testUrl: String,
    routingMode: KiyoriNetworkConnectionMode,
    customRules: List<KiyoriNetworkProxyRule>,
): String =
    sha256Utf8 {
        append(subscriptionId)
        append('\u0000')
        append(sanitizedYaml)
        append('\u0000')
        append(testUrl)
        append('\u0000')
        append(routingMode.name)
        append('\u0000')
        customRules.forEachIndexed { index, rule ->
            if (index > 0) append('\u0001')
            append(rule.id)
            append(':')
            append(rule.type.name)
            append(':')
            append(rule.pattern)
            append(':')
            append(rule.mode.name)
            append(':')
            append(rule.enabled.toString())
        }
    }

private fun sha256Utf8(writePayload: Appendable.() -> Unit): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val digestSink =
        object : OutputStream() {
            override fun write(byte: Int) {
                digest.update(byte.toByte())
            }

            override fun write(
                bytes: ByteArray,
                offset: Int,
                length: Int,
            ) {
                digest.update(bytes, offset, length)
            }
        }
    OutputStreamWriter(digestSink, Charsets.UTF_8).use { writer ->
        writer.writePayload()
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

data class MihomoRuntimeGroupState(
    val name: String,
    val type: String,
    val currentItem: String?,
    val allItems: List<String>,
)

data class MihomoRuntimeNodeState(
    val name: String,
    val type: String,
    val groupNames: List<String>,
    val delayMillis: Int?,
    val alive: Boolean?,
)

data class MihomoRuntimeSnapshot(
    val groups: List<MihomoRuntimeGroupState>,
    val nodes: List<MihomoRuntimeNodeState>,
)

data class KiyoriMihomoRuntimeState(
    val phase: KiyoriMihomoRuntimePhase = KiyoriMihomoRuntimePhase.STOPPED,
    val subscriptionId: String? = null,
    val groups: List<MihomoRuntimeGroupState> = emptyList(),
    val nodes: List<MihomoRuntimeNodeState> = emptyList(),
    val message: String? = null,
    val runtimeGeneration: Long? = null,
    val mixedPort: Int? = null,
    val controllerPort: Int? = null,
    val controllerHealthy: Boolean? = null,
    val mixedPortListening: Boolean? = null,
    val startedAtEpochMillis: Long? = null,
    val stopReason: String? = null,
    val failureKind: KiyoriMihomoRuntimeFailureKind? = null,
)

internal fun formatKiyoriMihomoRuntimeDiagnostic(
    state: KiyoriMihomoRuntimeState,
): String =
    "应用级 Mihomo runtime phase=${state.phase} " +
        "runtimeGeneration=${state.runtimeGeneration ?: "none"} " +
        "mixedPort=${state.mixedPort ?: "none"} " +
        "controllerPort=${state.controllerPort ?: "none"} " +
        "controllerHealthy=${state.controllerHealthy ?: "unknown"} " +
        "mixedPortListening=${state.mixedPortListening ?: "unknown"} " +
        "stopReason=${state.stopReason ?: "none"} " +
        "failure=${state.failureKind ?: "none"} " +
        "message=${state.message ?: "none"}"

enum class KiyoriMihomoProbePhase {
    IDLE,
    STARTING,
    READY,
    TESTING,
    ERROR,
}

data class KiyoriMihomoProbeState(
    val phase: KiyoriMihomoProbePhase = KiyoriMihomoProbePhase.IDLE,
    val subscriptionId: String? = null,
    val message: String? = null,
)

class KiyoriMihomoRuntime private constructor(context: Context) {
    companion object {
        private const val TAG = "KiyoriMihomoRuntime"
        private const val CORE_FILE_NAME = "libkiyori_mihomo.so"
        private const val LAUNCHER_FILE_NAME = "libkiyori_mihomo_launcher.so"
        private const val RUNTIME_DIRECTORY_PREFIX = "network_proxy/runtime-"
        private const val VALIDATION_DIRECTORY_PREFIX = "network_proxy/validation-"
        private const val PROBE_DIRECTORY_PREFIX = "network_proxy/probe-"
        private const val CONFIG_FILE_NAME = "config.yaml"
        private const val READINESS_TIMEOUT_MILLIS = 12_000L
        private const val TEST_TIMEOUT_SECONDS = 20L
        private const val STOP_TIMEOUT_SECONDS = 3L
        private const val OUTPUT_DRAIN_TIMEOUT_MILLIS = 2_000L
        private const val MAX_CAPTURED_CORE_LINES = 32
        private const val DELAY_TEST_TIMEOUT_MILLIS = 8_000
        private const val PORT_HEALTH_TIMEOUT_MILLIS = 500
        private const val CONTROLLER_HEALTH_TIMEOUT_MILLIS = 800L
        private const val HEALTH_MONITOR_INTERVAL_MILLIS = 10_000L
        private const val HEALTH_FAILURE_THRESHOLD = 2
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @Volatile
        private var instance: KiyoriMihomoRuntime? = null

        fun getInstance(context: Context): KiyoriMihomoRuntime =
            instance
                ?: synchronized(this) {
                    instance
                        ?: KiyoriMihomoRuntime(context.applicationContext).also { instance = it }
                }
    }

    private data class ActiveRuntime(
        val process: Process,
        val subscriptionId: String,
        val fingerprint: String,
        val workDirectory: File,
        val endpoint: KiyoriProxyEndpoint,
        val controllerPort: Int,
        val controllerSecret: String,
        val groupSummaries: List<MihomoProxyGroupSummary>,
        var groups: List<MihomoRuntimeGroupState>,
        var nodes: List<MihomoRuntimeNodeState>,
        var appliedSelections: Map<String, String>,
        val runtimeGeneration: Long,
        val startedAtEpochMillis: Long,
        val outputCollector: ProcessOutputCollector,
        @Volatile var expectedStop: Boolean = false,
        @Volatile var stopReason: String? = null,
        @Volatile var controllerHealthy: Boolean = true,
        @Volatile var mixedPortListening: Boolean = true,
    )

    private class ProcessOutputCollector(
        private val worker: Thread,
        private val lines: ArrayDeque<String>,
        private val lineLock: Any,
    ) {
        fun await(): List<String> {
            worker.join(OUTPUT_DRAIN_TIMEOUT_MILLIS)
            return synchronized(lineLock) { lines.toList() }
        }

        fun snapshot(): List<String> = synchronized(lineLock) { lines.toList() }
    }

    private data class RuntimeHealth(
        val controllerReady: Boolean,
        val controllerStatus: Int?,
        val mixedPortListening: Boolean,
        val elapsedMillis: Long,
    )

    private val appContext = context.applicationContext
    private val proxyLog = KiyoriNetworkProxyLogStore
    private val processName = KiyoriProcessIdentity.currentProcessName(appContext)
    private val hostProcessId = AndroidProcess.myPid()
    private val processKey = sha256(KiyoriProcessIdentity.currentProcessName(appContext)).take(12)
    private val runtimeDirectory =
        appContext.noBackupFilesDir.resolve(RUNTIME_DIRECTORY_PREFIX + processKey)
    private val validationDirectory =
        appContext.noBackupFilesDir.resolve(VALIDATION_DIRECTORY_PREFIX + processKey)
    private val probeDirectory =
        appContext.noBackupFilesDir.resolve(PROBE_DIRECTORY_PREFIX + processKey)
    private val mutex = Mutex()
    private val probeMutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startupCleanup =
        cleanupScope.async(start = CoroutineStart.LAZY) {
            mutex.withLock {
                clearRuntimeDirectory()
                clearDirectory(validationDirectory, "validation")
                withContext(Dispatchers.IO) { clearDirectory(probeDirectory, "probe") }
            }
        }
    private val random by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SecureRandom() }
    private val runtimeGeneration = AtomicLong(0L)
    private val controllerClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }
    // A stuck-but-alive core must fail route setup quickly instead of holding Browser/player setup
    // behind the normal Controller request timeout.
    private val controllerHealthClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(CONTROLLER_HEALTH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .readTimeout(CONTROLLER_HEALTH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .callTimeout(CONTROLLER_HEALTH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .build()
    }
    private val mutableState = MutableStateFlow(KiyoriMihomoRuntimeState())
    val state: StateFlow<KiyoriMihomoRuntimeState> = mutableState.asStateFlow()
    private val mutableProbeState = MutableStateFlow(KiyoriMihomoProbeState())
    val probeState: StateFlow<KiyoriMihomoProbeState> = mutableProbeState.asStateFlow()

    private var activeRuntime: ActiveRuntime? = null

    init {
        proxyLog.setProcessContext(processName, hostProcessId)
    }

    fun scheduleStaleRuntimeCleanup() {
        startupCleanup.start()
    }

    suspend fun validateConfiguration(
        config: KiyoriProxySubscription,
        testUrl: String,
    ) {
        awaitStartupCleanup()
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val nativeDirectory = File(appContext.applicationInfo.nativeLibraryDir)
                val core = nativeDirectory.resolve(CORE_FILE_NAME)
                val launcher = nativeDirectory.resolve(LAUNCHER_FILE_NAME)
                if (!core.isFile || !launcher.isFile) {
                    throw KiyoriNetworkException(
                        KiyoriNetworkErrorCode.CORE_MISSING,
                        "The embedded Mihomo runtime is missing from this APK.",
                    )
                }
                clearDirectory(validationDirectory, "validation")
                if (!validationDirectory.mkdirs() && !validationDirectory.isDirectory) {
                    throw IllegalStateException("Unable to create the Mihomo validation directory")
                }
                restrictToOwner(validationDirectory)
                try {
                    proxyLog.info("配置校验", "开始使用内嵌 Mihomo 校验订阅运行配置")
                    val mixedPort = allocateLoopbackPort()
                    var controllerPort = allocateLoopbackPort()
                    while (controllerPort == mixedPort) controllerPort = allocateLoopbackPort()
                    val runtimeConfig =
                        MihomoConfigSanitizer.buildRuntimeConfig(
                            sanitizedYaml = config.sanitizedYaml,
                            mixedPort = mixedPort,
                            controllerPort = controllerPort,
                            controllerSecret = randomHex(32),
                            testUrl = testUrl,
                        )
                    val configFile = validationDirectory.resolve(CONFIG_FILE_NAME)
                    configFile.writeText(runtimeConfig.yaml, Charsets.UTF_8)
                    restrictToOwner(configFile)
                    testConfiguration(
                        launcher = launcher,
                        core = core,
                        configFile = configFile,
                        workDirectory = validationDirectory,
                    )
                    proxyLog.info("配置校验", "订阅运行配置已通过内嵌 Mihomo 校验")
                } finally {
                    clearDirectory(validationDirectory, "validation")
                }
            }
        }
    }

    suspend fun ensureReady(
        config: KiyoriProxySubscription,
        testUrl: String,
        routingMode: KiyoriNetworkConnectionMode = KiyoriNetworkConnectionMode.GLOBAL,
        customRules: List<KiyoriNetworkProxyRule> = emptyList(),
    ): KiyoriProxyEndpoint {
        awaitStartupCleanup()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val active = startOrReuseLocked(config, testUrl, routingMode, customRules)
                if (active.appliedSelections != config.selectedGroupItems) {
                    applySelectionsLocked(active, config.selectedGroupItems)
                    active.appliedSelections = config.selectedGroupItems
                    refreshSnapshotLocked(active)
                }
                active.endpoint
            }
        }
    }

    suspend fun discoverSnapshot(
        config: KiyoriProxySubscription,
        testUrl: String,
    ): MihomoRuntimeSnapshot {
        ensureReady(config, testUrl)
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val active = requireActiveRuntimeLocked()
                refreshSnapshotLocked(active)
            }
        }
    }

    suspend fun selectGroup(
        groupName: String,
        itemName: String,
    ): List<MihomoRuntimeGroupState> {
        awaitStartupCleanup()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val active = requireActiveRuntimeLocked()
                selectGroupLocked(active, groupName.trim(), itemName.trim())
                active.appliedSelections =
                    active.appliedSelections.toMutableMap().apply {
                        this[groupName.trim()] = itemName.trim()
                    }
                refreshSnapshotLocked(active)
                active.groups
            }
        }
    }

    suspend fun refreshActiveSnapshot(): MihomoRuntimeSnapshot {
        awaitStartupCleanup()
        return withContext(Dispatchers.IO) {
            mutex.withLock { refreshSnapshotLocked(requireActiveRuntimeLocked()) }
        }
    }

    suspend fun testActiveNodeDelay(
        nodeName: String,
        testUrl: String,
    ): Int {
        awaitStartupCleanup()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val active = requireActiveRuntimeLocked()
                val delay = testNodeDelay(active, nodeName.trim(), testUrl)
                refreshSnapshotLocked(active)
                delay
            }
        }
    }

    suspend fun testActiveGroupDelays(
        groupName: String,
        testUrl: String,
    ): Map<String, Int> {
        awaitStartupCleanup()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val active = requireActiveRuntimeLocked()
                val delays = testGroupDelays(active, groupName.trim(), testUrl)
                refreshSnapshotLocked(active)
                delays
            }
        }
    }

    suspend fun probeSnapshot(
        config: KiyoriProxySubscription,
        testUrl: String,
    ): MihomoRuntimeSnapshot =
        withProbeRuntime(config, testUrl, KiyoriMihomoProbePhase.READY) { active ->
            refreshSnapshotLocked(active)
        }

    suspend fun probeNodeDelay(
        config: KiyoriProxySubscription,
        nodeName: String,
        testUrl: String,
    ): Pair<MihomoRuntimeSnapshot, Int> =
        withProbeRuntime(config, testUrl, KiyoriMihomoProbePhase.TESTING) { active ->
            val delay = testNodeDelay(active, nodeName.trim(), testUrl)
            refreshSnapshotLocked(active) to delay
        }

    suspend fun probeGroupDelays(
        config: KiyoriProxySubscription,
        groupName: String,
        testUrl: String,
    ): Pair<MihomoRuntimeSnapshot, Map<String, Int>> =
        withProbeRuntime(config, testUrl, KiyoriMihomoProbePhase.TESTING) { active ->
            val delays = testGroupDelays(active, groupName.trim(), testUrl)
            refreshSnapshotLocked(active) to delays
        }

    private suspend fun <T> withProbeRuntime(
        config: KiyoriProxySubscription,
        testUrl: String,
        operationPhase: KiyoriMihomoProbePhase,
        block: (ActiveRuntime) -> T,
    ): T {
        awaitStartupCleanup()
        return probeMutex.withLock {
            mutableProbeState.value =
                KiyoriMihomoProbeState(
                    phase = KiyoriMihomoProbePhase.STARTING,
                    subscriptionId = config.id,
                )
            var probe: ActiveRuntime? = null
            try {
                proxyLog.info("订阅探测", "正在启动隔离 Mihomo 以读取节点或执行测速")
                val active =
                    withContext(Dispatchers.IO) {
                        startProcess(
                            config = config,
                            testUrl = testUrl,
                            fingerprint =
                                computeMihomoProbeFingerprint(
                                    subscriptionId = config.id,
                                    sanitizedYaml = config.sanitizedYaml,
                                    testUrl = testUrl,
                                ),
                            workDirectory = probeDirectory,
                            directoryLabel = "probe",
                        )
                    }
                probe = active
                withContext(Dispatchers.IO) {
                    applySelectionsLocked(active, config.selectedGroupItems)
                    active.appliedSelections = config.selectedGroupItems
                    refreshSnapshotLocked(active)
                }
                mutableProbeState.value =
                    KiyoriMihomoProbeState(
                        phase = operationPhase,
                        subscriptionId = config.id,
                    )
                withContext(Dispatchers.IO) { block(active) }.also {
                    proxyLog.info("订阅探测", "隔离 Mihomo 操作已完成")
                }
            } catch (error: KiyoriNetworkException) {
                proxyLog.error("订阅探测", "隔离 Mihomo 操作失败：${error.code.name} ${error.message.orEmpty()}")
                mutableProbeState.value =
                    KiyoriMihomoProbeState(
                        phase = KiyoriMihomoProbePhase.ERROR,
                        subscriptionId = config.id,
                        message = error.message,
                    )
                throw error
            } catch (error: Exception) {
                KiyoriLogger.e(TAG, "Unable to run the isolated Mihomo subscription probe", error)
                proxyLog.error("订阅探测", "隔离 Mihomo 启动异常：${error::class.java.simpleName}")
                val wrapped =
                    KiyoriNetworkException(
                        KiyoriNetworkErrorCode.CORE_START_FAILED,
                        "The isolated Mihomo subscription probe could not be started.",
                        error,
                    )
                mutableProbeState.value =
                    KiyoriMihomoProbeState(
                        phase = KiyoriMihomoProbePhase.ERROR,
                        subscriptionId = config.id,
                        message = wrapped.message,
                    )
                throw wrapped
            } finally {
                probe?.let { active ->
                    withContext(Dispatchers.IO) { stopProcessBlocking(active.process) }
                }
                withContext(Dispatchers.IO) { clearDirectory(probeDirectory, "probe") }
                if (mutableProbeState.value.phase != KiyoriMihomoProbePhase.ERROR) {
                    mutableProbeState.value = KiyoriMihomoProbeState()
                }
            }
        }
    }

    suspend fun stop(reason: String = "explicit_request") {
        awaitStartupCleanup()
        mutex.withLock { stopLocked(KiyoriMihomoRuntimePhase.STOPPED, null, reason) }
    }

    private suspend fun awaitStartupCleanup() {
        startupCleanup.start()
        startupCleanup.await()
    }

    private suspend fun startOrReuseLocked(
        config: KiyoriProxySubscription,
        testUrl: String,
        routingMode: KiyoriNetworkConnectionMode,
        customRules: List<KiyoriNetworkProxyRule>,
    ): ActiveRuntime {
        if (config.sanitizedYaml.isBlank()) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.CONFIG_MISSING,
                "No Clash or Mihomo subscription has been imported.",
            )
        }
        // 每个被代理请求都会经过复用检查；增量哈希避免为大订阅再复制完整 String 和 UTF-8 数组。
        val fingerprint =
            computeMihomoRuntimeFingerprint(
                subscriptionId = config.id,
                sanitizedYaml = config.sanitizedYaml,
                testUrl = testUrl,
                routingMode = routingMode,
                customRules = customRules,
            )
        activeRuntime?.let { active ->
            if (active.process.isAlive && active.fingerprint == fingerprint) {
                val health = inspectRuntimeHealth(active)
                active.controllerHealthy = health.controllerReady
                active.mixedPortListening = health.mixedPortListening
                if (!health.controllerReady || !health.mixedPortListening) {
                    proxyLog.error(
                        "运行时健康",
                        "复用检查失败 runtimeGeneration=${active.runtimeGeneration} " +
                            "processAlive=true controllerReady=${health.controllerReady} " +
                            "mixedPortListening=${health.mixedPortListening} " +
                            "controllerStatus=${health.controllerStatus ?: "none"} " +
                            "elapsedMs=${health.elapsedMillis}",
                    )
                    stopLocked(
                        finalPhase = KiyoriMihomoRuntimePhase.ERROR,
                        message = "The embedded Mihomo runtime health check failed.",
                        reason = "health_check_failed",
                        failureKind = KiyoriMihomoRuntimeFailureKind.HEALTH_CHECK_FAILED,
                    )
                    throw KiyoriNetworkException(
                        KiyoriNetworkErrorCode.CORE_START_FAILED,
                        "The embedded Mihomo runtime health check failed.",
                    )
                }
                proxyLog.info(
                    "运行时健康",
                    "复用检查通过 runtimeGeneration=${active.runtimeGeneration} " +
                        "mixedPortListening=true controllerReady=true elapsedMs=${health.elapsedMillis}",
                )
                publishRunningState(active)
                return active
            }
            stopLocked(KiyoriMihomoRuntimePhase.STOPPED, null, "runtime_replaced")
        }

        mutableState.value = KiyoriMihomoRuntimeState(phase = KiyoriMihomoRuntimePhase.STARTING)
        proxyLog.info("主运行时", "正在启动内嵌 Mihomo")
        return try {
            val active =
                withContext(Dispatchers.IO) {
                    startProcess(
                        config = config,
                        testUrl = testUrl,
                        fingerprint = fingerprint,
                        workDirectory = runtimeDirectory,
                        directoryLabel = "runtime",
                        routingMode = routingMode,
                        customRules = customRules,
                    )
                }
            activeRuntime = active
            monitorProcess(active)
            monitorRuntimeHealth(active)
            publishRunningState(active)
            active
        } catch (error: KiyoriNetworkException) {
            proxyLog.error("主运行时", "内嵌 Mihomo 启动失败：${error.code.name} ${error.message.orEmpty()}")
            stopLocked(KiyoriMihomoRuntimePhase.ERROR, error.message, "runtime_start_failed")
            throw error
        } catch (error: Exception) {
            KiyoriLogger.e(TAG, "Unable to start the embedded Mihomo runtime", error)
            proxyLog.error("主运行时", "内嵌 Mihomo 启动异常：${error::class.java.simpleName}")
            val wrapped =
                KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CORE_START_FAILED,
                    "The embedded Mihomo runtime could not be started.",
                    error,
                )
            stopLocked(KiyoriMihomoRuntimePhase.ERROR, wrapped.message, "runtime_start_exception")
            throw wrapped
        }
    }

    private fun startProcess(
        config: KiyoriProxySubscription,
        testUrl: String,
        fingerprint: String,
        workDirectory: File,
        directoryLabel: String,
        routingMode: KiyoriNetworkConnectionMode = KiyoriNetworkConnectionMode.GLOBAL,
        customRules: List<KiyoriNetworkProxyRule> = emptyList(),
    ): ActiveRuntime {
        val nativeDirectory = File(appContext.applicationInfo.nativeLibraryDir)
        val core = nativeDirectory.resolve(CORE_FILE_NAME)
        val launcher = nativeDirectory.resolve(LAUNCHER_FILE_NAME)
        if (!core.isFile || !launcher.isFile) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.CORE_MISSING,
                "The embedded Mihomo runtime is missing from this APK.",
            )
        }

        resetDirectory(workDirectory, directoryLabel)
        proxyLog.info("Mihomo $directoryLabel", "正在生成并校验自包含运行配置")
        val mixedPort = allocateLoopbackPort()
        var controllerPort = allocateLoopbackPort()
        while (controllerPort == mixedPort) controllerPort = allocateLoopbackPort()
        val secret = randomHex(32)
        val runtimeConfig =
            MihomoConfigSanitizer.buildRuntimeConfig(
                sanitizedYaml = config.sanitizedYaml,
                mixedPort = mixedPort,
                controllerPort = controllerPort,
                controllerSecret = secret,
                testUrl = testUrl,
                routingMode = routingMode,
                customRules = customRules,
            )
        val configFile = workDirectory.resolve(CONFIG_FILE_NAME)
        configFile.writeText(runtimeConfig.yaml, Charsets.UTF_8)
        restrictToOwner(configFile)

        testConfiguration(launcher, core, configFile, workDirectory)
        val generation = runtimeGeneration.incrementAndGet()
        val startedAtEpochMillis = System.currentTimeMillis()
        proxyLog.info(
            "Mihomo $directoryLabel",
            "启动参数已准备 runtimeGeneration=$generation mixedPort=$mixedPort " +
                "controllerPort=$controllerPort hostProcess=$processName hostPid=$hostProcessId",
        )
        val process =
            startMihomoProcess(
                launcher = launcher,
                core = core,
                configFile = configFile,
                controllerPort = controllerPort,
                secret = secret,
                workDirectory = workDirectory,
            )
        val outputCollector =
            collectProcessOutput(
                process,
                "Mihomo $directoryLabel generation=$generation",
            )
        val active =
            ActiveRuntime(
                process = process,
                subscriptionId = config.id,
                fingerprint = fingerprint,
                workDirectory = workDirectory,
                endpoint = KiyoriProxyEndpoint(host = "127.0.0.1", port = mixedPort),
                controllerPort = controllerPort,
                controllerSecret = secret,
                groupSummaries = runtimeConfig.groupSummaries,
                groups = emptyList(),
                nodes = emptyList(),
                appliedSelections = emptyMap(),
                runtimeGeneration = generation,
                startedAtEpochMillis = startedAtEpochMillis,
                outputCollector = outputCollector,
            )
        try {
            val deadline = System.currentTimeMillis() + READINESS_TIMEOUT_MILLIS
            var controllerReady = false
            while (System.currentTimeMillis() < deadline && process.isAlive) {
                if (controllerVersionIsReady(active)) {
                    controllerReady = true
                    break
                }
                Thread.sleep(120L)
            }
            if (!controllerReady) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CORE_START_FAILED,
                    "The embedded Mihomo controller did not become ready in time.",
                )
            }

            val snapshot = fetchSnapshot(active)
            active.groups = snapshot.groups
            active.nodes = snapshot.nodes
            val root = active.groups.firstOrNull { it.name == MihomoConfigSanitizer.ROUTE_GROUP_NAME }
            if (root == null || root.allItems.isEmpty()) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CONFIG_INVALID,
                    "The embedded subscription exposed no selectable root route.",
                )
            }
            val health = inspectRuntimeHealth(active)
            active.controllerHealthy = health.controllerReady
            active.mixedPortListening = health.mixedPortListening
            if (!health.mixedPortListening) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CORE_START_FAILED,
                    "The embedded Mihomo mixed port did not start listening.",
                )
            }
            proxyLog.info(
                "Mihomo $directoryLabel",
                "运行时健康 runtimeGeneration=${active.runtimeGeneration} " +
                    "controllerReady=${health.controllerReady} mixedPortListening=${health.mixedPortListening} " +
                    "controllerStatus=${health.controllerStatus ?: "none"} elapsedMs=${health.elapsedMillis}",
            )
            if (configFile.exists() && !configFile.delete()) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CORE_START_FAILED,
                    "The embedded proxy configuration could not be removed after startup.",
                )
            }
            proxyLog.info("Mihomo $directoryLabel", "Controller 已就绪，明文运行配置已清理")
            return active
        } catch (error: Exception) {
            val exitCode = stopProcessBlocking(process)
            proxyLog.error(
                "Mihomo $directoryLabel",
                "启动阶段失败 runtimeGeneration=${active.runtimeGeneration} exitCode=$exitCode " +
                    "lastCoreLine=${active.outputCollector.snapshot().lastOrNull() ?: "none"}",
            )
            clearDirectory(workDirectory, directoryLabel)
            throw error
        }
    }

    private fun testConfiguration(
        launcher: File,
        core: File,
        configFile: File,
        workDirectory: File,
    ) {
        val process =
            processBuilder(
                launcher,
                core,
                "-t",
                "-d",
                workDirectory.absolutePath,
                "-f",
                configFile.absolutePath,
                workDirectory = workDirectory,
            ).start()
        val output = collectProcessOutput(process, "Mihomo 校验")
        if (!process.waitFor(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            output.await()
            proxyLog.error("Mihomo 校验", "配置校验在 ${TEST_TIMEOUT_SECONDS} 秒后超时")
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.CONFIG_INVALID,
                "Mihomo configuration validation timed out.",
            )
        }
        val captured = output.await()
        val exitCode = process.exitValue()
        if (exitCode != 0) {
            val detail = captured.lastOrNull { line -> line.isNotBlank() }
            proxyLog.error(
                "Mihomo 校验",
                buildString {
                    append("核心拒绝运行配置，退出码 ")
                    append(exitCode)
                    detail?.let { append("：").append(it) }
                },
            )
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.CONFIG_INVALID,
                buildString {
                    append("Mihomo rejected the sanitized subscription")
                    detail?.let { append(": ").append(it) }
                    append('.')
                },
            )
        }
    }

    private fun startMihomoProcess(
        launcher: File,
        core: File,
        configFile: File,
        controllerPort: Int,
        secret: String,
        workDirectory: File,
    ): Process =
        processBuilder(
            launcher,
            core,
            "-d",
            workDirectory.absolutePath,
            "-f",
            configFile.absolutePath,
            "-ext-ctl",
            "127.0.0.1:$controllerPort",
            "-secret",
            secret,
            workDirectory = workDirectory,
        ).start()

    private fun processBuilder(
        launcher: File,
        core: File,
        vararg arguments: String,
        workDirectory: File,
    ): ProcessBuilder =
        ProcessBuilder(
            buildList {
                add(launcher.absolutePath)
                add(core.absolutePath)
                addAll(arguments)
            },
        )
            .directory(workDirectory)
            .redirectErrorStream(true)
            .also { builder ->
                builder.environment().keys
                    .filter { key -> key.startsWith("CLASH_") }
                    .forEach(builder.environment()::remove)
            }

    private fun controllerVersionIsReady(active: ActiveRuntime): Boolean =
        controllerHealth(active, controllerClient).first

    private fun controllerHealth(
        active: ActiveRuntime,
        client: OkHttpClient,
    ): Pair<Boolean, Int?> {
        val request = controllerRequest(active, controllerUrl(active, "version")).get().build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                response.isSuccessful to response.code
            }
        }.getOrDefault(false to null)
    }

    private fun inspectRuntimeHealth(active: ActiveRuntime): RuntimeHealth {
        val startedAt = System.currentTimeMillis()
        val (controllerReady, controllerStatus) = controllerHealth(active, controllerHealthClient)
        val mixedPortListening =
            runCatching {
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress(active.endpoint.host, active.endpoint.port),
                        PORT_HEALTH_TIMEOUT_MILLIS,
                    )
                }
                true
            }.getOrDefault(false)
        return RuntimeHealth(
            controllerReady = controllerReady,
            controllerStatus = controllerStatus,
            mixedPortListening = mixedPortListening,
            elapsedMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L),
        )
    }

    private fun fetchSnapshot(active: ActiveRuntime): MihomoRuntimeSnapshot {
        val request = controllerRequest(active, controllerUrl(active, "proxies")).get().build()
        controllerClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CORE_START_FAILED,
                    "Unable to read Mihomo groups from the local controller.",
                )
            }
            val body =
                response.body
                    ?: throw KiyoriNetworkException(
                        KiyoriNetworkErrorCode.CORE_START_FAILED,
                        "The local Mihomo controller returned an empty response.",
                    )
            val proxies = JSONObject(body.string()).optJSONObject("proxies")
                ?: throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CORE_START_FAILED,
                    "The local Mihomo controller returned no proxy map.",
                )
            val groups = active.groupSummaries.mapNotNull { summary ->
                val group = proxies.optJSONObject(summary.name) ?: return@mapNotNull null
                val all = group.optJSONArray("all") ?: return@mapNotNull null
                MihomoRuntimeGroupState(
                    name = summary.name,
                    type = group.optString("type", summary.type),
                    currentItem = group.optString("now").trim().takeIf(String::isNotEmpty),
                    allItems =
                        buildList {
                            repeat(all.length()) { index ->
                                all.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
                            }
                        }.distinct(),
                )
            }
            val groupNames = groups.mapTo(linkedSetOf(), MihomoRuntimeGroupState::name)
            val orderedNames =
                buildList {
                    groups.forEach { group -> addAll(group.allItems) }
                    val keys = proxies.keys()
                    while (keys.hasNext()) add(keys.next())
                }.distinct()
            val nodes =
                orderedNames.mapNotNull { name ->
                    if (name in groupNames || name in CONTROLLER_BUILTIN_OUTBOUNDS) {
                        return@mapNotNull null
                    }
                    val proxy = proxies.optJSONObject(name) ?: return@mapNotNull null
                    val type = proxy.optString("type").trim()
                    if (type in CONTROLLER_GROUP_TYPES) return@mapNotNull null
                    MihomoRuntimeNodeState(
                        name = name,
                        type = type.ifBlank { "Unknown" },
                        groupNames =
                            groups.filter { group -> name in group.allItems }
                                .map(MihomoRuntimeGroupState::name),
                        delayMillis = latestDelay(proxy),
                        alive = proxy.optBoolean("alive").takeIf { proxy.has("alive") },
                    )
                }
            return MihomoRuntimeSnapshot(groups = groups, nodes = nodes)
        }
    }

    private fun latestDelay(proxy: JSONObject): Int? {
        val history = proxy.optJSONArray("history") ?: return null
        for (index in history.length() - 1 downTo 0) {
            val delay = history.optJSONObject(index)?.optInt("delay", 0) ?: 0
            if (delay > 0) return delay
        }
        return null
    }

    private fun refreshSnapshotLocked(active: ActiveRuntime): MihomoRuntimeSnapshot {
        val snapshot = fetchSnapshot(active)
        active.groups = snapshot.groups
        active.nodes = snapshot.nodes
        if (activeRuntime?.process === active.process) publishRunningState(active)
        return snapshot
    }

    private fun publishRunningState(active: ActiveRuntime) {
        mutableState.value =
            KiyoriMihomoRuntimeState(
                phase = KiyoriMihomoRuntimePhase.RUNNING,
                subscriptionId = active.subscriptionId,
                groups = active.groups,
                nodes = active.nodes,
                runtimeGeneration = active.runtimeGeneration,
                mixedPort = active.endpoint.port,
                controllerPort = active.controllerPort,
                controllerHealthy = active.controllerHealthy,
                mixedPortListening = active.mixedPortListening,
                startedAtEpochMillis = active.startedAtEpochMillis,
            )
    }

    private fun testNodeDelay(
        active: ActiveRuntime,
        nodeName: String,
        testUrl: String,
    ): Int {
        proxyLog.info("节点测速", "正在测试节点：$nodeName")
        if (active.nodes.none { node -> node.name == nodeName }) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.PROXY_CONNECT_FAILED,
                "The selected Mihomo node is not available.",
            )
        }
        val responseJson = executeDelayRequest(active, listOf("proxies", nodeName, "delay"), testUrl)
        val delay = responseJson.optInt("delay", -1)
        if (delay <= 0) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.PROXY_CONNECT_FAILED,
                "Mihomo returned an invalid node delay.",
            )
        }
        proxyLog.info("节点测速", "节点测速完成：$nodeName，${delay} ms")
        return delay
    }

    private fun testGroupDelays(
        active: ActiveRuntime,
        groupName: String,
        testUrl: String,
    ): Map<String, Int> {
        proxyLog.info("分组测速", "正在测试策略组：$groupName")
        if (active.groups.none { group -> group.name == groupName }) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.GROUP_SELECTION_INVALID,
                "The selected Mihomo group is not available.",
            )
        }
        val responseJson = executeDelayRequest(active, listOf("group", groupName, "delay"), testUrl)
        return buildMap {
            val keys = responseJson.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val delay = responseJson.optInt(name, -1)
                if (delay > 0) put(name, delay)
            }
        }.takeIf(Map<String, Int>::isNotEmpty)
            ?.also { delays ->
                proxyLog.info("分组测速", "策略组测速完成：$groupName，成功 ${delays.size} 项")
            }
            ?: throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.PROXY_CONNECT_FAILED,
                "Mihomo returned no successful node delays for the selected group.",
            )
    }

    private fun executeDelayRequest(
        active: ActiveRuntime,
        pathSegments: List<String>,
        testUrl: String,
    ): JSONObject {
        val startedAt = System.currentTimeMillis()
        val operation = pathSegments.firstOrNull().orEmpty().ifBlank { "controller" }
        proxyLog.info(
            "Mihomo Controller",
            "开始请求 operation=$operation runtimeGeneration=${active.runtimeGeneration} " +
                "controllerPort=${active.controllerPort} timeoutMs=$DELAY_TEST_TIMEOUT_MILLIS",
        )
        val url =
            controllerUrl(active, *pathSegments.toTypedArray())
                .newBuilder()
                .addQueryParameter("url", testUrl)
                .addQueryParameter("timeout", DELAY_TEST_TIMEOUT_MILLIS.toString())
                .build()
        val request = controllerRequest(active, url).get().build()
        return try {
            controllerClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    proxyLog.error(
                        "Mihomo Controller",
                        "请求失败 operation=$operation runtimeGeneration=${active.runtimeGeneration} " +
                            "httpStatus=${response.code} elapsedMs=${elapsedSince(startedAt)}",
                    )
                    throw KiyoriNetworkException(
                        KiyoriNetworkErrorCode.PROXY_CONNECT_FAILED,
                        "Mihomo could not complete the requested delay test.",
                    )
                }
                val body =
                    response.body
                        ?: throw KiyoriNetworkException(
                            KiyoriNetworkErrorCode.PROXY_CONNECT_FAILED,
                            "Mihomo returned an empty delay-test response.",
                        )
                JSONObject(body.string()).also {
                    proxyLog.info(
                        "Mihomo Controller",
                        "请求完成 operation=$operation runtimeGeneration=${active.runtimeGeneration} " +
                            "httpStatus=${response.code} elapsedMs=${elapsedSince(startedAt)}",
                    )
                }
            }
        } catch (error: KiyoriNetworkException) {
            throw error
        } catch (error: Exception) {
            proxyLog.error(
                "Mihomo Controller",
                "请求异常 operation=$operation runtimeGeneration=${active.runtimeGeneration} " +
                    "type=${error::class.java.simpleName} elapsedMs=${elapsedSince(startedAt)}",
            )
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.PROXY_CONNECT_FAILED,
                "Mihomo could not complete the requested delay test.",
                error,
            )
        }
    }

    private fun applySelectionsLocked(
        active: ActiveRuntime,
        selectedGroupItems: Map<String, String>,
    ) {
        val availableGroups = active.groups.associateBy(MihomoRuntimeGroupState::name)
        val ordered =
            selectedGroupItems.entries.sortedBy { (groupName, _) ->
                groupName == MihomoConfigSanitizer.ROUTE_GROUP_NAME
            }
        ordered.forEach { (groupName, itemName) ->
            val group = availableGroups[groupName] ?: return@forEach
            if (group.type.equals("Selector", ignoreCase = true) ||
                active.groupSummaries.firstOrNull { it.name == groupName }?.manuallySelectable == true
            ) {
                selectGroupLocked(active, groupName, itemName)
            }
        }
    }

    private fun selectGroupLocked(
        active: ActiveRuntime,
        groupName: String,
        itemName: String,
    ) {
        val group = active.groups.firstOrNull { it.name == groupName }
            ?: throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.GROUP_SELECTION_INVALID,
                "The selected Mihomo group is not available.",
            )
        if (itemName.isBlank() || itemName !in group.allItems) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.GROUP_SELECTION_INVALID,
                "The selected Mihomo group item is not available.",
            )
        }
        if (group.currentItem == itemName) return
        val body = JSONObject().put("name", itemName).toString().toRequestBody(JSON_MEDIA_TYPE)
        val request =
            controllerRequest(active, controllerUrl(active, "proxies", groupName))
                .put(body)
                .build()
        try {
            controllerClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw KiyoriNetworkException(
                        KiyoriNetworkErrorCode.GROUP_SELECTION_INVALID,
                        "The local Mihomo controller rejected the selected group item.",
                    )
                }
            }
            proxyLog.info("节点选择", "已将策略组 $groupName 切换为 $itemName")
        } catch (error: KiyoriNetworkException) {
            throw error
        } catch (error: Exception) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.CORE_START_FAILED,
                "The embedded Mihomo controller is unavailable.",
                error,
            )
        }
    }

    private fun controllerUrl(active: ActiveRuntime, vararg pathSegments: String): HttpUrl =
        "http://127.0.0.1:${active.controllerPort}/".toHttpUrl().newBuilder().apply {
            pathSegments.forEach(::addPathSegment)
        }.build()

    private fun controllerRequest(active: ActiveRuntime, url: HttpUrl): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${active.controllerSecret}")

    private fun requireActiveRuntimeLocked(): ActiveRuntime {
        val active =
            activeRuntime
                ?: throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CORE_START_FAILED,
                    "The embedded Mihomo runtime is not running.",
                )
        if (!active.process.isAlive) {
            activeRuntime = null
            clearRuntimeDirectory()
            proxyLog.error(
                "运行时健康",
                "使用前发现核心进程已退出 runtimeGeneration=${active.runtimeGeneration} " +
                    "lastCoreLine=${active.outputCollector.snapshot().lastOrNull() ?: "none"}",
            )
            mutableState.value =
                KiyoriMihomoRuntimeState(
                    phase = KiyoriMihomoRuntimePhase.ERROR,
                    message = "The embedded Mihomo process is no longer running.",
                    runtimeGeneration = active.runtimeGeneration,
                    mixedPort = active.endpoint.port,
                    controllerPort = active.controllerPort,
                    controllerHealthy = false,
                    mixedPortListening = false,
                    startedAtEpochMillis = active.startedAtEpochMillis,
                    failureKind = KiyoriMihomoRuntimeFailureKind.UNEXPECTED_PROCESS_EXIT,
                )
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.CORE_START_FAILED,
                "The embedded Mihomo process is no longer running.",
            )
        }
        return active
    }

    private fun monitorProcess(active: ActiveRuntime) {
        thread(name = "Kiyori-Mihomo-Monitor", isDaemon = true) {
            val exitCode = runCatching { active.process.waitFor() }.getOrNull() ?: return@thread
            kotlinx.coroutines.runBlocking {
                mutex.withLock {
                    if (isCurrentMihomoProcess(activeRuntime?.process, active.process)) {
                        val outcome = classifyMihomoProcessExit(active.expectedStop, exitCode)
                        val detailedMessage =
                            outcome.message +
                                " runtimeGeneration=${active.runtimeGeneration}" +
                                " uptimeMs=${(System.currentTimeMillis() - active.startedAtEpochMillis).coerceAtLeast(0L)}" +
                                " lastCoreLine=${active.outputCollector.snapshot().lastOrNull() ?: "none"}"
                        when (outcome.logLevel) {
                            KiyoriNetworkProxyLogLevel.INFO -> proxyLog.info("主运行时", detailedMessage)
                            KiyoriNetworkProxyLogLevel.WARNING -> proxyLog.warning("主运行时", detailedMessage)
                            KiyoriNetworkProxyLogLevel.ERROR -> proxyLog.error("主运行时", detailedMessage)
                        }
                        activeRuntime = null
                        mutableState.value =
                            KiyoriMihomoRuntimeState(
                                phase = outcome.phase,
                                message = detailedMessage,
                                runtimeGeneration = active.runtimeGeneration,
                                mixedPort = active.endpoint.port,
                                controllerPort = active.controllerPort,
                                controllerHealthy = false,
                                mixedPortListening = false,
                                startedAtEpochMillis = active.startedAtEpochMillis,
                                stopReason = active.stopReason,
                                failureKind = outcome.failureKind,
                            )
                        clearRuntimeDirectory()
                    }
                }
            }
        }
    }

    private fun monitorRuntimeHealth(active: ActiveRuntime) {
        thread(name = "Kiyori-Mihomo-Health", isDaemon = true) {
            var consecutiveFailures = 0
            while (active.process.isAlive) {
                try {
                    Thread.sleep(HEALTH_MONITOR_INTERVAL_MILLIS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@thread
                }
                if (!active.process.isAlive) return@thread

                val health = inspectRuntimeHealth(active)
                var runtimeStopped = false
                kotlinx.coroutines.runBlocking {
                    mutex.withLock {
                        if (!isCurrentMihomoProcess(activeRuntime?.process, active.process) || active.expectedStop) {
                            return@withLock
                        }
                        active.controllerHealthy = health.controllerReady
                        active.mixedPortListening = health.mixedPortListening
                        if (health.controllerReady && health.mixedPortListening) {
                            if (consecutiveFailures > 0) {
                                proxyLog.info(
                                    "运行时健康",
                                    "健康看护恢复 runtimeGeneration=${active.runtimeGeneration} " +
                                        "consecutiveFailures=$consecutiveFailures " +
                                        "controllerStatus=${health.controllerStatus ?: "none"} " +
                                        "elapsedMs=${health.elapsedMillis}",
                                )
                                publishRunningState(active)
                            }
                            consecutiveFailures = 0
                            return@withLock
                        }

                        consecutiveFailures += 1
                        proxyLog.warning(
                            "运行时健康",
                            "健康看护失败 runtimeGeneration=${active.runtimeGeneration} " +
                                "consecutiveFailures=$consecutiveFailures/$HEALTH_FAILURE_THRESHOLD " +
                                "controllerReady=${health.controllerReady} " +
                                "mixedPortListening=${health.mixedPortListening} " +
                                "controllerStatus=${health.controllerStatus ?: "none"} " +
                                "elapsedMs=${health.elapsedMillis}",
                        )
                        publishRunningState(active)
                        if (consecutiveFailures >= HEALTH_FAILURE_THRESHOLD) {
                            runtimeStopped = true
                            stopLocked(
                                finalPhase = KiyoriMihomoRuntimePhase.ERROR,
                                message = "The embedded Mihomo runtime failed its health checks.",
                                reason = "health_check_failed",
                                failureKind = KiyoriMihomoRuntimeFailureKind.HEALTH_CHECK_FAILED,
                            )
                        }
                    }
                }
                if (runtimeStopped) return@thread
            }
        }
    }

    private fun collectProcessOutput(process: Process, source: String): ProcessOutputCollector {
        val captured = ArrayDeque<String>(MAX_CAPTURED_CORE_LINES)
        val lineLock = Any()
        val worker =
            thread(name = "Kiyori-Mihomo-Output", isDaemon = true) {
                runCatching {
                    process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { rawLine ->
                            proxyLog.core(source, rawLine)?.let { redactedLine ->
                                synchronized(lineLock) {
                                    captured.addLast(redactedLine)
                                    while (captured.size > MAX_CAPTURED_CORE_LINES) {
                                        captured.removeFirst()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        return ProcessOutputCollector(worker, captured, lineLock)
    }

    private suspend fun stopLocked(
        finalPhase: KiyoriMihomoRuntimePhase,
        message: String?,
        reason: String,
        failureKind: KiyoriMihomoRuntimeFailureKind? =
            if (finalPhase == KiyoriMihomoRuntimePhase.ERROR) {
                KiyoriMihomoRuntimeFailureKind.START_FAILED
            } else {
                null
            },
    ) {
        val active = activeRuntime
        active?.expectedStop = true
        active?.stopReason = reason
        activeRuntime = null
        var exitCode: Int? = null
        if (active != null) {
            proxyLog.info(
                "主运行时",
                "正在停止内嵌 Mihomo runtimeGeneration=${active.runtimeGeneration} " +
                    "reason=$reason",
            )
            mutableState.value =
                KiyoriMihomoRuntimeState(
                    phase = KiyoriMihomoRuntimePhase.STOPPING,
                    runtimeGeneration = active.runtimeGeneration,
                    mixedPort = active.endpoint.port,
                    controllerPort = active.controllerPort,
                    controllerHealthy = active.controllerHealthy,
                    mixedPortListening = active.mixedPortListening,
                    startedAtEpochMillis = active.startedAtEpochMillis,
                    stopReason = reason,
                )
            exitCode = withContext(Dispatchers.IO) { stopProcessBlocking(active.process) }
            proxyLog.info(
                "主运行时",
                "内嵌 Mihomo 停止完成 runtimeGeneration=${active.runtimeGeneration} " +
                    "reason=$reason exitCode=$exitCode lastCoreLine=${active.outputCollector.snapshot().lastOrNull() ?: "none"}",
            )
        }
        withContext(Dispatchers.IO) { clearRuntimeDirectory() }
        mutableState.value =
            KiyoriMihomoRuntimeState(
                phase = finalPhase,
                message = message,
                runtimeGeneration = active?.runtimeGeneration,
                mixedPort = active?.endpoint?.port,
                controllerPort = active?.controllerPort,
                controllerHealthy = active?.controllerHealthy,
                mixedPortListening = active?.mixedPortListening,
                startedAtEpochMillis = active?.startedAtEpochMillis,
                stopReason = reason,
                failureKind = failureKind,
            )
        if (active != null && finalPhase == KiyoriMihomoRuntimePhase.STOPPED) {
            proxyLog.info(
                "主运行时",
                "内嵌 Mihomo 已停止 runtimeGeneration=${active.runtimeGeneration} " +
                    "reason=$reason exitCode=${exitCode ?: "none"}",
            )
        }
    }

    private fun resetDirectory(directory: File, label: String) {
        clearDirectory(directory, label)
        if (!directory.mkdirs() && !directory.isDirectory) {
            throw IllegalStateException("Unable to create the private Mihomo $label directory")
        }
        restrictToOwner(directory)
    }

    private fun clearRuntimeDirectory() {
        clearDirectory(runtimeDirectory, "runtime")
    }

    private fun clearDirectory(directory: File, label: String) {
        if (directory.exists() && !directory.deleteRecursively()) {
            KiyoriLogger.w(TAG, "Unable to clear the private Mihomo $label directory")
        }
    }

    private fun stopProcessBlocking(process: Process): Int {
        process.destroy()
        if (!process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        return runCatching { process.exitValue() }.getOrDefault(-1)
    }

    private fun elapsedSince(startedAtEpochMillis: Long): Long =
        (System.currentTimeMillis() - startedAtEpochMillis).coerceAtLeast(0L)

    private fun restrictToOwner(file: File) {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        if (file.isDirectory) file.setExecutable(true, true)
    }

    private fun allocateLoopbackPort(): Int =
        ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { it.localPort }

    private fun randomHex(byteCount: Int): String =
        ByteArray(byteCount)
            .also(random::nextBytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun sha256(value: String): String = sha256Utf8 { append(value) }

    private val CONTROLLER_GROUP_TYPES =
        setOf("Selector", "URLTest", "Fallback", "LoadBalance")
    private val CONTROLLER_BUILTIN_OUTBOUNDS =
        setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE", "GLOBAL")
}
