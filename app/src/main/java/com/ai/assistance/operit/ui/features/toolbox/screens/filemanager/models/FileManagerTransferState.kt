package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models

enum class FileManagerTransferOutcome { COMPLETED, FAILED, COPIED_SOURCE_RETAINED, SKIPPED, NOT_STARTED, UNKNOWN }

@kotlinx.serialization.Serializable
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
    val clipboardVersion: Long? = null,
)

fun fileManagerIsLocal(environment: String?): Boolean = environment.isNullOrBlank() || environment == "android"

fun fileManagerLocationLabel(location: FileManagerLocation): String =
    "${when {
        fileManagerIsLocal(location.environment) -> "手机存储"
        location.environment == "recycle" -> "回收站"
        location.environment == "linux" -> "Linux"
        location.environment?.startsWith("network:") == true -> "网络存储"
        else -> "授权存储"
    }} · ${location.path}"

/** UI 与提交入口共用能力判断，不能把浏览能力误当作安全写入能力。 */
fun fileManagerTransferError(request: FileManagerCopyRequest): String? {
    if (!fileManagerIsLocal(request.source.environment) || !fileManagerIsLocal(request.destination.environment))
        return "此操作目前仅支持手机存储；请选择手机中的目标目录。"
    if (!request.source.path.startsWith('/') || !request.destination.path.startsWith('/') ||
        '\u0000' in request.source.path || '\u0000' in request.destination.path) return "来源和目标必须是完整目录路径。"
    // 文件工具路径属于 Android，不能用运行测试的 Windows Path 去解释绝对路径或反斜杠。
    fun normalize(path: String): String {
        val segments = mutableListOf<String>()
        path.split('/').forEach { part -> when (part) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            else -> segments.add(part)
        } }
        return "/" + segments.joinToString("/")
    }
    val source = normalize(request.source.path)
    val destination = normalize(request.destination.path)
    if (request.moveRequested && source == destination) return "项目已在此目录，无需移动。请选择其他目录。"
    if (request.files.any { file ->
        val child = normalize(fileManagerJoinPath(source, file.name))
        file.isDirectory && (destination == child || destination.startsWith("$child/"))
    })
        return "不能把文件夹放入自身或其子目录。请选择其他目录。"
    return null
}

data class FileManagerCopyConflict(val sourceName: String, val destinationName: String, val directory: Boolean)

/** 保留扩展名、点文件语义；数字是建议，提交时仍必须原子检查冲突。 */
fun fileManagerCopyName(name: String, directory: Boolean, number: Int = 1): String {
    val dot = name.lastIndexOf('.').takeIf { !directory && it > 0 } ?: name.length
    return name.substring(0, dot) + " (副本${if (number == 1) "" else " $number"})" + name.substring(dot)
}
