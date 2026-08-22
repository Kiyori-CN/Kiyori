package com.kiyori.platform.network

import android.content.Context
import com.kiyori.platform.android.KiyoriProcessIdentity
import com.kiyori.platform.logging.KiyoriLogger
import java.io.File
import java.net.Proxy
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
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
)

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
        private const val DELAY_TEST_TIMEOUT_MILLIS = 8_000
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
    )

    private val appContext = context.applicationContext
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
    private val controllerClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }
    private val mutableState = MutableStateFlow(KiyoriMihomoRuntimeState())
    val state: StateFlow<KiyoriMihomoRuntimeState> = mutableState.asStateFlow()
    private val mutableProbeState = MutableStateFlow(KiyoriMihomoProbeState())
    val probeState: StateFlow<KiyoriMihomoProbeState> = mutableProbeState.asStateFlow()

    private var activeRuntime: ActiveRuntime? = null

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
                } finally {
                    clearDirectory(validationDirectory, "validation")
                }
            }
        }
    }

    suspend fun ensureReady(
        config: KiyoriProxySubscription,
        testUrl: String,
    ): KiyoriProxyEndpoint {
        awaitStartupCleanup()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val active = startOrReuseLocked(config, testUrl)
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
                val active =
                    withContext(Dispatchers.IO) {
                        startProcess(
                            config = config,
                            testUrl = testUrl,
                            fingerprint = sha256(config.id + '\u0000' + config.sanitizedYaml + '\u0000' + testUrl),
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
                withContext(Dispatchers.IO) { block(active) }
            } catch (error: KiyoriNetworkException) {
                mutableProbeState.value =
                    KiyoriMihomoProbeState(
                        phase = KiyoriMihomoProbePhase.ERROR,
                        subscriptionId = config.id,
                        message = error.message,
                    )
                throw error
            } catch (error: Exception) {
                KiyoriLogger.e(TAG, "Unable to run the isolated Mihomo subscription probe", error)
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

    suspend fun stop() {
        awaitStartupCleanup()
        mutex.withLock { stopLocked(KiyoriMihomoRuntimePhase.STOPPED, null) }
    }

    private suspend fun awaitStartupCleanup() {
        startupCleanup.start()
        startupCleanup.await()
    }

    private suspend fun startOrReuseLocked(
        config: KiyoriProxySubscription,
        testUrl: String,
    ): ActiveRuntime {
        if (config.sanitizedYaml.isBlank()) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.CONFIG_MISSING,
                "No Clash or Mihomo subscription has been imported.",
            )
        }
        val fingerprint = sha256(config.id + '\u0000' + config.sanitizedYaml + '\u0000' + testUrl)
        activeRuntime?.let { active ->
            if (active.process.isAlive && active.fingerprint == fingerprint) return active
            stopLocked(KiyoriMihomoRuntimePhase.STOPPED, null)
        }

        mutableState.value = KiyoriMihomoRuntimeState(phase = KiyoriMihomoRuntimePhase.STARTING)
        return try {
            val active =
                withContext(Dispatchers.IO) {
                    startProcess(config, testUrl, fingerprint, runtimeDirectory, "runtime")
                }
            activeRuntime = active
            monitorProcess(active)
            publishRunningState(active)
            active
        } catch (error: KiyoriNetworkException) {
            stopLocked(KiyoriMihomoRuntimePhase.ERROR, error.message)
            throw error
        } catch (error: Exception) {
            KiyoriLogger.e(TAG, "Unable to start the embedded Mihomo runtime", error)
            val wrapped =
                KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CORE_START_FAILED,
                    "The embedded Mihomo runtime could not be started.",
                    error,
                )
            stopLocked(KiyoriMihomoRuntimePhase.ERROR, wrapped.message)
            throw wrapped
        }
    }

    private fun startProcess(
        config: KiyoriProxySubscription,
        testUrl: String,
        fingerprint: String,
        workDirectory: File,
        directoryLabel: String,
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
            )
        val configFile = workDirectory.resolve(CONFIG_FILE_NAME)
        configFile.writeText(runtimeConfig.yaml, Charsets.UTF_8)
        restrictToOwner(configFile)

        testConfiguration(launcher, core, configFile, workDirectory)
        val process =
            startMihomoProcess(
                launcher = launcher,
                core = core,
                configFile = configFile,
                controllerPort = controllerPort,
                secret = secret,
                workDirectory = workDirectory,
            )
        drainProcessOutput(process)
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
            if (configFile.exists() && !configFile.delete()) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CORE_START_FAILED,
                    "The embedded proxy configuration could not be removed after startup.",
                )
            }
            return active
        } catch (error: Exception) {
            stopProcessBlocking(process)
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
        drainProcessOutput(process)
        if (!process.waitFor(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.CONFIG_INVALID,
                "Mihomo configuration validation timed out.",
            )
        }
        if (process.exitValue() != 0) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.CONFIG_INVALID,
                "Mihomo rejected the sanitized subscription.",
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

    private fun controllerVersionIsReady(active: ActiveRuntime): Boolean {
        val request = controllerRequest(active, controllerUrl(active, "version")).get().build()
        return runCatching {
            controllerClient.newCall(request).execute().use { response -> response.isSuccessful }
        }.getOrDefault(false)
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
            )
    }

    private fun testNodeDelay(
        active: ActiveRuntime,
        nodeName: String,
        testUrl: String,
    ): Int {
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
        return delay
    }

    private fun testGroupDelays(
        active: ActiveRuntime,
        groupName: String,
        testUrl: String,
    ): Map<String, Int> {
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
        val url =
            controllerUrl(active, *pathSegments.toTypedArray())
                .newBuilder()
                .addQueryParameter("url", testUrl)
                .addQueryParameter("timeout", DELAY_TEST_TIMEOUT_MILLIS.toString())
                .build()
        val request = controllerRequest(active, url).get().build()
        controllerClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
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
            return JSONObject(body.string())
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
            mutableState.value =
                KiyoriMihomoRuntimeState(
                    phase = KiyoriMihomoRuntimePhase.ERROR,
                    message = "The embedded Mihomo process is no longer running.",
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
                    if (activeRuntime?.process === active.process) {
                        activeRuntime = null
                        mutableState.value =
                            KiyoriMihomoRuntimeState(
                                phase = KiyoriMihomoRuntimePhase.ERROR,
                                message = "Embedded Mihomo process exited with code $exitCode.",
                            )
                        clearRuntimeDirectory()
                    }
                }
            }
        }
    }

    private fun drainProcessOutput(process: Process) {
        thread(name = "Kiyori-Mihomo-Output", isDaemon = true) {
            runCatching {
                process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { /* Drain without persisting subscription or credential data. */ }
                }
            }
        }
    }

    private suspend fun stopLocked(
        finalPhase: KiyoriMihomoRuntimePhase,
        message: String?,
    ) {
        val active = activeRuntime
        activeRuntime = null
        if (active != null) {
            mutableState.value =
                KiyoriMihomoRuntimeState(phase = KiyoriMihomoRuntimePhase.STOPPING)
            withContext(Dispatchers.IO) { stopProcessBlocking(active.process) }
        }
        withContext(Dispatchers.IO) { clearRuntimeDirectory() }
        mutableState.value = KiyoriMihomoRuntimeState(phase = finalPhase, message = message)
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

    private fun stopProcessBlocking(process: Process) {
        process.destroy()
        if (!process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

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

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private val CONTROLLER_GROUP_TYPES =
        setOf("Selector", "URLTest", "Fallback", "LoadBalance")
    private val CONTROLLER_BUILTIN_OUTBOUNDS =
        setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE", "GLOBAL")
}
