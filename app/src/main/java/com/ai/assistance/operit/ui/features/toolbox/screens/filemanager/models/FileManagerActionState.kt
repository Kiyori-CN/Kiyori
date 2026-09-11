package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models

import com.ai.assistance.operit.core.tools.FileInspectionData

enum class FileManagerActionKind { DELETE, RESTORE, PURGE, RENAME, ZIP, EXTRACT, PROPERTIES, TOOLS }
data class FileManagerActionState(
    val id: Long,
    val kind: FileManagerActionKind,
    val file: FileItem,
    val location: FileManagerLocation,
    val loading: Boolean = true,
    val running: Boolean = false,
    val inspection: FileInspectionData? = null,
    val outputName: String = "",
    val error: String? = null,
    val completed: Boolean = false,
    val unknown: Boolean = false,
    val stagingPath: String? = null,
    val shareAfter: Boolean = false,
    val files: List<FileItem> = listOf(file),
    val inspections: Map<String, FileInspectionData> = emptyMap(),
    val results: List<FileManagerTransferItemResult> = emptyList(),
)
data class FileManagerShareRequest(val path: String, val name: String)

/** 目标选择的快照与窗格布局无关；只有界面决定是否展示另一位置快捷入口。 */
data class FileManagerTransferDraft(
    val files: List<FileItem>, val source: FileManagerLocation, val other: FileManagerLocation,
    val move: Boolean,
)
