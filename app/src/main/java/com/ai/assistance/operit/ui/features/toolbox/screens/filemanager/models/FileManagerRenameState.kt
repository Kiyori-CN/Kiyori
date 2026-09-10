package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models

data class FileManagerRenameRequest(val name: String, val location: FileManagerLocation)

data class FileManagerRenameState(
    val request: FileManagerRenameRequest? = null,
    val newName: String = "",
    val running: Boolean = false,
    val error: String? = null,
    val unknown: Boolean = false,
)
