package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptBridgeAuthorizationPolicyTest {
    private val authorization =
        UserscriptBridgeAuthorization(
            scriptId = 41L,
            token = "token-41",
            grants = setOf("GM.setValue", "GM.xmlHttpRequest"),
        )

    @Test
    fun `matching script token and grant are authorized`() {
        assertTrue(
            UserscriptBridgeAuthorizationPolicy.isAuthorized(
                expectedUserscriptId = 41L,
                messageType = "storage_set",
                payloadUserscriptId = 41L,
                presentedToken = "token-41",
                authorization = authorization,
            ),
        )
    }

    @Test
    fun `cross-script impersonation is rejected`() {
        assertFalse(
            UserscriptBridgeAuthorizationPolicy.isAuthorized(
                expectedUserscriptId = 42L,
                messageType = "storage_set",
                payloadUserscriptId = 41L,
                presentedToken = "token-41",
                authorization = authorization,
            ),
        )
    }

    @Test
    fun `incorrect token is rejected`() {
        assertFalse(
            UserscriptBridgeAuthorizationPolicy.isAuthorized(
                expectedUserscriptId = 41L,
                messageType = "storage_set",
                payloadUserscriptId = 41L,
                presentedToken = "wrong-token",
                authorization = authorization,
            ),
        )
    }

    @Test
    fun `missing grant is rejected`() {
        assertFalse(
            UserscriptBridgeAuthorizationPolicy.isAuthorized(
                expectedUserscriptId = 41L,
                messageType = "gm_cookie",
                payloadUserscriptId = 41L,
                presentedToken = "token-41",
                authorization = authorization,
            ),
        )
    }

    @Test
    fun `internal status message is restricted to the owning script`() {
        assertTrue(
            UserscriptBridgeAuthorizationPolicy.isAuthorized(
                expectedUserscriptId = 41L,
                messageType = "script_status",
                payloadUserscriptId = 41L,
                presentedToken = "token-41",
                authorization = authorization,
            ),
        )
        assertFalse(
            UserscriptBridgeAuthorizationPolicy.isAuthorized(
                expectedUserscriptId = 42L,
                messageType = "script_status",
                payloadUserscriptId = 41L,
                presentedToken = "token-41",
                authorization = authorization,
            ),
        )
    }

    @Test
    fun `unknown bridge message is rejected`() {
        assertFalse(
            UserscriptBridgeAuthorizationPolicy.isAuthorized(
                expectedUserscriptId = 41L,
                messageType = "unknown_operation",
                payloadUserscriptId = 41L,
                presentedToken = "token-41",
                authorization = authorization,
            ),
        )
    }
}
