package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/** 日记的结构只有正文一个来源，解析和追加必须互为逆运算，旧的纯文本也不能被吞掉。 */
class MemoryDiaryPolicyTest {

    private fun at(stamp: String) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(stamp)!!

    @Test fun plainTextStaysReadableAsPreface() {
        val diary = MemoryDiaryPolicy.parse("旧版本写下的一段没有小节的日记")
        assertEquals("旧版本写下的一段没有小节的日记", diary.preface)
        assertTrue(diary.entries.isEmpty())
        assertEquals(MemoryDiaryPolicy.STATUS_ACTIVE, diary.status)
    }

    @Test fun appendKeepsEveryEarlierEntry() {
        var content = MemoryDiaryPolicy.seed("确认状态所有权", MemoryDiaryPolicy.PLAN, at("2026-09-15 09:00"))
        content = MemoryDiaryPolicy.append(content, "定位到 DrawerHost", MemoryDiaryPolicy.PROGRESS, at("2026-09-15 11:30"))
        content = MemoryDiaryPolicy.append(content, "复用同一个状态所有者", MemoryDiaryPolicy.DECISION, at("2026-09-16 08:05"))

        val entries = MemoryDiaryPolicy.parse(content).entries
        assertEquals(3, entries.size)
        assertEquals(listOf(MemoryDiaryPolicy.PLAN, MemoryDiaryPolicy.PROGRESS, MemoryDiaryPolicy.DECISION), entries.map { it.phase })
        assertEquals("确认状态所有权", entries[0].body)
        assertEquals("2026-09-15 09:00", entries[0].stamp)
        assertEquals("2026-09-16 08:05", entries[2].stamp)
        assertEquals("2026-09-15", MemoryDiaryPolicy.firstDay(content, at("2020-01-01 00:00")))
    }

    @Test fun closingIsDecidedByTheLastEntry() {
        val opened = MemoryDiaryPolicy.seed("开始", MemoryDiaryPolicy.PLAN, at("2026-09-15 09:00"))
        val closed = MemoryDiaryPolicy.append(opened, "", MemoryDiaryPolicy.CLOSED, at("2026-09-15 18:00"))
        assertEquals(MemoryDiaryPolicy.STATUS_CLOSED, MemoryDiaryPolicy.status(closed))

        val reopened = MemoryDiaryPolicy.append(closed, "又发现一个问题", MemoryDiaryPolicy.PROGRESS, at("2026-09-16 09:00"))
        assertEquals(MemoryDiaryPolicy.STATUS_ACTIVE, MemoryDiaryPolicy.status(reopened))
        assertEquals(3, MemoryDiaryPolicy.entryCount(reopened))
    }

    @Test fun emptyBodyIsOnlyAllowedWhenClosing() {
        val opened = MemoryDiaryPolicy.seed("开始", MemoryDiaryPolicy.PLAN, at("2026-09-15 09:00"))
        assertTrue(runCatching { MemoryDiaryPolicy.append(opened, "   ", MemoryDiaryPolicy.PROGRESS, at("2026-09-15 10:00")) }.isFailure)
        assertTrue(runCatching { MemoryDiaryPolicy.append(opened, "", MemoryDiaryPolicy.CLOSED, at("2026-09-15 10:00")) }.isSuccess)
    }

    @Test fun unknownPhaseTextIsKeptInsteadOfSwallowed() {
        val content = "## 2026-09-15 09:00 · 我自己的阶段\n正文"
        val entry = MemoryDiaryPolicy.parse(content).entries.single()
        assertEquals(MemoryDiaryPolicy.NOTE, entry.phase)
        assertEquals("我自己的阶段", entry.rawPhase)
        assertEquals("正文", entry.body)
    }

    @Test fun summaryFollowsTheLatestEntryWithContent() {
        var content = MemoryDiaryPolicy.seed("开篇", MemoryDiaryPolicy.PLAN, at("2026-09-15 09:00"))
        content = MemoryDiaryPolicy.append(content, "第二条", MemoryDiaryPolicy.PROGRESS, at("2026-09-15 10:00"))
        content = MemoryDiaryPolicy.append(content, "", MemoryDiaryPolicy.CLOSED, at("2026-09-15 18:00"))
        assertEquals("第二条", MemoryDiaryPolicy.summary(content))
    }

    @Test fun oversizedEntryIsRejectedInsteadOfTruncated() {
        val opened = MemoryDiaryPolicy.seed("开始", MemoryDiaryPolicy.PLAN, at("2026-09-15 09:00"))
        val huge = "字".repeat(MemoryDiaryPolicy.MAX_ENTRY_CHARS + 1)
        assertTrue(runCatching { MemoryDiaryPolicy.append(opened, huge, MemoryDiaryPolicy.NOTE, at("2026-09-15 10:00")) }.isFailure)
    }

    /** 列表筛选走的是尾部扫描而不是完整解析；两条路径给出不同答案就会出现筛不出自己的日记。 */
    @Test fun fastStatusAgreesWithFullParse() {
        val opened = MemoryDiaryPolicy.seed("开始", MemoryDiaryPolicy.PLAN, at("2026-09-15 09:00"))
        val closed = MemoryDiaryPolicy.append(opened, "收尾", MemoryDiaryPolicy.CLOSED, at("2026-09-15 18:00"))
        val reopened = MemoryDiaryPolicy.append(closed, "又发现一个问题", MemoryDiaryPolicy.PROGRESS, at("2026-09-16 09:00"))
        val samples = listOf(
            "",
            "没有小节的旧日记",
            "多行\n纯文本\n没有任何标题",
            opened,
            closed,
            // 收尾节之后只剩空行时仍然是已完结。
            closed + "\n\n",
            reopened,
        )
        samples.forEach { content ->
            assertEquals(content, MemoryDiaryPolicy.parse(content).status, MemoryDiaryPolicy.status(content))
        }
    }

    @Test fun diaryIsAWritableKindAlongsideMemoryAndKnowledge() {
        assertEquals(listOf("memory", "diary", "knowledge"), MemoryLibraryPolicy.kinds)
        assertTrue(runCatching { MemoryLibraryPolicy.requireWritableKind(MemoryLibraryPolicy.DIARY) }.isSuccess)
        assertFalse(runCatching { MemoryLibraryPolicy.requireWritableKind("journal") }.isSuccess)
    }
}
