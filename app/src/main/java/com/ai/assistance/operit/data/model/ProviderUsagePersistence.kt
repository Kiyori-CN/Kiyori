package com.ai.assistance.operit.data.model

fun ChatHistory.toProviderUsageAggregate(): ProviderUsageAggregate =
    ProviderUsageAggregate(
        requestCount = providerRequestCount,
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

fun ChatEntity.toProviderUsageAggregate(): ProviderUsageAggregate =
    ProviderUsageAggregate(
        requestCount = providerRequestCount,
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

fun ChatMessage.toProviderUsageAggregate(): ProviderUsageAggregate =
    ProviderUsageAggregate(
        requestCount = providerRequestCount,
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

fun ChatMessage.withProviderUsageAggregate(
    usage: ProviderUsageAggregate,
): ChatMessage =
    copy(
        providerRequestCount = usage.requestCount,
        providerUsageRequestCount = usage.providerUsageRequestCount,
        providerCacheMetricRequestCount = usage.providerCacheMetricRequestCount,
        providerCacheMetricPromptTokens = usage.providerCacheMetricPromptTokens,
        providerTotalInputTokens = usage.providerTotalInputTokens,
        providerUncachedInputTokens = usage.providerUncachedInputTokens,
        providerCacheReadTokens = usage.providerCacheReadTokens,
        providerCacheWriteTokens = usage.providerCacheWriteTokens,
        providerOutputTokens = usage.providerOutputTokens,
        providerReasoningTokens = usage.providerReasoningTokens,
    )
