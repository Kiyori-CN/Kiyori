package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui

import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptInstallPreview
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptDraft
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptEditorReview
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptLogItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeStatus
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptRevisionInfo
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptSupportState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptUpdateCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class UserscriptDetailUiState(
    val userscriptId: Long,
    val isLoading: Boolean = false,
    val activeSource: String? = null,
    val draft: UserscriptDraft? = null,
    val revisions: List<UserscriptRevisionInfo> = emptyList(),
    val error: String? = null,
)

internal data class UserscriptEditorUiState(
    val draftId: String,
    val userscriptId: Long?,
    val isLoading: Boolean = false,
    val activeSource: String? = null,
    val buffer: String = "",
    val persistedSourceHash: String? = null,
    val hasUnpersistedChanges: Boolean = false,
    val review: UserscriptEditorReview? = null,
    val error: String? = null,
    val isPersisting: Boolean = false,
    val isValidating: Boolean = false,
    val isFormatting: Boolean = false,
    val isApplying: Boolean = false,
) {
    val hasUnappliedChanges: Boolean
        get() = !isLoading && (userscriptId == null || buffer != activeSource)
}

internal data class WebSessionUserscriptUiState(
    val supportState: UserscriptSupportState = UserscriptSupportState(isSupported = false),
    val userScriptsAllowed: Boolean = false,
    val installedScripts: List<UserscriptListItem> = emptyList(),
    val recentLogs: List<UserscriptLogItem> = emptyList(),
    val currentPageUrl: String? = null,
    val currentPageStatuses: Map<Long, UserscriptPageRuntimeStatus> = emptyMap(),
    val pendingInstall: UserscriptInstallPreview? = null,
    val drafts: List<UserscriptDraft> = emptyList(),
    val details: Map<Long, UserscriptDetailUiState> = emptyMap(),
    val editors: Map<String, UserscriptEditorUiState> = emptyMap(),
    val updateCandidates: Map<Long, UserscriptUpdateCandidate> = emptyMap(),
    val checkingUpdateIds: Set<Long> = emptySet(),
    val isCheckingAllUpdates: Boolean = false,
    val isApplyingSafeUpdates: Boolean = false,
)

internal class WebSessionUserscriptUiStateStore(
    initialSupportState: UserscriptSupportState
) {
    private val mutableState = MutableStateFlow(WebSessionUserscriptUiState(supportState = initialSupportState))
    val state: StateFlow<WebSessionUserscriptUiState> = mutableState.asStateFlow()

    fun updateSupportState(value: UserscriptSupportState) {
        mutableState.update { current -> current.copy(supportState = value) }
    }

    fun updateUserScriptsAllowed(allowed: Boolean) {
        mutableState.update { current -> current.copy(userScriptsAllowed = allowed) }
    }

    fun updateScripts(items: List<UserscriptListItem>) {
        mutableState.update { current -> current.copy(installedScripts = items) }
    }

    fun updateLogs(items: List<UserscriptLogItem>) {
        mutableState.update { current -> current.copy(recentLogs = items) }
    }

    fun updateCurrentPageSnapshot(
        pageUrl: String?,
        statuses: Map<Long, UserscriptPageRuntimeStatus>,
    ) {
        mutableState.update { current ->
            current.copy(
                currentPageUrl = pageUrl,
                currentPageStatuses = statuses,
            )
        }
    }

    fun setPendingInstall(preview: UserscriptInstallPreview?) {
        mutableState.update { current -> current.copy(pendingInstall = preview) }
    }

    fun updateDrafts(items: List<UserscriptDraft>) {
        mutableState.update { current -> current.copy(drafts = items) }
    }

    fun updateDetail(value: UserscriptDetailUiState) {
        mutableState.update { current ->
            current.copy(details = current.details + (value.userscriptId to value))
        }
    }

    fun removeDetail(scriptId: Long) {
        mutableState.update { current ->
            current.copy(details = current.details - scriptId)
        }
    }

    fun updateEditor(value: UserscriptEditorUiState) {
        mutableState.update { current ->
            current.copy(editors = current.editors + (value.draftId to value))
        }
    }

    fun updateEditor(
        draftId: String,
        transform: (UserscriptEditorUiState) -> UserscriptEditorUiState,
    ) {
        mutableState.update { current ->
            val existing = current.editors[draftId] ?: return@update current
            current.copy(editors = current.editors + (draftId to transform(existing)))
        }
    }

    fun removeEditor(draftId: String) {
        mutableState.update { current ->
            current.copy(editors = current.editors - draftId)
        }
    }

    fun setUpdateChecking(
        scriptId: Long,
        checking: Boolean,
    ) {
        mutableState.update { current ->
            current.copy(
                checkingUpdateIds =
                    if (checking) {
                        current.checkingUpdateIds + scriptId
                    } else {
                        current.checkingUpdateIds - scriptId
                    },
            )
        }
    }

    fun setCheckingAllUpdates(checking: Boolean) {
        mutableState.update { current ->
            current.copy(isCheckingAllUpdates = checking)
        }
    }

    fun setApplyingSafeUpdates(applying: Boolean) {
        mutableState.update { current ->
            current.copy(isApplyingSafeUpdates = applying)
        }
    }

    fun setUpdateCandidate(candidate: UserscriptUpdateCandidate?) {
        candidate ?: return
        mutableState.update { current ->
            current.copy(
                updateCandidates =
                    current.updateCandidates + (candidate.scriptId to candidate),
            )
        }
    }

    fun removeUpdateCandidate(scriptId: Long) {
        mutableState.update { current ->
            current.copy(updateCandidates = current.updateCandidates - scriptId)
        }
    }

    fun retainInstalledState(scriptIds: Set<Long>) {
        mutableState.update { current ->
            current.copy(
                details = current.details.filterKeys(scriptIds::contains),
                editors =
                    current.editors.filterValues { editor ->
                        editor.userscriptId == null || editor.userscriptId in scriptIds
                    },
                updateCandidates = current.updateCandidates.filterKeys(scriptIds::contains),
                checkingUpdateIds = current.checkingUpdateIds.intersect(scriptIds),
            )
        }
    }
}
