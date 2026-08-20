package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.Context
import android.util.AtomicFile
import com.ai.assistance.operit.util.AppLogger
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject

internal data class BrowserAdBlockCustomNetworkRule(
    val id: String,
    val rule: String,
    val enabled: Boolean,
    val createdAt: Long,
)

internal data class BrowserAdBlockCustomElementRule(
    val id: String,
    val domain: String,
    val selector: String,
    val enabled: Boolean,
    val createdAt: Long,
)

internal data class BrowserAdBlockSubscriptionElementRule(
    val domainExpression: String,
    val selector: String,
    val exception: Boolean,
)

internal data class BrowserAdBlockSubscription(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean,
    val group: BrowserAdBlockSubscriptionGroup = BrowserAdBlockSubscriptionGroup.CUSTOM,
    val builtIn: Boolean = false,
    val lastUpdatedAt: Long?,
    val lastError: String?,
    val ignoredLineCount: Int,
    val networkBlockingRuleCount: Int = 0,
    val networkExceptionRuleCount: Int = 0,
    val elementBlockingRuleCount: Int = 0,
    val elementExceptionRuleCount: Int = 0,
    val payloadSha256: String? = null,
    val payloadByteCount: Long? = null,
    val payloadStorageVersion: Int? = null,
) {
    val effectiveRuleCount: Int
        get() =
            networkBlockingRuleCount +
                networkExceptionRuleCount +
                elementBlockingRuleCount +
                elementExceptionRuleCount

    val hasLocalRules: Boolean
        get() = hasCommittedPayloadMetadata
}

internal data class BrowserAdBlockState(
    val enabled: Boolean = true,
    val autoUpdateBuiltInSubscriptions: Boolean = true,
    val allowlistedDomains: List<String> = emptyList(),
    val customNetworkRules: List<BrowserAdBlockCustomNetworkRule> = emptyList(),
    val customElementRules: List<BrowserAdBlockCustomElementRule> = emptyList(),
    val subscriptions: List<BrowserAdBlockSubscription> = emptyList(),
    val blockedRequestCount: Long = 0L,
    val ruleRevision: Long = 0L,
) {
    val activeNetworkRuleCount: Int
        get() =
            customNetworkRules.count(BrowserAdBlockCustomNetworkRule::enabled) +
                subscriptions
                    .asSequence()
                    .filter(BrowserAdBlockSubscription::enabled)
                    .sumOf(BrowserAdBlockSubscription::networkBlockingRuleCount)

    val activeNetworkExceptionRuleCount: Int
        get() =
            subscriptions
                .asSequence()
                .filter(BrowserAdBlockSubscription::enabled)
                .sumOf(BrowserAdBlockSubscription::networkExceptionRuleCount)

    val activeElementRuleCount: Int
        get() =
            customElementRules.count(BrowserAdBlockCustomElementRule::enabled) +
                subscriptions
                    .asSequence()
                    .filter(BrowserAdBlockSubscription::enabled)
                    .sumOf(BrowserAdBlockSubscription::elementBlockingRuleCount)

    val activeElementExceptionRuleCount: Int
        get() =
            subscriptions
                .asSequence()
                .filter(BrowserAdBlockSubscription::enabled)
                .sumOf(BrowserAdBlockSubscription::elementExceptionRuleCount)

    val readyBuiltInSubscriptionCount: Int
        get() =
            subscriptions.count { subscription ->
                subscription.builtIn &&
                    subscription.lastUpdatedAt != null &&
                    subscription.hasLocalRules
            }
}

internal data class BrowserAdBlockDomainClearResult(
    val removedNetworkRuleCount: Int,
    val removedElementRuleCount: Int,
) {
    val removedRuleCount: Int
        get() = removedNetworkRuleCount + removedElementRuleCount
}

internal data class BrowserAdBlockRefreshSummary(
    val refreshedCount: Int,
    val failedCount: Int,
)

internal data class BrowserAdBlockUnchangedRefreshCommit(
    val state: BrowserAdBlockState,
    val subscription: BrowserAdBlockSubscription,
)

internal enum class BrowserAdBlockSubscriptionRefreshOrigin {
    AUTOMATIC,
    ACTIVATION,
    MANUAL_ALL,
}

internal data class BrowserAdBlockSubscriptionRefreshProgress(
    val origin: BrowserAdBlockSubscriptionRefreshOrigin,
    val totalCount: Int,
    val completedCount: Int,
    val refreshedCount: Int,
    val failedCount: Int,
    val currentSubscriptionId: String?,
)

internal enum class BrowserAdBlockRuntimePhase {
    READING_SETTINGS,
    LOADING_COMPILED_RULES,
    COMPILING_RULES,
    READY,
    FAILED,
}

internal data class BrowserAdBlockRuntimeStatus(
    val phase: BrowserAdBlockRuntimePhase,
    val completedSubscriptionCount: Int = 0,
    val totalSubscriptionCount: Int = 0,
    val cacheHitCount: Int = 0,
    val cacheMissCount: Int = 0,
    val cacheInvalidCount: Int = 0,
    val compiledSubscriptionCount: Int = 0,
    val warningMessage: String? = null,
    val errorMessage: String? = null,
) {
    val ready: Boolean
        get() = phase == BrowserAdBlockRuntimePhase.READY
}

private data class BrowserAdBlockInitialSnapshot(
    val state: BrowserAdBlockState,
    val requiresPersistence: Boolean,
    val migratedPayloads: Map<String, ByteArray> = emptyMap(),
    val legacyPayloadIds: Set<String> = emptySet(),
)

internal fun mergeBrowserAdBlockBuiltInSubscriptions(
    existingSubscriptions: List<BrowserAdBlockSubscription>,
): List<BrowserAdBlockSubscription> {
    val remaining = existingSubscriptions.toMutableList()
    val builtIns =
        BrowserAdBlockBuiltInSubscriptions.map { definition ->
            val existing =
                remaining.firstOrNull { subscription -> subscription.id == definition.id }
                    ?: remaining.firstOrNull { subscription -> subscription.url == definition.url }
            remaining.removeAll { subscription ->
                subscription.id == definition.id || subscription.url == definition.url
            }
            if (existing == null) {
                BrowserAdBlockSubscription(
                    id = definition.id,
                    name = definition.name,
                    url = definition.url,
                    enabled = true,
                    group = definition.group,
                    builtIn = true,
                    lastUpdatedAt = null,
                    lastError = null,
                    ignoredLineCount = 0,
                )
            } else {
                existing.copy(
                    id = definition.id,
                    name = definition.name,
                    url = definition.url,
                    group = definition.group,
                    builtIn = true,
                )
            }
        }
    val custom =
        remaining
            .map { subscription ->
                subscription.copy(
                    group = BrowserAdBlockSubscriptionGroup.CUSTOM,
                    builtIn = false,
                )
            }
            .sortedBy(BrowserAdBlockSubscription::name)
    return builtIns + custom
}

