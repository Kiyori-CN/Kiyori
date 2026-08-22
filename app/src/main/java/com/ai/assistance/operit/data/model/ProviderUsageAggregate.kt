package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

internal fun saturatedProviderUsageIntSum(left: Int, right: Int): Int {
    require(left >= 0) { "provider usage values must not be negative" }
    require(right >= 0) { "provider usage values must not be negative" }
    return (left.toLong() + right.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

internal fun saturatedProviderUsageLongSum(vararg values: Long): Long {
    var total = 0L
    values.forEach { value ->
        require(value >= 0L) { "provider usage values must not be negative" }
        if (Long.MAX_VALUE - total < value) {
            return Long.MAX_VALUE
        }
        total += value
    }
    return total
}

/**
 * 可持久化的 provider usage 聚合。
 *
 * 请求覆盖计数与 token 桶必须一起保存，否则进程重启后无法区分“供应商明确报告 0”
 * 和“供应商没有提供该指标”。reasoning 是 output 的细分，不重复计入总量。
 */
@Serializable
data class ProviderUsageAggregate(
    val requestCount: Int = 0,
    val providerUsageRequestCount: Int = 0,
    val providerCacheMetricRequestCount: Int = 0,
    val providerCacheMetricPromptTokens: Long = 0L,
    val providerTotalInputTokens: Long = 0L,
    val providerUncachedInputTokens: Long = 0L,
    val providerCacheReadTokens: Long = 0L,
    val providerCacheWriteTokens: Long = 0L,
    val providerOutputTokens: Long = 0L,
    val providerReasoningTokens: Long = 0L,
) {
    init {
        require(requestCount >= 0) { "requestCount must not be negative" }
        require(providerUsageRequestCount in 0..requestCount) {
            "providerUsageRequestCount must be within requestCount"
        }
        require(providerCacheMetricRequestCount in 0..providerUsageRequestCount) {
            "providerCacheMetricRequestCount must be within providerUsageRequestCount"
        }
        require(providerCacheMetricPromptTokens >= 0L) {
            "providerCacheMetricPromptTokens must not be negative"
        }
        require(providerTotalInputTokens >= 0L) {
            "providerTotalInputTokens must not be negative"
        }
        require(providerUncachedInputTokens >= 0L) {
            "providerUncachedInputTokens must not be negative"
        }
        require(providerCacheReadTokens >= 0L) {
            "providerCacheReadTokens must not be negative"
        }
        require(providerCacheWriteTokens >= 0L) {
            "providerCacheWriteTokens must not be negative"
        }
        require(providerOutputTokens >= 0L) {
            "providerOutputTokens must not be negative"
        }
        require(providerReasoningTokens >= 0L) {
            "providerReasoningTokens must not be negative"
        }
        require(providerCacheMetricPromptTokens <= providerTotalInputTokens) {
            "providerCacheMetricPromptTokens must be within providerTotalInputTokens"
        }
        require(providerTotalInputTokens == providerPromptTokens) {
            "providerTotalInputTokens must equal the three provider prompt buckets"
        }
    }

    val providerPromptTokens: Long
        get() =
            saturatedProviderUsageLongSum(
                providerUncachedInputTokens,
                providerCacheReadTokens,
                providerCacheWriteTokens,
            )

    val providerTotalTokens: Long
        get() =
            saturatedProviderUsageLongSum(
                providerTotalInputTokens,
                providerOutputTokens,
            )

    /**
     * Null 表示没有任何请求提供 provider cache metric。供应商明确报告零 token 时返回 0.0，
     * 不得与未知态合并。
     */
    val cacheHitRate: Double?
        get() =
            if (providerCacheMetricRequestCount == 0) {
                null
            } else if (providerCacheMetricPromptTokens == 0L) {
                0.0
            } else {
                providerCacheReadTokens.toDouble() / providerCacheMetricPromptTokens.toDouble()
            }

    operator fun plus(other: ProviderUsageAggregate): ProviderUsageAggregate {
        val uncachedInputTokens =
            saturatedProviderUsageLongSum(
                providerUncachedInputTokens,
                other.providerUncachedInputTokens,
            )
        val cacheReadTokens =
            saturatedProviderUsageLongSum(
                providerCacheReadTokens,
                other.providerCacheReadTokens,
            )
        val cacheWriteTokens =
            saturatedProviderUsageLongSum(
                providerCacheWriteTokens,
                other.providerCacheWriteTokens,
            )
        return ProviderUsageAggregate(
            requestCount = saturatedProviderUsageIntSum(requestCount, other.requestCount),
            providerUsageRequestCount =
                saturatedProviderUsageIntSum(
                    providerUsageRequestCount,
                    other.providerUsageRequestCount,
                ),
            providerCacheMetricRequestCount =
                saturatedProviderUsageIntSum(
                    providerCacheMetricRequestCount,
                    other.providerCacheMetricRequestCount,
                ),
            providerCacheMetricPromptTokens =
                saturatedProviderUsageLongSum(
                    providerCacheMetricPromptTokens,
                    other.providerCacheMetricPromptTokens,
                ),
            providerTotalInputTokens =
                saturatedProviderUsageLongSum(
                    uncachedInputTokens,
                    cacheReadTokens,
                    cacheWriteTokens,
                ),
            providerUncachedInputTokens = uncachedInputTokens,
            providerCacheReadTokens = cacheReadTokens,
            providerCacheWriteTokens = cacheWriteTokens,
            providerOutputTokens =
                saturatedProviderUsageLongSum(
                    providerOutputTokens,
                    other.providerOutputTokens,
                ),
            providerReasoningTokens =
                saturatedProviderUsageLongSum(
                    providerReasoningTokens,
                    other.providerReasoningTokens,
                ),
        )
    }
}
