package com.kiyori.platform.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class KiyoriNotificationPermissionCapabilityTest {
    @Test
    fun `platforms below android thirteen do not request notification permission`() {
        assertEquals(
            KiyoriNotificationPermissionAction.NOT_REQUIRED,
            resolveKiyoriNotificationPermissionAction(
                sdkInt = 32,
                isGranted = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun `granted permission takes precedence over rationale`() {
        assertEquals(
            KiyoriNotificationPermissionAction.ALREADY_GRANTED,
            resolveKiyoriNotificationPermissionAction(
                sdkInt = 33,
                isGranted = true,
                shouldShowRationale = true,
            ),
        )
    }

    @Test
    fun `denied permission with rationale shows explanation before request`() {
        assertEquals(
            KiyoriNotificationPermissionAction.SHOW_RATIONALE_AND_REQUEST,
            resolveKiyoriNotificationPermissionAction(
                sdkInt = 33,
                isGranted = false,
                shouldShowRationale = true,
            ),
        )
    }

    @Test
    fun `denied permission without rationale requests directly`() {
        assertEquals(
            KiyoriNotificationPermissionAction.REQUEST,
            resolveKiyoriNotificationPermissionAction(
                sdkInt = 33,
                isGranted = false,
                shouldShowRationale = false,
            ),
        )
    }
}
