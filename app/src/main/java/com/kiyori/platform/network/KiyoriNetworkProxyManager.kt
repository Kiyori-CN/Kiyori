package com.kiyori.platform.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.kiyori.platform.android.ApplicationContextAccess
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URL
import java.net.URLConnection
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

internal enum class KiyoriNetworkProxyReadiness {
    NOT_STARTED,
    RECONCILING,
    READY,
    FAILED,
}

internal fun shouldStartStartupProxyReconciliation(
    startupScheduled: Boolean,
    browserWebViewRuntimeReady: Boolean,
    startupAlreadyLaunched: Boolean,
): Boolean =
    startupScheduled && browserWebViewRuntimeReady && !startupAlreadyLaunched

internal fun shouldAutoRecoverMihomoFailure(
    state: KiyoriMihomoRuntimeState,
    handledGeneration: Long?,
): Boolean {
    val generation = state.runtimeGeneration ?: return false
    return state.phase == KiyoriMihomoRuntimePhase.ERROR &&
        state.failureKind in
            setOf(
                KiyoriMihomoRuntimeFailureKind.UNEXPECTED_PROCESS_EXIT,
                KiyoriMihomoRuntimeFailureKind.HEALTH_CHECK_FAILED,
            ) &&
        generation > 0L &&
        generation != handledGeneration
}

internal fun shouldFailReadinessAfterMihomoRecoveryLimit(
    requiresEmbeddedProxy: Boolean,
    attemptsInWindow: Int,
    recoveryLimit: Int,
): Boolean =
    requiresEmbeddedProxy && attemptsInWindow >= recoveryLimit

internal fun shouldApplyReadinessCompletion(
    completionGeneration: Long,
    currentGeneration: Long,
    currentState: KiyoriNetworkProxyReadiness,
): Boolean =
    completionGeneration == currentGeneration &&
        currentState == KiyoriNetworkProxyReadiness.RECONCILING

class KiyoriNetworkProxyManager private constructor(context: Context) {
    companion object {
        private const val AUTOMATIC_RECOVERY_WINDOW_MILLIS = 5 * 60 * 1000L
        private const val MAX_AUTOMATIC_RECOVERY_ATTEMPTS_PER_WINDOW = 2
        private const val WEBVIEW_PROXY_OPERATION_TIMEOUT_MILLIS = 5_000L

        @Volatile
        private var instance: KiyoriNetworkProxyManager? = null

        fun getInstance(context: Context): KiyoriNetworkProxyManager =
            instance
                ?: synchronized(this) {
                    instance
                        ?: KiyoriNetworkProxyManager(context.applicationContext).also {
                            instance = it
                        }
                }
    }

    private val appContext = context.applicationContext
    private val configStore = KiyoriNetworkProxyConfigStore.getInstance(appContext)
    private val runtime = KiyoriMihomoRuntime.getInstance(appContext)
    private val proxyLog = KiyoriNetworkProxyLogStore
    private val subscriptionClient = MihomoSubscriptionClient()
    private val mutationMutex = Mutex()
    private val nodeTestMutex = Mutex()
    private val runtimeRecoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val readinessLock = Any()
    @Volatile private var readinessState = KiyoriNetworkProxyReadiness.NOT_STARTED
    @Volatile private var readinessGeneration = 0L
    @Volatile private var readinessFailure: Throwable? = null
    @Volatile private var readinessAttempt: CompletableDeferred<Result<Unit>>? = null
    @Volatile private var appliedWebViewProxyFingerprint: String? = null
    private val startupReconciliationScheduled = AtomicBoolean(false)
    private val startupReconciliationLaunched = AtomicBoolean(false)
    @Volatile private var browserWebViewRuntimeReady = false
    @Volatile private var browserSiteProxyDisabledProvider: ((String) -> Boolean)? = null
    @Volatile private var browserSiteProxyDisabledDomainsProvider: (() -> Set<String>)? = null
    private var lastHandledUnexpectedRuntimeGeneration: Long? = null
    private var automaticRecoveryWindowStartedAtEpochMillis = 0L
    private var automaticRecoveryAttemptsInWindow = 0

    val configState: StateFlow<KiyoriNetworkProxyStoreState> = configStore.state
    val runtimeState: StateFlow<KiyoriMihomoRuntimeState> = runtime.state
    val probeState: StateFlow<KiyoriMihomoProbeState> = runtime.probeState
    val logEntries: StateFlow<List<KiyoriNetworkProxyLogEntry>> = proxyLog.entries

    init {
        observeUnexpectedRuntimeExit()
        observeSystemNetworks()
    }

    private fun observeSystemNetworks() {
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
        val changes = Channel<Unit>(Channel.CONFLATED)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { changes.trySend(Unit) }
            override fun onLost(network: Network) { changes.trySend(Unit) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { changes.trySend(Unit) }
        }
        runtimeRecoveryScope.launch {
            var previous: String? = null
            for (ignored in changes) {
                try {
                    val signature = connectivity.allNetworks.map { network ->
                        "$network:${connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)}"
                    }.sorted().joinToString("|")
                    if (signature == previous) continue
                    previous = signature
                    // 网络事件不取消正在保存的事务；只合并待处理通知，在现有锁中重读最新配置。
                    mutationMutex.withLock {
                        val config = currentConfig()
                        if (config.enabled) reconcileEnabledStateLocked(config)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    proxyLog.error("网络变化", "重新协调失败 type=${error::class.java.simpleName}")
                }
            }
        }
        val request = NetworkRequest.Builder().apply {
            // clearCapabilities 自 API 30 才公开。旧系统移除其三个默认约束，
            // 同样观察所有网络及 VPN，避免 Android 8–10 启动触发 NoSuchMethodError。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                clearCapabilities()
            } else {
                removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            }
        }.build()
        connectivity.registerNetworkCallback(
            request,
            callback,
        )
    }

    fun exportLogText(): String = proxyLog.exportText()

    fun clearLog() = proxyLog.clear()

    fun scheduleStartupReconciliation() {
        if (!startupReconciliationScheduled.compareAndSet(false, true)) return
        runtime.scheduleStaleRuntimeCleanup()
        beginReadinessAttempt()
        startStartupReconciliationIfReady()
    }

    /**
     * ProxyController is process-wide but its provider is initialized by the first real WebView.
     * The Application cannot install an override before that point: Mihomo may be healthy while
     * the provider still rejects the override call. Browser creation is the single readiness
     * signal, so startup reconciliation begins only after that WebView exists.
     */
    internal fun notifyBrowserWebViewRuntimeReady() {
        if (browserWebViewRuntimeReady) return
        browserWebViewRuntimeReady = true
        beginReadinessAttempt()
        startStartupReconciliationIfReady()
    }

    private fun startStartupReconciliationIfReady() {
        if (
            !shouldStartStartupProxyReconciliation(
                startupScheduled = startupReconciliationScheduled.get(),
                browserWebViewRuntimeReady = browserWebViewRuntimeReady,
                startupAlreadyLaunched = startupReconciliationLaunched.get(),
            )
        ) {
            return
        }
        if (!startupReconciliationLaunched.compareAndSet(false, true)) return
        runtimeRecoveryScope.launch {
            try {
                reconcileEnabledState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                proxyLog.error(
                    "运行协调",
                    "应用启动代理协调失败 type=${error::class.java.simpleName} " +
                        "message=${error.message?.take(240) ?: "none"}",
                )
            }
        }
    }

    internal fun startupProxyReadiness(): KiyoriNetworkProxyReadiness = readinessState

    internal fun startupProxyReadinessGeneration(): Long = readinessGeneration

    internal fun setBrowserSiteProxyPolicy(
        disabledHostProvider: (String) -> Boolean,
        disabledDomainsProvider: () -> Set<String>,
    ) {
        browserSiteProxyDisabledProvider = disabledHostProvider
        browserSiteProxyDisabledDomainsProvider = disabledDomainsProvider
    }

    internal fun isBrowserSiteProxyDisabled(host: String): Boolean =
        browserSiteProxyDisabledProvider?.invoke(host.trim().lowercase()) == true

    internal suspend fun awaitStartupReconciliation() {
        scheduleStartupReconciliation()
        requireNotNull(readinessAttempt).await().getOrThrow()
    }

    private fun beginReadinessAttempt(): Long = synchronized(readinessLock) {
        if (readinessState == KiyoriNetworkProxyReadiness.RECONCILING) {
            return@synchronized readinessGeneration
        }
        readinessGeneration += 1L
        readinessState = KiyoriNetworkProxyReadiness.RECONCILING
        readinessFailure = null
        CompletableDeferred<Result<Unit>>().also { readinessAttempt = it }
        readinessGeneration
    }

