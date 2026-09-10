package com.ai.assistance.operit.core.tools

import kotlinx.serialization.Serializable

/** 文件管理操作预览；fingerprint 是清单/元数据指纹，sha256 才是文件内容哈希。 */
@Serializable
data class FileInspectionData(
    val path: String,
    val directory: Boolean,
    val bytes: Long,
    val files: Int,
    val directories: Int,
    val modified: Long,
    val readable: Boolean,
    val writable: Boolean,
    val fingerprint: String,
    val sha256: String? = null,
) : ToolResultData() {
    override fun toString(): String = "$path · $bytes bytes · $files files · $directories directories" + (sha256?.let { " · SHA-256: $it" } ?: "")
}
