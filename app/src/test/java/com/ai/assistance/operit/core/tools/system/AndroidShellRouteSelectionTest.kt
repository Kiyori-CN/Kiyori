package com.ai.assistance.operit.core.tools.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidShellRouteSelectionTest {

    @Test
    fun configuredRootAlwaysSelectsRootRoute() {
        val selection =
            AndroidShellExecutor.selectPrivilegedShellRoute(
                configuredLevel = AndroidPermissionLevel.ROOT,
                shizukuServiceRunning = false,
                shizukuPermissionGranted = false,
            )

        assertEquals(PrivilegedShellRoute.ROOT, selection.route)
        assertEquals("configured ROOT", selection.reason)
    }

    @Test
    fun configuredDebuggerSelectsDebuggerEvenBeforeServiceSnapshot() {
        val selection =
            AndroidShellExecutor.selectPrivilegedShellRoute(
                configuredLevel = AndroidPermissionLevel.DEBUGGER,
                shizukuServiceRunning = false,
                shizukuPermissionGranted = false,
            )

        assertEquals(PrivilegedShellRoute.DEBUGGER, selection.route)
        assertEquals("configured DEBUGGER", selection.reason)
    }

    @Test
    fun unconfiguredAuthorizedShizukuSelectsDebugger() {
        val selection =
            AndroidShellExecutor.selectPrivilegedShellRoute(
                configuredLevel = null,
                shizukuServiceRunning = true,
                shizukuPermissionGranted = true,
            )

        assertEquals(PrivilegedShellRoute.DEBUGGER, selection.route)
        assertEquals("live authorized Shizuku", selection.reason)
    }

    @Test
    fun unconfiguredServiceUnavailableIsExplicitlyRejected() {
        val selection =
            AndroidShellExecutor.selectPrivilegedShellRoute(
                configuredLevel = null,
                shizukuServiceRunning = false,
                shizukuPermissionGranted = false,
            )

        assertEquals(null, selection.route)
        assertTrue(selection.reason.contains("service is not running"))
    }

    @Test
    fun unconfiguredPermissionDeniedIsExplicitlyRejected() {
        val selection =
            AndroidShellExecutor.selectPrivilegedShellRoute(
                configuredLevel = null,
                shizukuServiceRunning = true,
                shizukuPermissionGranted = false,
            )

        assertEquals(null, selection.route)
        assertTrue(selection.reason.contains("permission is not granted"))
    }

    @Test
    fun unconfiguredDeadBinderIsExplicitlyRejectedEvenWhenPermissionSnapshotIsGranted() {
        val selection =
            AndroidShellExecutor.selectPrivilegedShellRoute(
                configuredLevel = null,
                shizukuServiceRunning = true,
                shizukuPermissionGranted = true,
                shizukuBinderAlive = false,
            )

        assertEquals(null, selection.route)
        assertTrue(selection.reason.contains("binder is not alive"))
    }

    @Test
    fun nonPrivilegedConfiguredLevelsNeverChangeIdentity() {
        listOf(
            AndroidPermissionLevel.STANDARD,
            AndroidPermissionLevel.ACCESSIBILITY,
            AndroidPermissionLevel.ADMIN,
        ).forEach { level ->
            val selection =
                AndroidShellExecutor.selectPrivilegedShellRoute(
                    configuredLevel = level,
                    shizukuServiceRunning = true,
                    shizukuPermissionGranted = true,
                )

            assertEquals(null, selection.route)
            assertTrue(selection.reason.contains(level.name))
        }
    }
}
