package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProtocol
import com.ai.assistance.operit.data.model.ProviderUsageAggregate
import com.ai.assistance.operit.data.model.saturatedProviderUsageLongSum

/**
 * One provider-reported (or explicitly non-reported) usage observation for a
 * single provider hop.
 *
 * The existing message fields remain the compatibility surface. This type is
 * the new source-aware boundary: a local prefix estimate must never be
 * mistaken for server-side prompt-cache evidence.
 */
enum class ProviderUsageSource {
    PROVIDER,
    LOCAL_ESTIMATE,
    UNAVAILABLE,
}

enum class ProviderCacheMetricState {
    REPORTED,
    NOT_REPORTED,
    INVALID,
}

data class ProviderUsageSnapshot(
    val providerModel: String,
    val protocol: ApiProtocol,
    val totalInputTokens: Long,
    val uncachedInputTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long,
    val outputTokens: Long,
    val reasoningTokens: Long,
    val cacheMetricState: ProviderCacheMetricState,
    val source: ProviderUsageSource,
    val inputTokensReported: Boolean = true,
    val outputTokensReported: Boolean = true,
    val reasoningTokensReported: Boolean = false,
    val cacheWriteTokensReported: Boolean = false,
) {
    init {
        require(providerModel.isNotBlank()) { "providerModel must not be blank" }
        require(totalInputTokens >= 0L) { "totalInputTokens must not be negative" }
        require(uncachedInputTokens >= 0L) { "uncachedInputTokens must not be negative" }
        require(cacheReadTokens >= 0L) { "cacheReadTokens must not be negative" }
        require(cacheWriteTokens >= 0L) { "cacheWriteTokens must not be negative" }
        require(outputTokens >= 0L) { "outputTokens must not be negative" }
        require(reasoningTokens >= 0L) { "reasoningTokens must not be negative" }
        require(
            totalInputTokens ==
                saturatedProviderUsageLongSum(uncachedInputTokens, cacheReadTokens, cacheWriteTokens)
        ) {
            "totalInputTokens must equal uncachedInputTokens + cacheReadTokens + cacheWriteTokens"
        }
        if (source != ProviderUsageSource.PROVIDER) {
            require(cacheMetricState != ProviderCacheMetricState.REPORTED) {
                "non-provider usage cannot claim a reported cache metric"
            }
        }
    }

    /** Prompt-side tokens represented by the provider usage buckets. */
    val providerPromptTokens: Long
        get() = saturatedProviderUsageLongSum(uncachedInputTokens, cacheReadTokens, cacheWriteTokens)

    companion object {
        fun unavailable(
            providerModel: String,
            protocol: ApiProtocol,
        ): ProviderUsageSnapshot =
            ProviderUsageSnapshot(
                providerModel = providerModel,
                protocol = protocol,
                totalInputTokens = 0L,
                uncachedInputTokens = 0L,
                cacheReadTokens = 0L,
                cacheWriteTokens = 0L,
                outputTokens = 0L,
                reasoningTokens = 0L,
                cacheMetricState = ProviderCacheMetricState.NOT_REPORTED,
                source = ProviderUsageSource.UNAVAILABLE,
            )
    }
}

/**
 * Last-write-wins fold for one active conversation/turn.
 *
 * A provider may expose usage on both a streaming finish chunk and a terminal
 * snapshot. The message layer supplies the stable provider-hop identity, so
 * the same hop replaces its previous sample instead of being counted twice.
 */
class ProviderUsageAccumulator {
    private val samples = LinkedHashMap<String, ProviderUsageSnapshot>()
    private var total = ProviderUsageAggregate()

    @Synchronized
    fun record(
        providerHopId: String,
        snapshot: ProviderUsageSnapshot,
    ): Boolean {
        require(providerHopId.isNotBlank()) { "providerHopId must not be blank" }
        val previous = samples.put(providerHopId, snapshot)
        if (previous == null) {
            total += snapshot.toProviderUsageAggregate()
        } else if (previous != snapshot) {
            // 替换少见；只有替换才重算，避免长工具链每次入账都遍历整个回合。
            // 不做减法回滚，因为饱和后的总量无法逆向恢复原始精度。
            total = samples.values.fold(ProviderUsageAggregate()) { sum, value ->
                sum + value.toProviderUsageAggregate()
            }
        }
        return previous != snapshot
    }

    @Synchronized
    fun clear() {
        samples.clear()
        total = ProviderUsageAggregate()
    }

    @Synchronized
    fun aggregate(): ProviderUsageAggregate = total
}

interface ProviderUsageReporting {
    /**
     * Consume the latest usage observed by one provider service instance.
     *
     * The message layer calls this exactly once after a provider hop closes.
     * Providers with no server usage return null; callers then record an
     * explicit UNAVAILABLE sample instead of treating local estimates as hits.
     */
    fun consumeLatestProviderUsageSnapshot(): ProviderUsageSnapshot?
}

fun ProviderUsageSnapshot.toProviderUsageAggregate(): ProviderUsageAggregate {
    val providerUsageReported = source == ProviderUsageSource.PROVIDER && inputTokensReported && outputTokensReported
    val cacheReported = providerUsageReported && cacheMetricState == ProviderCacheMetricState.REPORTED
    return ProviderUsageAggregate(
        requestCount = 1,
        providerUsageRequestCount = if (providerUsageReported) 1 else 0,
        providerCacheMetricRequestCount =
            if (
                providerUsageReported &&
                    cacheMetricState == ProviderCacheMetricState.REPORTED
            ) {
                1
            } else {
                0
            },
        providerCacheMetricPromptTokens =
            if (
                providerUsageReported &&
                    cacheMetricState == ProviderCacheMetricState.REPORTED
            ) {
                totalInputTokens
            } else {
                0L
            },
        providerTotalInputTokens = if (providerUsageReported) totalInputTokens else 0L,
        // 异常/缺失缓存分桶不能进入命中分子；保留供应商输入总数到未分类输入桶。
        // 原始异常数仍在 snapshot/审计中，不让统计异常中断对话或伪造缓存命中。
        providerUncachedInputTokens = when {
            cacheReported -> uncachedInputTokens
            providerUsageReported -> totalInputTokens
            else -> 0L
        },
        providerCacheReadTokens = if (cacheReported) cacheReadTokens else 0L,
        providerCacheWriteTokens = if (cacheReported) cacheWriteTokens else 0L,
        providerOutputTokens = if (providerUsageReported) outputTokens else 0L,
        providerReasoningTokens = if (providerUsageReported) reasoningTokens else 0L,
    )
}
