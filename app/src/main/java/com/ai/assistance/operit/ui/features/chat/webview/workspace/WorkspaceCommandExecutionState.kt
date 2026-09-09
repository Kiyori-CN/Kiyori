package com.ai.assistance.operit.ui.features.chat.webview.workspace

data class WorkspaceCommandExecutionState(
    val workspacePath: String,
    val commandLabel: String,
    val commandText: String,
    val sessionId: String,
    val commandId: String = "",
    val usesDedicatedSession: Boolean,
    val outputEntries: List<String> = emptyList(),
    val isRunning: Boolean = true,
    val isVisible: Boolean = true,
    val isCancelling: Boolean = false,
    val exitCode: Int? = null,
    val omittedOutputEntries: Int = 0,
    val workspaceEnvironment: String? = null,
) {
    fun appendOutput(entries: List<String>): WorkspaceCommandExecutionState {
        if (entries.isEmpty()) return this
        val combined = outputEntries + entries
        val removed = (combined.size - MAX_OUTPUT_ENTRIES).coerceAtLeast(0)
        return copy(outputEntries = combined.takeLast(MAX_OUTPUT_ENTRIES), omittedOutputEntries = omittedOutputEntries + removed)
    }

    companion object { const val MAX_OUTPUT_ENTRIES = 2_000 }
}

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
