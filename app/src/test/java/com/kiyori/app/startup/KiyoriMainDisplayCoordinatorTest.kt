package com.kiyori.app.startup

import org.junit.Assert.assertEquals
import org.junit.Test

class KiyoriMainDisplayCoordinatorTest {
    @Test
    fun `mode selection ignores rates at or below sixty hertz`() {
        assertEquals(
            KiyoriDisplayModeSelection(modeId = 0, refreshRate = 60f),
            selectHighestRefreshRateMode(
                listOf(
                    KiyoriDisplayModeCandidate(modeId = 1, refreshRate = 59.94f),
                    KiyoriDisplayModeCandidate(modeId = 2, refreshRate = 60f),
                )
            ),
        )
    }

    @Test
    fun `mode selection keeps first mode when highest refresh rates tie`() {
        assertEquals(
            KiyoriDisplayModeSelection(modeId = 2, refreshRate = 120f),
            selectHighestRefreshRateMode(
                listOf(
                    KiyoriDisplayModeCandidate(modeId = 1, refreshRate = 90f),
                    KiyoriDisplayModeCandidate(modeId = 2, refreshRate = 120f),
                    KiyoriDisplayModeCandidate(modeId = 3, refreshRate = 120f),
                )
            ),
        )
    }

    @Test
    fun `legacy refresh-rate selection returns highest value above sixty`() {
        assertEquals(
            144f,
            selectHighestRefreshRate(listOf(60f, 90f, 144f, 120f)),
        )
        assertEquals(
            60f,
            selectHighestRefreshRate(listOf(30f, 59.94f, 60f)),
        )
    }
}
