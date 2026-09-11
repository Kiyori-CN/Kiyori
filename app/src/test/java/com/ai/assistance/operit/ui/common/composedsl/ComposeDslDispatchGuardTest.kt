package com.ai.assistance.operit.ui.common.composedsl

import org.junit.Assert.*
import org.junit.Test

class ComposeDslDispatchGuardTest {
    @Test fun `completed callback never becomes eligible after many newer actions`() {
        val guard = ComposeDslDispatchGuard()
        val old = requireNotNull(guard.begin())
        assertTrue(guard.complete(old))
        repeat(200) { assertTrue(guard.complete(requireNotNull(guard.begin()))) }
        assertFalse(guard.isActive(old))
        assertFalse(guard.complete(old))
    }

    @Test fun `rebuild invalidates outstanding calls without invalidating replacements`() {
        val guard = ComposeDslDispatchGuard()
        val old = requireNotNull(guard.begin())
        guard.reset()
        val current = requireNotNull(guard.begin())
        assertNotEquals(old, current)
        assertFalse(guard.complete(old))
        assertTrue(guard.isActive(current))
    }

    @Test fun `dispose prevents callbacks and new dispatch even after reset`() {
        val guard = ComposeDslDispatchGuard()
        val old = requireNotNull(guard.begin())
        guard.close()
        guard.reset()
        assertFalse(guard.isOpen())
        assertNull(guard.begin())
        assertFalse(guard.isActive(old))
    }
}
