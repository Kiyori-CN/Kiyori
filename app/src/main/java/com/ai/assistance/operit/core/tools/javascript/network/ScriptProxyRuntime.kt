package com.ai.assistance.operit.core.tools.javascript.network

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

enum class ScriptProxyRuntimePhase {
    STOPPED,
    STARTING,
    AWAITING_SELECTION,
    RUNNING,
    STOPPING,
    ERROR,
}

data class ScriptProxyRuntimeState(
    val phase: ScriptProxyRuntimePhase = ScriptProxyRuntimePhase.STOPPED,
    val availableProxyNames: List<String> = emptyList(),
    val selectedProxyName: String? = null,
    val message: String? = null,
)

data class ScriptProxyEndpoint(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
)

class ScriptProxyRuntime private constructor(context: Context) {
    companion object {
        private const val TAG = "ScriptProxyRuntime"
        private const val CORE_FILE_NAME = "libkiyori_mihomo.so"
        private const val LAUNCHER_FILE_NAME = "libkiyori_mihomo_launcher.so"
        private const val RUNTIME_DIRECTORY = "script_network/runtime"
        private const val CONFIG_FILE_NAME = "config.yaml"
        private const val READINESS_TIMEOUT_MILLIS = 12_000L
        private const val TEST_TIMEOUT_SECONDS = 20L
        private const val STOP_TIMEOUT_SECONDS = 3L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @Volatile
        private var instance: ScriptProxyRuntime? = null

        fun getInstance(context: Context): ScriptProxyRuntime =
            instance
                ?: synchronized(this) {
                    instance
                        ?: ScriptProxyRuntime(context.applicationContext).also { instance = it }
                }
    }

    private data class ActiveRuntime(
        val process: Process,
        val fingerprint: String,
        val endpoint: ScriptProxyEndpoint,
        val controllerPort: Int,
        val controllerSecret: String,
        var availableProxyNames: List<String>,
        var selectedProxyName: String?,
    )

