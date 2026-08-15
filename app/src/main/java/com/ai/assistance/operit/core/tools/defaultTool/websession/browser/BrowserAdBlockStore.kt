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
) {
    val effectiveRuleCount: Int
        get() =
            networkBlockingRuleCount +
                networkExceptionRuleCount +
                elementBlockingRuleCount +
                elementExceptionRuleCount

    val hasLocalRules: Boolean
        get() = effectiveRuleCount > 0
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
    COMPILING_RULES,
    READY,
    FAILED,
}

internal data class BrowserAdBlockRuntimeStatus(
    val phase: BrowserAdBlockRuntimePhase,
    val completedSubscriptionCount: Int = 0,
    val totalSubscriptionCount: Int = 0,
    val errorMessage: String? = null,
) {
    val ready: Boolean
        get() = phase == BrowserAdBlockRuntimePhase.READY
}

private data class BrowserAdBlockInitialSnapshot(
    val state: BrowserAdBlockState,
    val requiresPersistence: Boolean,
    val migratedPayloads: Map<String, ByteArray> = emptyMap(),
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
    private var subscriptionRuleSets = emptyMap<String, BrowserAdBlockCompiledRuleSet>()
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
                deleteSubscriptionPayload(subscription.id)
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
        deleteSubscriptionPayload(id)
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
                val parsed =
                    parseBrowserAdBlockSubscription(
                        text = bytes.toString(StandardCharsets.UTF_8),
                        subscriptionId = subscription.id,
                        subscriptionName = subscription.name,
                    )
                check(parsed.networkRules.isNotEmpty() || parsed.elementRules.isNotEmpty()) {
                    "订阅中没有可用规则"
                }
                Result.success(
                    commitRefreshedSubscription(
                        id = subscription.id,
                        expectedUrl = subscription.url,
                        parsed = parsed,
                        updatedAt = System.currentTimeMillis(),
                        payload = bytes,
                    ),
                )
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
        try {
            val initialSnapshot = readInitialSnapshot()
            initialSnapshot.migratedPayloads.forEach { (id, payload) ->
                writeSubscriptionPayload(id, payload)
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
                    phase = BrowserAdBlockRuntimePhase.COMPILING_RULES,
                    totalSubscriptionCount = subscriptionsToCompile.size,
                )
            val compiledCustomRules = compileCustomRuleSet(loadedState)
            val compiledSubscriptions =
                buildMap<String, BrowserAdBlockCompiledRuleSet> {
                    subscriptionsToCompile.forEachIndexed { index, subscription ->
                        val parsedResult =
                            runCatching {
                                val payload =
                                    checkNotNull(readSubscriptionPayload(subscription.id)) {
                                        "本地订阅规则缺失，等待重新同步"
                                    }
                                parseBrowserAdBlockSubscription(
                                    text = payload.toString(StandardCharsets.UTF_8),
                                    subscriptionId = subscription.id,
                                    subscriptionName = subscription.name,
                                )
                            }
                        parsedResult.fold(
                            onSuccess = { parsed ->
                                put(
                                    subscription.id,
                                    compileSubscriptionRuleSet(
                                        subscription = subscription,
                                        parsed = parsed,
                                    ),
                                )
                                loadedState =
                                    loadedState.updateSubscriptionMetadata(
                                        subscription.withParsedRuleCounts(
                                            parsed = parsed,
                                            updatedAt = subscription.lastUpdatedAt,
                                            clearError = false,
                                        ),
                                    )
                            },
                            onFailure = { error ->
                                AppLogger.e(
                                    BROWSER_AD_BLOCK_TAG,
                                    "Failed to compile stored subscription id=${subscription.id}",
                                    error,
                                )
                                loadedState =
                                    loadedState.updateSubscriptionMetadata(
                                        subscription.copy(
                                            lastError =
                                                browserAdBlockSubscriptionRefreshErrorMessage(error)
                                                    .take(BROWSER_AD_BLOCK_MAX_ERROR_LENGTH),
                                            networkBlockingRuleCount = 0,
                                            networkExceptionRuleCount = 0,
                                            elementBlockingRuleCount = 0,
                                            elementExceptionRuleCount = 0,
                                        ),
                                    )
                            },
                        )
                        _runtimeStatus.value =
                            BrowserAdBlockRuntimeStatus(
                                phase = BrowserAdBlockRuntimePhase.COMPILING_RULES,
                                completedSubscriptionCount = index + 1,
                                totalSubscriptionCount = subscriptionsToCompile.size,
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
                subscriptionRuleSets = compiledSubscriptions
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
                    )
            }
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
            val retainedSubscriptionRuleSets =
                subscriptionRuleSets.filterKeys { id ->
                    transformed.subscriptions.any { subscription ->
                        subscription.id == id && subscription.hasLocalRules
                    }
                }
            val subscriptionRulesChanged =
                retainedSubscriptionRuleSets.keys != subscriptionRuleSets.keys
            val nextCustomRuntimeEngine =
                if (customRulesChanged) {
                    compileCustomRuntimeEngine(nextCustomRuleSet)
                } else {
                    customRuntimeEngine
                }
            val nextSubscriptionRuntimeEngine =
                if (subscriptionRulesChanged) {
                    compileSubscriptionRuntimeEngine(retainedSubscriptionRuleSets)
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
            subscriptionRuleSets = retainedSubscriptionRuleSets
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

    private fun compileSubscriptionRuleSet(
        subscription: BrowserAdBlockSubscription,
        parsed: BrowserAdBlockSubscriptionParseResult,
    ): BrowserAdBlockCompiledRuleSet =
        BrowserAdBlockCompiledRuleSet.compile(
            id = subscription.id,
            networkRules = parsed.networkRules,
            elementRules = parsed.elementRules,
        )

    private fun compileCustomRuntimeEngine(
        customRules: BrowserAdBlockCompiledRuleSet,
    ): BrowserAdBlockEngine =
        BrowserAdBlockEngine.compile(
            listOf(customRules),
        )

    private fun compileSubscriptionRuntimeEngine(
        subscriptionRules: Map<String, BrowserAdBlockCompiledRuleSet>,
    ): BrowserAdBlockEngine =
        BrowserAdBlockEngine.compile(
            subscriptionRules.values,
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
                                subscription.id in subscriptionRuleSets
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
        parsed: BrowserAdBlockSubscriptionParseResult,
        updatedAt: Long,
        payload: ByteArray,
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
            val subscription =
                mergeBrowserAdBlockRefreshedSubscription(
                    current = currentSubscription,
                    expectedUrl = expectedUrl,
                    parsed = parsed,
                    updatedAt = updatedAt,
                )
            val compiledRuleSet =
                compileSubscriptionRuleSet(
                    subscription = subscription,
                    parsed = parsed,
                )
            val updatedState =
                currentState.copy(
                    subscriptions =
                        currentState.subscriptions.map { candidate ->
                            if (candidate.id == subscription.id) subscription else candidate
                        },
                    blockedRequestCount = blockedRequestCounter.get(),
                )
            val revisionedState =
                updatedState.copy(
                    ruleRevision = currentState.ruleRevision + 1L,
                )
            val updatedSubscriptionRuleSets =
                subscriptionRuleSets + (subscription.id to compiledRuleSet)
            val updatedSubscriptionRuntimeEngine =
                compileSubscriptionRuntimeEngine(updatedSubscriptionRuleSets)
            val updatedRuntimeEngine =
                combineRuntimeEngines(
                    customEngine = customRuntimeEngine,
                    subscriptionEngine = updatedSubscriptionRuntimeEngine,
                )
            writeSubscriptionPayload(subscription.id, payload)
            writePersistedState(revisionedState)
            subscriptionRuleSets = updatedSubscriptionRuleSets
            subscriptionRuntimeEngine = updatedSubscriptionRuntimeEngine
            runtimeEngine = updatedRuntimeEngine
            matcher = compileMatcher(revisionedState, updatedRuntimeEngine)
            publishState(revisionedState)
            subscription
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

    private fun subscriptionPayloadFile(id: String): AtomicFile {
        require(BROWSER_AD_BLOCK_SUBSCRIPTION_ID_REGEX.matches(id)) {
            "Invalid browser ad-block subscription ID: $id"
        }
        return AtomicFile(subscriptionPayloadDirectory.resolve("$id.txt"))
    }

    private fun writeSubscriptionPayload(
        id: String,
        bytes: ByteArray,
    ) {
        val payloadFile = subscriptionPayloadFile(id)
        var output = payloadFile.startWrite()
        try {
            output.write(bytes)
            payloadFile.finishWrite(output)
        } catch (error: Throwable) {
            payloadFile.failWrite(output)
            throw error
        }
    }

    private fun readSubscriptionPayload(id: String): ByteArray? {
        val payloadFile = subscriptionPayloadFile(id)
        if (!payloadFile.baseFile.exists()) {
            return null
        }
        return payloadFile.openRead().use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES) {
                "Stored subscription is larger than $BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES bytes"
            }
            bytes
        }
    }

    private fun deleteSubscriptionPayload(id: String) {
        val payloadFile = subscriptionPayloadFile(id)
        if (payloadFile.baseFile.exists() && !payloadFile.baseFile.delete()) {
            AppLogger.w(
                BROWSER_AD_BLOCK_TAG,
                "Could not delete browser ad-block subscription payload id=$id",
            )
        }
        val backupFile = File(payloadFile.baseFile.path + ".bak")
        if (backupFile.exists() && !backupFile.delete()) {
            AppLogger.w(
                BROWSER_AD_BLOCK_TAG,
                "Could not delete browser ad-block subscription backup id=$id",
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
                BROWSER_AD_BLOCK_SCHEMA_VERSION -> readSchemaTwoSnapshot(json)
                else ->
                    error("Unsupported browser ad-block state schema: $schemaVersion")
            }
        val mergedSubscriptions =
            mergeBrowserAdBlockBuiltInSubscriptions(loadedSnapshot.state.subscriptions)
        val state = loadedSnapshot.state.copy(subscriptions = mergedSubscriptions)
        validateStoredState(state)
        return loadedSnapshot.copy(
            state = state,
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
                if (networkRules.isNotEmpty() || elementRules.isNotEmpty()) {
                    migratedPayloads[id] =
                        buildString {
                            appendLine("[Adblock Plus 2.0]")
                            networkRules.forEach(::appendLine)
                            elementRules.forEach { rule ->
                                append(rule.domainExpression)
                                append(if (rule.exception) "#@#" else "##")
                                appendLine(rule.selector)
                            }
                        }.toByteArray(StandardCharsets.UTF_8)
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
        var metadataChanged = false
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
                if (!metadata.hasCommittedPayloadMetadata) {
                    metadata
                } else {
                    val payloadFile = subscriptionPayloadFile(metadata.id).baseFile
                    if (!payloadFile.exists()) {
                        metadataChanged = true
                        metadata.copy(
                            lastError = "本地订阅规则缺失，等待重新同步",
                            networkBlockingRuleCount = 0,
                            networkExceptionRuleCount = 0,
                            elementBlockingRuleCount = 0,
                            elementExceptionRuleCount = 0,
                        )
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
    get() = effectiveRuleCount > 0

internal fun mergeBrowserAdBlockRefreshedSubscription(
    current: BrowserAdBlockSubscription,
    expectedUrl: String,
    parsed: BrowserAdBlockSubscriptionParseResult,
    updatedAt: Long,
): BrowserAdBlockSubscription {
    require(current.url == expectedUrl) {
        "广告拦截订阅地址在更新期间发生变化，请重新刷新"
    }
    return current.withParsedRuleCounts(
        parsed = parsed,
        updatedAt = updatedAt,
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

private fun BrowserAdBlockSubscription.withParsedRuleCounts(
    parsed: BrowserAdBlockSubscriptionParseResult,
    updatedAt: Long?,
    clearError: Boolean = true,
): BrowserAdBlockSubscription =
    copy(
        lastUpdatedAt = updatedAt,
        lastError = if (clearError) null else lastError,
        ignoredLineCount = parsed.ignoredLineCount,
        networkBlockingRuleCount =
            parsed.networkRules.count { rule -> !rule.rule.trim().startsWith("@@") },
        networkExceptionRuleCount =
            parsed.networkRules.count { rule -> rule.rule.trim().startsWith("@@") },
        elementBlockingRuleCount = parsed.elementRules.count { rule -> !rule.exception },
        elementExceptionRuleCount =
            parsed.elementRules.count(BrowserAdBlockElementRuleSpec::exception),
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
private const val BROWSER_AD_BLOCK_SCHEMA_VERSION = 2
private const val BROWSER_AD_BLOCK_STATE_FILE_NAME = "browser_ad_block_state.json"
private const val BROWSER_AD_BLOCK_SUBSCRIPTION_DIRECTORY_NAME =
    "browser_ad_block_subscriptions"
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
