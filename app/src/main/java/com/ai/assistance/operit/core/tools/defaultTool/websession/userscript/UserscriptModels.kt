package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

import kotlinx.serialization.Serializable

internal enum class UserscriptRunAt(val rawValue: String) {
    DOCUMENT_START("document-start"),
    DOCUMENT_BODY("document-body"),
    DOCUMENT_END("document-end"),
    DOCUMENT_IDLE("document-idle"),
    UNSUPPORTED("unsupported");

    companion object {
        fun fromRaw(raw: String?): UserscriptRunAt {
            val normalized = raw?.trim().orEmpty()
            if (normalized.isBlank()) {
                return DOCUMENT_END
            }
            return entries.firstOrNull { it.rawValue.equals(normalized, ignoreCase = true) }
                ?: UNSUPPORTED
        }
    }
}

@Serializable
internal enum class UserscriptInjectInto(val rawValue: String) {
    AUTO("auto"),
    CONTENT("content"),
    PAGE("page"),
    UNSUPPORTED("unsupported");

    companion object {
        fun fromRaw(raw: String?): UserscriptInjectInto {
            val normalized = raw?.trim().orEmpty()
            if (normalized.isBlank()) {
                return AUTO
            }
            return entries.firstOrNull { it.rawValue.equals(normalized, ignoreCase = true) }
                ?: UNSUPPORTED
        }
    }
}

internal enum class UserscriptExecutionWorld {
    PAGE,
    ISOLATED,
}

internal enum class UserscriptUnsafeWindowMode {
    NONE,
    DIRECT_PAGE,
    ISOLATED_PAGE_BRIDGE,
}

internal enum class UserscriptInstallSourceType {
    LOCAL_FILE,
    REMOTE_URL,
    PAGE_LINK,
    UPDATE,
    TOOL_INPUT
}

@Serializable
internal data class UserscriptRequireEntry(
    val url: String
)

@Serializable
internal data class UserscriptHeaderEntry(
    val key: String,
    val value: String = ""
)

@Serializable
internal data class UserscriptResourceEntry(
    val name: String,
    val url: String
)

@Serializable
internal data class UserscriptIconSet(
    val icon: String? = null,
    val icon64: String? = null,
    val defaultIcon: String? = null
)

@Serializable
internal data class ParsedUserscriptMetadata(
    val name: String,
    val namespace: String? = null,
    val version: String = "0",
    val description: String? = null,
    val author: String? = null,
    val homepage: String? = null,
    val website: String? = null,
    val supportUrl: String? = null,
    val downloadUrl: String? = null,
    val updateUrl: String? = null,
    val runAt: UserscriptRunAt = UserscriptRunAt.DOCUMENT_END,
    val grants: List<String> = emptyList(),
    val matches: List<String> = emptyList(),
    val includes: List<String> = emptyList(),
    val excludes: List<String> = emptyList(),
    val excludeMatches: List<String> = emptyList(),
    val connects: List<String> = emptyList(),
    val requires: List<UserscriptRequireEntry> = emptyList(),
    val resources: List<UserscriptResourceEntry> = emptyList(),
    val icons: UserscriptIconSet = UserscriptIconSet(),
    val tags: List<String> = emptyList(),
    val injectInto: UserscriptInjectInto = UserscriptInjectInto.AUTO,
    val sandbox: String? = null,
    val runIn: String? = null,
    val unwrap: Boolean = false,
    val webRequestRules: List<String> = emptyList(),
    val rawHeaders: List<UserscriptHeaderEntry> = emptyList(),
    val noFrames: Boolean = false,
    val metadataBlock: String = ""
)