    private fun invalidateReadinessForRuntimeFailure(): Long = synchronized(readinessLock) {
        readinessAttempt?.complete(
            Result.failure(
                KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CORE_START_FAILED,
                    "The embedded Mihomo runtime became unavailable while proxy readiness was being reconciled.",
                ),
            ),
        )
        readinessGeneration += 1L
        readinessState = KiyoriNetworkProxyReadiness.RECONCILING
        readinessFailure = null
        readinessAttempt = CompletableDeferred()
        readinessGeneration
    }

    private fun completeReadinessSuccess(completionGeneration: Long) {
        synchronized(readinessLock) {
            if (
                !shouldApplyReadinessCompletion(
                    completionGeneration = completionGeneration,
                    currentGeneration = readinessGeneration,
                    currentState = readinessState,
                )
            ) {
                return
            }
            readinessState = KiyoriNetworkProxyReadiness.READY
            readinessFailure = null
            readinessAttempt?.complete(Result.success(Unit))
        }
    }

    private fun completeReadinessFailure(completionGeneration: Long, error: Throwable) {
        synchronized(readinessLock) {
            if (
                !shouldApplyReadinessCompletion(
                    completionGeneration = completionGeneration,
                    currentGeneration = readinessGeneration,
                    currentState = readinessState,
                )
            ) {
                return
            }
            readinessState = KiyoriNetworkProxyReadiness.FAILED
            readinessFailure = error
            readinessAttempt?.complete(Result.failure(error))
        }
    }

    private fun observeUnexpectedRuntimeExit() {
        runtimeRecoveryScope.launch {
            runtime.state.collect { state ->
                val generation = state.runtimeGeneration ?: return@collect
                if (!shouldAutoRecoverMihomoFailure(state, lastHandledUnexpectedRuntimeGeneration)) {
                    return@collect
                }
                // The restarted core may receive the same loopback port. Do not let a cached
                // process-wide override make WebView keep trusting an endpoint whose owner died.
                appliedWebViewProxyFingerprint = null
                if (KiyoriNetworkProxyPolicy.requiresEmbeddedProxy(currentConfig())) {
                    // A runtime failure invalidates the previously published WebView endpoint
                    // before recovery starts. Otherwise a new navigation can escape through a
                    // dead mixed port while the recovery coroutine is still coordinating.
                    invalidateReadinessForRuntimeFailure()
                }
                lastHandledUnexpectedRuntimeGeneration = generation
                recoverMihomoRuntimeFailure(state)
            }
        }
    }

    private suspend fun recoverMihomoRuntimeFailure(
        failedState: KiyoriMihomoRuntimeState,
    ) {
        val now = System.currentTimeMillis()
        if (now - automaticRecoveryWindowStartedAtEpochMillis >= AUTOMATIC_RECOVERY_WINDOW_MILLIS) {
            automaticRecoveryWindowStartedAtEpochMillis = now
            automaticRecoveryAttemptsInWindow = 0
        }
        val proxyRequired = KiyoriNetworkProxyPolicy.requiresEmbeddedProxy(currentConfig())
        val recoveryReadinessGeneration = beginReadinessAttempt()
        if (!proxyRequired) {
            proxyLog.info(
                "运行恢复",
                "当前配置已不需要内嵌代理，跳过恢复限额判定 failedGeneration=${failedState.runtimeGeneration}",
            )
            completeReadinessSuccess(recoveryReadinessGeneration)
            return
        }
        if (
            shouldFailReadinessAfterMihomoRecoveryLimit(
                requiresEmbeddedProxy = proxyRequired,
                attemptsInWindow = automaticRecoveryAttemptsInWindow,
                recoveryLimit = MAX_AUTOMATIC_RECOVERY_ATTEMPTS_PER_WINDOW,
            )
        ) {
            proxyLog.warning(
                "运行恢复",
                "自动恢复限额已达到，保持 ERROR failedGeneration=${failedState.runtimeGeneration} " +
                    "attempts=$automaticRecoveryAttemptsInWindow windowMs=$AUTOMATIC_RECOVERY_WINDOW_MILLIS",
            )
            completeReadinessFailure(
                recoveryReadinessGeneration,
                KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CORE_START_FAILED,
                    "The embedded Mihomo runtime recovery limit was reached.",
                ),
            )
            return
        }

        automaticRecoveryAttemptsInWindow += 1
        proxyLog.warning(
            "运行恢复",
            "检测到 Mihomo runtime 故障，开始第 $automaticRecoveryAttemptsInWindow 次自动恢复 " +
                "failedGeneration=${failedState.runtimeGeneration} " +
                "failure=${failedState.failureKind} controllerHealthy=${failedState.controllerHealthy ?: "unknown"} " +
                "mixedPortListening=${failedState.mixedPortListening ?: "unknown"}",
        )
        var attempted = false
        try {
            mutationMutex.withLock {
                val config = currentConfig()
                if (!KiyoriNetworkProxyPolicy.requiresEmbeddedProxy(config)) {
                    proxyLog.info(
                        "运行恢复",
                        "当前配置已不需要内嵌代理，跳过自动恢复 failedGeneration=${failedState.runtimeGeneration}",
                    )
                    completeReadinessSuccess(recoveryReadinessGeneration)
                    return@withLock
                }
                attempted = true
                reconcileEnabledStateLocked(config)
            }
            if (attempted) {
                val recoveredState = runtimeState.value
                proxyLog.info(
                    "运行恢复",
                    "Mihomo 自动恢复完成 failedGeneration=${failedState.runtimeGeneration} " +
                        "runtimeGeneration=${recoveredState.runtimeGeneration ?: "none"} " +
                        "mixedPort=${recoveredState.mixedPort ?: "none"} " +
                        "controllerHealthy=${recoveredState.controllerHealthy ?: "unknown"} " +
                        "mixedPortListening=${recoveredState.mixedPortListening ?: "unknown"}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: KiyoriNetworkException) {
            proxyLog.error(
                "运行恢复",
                "Mihomo 自动恢复失败 failedGeneration=${failedState.runtimeGeneration} " +
                    "code=${error.code.name}",
            )
        } catch (error: Exception) {
            proxyLog.error(
                "运行恢复",
                "Mihomo 自动恢复异常 failedGeneration=${failedState.runtimeGeneration} " +
                    "type=${error::class.java.simpleName}",
            )
        }
    }

    fun currentConfig(): KiyoriNetworkProxyConfig = configStore.currentConfig()

    suspend fun updateConfig(
        transform: (KiyoriNetworkProxyConfig) -> KiyoriNetworkProxyConfig,
    ): KiyoriNetworkProxyConfig =
        mutationMutex.withLock {
            val candidate = transform(currentConfig())
            KiyoriNetworkProxyPolicy.validateSchema(candidate)
            KiyoriNetworkProxyPolicy.validateEnabledConfig(candidate)
            if (candidate.enabled && !currentConfig().enabled) {
                runtime.validateConfiguration(
                    KiyoriNetworkProxyPolicy.requireSubscription(candidate, candidate.activeSubscriptionId!!),
                    candidate.testUrl,
                    candidate.customRules,
                )
            }
            val updated = persistConfig { candidate }
            reconcileSavedConfig(updated)
            updated
        }

    suspend fun replaceConfig(config: KiyoriNetworkProxyConfig): KiyoriNetworkProxyConfig =
        updateConfig { config }

    suspend fun addCustomRule(
        pattern: String,
        type: KiyoriNetworkRuleType,
        mode: KiyoriNetworkRuleMode,
    ): KiyoriNetworkProxyRule =
        mutationMutex.withLock {
            requireSelfContainedCustomRule(pattern, type)
            val now = System.currentTimeMillis()
            val rule =
                KiyoriNetworkProxyRule(
                    id = UUID.randomUUID().toString(),
                    pattern = normalizeCustomRulePattern(pattern, type),
                    mode = mode,
                    type = type,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
            val updated = persistConfig { current -> current.copy(customRules = current.customRules + rule) }
            reconcileSavedConfig(updated)
            rule
        }

    suspend fun updateCustomRule(
        ruleId: String,
        pattern: String,
        type: KiyoriNetworkRuleType,
        mode: KiyoriNetworkRuleMode,
        enabled: Boolean,
    ): KiyoriNetworkProxyRule =
        mutationMutex.withLock {
            if (enabled) requireSelfContainedCustomRule(pattern, type)
            val current = currentConfig()
            val existing = current.customRules.firstOrNull { it.id == ruleId }
                ?: throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CONFIG_INVALID,
                    "The selected custom rule no longer exists.",
                )
            val updatedRule = existing.copy(
                pattern = normalizeCustomRulePattern(pattern, type),
                type = type,
                mode = mode,
                enabled = enabled,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            val updated = persistConfig {
                it.copy(customRules = it.customRules.map { rule -> if (rule.id == ruleId) updatedRule else rule })
            }
            reconcileSavedConfig(updated)
            updatedRule
        }

    suspend fun removeCustomRule(ruleId: String): KiyoriNetworkProxyConfig =
        mutationMutex.withLock {
            val current = currentConfig()
            if (current.customRules.none { it.id == ruleId }) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CONFIG_INVALID,
                    "The selected custom rule no longer exists.",
                )
            }
            val updated = persistConfig { it.copy(customRules = it.customRules.filterNot { rule -> rule.id == ruleId }) }
            reconcileSavedConfig(updated)
            updated
        }

    suspend fun updateSubscriptionRule(
        subscriptionId: String,
        ruleIndex: Int,
        rawRule: String,
    ): KiyoriProxySubscription =
        mutationMutex.withLock {
            val current = currentConfig()
            val existing = KiyoriNetworkProxyPolicy.requireSubscription(current, subscriptionId)
            val sanitized = withContext(Dispatchers.Default) {
                MihomoConfigSanitizer.replaceSubscriptionRule(
                    sanitizedYaml = existing.sanitizedYaml,
                    ruleIndex = ruleIndex,
                    rawRule = rawRule,
                )
            }
            val updatedSubscription =
                existing.copy(
                    sanitizedYaml = sanitized.yaml,
                    summary = sanitized.summary,
                    rules = sanitized.rules,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            runtime.validateConfiguration(updatedSubscription, current.testUrl, current.customRules)
            persistSubscriptionReplacement(
                current = current,
                subscription = updatedSubscription,
                reconcileActive = current.activeSubscriptionId == subscriptionId,
            )
            updatedSubscription
        }

    internal suspend fun refreshBrowserProxyOverride() =
        mutationMutex.withLock {
            val config = currentConfig()
            if (!KiyoriNetworkProxyPolicy.requiresEmbeddedProxy(config)) {
                clearWebViewProxy()
                return@withLock
            }
            val subscription =
                KiyoriNetworkProxyPolicy.validateEmbeddedStart(config, isSystemVpnActive())
            val endpoint =
                runtime.ensureReady(
                    subscription,
                    config.testUrl,
                    KiyoriNetworkProxyPolicy.runtimeMode(config),
                    config.customRules,
                )
            if (
                KiyoriNetworkProxyPolicy.effectiveMode(
                    config,
                    KiyoriNetworkModule.BROWSER,
                ) == KiyoriNetworkConnectionMode.DIRECT
            ) {
                clearWebViewProxy()
            } else {
                setWebViewProxy(endpoint, config.proxyPrivateNetworks)
            }
        }

    suspend fun reconcileEnabledState(
        config: KiyoriNetworkProxyConfig? = null,
    ) = mutationMutex.withLock {
        reconcileEnabledStateLocked(config ?: currentConfig())
    }

    private suspend fun reconcileEnabledStateLocked(
        config: KiyoriNetworkProxyConfig,
    ) {
        val readinessGeneration = beginReadinessAttempt()
        proxyLog.info(
            "运行协调",
            "开始协调 enabled=${config.enabled} proxyRequired=${KiyoriNetworkProxyPolicy.requiresEmbeddedProxy(config)} " +
                "vpnActive=${isSystemVpnActive()}",
        )
        if (!KiyoriNetworkProxyPolicy.requiresEmbeddedProxy(config)) {
            try {
                clearWebViewProxy()
                runtime.stop("proxy_route_disabled")
                completeReadinessSuccess(readinessGeneration)
            } catch (error: CancellationException) {
                completeReadinessFailure(readinessGeneration, error)
                throw error
            } catch (error: Exception) {
                completeReadinessFailure(readinessGeneration, error)
                throw error
            }
            return
        }
        try {
            val subscription =
                KiyoriNetworkProxyPolicy.validateEmbeddedStart(
                    config = config,
                    isSystemVpnActive = isSystemVpnActive(),
                )
            val endpoint = runtime.ensureReady(
                subscription,
                config.testUrl,
                KiyoriNetworkProxyPolicy.runtimeMode(config),
                config.customRules,
            )
            if (
                KiyoriNetworkProxyPolicy.effectiveModuleMode(
                    config,
                    KiyoriNetworkModule.BROWSER,
                ) != KiyoriNetworkConnectionMode.DIRECT
            ) {
                setWebViewProxy(endpoint, config.proxyPrivateNetworks)
            } else {
                clearWebViewProxy()
            }
            val state = runtimeState.value
            proxyLog.info(
                "运行协调",
                "协调完成 routeReady=true runtimeGeneration=${state.runtimeGeneration ?: "none"} " +
                    "mixedPort=${state.mixedPort ?: endpoint.port} controllerPort=${state.controllerPort ?: "none"} " +
                    "controllerHealthy=${state.controllerHealthy ?: "unknown"} " +
                    "mixedPortListening=${state.mixedPortListening ?: "unknown"}",
            )
            if (browserWebViewRuntimeReady) completeReadinessSuccess(readinessGeneration)
        } catch (error: CancellationException) {
            completeReadinessFailure(readinessGeneration, error)
            throw error
        } catch (error: KiyoriNetworkException) {
            try {
                if (error.code == KiyoriNetworkErrorCode.VPN_CONFLICT) {
                    runtime.stop("vpn_conflict")
                    clearWebViewProxy()
                }
            } finally {
                proxyLog.error("运行协调", "协调失败 code=${error.code.name}")
                completeReadinessFailure(readinessGeneration, error)
            }
            throw error
        } catch (error: Exception) {
            proxyLog.error(
                "运行协调",
                "协调失败 type=${error::class.java.simpleName} message=${error.message?.take(240) ?: "none"}",
            )
            completeReadinessFailure(readinessGeneration, error)
            throw error
        }
    }

    suspend fun refreshRuntimeState(): MihomoRuntimeSnapshot? =
        mutationMutex.withLock {
            val config = currentConfig()
            reconcileEnabledStateLocked(config)
            if (!KiyoriNetworkProxyPolicy.requiresEmbeddedProxy(config)) return@withLock null
            runtime.refreshActiveSnapshot()
        }

    suspend fun testActiveProxyConnection(): Int =
        mutationMutex.withLock { testActiveProxyConnectionLocked() }

    private suspend fun testActiveProxyConnectionLocked(): Int {
        val readinessGeneration = beginReadinessAttempt()
        val config = currentConfig()
        if (!KiyoriNetworkProxyPolicy.requiresEmbeddedProxy(config)) {
            val error = KiyoriNetworkException(
                KiyoriNetworkErrorCode.CONFIG_MISSING,
                "Enable at least one proxy route before testing the active connection.",
            )
            completeReadinessFailure(readinessGeneration, error)
            throw error
        }
        return try {
            val subscription =
                KiyoriNetworkProxyPolicy.validateEmbeddedStart(config, isSystemVpnActive())
            val endpoint = runtime.ensureReady(
                subscription,
                config.testUrl,
                KiyoriNetworkProxyPolicy.runtimeMode(config),
                config.customRules,
            )
            if (KiyoriNetworkProxyPolicy.effectiveMode(config, KiyoriNetworkModule.BROWSER) != KiyoriNetworkConnectionMode.DIRECT) {
                setWebViewProxy(endpoint, config.proxyPrivateNetworks)
            } else {
                clearWebViewProxy()
            }
            val client =
                OkHttpClient.Builder()
                    .proxy(endpoint.toJavaProxy())
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .callTimeout(20, TimeUnit.SECONDS)
                    .build()
            val request = Request.Builder().url(config.testUrl).get().build()
            val startedAt = System.currentTimeMillis()
            proxyLog.info(
                "代理连通性",
                "开始测试应用级代理 runtimeGeneration=${runtimeState.value.runtimeGeneration ?: "none"} " +
                    "endpointPort=${endpoint.port}",
            )
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        proxyLog.error(
                            "代理连通性",
                            "代理测试失败 httpStatus=${response.code} elapsedMs=${elapsedSince(startedAt)}",
                        )
                        throw KiyoriNetworkException(
                            KiyoriNetworkErrorCode.PROXY_CONNECT_FAILED,
                            "The active proxy returned HTTP ${response.code} for the connection test.",
                        )
                    }
                    proxyLog.info(
                        "代理连通性",
                        "代理测试完成 httpStatus=${response.code} elapsedMs=${elapsedSince(startedAt)}",
                    )
                    completeReadinessSuccess(readinessGeneration)
                    response.code
                }
            }
        } catch (error: CancellationException) {
            completeReadinessFailure(readinessGeneration, error)
            throw error
        } catch (error: KiyoriNetworkException) {
            completeReadinessFailure(readinessGeneration, error)
            throw error
        } catch (error: Exception) {
            completeReadinessFailure(readinessGeneration, error)
            proxyLog.error(
                "代理连通性",
                "代理测试异常 type=${error::class.java.simpleName}",
            )
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.PROXY_CONNECT_FAILED,
                "The active proxy could not reach the connection-test endpoint.",
                error,
            )
        }
    }

    suspend fun resolveRoute(
        module: KiyoriNetworkModule,
        scriptPackageName: String? = null,
    ): Pair<KiyoriNetworkRoute, KiyoriProxyEndpoint?> =
        resolveRouteSnapshot(module, scriptPackageName).let { snapshot ->
            snapshot.route to snapshot.endpoint
        }

    private suspend fun resolveRouteSnapshot(
        module: KiyoriNetworkModule,
        scriptPackageName: String? = null,
    ): ResolvedNetworkRoute =
        mutationMutex.withLock {
            resolveRouteSnapshotLocked(module, scriptPackageName)
        }

    private suspend fun resolveRouteSnapshotLocked(
        module: KiyoriNetworkModule,
        scriptPackageName: String?,
    ): ResolvedNetworkRoute {
        val config = currentConfig()
        val systemVpnActive = isSystemVpnActive()
        val route =
            try {
                KiyoriNetworkProxyPolicy.resolve(
                    config = config,
                    module = module,
                    scriptPackageName = scriptPackageName,
                    isSystemVpnActive = systemVpnActive,
                )
            } catch (error: KiyoriNetworkException) {
                if (error.code == KiyoriNetworkErrorCode.VPN_CONFLICT) {
                    runtime.stop("vpn_conflict")
                    invalidateReadinessForRuntimeFailure()
                    clearWebViewProxy()
                }
                proxyLog.error(
                    "路由解析",
                    "module=${module.name} route=REJECTED code=${error.code.name} " +
                        "vpnActive=$systemVpnActive",
                )
                throw error
            }
        return when (route) {
            KiyoriNetworkRoute.Direct -> {
                proxyLog.info(
                    "路由解析",
                    "module=${module.name} route=DIRECT vpnActive=$systemVpnActive",
                )
                ResolvedNetworkRoute(
                    config = config,
                    module = module,
                    route = route,
                    endpoint = null,
                    runtimeGeneration = null,
                )
            }
            KiyoriNetworkRoute.EmbeddedProxy -> {
                val subscription =
                    KiyoriNetworkProxyPolicy.activeSubscription(config)
                        ?: throw KiyoriNetworkException(
                            KiyoriNetworkErrorCode.CONFIG_MISSING,
                            "No active Clash or Mihomo subscription has been selected.",
                        )
                val endpoint = runtime.ensureReady(
                    subscription,
                    config.testUrl,
                    KiyoriNetworkProxyPolicy.runtimeMode(config),
                    config.customRules,
                )
                val state = runtimeState.value
                proxyLog.info(
                    "路由解析",
                    "module=${module.name} route=PROXY vpnActive=$systemVpnActive " +
                        "runtimeGeneration=${state.runtimeGeneration ?: "none"} endpointPort=${endpoint.port} " +
                        "controllerHealthy=${state.controllerHealthy ?: "unknown"} " +
                        "mixedPortListening=${state.mixedPortListening ?: "unknown"}",
                )
                ResolvedNetworkRoute(
                    config = config,
                    module = module,
                    route = route,
                    endpoint = endpoint,
                    runtimeGeneration = state.runtimeGeneration,
                )
            }
        }
    }

    private data class ResolvedNetworkRoute(
        val config: KiyoriNetworkProxyConfig,
        val module: KiyoriNetworkModule,
        val route: KiyoriNetworkRoute,
        val endpoint: KiyoriProxyEndpoint?,
        val runtimeGeneration: Long?,
    )

    internal data class TransportRouteSnapshot(
        val route: KiyoriNetworkRoute,
        val proxySelector: ProxySelector,
        val runtimeGeneration: Long?,
    )

    fun resolveRouteBlocking(
        module: KiyoriNetworkModule,
    ): KiyoriProxyEndpoint? =
        runBlocking(Dispatchers.IO) { resolveRoute(module).second }

    internal suspend fun resolveTransportRoute(
        module: KiyoriNetworkModule,
    ): TransportRouteSnapshot {
        val snapshot = resolveRouteSnapshot(module)
        return TransportRouteSnapshot(
            route = snapshot.route,
            proxySelector =
                ScopedKiyoriProxySelector(
                    proxy = snapshot.endpoint?.toJavaProxy() ?: Proxy.NO_PROXY,
                    proxyPrivateNetworks = snapshot.config.proxyPrivateNetworks,
                    browserSiteProxyDisabled =
                        if (module == KiyoriNetworkModule.BROWSER) {
                            ::isBrowserSiteProxyDisabled
                        } else {
                            { false }
                        },
                ),
            runtimeGeneration = snapshot.runtimeGeneration,
        )
    }

    internal fun resolveTransportRouteBlocking(
        module: KiyoriNetworkModule,
    ): TransportRouteSnapshot =
        runBlocking(Dispatchers.IO) { resolveTransportRoute(module) }

    suspend fun applyRoute(
        builder: OkHttpClient.Builder,
        module: KiyoriNetworkModule,
        scriptPackageName: String? = null,
        mapFailures: Boolean = false,
    ): KiyoriNetworkRoute {
        val snapshot = resolveRouteSnapshot(module, scriptPackageName)
        if (snapshot.endpoint == null) {
            builder.proxy(Proxy.NO_PROXY)
        } else {
            builder.proxySelector(
                ScopedKiyoriProxySelector(
                    proxy = snapshot.endpoint.toJavaProxy(),
                    proxyPrivateNetworks = snapshot.config.proxyPrivateNetworks,
                    browserSiteProxyDisabled =
                        if (module == KiyoriNetworkModule.BROWSER) {
                            ::isBrowserSiteProxyDisabled
                        } else {
                            { false }
                        },
                ),
            )
        }
        if (mapFailures) installFailureMapping(builder, snapshot.route, module)
        return snapshot.route
    }

    fun applyDynamicRoute(
        builder: OkHttpClient.Builder,
        module: KiyoriNetworkModule,
    ): OkHttpClient.Builder =
        builder
            .proxySelector(
                DynamicKiyoriProxySelector(
                    manager = this,
                    module = module,
                ),
            ).addNetworkInterceptor { chain ->
                // OkHttp 复用连接时可能不再次调用 ProxySelector；发送 HTTP 前拒绝旧端点或旧直连。
                val expected = DynamicKiyoriProxySelector(this, module).select(chain.request().url.toUri()).single()
                val connection = chain.connection()
                if (connection != null && connection.route().proxy != expected) {
                    connection.socket().close()
                    throw IOException("The application proxy route changed; this connection is no longer valid.")
                }
                chain.proceed(chain.request())
            }.also { installDynamicFailureLogging(it, module) }

    fun openConnectionBlocking(
        url: URL,
        module: KiyoriNetworkModule,
        scriptPackageName: String? = null,
    ): URLConnection {
        val snapshot =
            runBlocking(Dispatchers.IO) {
                resolveRouteSnapshot(module, scriptPackageName)
            }
        return openConnection(url, snapshot)
    }

    /**
     * Resolves one route for a whole transfer. Every connection opened by the returned factory
     * uses the same endpoint, so a subscription or node change cannot split one download across
     * two routes while its ranged segments are running.
     */
    fun connectionFactoryBlocking(
        module: KiyoriNetworkModule,
        scriptPackageName: String? = null,
    ): (URL) -> URLConnection {
        val snapshot =
            runBlocking(Dispatchers.IO) {
                resolveRouteSnapshot(module, scriptPackageName)
            }
        return { url -> openConnection(url, snapshot) }
    }

    fun proxySelectorBlocking(
        module: KiyoriNetworkModule,
        scriptPackageName: String? = null,
    ): ProxySelector {
        val snapshot =
            runBlocking(Dispatchers.IO) {
                resolveRouteSnapshot(module, scriptPackageName)
            }
        return ScopedKiyoriProxySelector(
            proxy = snapshot.endpoint?.toJavaProxy() ?: Proxy.NO_PROXY,
            proxyPrivateNetworks = snapshot.config.proxyPrivateNetworks,
            browserSiteProxyDisabled =
                if (module == KiyoriNetworkModule.BROWSER) {
                    ::isBrowserSiteProxyDisabled
                } else {
                    { false }
                },
        )
    }

    private fun openConnection(
        url: URL,
        snapshot: ResolvedNetworkRoute,
    ): URLConnection {
        val bypass =
            snapshot.endpoint == null ||
                shouldBypassKiyoriProxy(url.host, snapshot.config.proxyPrivateNetworks) ||
                (snapshot.module == KiyoriNetworkModule.BROWSER && isBrowserSiteProxyDisabled(url.host))
        return url.openConnection(if (bypass) Proxy.NO_PROXY else snapshot.endpoint.toJavaProxy())
    }

    suspend fun addSubscriptionUrl(
        rawUrl: String,
        requestedDisplayName: String = "",
    ): KiyoriProxySubscription =
        mutationMutex.withLock {
            val current = currentConfig()
            val url = rawUrl.trim()
            requireUniqueUrl(current, url, excludedSubscriptionId = null)
            val sanitized =
                subscriptionClient.fetchAndSanitize(
                    rawUrl = url,
                    proxyEndpoint = subscriptionUpdateEndpoint(current),
                )
            val now = System.currentTimeMillis()
            val subscription =
                buildImportedSubscription(
                    existing = null,
                    id = UUID.randomUUID().toString(),
                    displayName =
                        normalizedDisplayName(
                            requestedDisplayName,
                            defaultDisplayNameForUrl(url, current),
                        ),
                    sourceType = KiyoriSubscriptionSourceType.URL,
                    subscriptionUrl = url,
                    sourceLabel = "",
                    sanitized = sanitized,
                    now = now,
                )
            runtime.validateConfiguration(subscription, current.testUrl, current.customRules)
            val updated =
                persistConfig { latest ->
                    if (latest.subscriptions.size >= KiyoriNetworkProxyConfig.MAX_SUBSCRIPTIONS) {
                        throw KiyoriNetworkException(
                            KiyoriNetworkErrorCode.CONFIG_INVALID,
                            "The subscription library limit has been reached.",
                        )
                    }
                    latest.copy(
                        subscriptions = latest.subscriptions + subscription,
                        activeSubscriptionId = latest.activeSubscriptionId ?: subscription.id,
                    )
                }
            if (updated.activeSubscriptionId == subscription.id) reconcileSavedConfig(updated)
            subscription
        }

    suspend fun addLocalYaml(
        rawYaml: String,
        sourceLabel: String,
        requestedDisplayName: String = "",
    ): KiyoriProxySubscription =
        mutationMutex.withLock {
            val current = currentConfig()
            val sanitized = withContext(Dispatchers.Default) { MihomoSubscriptionInput.sanitize(rawYaml) }
            val now = System.currentTimeMillis()
            val normalizedSourceLabel = sourceLabel.trim().take(160)
            val subscription =
                buildImportedSubscription(
                    existing = null,
                    id = UUID.randomUUID().toString(),
                    displayName =
                        normalizedDisplayName(
                            requestedDisplayName,
                            defaultDisplayNameForFile(normalizedSourceLabel, current),
                        ),
                    sourceType = KiyoriSubscriptionSourceType.LOCAL_FILE,
                    subscriptionUrl = "",
                    sourceLabel = normalizedSourceLabel,
                    sanitized = sanitized,
                    now = now,
                )
            runtime.validateConfiguration(subscription, current.testUrl, current.customRules)
            val updated =
                persistConfig { latest ->
                    if (latest.subscriptions.size >= KiyoriNetworkProxyConfig.MAX_SUBSCRIPTIONS) {
                        throw KiyoriNetworkException(
                            KiyoriNetworkErrorCode.CONFIG_INVALID,
                            "The subscription library limit has been reached.",
                        )
                    }
                    latest.copy(
                        subscriptions = latest.subscriptions + subscription,
                        activeSubscriptionId = latest.activeSubscriptionId ?: subscription.id,
                    )
                }
            if (updated.activeSubscriptionId == subscription.id) reconcileSavedConfig(updated)
            subscription
        }

    suspend fun updateUrlSubscription(subscriptionId: String): KiyoriProxySubscription =
        mutationMutex.withLock {
            val current = currentConfig()
            val existing = KiyoriNetworkProxyPolicy.requireSubscription(current, subscriptionId)
            if (existing.sourceType != KiyoriSubscriptionSourceType.URL) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CONFIG_INVALID,
                    "A local YAML subscription must be updated by selecting a new file.",
                )
            }
            replaceUrlSubscriptionLocked(
                current = current,
                existing = existing,
                displayName = existing.displayName,
                rawUrl = existing.subscriptionUrl,
            )
        }

    suspend fun editUrlSubscription(
        subscriptionId: String,
        displayName: String,
        rawUrl: String,
    ): KiyoriProxySubscription =
        mutationMutex.withLock {
            val current = currentConfig()
            val existing = KiyoriNetworkProxyPolicy.requireSubscription(current, subscriptionId)
            if (existing.sourceType != KiyoriSubscriptionSourceType.URL) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CONFIG_INVALID,
                    "The selected subscription is not URL-based.",
                )
            }
            val normalizedName = normalizedDisplayName(displayName, existing.displayName)
            val normalizedUrl = rawUrl.trim()
            if (normalizedUrl == existing.subscriptionUrl) {
                val renamed = existing.copy(displayName = normalizedName)
                persistSubscriptionReplacement(current, renamed, reconcileActive = false)
                renamed
            } else {
                requireUniqueUrl(current, normalizedUrl, excludedSubscriptionId = existing.id)
                replaceUrlSubscriptionLocked(current, existing, normalizedName, normalizedUrl)
            }
        }

    suspend fun renameSubscription(
        subscriptionId: String,
        displayName: String,
    ): KiyoriProxySubscription =
        mutationMutex.withLock {
            val current = currentConfig()
            val existing = KiyoriNetworkProxyPolicy.requireSubscription(current, subscriptionId)
            val renamed = existing.copy(displayName = normalizedDisplayName(displayName, existing.displayName))
            persistSubscriptionReplacement(current, renamed, reconcileActive = false)
            renamed
        }

    suspend fun replaceLocalSubscription(
        subscriptionId: String,
        rawYaml: String,
        sourceLabel: String,
    ): KiyoriProxySubscription =
        mutationMutex.withLock {
            val current = currentConfig()
            val existing = KiyoriNetworkProxyPolicy.requireSubscription(current, subscriptionId)
            if (existing.sourceType != KiyoriSubscriptionSourceType.LOCAL_FILE) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CONFIG_INVALID,
                    "The selected subscription is not a local YAML entry.",
                )
            }
            val sanitized = withContext(Dispatchers.Default) { MihomoSubscriptionInput.sanitize(rawYaml) }
            val updatedSubscription =
                buildImportedSubscription(
                    existing = existing,
                    id = existing.id,
                    displayName = existing.displayName,
                    sourceType = KiyoriSubscriptionSourceType.LOCAL_FILE,
                    subscriptionUrl = "",
                    sourceLabel = sourceLabel.trim().take(160),
                    sanitized = sanitized,
                    now = System.currentTimeMillis(),
                )
            runtime.validateConfiguration(updatedSubscription, current.testUrl, current.customRules)
            persistSubscriptionReplacement(
                current = current,
                subscription = updatedSubscription,
                reconcileActive = current.activeSubscriptionId == existing.id,
            )
            updatedSubscription
        }

    suspend fun duplicateSubscription(
        subscriptionId: String,
        requestedDisplayName: String,
    ): KiyoriProxySubscription =
        mutationMutex.withLock {
            val current = currentConfig()
            if (current.subscriptions.size >= KiyoriNetworkProxyConfig.MAX_SUBSCRIPTIONS) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CONFIG_INVALID,
                    "The subscription library limit has been reached.",
                )
            }
            val existing = KiyoriNetworkProxyPolicy.requireSubscription(current, subscriptionId)
            val now = System.currentTimeMillis()
            val duplicate =
                existing.copy(
                    id = UUID.randomUUID().toString(),
                    displayName = normalizedDisplayName(requestedDisplayName, "${existing.displayName} copy"),
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
            persistConfig { latest -> latest.copy(subscriptions = latest.subscriptions + duplicate) }
            duplicate
        }

    suspend fun switchActiveSubscription(subscriptionId: String): KiyoriNetworkProxyConfig =
        mutationMutex.withLock {
            val current = currentConfig()
            val subscription = KiyoriNetworkProxyPolicy.requireSubscription(current, subscriptionId)
            proxyLog.info("订阅管理", "正在切换当前订阅：${subscription.displayName}")
            runtime.validateConfiguration(subscription, current.testUrl, current.customRules)
            val updated = persistConfig { it.copy(activeSubscriptionId = subscriptionId) }
            reconcileSavedConfig(updated)
            proxyLog.info("订阅管理", "当前订阅已切换：${subscription.displayName}")
            updated
        }

    suspend fun removeSubscription(subscriptionId: String): KiyoriNetworkProxyConfig =
        mutationMutex.withLock {
            val current = currentConfig()
            KiyoriNetworkProxyPolicy.requireSubscription(current, subscriptionId)
            if (
                current.activeSubscriptionId == subscriptionId &&
                    KiyoriNetworkProxyPolicy.requiresEmbeddedProxy(current)
            ) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.SUBSCRIPTION_IN_USE,
                    "Switch subscriptions or disable the application proxy before deleting the active subscription.",
                )
            }
            val updated =
                persistConfig { latest ->
                    latest.copy(
                        subscriptions = latest.subscriptions.filterNot { it.id == subscriptionId },
                        enabled = latest.enabled && latest.activeSubscriptionId != subscriptionId,
                        activeSubscriptionId =
                            latest.activeSubscriptionId.takeIf { activeId -> activeId != subscriptionId },
                    )
                }
            if (current.activeSubscriptionId == subscriptionId) reconcileSavedConfig(updated)
            updated
        }

    suspend fun selectGroup(
        subscriptionId: String,
        groupName: String,
        itemName: String,
    ): KiyoriNetworkProxyConfig =
        mutationMutex.withLock {
            val current = currentConfig()
            val subscription = KiyoriNetworkProxyPolicy.requireSubscription(current, subscriptionId)
            val normalizedGroup = groupName.trim()
            val normalizedItem = itemName.trim()
            val availableOptions = availableGroupOptions(subscription, normalizedGroup)
            if (normalizedItem.isBlank() || normalizedItem !in availableOptions) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.GROUP_SELECTION_INVALID,
                    "The selected group item is not available for this subscription.",
                )
            }
            val updatedSubscription =
                subscription.copy(
                    selectedGroupItems =
                        subscription.selectedGroupItems.toMutableMap().apply {
                            this[normalizedGroup] = normalizedItem
                        },
                )
            val updated = replaceSubscription(current, updatedSubscription)
            val persisted = persistConfig { updated }
            if (
                persisted.activeSubscriptionId == subscriptionId &&
                    KiyoriNetworkProxyPolicy.requiresEmbeddedProxy(persisted)
            ) {
                reconcileSavedConfig(persisted)
            }
            persisted
        }

    suspend fun refreshSubscriptionNodes(subscriptionId: String): KiyoriProxySubscription =
        mutationMutex.withLock {
            val current = currentConfig()
            val subscription = KiyoriNetworkProxyPolicy.requireSubscription(current, subscriptionId)
            val snapshot = runtime.probeSnapshot(subscription, current.testUrl)
            val refreshed =
                subscription.copy(
                    nodeTests = mergeSnapshot(subscription.nodeTests, snapshot, System.currentTimeMillis()),
                )
            persistSubscriptionReplacement(current, refreshed, reconcileActive = false)
            refreshed
        }

    suspend fun testSubscriptionNode(
        subscriptionId: String,
        nodeName: String,
    ): KiyoriProxySubscription =
        nodeTestMutex.withLock {
            val initial = currentConfig()
            val subscription = KiyoriNetworkProxyPolicy.requireSubscription(initial, subscriptionId)
            val normalizedNode = nodeName.trim()
            try {
                val (snapshot, delay) = testNodeDelayForSubscription(subscription, normalizedNode, initial.testUrl)
                persistNodeTestResult(subscriptionId, normalizedNode, snapshot, delay)
            } catch (error: KiyoriNetworkException) {
                persistNodeFailure(subscriptionId, normalizedNode, error)
                throw error
            }
        }

    suspend fun testSubscriptionGroup(
        subscriptionId: String,
        groupName: String,
    ): KiyoriProxySubscription =
        nodeTestMutex.withLock {
            val initial = currentConfig()
            val subscription = KiyoriNetworkProxyPolicy.requireSubscription(initial, subscriptionId)
            val normalizedGroup = groupName.trim()
            try {
                val (snapshot, delays) = testGroupDelaysForSubscription(subscription, normalizedGroup, initial.testUrl)
                persistGroupTestResult(subscriptionId, normalizedGroup, snapshot, delays)
            } catch (error: KiyoriNetworkException) {
                proxyLog.error("分组测速", "策略组测速失败：$normalizedGroup，${error.code.name}")
                throw error
            }
        }

    private suspend fun testNodeDelayForSubscription(
        subscription: KiyoriProxySubscription,
        nodeName: String,
        testUrl: String,
    ): Pair<MihomoRuntimeSnapshot, Int> {
        if (runtimeState.value.subscriptionId == subscription.id && runtimeState.value.phase == KiyoriMihomoRuntimePhase.RUNNING) {
            val delay = runtime.testActiveNodeDelay(nodeName, testUrl)
            return runtime.refreshActiveSnapshot() to delay
        }
        return runtime.probeNodeDelay(subscription, nodeName, testUrl)
    }

    private suspend fun testGroupDelaysForSubscription(
        subscription: KiyoriProxySubscription,
        groupName: String,
        testUrl: String,
    ): Pair<MihomoRuntimeSnapshot, Map<String, Int>> {
        if (runtimeState.value.subscriptionId == subscription.id && runtimeState.value.phase == KiyoriMihomoRuntimePhase.RUNNING) {
            val delays = runtime.testActiveGroupDelays(groupName, testUrl)
            return runtime.refreshActiveSnapshot() to delays
        }
        return runtime.probeGroupDelays(subscription, groupName, testUrl)
    }

    private suspend fun persistNodeTestResult(
        subscriptionId: String,
        nodeName: String,
        snapshot: MihomoRuntimeSnapshot,
        delay: Int,
    ): KiyoriProxySubscription =
        mutationMutex.withLock {
            val latest = currentConfig()
            val subscription = KiyoriNetworkProxyPolicy.requireSubscription(latest, subscriptionId)
            val now = System.currentTimeMillis()
            val tested =
                subscription.copy(
                    nodeTests =
                        mergeSnapshot(subscription.nodeTests, snapshot, now).map { result ->
                            if (result.name == nodeName) {
                                result.copy(
                                    delayMillis = delay,
                                    status = MihomoNodeTestStatus.SUCCESS,
                                    testedAtEpochMillis = now,
                                )
                            } else {
                                result
                            }
                        },
                )
            persistSubscriptionReplacement(latest, tested, reconcileActive = false)
            tested
        }

    private suspend fun persistGroupTestResult(
        subscriptionId: String,
        groupName: String,
        snapshot: MihomoRuntimeSnapshot,
        delays: Map<String, Int>,
    ): KiyoriProxySubscription =
        mutationMutex.withLock {
            val latest = currentConfig()
            val subscription = KiyoriNetworkProxyPolicy.requireSubscription(latest, subscriptionId)
            val now = System.currentTimeMillis()
            val tested =
                subscription.copy(
                    nodeTests =
                        mergeSnapshot(subscription.nodeTests, snapshot, now).map { result ->
                            if (groupName !in result.groupNames) return@map result
                            delays[result.name]?.let { delay ->
                                result.copy(
                                    delayMillis = delay,
                                    status = MihomoNodeTestStatus.SUCCESS,
                                    testedAtEpochMillis = now,
                                )
                            } ?: result.copy(
                                delayMillis = null,
                                status = MihomoNodeTestStatus.TIMEOUT,
                                testedAtEpochMillis = now,
                            )
                        },
                )
            persistSubscriptionReplacement(latest, tested, reconcileActive = false)
            tested
        }

    suspend fun reset(): KiyoriNetworkProxyConfig =
        mutationMutex.withLock {
            clearWebViewProxy()
            runtime.stop("settings_reset")
            withContext(Dispatchers.IO) { configStore.reset() }
        }

    fun isSystemVpnActive(): Boolean {
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
        return manager.allNetworks.any { network ->
            manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    private suspend fun replaceUrlSubscriptionLocked(
        current: KiyoriNetworkProxyConfig,
        existing: KiyoriProxySubscription,
        displayName: String,
        rawUrl: String,
    ): KiyoriProxySubscription {
        val sanitized =
            subscriptionClient.fetchAndSanitize(
                rawUrl = rawUrl,
                proxyEndpoint = subscriptionUpdateEndpoint(current),
            )
        val updatedSubscription =
            buildImportedSubscription(
                existing = existing,
                id = existing.id,
                displayName = displayName,
                sourceType = KiyoriSubscriptionSourceType.URL,
                subscriptionUrl = rawUrl,
                sourceLabel = "",
                sanitized = sanitized,
                now = System.currentTimeMillis(),
            )
        runtime.validateConfiguration(updatedSubscription, current.testUrl, current.customRules)
        persistSubscriptionReplacement(
            current = current,
            subscription = updatedSubscription,
            reconcileActive = current.activeSubscriptionId == existing.id,
        )
        return updatedSubscription
    }

    private suspend fun subscriptionUpdateEndpoint(
        config: KiyoriNetworkProxyConfig,
    ): KiyoriProxyEndpoint? {
        if (KiyoriNetworkProxyPolicy.activeSubscription(config) == null) {
            proxyLog.info("订阅下载", "首次导入 bootstrap=SYSTEM；允许系统 VPN 提供网络")
            return null
        }
        if (!KiyoriNetworkProxyPolicy.requiresEmbeddedProxy(config)) return null
        val active = KiyoriNetworkProxyPolicy.validateEmbeddedStart(config, isSystemVpnActive())
        return runtime.ensureReady(
            active,
            config.testUrl,
            KiyoriNetworkProxyPolicy.runtimeMode(config),
            config.customRules,
        )
    }

    private fun buildImportedSubscription(
        existing: KiyoriProxySubscription?,
        id: String,
        displayName: String,
        sourceType: KiyoriSubscriptionSourceType,
        subscriptionUrl: String,
        sourceLabel: String,
        sanitized: SanitizedMihomoSubscription,
        now: Long,
    ): KiyoriProxySubscription {
        val selectedGroups =
            retainValidSelections(
                previous = existing?.selectedGroupItems.orEmpty(),
                summary = sanitized.summary,
            ).toMutableMap()
        if (
            MihomoConfigSanitizer.ROUTE_GROUP_NAME !in selectedGroups &&
                sanitized.summary.rootCandidates.isNotEmpty()
        ) {
            selectedGroups[MihomoConfigSanitizer.ROUTE_GROUP_NAME] =
                sanitized.summary.rootCandidates.first()
        }
        return KiyoriProxySubscription(
            id = id,
            displayName = displayName,
            sourceType = sourceType,
            subscriptionUrl = subscriptionUrl,
            sourceLabel = sourceLabel,
            sanitizedYaml = sanitized.yaml,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
            summary = sanitized.summary,
            usage = sanitized.usage,
            selectedGroupItems = selectedGroups,
            nodeTests = emptyList(),
            rules = sanitized.rules,
        )
    }

    private fun retainValidSelections(
        previous: Map<String, String>,
        summary: MihomoSubscriptionSummary,
    ): Map<String, String> {
        val validOptions =
            buildMap<String, Set<String>> {
                put(MihomoConfigSanitizer.ROUTE_GROUP_NAME, summary.rootCandidates.toSet())
                summary.groups.forEach { group -> put(group.name, group.options.toSet()) }
            }
        return previous.filter { (groupName, itemName) ->
            itemName in validOptions[groupName].orEmpty()
        }
    }

    private fun availableGroupOptions(
        subscription: KiyoriProxySubscription,
        groupName: String,
    ): Set<String> {
        val staticOptions =
            if (groupName == MihomoConfigSanitizer.ROUTE_GROUP_NAME) {
                subscription.summary.rootCandidates
            } else {
                subscription.summary.groups.firstOrNull { it.name == groupName }?.options.orEmpty()
            }
        val discoveredNodes =
            subscription.nodeTests.filter { result -> groupName in result.groupNames }
                .map(MihomoNodeTestResult::name)
        return (staticOptions + discoveredNodes).toSet()
    }

    private fun mergeSnapshot(
        previous: List<MihomoNodeTestResult>,
        snapshot: MihomoRuntimeSnapshot,
        observedAt: Long,
    ): List<MihomoNodeTestResult> {
        val previousByName = previous.associateBy(MihomoNodeTestResult::name)
        return snapshot.nodes.take(KiyoriNetworkProxyConfig.MAX_NODE_TEST_RESULTS).map { node ->
            val stored = previousByName[node.name]
            when {
                node.delayMillis != null ->
                    MihomoNodeTestResult(
                        name = node.name,
                        type = node.type,
                        groupNames = node.groupNames,
                        delayMillis = node.delayMillis,
                        status = MihomoNodeTestStatus.SUCCESS,
                        testedAtEpochMillis = observedAt,
                    )
                stored != null ->
                    stored.copy(type = node.type, groupNames = node.groupNames)
                else ->
                    MihomoNodeTestResult(
                        name = node.name,
                        type = node.type,
                        groupNames = node.groupNames,
                    )
            }
        }
    }

    private suspend fun persistNodeFailure(
        subscriptionId: String,
        nodeName: String,
        originalError: KiyoriNetworkException,
    ) {
        try {
            mutationMutex.withLock {
                val latest = currentConfig()
                val subscription = latest.subscriptions.firstOrNull { it.id == subscriptionId } ?: return@withLock
                if (subscription.nodeTests.none { result -> result.name == nodeName }) return@withLock
                val now = System.currentTimeMillis()
                val failed =
                    subscription.copy(
                        nodeTests =
                            subscription.nodeTests.map { result ->
                                if (result.name == nodeName) {
                                    result.copy(
                                        delayMillis = null,
                                        status = MihomoNodeTestStatus.FAILED,
                                        testedAtEpochMillis = now,
                                    )
                                } else {
                                    result
                                }
                            },
                    )
                persistSubscriptionReplacement(latest, failed, reconcileActive = false)
            }
        } catch (error: Exception) {
            proxyLog.error(
                "节点测速",
                "测速失败状态保存失败：$nodeName，原始错误=${originalError.code.name}，保存错误=${error::class.java.simpleName}",
            )
        }
    }

    private suspend fun persistSubscriptionReplacement(
        current: KiyoriNetworkProxyConfig,
        subscription: KiyoriProxySubscription,
        reconcileActive: Boolean,
    ): KiyoriNetworkProxyConfig {
        val updated = replaceSubscription(current, subscription)
        val persisted = persistConfig { updated }
        if (reconcileActive) reconcileSavedConfig(persisted)
        return persisted
    }

    private fun replaceSubscription(
        config: KiyoriNetworkProxyConfig,
        subscription: KiyoriProxySubscription,
    ): KiyoriNetworkProxyConfig =
        config.copy(
            subscriptions =
                config.subscriptions.map { existing ->
                    if (existing.id == subscription.id) subscription else existing
                },
        )

    private suspend fun persistConfig(
        transform: (KiyoriNetworkProxyConfig) -> KiyoriNetworkProxyConfig,
    ): KiyoriNetworkProxyConfig =
        withContext(Dispatchers.IO) { configStore.update(transform) }

    private suspend fun reconcileSavedConfig(config: KiyoriNetworkProxyConfig) {
        try {
            reconcileEnabledStateLocked(config)
        } catch (error: KiyoriNetworkSettingsAppliedException) {
            throw error
        } catch (error: KiyoriNetworkException) {
            throw KiyoriNetworkSettingsAppliedException(error)
        }
    }

    private fun requireUniqueUrl(
        config: KiyoriNetworkProxyConfig,
        rawUrl: String,
        excludedSubscriptionId: String?,
    ) {
        val normalizedUrl = rawUrl.trim()
        if (
            config.subscriptions.any { subscription ->
                subscription.id != excludedSubscriptionId &&
                    subscription.sourceType == KiyoriSubscriptionSourceType.URL &&
                    subscription.subscriptionUrl == normalizedUrl
            }
        ) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.SUBSCRIPTION_DUPLICATE,
                "The subscription URL already exists in the library.",
            )
        }
    }

    private fun normalizedDisplayName(requested: String, defaultName: String): String {
        val name = requested.trim().ifEmpty { defaultName.trim() }
        if (name.isBlank() || name.length > KiyoriNetworkProxyConfig.MAX_SUBSCRIPTION_NAME_LENGTH) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.CONFIG_INVALID,
                "The subscription name must contain 1 to ${KiyoriNetworkProxyConfig.MAX_SUBSCRIPTION_NAME_LENGTH} characters.",
            )
        }
        return name
    }

    private fun defaultDisplayNameForUrl(
        url: String,
        config: KiyoriNetworkProxyConfig,
    ): String {
        val host = URI(url).host.orEmpty().ifBlank { "Clash subscription" }
        return uniqueDisplayName(host, config)
    }

    private fun defaultDisplayNameForFile(
        sourceLabel: String,
        config: KiyoriNetworkProxyConfig,
    ): String {
        val base =
            sourceLabel.substringBeforeLast('.').trim()
                .ifBlank { "Local YAML" }
                .take(KiyoriNetworkProxyConfig.MAX_SUBSCRIPTION_NAME_LENGTH)
        return uniqueDisplayName(base, config)
    }

    private fun uniqueDisplayName(
        base: String,
        config: KiyoriNetworkProxyConfig,
    ): String {
        val existing = config.subscriptions.mapTo(hashSetOf()) { it.displayName.lowercase() }
        if (base.lowercase() !in existing) return base
        for (suffix in 2..KiyoriNetworkProxyConfig.MAX_SUBSCRIPTIONS + 1) {
            val suffixText = " $suffix"
            val candidate =
                base.take(KiyoriNetworkProxyConfig.MAX_SUBSCRIPTION_NAME_LENGTH - suffixText.length) +
                    suffixText
            if (candidate.lowercase() !in existing) return candidate
        }
        throw KiyoriNetworkException(
            KiyoriNetworkErrorCode.CONFIG_INVALID,
            "A unique subscription name could not be generated.",
        )
    }

    private suspend fun setWebViewProxy(
        endpoint: KiyoriProxyEndpoint,
        proxyPrivateNetworks: Boolean,
    ) {
        // 代理可由 AI 或设置先启动；实际 WebView 出现后才安装覆盖，导航仍等待 readiness。
        if (!browserWebViewRuntimeReady) return
        val startedAt = System.currentTimeMillis()
        val bypassDomains =
            browserSiteProxyDisabledDomainsProvider
                ?.invoke()
                .orEmpty()
                .map { domain -> domain.trim().lowercase() }
                .filter(String::isNotBlank)
                .distinct()
                .sorted()
        val fingerprint =
            buildString {
                append(endpoint.host.trim().lowercase())
                append(':')
                append(endpoint.port)
                append('|')
                append(proxyPrivateNetworks)
                append('|')
                append(bypassDomains.joinToString(","))
            }
        if (appliedWebViewProxyFingerprint == fingerprint) {
            proxyLog.info(
                "WebView 代理",
                "复用已安装 processWideOverride=true endpointPort=${endpoint.port}",
            )
            return
        }
        proxyLog.info(
            "WebView 代理",
            "开始安装 processWideOverride=true endpointPort=${endpoint.port} " +
                "proxyPrivateNetworks=$proxyPrivateNetworks",
        )
        val proxyController =
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance()
            } else {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.WEBVIEW_UNSUPPORTED,
                    "The installed Android WebView does not support proxy overrides.",
                )
            }
        val builder =
            ProxyConfig.Builder()
                .addProxyRule("http://${endpoint.host}:${endpoint.port}")
        kiyoriWebViewProxyBypassRules(proxyPrivateNetworks).forEach(builder::addBypassRule)
        bypassDomains.forEach { normalizedDomain ->
            builder.addBypassRule(normalizedDomain)
            builder.addBypassRule("*.$normalizedDomain")
        }
        val completed =
            withTimeoutOrNull(WEBVIEW_PROXY_OPERATION_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine { continuation ->
                    proxyController.setProxyOverride(
                        builder.build(),
                        ContextCompat.getMainExecutor(appContext),
                    ) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
        if (completed == null) {
            proxyLog.error(
                "WebView 代理",
                "安装超时 endpointPort=${endpoint.port} timeoutMs=$WEBVIEW_PROXY_OPERATION_TIMEOUT_MILLIS",
            )
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.WEBVIEW_UNSUPPORTED,
                "The installed Android WebView did not complete the proxy override operation.",
            )
        }
        appliedWebViewProxyFingerprint = fingerprint
        proxyLog.info(
            "WebView 代理",
            "安装完成 endpointPort=${endpoint.port} elapsedMs=${elapsedSince(startedAt)}",
        )
    }

    private suspend fun clearWebViewProxy() {
        if (!browserWebViewRuntimeReady) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        val proxyController = ProxyController.getInstance()
        val startedAt = System.currentTimeMillis()
        val completed =
            withTimeoutOrNull(WEBVIEW_PROXY_OPERATION_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine { continuation ->
                    proxyController.clearProxyOverride(
                        ContextCompat.getMainExecutor(appContext),
                    ) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
        if (completed == null) {
            proxyLog.error(
                "WebView 代理",
                "清理超时 timeoutMs=$WEBVIEW_PROXY_OPERATION_TIMEOUT_MILLIS",
            )
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.WEBVIEW_UNSUPPORTED,
                "The installed Android WebView did not complete the proxy clear operation.",
            )
        }
        appliedWebViewProxyFingerprint = null
        proxyLog.info("WebView 代理", "清理完成 elapsedMs=${elapsedSince(startedAt)}")
    }

    private fun installFailureMapping(
        builder: OkHttpClient.Builder,
        route: KiyoriNetworkRoute,
        module: KiyoriNetworkModule,
    ) {
        builder.addInterceptor { chain ->
            val startedAt = System.currentTimeMillis()
            try {
                chain.proceed(chain.request())
            } catch (error: KiyoriNetworkException) {
                throw error
            } catch (error: IOException) {
                logConnectionFailure(
                    module = module,
                    route = routeLabel(route),
                    request = chain.request(),
                    startedAtEpochMillis = startedAt,
                    error = error,
                )
                val proxyRoute = route is KiyoriNetworkRoute.EmbeddedProxy
                throw KiyoriNetworkException(
                    if (proxyRoute) {
                        KiyoriNetworkErrorCode.PROXY_CONNECT_FAILED
                    } else {
                        KiyoriNetworkErrorCode.HTTP_FAILED
                    },
                    if (proxyRoute) {
                        "The ${module.name.lowercase()} request could not connect through Kiyori Mihomo."
                    } else {
                        "The ${module.name.lowercase()} direct request could not connect."
                    },
                    error,
                )
            }
        }
    }

    private fun installDynamicFailureLogging(
        builder: OkHttpClient.Builder,
        module: KiyoriNetworkModule,
    ) {
        builder.addInterceptor { chain ->
            val startedAt = System.currentTimeMillis()
            try {
                chain.proceed(chain.request())
            } catch (error: IOException) {
                logConnectionFailure(
                    module = module,
                    route = currentDiagnosticRoute(module),
                    request = chain.request(),
                    startedAtEpochMillis = startedAt,
                    error = error,
                )
                throw error
            }
        }
    }

    private fun logConnectionFailure(
        module: KiyoriNetworkModule,
        route: String,
        request: Request,
        startedAtEpochMillis: Long,
        error: IOException,
    ) {
        val state = runtimeState.value
        proxyLog.error(
            "网络请求",
            "连接失败 module=${module.name} route=$route method=${request.method} " +
                "url=${request.url} type=${error::class.java.simpleName} " +
                "runtimeGeneration=${state.runtimeGeneration ?: "none"} " +
                "endpointPort=${state.mixedPort ?: "none"} " +
                "controllerHealthy=${state.controllerHealthy ?: "unknown"} " +
                "mixedPortListening=${state.mixedPortListening ?: "unknown"} " +
                "elapsedMs=${elapsedSince(startedAtEpochMillis)} " +
                "message=${error.message.orEmpty()}",
        )
    }

    private fun currentDiagnosticRoute(module: KiyoriNetworkModule): String {
        val config = runCatching { currentConfig() }.getOrNull() ?: return "UNKNOWN"
        return when (KiyoriNetworkProxyPolicy.effectiveMode(config, module)) {
            KiyoriNetworkConnectionMode.DIRECT -> "DIRECT"
            KiyoriNetworkConnectionMode.RULE -> "RULE"
            KiyoriNetworkConnectionMode.GLOBAL,
            KiyoriNetworkConnectionMode.PROXY,
            -> "PROXY"
        }
    }

    private fun normalizeCustomRulePattern(
        pattern: String,
        type: KiyoriNetworkRuleType,
    ): String {
        val normalized = pattern.trim()
        return when (type) {
            KiyoriNetworkRuleType.DOMAIN,
            KiyoriNetworkRuleType.DOMAIN_KEYWORD,
            -> normalized.lowercase()
            KiyoriNetworkRuleType.DOMAIN_SUFFIX ->
                normalized.lowercase().removePrefix("*.").removePrefix(".")
            else -> normalized
        }
    }

    private fun requireSelfContainedCustomRule(
        pattern: String,
        type: KiyoriNetworkRuleType,
    ) {
        if (isMihomoRuleExternalDataDependent(type, pattern)) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.CONFIG_INVALID,
                "The selected custom rule type requires external GeoIP, GeoSite, ASN, or rule-provider data.",
            )
        }
    }

    private fun routeLabel(route: KiyoriNetworkRoute): String =
        when (route) {
            KiyoriNetworkRoute.Direct -> "DIRECT"
            KiyoriNetworkRoute.EmbeddedProxy -> "PROXY"
        }

    private fun elapsedSince(startedAtEpochMillis: Long): Long =
        (System.currentTimeMillis() - startedAtEpochMillis).coerceAtLeast(0L)
}

