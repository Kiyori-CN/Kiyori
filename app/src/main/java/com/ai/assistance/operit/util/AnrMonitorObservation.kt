package com.ai.assistance.operit.util

internal data class AnrMonitorObservationSnapshot(
    val phase: String,
    val processLifecycleState: String,
    val firstFrameRendered: Boolean,
    val consecutiveDelayedSamples: Int,
    val totalWarningCount: Int,
    val totalAnrCount: Int,
    val maxBlockDurationMs: Long,
) {
    init {
        require(phase.isNotBlank()) { "phase must not be blank" }
        require(processLifecycleState.isNotBlank()) {
            "processLifecycleState must not be blank"
        }
        require(consecutiveDelayedSamples >= 0) {
            "consecutiveDelayedSamples must not be negative"
        }
        require(totalWarningCount >= 0) {
            "totalWarningCount must not be negative"
        }
        require(totalAnrCount >= 0) {
            "totalAnrCount must not be negative"
        }
        require(maxBlockDurationMs >= 0L) {
            "maxBlockDurationMs must not be negative"
        }
    }
}

/**
 * ANR 采样上下文的唯一状态 owner。
 *
 * 该类只记录观测事实，不决定阈值、调度器或堆栈采集策略。将状态从 AnrMonitor 中独立出来，
 * 使“连续延迟”和“首帧前后”合同可以在 JVM 中稳定回归验证。
 */
internal class AnrMonitorObservationState {
    companion object {
        const val PHASE_STARTUP_BEFORE_FIRST_FRAME = "startup_before_first_frame"
        const val PHASE_STARTUP_AFTER_FIRST_FRAME = "startup_after_first_frame"
        const val PHASE_USER_OPERATION = "user_operation"
        const val PROCESS_STATE_UNKNOWN = "unknown"
        const val PROCESS_STATE_FOREGROUND = "foreground"
        const val PROCESS_STATE_BACKGROUND = "background"
        const val PROCESS_STATE_DESTROYED = "destroyed"
    }

    private var phase = PHASE_STARTUP_BEFORE_FIRST_FRAME
    private var processLifecycleState = PROCESS_STATE_UNKNOWN
    private var firstFrameRendered = false
    private var consecutiveDelayedSamples = 0
    private var totalWarningCount = 0
    private var totalAnrCount = 0
    private var maxBlockDurationMs = 0L

    @Synchronized
    fun markFirstFrameRendered() {
        firstFrameRendered = true
        if (phase == PHASE_STARTUP_BEFORE_FIRST_FRAME) {
            phase = PHASE_STARTUP_AFTER_FIRST_FRAME
        }
    }

    @Synchronized
    fun markApplicationReady() {
        if (firstFrameRendered) {
            phase = PHASE_USER_OPERATION
        }
    }

    @Synchronized
    fun markProcessLifecycleState(state: String) {
        require(state.isNotBlank()) { "state must not be blank" }
        processLifecycleState = state
    }

    @Synchronized
    fun markDestroyed() {
        processLifecycleState = PROCESS_STATE_DESTROYED
    }

    @Synchronized
    fun markHealthy() {
        consecutiveDelayedSamples = 0
    }

    @Synchronized
    fun recordDelayedSample(
        responseTimeMs: Long,
        isPotentialAnr: Boolean,
    ): AnrMonitorObservationSnapshot {
        require(responseTimeMs >= 0L) { "responseTimeMs must not be negative" }
        consecutiveDelayedSamples += 1
        totalWarningCount += 1
        if (isPotentialAnr) {
            totalAnrCount += 1
        }
        if (responseTimeMs > maxBlockDurationMs) {
            maxBlockDurationMs = responseTimeMs
        }
        return snapshotLocked()
    }

    @Synchronized
    fun snapshot(): AnrMonitorObservationSnapshot = snapshotLocked()

    private fun snapshotLocked(): AnrMonitorObservationSnapshot =
        AnrMonitorObservationSnapshot(
            phase = phase,
            processLifecycleState = processLifecycleState,
            firstFrameRendered = firstFrameRendered,
            consecutiveDelayedSamples = consecutiveDelayedSamples,
            totalWarningCount = totalWarningCount,
            totalAnrCount = totalAnrCount,
            maxBlockDurationMs = maxBlockDurationMs,
        )
}
