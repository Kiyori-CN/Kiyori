package com.ai.assistance.operit.ui.features.chat.webview.workspace

data class WorkspaceCommandExecutionState(
    val workspacePath: String,
    val commandLabel: String,
    val commandText: String,
    val sessionId: String,
    val usesDedicatedSession: Boolean,
    val outputEntries: List<String> = emptyList(),
    val isRunning: Boolean = true,
    val isVisible: Boolean = true,
    val isCancelling: Boolean = false
)

fun String.toWorkspaceCommandOutputEntries(): List<String> {
    if (isEmpty()) return emptyList()
    val normalized = replace("\r\n", "\n")
        .replace('\r', '\n')
    val entries = normalized.split('\n')
    // Incremental terminal events carry the delimiter so adjacent chunks cannot be glued
    // together. Do not turn that delimiter into a spurious blank workspace row; an actual blank
    // line remains represented by the empty entry before the final delimiter.
    return if (normalized.endsWith('\n')) entries.dropLast(1) else entries
}
