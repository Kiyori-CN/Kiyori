package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class FileManagerItemType(val label: String) { ALL("全部"), FILE("仅文件"), DIRECTORY("仅文件夹") }
enum class FileManagerSizeUnit(val bytes: Long) { B(1), KiB(1024), MiB(1048576), GiB(1073741824) }

/** 时间边界在确认时冻结，后台刷新不能改变筛选身份。 */
data class FileManagerFilter(
    val name: String = "", val formats: Set<String> = emptySet(), val noExtension: Boolean = false,
    val minimum: Long? = null, val maximum: Long? = null,
    val after: Long? = null, val before: Long? = null,
    val type: FileManagerItemType = FileManagerItemType.ALL,
) {
    val active get() = this != FileManagerFilter()
    fun matches(file: FileItem): Boolean {
        if (!file.displayName.contains(name, ignoreCase = true)) return false
        if (type == FileManagerItemType.FILE && file.isDirectory || type == FileManagerItemType.DIRECTORY && !file.isDirectory) return false
        if (formats.isNotEmpty() || noExtension) {
            if (file.isDirectory) return false
            val extension = fileManagerExtension(file.displayName)
            if (!(noExtension && extension.isEmpty()) && formats.none { format ->
                extension == format || (file.displayName.length > format.length + 1 && file.displayName.lowercase(Locale.ROOT).endsWith(".$format"))
            }) return false
        }
        if (minimum != null || maximum != null) {
            if (file.isDirectory || file.size < 0) return false
            if (minimum != null && file.size < minimum || maximum != null && file.size > maximum) return false
        }
        if (after != null || before != null) {
            if (file.lastModified <= 0) return false
            if (after != null && file.lastModified < after || before != null && file.lastModified > before) return false
        }
        return true
    }
}

internal fun fileManagerExtension(name: String): String =
    name.lastIndexOf('.').takeIf { it > 0 && it < name.lastIndex }
        ?.let { name.substring(it + 1).lowercase(Locale.ROOT) }.orEmpty()

data class FileManagerFilterDraft(
    val name: String = "", val formats: String = "", val noExtension: Boolean = false,
    val minimum: String = "", val maximum: String = "", val unit: FileManagerSizeUnit = FileManagerSizeUnit.MiB,
    val days: Int = 0, val from: String = "", val to: String = "",
    val type: FileManagerItemType = FileManagerItemType.ALL,
) {
    fun compile(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): FileManagerFilter {
        fun size(value: String): Long? {
            if (value.isBlank()) return null
            val result = try { BigDecimal(value.trim()).multiply(BigDecimal(unit.bytes)).longValueExact() }
                catch (error: NumberFormatException) { throw IllegalArgumentException("请输入有效大小") }
                catch (error: ArithmeticException) { throw IllegalArgumentException("大小超出范围或不足一个字节") }
            require(result >= 0) { "大小不能为负数" }; return result
        }
        fun date(value: String, end: Boolean): Long? {
            if (value.isBlank()) return null
            return try { LocalDate.parse(value.trim()).let { if (end) it.plusDays(1) else it }
                .atStartOfDay(zone).toInstant().toEpochMilli() - if (end) 1 else 0 }
            catch (error: java.time.DateTimeException) { throw IllegalArgumentException("日期格式应为 yyyy-MM-dd") }
        }
        val extensions = formats.split(',', '，', ';', '；', ' ').filter { it.isNotBlank() }
            .map { it.trim().removePrefix("*.").removePrefix(".").lowercase(Locale.ROOT) }.toSet()
        require(extensions.all { it.isNotEmpty() && it.none { c -> c in "/*?\\:" || c.isWhitespace() } }) { "格式请输入扩展名，例如 pdf、tar.gz" }
        val min = size(minimum); val max = size(maximum)
        require(min == null || max == null || min <= max) { "最小大小不能超过最大大小" }
        val after = if (days > 0) now - days * 86400000L else if (days < 0) date(from, false) else null
        val before = if (days > 0) now else if (days < 0) date(to, true) else null
        require(after == null || before == null || after <= before) { "起始日期不能晚于结束日期" }
        require(type != FileManagerItemType.DIRECTORY || (extensions.isEmpty() && !noExtension && min == null && max == null)) {
            "仅文件夹不能同时使用文件格式或文件大小条件"
        }
        return FileManagerFilter(name, extensions, noExtension, min, max, after, before, type)
    }
}
