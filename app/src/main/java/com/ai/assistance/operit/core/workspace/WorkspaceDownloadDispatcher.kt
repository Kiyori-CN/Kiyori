package com.ai.assistance.operit.core.workspace

/** Capability used by the workspace WebView to submit downloads without owning a transport. */
interface WorkspaceDownloadDispatcher {
    fun enqueueNetwork(request: WorkspaceDownloadRequest): Boolean

    fun enqueueInline(request: WorkspaceInlineDownloadRequest)
}

data class WorkspaceDownloadRequest(
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val contentLength: Long,
    val headers: Map<String, String>,
)

data class WorkspaceInlineDownloadRequest(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
)
