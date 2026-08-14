package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.Context
import android.util.AtomicFile
import com.ai.assistance.operit.util.AppLogger
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
    val lastUpdatedAt: Long?,
    val lastError: String?,
    val ignoredLineCount: Int,
    val networkRules: List<String>,
    val elementRules: List<BrowserAdBlockSubscriptionElementRule>,
)

internal data class BrowserAdBlockState(
    val enabled: Boolean = true,
    val allowlistedDomains: List<String> = emptyList(),
    val customNetworkRules: List<BrowserAdBlockCustomNetworkRule> = emptyList(),
    val customElementRules: List<BrowserAdBlockCustomElementRule> = emptyList(),
    val subscriptions: List<BrowserAdBlockSubscription> = emptyList(),
    val blockedRequestCount: Long = 0L,
) {
    val activeNetworkRuleCount: Int
        get() =
            customNetworkRules.count(BrowserAdBlockCustomNetworkRule::enabled) +
                subscriptions
                    .asSequence()
                    .filter(BrowserAdBlockSubscription::enabled)
                    .sumOf { subscription -> subscription.networkRules.size }

    val activeElementRuleCount: Int
        get() =
            customElementRules.count(BrowserAdBlockCustomElementRule::enabled) +
                subscriptions
                    .asSequence()
                    .filter(BrowserAdBlockSubscription::enabled)
                    .sumOf { subscription -> subscription.elementRules.count { rule -> !rule.exception } }
}

