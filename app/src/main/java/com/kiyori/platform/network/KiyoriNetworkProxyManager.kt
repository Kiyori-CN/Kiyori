package com.kiyori.platform.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class KiyoriNetworkProxyManager private constructor(context: Context) {
    companion object {
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

    val configState: StateFlow<KiyoriNetworkProxyStoreState> = configStore.state
    val runtimeState: StateFlow<KiyoriMihomoRuntimeState> = runtime.state
    val probeState: StateFlow<KiyoriMihomoProbeState> = runtime.probeState
    val logEntries: StateFlow<List<KiyoriNetworkProxyLogEntry>> = proxyLog.entries

    fun exportLogText(): String = proxyLog.exportText()

    fun clearLog() = proxyLog.clear()

    fun scheduleStartupReconciliation() {
        runtime.scheduleStaleRuntimeCleanup()
    }

    fun currentConfig(): KiyoriNetworkProxyConfig = configStore.currentConfig()

    suspend fun updateConfig(
        transform: (KiyoriNetworkProxyConfig) -> KiyoriNetworkProxyConfig,
    ): KiyoriNetworkProxyConfig =
        mutationMutex.withLock {
            val updated = persistConfig(transform)
            reconcileSavedConfig(updated)
            updated
        }

    suspend fun replaceConfig(config: KiyoriNetworkProxyConfig): KiyoriNetworkProxyConfig =
        updateConfig { config }

    suspend fun reconcileEnabledState(
        config: KiyoriNetworkProxyConfig = currentConfig(),
    ) {
        if (!KiyoriNetworkProxyPolicy.requiresEmbeddedProxy(config)) {
            clearWebViewProxy()
            runtime.stop()
            return
        }
        try {
            val subscription =
                KiyoriNetworkProxyPolicy.validateEmbeddedStart(
                    config = config,
                    isSystemVpnActive = isSystemVpnActive(),
                )
            val endpoint = runtime.ensureReady(subscription, config.testUrl)
            if (
                KiyoriNetworkProxyPolicy.effectiveModuleMode(
                    config,
                    KiyoriNetworkModule.BROWSER,
                ) == KiyoriNetworkConnectionMode.PROXY
            ) {
                setWebViewProxy(endpoint, config.proxyPrivateNetworks)
            } else {
                clearWebViewProxy()
            }
        } catch (error: KiyoriNetworkException) {
            if (error.code == KiyoriNetworkErrorCode.VPN_CONFLICT) {
                clearWebViewProxy()
                runtime.stop()
            }
            throw error
        }
    }

    suspend fun refreshRuntimeState(): MihomoRuntimeSnapshot? {
        val config = currentConfig()
        reconcileEnabledState(config)
        if (!KiyoriNetworkProxyPolicy.requiresEmbeddedProxy(config)) return null
        return runtime.refreshActiveSnapshot()
    }

    suspend fun testActiveProxyConnection(): Int {
        val config = currentConfig()
        if (!KiyoriNetworkProxyPolicy.requiresEmbeddedProxy(config)) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.CONFIG_MISSING,
                "Enable at least one proxy route before testing the active connection.",
            )
        }
        val subscription =
            KiyoriNetworkProxyPolicy.validateEmbeddedStart(config, isSystemVpnActive())
        val endpoint = runtime.ensureReady(subscription, config.testUrl)
        val client =
            OkHttpClient.Builder()
                .proxy(endpoint.toJavaProxy())
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
        val request = Request.Builder().url(config.testUrl).get().build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw KiyoriNetworkException(
                            KiyoriNetworkErrorCode.PROXY_CONNECT_FAILED,
                            "The active proxy returned HTTP ${response.code} for the connection test.",
                        )
                    }
                    response.code
                }
            } catch (error: KiyoriNetworkException) {
                throw error
            } catch (error: Exception) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.PROXY_CONNECT_FAILED,
                    "The active proxy could not reach the connection-test endpoint.",
                    error,
                )
            }
        }
    }

    suspend fun resolveRoute(
        module: KiyoriNetworkModule,
        scriptPackageName: String? = null,
    ): Pair<KiyoriNetworkRoute, KiyoriProxyEndpoint?> {
        val config = currentConfig()
        val route =
            try {
                KiyoriNetworkProxyPolicy.resolve(
                    config = config,
                    module = module,
                    scriptPackageName = scriptPackageName,
                    isSystemVpnActive = isSystemVpnActive(),
                )
            } catch (error: KiyoriNetworkException) {
                if (error.code == KiyoriNetworkErrorCode.VPN_CONFLICT) runtime.stop()
                throw error
            }
        return when (route) {
            KiyoriNetworkRoute.Direct -> route to null
            KiyoriNetworkRoute.EmbeddedProxy -> {
                val subscription =
                    KiyoriNetworkProxyPolicy.activeSubscription(config)
                        ?: throw KiyoriNetworkException(
                            KiyoriNetworkErrorCode.CONFIG_MISSING,
                            "No active Clash or Mihomo subscription has been selected.",
                        )
                route to runtime.ensureReady(subscription, config.testUrl)
            }
        }
    }

    fun resolveRouteBlocking(
        module: KiyoriNetworkModule,
    ): KiyoriProxyEndpoint? =
        runBlocking(Dispatchers.IO) { resolveRoute(module).second }

    suspend fun applyRoute(
        builder: OkHttpClient.Builder,
        module: KiyoriNetworkModule,
        scriptPackageName: String? = null,
        mapFailures: Boolean = false,
    ): KiyoriNetworkRoute {
        val config = currentConfig()
        val (route, endpoint) = resolveRoute(module, scriptPackageName)
        if (endpoint == null) {
            builder.proxy(Proxy.NO_PROXY)
        } else {
            builder.proxySelector(
                ScopedKiyoriProxySelector(
                    proxy = endpoint.toJavaProxy(),
                    proxyPrivateNetworks = config.proxyPrivateNetworks,
                ),
            )
        }
        if (mapFailures) installFailureMapping(builder, route, module)
        return route
    }

    fun applyDynamicRoute(
        builder: OkHttpClient.Builder,
        module: KiyoriNetworkModule,
    ): OkHttpClient.Builder =
        builder.proxySelector(
            DynamicKiyoriProxySelector(
                manager = this,
                module = module,
            ),
        )

    fun openConnectionBlocking(
        url: URL,
        module: KiyoriNetworkModule,
    ): URLConnection {
        val (_, endpoint) = runBlocking(Dispatchers.IO) { resolveRoute(module) }
        return url.openConnection(endpoint?.toJavaProxy() ?: Proxy.NO_PROXY)
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
            runtime.validateConfiguration(subscription, current.testUrl)
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
            val sanitized = MihomoConfigSanitizer.sanitize(rawYaml)
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
            runtime.validateConfiguration(subscription, current.testUrl)
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
            val sanitized = MihomoConfigSanitizer.sanitize(rawYaml)
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
            runtime.validateConfiguration(updatedSubscription, current.testUrl)
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
        mutationMutex.withLock {
            val current = currentConfig()
            val subscription = KiyoriNetworkProxyPolicy.requireSubscription(current, subscriptionId)
            val normalizedNode = nodeName.trim()
            try {
                val (snapshot, delay) =
                    runtime.probeNodeDelay(subscription, normalizedNode, current.testUrl)
                val now = System.currentTimeMillis()
                val tested =
                    subscription.copy(
                        nodeTests =
                            mergeSnapshot(subscription.nodeTests, snapshot, now).map { result ->
                                if (result.name == normalizedNode) {
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
                persistSubscriptionReplacement(current, tested, reconcileActive = false)
                tested
            } catch (error: KiyoriNetworkException) {
                persistNodeFailure(current, subscription, normalizedNode)
                throw error
            }
        }

    suspend fun testSubscriptionGroup(
        subscriptionId: String,
        groupName: String,
    ): KiyoriProxySubscription =
        mutationMutex.withLock {
            val current = currentConfig()
            val subscription = KiyoriNetworkProxyPolicy.requireSubscription(current, subscriptionId)
            val normalizedGroup = groupName.trim()
            val (snapshot, delays) =
                runtime.probeGroupDelays(subscription, normalizedGroup, current.testUrl)
            val now = System.currentTimeMillis()
            val tested =
                subscription.copy(
                    nodeTests =
                        mergeSnapshot(subscription.nodeTests, snapshot, now).map { result ->
                            if (normalizedGroup !in result.groupNames) return@map result
                            val delay = delays[result.name]
                            if (delay == null) {
                                result.copy(
                                    delayMillis = null,
                                    status = MihomoNodeTestStatus.TIMEOUT,
                                    testedAtEpochMillis = now,
                                )
                            } else {
                                result.copy(
                                    delayMillis = delay,
                                    status = MihomoNodeTestStatus.SUCCESS,
                                    testedAtEpochMillis = now,
                                )
                            }
                        },
                )
            persistSubscriptionReplacement(current, tested, reconcileActive = false)
            tested
        }

    suspend fun reset(): KiyoriNetworkProxyConfig =
        mutationMutex.withLock {
            clearWebViewProxy()
            runtime.stop()
            withContext(Dispatchers.IO) { configStore.reset() }
        }

    fun isSystemVpnActive(): Boolean {
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
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
        runtime.validateConfiguration(updatedSubscription, current.testUrl)
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
        if (!KiyoriNetworkProxyPolicy.requiresEmbeddedProxy(config)) return null
        val active = KiyoriNetworkProxyPolicy.validateEmbeddedStart(config, isSystemVpnActive())
        return runtime.ensureReady(active, config.testUrl)
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
        current: KiyoriNetworkProxyConfig,
        subscription: KiyoriProxySubscription,
        nodeName: String,
    ) {
        val now = System.currentTimeMillis()
        val existing = subscription.nodeTests.firstOrNull { result -> result.name == nodeName }
        if (existing == null) return
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
        persistSubscriptionReplacement(current, failed, reconcileActive = false)
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
            reconcileEnabledState(config)
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
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.WEBVIEW_UNSUPPORTED,
                "The installed Android WebView does not support proxy overrides.",
            )
        }
        val builder =
            ProxyConfig.Builder()
                .addProxyRule("http://${endpoint.host}:${endpoint.port}")
                .addBypassRule("localhost")
                .addBypassRule("127.0.0.1")
                .addBypassRule("[::1]")
                .addBypassRule("<local>")
        if (!proxyPrivateNetworks) {
            builder.addBypassRule("10.*")
            builder.addBypassRule("192.168.*")
            (16..31).forEach { secondOctet ->
                builder.addBypassRule("172.$secondOctet.*")
            }
        }
        suspendCancellableCoroutine { continuation ->
            ProxyController.getInstance().setProxyOverride(
                builder.build(),
                ContextCompat.getMainExecutor(appContext),
            ) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    private suspend fun clearWebViewProxy() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        suspendCancellableCoroutine { continuation ->
            ProxyController.getInstance().clearProxyOverride(
                ContextCompat.getMainExecutor(appContext),
            ) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    private fun installFailureMapping(
        builder: OkHttpClient.Builder,
        route: KiyoriNetworkRoute,
        module: KiyoriNetworkModule,
    ) {
        builder.addInterceptor { chain ->
            try {
                chain.proceed(chain.request())
            } catch (error: KiyoriNetworkException) {
                throw error
            } catch (error: IOException) {
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
        if (host.isEmpty() || shouldBypassKiyoriProxy(host, config.proxyPrivateNetworks)) {
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
) : ProxySelector() {
    override fun select(uri: URI?): List<Proxy> {
        val host = uri?.host?.trim()?.lowercase().orEmpty()
        return if (host.isEmpty() || shouldBypassKiyoriProxy(host, proxyPrivateNetworks)) {
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

private fun parseNumericAddress(host: String): InetAddress? {
    val couldBeNumeric = ':' in host || host.all { character -> character.isDigit() || character == '.' }
    if (!couldBeNumeric) return null
    return runCatching { InetAddress.getByName(host) }.getOrNull()
}

private fun KiyoriProxyEndpoint.toJavaProxy(): Proxy =
    Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port))
