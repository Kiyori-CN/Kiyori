package com.ai.assistance.operit.core.tools

import kotlinx.serialization.Serializable

@Serializable
enum class FileSearchNameMode { CONTAINS, GLOB, REGEX }

@Serializable
data class FileSearchOptions(
    val name: String = "", val recursive: Boolean = false,
    val nameMode: FileSearchNameMode = FileSearchNameMode.CONTAINS,
    val caseSensitive: Boolean = false, val content: String = "",
    val minimumBytes: Long? = null, val maximumBytes: Long? = null,
    val modifiedAfter: Long? = null, val modifiedBefore: Long? = null,
    val includeHidden: Boolean = false,
)

@Serializable
data class FileSearchEntry(val path: String, val directory: Boolean, val size: Long, val modified: Long)

@Serializable
data class FileSearchData(
    val path: String, val entries: List<FileSearchEntry>, val scanned: Int,
    val skipped: Int, val limitations: List<String> = emptyList(),
) : ToolResultData() {
    override fun toString() = "${entries.size} matches; $scanned scanned; $skipped skipped"
}
