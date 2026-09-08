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
        assertEquals(GenerationSpeed(50.0, true), tracker.onTokens(100))
        tracker.stop()
        now = 90_000 // usage 存储或后续工具耗时不能改变已停止的分母。
        assertEquals(GenerationSpeed(60.0, false), tracker.complete(usage()))
        tracker.stop()
        assertEquals(GenerationSpeed(60.0, false), tracker.complete(usage()))
    }

    @Test
    fun missingOutputZeroDurationAndNonStreamingRemainUnavailable() {
        val tracker = tracker()
        assertNull(tracker.complete(usage()))
        tracker.onContent("one chunk")
        tracker.stop()
        assertNull(tracker.complete(usage()))
        now = 1_000
        assertNull(tracker.onTokens(120)) // 单个输出块不能把本地交付耗时伪装成生成速度。
        val nonStreaming = tracker(streaming = false)
        nonStreaming.onContent("all at once")
        now += 2_000
        nonStreaming.stop()
        assertNull(nonStreaming.complete(usage()))
    }

    @Test
    fun unknownAndLocalUsageDoNotBecomeProviderMeasurements() {
        val tracker = tracker()
        tracker.onContent("text")
        now = 1_000
        tracker.onContent("more text")
        tracker.stop()
        assertNull(tracker.complete(usage(source = ProviderUsageSource.UNAVAILABLE)))
        assertEquals(GenerationSpeed(120.0, true),
            tracker.complete(usage(source = ProviderUsageSource.LOCAL_ESTIMATE)))
        assertNull(tracker.complete(usage(output = 0)))
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
        assertEquals(GenerationSpeed(60.0, false), next.complete(usage()))
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
