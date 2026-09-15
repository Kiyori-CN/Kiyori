package com.ai.assistance.operit.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日记的正文结构约定。日记与记忆、知识共用同一张 Memory 表：
 * 追加一条记录只是在 `content` 末尾拼接一段带时间戳的小节，不引入第二套存储。
 *
 * 选择正文内联而不是新实体的原因：
 * 1. 备份、导出、嵌入和检索沿用同一条正文路径，旧库和旧备份不需要迁移；
 * 2. 一篇日记就是一个可检索单元，模型读到的是完整过程，而不是互相看不见的碎片；
 * 3. 结构完全由这里解析，UI 与工具不各自约定一套格式。
 *
 * 小节标题使用与界面语言无关的稳定键（`progress`、`decision`…），
 * 渲染时再翻译；把本地化文案写进正文会让切换语言后的历史记录含义漂移。
 */
object MemoryDiaryPolicy {

    /** 未声明阶段时的中性阶段；旧内容和自由书写都落在这里。 */
    const val NOTE = "note"
    const val PLAN = "plan"
    const val PROGRESS = "progress"
    const val DECISION = "decision"
    const val EVIDENCE = "evidence"
    const val RISK = "risk"
    const val VALIDATION = "validation"
    /** 收尾小节；它是否位于末尾决定这篇日记是否已完结。 */
    const val CLOSED = "closed"

    /** 供选择器与工具校验使用的完整阶段表，顺序即展示顺序。 */
    val phases = listOf(NOTE, PLAN, PROGRESS, DECISION, EVIDENCE, RISK, VALIDATION, CLOSED)

    /** 追加时可选的阶段：收尾由“结束日记”单独触发，避免在阶段选择器里混入状态操作。 */
    val appendablePhases = phases - CLOSED

    const val STATUS_ACTIVE = "active"
    const val STATUS_CLOSED = "closed"

    /** 单次追加的字符上限；超出应拆成多条记录，而不是把一整份日志塞进一节。 */
    const val MAX_ENTRY_CHARS = 20_000

    private const val STAMP_PATTERN = "yyyy-MM-dd HH:mm"

    /**
     * 小节标题：`## 2026-09-15 14:32 · progress`。
     * 阶段段落允许任意非空文本，未知值按 [NOTE] 处理但保留原文展示，
     * 用户手写的标题不会因为不认识就被吞进正文。
     */
    private val HEADER = Regex("""^##[ \t]+(\d{4}-\d{2}-\d{2})[ \t]+(\d{2}:\d{2})(?:[ \t]*·[ \t]*(\S[^\n]*?))?[ \t]*$""")

    /**
     * 一条日记记录。[stamp] 是写入时的本地时间文本，不做时区换算：
     * 它描述“当时在哪个时刻写下”，重新解释会让历史记录随设备设置变化。
     */
    data class Entry(
        val index: Int,
        val date: String,
        val time: String,
        val phase: String,
        val rawPhase: String?,
        val body: String,
    ) {
        val stamp: String get() = "$date $time"
    }

    /** [preface] 是第一条记录之前的开篇文字，旧的纯文本日记整段落在这里。 */
    data class Diary(val preface: String, val entries: List<Entry>) {
        val status: String
            get() = if (entries.lastOrNull()?.phase == CLOSED) STATUS_CLOSED else STATUS_ACTIVE
        val isClosed: Boolean get() = status == STATUS_CLOSED
        val lastEntry: Entry? get() = entries.lastOrNull()
    }

    fun normalizePhase(raw: String?): String {
        val value = raw?.trim()?.lowercase(Locale.US) ?: return NOTE
        return if (value in phases) value else NOTE
    }

    fun formatStamp(at: Date): String = SimpleDateFormat(STAMP_PATTERN, Locale.US).format(at)

    fun header(at: Date, phase: String): String = "## ${formatStamp(at)} · ${normalizePhase(phase)}"

    fun parse(content: String): Diary {
        val lines = content.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val preface = StringBuilder()
        val entries = mutableListOf<Entry>()
        var date: String? = null
        var time = ""
        var phase = NOTE
        var rawPhase: String? = null
        val body = StringBuilder()

        fun flush() {
            val started = date ?: return
            entries += Entry(entries.size, started, time, phase, rawPhase, body.toString().trim())
            body.setLength(0)
        }

        lines.forEach { line ->
            val match = HEADER.matchEntire(line)
            if (match == null) {
                if (date == null) preface.appendLine(line) else body.appendLine(line)
                return@forEach
            }
            flush()
            date = match.groupValues[1]
            time = match.groupValues[2]
            rawPhase = match.groupValues[3].takeIf { it.isNotBlank() }
            phase = normalizePhase(rawPhase)
        }
        flush()
        return Diary(preface.toString().trim(), entries)
    }

    /**
     * 在正文末尾追加一条记录。收尾小节允许正文为空（只标记完结），
     * 其余阶段必须有内容：空记录既不能被检索，也说不清当时发生了什么。
     */
    fun append(content: String, body: String, phase: String, at: Date): String {
        val normalizedPhase = normalizePhase(phase)
        val text = body.replace("\r\n", "\n").replace('\r', '\n').trim()
        require(text.isNotEmpty() || normalizedPhase == CLOSED) { "请填写这次要记录的内容" }
        require(text.length <= MAX_ENTRY_CHARS) { "单条记录不能超过 $MAX_ENTRY_CHARS 字符，请拆分后追加" }
        val head = content.trimEnd()
        val section = header(at, normalizedPhase) + if (text.isEmpty()) "" else "\n$text"
        return if (head.isEmpty()) section else "$head\n\n$section"
    }

    /** 新建日记：开篇内容本身就是第一条记录，之后的续写都追加在它后面。 */
    fun seed(body: String, phase: String, at: Date): String = append("", body, phase, at)

    /**
     * 完结状态只由最后一个小节标题决定，因此从末尾反向找第一个标题即可，
     * 不必为列表筛选把每篇日记都解析成对象。结果与 [parse] 的 `status` 一致。
     */
    fun status(content: String): String {
        val text = content.replace("\r\n", "\n").replace('\r', '\n')
        var end = text.length
        while (end > 0) {
            val start = text.lastIndexOf('\n', end - 1) + 1
            HEADER.matchEntire(text.substring(start, end))?.let { match ->
                val phase = normalizePhase(match.groupValues[3].takeIf { it.isNotBlank() })
                return if (phase == CLOSED) STATUS_CLOSED else STATUS_ACTIVE
            }
            if (start == 0) break
            end = start - 1
        }
        return STATUS_ACTIVE
    }

    /** 卡片摘要：优先展示最新一条记录，空的收尾节退回到上一条有内容的记录。 */
    fun summary(content: String): String {
        val diary = parse(content)
        val latest = diary.entries.lastOrNull { it.body.isNotBlank() }?.body
        return latest?.takeIf { it.isNotBlank() } ?: diary.preface
    }

    fun entryCount(content: String): Int = parse(content).entries.size

    /** 分组用的日期键；没有任何记录时退回到条目自身的创建日期。 */
    fun firstDay(content: String, fallback: Date): String =
        parse(content).entries.firstOrNull()?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(fallback)
}