internal class BrowserAdBlockStore private constructor(
    context: Context,
    private val httpClient: OkHttpClient,
) {
    private val applicationContext = context.applicationContext
    private val lock = Any()
    private val stateFile =
        AtomicFile(
            applicationContext.filesDir.resolve(BROWSER_AD_BLOCK_STATE_FILE_NAME),
        )
    private val subscriptionPayloadDirectory =
        applicationContext.filesDir.resolve(BROWSER_AD_BLOCK_SUBSCRIPTION_DIRECTORY_NAME).also {
            require(it.exists() || it.mkdirs()) {
                "Cannot create browser ad-block subscription directory: ${it.absolutePath}"
            }
        }
    private val compiledCacheDirectory =
        applicationContext.noBackupFilesDir.resolve(BROWSER_AD_BLOCK_COMPILED_DIRECTORY_NAME).also {
            require(it.exists() || it.mkdirs()) {
                "Cannot create browser ad-block compiled cache directory: ${it.absolutePath}"
            }
        }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val subscriptionRefreshMutex = Mutex()
    private val blockedRequestCounter = AtomicLong(0L)
    private val blockedCountPublishScheduled = AtomicBoolean(false)
    private val _state =
        MutableStateFlow(
            BrowserAdBlockState(
                subscriptions = mergeBrowserAdBlockBuiltInSubscriptions(emptyList()),
            ),
        )
    private val _runtimeStatus =
        MutableStateFlow(
            BrowserAdBlockRuntimeStatus(
                phase = BrowserAdBlockRuntimePhase.READING_SETTINGS,
            ),
        )
    private val _refreshingSubscriptionIds = MutableStateFlow<Set<String>>(emptySet())
    private val _subscriptionRefreshProgress =
        MutableStateFlow<BrowserAdBlockSubscriptionRefreshProgress?>(null)
    private var customRuleSet = BrowserAdBlockCompiledRuleSet.EMPTY
    private var subscriptionRuntimePartitions =
        emptyMap<String, BrowserAdBlockCompiledPartition>()
    private var customRuntimeEngine = BrowserAdBlockEngine.EMPTY
    private var subscriptionRuntimeEngine = BrowserAdBlockEngine.EMPTY
    private var runtimeEngine = BrowserAdBlockEngine.EMPTY
    private var stateReady = false

    @Volatile
    private var matcher = BrowserAdBlockMatcher.EMPTY

    val state: StateFlow<BrowserAdBlockState> = _state.asStateFlow()
    val runtimeStatus: StateFlow<BrowserAdBlockRuntimeStatus> = _runtimeStatus.asStateFlow()
    val refreshingSubscriptionIds: StateFlow<Set<String>> =
        _refreshingSubscriptionIds.asStateFlow()
    val subscriptionRefreshProgress: StateFlow<BrowserAdBlockSubscriptionRefreshProgress?> =
        _subscriptionRefreshProgress.asStateFlow()
    val current: BrowserAdBlockState
        get() = _state.value

    init {
        ioScope.launch {
            initializeRuntime()
        }
    }

    fun decide(
        pageUrl: String,
        requestUrl: String,
    ): BrowserAdBlockDecision? = matcher.decide(pageUrl, requestUrl)

    fun decide(context: BrowserAdBlockRequestContext): BrowserAdBlockDecision? =
        matcher.decide(context)

    fun selectorsForPage(pageUrl: String): List<String> = matcher.selectorsForPage(pageUrl)

    fun elementDecisionsForPage(pageUrl: String): List<BrowserAdBlockElementDecision> =
        matcher.elementDecisionsForPage(pageUrl)

    fun recordBlockedRequest() {
        blockedRequestCounter.incrementAndGet()
        if (blockedCountPublishScheduled.compareAndSet(false, true)) {
            ioScope.launch {
                do {
                    delay(BROWSER_AD_BLOCK_BLOCKED_COUNT_PUBLISH_INTERVAL_MILLIS)
                    publishBlockedRequestCount()
                    blockedCountPublishScheduled.set(false)
                    // 增量可能恰好落在发布与 scheduled 清零之间。重新抢占发布权可保证
                    // 最后一次拦截也会进入 UI，而不会一直等待下一条网络请求来补发。
                } while (
                    blockedRequestCounter.get() != _state.value.blockedRequestCount &&
                        blockedCountPublishScheduled.compareAndSet(false, true)
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        mutatePersistedState { current -> current.copy(enabled = enabled) }
    }

    fun setAutoUpdateBuiltInSubscriptions(enabled: Boolean) {
        mutatePersistedState { current ->
            current.copy(autoUpdateBuiltInSubscriptions = enabled)
        }
        if (enabled) {
            ioScope.launch {
                refreshDueBuiltInSubscriptions()
            }
        }
    }

    fun addOrUpdateNetworkRule(
        id: String?,
        rule: String,
        enabled: Boolean = true,
    ): BrowserAdBlockCustomNetworkRule {
        val normalizedRule = normalizeCustomNetworkRule(rule)
        val now = System.currentTimeMillis()
        var savedRule: BrowserAdBlockCustomNetworkRule? = null
        mutatePersistedState { current ->
            val existing =
                id?.let { targetId ->
                    current.customNetworkRules.singleOrNull { candidate -> candidate.id == targetId }
                }
            val candidate =
                BrowserAdBlockCustomNetworkRule(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    rule = normalizedRule,
                    enabled = enabled,
                    createdAt = existing?.createdAt ?: now,
                )
            require(
                current.customNetworkRules.none { other ->
                    other.id != candidate.id && other.rule.equals(candidate.rule, ignoreCase = true)
                },
            ) {
                "Duplicate browser ad-block network rule: $normalizedRule"
            }
            savedRule = candidate
            current.copy(
                customNetworkRules =
                    current.customNetworkRules
                        .filterNot { existingRule -> existingRule.id == candidate.id }
                        .plus(candidate)
                        .sortedByDescending(BrowserAdBlockCustomNetworkRule::createdAt),
            )
        }
        return checkNotNull(savedRule)
    }

    fun setNetworkRuleEnabled(
        id: String,
        enabled: Boolean,
    ) {
        mutatePersistedState { current ->
            require(current.customNetworkRules.any { rule -> rule.id == id }) {
                "Unknown browser ad-block network rule: $id"
            }
            current.copy(
                customNetworkRules =
                    current.customNetworkRules.map { rule ->
                        if (rule.id == id) rule.copy(enabled = enabled) else rule
                    },
            )
        }
    }

    fun removeNetworkRule(id: String) {
        mutatePersistedState { current ->
            require(current.customNetworkRules.any { rule -> rule.id == id }) {
                "Unknown browser ad-block network rule: $id"
            }
            current.copy(
                customNetworkRules =
                    current.customNetworkRules.filterNot { rule -> rule.id == id },
            )
        }
    }

    fun addOrUpdateElementRule(
        id: String?,
        domain: String,
        selector: String,
        enabled: Boolean = true,
    ): BrowserAdBlockCustomElementRule {
        val normalizedDomain =
            requireNotNull(normalizeBrowserAdBlockDomainInput(domain)) {
                "Invalid browser ad-block element-rule domain: $domain"
            }
        val normalizedSelector = selector.trim()
        require(isValidBrowserAdBlockSelector(normalizedSelector)) {
            "Invalid browser ad-block element selector"
        }
        val now = System.currentTimeMillis()
        var savedRule: BrowserAdBlockCustomElementRule? = null
        mutatePersistedState { current ->
            val existing =
                id?.let { targetId ->
                    current.customElementRules.singleOrNull { candidate -> candidate.id == targetId }
                }
            val candidate =
                BrowserAdBlockCustomElementRule(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    domain = normalizedDomain,
                    selector = normalizedSelector,
                    enabled = enabled,
                    createdAt = existing?.createdAt ?: now,
                )
            require(
                current.customElementRules.none { other ->
                    other.id != candidate.id &&
                        other.domain == candidate.domain &&
                        other.selector == candidate.selector
                },
            ) {
                "Duplicate browser ad-block element rule for $normalizedDomain"
            }
            savedRule = candidate
            current.copy(
                customElementRules =
                    current.customElementRules
                        .filterNot { existingRule -> existingRule.id == candidate.id }
                        .plus(candidate)
                        .sortedByDescending(BrowserAdBlockCustomElementRule::createdAt),
            )
        }
        return checkNotNull(savedRule)
    }

    fun setElementRuleEnabled(
        id: String,
        enabled: Boolean,
    ) {
        mutatePersistedState { current ->
            require(current.customElementRules.any { rule -> rule.id == id }) {
                "Unknown browser ad-block element rule: $id"
            }
            current.copy(
                customElementRules =
                    current.customElementRules.map { rule ->
                        if (rule.id == id) rule.copy(enabled = enabled) else rule
                    },
            )
        }
    }

    fun removeElementRule(id: String) {
        mutatePersistedState { current ->
            require(current.customElementRules.any { rule -> rule.id == id }) {
                "Unknown browser ad-block element rule: $id"
            }
            current.copy(
                customElementRules =
                    current.customElementRules.filterNot { rule -> rule.id == id },
            )
        }
    }

    fun clearCustomRulesForDomain(domain: String): BrowserAdBlockDomainClearResult {
        val normalizedDomain =
            requireNotNull(normalizeBrowserAdBlockDomainInput(domain)) {
                "Invalid browser ad-block clear domain: $domain"
            }
        var result = BrowserAdBlockDomainClearResult(0, 0)
        mutatePersistedState { current ->
            val remainingNetworkRules =
                current.customNetworkRules.filterNot { rule ->
                    isBrowserAdBlockNetworkRuleTargetedAtDomain(rule.rule, normalizedDomain)
                }
            val remainingElementRules =
                current.customElementRules.filterNot { rule ->
                    rule.domain == normalizedDomain
                }
            result =
                BrowserAdBlockDomainClearResult(
                    removedNetworkRuleCount =
                        current.customNetworkRules.size - remainingNetworkRules.size,
                    removedElementRuleCount =
                        current.customElementRules.size - remainingElementRules.size,
                )
            current.copy(
                customNetworkRules = remainingNetworkRules,
                customElementRules = remainingElementRules,
            )
        }
        return result
    }

    fun addAllowlistedDomain(domain: String): String {
        val normalized =
            requireNotNull(normalizeBrowserAdBlockDomainInput(domain)) {
                "Invalid browser ad-block allowlist domain: $domain"
            }
        mutatePersistedState { current ->
            require(normalized !in current.allowlistedDomains) {
                "Browser ad-block allowlist already contains: $normalized"
            }
            current.copy(
                allowlistedDomains = current.allowlistedDomains.plus(normalized).sorted(),
            )
        }
        return normalized
    }

    fun removeAllowlistedDomain(domain: String) {
        val normalized =
            requireNotNull(normalizeBrowserAdBlockDomainInput(domain)) {
                "Invalid browser ad-block allowlist domain: $domain"
            }
        mutatePersistedState { current ->
            require(normalized in current.allowlistedDomains) {
                "Browser ad-block allowlist does not contain: $normalized"
            }
            current.copy(
                allowlistedDomains =
                    current.allowlistedDomains.filterNot { candidate -> candidate == normalized },
            )
        }
    }

    fun addOrUpdateSubscription(
        id: String?,
        name: String,
        url: String,
        enabled: Boolean = true,
    ): BrowserAdBlockSubscription {
        val normalizedName = name.trim()
        require(normalizedName.isNotBlank()) { "Browser ad-block subscription name is blank" }
        require(normalizedName.length <= BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_NAME_LENGTH) {
            "Browser ad-block subscription name is too long"
        }
        val normalizedUrl = normalizeBrowserAdBlockSubscriptionUrl(url)
        var savedSubscription: BrowserAdBlockSubscription? = null
        var removeStoredPayload = false
        mutatePersistedState { current ->
            val existing =
                id?.let { targetId ->
                    current.subscriptions.singleOrNull { subscription ->
                        subscription.id == targetId
                    }
                }
            require(id == null || existing != null) {
                "Unknown browser ad-block subscription: $id"
            }
            require(existing?.builtIn != true) {
                "Built-in browser ad-block subscriptions cannot be edited"
            }
            removeStoredPayload = existing != null && existing.url != normalizedUrl
            val subscription =
                if (existing == null) {
                    BrowserAdBlockSubscription(
                        id = UUID.randomUUID().toString(),
                        name = normalizedName,
                        url = normalizedUrl,
                        enabled = enabled,
                        lastUpdatedAt = null,
                        lastError = null,
                        ignoredLineCount = 0,
                        networkBlockingRuleCount = 0,
                        networkExceptionRuleCount = 0,
                        elementBlockingRuleCount = 0,
                        elementExceptionRuleCount = 0,
                    )
                } else {
                    existing.copy(
                        name = normalizedName,
                        url = normalizedUrl,
                        enabled = enabled,
                        lastUpdatedAt =
                            existing.lastUpdatedAt.takeIf { existing.url == normalizedUrl },
                        lastError =
                            existing.lastError.takeIf { existing.url == normalizedUrl },
                        ignoredLineCount =
                            existing.ignoredLineCount.takeIf { existing.url == normalizedUrl } ?: 0,
                        networkBlockingRuleCount =
                            existing.networkBlockingRuleCount.takeIf {
                                existing.url == normalizedUrl
                            } ?: 0,
                        networkExceptionRuleCount =
                            existing.networkExceptionRuleCount.takeIf {
                                existing.url == normalizedUrl
                            } ?: 0,
                        elementBlockingRuleCount =
                            existing.elementBlockingRuleCount.takeIf {
                                existing.url == normalizedUrl
                            } ?: 0,
                        elementExceptionRuleCount =
                            existing.elementExceptionRuleCount.takeIf {
                                existing.url == normalizedUrl
                            } ?: 0,
                        payloadSha256 =
                            existing.payloadSha256.takeIf {
                                existing.url == normalizedUrl
                            },
                        payloadByteCount =
                            existing.payloadByteCount.takeIf {
                                existing.url == normalizedUrl
                            },
                        payloadStorageVersion =
                            existing.payloadStorageVersion.takeIf {
                                existing.url == normalizedUrl
                            },
                    )
                }
            require(
                current.subscriptions.none { other ->
                    other.id != subscription.id && other.url == subscription.url
                },
            ) {
                "Duplicate browser ad-block subscription URL: $normalizedUrl"
            }
            savedSubscription = subscription
            current.copy(
                subscriptions =
                    mergeBrowserAdBlockBuiltInSubscriptions(
                        current.subscriptions
                            .filterNot { existingSubscription ->
                                existingSubscription.id == subscription.id
                            }
                            .plus(subscription),
                    ),
            )
        }
        return checkNotNull(savedSubscription).also { subscription ->
            if (removeStoredPayload) {
                deleteSubscriptionArtifacts(subscription.id)
            }
        }
    }

    fun setSubscriptionEnabled(
        id: String,
        enabled: Boolean,
    ) {
        mutatePersistedState { current ->
            require(current.subscriptions.any { subscription -> subscription.id == id }) {
                "Unknown browser ad-block subscription: $id"
            }
            current.copy(
                subscriptions =
                    current.subscriptions.map { subscription ->
                        if (subscription.id == id) {
                            subscription.copy(enabled = enabled)
                        } else {
                            subscription
                        }
                },
            )
        }
        if (enabled) {
            scheduleMissingSubscriptionRefresh(
                ids = setOf(id),
                origin = BrowserAdBlockSubscriptionRefreshOrigin.ACTIVATION,
            )
        }
    }

    fun setSubscriptionGroupEnabled(
        group: BrowserAdBlockSubscriptionGroup,
        enabled: Boolean,
    ) {
        require(group != BrowserAdBlockSubscriptionGroup.CUSTOM) {
            "Custom browser ad-block subscriptions do not have a group switch"
        }
        mutatePersistedState { current ->
            require(current.subscriptions.any { subscription -> subscription.group == group }) {
                "Unknown browser ad-block subscription group: $group"
            }
            current.copy(
                subscriptions =
                    current.subscriptions.map { subscription ->
                        if (subscription.group == group) {
                            subscription.copy(enabled = enabled)
                        } else {
                            subscription
                        }
                },
            )
        }
        if (enabled) {
            scheduleMissingSubscriptionRefresh(
                ids =
                    current.subscriptions
                        .filter { subscription -> subscription.group == group }
                        .map(BrowserAdBlockSubscription::id)
                        .toSet(),
                origin = BrowserAdBlockSubscriptionRefreshOrigin.ACTIVATION,
            )
        }
    }

    fun removeSubscription(id: String) {
        val subscription =
            current.subscriptions.singleOrNull { candidate -> candidate.id == id }
                ?: throw IllegalArgumentException("Unknown browser ad-block subscription: $id")
        require(!subscription.builtIn) {
            "Built-in browser ad-block subscriptions cannot be removed"
        }
        mutatePersistedState { current ->
            current.copy(
                subscriptions =
                    current.subscriptions.filterNot { subscription -> subscription.id == id },
            )
        }
        deleteSubscriptionArtifacts(id)
    }

    suspend fun refreshSubscription(id: String): Result<BrowserAdBlockSubscription> =
        withContext(Dispatchers.IO) {
            subscriptionRefreshMutex.withLock {
                refreshSubscriptionLocked(id)
            }
        }

    suspend fun refreshAllEnabledSubscriptions(): BrowserAdBlockRefreshSummary =
        withContext(Dispatchers.IO) {
            subscriptionRefreshMutex.withLock {
                val ids =
                    current.subscriptions
                        .filter(BrowserAdBlockSubscription::enabled)
                        .map(BrowserAdBlockSubscription::id)
                refreshSubscriptionsLocked(
                    ids = ids,
                    origin = BrowserAdBlockSubscriptionRefreshOrigin.MANUAL_ALL,
                )
            }
        }

    private fun refreshSubscriptionLocked(
        id: String,
    ): Result<BrowserAdBlockSubscription> {
        setSubscriptionRefreshing(id, refreshing = true)
        try {
            val subscription =
                current.subscriptions.singleOrNull { candidate -> candidate.id == id }
                    ?: return Result.failure(
                        IllegalArgumentException(
                            "Unknown browser ad-block subscription: $id",
                        ),
                    )
            return try {
                val bytes = downloadSubscription(subscription)
                val payloadSha256 = browserAdBlockSha256(bytes)
                val updatedAt = System.currentTimeMillis()
                if (
                    subscription.matchesCommittedPayload(
                        payloadSha256 = payloadSha256,
                        payloadByteCount = bytes.size.toLong(),
                    )
                ) {
                    return Result.success(
                        commitUnchangedSubscriptionRefresh(
                            id = subscription.id,
                            expectedUrl = subscription.url,
                            expectedName = subscription.name,
                            updatedAt = updatedAt,
                        ),
                    )
                }
                val compilation =
                    compileSubscriptionPayload(
                        bytes = bytes,
                        subscription = subscription,
                    )
                check(compilation.counts.effectiveRuleCount > 0) {
                    "订阅中没有可用规则"
                }
                val refreshedSubscription =
                    mergeBrowserAdBlockRefreshedSubscription(
                        current = subscription,
                        expectedUrl = subscription.url,
                        expectedName = subscription.name,
                        counts = compilation.counts,
                        updatedAt = updatedAt,
                        payloadSha256 = payloadSha256,
                        payloadByteCount = bytes.size.toLong(),
                    )
                val partition =
                    compileSubscriptionPartition(
                        compilation = compilation,
                    )
                // 内容寻址载荷和编译快照先完整落盘，状态最后提交。进程在此前终止时，
                // 旧状态仍引用旧内容；新文件只会成为后续可安全清理的未引用派生物。
                writeSubscriptionPayload(refreshedSubscription, bytes)
                try {
                    BrowserAdBlockCompiledCacheCodec.write(
                        target = compiledCacheFile(refreshedSubscription),
                        identity = refreshedSubscription.compiledCacheIdentity(),
                        partition = partition,
                    )
                } catch (writeError: Exception) {
                    AppLogger.e(
                        BROWSER_AD_BLOCK_TAG,
                        "Failed to persist refreshed compiled subscription cache id=${subscription.id}",
                        writeError,
                    )
                    publishCompiledCacheWarning()
                }
                val committed =
                    commitRefreshedSubscription(
                        id = subscription.id,
                        expectedUrl = subscription.url,
                        expectedName = subscription.name,
                        subscription = refreshedSubscription,
                        partition = partition,
                    )
                pruneSubscriptionArtifacts(committed)
                Result.success(committed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                recordSubscriptionRefreshFailure(id, error)
                Result.failure(error)
            }
        } finally {
            setSubscriptionRefreshing(id, refreshing = false)
        }
    }

    private fun refreshSubscriptionsLocked(
        ids: List<String>,
        origin: BrowserAdBlockSubscriptionRefreshOrigin,
    ): BrowserAdBlockRefreshSummary {
        if (ids.isEmpty()) {
            return BrowserAdBlockRefreshSummary(
                refreshedCount = 0,
                failedCount = 0,
            )
        }
        var refreshedCount = 0
        var failedCount = 0
        _subscriptionRefreshProgress.value =
            BrowserAdBlockSubscriptionRefreshProgress(
                origin = origin,
                totalCount = ids.size,
                completedCount = 0,
                refreshedCount = 0,
                failedCount = 0,
                currentSubscriptionId = null,
            )
        try {
            ids.forEachIndexed { index, id ->
                _subscriptionRefreshProgress.value =
                    BrowserAdBlockSubscriptionRefreshProgress(
                        origin = origin,
                        totalCount = ids.size,
                        completedCount = index,
                        refreshedCount = refreshedCount,
                        failedCount = failedCount,
                        currentSubscriptionId = id,
                    )
                refreshSubscriptionLocked(id).fold(
                    onSuccess = { refreshedCount += 1 },
                    onFailure = { failedCount += 1 },
                )
                _subscriptionRefreshProgress.value =
                    BrowserAdBlockSubscriptionRefreshProgress(
                        origin = origin,
                        totalCount = ids.size,
                        completedCount = index + 1,
                        refreshedCount = refreshedCount,
                        failedCount = failedCount,
                        currentSubscriptionId = null,
                    )
            }
        } finally {
            _subscriptionRefreshProgress.value = null
        }
        return BrowserAdBlockRefreshSummary(
            refreshedCount = refreshedCount,
            failedCount = failedCount,
        )
    }

    private fun scheduleMissingSubscriptionRefresh(
        ids: Set<String>,
        origin: BrowserAdBlockSubscriptionRefreshOrigin,
    ) {
        ioScope.launch {
            subscriptionRefreshMutex.withLock {
                val idsToRefresh =
                    current.subscriptions
                        .filter { subscription ->
                            subscription.id in ids &&
                                subscription.enabled &&
                                !subscription.hasLocalRules
                        }
                        .map(BrowserAdBlockSubscription::id)
                refreshSubscriptionsLocked(idsToRefresh, origin)
            }
        }
    }

    private suspend fun initializeRuntime() {
        val initializationStartedAt = System.nanoTime()
        try {
            val initialSnapshot = readInitialSnapshot()
            initialSnapshot.migratedPayloads.forEach { (id, payload) ->
                val subscription =
                    checkNotNull(
                        initialSnapshot.state.subscriptions.singleOrNull { candidate ->
                            candidate.id == id
                        },
                    ) {
                        "Migrated browser ad-block payload has no subscription metadata: $id"
                    }
                writeSubscriptionPayload(subscription, payload)
            }
            var loadedState = initialSnapshot.state
            synchronized(lock) {
                _state.value =
                    loadedState.copy(
                        blockedRequestCount = blockedRequestCounter.get(),
                    )
            }
            val subscriptionsToCompile =
                loadedState.subscriptions.filter(BrowserAdBlockSubscription::hasLocalRules)
            _runtimeStatus.value =
                BrowserAdBlockRuntimeStatus(
                    phase = BrowserAdBlockRuntimePhase.LOADING_COMPILED_RULES,
                    totalSubscriptionCount = subscriptionsToCompile.size,
                )
            val compiledCustomRules = compileCustomRuleSet(loadedState)
            var cacheHitCount = 0
            var cacheMissCount = 0
            var cacheInvalidCount = 0
            var compiledSubscriptionCount = 0
            var cacheWarning: String? = null
            val compiledSubscriptions =
                buildMap<String, BrowserAdBlockCompiledPartition> {
                    subscriptionsToCompile.forEachIndexed { index, originalSubscription ->
                        var subscription = originalSubscription
                        val cacheResult =
                            runCatching {
                                BrowserAdBlockCompiledCacheCodec.read(
                                    source = compiledCacheFile(subscription),
                                    expectedIdentity = subscription.compiledCacheIdentity(),
                                )
                            }
                        cacheResult.fold(
                            onSuccess = { partition ->
                                cacheHitCount += 1
                                put(subscription.id, partition)
                            },
                            onFailure = { cacheError ->
                                val invalidReason =
                                    (cacheError as? BrowserAdBlockCompiledCacheException)
                                        ?.invalidReason
                                if (invalidReason == "missing") {
                                    cacheMissCount += 1
                                } else {
                                    cacheInvalidCount += 1
                                    AppLogger.w(
                                        BROWSER_AD_BLOCK_TAG,
                                        "Invalid compiled subscription cache id=${subscription.id} reason=${invalidReason ?: cacheError::class.java.simpleName}",
                                    )
                                }
                                _runtimeStatus.value =
                                    BrowserAdBlockRuntimeStatus(
                                        phase = BrowserAdBlockRuntimePhase.COMPILING_RULES,
                                        completedSubscriptionCount = index,
                                        totalSubscriptionCount = subscriptionsToCompile.size,
                                        cacheHitCount = cacheHitCount,
                                        cacheMissCount = cacheMissCount,
                                        cacheInvalidCount = cacheInvalidCount,
                                        compiledSubscriptionCount = compiledSubscriptionCount,
                                        warningMessage = cacheWarning,
                                    )
                                val compiledResult =
                                    runCatching {
                                        val payload =
                                            checkNotNull(readSubscriptionPayload(subscription)) {
                                                "本地订阅规则缺失，等待重新同步"
                                            }
                                        val compilation =
                                            compileSubscriptionPayload(
                                                bytes = payload,
                                                subscription = subscription,
                                            )
                                        check(compilation.counts.effectiveRuleCount > 0) {
                                            "订阅中没有可用规则"
                                        }
                                        subscription =
                                            subscription.withRuleCounts(
                                                counts = compilation.counts,
                                                updatedAt = subscription.lastUpdatedAt,
                                                clearError = false,
                                            )
                                        val partition =
                                            compileSubscriptionPartition(
                                                compilation = compilation,
                                            )
                                        try {
                                            BrowserAdBlockCompiledCacheCodec.write(
                                                target = compiledCacheFile(subscription),
                                                identity = subscription.compiledCacheIdentity(),
                                                partition = partition,
                                            )
                                        } catch (writeError: Exception) {
                                            AppLogger.e(
                                                BROWSER_AD_BLOCK_TAG,
                                                "Failed to persist compiled subscription cache id=${subscription.id}",
                                                writeError,
                                            )
                                            cacheWarning =
                                                "部分本地编译快照未保存，下次启动会重新编译"
                                        }
                                        partition
                                    }
                                compiledResult.fold(
                                    onSuccess = { partition ->
                                        compiledSubscriptionCount += 1
                                        put(subscription.id, partition)
                                        loadedState =
                                            loadedState.updateSubscriptionMetadata(subscription)
                                    },
                                    onFailure = { error ->
                                        AppLogger.e(
                                            BROWSER_AD_BLOCK_TAG,
                                            "Failed to compile stored subscription id=${subscription.id}",
                                            error,
                                        )
                                        loadedState =
                                            loadedState.updateSubscriptionMetadata(
                                                subscription.withoutCommittedPayload(
                                                    errorMessage =
                                                        browserAdBlockSubscriptionRefreshErrorMessage(
                                                            error,
                                                        ),
                                                ),
                                            )
                                    },
                                )
                            },
                        )
                        _runtimeStatus.value =
                            BrowserAdBlockRuntimeStatus(
                                phase =
                                    if (compiledSubscriptionCount > 0) {
                                        BrowserAdBlockRuntimePhase.COMPILING_RULES
                                    } else {
                                        BrowserAdBlockRuntimePhase.LOADING_COMPILED_RULES
                                    },
                                completedSubscriptionCount = index + 1,
                                totalSubscriptionCount = subscriptionsToCompile.size,
                                cacheHitCount = cacheHitCount,
                                cacheMissCount = cacheMissCount,
                                cacheInvalidCount = cacheInvalidCount,
                                compiledSubscriptionCount = compiledSubscriptionCount,
                                warningMessage = cacheWarning,
                            )
                    }
                }
            val compiledCustomEngine = compileCustomRuntimeEngine(compiledCustomRules)
            val compiledSubscriptionEngine =
                compileSubscriptionRuntimeEngine(compiledSubscriptions)
            val compiledEngine =
                combineRuntimeEngines(
                    customEngine = compiledCustomEngine,
                    subscriptionEngine = compiledSubscriptionEngine,
                )
            synchronized(lock) {
                val revisionedState =
                    loadedState.copy(
                        blockedRequestCount = blockedRequestCounter.get(),
                        ruleRevision =
                            if (
                                loadedState.enabled &&
                                    (
                                        compiledCustomRules.networkRules.isNotEmpty() ||
                                            compiledCustomRules.elementRules.isNotEmpty() ||
                                            compiledSubscriptions.isNotEmpty()
                                        )
                            ) {
                                1L
                            } else {
                                0L
                            },
                    )
                if (
                    initialSnapshot.requiresPersistence ||
                        initialSnapshot.state != loadedState
                ) {
                    writePersistedState(revisionedState)
                }
                customRuleSet = compiledCustomRules
                subscriptionRuntimePartitions = compiledSubscriptions
                customRuntimeEngine = compiledCustomEngine
                subscriptionRuntimeEngine = compiledSubscriptionEngine
                runtimeEngine = compiledEngine
                matcher = compileMatcher(revisionedState, compiledEngine)
                stateReady = true
                _state.value = revisionedState
                _runtimeStatus.value =
                    BrowserAdBlockRuntimeStatus(
                        phase = BrowserAdBlockRuntimePhase.READY,
                        completedSubscriptionCount = subscriptionsToCompile.size,
                        totalSubscriptionCount = subscriptionsToCompile.size,
                        cacheHitCount = cacheHitCount,
                        cacheMissCount = cacheMissCount,
                        cacheInvalidCount = cacheInvalidCount,
                        compiledSubscriptionCount = compiledSubscriptionCount,
                        warningMessage = cacheWarning,
                    )
            }
            initialSnapshot.legacyPayloadIds.forEach(::deleteLegacySubscriptionPayload)
            pruneObsoleteCompiledCacheDirectories()
            loadedState.subscriptions
                .filter(BrowserAdBlockSubscription::hasLocalRules)
                .forEach(::pruneSubscriptionArtifacts)
            AppLogger.i(
                BROWSER_AD_BLOCK_TAG,
                "Runtime ready subscriptions=${subscriptionsToCompile.size} cacheHits=$cacheHitCount cacheMisses=$cacheMissCount cacheInvalid=$cacheInvalidCount compiled=$compiledSubscriptionCount elapsedMillis=${(System.nanoTime() - initializationStartedAt) / 1_000_000L}",
            )
            refreshDueBuiltInSubscriptions()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.e(
                BROWSER_AD_BLOCK_TAG,
                "Failed to initialize browser ad-block runtime",
                error,
            )
            _runtimeStatus.value =
                BrowserAdBlockRuntimeStatus(
                    phase = BrowserAdBlockRuntimePhase.FAILED,
                    errorMessage =
                        error.message
                            ?.take(BROWSER_AD_BLOCK_MAX_ERROR_LENGTH)
                            ?: "广告拦截规则初始化失败",
                )
        }
    }

    private fun publishBlockedRequestCount() {
        synchronized(lock) {
            val latestCount = blockedRequestCounter.get()
            if (_state.value.blockedRequestCount != latestCount) {
                _state.value =
                    _state.value.copy(
                        blockedRequestCount = latestCount,
                    )
            }
        }
    }

    private fun mutatePersistedState(
        transform: (BrowserAdBlockState) -> BrowserAdBlockState,
    ) {
        synchronized(lock) {
            check(stateReady) {
                "广告拦截规则仍在初始化，请稍后再试"
            }
            val currentState = _state.value
            val transformed =
                transform(currentState).copy(
                    blockedRequestCount = blockedRequestCounter.get(),
                )
            if (transformed == currentState) {
                return
            }
            val customRulesChanged =
                currentState.customNetworkRules != transformed.customNetworkRules ||
                    currentState.customElementRules != transformed.customElementRules
            val nextCustomRuleSet =
                if (customRulesChanged) {
                    compileCustomRuleSet(transformed)
                } else {
                    customRuleSet
                }
            val retainedSubscriptionPartitions =
                subscriptionRuntimePartitions.filterKeys { id ->
                    transformed.subscriptions.any { subscription ->
                        subscription.id == id && subscription.hasLocalRules
                    }
                }
            val subscriptionRulesChanged =
                retainedSubscriptionPartitions.keys != subscriptionRuntimePartitions.keys
            val nextCustomRuntimeEngine =
                if (customRulesChanged) {
                    compileCustomRuntimeEngine(nextCustomRuleSet)
                } else {
                    customRuntimeEngine
                }
            val nextSubscriptionRuntimeEngine =
                if (subscriptionRulesChanged) {
                    compileSubscriptionRuntimeEngine(retainedSubscriptionPartitions)
                } else {
                    subscriptionRuntimeEngine
                }
            val engineChanged = customRulesChanged || subscriptionRulesChanged
            val nextRuntimeEngine =
                if (engineChanged) {
                    combineRuntimeEngines(
                        customEngine = nextCustomRuntimeEngine,
                        subscriptionEngine = nextSubscriptionRuntimeEngine,
                    )
                } else {
                    runtimeEngine
                }
            val runtimeChanged =
                engineChanged ||
                    browserAdBlockRuntimeConfigurationChanged(
                        previous = currentState,
                        updated = transformed,
                    )
            val updated =
                transformed.copy(
                    ruleRevision =
                        if (runtimeChanged) {
                            currentState.ruleRevision + 1L
                        } else {
                            currentState.ruleRevision
                        },
            )
            writePersistedState(updated)
            customRuleSet = nextCustomRuleSet
            subscriptionRuntimePartitions = retainedSubscriptionPartitions
            customRuntimeEngine = nextCustomRuntimeEngine
            subscriptionRuntimeEngine = nextSubscriptionRuntimeEngine
            runtimeEngine = nextRuntimeEngine
            matcher = compileMatcher(updated, nextRuntimeEngine)
            publishState(updated)
        }
    }

    private fun compileCustomRuleSet(
        state: BrowserAdBlockState,
    ): BrowserAdBlockCompiledRuleSet {
        val networkRules =
            buildList {
                state.customNetworkRules.forEach { rule ->
                    add(
                        BrowserAdBlockNetworkRuleSpec(
                            id = rule.id,
                            rule = rule.rule,
                            source = BrowserAdBlockRuleSource.CUSTOM,
                            sourceName = "自定义网址规则",
                            enabled = rule.enabled,
                        ),
                    )
                }
            }
        val elementRules =
            buildList {
                state.customElementRules.forEach { rule ->
                    add(
                        BrowserAdBlockElementRuleSpec(
                            id = rule.id,
                            domainExpression = rule.domain,
                            selector = rule.selector,
                            source = BrowserAdBlockRuleSource.CUSTOM,
                            sourceName = "自定义元素规则",
                            enabled = rule.enabled,
                        ),
                    )
                }
            }
        return BrowserAdBlockCompiledRuleSet.compile(
            id = BROWSER_AD_BLOCK_CUSTOM_RULE_SET_ID,
            networkRules = networkRules,
            elementRules = elementRules,
        )
    }

    private fun compileSubscriptionPayload(
        bytes: ByteArray,
        subscription: BrowserAdBlockSubscription,
    ): BrowserAdBlockSubscriptionCompilationResult =
        bytes
            .inputStream()
            .bufferedReader(StandardCharsets.UTF_8)
            .use { reader ->
                compileBrowserAdBlockSubscription(
                    reader = reader,
                    subscriptionId = subscription.id,
                    subscriptionName = subscription.name,
                )
            }

    private fun compileSubscriptionPartition(
        compilation: BrowserAdBlockSubscriptionCompilationResult,
    ): BrowserAdBlockCompiledPartition =
        compileBrowserAdBlockCompiledPartition(
            compilation.ruleSet,
        )

    private fun compileCustomRuntimeEngine(
        customRules: BrowserAdBlockCompiledRuleSet,
    ): BrowserAdBlockEngine =
        BrowserAdBlockEngine.compile(
            listOf(customRules),
        )

    private fun compileSubscriptionRuntimeEngine(
        subscriptionPartitions: Map<String, BrowserAdBlockCompiledPartition>,
    ): BrowserAdBlockEngine =
        BrowserAdBlockEngine.combine(
            subscriptionPartitions.values.map(BrowserAdBlockCompiledPartition::engine),
        )

    private fun combineRuntimeEngines(
        customEngine: BrowserAdBlockEngine,
        subscriptionEngine: BrowserAdBlockEngine,
    ): BrowserAdBlockEngine =
        BrowserAdBlockEngine.combine(
            listOf(
                customEngine,
                subscriptionEngine,
            ),
        )

    private fun compileMatcher(
        state: BrowserAdBlockState,
        engine: BrowserAdBlockEngine,
    ): BrowserAdBlockMatcher =
        engine.createMatcher(
            enabled = state.enabled,
            allowlistedDomains = state.allowlistedDomains,
            activeRuleSetIds =
                buildList {
                    add(BROWSER_AD_BLOCK_CUSTOM_RULE_SET_ID)
                    state.subscriptions
                        .filter { subscription ->
                            subscription.enabled &&
                                subscription.id in subscriptionRuntimePartitions
                        }
                        .forEach { subscription ->
                            add(subscription.id)
                        }
                },
        )

    private fun downloadSubscription(
        subscription: BrowserAdBlockSubscription,
    ): ByteArray {
        val request =
            Request.Builder()
                .url(subscription.url)
                .header("Accept", "text/plain, application/octet-stream;q=0.9, */*;q=0.1")
                .header("User-Agent", BROWSER_AD_BLOCK_SUBSCRIPTION_USER_AGENT)
                .build()
        return httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "订阅服务器返回 HTTP ${response.code}"
            }
            val body = checkNotNull(response.body) {
                "订阅响应没有内容"
            }
            val declaredLength = body.contentLength()
            check(
                declaredLength < 0L ||
                    declaredLength <= BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES,
            ) {
                "订阅文件超过 ${formatByteCount(BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES)} 上限"
            }
            val bytes =
                readBrowserAdBlockSubscriptionPayload(
                    source = body.source(),
                    declaredLength = declaredLength,
                    maxBytes = BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES,
                )
            validateBrowserAdBlockSubscriptionPayload(bytes)
            bytes
        }
    }

    private fun commitRefreshedSubscription(
        id: String,
        expectedUrl: String,
        expectedName: String,
        subscription: BrowserAdBlockSubscription,
        partition: BrowserAdBlockCompiledPartition,
    ): BrowserAdBlockSubscription =
        synchronized(lock) {
            val currentState = _state.value
            val currentSubscription =
                currentState.subscriptions.singleOrNull { candidate -> candidate.id == id }
                    ?: throw IllegalStateException(
                        "广告拦截订阅在更新期间已被删除：$id",
                    )
            require(currentSubscription.url == expectedUrl) {
                "广告拦截订阅地址在更新期间发生变化，请重新刷新"
            }
            require(currentSubscription.name == expectedName) {
                "广告拦截订阅名称在更新期间发生变化，请重新刷新"
            }
            require(subscription.id == currentSubscription.id) {
                "广告拦截订阅更新结果不属于当前订阅"
            }
            val committedSubscription =
                mergeBrowserAdBlockPreparedSubscriptionAtCommit(
                    current = currentSubscription,
                    expectedUrl = expectedUrl,
                    expectedName = expectedName,
                    prepared = subscription,
                )
            val updatedState =
                currentState.copy(
                    subscriptions =
                        currentState.subscriptions.map { candidate ->
                            if (candidate.id == committedSubscription.id) {
                                committedSubscription
                            } else {
                                candidate
                            }
                        },
                    blockedRequestCount = blockedRequestCounter.get(),
                )
            val revisionedState =
                updatedState.copy(
                    ruleRevision = currentState.ruleRevision + 1L,
                )
            val updatedSubscriptionPartitions =
                subscriptionRuntimePartitions + (committedSubscription.id to partition)
            val updatedSubscriptionRuntimeEngine =
                compileSubscriptionRuntimeEngine(updatedSubscriptionPartitions)
            val updatedRuntimeEngine =
                combineRuntimeEngines(
                    customEngine = customRuntimeEngine,
                    subscriptionEngine = updatedSubscriptionRuntimeEngine,
                )
            writePersistedState(revisionedState)
            subscriptionRuntimePartitions = updatedSubscriptionPartitions
            subscriptionRuntimeEngine = updatedSubscriptionRuntimeEngine
            runtimeEngine = updatedRuntimeEngine
            matcher = compileMatcher(revisionedState, updatedRuntimeEngine)
            publishState(revisionedState)
            committedSubscription
        }

    private fun commitUnchangedSubscriptionRefresh(
        id: String,
        expectedUrl: String,
        expectedName: String,
        updatedAt: Long,
    ): BrowserAdBlockSubscription =
        synchronized(lock) {
            val currentState = _state.value
            val commit =
                browserAdBlockUnchangedRefreshCommit(
                    currentState = currentState,
                    id = id,
                    expectedUrl = expectedUrl,
                    expectedName = expectedName,
                    updatedAt = updatedAt,
                )
            val updatedState =
                commit.state.copy(
                    blockedRequestCount = blockedRequestCounter.get(),
                )
            writePersistedState(updatedState)
            publishState(updatedState)
            commit.subscription
        }

    private fun publishCompiledCacheWarning() {
        _runtimeStatus.update { currentStatus ->
            currentStatus.copy(
                warningMessage = "部分本地编译快照未保存，下次启动会重新编译",
            )
        }
    }

    private fun publishState(state: BrowserAdBlockState) {
        _state.value =
            state.copy(
                blockedRequestCount = blockedRequestCounter.get(),
            )
    }

    private fun recordSubscriptionRefreshFailure(
        id: String,
        error: Throwable,
    ) {
        AppLogger.e(
            BROWSER_AD_BLOCK_TAG,
            "Failed to refresh browser ad-block subscription id=$id",
            error,
        )
        mutatePersistedState { currentState ->
            val currentSubscription =
                currentState.subscriptions.singleOrNull { candidate -> candidate.id == id }
                    ?: return@mutatePersistedState currentState
            currentState.copy(
                subscriptions =
                    currentState.subscriptions.map { candidate ->
                        if (candidate.id == id) {
                            currentSubscription.copy(
                                lastError =
                                    browserAdBlockSubscriptionRefreshErrorMessage(error)
                                        .take(BROWSER_AD_BLOCK_MAX_ERROR_LENGTH),
                            )
                        } else {
                            candidate
                        }
                    },
            )
        }
    }

    private fun setSubscriptionRefreshing(
        id: String,
        refreshing: Boolean,
    ) {
        synchronized(lock) {
            _refreshingSubscriptionIds.value =
                if (refreshing) {
                    _refreshingSubscriptionIds.value + id
                } else {
                    _refreshingSubscriptionIds.value - id
                }
        }
    }

    private suspend fun refreshDueBuiltInSubscriptions() {
        withContext(Dispatchers.IO) {
            subscriptionRefreshMutex.withLock {
                val now = System.currentTimeMillis()
                val ids =
                    current.subscriptions
                        .filter { subscription ->
                            if (!subscription.builtIn || !subscription.enabled) {
                                false
                            } else if (!subscription.hasLocalRules) {
                                true
                            } else if (!current.autoUpdateBuiltInSubscriptions) {
                                false
                            } else {
                                val refreshInterval =
                                    browserAdBlockBuiltInSubscriptionDefinition(subscription.id)
                                        ?.refreshIntervalMillis
                                        ?: return@filter false
                                val lastUpdatedAt = subscription.lastUpdatedAt ?: return@filter true
                                now - lastUpdatedAt >= refreshInterval
                            }
                        }
                        .map(BrowserAdBlockSubscription::id)
                refreshSubscriptionsLocked(
                    ids = ids,
                    origin = BrowserAdBlockSubscriptionRefreshOrigin.AUTOMATIC,
                )
            }
        }
    }

    private fun legacySubscriptionPayloadFile(id: String): AtomicFile {
        require(BROWSER_AD_BLOCK_SUBSCRIPTION_ID_REGEX.matches(id)) {
            "Invalid browser ad-block subscription ID: $id"
        }
        return AtomicFile(subscriptionPayloadDirectory.resolve("$id.txt"))
    }

    private fun subscriptionPayloadFile(
        subscription: BrowserAdBlockSubscription,
    ): AtomicFile {
        val payloadSha256 =
            requireNotNull(subscription.payloadSha256) {
                "Browser ad-block subscription has no payload SHA-256"
            }
        require(BROWSER_AD_BLOCK_SHA_256_REGEX.matches(payloadSha256)) {
            "Invalid browser ad-block payload SHA-256"
        }
        val directory =
            subscriptionPayloadDirectory.resolve(subscription.id).also { target ->
                require(target.exists() || target.mkdirs()) {
                    "Cannot create browser ad-block subscription payload directory: ${target.absolutePath}"
                }
            }
        return AtomicFile(directory.resolve("$payloadSha256.txt"))
    }

    private fun compiledCacheFile(
        subscription: BrowserAdBlockSubscription,
    ): File {
        val payloadSha256 =
            requireNotNull(subscription.payloadSha256) {
                "Browser ad-block subscription has no payload SHA-256"
            }
        require(BROWSER_AD_BLOCK_SHA_256_REGEX.matches(payloadSha256)) {
            "Invalid browser ad-block payload SHA-256"
        }
        val sourceNameSha256 = browserAdBlockSha256(subscription.name)
        val directory =
            compiledCacheDirectory
                .resolve(BrowserAdBlockCompilerContract.CACHE_FORMAT_VERSION.toString())
                .resolve(BrowserAdBlockCompilerContract.COMPILER_CONTRACT_ID)
                .resolve(subscription.id)
        return directory.resolve("$payloadSha256-$sourceNameSha256.bin")
    }

    private fun writeSubscriptionPayload(
        subscription: BrowserAdBlockSubscription,
        bytes: ByteArray,
    ) {
        require(subscription.payloadByteCount == bytes.size.toLong()) {
            "Browser ad-block payload byte count changed before persistence"
        }
        require(subscription.payloadSha256 == browserAdBlockSha256(bytes)) {
            "Browser ad-block payload SHA-256 changed before persistence"
        }
        val payloadFile = subscriptionPayloadFile(subscription)
        if (payloadFile.baseFile.isFile) {
            val existingPayloadIsValid =
                runCatching {
                    readSubscriptionPayload(subscription)
                }.isSuccess
            if (existingPayloadIsValid) {
                return
            }
            AppLogger.w(
                BROWSER_AD_BLOCK_TAG,
                "Replacing invalid browser ad-block content-addressed payload id=${subscription.id}",
            )
        }
        var output = payloadFile.startWrite()
        try {
            output.write(bytes)
            payloadFile.finishWrite(output)
        } catch (error: Throwable) {
            payloadFile.failWrite(output)
            throw error
        }
    }

    private fun readSubscriptionPayload(
        subscription: BrowserAdBlockSubscription,
    ): ByteArray? {
        val payloadFile = subscriptionPayloadFile(subscription)
        if (!payloadFile.baseFile.exists()) {
            return null
        }
        return payloadFile.openRead().use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES) {
                "Stored subscription is larger than $BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES bytes"
            }
            require(bytes.size.toLong() == subscription.payloadByteCount) {
                "Stored subscription byte count does not match committed metadata"
            }
            require(browserAdBlockSha256(bytes) == subscription.payloadSha256) {
                "Stored subscription SHA-256 does not match committed metadata"
            }
            bytes
        }
    }

    private fun deleteLegacySubscriptionPayload(id: String) {
        val payloadFile = legacySubscriptionPayloadFile(id)
        if (payloadFile.baseFile.exists() && !payloadFile.baseFile.delete()) {
            AppLogger.w(
                BROWSER_AD_BLOCK_TAG,
                "Could not delete legacy browser ad-block subscription payload id=$id",
            )
        }
        val backupFile = File(payloadFile.baseFile.path + ".bak")
        if (backupFile.exists() && !backupFile.delete()) {
            AppLogger.w(
                BROWSER_AD_BLOCK_TAG,
                "Could not delete legacy browser ad-block subscription backup id=$id",
            )
        }
    }

    private fun deleteSubscriptionArtifacts(id: String) {
        require(BROWSER_AD_BLOCK_SUBSCRIPTION_ID_REGEX.matches(id)) {
            "Invalid browser ad-block subscription ID: $id"
        }
        deleteLegacySubscriptionPayload(id)
        deleteFilesAndDirectory(
            subscriptionPayloadDirectory.resolve(id),
            "subscription payload",
        )
        deleteFilesAndDirectory(
            compiledCacheDirectory
                .resolve(BrowserAdBlockCompilerContract.CACHE_FORMAT_VERSION.toString())
                .resolve(BrowserAdBlockCompilerContract.COMPILER_CONTRACT_ID)
                .resolve(id),
            "compiled subscription cache",
        )
    }

    private fun pruneSubscriptionArtifacts(
        subscription: BrowserAdBlockSubscription,
    ) {
        if (!subscription.hasCommittedPayloadMetadata) {
            return
        }
        val payloadFile = subscriptionPayloadFile(subscription).baseFile
        val payloadBackup = File(payloadFile.path + ".bak")
        subscriptionPayloadDirectory.resolve(subscription.id).listFiles()?.forEach { candidate ->
            if (candidate != payloadFile && candidate != payloadBackup && !candidate.delete()) {
                AppLogger.w(
                    BROWSER_AD_BLOCK_TAG,
                    "Could not delete unreferenced browser ad-block payload id=${subscription.id} file=${candidate.name}",
                )
            }
        }
        val cacheFile = compiledCacheFile(subscription)
        cacheFile.parentFile?.listFiles()?.forEach { candidate ->
            if (candidate != cacheFile && !candidate.delete()) {
                AppLogger.w(
                    BROWSER_AD_BLOCK_TAG,
                    "Could not delete unreferenced browser ad-block compiled cache id=${subscription.id} file=${candidate.name}",
                )
            }
        }
    }

    private fun pruneObsoleteCompiledCacheDirectories() {
        val activeFormatDirectory =
            compiledCacheDirectory.resolve(
                BrowserAdBlockCompilerContract.CACHE_FORMAT_VERSION.toString(),
            )
        compiledCacheDirectory.listFiles()?.forEach { formatDirectory ->
            if (formatDirectory != activeFormatDirectory) {
                deleteCompiledCacheTree(formatDirectory)
            }
        }
        val activeContractDirectory =
            activeFormatDirectory.resolve(
                BrowserAdBlockCompilerContract.COMPILER_CONTRACT_ID,
            )
        activeFormatDirectory.listFiles()?.forEach { contractDirectory ->
            if (contractDirectory != activeContractDirectory) {
                deleteCompiledCacheTree(contractDirectory)
            }
        }
    }

    private fun deleteCompiledCacheTree(entry: File) {
        val rootPath = compiledCacheDirectory.absoluteFile.toPath().normalize()
        val entryPath = entry.absoluteFile.toPath().normalize()
        require(entryPath != rootPath && entryPath.startsWith(rootPath)) {
            "Compiled browser ad-block cache cleanup escaped its private root"
        }
        if (java.nio.file.Files.isSymbolicLink(entry.toPath())) {
            if (!entry.delete()) {
                AppLogger.w(
                    BROWSER_AD_BLOCK_TAG,
                    "Could not delete obsolete browser ad-block compiled cache link=${entry.name}",
                )
            }
            return
        }
        val canonicalRootPath = compiledCacheDirectory.canonicalFile.toPath()
        val canonicalEntryPath = entry.canonicalFile.toPath()
        require(
            canonicalEntryPath != canonicalRootPath &&
                canonicalEntryPath.startsWith(canonicalRootPath),
        ) {
            "Compiled browser ad-block cache cleanup escaped its canonical private root"
        }
        if (entry.isDirectory) {
            entry.listFiles()?.forEach(::deleteCompiledCacheTree)
        }
        if (entry.exists() && !entry.delete()) {
            AppLogger.w(
                BROWSER_AD_BLOCK_TAG,
                "Could not delete obsolete browser ad-block compiled cache entry=${entry.name}",
            )
        }
    }

    private fun deleteFilesAndDirectory(
        directory: File,
        artifactName: String,
    ) {
        if (!directory.exists()) {
            return
        }
        directory.listFiles()?.forEach { candidate ->
            if (candidate.isFile && !candidate.delete()) {
                AppLogger.w(
                    BROWSER_AD_BLOCK_TAG,
                    "Could not delete browser ad-block $artifactName file=${candidate.name}",
                )
            }
        }
        if (!directory.delete()) {
            AppLogger.w(
                BROWSER_AD_BLOCK_TAG,
                "Could not delete browser ad-block $artifactName directory=${directory.name}",
            )
        }
    }

    private fun readInitialSnapshot(): BrowserAdBlockInitialSnapshot {
        val file = stateFile.baseFile
        if (!file.exists()) {
            return BrowserAdBlockInitialSnapshot(
                state =
                    BrowserAdBlockState(
                        subscriptions = mergeBrowserAdBlockBuiltInSubscriptions(emptyList()),
                    ),
                requiresPersistence = true,
            )
        }
        val json =
            stateFile.openRead().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                JSONObject(reader.readText())
            }
        val schemaVersion = json.getInt("schemaVersion")
        val loadedSnapshot =
            when (schemaVersion) {
                1 -> readSchemaOneSnapshot(json)
                2 -> readSchemaTwoSnapshot(json)
                BROWSER_AD_BLOCK_SCHEMA_VERSION -> readSchemaThreeSnapshot(json)
                else ->
                    error("Unsupported browser ad-block state schema: $schemaVersion")
            }
        val mergedSubscriptions =
            mergeBrowserAdBlockBuiltInSubscriptions(loadedSnapshot.state.subscriptions)
        val state = loadedSnapshot.state.copy(subscriptions = mergedSubscriptions)
        validateStoredState(state)
        val migratedPayloads =
            loadedSnapshot.migratedPayloads.mapKeys { (legacyId, _) ->
                val legacySubscription =
                    loadedSnapshot.state.subscriptions.singleOrNull { subscription ->
                        subscription.id == legacyId
                    }
                mergedSubscriptions
                    .singleOrNull { subscription ->
                        subscription.id == legacyId ||
                            (
                                legacySubscription != null &&
                                    subscription.url == legacySubscription.url
                                )
                    }
                    ?.id
                    ?: legacyId
            }
        return loadedSnapshot.copy(
            state = state,
            migratedPayloads = migratedPayloads,
            requiresPersistence =
                loadedSnapshot.requiresPersistence ||
                    schemaVersion != BROWSER_AD_BLOCK_SCHEMA_VERSION ||
                    mergedSubscriptions != loadedSnapshot.state.subscriptions,
        )
    }

    private fun readSchemaOneSnapshot(json: JSONObject): BrowserAdBlockInitialSnapshot {
        val migratedPayloads = mutableMapOf<String, ByteArray>()
        val subscriptions =
            json.getJSONArray("subscriptions").mapObjects { item ->
                val networkRules = item.getJSONArray("networkRules").mapStrings()
                val elementRules =
                    item.getJSONArray("elementRules").mapObjects(::readStoredElementRule)
                val id = item.getString("id")
                val migratedPayload =
                    if (networkRules.isNotEmpty() || elementRules.isNotEmpty()) {
                        buildString {
                            appendLine("[Adblock Plus 2.0]")
                            networkRules.forEach(::appendLine)
                            elementRules.forEach { rule ->
                                append(rule.domainExpression)
                                append(if (rule.exception) "#@#" else "##")
                                appendLine(rule.selector)
                            }
                        }.toByteArray(StandardCharsets.UTF_8)
                    } else {
                        null
                    }
                if (migratedPayload != null) {
                    migratedPayloads[id] = migratedPayload
                }
                BrowserAdBlockSubscription(
                    id = id,
                    name = item.getString("name"),
                    url = normalizeBrowserAdBlockSubscriptionUrl(item.getString("url")),
                    enabled = item.getBoolean("enabled"),
                    lastUpdatedAt =
                        item.optLong("lastUpdatedAt", -1L).takeIf { value -> value >= 0L },
                    lastError =
                        item.optString("lastError", "").takeIf(String::isNotBlank),
                    ignoredLineCount = item.getInt("ignoredLineCount"),
                    networkBlockingRuleCount =
                        networkRules.count { rule -> !rule.trim().startsWith("@@") },
                    networkExceptionRuleCount =
                        networkRules.count { rule -> rule.trim().startsWith("@@") },
                    elementBlockingRuleCount =
                        elementRules.count { rule -> !rule.exception },
                    elementExceptionRuleCount =
                        elementRules.count(BrowserAdBlockSubscriptionElementRule::exception),
                    payloadSha256 = migratedPayload?.let(::browserAdBlockSha256),
                    payloadByteCount = migratedPayload?.size?.toLong(),
                    payloadStorageVersion =
                        migratedPayload?.let {
                            BrowserAdBlockCompilerContract.PAYLOAD_STORAGE_VERSION
                        },
                )
            }
        return BrowserAdBlockInitialSnapshot(
            state =
                readStoredStateWithoutSubscriptions(json).copy(
                    subscriptions = subscriptions,
                ),
            requiresPersistence = true,
            migratedPayloads = migratedPayloads,
        )
    }

    private fun readSchemaTwoSnapshot(json: JSONObject): BrowserAdBlockInitialSnapshot {
        val migratedPayloads = mutableMapOf<String, ByteArray>()
        val legacyPayloadIds = mutableSetOf<String>()
        val subscriptions =
            json.getJSONArray("subscriptions").mapObjects { item ->
                val metadata =
                    BrowserAdBlockSubscription(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        url = normalizeBrowserAdBlockSubscriptionUrl(item.getString("url")),
                        enabled = item.getBoolean("enabled"),
                        group =
                            BrowserAdBlockSubscriptionGroup.valueOf(
                                item.optString(
                                    "group",
                                    BrowserAdBlockSubscriptionGroup.CUSTOM.name,
                                ),
                            ),
                        builtIn = item.optBoolean("builtIn", false),
                        lastUpdatedAt =
                            item.optLong("lastUpdatedAt", -1L).takeIf { value -> value >= 0L },
                        lastError =
                            item.optString("lastError", "").takeIf(String::isNotBlank),
                        ignoredLineCount = item.optInt("ignoredLineCount", 0),
                        networkBlockingRuleCount =
                            item.optInt("networkBlockingRuleCount", 0),
                        networkExceptionRuleCount =
                            item.optInt("networkExceptionRuleCount", 0),
                        elementBlockingRuleCount =
                            item.optInt("elementBlockingRuleCount", 0),
                        elementExceptionRuleCount =
                            item.optInt("elementExceptionRuleCount", 0),
                    )
                if (metadata.effectiveRuleCount <= 0) {
                    if (legacySubscriptionPayloadFile(metadata.id).baseFile.exists()) {
                        legacyPayloadIds += metadata.id
                    }
                    metadata
                } else {
                    val payloadFile = legacySubscriptionPayloadFile(metadata.id).baseFile
                    if (!payloadFile.exists()) {
                        metadata.withoutCommittedPayload("本地订阅规则缺失，等待重新同步")
                    } else {
                        val payload =
                            legacySubscriptionPayloadFile(metadata.id).openRead().use { input ->
                                input.readBytes().also { bytes ->
                                    require(bytes.size <= BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES) {
                                        "Stored subscription is larger than $BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES bytes"
                                    }
                                }
                            }
                        legacyPayloadIds += metadata.id
                        if (payload.isEmpty()) {
                            metadata.withoutCommittedPayload("本地订阅规则为空，等待重新同步")
                        } else {
                            migratedPayloads[metadata.id] = payload
                            metadata.copy(
                                payloadSha256 = browserAdBlockSha256(payload),
                                payloadByteCount = payload.size.toLong(),
                                payloadStorageVersion =
                                    BrowserAdBlockCompilerContract.PAYLOAD_STORAGE_VERSION,
                            )
                        }
                    }
                }
            }
        val state =
            readStoredStateWithoutSubscriptions(json).copy(
                autoUpdateBuiltInSubscriptions =
                    json.optBoolean("autoUpdateBuiltInSubscriptions", true),
                subscriptions = subscriptions,
            )
        return BrowserAdBlockInitialSnapshot(
            state = state,
            requiresPersistence = true,
            migratedPayloads = migratedPayloads,
            legacyPayloadIds = legacyPayloadIds,
        )
    }

    private fun readSchemaThreeSnapshot(json: JSONObject): BrowserAdBlockInitialSnapshot {
        var metadataChanged = false
        val legacyPayloadIds = mutableSetOf<String>()
        val subscriptions =
            json.getJSONArray("subscriptions").mapObjects { item ->
                val metadata =
                    BrowserAdBlockSubscription(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        url = normalizeBrowserAdBlockSubscriptionUrl(item.getString("url")),
                        enabled = item.getBoolean("enabled"),
                        group =
                            BrowserAdBlockSubscriptionGroup.valueOf(
                                item.optString(
                                    "group",
                                    BrowserAdBlockSubscriptionGroup.CUSTOM.name,
                                ),
                            ),
                        builtIn = item.optBoolean("builtIn", false),
                        lastUpdatedAt =
                            item.optLong("lastUpdatedAt", -1L).takeIf { value -> value >= 0L },
                        lastError =
                            item.optString("lastError", "").takeIf(String::isNotBlank),
                        ignoredLineCount = item.optInt("ignoredLineCount", 0),
                        networkBlockingRuleCount =
                            item.optInt("networkBlockingRuleCount", 0),
                        networkExceptionRuleCount =
                            item.optInt("networkExceptionRuleCount", 0),
                        elementBlockingRuleCount =
                            item.optInt("elementBlockingRuleCount", 0),
                        elementExceptionRuleCount =
                            item.optInt("elementExceptionRuleCount", 0),
                        payloadSha256 =
                            item.optString("payloadSha256", "").takeIf(String::isNotBlank),
                        payloadByteCount =
                            item.optLong("payloadByteCount", -1L).takeIf { value -> value >= 0L },
                        payloadStorageVersion =
                            item.optInt("payloadStorageVersion", -1)
                                .takeIf { value -> value >= 0 },
                    )
                if (legacySubscriptionPayloadFile(metadata.id).baseFile.exists()) {
                    legacyPayloadIds += metadata.id
                }
                if (!metadata.hasCommittedPayloadMetadata) {
                    metadata
                } else {
                    val payloadFile = subscriptionPayloadFile(metadata).baseFile
                    if (
                        !payloadFile.isFile ||
                            payloadFile.length() != metadata.payloadByteCount
                    ) {
                        metadataChanged = true
                        metadata.withoutCommittedPayload("本地订阅规则缺失，等待重新同步")
                    } else {
                        metadata
                    }
                }
            }
        val state =
            readStoredStateWithoutSubscriptions(json).copy(
                autoUpdateBuiltInSubscriptions =
                    json.optBoolean("autoUpdateBuiltInSubscriptions", true),
                subscriptions = subscriptions,
            )
        return BrowserAdBlockInitialSnapshot(
            state = state,
            requiresPersistence = metadataChanged,
            legacyPayloadIds = legacyPayloadIds,
        )
    }

    private fun readStoredStateWithoutSubscriptions(
        json: JSONObject,
    ): BrowserAdBlockState =
        BrowserAdBlockState(
            enabled = json.getBoolean("enabled"),
            autoUpdateBuiltInSubscriptions =
                json.optBoolean("autoUpdateBuiltInSubscriptions", true),
            allowlistedDomains =
                json.getJSONArray("allowlistedDomains").mapStrings().map { domain ->
                    requireNotNull(normalizeBrowserAdBlockDomainInput(domain)) {
                        "Stored browser ad-block allowlist domain is invalid: $domain"
                    }
                },
            customNetworkRules =
                json.getJSONArray("customNetworkRules").mapObjects { item ->
                    BrowserAdBlockCustomNetworkRule(
                        id = item.getString("id"),
                        rule = normalizeCustomNetworkRule(item.getString("rule")),
                        enabled = item.getBoolean("enabled"),
                        createdAt = item.getLong("createdAt"),
                    )
                },
            customElementRules =
                json.getJSONArray("customElementRules").mapObjects { item ->
                    BrowserAdBlockCustomElementRule(
                        id = item.getString("id"),
                        domain =
                            requireNotNull(
                                normalizeBrowserAdBlockDomainInput(item.getString("domain")),
                            ) {
                                "Stored browser ad-block element domain is invalid"
                            },
                        selector =
                            item.getString("selector").trim().also { selector ->
                                require(isValidBrowserAdBlockSelector(selector)) {
                                    "Stored browser ad-block element selector is invalid"
                                }
                            },
                        enabled = item.getBoolean("enabled"),
                        createdAt = item.getLong("createdAt"),
                    )
                },
        )

    private fun readStoredElementRule(
        rule: JSONObject,
    ): BrowserAdBlockSubscriptionElementRule =
        BrowserAdBlockSubscriptionElementRule(
            domainExpression = rule.getString("domainExpression"),
            selector =
                rule.getString("selector").also { selector ->
                    require(isValidBrowserAdBlockSelector(selector)) {
                        "Stored subscription element selector is invalid"
                    }
                },
            exception = rule.getBoolean("exception"),
        )

    private fun validateStoredState(state: BrowserAdBlockState) {
        require(state.allowlistedDomains.distinct().size == state.allowlistedDomains.size) {
            "Stored browser ad-block allowlist contains duplicates"
        }
        require(state.customNetworkRules.map { rule -> rule.id }.distinct().size == state.customNetworkRules.size) {
            "Stored browser ad-block network-rule IDs contain duplicates"
        }
        require(state.customElementRules.map { rule -> rule.id }.distinct().size == state.customElementRules.size) {
            "Stored browser ad-block element-rule IDs contain duplicates"
        }
        require(state.subscriptions.map { subscription -> subscription.id }.distinct().size == state.subscriptions.size) {
            "Stored browser ad-block subscription IDs contain duplicates"
        }
        state.subscriptions.forEach { subscription ->
            val payloadFields =
                listOf(
                    subscription.payloadSha256,
                    subscription.payloadByteCount,
                    subscription.payloadStorageVersion,
                )
            require(payloadFields.all { value -> value == null } || payloadFields.none { value -> value == null }) {
                "Stored browser ad-block payload metadata is incomplete"
            }
            require(
                (subscription.effectiveRuleCount > 0) ==
                    payloadFields.none { value -> value == null },
            ) {
                "Stored browser ad-block rule counts and payload identity are inconsistent"
            }
            if (subscription.payloadSha256 != null) {
                require(BROWSER_AD_BLOCK_SHA_256_REGEX.matches(subscription.payloadSha256)) {
                    "Stored browser ad-block payload SHA-256 is invalid"
                }
                require(subscription.payloadByteCount in 1..BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES) {
                    "Stored browser ad-block payload byte count is invalid"
                }
                require(
                    subscription.payloadStorageVersion ==
                        BrowserAdBlockCompilerContract.PAYLOAD_STORAGE_VERSION,
                ) {
                    "Stored browser ad-block payload storage version is unsupported"
                }
            }
        }
    }

    private fun writePersistedState(state: BrowserAdBlockState) {
        val json =
            JSONObject()
                .put("schemaVersion", BROWSER_AD_BLOCK_SCHEMA_VERSION)
                .put("enabled", state.enabled)
                .put(
                    "autoUpdateBuiltInSubscriptions",
                    state.autoUpdateBuiltInSubscriptions,
                )
                .put("allowlistedDomains", JSONArray(state.allowlistedDomains))
                .put(
                    "customNetworkRules",
                    JSONArray().apply {
                        state.customNetworkRules.forEach { rule ->
                            put(
                                JSONObject()
                                    .put("id", rule.id)
                                    .put("rule", rule.rule)
                                    .put("enabled", rule.enabled)
                                    .put("createdAt", rule.createdAt),
                            )
                        }
                    },
                )
                .put(
                    "customElementRules",
                    JSONArray().apply {
                        state.customElementRules.forEach { rule ->
                            put(
                                JSONObject()
                                    .put("id", rule.id)
                                    .put("domain", rule.domain)
                                    .put("selector", rule.selector)
                                    .put("enabled", rule.enabled)
                                    .put("createdAt", rule.createdAt),
                            )
                        }
                    },
                )
                .put(
                    "subscriptions",
                    JSONArray().apply {
                        state.subscriptions.forEach { subscription ->
                            put(
                                JSONObject()
                                    .put("id", subscription.id)
                                    .put("name", subscription.name)
                                    .put("url", subscription.url)
                                    .put("enabled", subscription.enabled)
                                    .put("group", subscription.group.name)
                                    .put("builtIn", subscription.builtIn)
                                    .put("lastUpdatedAt", subscription.lastUpdatedAt ?: -1L)
                                    .put("lastError", subscription.lastError.orEmpty())
                                    .put("ignoredLineCount", subscription.ignoredLineCount)
                                    .put(
                                        "networkBlockingRuleCount",
                                        subscription.networkBlockingRuleCount,
                                    )
                                    .put(
                                        "networkExceptionRuleCount",
                                        subscription.networkExceptionRuleCount,
                                    )
                                    .put(
                                        "elementBlockingRuleCount",
                                        subscription.elementBlockingRuleCount,
                                    )
                                    .put(
                                        "elementExceptionRuleCount",
                                        subscription.elementExceptionRuleCount,
                                    )
                                    .put(
                                        "payloadSha256",
                                        subscription.payloadSha256.orEmpty(),
                                    )
                                    .put(
                                        "payloadByteCount",
                                        subscription.payloadByteCount ?: -1L,
                                    )
                                    .put(
                                        "payloadStorageVersion",
                                        subscription.payloadStorageVersion ?: -1,
                                    ),
                            )
                        }
                    },
                )
        var output = stateFile.startWrite()
        try {
            output.write(json.toString().toByteArray(StandardCharsets.UTF_8))
            stateFile.finishWrite(output)
        } catch (error: Throwable) {
            stateFile.failWrite(output)
            throw error
        }
    }

    companion object {
        @Volatile
        private var instance: BrowserAdBlockStore? = null

        fun getInstance(context: Context): BrowserAdBlockStore =
            instance ?: synchronized(this) {
                instance
                    ?: BrowserAdBlockStore(
                        context = context.applicationContext,
                        httpClient =
                            OkHttpClient.Builder()
                                .connectTimeout(20L, TimeUnit.SECONDS)
                                .readTimeout(60L, TimeUnit.SECONDS)
                                .callTimeout(10L, TimeUnit.MINUTES)
                                .build(),
                    ).also { store ->
                        instance = store
                    }
            }
    }
}

