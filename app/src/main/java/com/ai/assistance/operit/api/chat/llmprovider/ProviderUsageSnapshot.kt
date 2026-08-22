package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProtocol
import com.ai.assistance.operit.data.model.ProviderUsageAggregate

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
                uncachedInputTokens + cacheReadTokens + cacheWriteTokens
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
        get() = uncachedInputTokens + cacheReadTokens + cacheWriteTokens

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

    fun record(
        providerHopId: String,
        snapshot: ProviderUsageSnapshot,
    ): Boolean {
        require(providerHopId.isNotBlank()) { "providerHopId must not be blank" }
        val previous = samples.put(providerHopId, snapshot)
        return previous != snapshot
    }

    fun clear() {
        samples.clear()
    }

    fun aggregate(): ProviderUsageAggregate {
        var providerUsageRequestCount = 0
        var providerCacheMetricRequestCount = 0
        var providerCacheMetricPromptTokens = 0L
        var providerTotalInputTokens = 0L
        var providerUncachedInputTokens = 0L
        var providerCacheReadTokens = 0L
        var providerCacheWriteTokens = 0L
        var providerOutputTokens = 0L
        var providerReasoningTokens = 0L

        samples.values.forEach { snapshot ->
            if (snapshot.source != ProviderUsageSource.PROVIDER) {
                return@forEach
            }

            providerUsageRequestCount += 1
            providerTotalInputTokens += snapshot.totalInputTokens
            providerUncachedInputTokens += snapshot.uncachedInputTokens
            providerCacheReadTokens += snapshot.cacheReadTokens
            providerCacheWriteTokens += snapshot.cacheWriteTokens
            providerOutputTokens += snapshot.outputTokens
            providerReasoningTokens += snapshot.reasoningTokens
            if (snapshot.cacheMetricState == ProviderCacheMetricState.REPORTED) {
                providerCacheMetricRequestCount += 1
                providerCacheMetricPromptTokens += snapshot.totalInputTokens
            }
        }

        return ProviderUsageAggregate(
            requestCount = samples.size,
            providerUsageRequestCount = providerUsageRequestCount,
            providerCacheMetricRequestCount = providerCacheMetricRequestCount,
            providerCacheMetricPromptTokens = providerCacheMetricPromptTokens,
            providerTotalInputTokens = providerTotalInputTokens,
            providerUncachedInputTokens = providerUncachedInputTokens,
            providerCacheReadTokens = providerCacheReadTokens,
            providerCacheWriteTokens = providerCacheWriteTokens,
            providerOutputTokens = providerOutputTokens,
            providerReasoningTokens = providerReasoningTokens,
        )
    }
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
    val providerUsageReported = source == ProviderUsageSource.PROVIDER
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
        providerUncachedInputTokens =
            if (providerUsageReported) uncachedInputTokens else 0L,
        providerCacheReadTokens = if (providerUsageReported) cacheReadTokens else 0L,
        providerCacheWriteTokens = if (providerUsageReported) cacheWriteTokens else 0L,
        providerOutputTokens = if (providerUsageReported) outputTokens else 0L,
        providerReasoningTokens = if (providerUsageReported) reasoningTokens else 0L,
    )
}
