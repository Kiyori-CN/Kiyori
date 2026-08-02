package com.kiyori.app.startup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriMainTaskVisibilityCoordinatorTest {
    @Test
    fun `platforms below lollipop do not restore runtime task visibility`() {
        assertFalse(
            shouldRestoreRuntimeTaskVisibility(
                sdkInt = 20,
                isRuntimeForegroundServiceRunning = false,
            )
        )
    }

    @Test
    fun `running foreground service keeps runtime task visibility unchanged`() {
        assertFalse(
            shouldRestoreRuntimeTaskVisibility(
                sdkInt = 21,
                isRuntimeForegroundServiceRunning = true,
            )
        )
    }

    @Test
    fun `supported platform restores visibility when foreground service is stopped`() {
        assertTrue(
            shouldRestoreRuntimeTaskVisibility(
                sdkInt = 21,
                isRuntimeForegroundServiceRunning = false,
            )
        )
    }
}
