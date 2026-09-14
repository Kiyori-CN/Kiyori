package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.api.chat.llmprovider.ProviderUsageSnapshot
import com.ai.assistance.operit.api.chat.llmprovider.ProviderUsageSource
import com.ai.assistance.operit.api.chat.llmprovider.toProviderUsageAggregate
import com.ai.assistance.operit.data.model.GenerationSpeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 服务内只发布最新请求；迟到的回调和旧请求的清理不能覆盖新请求。 */
internal class GenerationSpeedMonitor {
    private var active: GenerationSpeedTracker? = null
    private val speed = MutableStateFlow<GenerationSpeed?>(null)
    val flow = speed.asStateFlow()

    @Synchronized
    fun start(streaming: Boolean, nowMs: () -> Long): GenerationSpeedTracker {
        val tracker = GenerationSpeedTracker(streaming, nowMs)
        active = tracker
        speed.value = null
        return tracker
    }

    @Synchronized
    fun publish(tracker: GenerationSpeedTracker, value: GenerationSpeed?) {
        if (active === tracker) speed.value = value
    }

    @Synchronized
    fun clear() {
        active = null
        speed.value = null
    }
}

/** 每个 provider 请求单独计时，工具执行、首包等待和统计持久化都不进入分母。 */
internal class GenerationSpeedTracker(
    private val streaming: Boolean,
    private val nowMs: () -> Long,
) {
    private val startedMs = nowMs()
    private var firstOutputMs: Long? = null
    private var stoppedMs: Long? = null
    private var outputTokens = 0L
    private var outputChunks = 0
    private var invalid = false

    @Synchronized
    fun onContent(content: String): GenerationSpeed? {
        if (content.isNotEmpty()) {
            if (firstOutputMs == null) firstOutputMs = nowMs()
            if (outputChunks < 2) outputChunks++
        }
        return sample(outputTokens, estimated = true)
    }

    @Synchronized
    fun onTokens(output: Int): GenerationSpeed? {
        outputTokens = output.toLong().coerceAtLeast(0L)
        return sample(outputTokens, estimated = true)
    }

    @Synchronized
    fun stop() {
        if (stoppedMs == null) stoppedMs = nowMs()
    }

    @Synchronized
    fun invalidate() {
        // 回滚后的 token 与已消耗时间不再属于同一输出，不能拼成一个速度。
        invalid = true
    }

    @Synchronized
    fun complete(usage: ProviderUsageSnapshot): GenerationSpeed? =
        if (invalid) null else {
            val measured = sample(if (usage.outputTokensReported && usage.source != ProviderUsageSource.UNAVAILABLE) usage.outputTokens else 0,
                estimated = usage.source != ProviderUsageSource.PROVIDER)
                ?: GenerationSpeed(null, usage.source != ProviderUsageSource.PROVIDER)
            measured.copy(
                firstOutputLatencyMs = firstOutputMs?.let { (it - startedMs).coerceAtLeast(0L) },
                requestDurationMs = stoppedMs?.let { (it - startedMs).coerceAtLeast(0L) },
                providerModel = usage.providerModel,
                providerUsage = usage.toProviderUsageAggregate(),
                inputTokens = usage.totalInputTokens.takeIf { usage.inputTokensReported && usage.source == ProviderUsageSource.PROVIDER },
                outputTokens = usage.outputTokens.takeIf { usage.outputTokensReported && usage.source == ProviderUsageSource.PROVIDER },
                reasoningTokens = usage.reasoningTokens.takeIf { usage.reasoningTokensReported },
                cacheWriteTokens = usage.cacheWriteTokens.takeIf { usage.cacheWriteTokensReported },
            )
        }

    private fun sample(tokens: Long, estimated: Boolean): GenerationSpeed? {
        val first = firstOutputMs ?: return null
        val elapsed = (stoppedMs ?: nowMs()) - first
        // 非流式响应没有可观测的生成区间，不能把一次交付的耗时当作生成速度。
        if (!streaming || invalid || outputChunks < 2 || elapsed <= 0L || tokens <= 0L) return null
        return GenerationSpeed(tokens.toDouble() * 1000.0 / elapsed, estimated,
            firstOutputLatencyMs = (first - startedMs).coerceAtLeast(0L))
    }
}