internal data class UserscriptInstallPreview(
    val metadata: ParsedUserscriptMetadata,
    val rawSource: String,
    val sourceType: UserscriptInstallSourceType,
    val sourceUrl: String? = null,
    val sourceDisplay: String? = null,
    val sourceEtag: String? = null,
    val sourceLastModifiedHeader: String? = null,
    val knownGrants: List<String> = emptyList(),
    val unknownGrants: List<String> = emptyList(),
    val blockedReasons: List<String> = emptyList(),
    val executionWorld: UserscriptExecutionWorld? = null,
    val unsafeWindowMode: UserscriptUnsafeWindowMode = UserscriptUnsafeWindowMode.NONE,
    val isUpdate: Boolean = false,
    val existingScriptId: Long? = null,
    val expectedRevisionId: String? = null,
    val requireNew: Boolean = false,
    val enabledOnCommit: Boolean? = null,
)

@Serializable
internal data class UserscriptBootstrapPayload(
    val scripts: List<UserscriptExecutionPayload> = emptyList()
)

@Serializable
internal data class UserscriptExecutionPayload(
    val scriptId: Long,
    val sessionId: String,
    val pageUrl: String,
    val name: String,
    val namespace: String? = null,
    val version: String,
    val runAt: String,
    val grants: List<String>,
    val capabilities: List<String>,
    val metadataJson: String,
    val code: String,
    val requires: List<String>,
    val values: Map<String, String>,
    val resources: Map<String, UserscriptResourcePayload>,
    val authorizationToken: String = "",
)

@Serializable
internal data class UserscriptResourcePayload(
    val text: String? = null,
    val dataUrl: String? = null
)

internal data class UserscriptListItem(
    val id: Long,
    val name: String,
    val namespace: String?,
    val version: String,
    val description: String?,
    val sourceDisplay: String?,
    val enabled: Boolean,
    val unknownGrants: List<String>,
    val blockedReasons: List<String>,
    val executionWorld: UserscriptExecutionWorld?,
    val unsafeWindowMode: UserscriptUnsafeWindowMode,
    val runAt: UserscriptRunAt,
    val grants: List<String>,
    val matches: List<String>,
    val includes: List<String>,
    val excludes: List<String>,
    val excludeMatches: List<String>,
    val connects: List<String>,
    val requires: List<UserscriptRequireEntry>,
    val resources: List<UserscriptResourceEntry>,
    val homepage: String?,
    val website: String?,
    val supportUrl: String?,
    val icons: UserscriptIconSet,
    val tags: List<String>,
    val injectInto: UserscriptInjectInto,
    val sandbox: String?,
    val runIn: String?,
    val noFrames: Boolean,
    val unwrap: Boolean,
    val webRequestRules: List<String>,
    val sourceUrl: String?,
    val updateUrl: String?,
    val downloadUrl: String?,
    val installedAt: Long,
    val updatedAt: Long
)

internal data class UserscriptDraft(
    val draftId: String,
    val userscriptId: Long?,
    val baseRevisionId: String?,
    val sourceHash: String,
    val source: String,
    val updatedAt: Long,
)

internal data class UserscriptRevisionInfo(
    val userscriptId: Long,
    val revisionId: String,
    val revisionNumber: Long,
    val version: String,
    val sourceHash: String,
    val sourceType: UserscriptInstallSourceType,
    val sourceUrl: String?,
    val sourceEtag: String?,
    val sourceLastModifiedHeader: String?,
    val createdAt: Long,
    val active: Boolean,
)

internal data class UserscriptLogItem(
    val id: Long,
    val userscriptId: Long?,
    val level: String,
    val message: String,
    val pageUrl: String?,
    val createdAt: Long
)

internal data class UserscriptPageMenuCommand(
    val commandId: String,
    val title: String,
    val userscriptId: Long,
    val runtimeCommandId: String = commandId,
)

internal data class UserscriptSupportState(
    val isSupported: Boolean,
    val reason: String? = null
)

internal enum class UserscriptPageRuntimeState {
    NO_ACTIVE_PAGE,
    DISABLED,
    PERMISSION_REQUIRED,
    UNSUPPORTED,
    NOT_MATCHED,
    MATCHED,
    QUEUED,
    RUNNING,
    SUCCESS,
    ERROR
}

internal data class UserscriptPageRuntimeStatus(
    val state: UserscriptPageRuntimeState,
    val detail: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