internal class BrowserAdBlockStore private constructor(
    context: Context,
    private val httpClient: OkHttpClient,
) {
    private val lock = Any()
    private val stateFile =
        AtomicFile(
            context.applicationContext.filesDir.resolve(BROWSER_AD_BLOCK_STATE_FILE_NAME),
        )
    private val blockedRequestCounter = AtomicLong(0L)
    private val _state = MutableStateFlow(readPersistedState())

    @Volatile
    private var matcher = compileMatcher(_state.value)

    val state: StateFlow<BrowserAdBlockState> = _state.asStateFlow()
    val current: BrowserAdBlockState
        get() = _state.value

    fun decide(
        pageUrl: String,
        requestUrl: String,
    ): BrowserAdBlockDecision? = matcher.decide(pageUrl, requestUrl)

    fun selectorsForPage(pageUrl: String): List<String> = matcher.selectorsForPage(pageUrl)

    fun recordBlockedRequest() {
        val count = blockedRequestCounter.incrementAndGet()
        _state.value = _state.value.copy(blockedRequestCount = count)
    }

    fun setEnabled(enabled: Boolean) {
        mutatePersistedState { current -> current.copy(enabled = enabled) }
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
        mutatePersistedState { current ->
            val existing =
                id?.let { targetId ->
                    current.subscriptions.singleOrNull { subscription ->
                        subscription.id == targetId
                    }
                }
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
                        networkRules = emptyList(),
                        elementRules = emptyList(),
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
                        networkRules =
                            existing.networkRules.takeIf { existing.url == normalizedUrl }
                                ?: emptyList(),
                        elementRules =
                            existing.elementRules.takeIf { existing.url == normalizedUrl }
                                ?: emptyList(),
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
                    current.subscriptions
                        .filterNot { existingSubscription ->
                            existingSubscription.id == subscription.id
                        }
                        .plus(subscription)
                        .sortedBy(BrowserAdBlockSubscription::name),
            )
        }
        return checkNotNull(savedSubscription)
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
    }

    fun removeSubscription(id: String) {
        mutatePersistedState { current ->
            require(current.subscriptions.any { subscription -> subscription.id == id }) {
                "Unknown browser ad-block subscription: $id"
            }
            current.copy(
                subscriptions =
                    current.subscriptions.filterNot { subscription -> subscription.id == id },
            )
        }
    }

    suspend fun refreshSubscription(id: String): Result<BrowserAdBlockSubscription> =
        withContext(Dispatchers.IO) {
            val subscription =
                current.subscriptions.singleOrNull { candidate -> candidate.id == id }
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Unknown browser ad-block subscription: $id"),
                    )
            runCatching {
                val request =
                    Request.Builder()
                        .url(subscription.url)
                        .header("Accept", "text/plain, application/octet-stream;q=0.9, */*;q=0.1")
                        .header("User-Agent", BROWSER_AD_BLOCK_SUBSCRIPTION_USER_AGENT)
                        .build()
                val text =
                    httpClient.newCall(request).execute().use { response ->
                        check(response.isSuccessful) {
                            "Subscription request failed with HTTP ${response.code}"
                        }
                        val body = checkNotNull(response.body) {
                            "Subscription response body is empty"
                        }
                        val declaredLength = body.contentLength()
                        check(
                            declaredLength < 0L ||
                                declaredLength <= BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES,
                        ) {
                            "Subscription is larger than $BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES bytes"
                        }
                        val source = body.source()
                        val bytes =
                            source.readByteArray(BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES + 1L)
                        check(
                            bytes.size <= BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES &&
                                source.exhausted(),
                        ) {
                            "Subscription is larger than $BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES bytes"
                        }
                        bytes.toString(StandardCharsets.UTF_8)
                    }
                val parsed =
                    parseBrowserAdBlockSubscription(
                        text = text,
                        subscriptionId = subscription.id,
                        subscriptionName = subscription.name,
                    )
                val updated =
                    subscription.copy(
                        lastUpdatedAt = System.currentTimeMillis(),
                        lastError = null,
                        ignoredLineCount = parsed.ignoredLineCount,
                        networkRules = parsed.networkRules.map(BrowserAdBlockNetworkRuleSpec::rule),
                        elementRules =
                            parsed.elementRules.map { rule ->
                                BrowserAdBlockSubscriptionElementRule(
                                    domainExpression = rule.domainExpression,
                                    selector = rule.selector,
                                    exception = rule.exception,
                                )
                            },
                    )
                mutatePersistedState { currentState ->
                    require(currentState.subscriptions.any { candidate -> candidate.id == id }) {
                        "Browser ad-block subscription was removed while refreshing: $id"
                    }
                    currentState.copy(
                        subscriptions =
                            currentState.subscriptions.map { candidate ->
                                if (candidate.id == id) updated else candidate
                            },
                    )
                }
                updated
            }.onFailure { error ->
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
                                            error.message
                                                ?.take(BROWSER_AD_BLOCK_MAX_ERROR_LENGTH)
                                                ?: error::class.java.simpleName,
                                    )
                                } else {
                                    candidate
                                }
                            },
                    )
                }
            }
        }

    private fun mutatePersistedState(
        transform: (BrowserAdBlockState) -> BrowserAdBlockState,
    ) {
        synchronized(lock) {
            val currentState = _state.value
            val updated =
                transform(currentState).copy(
                    blockedRequestCount = blockedRequestCounter.get(),
                )
            if (updated == currentState) {
                return
            }
            val compiledMatcher = compileMatcher(updated)
            writePersistedState(updated)
            matcher = compiledMatcher
            _state.value = updated
        }
    }

    private fun compileMatcher(state: BrowserAdBlockState): BrowserAdBlockMatcher {
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
                state.subscriptions
                    .filter(BrowserAdBlockSubscription::enabled)
                    .forEach { subscription ->
                        subscription.networkRules.forEachIndexed { index, rule ->
                            add(
                                BrowserAdBlockNetworkRuleSpec(
                                    id = "${subscription.id}:network:$index",
                                    rule = rule,
                                    source = BrowserAdBlockRuleSource.SUBSCRIPTION,
                                    sourceName = subscription.name,
                                ),
                            )
                        }
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
                state.subscriptions
                    .filter(BrowserAdBlockSubscription::enabled)
                    .forEach { subscription ->
                        subscription.elementRules.forEachIndexed { index, rule ->
                            add(
                                BrowserAdBlockElementRuleSpec(
                                    id = "${subscription.id}:element:$index",
                                    domainExpression = rule.domainExpression,
                                    selector = rule.selector,
                                    source = BrowserAdBlockRuleSource.SUBSCRIPTION,
                                    sourceName = subscription.name,
                                    exception = rule.exception,
                                ),
                            )
                        }
                    }
            }
        return BrowserAdBlockMatcher.compile(
            enabled = state.enabled,
            allowlistedDomains = state.allowlistedDomains,
            networkRules = networkRules,
            elementRules = elementRules,
        )
    }

    private fun readPersistedState(): BrowserAdBlockState {
        val file = stateFile.baseFile
        if (!file.exists()) {
            return BrowserAdBlockState()
        }
        val json =
            stateFile.openRead().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                JSONObject(reader.readText())
            }
        require(json.getInt("schemaVersion") == BROWSER_AD_BLOCK_SCHEMA_VERSION) {
            "Unsupported browser ad-block state schema: ${json.getInt("schemaVersion")}"
        }
        val state =
            BrowserAdBlockState(
                enabled = json.getBoolean("enabled"),
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
                subscriptions =
                    json.getJSONArray("subscriptions").mapObjects { item ->
                        BrowserAdBlockSubscription(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            url = normalizeBrowserAdBlockSubscriptionUrl(item.getString("url")),
                            enabled = item.getBoolean("enabled"),
                            lastUpdatedAt =
                                item.optLong("lastUpdatedAt", -1L).takeIf { value -> value >= 0L },
                            lastError =
                                item.optString("lastError", "").takeIf(String::isNotBlank),
                            ignoredLineCount = item.getInt("ignoredLineCount"),
                            networkRules = item.getJSONArray("networkRules").mapStrings(),
                            elementRules =
                                item.getJSONArray("elementRules").mapObjects { rule ->
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
                                },
                        )
                    },
            )
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
        return state
    }

    private fun writePersistedState(state: BrowserAdBlockState) {
        val json =
            JSONObject()
                .put("schemaVersion", BROWSER_AD_BLOCK_SCHEMA_VERSION)
                .put("enabled", state.enabled)
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
                                    .put("lastUpdatedAt", subscription.lastUpdatedAt ?: -1L)
                                    .put("lastError", subscription.lastError.orEmpty())
                                    .put("ignoredLineCount", subscription.ignoredLineCount)
                                    .put("networkRules", JSONArray(subscription.networkRules))
                                    .put(
                                        "elementRules",
                                        JSONArray().apply {
                                            subscription.elementRules.forEach { rule ->
                                                put(
                                                    JSONObject()
                                                        .put(
                                                            "domainExpression",
                                                            rule.domainExpression,
                                                        )
                                                        .put("selector", rule.selector)
                                                        .put("exception", rule.exception),
                                                )
                                            }
                                        },
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
                        httpClient = OkHttpClient(),
                    ).also { store ->
                        instance = store
                    }
            }
    }
}

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
private const val BROWSER_AD_BLOCK_SCHEMA_VERSION = 1
private const val BROWSER_AD_BLOCK_STATE_FILE_NAME = "browser_ad_block_state.json"
private const val BROWSER_AD_BLOCK_MAX_CUSTOM_RULE_LENGTH = 2_048
private const val BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_NAME_LENGTH = 80
private const val BROWSER_AD_BLOCK_MAX_SUBSCRIPTION_BYTES = 8L * 1024L * 1024L
private const val BROWSER_AD_BLOCK_MAX_ERROR_LENGTH = 240
private const val BROWSER_AD_BLOCK_SUBSCRIPTION_USER_AGENT = "Kiyori-AdBlock/1"
