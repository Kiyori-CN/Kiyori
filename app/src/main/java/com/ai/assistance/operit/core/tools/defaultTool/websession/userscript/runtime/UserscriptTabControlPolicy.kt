package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.runtime

internal object UserscriptTabControlPolicy {
    const val CURRENT_SESSION = "current_session"
    const val OPENED_TAB = "opened_tab"

    fun canControl(
        sourceSessionId: String,
        targetSessionId: String,
        userscriptId: Long,
        grants: Set<String>,
        controlKind: String,
        currentSessionGrant: String,
        ownerSessionId: String?,
        ownerUserscriptId: Long?,
    ): Boolean =
        when (controlKind) {
            CURRENT_SESSION ->
                targetSessionId == sourceSessionId &&
                    currentSessionGrant in grants

            OPENED_TAB ->
                "GM.openInTab" in grants &&
                    ownerSessionId == sourceSessionId &&
                    ownerUserscriptId == userscriptId

            else -> false
        }
}
