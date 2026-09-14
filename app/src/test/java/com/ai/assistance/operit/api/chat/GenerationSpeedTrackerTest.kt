package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.api.chat.llmprovider.ProviderCacheMetricState
import com.ai.assistance.operit.api.chat.llmprovider.ProviderUsageSnapshot
import com.ai.assistance.operit.api.chat.llmprovider.ProviderUsageSource
import com.ai.assistance.operit.data.model.ApiProtocol
import com.ai.assistance.operit.data.model.GenerationSpeed
import org.junit.Assert.*
import org.junit.Test

class GenerationSpeedTrackerTest {
    private var now = 0L
    private fun tracker(streaming: Boolean = true) = GenerationSpeedTracker(streaming) { now }
    private fun usage(output: Long = 120, source: ProviderUsageSource = ProviderUsageSource.PROVIDER) =
        ProviderUsageSnapshot("test", ApiProtocol.OPENAI_CHAT_COMPLETIONS, 0, 0, 0, 0, output, 0,
            ProviderCacheMetricState.NOT_REPORTED, source)

    @Test
    fun excludesInitialWaitAndPersistenceAndCorrectsEstimatedTokens() {
        val tracker = tracker()
        now = 15_000
        assertNull(tracker.onContent(""))
        assertNull(tracker.onTokens(0))
        now = 20_000
        assertNull(tracker.onContent("first"))
        now = 22_000
        tracker.onContent("second")
        assertEquals(50.0, tracker.onTokens(100)!!.tokensPerSecond!!, 0.000001)
        assertEquals(20_000L, tracker.onTokens(100)!!.firstOutputLatencyMs)
        tracker.stop()
        now = 90_000 // usage 存储或后续工具耗时不能改变已停止的分母。
        val completed = tracker.complete(usage())!!
        assertEquals(60.0, completed.tokensPerSecond!!, 0.000001)
        assertFalse(completed.isEstimated)
        assertEquals(22_000L, completed.requestDurationMs)
        assertEquals(120L, completed.providerUsage!!.providerOutputTokens)
        tracker.stop()
        assertEquals(completed, tracker.complete(usage()))
    }

    @Test
    fun missingOutputZeroDurationAndNonStreamingRemainUnavailable() {
        val tracker = tracker()
        assertNull(tracker.complete(usage())!!.tokensPerSecond)
        tracker.onContent("one chunk")
        tracker.stop()
        assertNull(tracker.complete(usage())!!.tokensPerSecond)
        now = 1_000
        assertNull(tracker.onTokens(120)) // 单个输出块不能把本地交付耗时伪装成生成速度。
        val nonStreaming = tracker(streaming = false)
        nonStreaming.onContent("all at once")
        now += 2_000
        nonStreaming.stop()
        assertNull(nonStreaming.complete(usage())!!.tokensPerSecond)
        assertEquals(2_000L, nonStreaming.complete(usage())!!.requestDurationMs)
    }

    @Test
    fun unknownAndLocalUsageDoNotBecomeProviderMeasurements() {
        val tracker = tracker()
        tracker.onContent("text")
        now = 1_000
        tracker.onContent("more text")
        tracker.stop()
        val unavailable = tracker.complete(usage(source = ProviderUsageSource.UNAVAILABLE))!!
        assertNull(unavailable.tokensPerSecond)
        assertEquals(0, unavailable.providerUsage!!.providerUsageRequestCount)
        val local = tracker.complete(usage(source = ProviderUsageSource.LOCAL_ESTIMATE))!!
        assertEquals(120.0, local.tokensPerSecond!!, 0.000001)
        assertTrue(local.isEstimated)
        assertNull(tracker.complete(usage(output = 0))!!.tokensPerSecond)
    }

    @Test
    fun rollbackInvalidatesOnlyThatRequest() {
        val tracker = tracker()
        tracker.onContent("rolled back")
        now = 1_000
        tracker.invalidate()
        assertNull(tracker.onTokens(40))
        tracker.stop()
        assertNull(tracker.complete(usage()))
        val next = tracker()
        next.onContent("new request")
        now = 3_000
        next.onContent("next chunk")
        next.stop()
        assertEquals(60.0, next.complete(usage())!!.tokensPerSecond!!, 0.000001)
    }

    @Test
    fun newRequestAndResetRejectLateUpdatesAndCleanup() {
        val monitor = GenerationSpeedMonitor()
        val old = monitor.start(true) { now }
        monitor.publish(old, GenerationSpeed(10.0, true))
        val current = monitor.start(true) { now }
        assertNull(monitor.flow.value)
        monitor.publish(current, GenerationSpeed(20.0, true))
        monitor.publish(old, null)
        monitor.publish(old, GenerationSpeed(999.0, false))
        assertEquals(GenerationSpeed(20.0, true), monitor.flow.value)
        monitor.clear()
        monitor.publish(current, GenerationSpeed(99.0, false))
        assertNull(monitor.flow.value)
    }
}
