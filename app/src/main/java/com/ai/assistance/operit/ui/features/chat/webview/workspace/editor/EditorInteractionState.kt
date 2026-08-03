package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

data class EditorInteractionState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val cursorLine: Int = 1,
    val cursorColumn: Int = 1,
    val selectionCharacters: Int = 0,
)
