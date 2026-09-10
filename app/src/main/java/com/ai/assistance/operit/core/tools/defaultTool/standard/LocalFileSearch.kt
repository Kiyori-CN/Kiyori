package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.core.tools.defaultTool.PathValidator
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.*
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

internal class FileSearchLimit(message: String) : RuntimeException(message)

/** Java 正则读取每个字符时检查预算与协程取消；不能用超时协程包住不可取消的 matcher。 */
private class SearchCharacters(
    private val text: String, private val start: Int = 0, private val end: Int = text.length,
    private val check: () -> Unit,
) : CharSequence {
    override val length get() = end - start
    override fun get(index: Int): Char { check(); return text[start + index] }
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = SearchCharacters(text, start + startIndex, start + endIndex, check)
    override fun toString(): String { check(); return text.substring(start, end) }
}

internal fun compileSearchPattern(query: String, mode: FileSearchNameMode, caseSensitive: Boolean): Pattern {
    require(query.length <= 512) { "搜索表达式不能超过 512 个字符" }
    val expression = when (mode) {
        FileSearchNameMode.CONTAINS -> Pattern.quote(query)
        FileSearchNameMode.REGEX -> query
        FileSearchNameMode.GLOB -> query.map { when (it) { '*' -> ".*"; '?' -> "."; else -> Pattern.quote(it.toString()) } }.joinToString("")
    }
    return try { Pattern.compile(expression, if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE) }
    catch (failure: java.util.regex.PatternSyntaxException) { throw IllegalArgumentException("正则表达式无效：${failure.description}") }
}

internal fun validateFileSearch(options: FileSearchOptions) {
    require(options.minimumBytes == null || options.minimumBytes >= 0) { "最小大小不能为负数" }
    require(options.maximumBytes == null || options.maximumBytes >= 0) { "最大大小不能为负数" }
    require(options.minimumBytes == null || options.maximumBytes == null || options.minimumBytes <= options.maximumBytes) { "最小大小不能超过最大大小" }
    require(options.modifiedAfter == null || options.modifiedBefore == null || options.modifiedAfter <= options.modifiedBefore) { "时间范围无效" }
    compileSearchPattern(options.name, options.nameMode, options.caseSensitive)
    if (options.content.isNotEmpty()) compileSearchPattern(options.content,
        if (options.nameMode == FileSearchNameMode.REGEX) FileSearchNameMode.REGEX else FileSearchNameMode.CONTAINS, options.caseSensitive)
}

