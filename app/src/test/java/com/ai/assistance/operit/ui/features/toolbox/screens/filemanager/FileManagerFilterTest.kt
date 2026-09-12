package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.LocalDate

class FileManagerFilterTest {
    @Test fun `confirmed conditions intersect and extensions union case insensitively`() {
        val filter = FileManagerFilterDraft(name = "Report", formats = "PDF, jpg", minimum = "1", maximum = "2", unit = FileManagerSizeUnit.KiB).compile()
        assertTrue(filter.matches(FileItem("report.PDF", false, 1024)))
        assertTrue(filter.matches(FileItem("REPORT.jpg", false, 2048)))
        assertFalse(filter.matches(FileItem("report.png", false, 1024)))
        assertFalse(filter.matches(FileItem("other.pdf", false, 1024)))
        assertFalse(filter.matches(FileItem("report.pdf", true, 1024)))
        assertFalse(filter.matches(FileItem("report.pdf", false, 2049)))
    }
    @Test fun `extensionless dotfiles compound extensions and literal names are supported`() {
        assertTrue(FileManagerFilterDraft(noExtension = true).compile().matches(FileItem(".config", false)))
        assertTrue(FileManagerFilterDraft(formats = "*.tar.gz").compile().matches(FileItem("backup.TAR.GZ", false)))
        assertFalse(FileManagerFilterDraft(formats = "gz").compile().matches(FileItem(".gz", false)))
        assertTrue(FileManagerFilterDraft(name = " report ").compile().matches(FileItem(" report .pdf", false)))
    }
    @Test fun `custom dates include end day and reject unavailable timestamp`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val filter = FileManagerFilterDraft(days = -1, from = "2026-09-12", to = "2026-09-12").compile(zone = zone)
        val midnight = LocalDate.parse("2026-09-12").atStartOfDay(zone).toInstant().toEpochMilli()
        assertTrue(filter.matches(FileItem("a", false, lastModified = midnight)))
        assertTrue(filter.matches(FileItem("a", false, lastModified = midnight + 86400000 - 1)))
        assertFalse(filter.matches(FileItem("a", false, lastModified = midnight + 86400000)))
        assertFalse(filter.matches(FileItem("a", false, lastModified = 0)))
    }
    @Test fun `invalid bounds and incompatible directory conditions never compile`() {
        listOf(
            FileManagerFilterDraft(minimum = "-1"), FileManagerFilterDraft(minimum = "2", maximum = "1"),
            FileManagerFilterDraft(maximum = "9999999999999999999999999"), FileManagerFilterDraft(formats = "*/pdf"),
            FileManagerFilterDraft(days = -1, from = "invalid"),
            FileManagerFilterDraft(days = -1, from = "2026-09-13", to = "2026-09-12"),
            FileManagerFilterDraft(type = FileManagerItemType.DIRECTORY, formats = "pdf"),
        ).forEach { draft -> assertThrows(IllegalArgumentException::class.java) { draft.compile() } }
    }
    @Test fun `filter retains navigation entry and format sorting retains directory priority`() {
        val files = listOf(FileItem("b.pdf", false), FileItem("folder", true), FileItem("a.jpg", false))
        assertEquals(listOf("..", "b.pdf"), fileManagerVisibleEntries(files, "", true, true,
            FileManagerFilterDraft(formats = "pdf").compile()).map { it.name })
        assertEquals(listOf("folder", "a.jpg", "b.pdf"), files.sortedWith(fileManagerComparator(FileManagerSortMode.FORMAT, false)).map { it.name })
    }
}