internal val BrowserAdBlockSubscription.hasCommittedPayloadMetadata: Boolean
    get() =
        effectiveRuleCount > 0 &&
            payloadSha256 != null &&
            payloadByteCount != null &&
            payloadStorageVersion == BrowserAdBlockCompilerContract.PAYLOAD_STORAGE_VERSION

internal fun BrowserAdBlockSubscription.matchesCommittedPayload(
    payloadSha256: String,
    payloadByteCount: Long,
): Boolean =
    hasCommittedPayloadMetadata &&
        this.payloadSha256 == payloadSha256 &&
        this.payloadByteCount == payloadByteCount

internal fun mergeBrowserAdBlockRefreshedSubscription(
    current: BrowserAdBlockSubscription,
    expectedUrl: String,
    expectedName: String,
    counts: BrowserAdBlockSubscriptionRuleCounts,
    updatedAt: Long,
    payloadSha256: String,
    payloadByteCount: Long,
): BrowserAdBlockSubscription {
    require(current.url == expectedUrl) {
        "广告拦截订阅地址在更新期间发生变化，请重新刷新"
    }
    require(current.name == expectedName) {
        "广告拦截订阅名称在更新期间发生变化，请重新刷新"
    }
    return current.withRuleCounts(
        counts = counts,
        updatedAt = updatedAt,
    ).copy(
        payloadSha256 = payloadSha256,
        payloadByteCount = payloadByteCount,
        payloadStorageVersion = BrowserAdBlockCompilerContract.PAYLOAD_STORAGE_VERSION,
    )
}

