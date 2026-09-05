package com.kiyori.platform.window

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriStatusBarAppearanceStateTest {
    @Test
    fun `same value declarations retain the remaining owner when either leaves`() {
        val state = KiyoriStatusBarAppearanceState()
        val first = Any()
        val second = Any()
        state.update(first, false)
        state.update(second, false)
        state.remove(first)
        assertEquals(false, state.darkIcons)
        state.remove(second)
        assertNull(state.darkIcons)

        state.update(first, true)
        state.update(second, true)
        state.remove(second)
        assertEquals(true, state.darkIcons)
    }

    @Test
    fun `updating an earlier declaration preserves the later page priority`() {
        val state = KiyoriStatusBarAppearanceState()
        val background = Any()
        val foreground = Any()
        state.update(background, false)
        state.update(foreground, false)
        state.update(background, true)
        assertEquals(false, state.darkIcons)
        state.remove(foreground)
        assertEquals(true, state.darkIcons)
    }

    @Test
    fun `updating and disposing an owner leaves no old declaration`() {
        val state = KiyoriStatusBarAppearanceState()
        val owner = Any()
        state.update(owner, false)
        state.update(owner, true)
        assertEquals(true, state.darkIcons)
        state.remove(owner)
        assertNull(state.darkIcons)
        state.remove(owner)
        assertNull(state.darkIcons)
    }

    @Test
    fun `null declaration explicitly follows the root theme until it leaves`() {
        val state = KiyoriStatusBarAppearanceState()
        val page = Any()
        val themePage = Any()
        state.update(page, false)
        state.update(themePage, null)
        assertNull(state.darkIcons)
        state.remove(themePage)
        assertEquals(false, state.darkIcons)
    }

    @Test
    fun `separate root windows never share declarations`() {
        val firstWindow = KiyoriStatusBarAppearanceState()
        val secondWindow = KiyoriStatusBarAppearanceState()
        val first = Any()
        val second = Any()
        firstWindow.update(first, false)
        assertNull(secondWindow.darkIcons)
        secondWindow.update(second, true)
        firstWindow.remove(first)
        assertNull(firstWindow.darkIcons)
        assertEquals(true, secondWindow.darkIcons)
    }

    @Test
    fun `composition observation is invalidated by changes but not identical updates`() {
        val state = KiyoriStatusBarAppearanceState()
        val owner = Any()
        val observer = SnapshotStateObserver { it() }
        var invalidated = false
        observer.start()
        try {
            observer.observeReads(Any(), { _: Any -> invalidated = true }) { state.darkIcons }
            state.update(owner, false)
            Snapshot.sendApplyNotifications()
            assertTrue(invalidated)

            invalidated = false
            observer.observeReads(Any(), { _: Any -> invalidated = true }) { state.darkIcons }
            state.update(owner, false)
            Snapshot.sendApplyNotifications()
            assertFalse(invalidated)
            state.remove(owner)
            Snapshot.sendApplyNotifications()
            assertTrue(invalidated)
        } finally {
            observer.stop()
            observer.clear()
        }
    }
}
