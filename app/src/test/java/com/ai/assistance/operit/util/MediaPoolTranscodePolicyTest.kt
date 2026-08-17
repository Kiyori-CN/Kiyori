package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPoolTranscodePolicyTest {
    @Test
    fun audioPlanUsesSingleBoundedBitrate() {
        val plan =
            requireNotNull(
                resolveMediaPoolTranscodePlan(
                    kind = MediaPoolTranscodeKind.AUDIO,
                    durationSeconds = 120.0,
                    targetBytes = 20L * 1024 * 1024,
                ),
            )

        assertEquals(96, plan.audioBitrateKbps)
        assertNull(plan.videoBitrateKbps)
    }

    @Test
    fun videoPlanReservesAudioBudgetAndCapsVideoRate() {
        val plan =
            requireNotNull(
                resolveMediaPoolTranscodePlan(
                    kind = MediaPoolTranscodeKind.VIDEO,
                    durationSeconds = 60.0,
                    targetBytes = 20L * 1024 * 1024,
                ),
            )

        assertEquals(48, plan.audioBitrateKbps)
        assertEquals(1_500, plan.videoBitrateKbps)
    }

    @Test
    fun durationThatCannotMeetMinimumRateIsRejectedBeforeEncoding() {
        assertNull(
            resolveMediaPoolTranscodePlan(
                kind = MediaPoolTranscodeKind.VIDEO,
                durationSeconds = 10_000.0,
                targetBytes = 20L * 1024 * 1024,
            ),
        )
    }

    @Test
    fun invalidDurationAndTargetAreRejected() {
        assertNull(
            resolveMediaPoolTranscodePlan(
                MediaPoolTranscodeKind.AUDIO,
                Double.NaN,
                1L,
            ),
        )
        assertNull(
            resolveMediaPoolTranscodePlan(
                MediaPoolTranscodeKind.AUDIO,
                1.0,
                0L,
            ),
        )
        assertTrue(
            requireNotNull(
                resolveMediaPoolTranscodePlan(
                    MediaPoolTranscodeKind.AUDIO,
                    1.0,
                    1_000_000L,
                ),
            ).audioBitrateKbps > 0,
        )
    }
}