internal fun mergeBrowserAdBlockPreparedSubscriptionAtCommit(
    current: BrowserAdBlockSubscription,
    expectedUrl: String,
    expectedName: String,
    prepared: BrowserAdBlockSubscription,
): BrowserAdBlockSubscription {
    require(current.id == prepared.id) {
        "广告拦截订阅更新结果不属于当前订阅"
    }
    require(current.url == expectedUrl) {
        "广告拦截订阅地址在更新期间发生变化，请重新刷新"
    }
    require(current.name == expectedName) {
        "广告拦截订阅名称在更新期间发生变化，请重新刷新"
    }
    return prepared.copy(
        name = current.name,
        url = current.url,
        enabled = current.enabled,
        group = current.group,
        builtIn = current.builtIn,
    )
}

internal fun browserAdBlockUnchangedRefreshCommit(
    currentState: BrowserAdBlockState,
    id: String,
    expectedUrl: String,
    expectedName: String,
    updatedAt: Long,
): BrowserAdBlockUnchangedRefreshCommit {
    val currentSubscription =
        currentState.subscriptions.singleOrNull { candidate -> candidate.id == id }
            ?: throw IllegalStateException(
                "广告拦截订阅在更新期间已被删除：$id",
            )
    require(currentSubscription.url == expectedUrl) {
        "广告拦截订阅地址在更新期间发生变化，请重新刷新"
    }
    require(currentSubscription.name == expectedName) {
        "广告拦截订阅名称在更新期间发生变化，请重新刷新"
    }
    val refreshed =
        currentSubscription.copy(
            lastUpdatedAt = updatedAt,
            lastError = null,
        )
    return BrowserAdBlockUnchangedRefreshCommit(
        state =
            currentState.copy(
                subscriptions =
                    currentState.subscriptions.map { candidate ->
                        if (candidate.id == refreshed.id) refreshed else candidate
                    },
            ),
        subscription = refreshed,
    )
}

