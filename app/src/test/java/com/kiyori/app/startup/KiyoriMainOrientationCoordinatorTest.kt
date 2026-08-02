package com.kiyori.app.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriMainOrientationCoordinatorTest {
    @Test
    fun `unchanged orientation preserves the same state instance`() {
        val currentState =
            KiyoriMainOrientationState(
                lastOrientation = 1,
                showChangeDialog = false,
            )

        assertSame(
            currentState,
            resolveKiyoriMainOrientationChange(
                currentState = currentState,
                newOrientation = 1,
            ),
        )
    }

    @Test
    fun `orientation change updates the orientation and shows the dialog`() {
        assertEquals(
            KiyoriMainOrientationState(
                lastOrientation = 2,
                showChangeDialog = true,
            ),
            resolveKiyoriMainOrientationChange(
                currentState =
                    KiyoriMainOrientationState(
                        lastOrientation = 1,
                        showChangeDialog = false,
                    ),
                newOrientation = 2,
            ),
        )
    }

    @Test
    fun `another orientation change keeps the visible dialog and records new value`() {
        assertEquals(
            KiyoriMainOrientationState(
                lastOrientation = 1,
                showChangeDialog = true,
            ),
            resolveKiyoriMainOrientationChange(
                currentState =
                    KiyoriMainOrientationState(
                        lastOrientation = 2,
                        showChangeDialog = true,
                    ),
                newOrientation = 1,
            ),
        )
    }

    @Test
    fun `coordinator initializes without a dialog and dismisses a shown dialog`() {
        val coordinator =
            KiyoriMainOrientationCoordinator(
                orientationChangeLogger = { _, _ -> },
            )

        coordinator.initialize(initialOrientation = 1)
        assertFalse(coordinator.showChangeDialog)

        coordinator.onConfigurationChanged(newOrientation = 2)
        assertTrue(coordinator.showChangeDialog)

        coordinator.dismissChangeDialog()
        assertFalse(coordinator.showChangeDialog)
    }
}