internal fun searchLocalFiles(root: Path, options: FileSearchOptions, checkActive: () -> Unit = {},
    maxResults: Int = 2000, maxEntries: Int = 100_000, maxContentBytes: Int = 4 * 1024 * 1024,
    maxDurationNanos: Long = 30_000_000_000L,
): FileSearchData {
    validateFileSearch(options)
    require(maxResults > 0 && maxEntries > 0 && maxContentBytes > 0)
    require(Files.isDirectory(root, NOFOLLOW_LINKS)) { "搜索位置不是可读取的普通目录" }
    val namePattern = compileSearchPattern(options.name, options.nameMode, options.caseSensitive)
    val contentPattern = options.content.takeIf { it.isNotEmpty() }?.let { compileSearchPattern(it,
        if (options.nameMode == FileSearchNameMode.REGEX) FileSearchNameMode.REGEX else FileSearchNameMode.CONTAINS, options.caseSensitive) }
    val entries = mutableListOf<FileSearchEntry>()
    val limitations = linkedSetOf<String>()
    var scanned = 0; var skipped = 0
    val started = System.nanoTime()
    fun checkBudget() {
        checkActive()
        if (System.nanoTime() - started >= maxDurationNanos) throw FileSearchLimit("已达到搜索时间上限，请缩小范围")
    }
    fun matches(pattern: Pattern, text: String, entire: Boolean): Boolean {
        var reads = 0
        val began = System.nanoTime()
        val guarded = SearchCharacters(text) {
            if (++reads % 256 == 0) {
                checkBudget()
                if (reads.toLong() > maxOf(4_000_000L, text.length.toLong() * 16) || System.nanoTime() - began > 200_000_000L) throw FileSearchLimit("正则匹配超出预算，请简化表达式")
            }
        }
        return try { pattern.matcher(guarded).let { if (entire) it.matches() else it.find() } }
        catch (failure: StackOverflowError) { throw FileSearchLimit("正则表达式过于复杂，请简化后重试") }
    }
    fun consider(path: Path, attrs: BasicFileAttributes) {
        checkBudget()
        if (++scanned > maxEntries) throw FileSearchLimit("已达到 $maxEntries 项扫描上限")
        if (!attrs.isDirectory && !attrs.isRegularFile) { skipped++; limitations += "已跳过链接或特殊文件"; return }
        if (options.name.isNotEmpty() && !matches(namePattern, path.fileName.toString(), options.nameMode == FileSearchNameMode.GLOB)) return
        val time = attrs.lastModifiedTime().toMillis()
        if (options.modifiedAfter?.let { time < it } == true || options.modifiedBefore?.let { time > it } == true) return
        if (options.minimumBytes != null || options.maximumBytes != null || contentPattern != null) {
            if (!attrs.isRegularFile) return
            if (options.minimumBytes?.let { attrs.size() < it } == true || options.maximumBytes?.let { attrs.size() > it } == true) return
        }
        if (contentPattern != null) {
            if (attrs.size() > maxContentBytes) { skipped++; limitations += "内容搜索跳过超过 ${maxContentBytes / 1024 / 1024} MiB 的文件"; return }
            val text = try {
                val bytes = Files.newInputStream(path, StandardOpenOption.READ, NOFOLLOW_LINKS).use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        checkBudget(); val n = input.read(buffer); if (n < 0) break
                        if (output.size() + n > maxContentBytes) throw IOException("文件增长超过读取上限")
                        output.write(buffer, 0, n)
                    }
                    output.toByteArray()
                }
                if (bytes.any { it == 0.toByte() }) throw IOException("二进制文件")
                val after = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                if (!after.isRegularFile || after.size() != attrs.size() || after.lastModifiedTime() != attrs.lastModifiedTime() || after.fileKey() != attrs.fileKey()) throw IOException("读取期间文件变化")
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            } catch (failure: IOException) { skipped++; limitations += "部分文件不可读、发生变化或不是 UTF-8 文本"; return }
            if (!matches(contentPattern, text, false)) return
        }
        if (entries.size >= maxResults) throw FileSearchLimit("结果超过 $maxResults 项，请缩小范围")
        entries += FileSearchEntry(path.toString(), attrs.isDirectory, attrs.size(), time)
    }
    try {
        Files.walkFileTree(root, emptySet(), if (options.recursive) 129 else 1, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                checkBudget()
                if (dir == root) return FileVisitResult.CONTINUE
                if (!options.includeHidden && dir.fileName.toString().startsWith('.')) return FileVisitResult.SKIP_SUBTREE
                consider(dir, attrs); return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!options.includeHidden && file.fileName.toString().startsWith('.')) return FileVisitResult.CONTINUE
                consider(file, attrs)
                if (attrs.isDirectory && options.recursive) limitations += "已达到 128 层目录深度上限"
                return FileVisitResult.CONTINUE
            }
            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                if (file == root) throw exc
                checkBudget(); skipped++; limitations += "部分位置没有读取权限或已失效"; return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                checkBudget()
                if (exc != null) { skipped++; limitations += "部分目录枚举未完成，请检查权限或重新搜索" }
                return FileVisitResult.CONTINUE
            }
        })
    } catch (limit: FileSearchLimit) { limitations += limit.message.orEmpty() }
    return FileSearchData(root.toString(), entries, scanned.coerceAtMost(maxEntries), skipped, limitations.toList())
}

internal suspend fun executeFileManagerSearch(tool: AITool, backend: String = "android"): ToolResult {
    fun parameter(name: String) = tool.parameters.find { it.name == name }?.value
    val path = parameter("path").orEmpty()
    val environment = parameter("environment") ?: backend
    fun failedResult(message: String) = ToolResult(tool.name, false, FileSearchData(path, emptyList(), 0, 0), message)
    if (backend != "android" || environment != "android") return failedResult("此位置暂不支持高级搜索，请选择手机存储")
    PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
    return try {
        require(parameter("search_mode") == "manager") { "不支持的搜索模式" }
        val options = Json.decodeFromString<FileSearchOptions>(requireNotNull(parameter("search_options")))
        val coroutine = currentCoroutineContext()
        ToolResult(tool.name, true, searchLocalFiles(Paths.get(path), options, { coroutine.ensureActive() }))
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) { failedResult(failure.message ?: "搜索失败，请检查位置和权限") }
}