fun OkHttpClient.Builder.applyKiyoriNetworkProxy(
    module: KiyoriNetworkModule,
): OkHttpClient.Builder =
    if (ApplicationContextAccess.isInstalled()) {
        KiyoriNetworkProxyManager.getInstance(ApplicationContextAccess.current)
            .applyDynamicRoute(this, module)
    } else {
        // JVM tests and library-only hosts have no Android Application lifecycle. Production
        // callers install the process context before constructing network clients.
        this
    }

internal class DynamicKiyoriProxySelector(
    private val manager: KiyoriNetworkProxyManager,
    private val module: KiyoriNetworkModule,
) : ProxySelector() {
    override fun select(uri: URI?): List<Proxy> {
        val config = manager.currentConfig()
        val host = uri?.host?.trim()?.lowercase().orEmpty()
        if (
            host.isEmpty() ||
                shouldBypassKiyoriProxy(host, config.proxyPrivateNetworks) ||
                (module == KiyoriNetworkModule.BROWSER && manager.isBrowserSiteProxyDisabled(host))
        ) {
            return listOf(Proxy.NO_PROXY)
        }
        val (_, endpoint) = runBlocking(Dispatchers.IO) { manager.resolveRoute(module) }
        return listOf(endpoint?.toJavaProxy() ?: Proxy.NO_PROXY)
    }

    override fun connectFailed(uri: URI?, socketAddress: SocketAddress?, error: IOException?) {
        // The owning network client receives the concrete connection exception.
    }
}

