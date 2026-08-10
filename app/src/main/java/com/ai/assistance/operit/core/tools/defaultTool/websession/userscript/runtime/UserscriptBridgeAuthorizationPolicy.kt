package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.runtime

import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptCapabilityRegistry
import java.security.MessageDigest

internal data class UserscriptBridgeAuthorization(
    val scriptId: Long,
    val token: String,
    val grants: Set<String>,
)

internal enum class UserscriptPermissionDecision {
    ALLOW,
    RETURN_EMPTY_BOOTSTRAP,
    REJECT_PERMISSION,
}

internal object UserscriptBridgeAuthorizationPolicy {
    const val EMPTY_BOOTSTRAP_PAYLOAD_JSON = """{"scripts":[]}"""

    private val internalMessageTypes = setOf("script_status", "runtime_log")

    fun permissionDecision(
        userScriptsAllowed: Boolean,
        messageType: String,
    ): UserscriptPermissionDecision =
        when {
            userScriptsAllowed -> UserscriptPermissionDecision.ALLOW
            messageType == "bootstrap_request" -> UserscriptPermissionDecision.RETURN_EMPTY_BOOTSTRAP
            else -> UserscriptPermissionDecision.REJECT_PERMISSION
        }

    fun isAuthorized(
        expectedUserscriptId: Long,
        messageType: String,
        payloadUserscriptId: Long,
        presentedToken: String,
        authorization: UserscriptBridgeAuthorization?,
    ): Boolean {
        if (
            payloadUserscriptId <= 0L ||
                payloadUserscriptId != expectedUserscriptId ||
                presentedToken.isBlank() ||
                authorization == null ||
                authorization.scriptId != expectedUserscriptId
        ) {
            return false
        }
        if (
            !MessageDigest.isEqual(
                authorization.token.toByteArray(Charsets.UTF_8),
                presentedToken.toByteArray(Charsets.UTF_8),
            )
        ) {
            return false
        }
        val requiredGrants = UserscriptCapabilityRegistry.grantsForHostMessage(messageType)
        return when {
            requiredGrants.isNotEmpty() -> requiredGrants.any(authorization.grants::contains)
            messageType in internalMessageTypes -> true
            else -> false
        }
    }
}
