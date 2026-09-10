package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models

enum class FileManagerTransferOutcome { COMPLETED, FAILED, COPIED_SOURCE_RETAINED, SKIPPED, NOT_STARTED, UNKNOWN }

data class FileManagerTransferItemResult(
    val name: String,
    val outcome: FileManagerTransferOutcome,
    val message: String? = null,
    val destination: String? = null,
    val stagingPath: String? = null,
)

/** 本次会话的传输投影；目录读取状态不兼任写任务进度。 */
data class FileManagerTransferState(
    val total: Int = 0,
    val running: Boolean = false,
    val results: List<FileManagerTransferItemResult> = emptyList(),
    val source: FileManagerLocation? = null,
    val destination: FileManagerLocation? = null,
    val currentName: String? = null,
    val stopRequested: Boolean = false,
    val move: Boolean = false,
)

data class FileManagerCopyRequest(
    val files: List<FileItem>,
    val source: FileManagerLocation,
    val destination: FileManagerLocation,
    val moveRequested: Boolean = false,
)

data class FileManagerCopyConflict(val sourceName: String, val destinationName: String, val directory: Boolean)

/** 保留扩展名、点文件语义；数字是建议，提交时仍必须原子检查冲突。 */
fun fileManagerCopyName(name: String, directory: Boolean, number: Int = 1): String {
    val dot = name.lastIndexOf('.').takeIf { !directory && it > 0 } ?: name.length
    return name.substring(0, dot) + " (副本${if (number == 1) "" else " $number"})" + name.substring(dot)
}
