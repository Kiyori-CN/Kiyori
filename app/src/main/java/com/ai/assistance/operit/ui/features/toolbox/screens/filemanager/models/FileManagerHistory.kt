package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models

import kotlinx.serialization.Serializable

@Serializable
data class FileManagerSearchRecord(
    val id: String, val time: Long, val query: String, val form: FileManagerSearchForm,
    val location: FileManagerLocation, val results: List<FileItem> = emptyList(),
    val total: Int = 0, val status: String = "搜索中", val summary: String = "",
    val limitations: List<String> = emptyList(), val error: String? = null,
)

@Serializable
data class FileManagerTaskRecord(
    val id: String, val time: Long, val title: String, val source: FileManagerLocation,
    val destination: FileManagerLocation? = null, val total: Int,
    val status: String = "进行中", val results: List<FileManagerTransferItemResult> = emptyList(),
    val finishedAt: Long? = null,
    val resultCount: Int = results.size,
)

@Serializable
data class FileManagerHistory(
    val version: Int = 1,
    val searches: List<FileManagerSearchRecord> = emptyList(),
    val tasks: List<FileManagerTaskRecord> = emptyList(),
)

internal fun fileManagerTaskStatus(results: List<FileManagerTransferItemResult>): String = when {
    results.any { it.outcome == FileManagerTransferOutcome.UNKNOWN } -> "结果待确认"
    results.isEmpty() -> "已取消"
    results.all { it.outcome == FileManagerTransferOutcome.COMPLETED } -> "完成"
    results.any { it.outcome == FileManagerTransferOutcome.COMPLETED || it.outcome == FileManagerTransferOutcome.COPIED_SOURCE_RETAINED } -> "部分完成"
    results.any { it.outcome == FileManagerTransferOutcome.FAILED } -> "失败"
    else -> "已取消"
}