    private val appContext = context.applicationContext
    private val runtimeDirectory = appContext.noBackupFilesDir.resolve(RUNTIME_DIRECTORY)
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startupCleanup =
        cleanupScope.async(start = CoroutineStart.LAZY) {
            mutex.withLock { clearRuntimeDirectory() }
        }
    private val random by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SecureRandom() }
    private val controllerClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    private val mutableState = MutableStateFlow(ScriptProxyRuntimeState())
    val state: StateFlow<ScriptProxyRuntimeState> = mutableState.asStateFlow()

    private var activeRuntime: ActiveRuntime? = null

    fun scheduleStaleRuntimeCleanup() {
        startupCleanup.start()
    }

    suspend fun ensureReady(config: ScriptEmbeddedProxyConfig): ScriptProxyEndpoint {
        awaitStartupCleanup()
        return mutex.withLock {
            val selected = config.selectedProxyName.trim()
            if (selected.isEmpty()) {
                throw ScriptNetworkException(
                    ScriptNetworkErrorCode.NODE_NOT_SELECTED,
                    "Select an embedded proxy node before running this script.",
                )
            }
            val active = startOrReuseLocked(config)
            selectProxyLocked(active, selected)
            mutableState.value =
                ScriptProxyRuntimeState(
                    phase = ScriptProxyRuntimePhase.RUNNING,
                    availableProxyNames = active.availableProxyNames,
                    selectedProxyName = selected,
                )
            active.endpoint
        }
    }

    suspend fun discoverProxyNames(config: ScriptEmbeddedProxyConfig): List<String> {
        awaitStartupCleanup()
        return mutex.withLock {
            val active = startOrReuseLocked(config)
            if (config.selectedProxyName.isNotBlank()) {
                selectProxyLocked(active, config.selectedProxyName.trim())
                mutableState.value =
                    ScriptProxyRuntimeState(
                        phase = ScriptProxyRuntimePhase.RUNNING,
                        availableProxyNames = active.availableProxyNames,
                        selectedProxyName = active.selectedProxyName,
                    )
            } else {
                mutableState.value =
                    ScriptProxyRuntimeState(
                        phase = ScriptProxyRuntimePhase.AWAITING_SELECTION,
                        availableProxyNames = active.availableProxyNames,
                        message = "Select a proxy node before scripts can use the embedded runtime.",
                    )
            }
            active.availableProxyNames
        }
    }

    suspend fun selectProxy(proxyName: String): ScriptProxyEndpoint {
        awaitStartupCleanup()
        return mutex.withLock {
            val active = activeRuntime
                ?: throw ScriptNetworkException(
                    ScriptNetworkErrorCode.CORE_START_FAILED,
                    "The embedded proxy runtime is not running.",
                )
            if (!active.process.isAlive) {
                activeRuntime = null
                clearRuntimeDirectory()
                mutableState.value =
                    ScriptProxyRuntimeState(
                        phase = ScriptProxyRuntimePhase.ERROR,
                        message = "The embedded proxy process is no longer running.",
                    )
                throw ScriptNetworkException(
                    ScriptNetworkErrorCode.CORE_START_FAILED,
                    "The embedded proxy process is no longer running.",
                )
            }
            selectProxyLocked(active, proxyName.trim())
            mutableState.value =
                ScriptProxyRuntimeState(
                    phase = ScriptProxyRuntimePhase.RUNNING,
                    availableProxyNames = active.availableProxyNames,
                    selectedProxyName = active.selectedProxyName,
                )
            active.endpoint
        }
    }

    suspend fun stop() {
        awaitStartupCleanup()
        mutex.withLock { stopLocked(ScriptProxyRuntimePhase.STOPPED, null) }
    }

    private suspend fun awaitStartupCleanup() {
        startupCleanup.start()
        startupCleanup.await()
    }

    private suspend fun startOrReuseLocked(config: ScriptEmbeddedProxyConfig): ActiveRuntime {
        if (config.sanitizedYaml.isBlank()) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CONFIG_MISSING,
                "No embedded subscription has been imported.",
            )
        }
        val fingerprint = sha256(config.sanitizedYaml)
        activeRuntime?.let { active ->
            if (active.process.isAlive && active.fingerprint == fingerprint) {
                return active
            }
            stopLocked(ScriptProxyRuntimePhase.STOPPED, null)
        }

        mutableState.value = ScriptProxyRuntimeState(phase = ScriptProxyRuntimePhase.STARTING)
        return try {
            withContext(Dispatchers.IO) { startProcess(config, fingerprint) }
        } catch (error: ScriptNetworkException) {
            stopLocked(ScriptProxyRuntimePhase.ERROR, error.message)
            throw error
        } catch (error: Exception) {
            AppLogger.e(TAG, "Unable to start the embedded proxy runtime", error)
            val wrapped =
                ScriptNetworkException(
                    ScriptNetworkErrorCode.CORE_START_FAILED,
                    "The embedded proxy runtime could not be started.",
                    error,
                )
            stopLocked(ScriptProxyRuntimePhase.ERROR, wrapped.message)
            throw wrapped
        }
    }

    private fun startProcess(
        config: ScriptEmbeddedProxyConfig,
        fingerprint: String,
    ): ActiveRuntime {
        val nativeDirectory = File(appContext.applicationInfo.nativeLibraryDir)
        val core = nativeDirectory.resolve(CORE_FILE_NAME)
        val launcher = nativeDirectory.resolve(LAUNCHER_FILE_NAME)
        if (!core.isFile || !launcher.isFile) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CORE_MISSING,
                "The embedded Mihomo runtime is missing from this APK.",
            )
        }

        resetRuntimeDirectory()
        val mixedPort = allocateLoopbackPort()
        var controllerPort = allocateLoopbackPort()
        while (controllerPort == mixedPort) controllerPort = allocateLoopbackPort()
        val username = randomHex(18)
        val password = randomHex(24)
        val secret = randomHex(32)
        val runtimeConfig =
            MihomoConfigSanitizer.buildRuntimeConfig(
                sanitizedYaml = config.sanitizedYaml,
                mixedPort = mixedPort,
                controllerPort = controllerPort,
                proxyUsername = username,
                proxyPassword = password,
                controllerSecret = secret,
            )
        val configFile = runtimeDirectory.resolve(CONFIG_FILE_NAME)
        configFile.writeText(runtimeConfig.yaml, Charsets.UTF_8)
        restrictToOwner(configFile)

        testConfiguration(launcher, core, configFile)
        val process = startMihomoProcess(launcher, core, configFile, controllerPort, secret)
        drainProcessOutput(process)
        val active =
            ActiveRuntime(
                process = process,
                fingerprint = fingerprint,
                endpoint =
                    ScriptProxyEndpoint(
                        host = "127.0.0.1",
                        port = mixedPort,
                        username = username,
                        password = password,
                    ),
                controllerPort = controllerPort,
                controllerSecret = secret,
                availableProxyNames = emptyList(),
                selectedProxyName = null,
            )
        activeRuntime = active
        monitorProcess(active)

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
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CORE_START_FAILED,
                "The embedded proxy controller did not become ready in time.",
            )
        }

        active.availableProxyNames = fetchProxyNames(active)
        if (active.availableProxyNames.isEmpty()) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CONFIG_INVALID,
                "The embedded subscription exposed no selectable proxy nodes.",
            )
        }
        if (configFile.exists() && !configFile.delete()) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CORE_START_FAILED,
                "The embedded proxy configuration could not be removed after startup.",
            )
        }
        mutableState.value =
            ScriptProxyRuntimeState(
                phase = ScriptProxyRuntimePhase.AWAITING_SELECTION,
                availableProxyNames = active.availableProxyNames,
                message = "Select a proxy node before scripts can use the embedded runtime.",
            )
        return active
    }

    private fun testConfiguration(launcher: File, core: File, configFile: File) {
        val process =
            processBuilder(
                launcher,
                core,
                "-t",
                "-d",
                runtimeDirectory.absolutePath,
                "-f",
                configFile.absolutePath,
            ).start()
        drainProcessOutput(process)
        if (!process.waitFor(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CONFIG_INVALID,
                "Mihomo configuration validation timed out.",
            )
        }
        if (process.exitValue() != 0) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CONFIG_INVALID,
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
    ): Process =
        processBuilder(
            launcher,
            core,
            "-d",
            runtimeDirectory.absolutePath,
            "-f",
            configFile.absolutePath,
            "-ext-ctl",
            "127.0.0.1:$controllerPort",
            "-secret",
            secret,
        ).start()

    private fun processBuilder(launcher: File, core: File, vararg arguments: String): ProcessBuilder =
        ProcessBuilder(
            buildList {
                add(launcher.absolutePath)
                add(core.absolutePath)
                addAll(arguments)
            },
        )
            .directory(runtimeDirectory)
            .redirectErrorStream(true)
            .also { builder ->
                builder.environment().keys
                    .filter { key -> key.startsWith("CLASH_") }
                    .forEach(builder.environment()::remove)
            }

    private fun controllerVersionIsReady(active: ActiveRuntime): Boolean {
        val request = controllerRequest(active, "/version").get().build()
        return runCatching {
            controllerClient.newCall(request).execute().use { response -> response.isSuccessful }
        }.getOrDefault(false)
    }

    private fun fetchProxyNames(active: ActiveRuntime): List<String> {
        val request =
            controllerRequest(active, "/proxies/${MihomoConfigSanitizer.ROUTE_GROUP_NAME}")
                .get()
                .build()
        controllerClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ScriptNetworkException(
                    ScriptNetworkErrorCode.CORE_START_FAILED,
                    "Unable to read embedded proxy nodes from the local controller.",
                )
            }
            val body = response.body
                ?: throw ScriptNetworkException(
                    ScriptNetworkErrorCode.CORE_START_FAILED,
                    "The local proxy controller returned an empty response.",
                )
            val root = JSONObject(body.string())
            val all = root.optJSONArray("all")
                ?: throw ScriptNetworkException(
                    ScriptNetworkErrorCode.CONFIG_INVALID,
                    "The embedded route group has no selectable nodes.",
                )
            return buildList {
                repeat(all.length()) { index ->
                    all.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
                }
            }.distinct()
        }
    }

    private fun selectProxyLocked(active: ActiveRuntime, proxyName: String) {
        if (proxyName.isBlank() || proxyName !in active.availableProxyNames) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.NODE_NOT_SELECTED,
                "The selected embedded proxy node is not available.",
            )
        }
        if (active.selectedProxyName == proxyName) return
        val body = JSONObject().put("name", proxyName).toString().toRequestBody(JSON_MEDIA_TYPE)
        val request =
            controllerRequest(active, "/proxies/${MihomoConfigSanitizer.ROUTE_GROUP_NAME}")
                .put(body)
                .build()
        try {
            controllerClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ScriptNetworkException(
                        ScriptNetworkErrorCode.NODE_NOT_SELECTED,
                        "The local controller rejected the selected embedded proxy node.",
                    )
                }
            }
        } catch (error: ScriptNetworkException) {
            throw error
        } catch (error: Exception) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CORE_START_FAILED,
                "The embedded proxy controller is unavailable.",
                error,
            )
        }
        active.selectedProxyName = proxyName
    }

    private fun controllerRequest(active: ActiveRuntime, path: String): Request.Builder =
        Request.Builder()
            .url("http://127.0.0.1:${active.controllerPort}$path")
            .header("Authorization", "Bearer ${active.controllerSecret}")

    private fun monitorProcess(active: ActiveRuntime) {
        thread(name = "Kiyori-Mihomo-Monitor", isDaemon = true) {
            val exitCode = runCatching { active.process.waitFor() }.getOrNull() ?: return@thread
            kotlinx.coroutines.runBlocking {
                mutex.withLock {
                    if (activeRuntime?.process === active.process) {
                        activeRuntime = null
                        mutableState.value =
                            ScriptProxyRuntimeState(
                                phase = ScriptProxyRuntimePhase.ERROR,
                                message = "Embedded proxy process exited with code $exitCode.",
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
                    lines.forEach { /* Drain without persisting subscription or credential material. */ }
                }
            }
        }
    }

    private suspend fun stopLocked(
        finalPhase: ScriptProxyRuntimePhase,
        message: String?,
    ) {
        val active = activeRuntime
        activeRuntime = null
        if (active != null) {
            mutableState.value = ScriptProxyRuntimeState(phase = ScriptProxyRuntimePhase.STOPPING)
            withContext(Dispatchers.IO) {
                active.process.destroy()
                if (!active.process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    active.process.destroyForcibly()
                    active.process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
            }
        }
        clearRuntimeDirectory()
        mutableState.value = ScriptProxyRuntimeState(phase = finalPhase, message = message)
    }

    private fun resetRuntimeDirectory() {
        clearRuntimeDirectory()
        if (!runtimeDirectory.mkdirs() && !runtimeDirectory.isDirectory) {
            throw IllegalStateException("Unable to create the private script proxy runtime directory")
        }
        restrictToOwner(runtimeDirectory)
    }

    private fun clearRuntimeDirectory() {
        if (runtimeDirectory.exists() && !runtimeDirectory.deleteRecursively()) {
            AppLogger.w(TAG, "Unable to clear the private script proxy runtime directory")
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
}
