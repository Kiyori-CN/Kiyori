package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models

import com.ai.assistance.operit.core.tools.FileSearchNameMode
import com.ai.assistance.operit.core.tools.FileSearchOptions
import com.ai.assistance.operit.core.tools.defaultTool.standard.validateFileSearch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

enum class FileSearchSizePreset(val label: String) { ANY("任意大小"), SMALL("小于 1 MiB"), MEDIUM("1–100 MiB"), LARGE("至少 100 MiB"), CUSTOM("自定义范围") }

@kotlinx.serialization.Serializable
data class FileManagerSearchForm(
    val recursive: Boolean = false, val caseSensitive: Boolean = false,
    val nameMode: FileSearchNameMode = FileSearchNameMode.CONTAINS, val content: String = "",
    val sizePreset: FileSearchSizePreset = FileSearchSizePreset.ANY,
    val minimumMiB: String = "", val maximumMiB: String = "",
    val modifiedDays: Int = 0, val modifiedFrom: String = "", val modifiedTo: String = "",
    val includeHidden: Boolean = false,
) {
    val hasAdvancedFilters get() = content.isNotEmpty() || sizePreset != FileSearchSizePreset.ANY || modifiedDays != 0 || caseSensitive || nameMode != FileSearchNameMode.CONTAINS || includeHidden
    fun options(name: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): FileSearchOptions {
        fun bytes(value: String): Long? {
            if (value.isBlank()) return null
            val size = try { BigDecimal(value.trim()).multiply(BigDecimal(1048576)).longValueExact() }
                catch (error: ArithmeticException) { throw IllegalArgumentException("大小超出范围或精度不足") }
                catch (error: NumberFormatException) { throw IllegalArgumentException("请输入有效的 MiB 数值") }
            require(size >= 0) { "大小不能为负数" }; return size
        }
        val (minimum, maximum) = when (sizePreset) {
            FileSearchSizePreset.ANY -> null to null
            FileSearchSizePreset.SMALL -> null to 1048575L
            FileSearchSizePreset.MEDIUM -> 1048576L to 104857599L
            FileSearchSizePreset.LARGE -> 104857600L to null
            FileSearchSizePreset.CUSTOM -> bytes(minimumMiB) to bytes(maximumMiB)
        }
        fun day(value: String, end: Boolean): Long? {
            if (value.isBlank()) return null
            return try { LocalDate.parse(value.trim()).let { if (end) it.plusDays(1) else it }.atStartOfDay(zone).toInstant().toEpochMilli() - if (end) 1 else 0 }
            catch (error: java.time.DateTimeException) { throw IllegalArgumentException("日期格式应为 yyyy-MM-dd") }
        }
        val after = if (modifiedDays < 0) day(modifiedFrom, false) else if (modifiedDays > 0) now - modifiedDays * 86400000L else null
        val before = if (modifiedDays < 0) day(modifiedTo, true) else if (modifiedDays > 0) now else null
        require(name.isNotBlank() || content.isNotEmpty() || minimum != null || maximum != null || after != null || before != null) { "输入名称、内容或设置大小／时间条件" }
        return FileSearchOptions(name, recursive, nameMode, caseSensitive, content, minimum, maximum, after, before, includeHidden).also(::validateFileSearch)
    }
}