internal fun readBrowserAdBlockSubscriptionPayload(
    source: BufferedSource,
    declaredLength: Long,
    maxBytes: Long,
): ByteArray {
    require(maxBytes > 0L) { "订阅载荷上限必须大于 0" }
    check(declaredLength < 0L || declaredLength <= maxBytes) {
        "订阅文件超过 ${formatByteCount(maxBytes)} 上限"
    }
    val buffer = Buffer()
    var totalBytes = 0L
    // readByteArray(count) requires exactly count bytes and treats a normal EOF as an error.
    // Chunked reads keep the upper bound without requiring every list to fill the limit.
    while (true) {
        val bytesToRead = minOf(BROWSER_AD_BLOCK_READ_CHUNK_BYTES, maxBytes - totalBytes + 1L)
        val read = source.read(buffer, bytesToRead)
        if (read == -1L) {
            break
        }
        check(read > 0L) {
            "订阅服务器返回了无效的空读取"
        }
        totalBytes += read
        check(totalBytes <= maxBytes) {
            "订阅文件超过 ${formatByteCount(maxBytes)} 上限"
        }
    }
    return buffer.readByteArray()
}

internal fun validateBrowserAdBlockSubscriptionPayload(bytes: ByteArray) {
    check(bytes.isNotEmpty()) {
        "订阅内容为空"
    }
    val prefix =
        bytes
            .copyOfRange(0, minOf(bytes.size, BROWSER_AD_BLOCK_HTML_PROBE_BYTES))
            .toString(StandardCharsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
    check(
        !prefix.startsWith("<!doctype html", ignoreCase = true) &&
            !prefix.startsWith("<html", ignoreCase = true),
    ) {
        "订阅服务器返回了网页内容，而不是规则列表"
    }
}

internal fun browserAdBlockSubscriptionRefreshErrorMessage(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.toList()
    return when {
        causes.any { it is UnknownHostException } -> "无法解析订阅服务器域名"
        causes.any { it is SocketTimeoutException } -> "连接或读取订阅超时"
        causes.any { it is SSLException } -> "订阅服务器安全连接失败"
        causes.any { it is EOFException } -> "订阅内容传输未完成"
        error.message?.isNotBlank() == true -> error.message!!.trim()
        causes.any { it is IOException } -> "订阅下载失败"
        else -> "订阅更新失败：${error::class.java.simpleName}"
    }
}

private fun formatByteCount(bytes: Long): String =
    if (bytes % (1024L * 1024L) == 0L) {
        "${bytes / (1024L * 1024L)} MB"
    } else {
        "${bytes / 1024L} KB"
    }

private fun BrowserAdBlockSubscription.withRuleCounts(
    counts: BrowserAdBlockSubscriptionRuleCounts,
    updatedAt: Long?,
    clearError: Boolean = true,
): BrowserAdBlockSubscription =
    copy(
        lastUpdatedAt = updatedAt,
        lastError = if (clearError) null else lastError,
        ignoredLineCount = counts.ignoredLineCount,
        networkBlockingRuleCount = counts.networkBlockingRuleCount,
        networkExceptionRuleCount = counts.networkExceptionRuleCount,
        elementBlockingRuleCount = counts.elementBlockingRuleCount,
        elementExceptionRuleCount = counts.elementExceptionRuleCount,
    )

private fun BrowserAdBlockSubscription.withoutCommittedPayload(
    errorMessage: String,
): BrowserAdBlockSubscription =
    copy(
        lastError = errorMessage.take(BROWSER_AD_BLOCK_MAX_ERROR_LENGTH),
        networkBlockingRuleCount = 0,
        networkExceptionRuleCount = 0,
        elementBlockingRuleCount = 0,
        elementExceptionRuleCount = 0,
        payloadSha256 = null,
        payloadByteCount = null,
        payloadStorageVersion = null,
    )

private fun BrowserAdBlockSubscription.compiledCacheIdentity():
    BrowserAdBlockCompiledCacheIdentity =
    BrowserAdBlockCompiledCacheIdentity(
        subscriptionId = id,
        subscriptionName = name,
        payloadSha256 =
            requireNotNull(payloadSha256) {
                "Browser ad-block subscription has no committed payload SHA-256"
            },
        payloadByteCount =
            requireNotNull(payloadByteCount) {
                "Browser ad-block subscription has no committed payload byte count"
            },
        networkBlockingRuleCount = networkBlockingRuleCount,
        networkExceptionRuleCount = networkExceptionRuleCount,
        elementBlockingRuleCount = elementBlockingRuleCount,
        elementExceptionRuleCount = elementExceptionRuleCount,
    )

private fun BrowserAdBlockState.updateSubscriptionMetadata(
    subscription: BrowserAdBlockSubscription,
): BrowserAdBlockState =
    copy(
        subscriptions =
            subscriptions.map { candidate ->
                if (candidate.id == subscription.id) subscription else candidate
            },
    )

private fun browserAdBlockRuntimeConfigurationChanged(
    previous: BrowserAdBlockState,
    updated: BrowserAdBlockState,
): Boolean =
    previous.enabled != updated.enabled ||
        previous.allowlistedDomains != updated.allowlistedDomains ||
        previous.subscriptions.map(BrowserAdBlockSubscription::runtimeConfigurationIdentity) !=
            updated.subscriptions.map(BrowserAdBlockSubscription::runtimeConfigurationIdentity)

private fun BrowserAdBlockSubscription.runtimeConfigurationIdentity(): List<Any> =
    listOf(id, enabled, hasLocalRules)

private fun normalizeCustomNetworkRule(rule: String): String {
    val normalized = rule.trim()
    require(normalized.isNotBlank()) { "Browser ad-block network rule is blank" }
    require(normalized.length <= BROWSER_AD_BLOCK_MAX_CUSTOM_RULE_LENGTH) {
        "Browser ad-block network rule is too long"
    }
    require(isValidBrowserAdBlockNetworkRule(normalized)) {
        "Browser ad-block network rule cannot be compiled"
    }
    return normalized
}

private fun normalizeBrowserAdBlockSubscriptionUrl(url: String): String {
    val normalized = url.trim()
    val uri = runCatching { URI(normalized) }.getOrNull()
    require(
        uri != null &&
            (uri.scheme.equals("https", ignoreCase = true) ||
                uri.scheme.equals("http", ignoreCase = true)) &&
            !uri.host.isNullOrBlank(),
    ) {
        "Browser ad-block subscription URL must be HTTP or HTTPS"
    }
    return normalized
}

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    buildList(length()) {
        for (index in 0 until length()) {
            add(transform(getJSONObject(index)))
        }
    }

private fun JSONArray.mapStrings(): List<String> =
    buildList(length()) {
        for (index in 0 until length()) {
            add(getString(index))
        }
    }

private const val BROWSER_AD_BLOCK_TAG = "BrowserAdBlock"
private const val BROWSER_AD_BLOCK_SCHEMA_VERSION = 3
private const val BROWSER_AD_BLOCK_STATE_FILE_NAME = "browser_ad_block_state.json"
private const val BROWSER_AD_BLOCK_SUBSCRIPTION_DIRECTORY_NAME =
    "browser_ad_block_subscriptions"
private const val BROWSER_AD_BLOCK_COMPILED_DIRECTORY_NAME =
    "browser_ad_block_compiled"
private const val BROWSER_AD_BLOCK_MAX_CUSTOM_RULE_LENGTH = 2_048
private const val BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_NAME_LENGTH = 80
private const val BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES = 32L * 1024L * 1024L
private const val BROWSER_AD_BLOCK_READ_CHUNK_BYTES = 64L * 1024L
private const val BROWSER_AD_BLOCK_HTML_PROBE_BYTES = 512
private const val BROWSER_AD_BLOCK_MAX_ERROR_LENGTH = 240
private const val BROWSER_AD_BLOCK_CUSTOM_RULE_SET_ID = "custom"
private const val BROWSER_AD_BLOCK_BLOCKED_COUNT_PUBLISH_INTERVAL_MILLIS = 500L
private const val BROWSER_AD_BLOCK_SUBSCRIPTION_USER_AGENT = "Kiyori-AdBlock/1"
private val BROWSER_AD_BLOCK_SUBSCRIPTION_ID_REGEX = Regex("[A-Za-z0-9._-]{1,96}")
private val BROWSER_AD_BLOCK_SHA_256_REGEX = Regex("[a-f0-9]{64}")