internal class ScopedKiyoriProxySelector(
    private val proxy: Proxy,
    private val proxyPrivateNetworks: Boolean,
    private val browserSiteProxyDisabled: (String) -> Boolean = { false },
) : ProxySelector() {
    override fun select(uri: URI?): List<Proxy> {
        val host = uri?.host?.trim()?.lowercase().orEmpty()
        return if (
            host.isEmpty() ||
                shouldBypassKiyoriProxy(host, proxyPrivateNetworks) ||
                browserSiteProxyDisabled(host)
        ) {
            listOf(Proxy.NO_PROXY)
        } else {
            listOf(proxy)
        }
    }

    override fun connectFailed(uri: URI?, socketAddress: SocketAddress?, error: IOException?) {
        // The owning network client receives the concrete connection exception.
    }
}

internal fun shouldBypassKiyoriProxy(
    rawHost: String,
    proxyPrivateNetworks: Boolean,
): Boolean {
    val host = rawHost.removePrefix("[").removeSuffix("]").lowercase()
    if (host == "localhost" || host.endsWith(".localhost")) return true
    val address = parseNumericAddress(host) ?: return false
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isMulticastAddress) {
        return true
    }
    if (proxyPrivateNetworks) return false
    if (address.isSiteLocalAddress || address.isLinkLocalAddress) return true
    return address is Inet6Address && (address.address[0].toInt() and 0xfe) == 0xfc
}

internal fun kiyoriWebViewProxyBypassRules(proxyPrivateNetworks: Boolean): List<String> = buildList {
    addAll(listOf("localhost", "*.localhost", "127.0.0.0/8", "[::1]", "0.0.0.0", "[::]", "224.0.0.0/4", "ff00::/8"))
    if (!proxyPrivateNetworks) addAll(listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "fe80::/10", "fc00::/7", "fec0::/10"))
}

private fun parseNumericAddress(host: String): InetAddress? {
    val couldBeNumeric = ':' in host || host.all { character -> character.isDigit() || character == '.' }
    if (!couldBeNumeric) return null
    return runCatching { InetAddress.getByName(host) }.getOrNull()
}

private fun KiyoriProxyEndpoint.toJavaProxy(): Proxy =
    Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port))
