package com.kiyori.integration.operit.onboarding

import android.Manifest
import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriOnboardingPermissionsTest {
    @Test
    fun `android thirteen requests notification and scoped media permissions`() {
        val permissions = kiyoriRuntimePermissionsForSdk(Build.VERSION_CODES.TIRAMISU)

        assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)
        assertTrue(Manifest.permission.READ_MEDIA_AUDIO in permissions)
        assertTrue(Manifest.permission.READ_MEDIA_VIDEO in permissions)
        assertFalse(Manifest.permission.READ_EXTERNAL_STORAGE in permissions)
        assertFalse(Manifest.permission.WRITE_EXTERNAL_STORAGE in permissions)
    }

    @Test
    fun `android twelve requests read storage without obsolete write storage`() {
        val permissions = kiyoriRuntimePermissionsForSdk(Build.VERSION_CODES.S)

        assertTrue(Manifest.permission.READ_EXTERNAL_STORAGE in permissions)
        assertFalse(Manifest.permission.WRITE_EXTERNAL_STORAGE in permissions)
        assertTrue(Manifest.permission.BLUETOOTH_CONNECT in permissions)
        assertTrue(Manifest.permission.BLUETOOTH_SCAN in permissions)
    }

    @Test
    fun `android nine includes both legacy storage permissions`() {
        val permissions = kiyoriRuntimePermissionsForSdk(Build.VERSION_CODES.P)

        assertTrue(Manifest.permission.READ_EXTERNAL_STORAGE in permissions)
        assertTrue(Manifest.permission.WRITE_EXTERNAL_STORAGE in permissions)
        assertFalse(Manifest.permission.POST_NOTIFICATIONS in permissions)
    }

    @Test
    fun `all supported sdk variants keep high intent runtime permissions centralized`() {
        listOf(
            Build.VERSION_CODES.P,
            Build.VERSION_CODES.S,
            Build.VERSION_CODES.TIRAMISU,
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        ).forEach { sdkInt ->
            val permissions = kiyoriRuntimePermissionsForSdk(sdkInt)

            assertTrue(Manifest.permission.CAMERA in permissions)
            assertTrue(Manifest.permission.RECORD_AUDIO in permissions)
            assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in permissions)
            assertTrue(Manifest.permission.ACCESS_COARSE_LOCATION in permissions)
            assertTrue(Manifest.permission.CALL_PHONE in permissions)
            assertTrue(Manifest.permission.SEND_SMS in permissions)
            assertTrue(Manifest.permission.READ_SMS in permissions)
            assertTrue(Manifest.permission.RECEIVE_SMS in permissions)
            assertTrue(permissions.size == permissions.distinct().size)
        }
    }

    @Test
    fun `selected permission ids limit the runtime request set`() {
        val permissions =
            kiyoriRuntimePermissionsForSdk(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                selectedPermissionIds =
                    setOf(
                        KiyoriPermissionId.MICROPHONE,
                        KiyoriPermissionId.MEDIA,
                    ),
            )

        assertTrue(Manifest.permission.RECORD_AUDIO in permissions)
        assertTrue(Manifest.permission.READ_MEDIA_AUDIO in permissions)
        assertTrue(Manifest.permission.READ_MEDIA_VIDEO in permissions)
        assertFalse(Manifest.permission.CAMERA in permissions)
        assertFalse(Manifest.permission.POST_NOTIFICATIONS in permissions)
        assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in permissions)
    }
}
