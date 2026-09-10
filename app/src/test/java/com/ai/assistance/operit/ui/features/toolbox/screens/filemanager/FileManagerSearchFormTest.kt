package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import com.ai.assistance.operit.core.tools.FileSearchNameMode
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class FileManagerSearchFormTest {
    @Test fun `blank name permits size only and decimal MiB is exact`() {
        val options = FileManagerSearchForm(sizePreset = FileSearchSizePreset.CUSTOM, minimumMiB = "0.5", maximumMiB = "1").options("")
        assertEquals(524288L, options.minimumBytes); assertEquals(1048576L, options.maximumBytes)
    }
    @Test fun `invalid ranges and overflow are rejected`() {
        listOf("-1" to "2", "3" to "2", "abc" to "", "999999999999999999999" to "").forEach { (a,b) ->
            assertThrows(IllegalArgumentException::class.java) { FileManagerSearchForm(sizePreset = FileSearchSizePreset.CUSTOM, minimumMiB = a, maximumMiB = b).options("note") }
        }
    }
    @Test fun `custom dates include the entire last day and obey local timezone`() {
        val options = FileManagerSearchForm(modifiedDays = -1, modifiedFrom = "2026-09-10", modifiedTo = "2026-09-10").options("", zone = ZoneId.of("Asia/Shanghai"))
        assertEquals(86400000L - 1, options.modifiedBefore!! - options.modifiedAfter!!)
        assertEquals("2026-09-09T16:00:00Z", java.time.Instant.ofEpochMilli(options.modifiedAfter!!).toString())
    }
    @Test fun `invalid dates regex and empty conditions stay in the form`() {
        assertThrows(IllegalArgumentException::class.java) { FileManagerSearchForm().options("") }
        assertThrows(IllegalArgumentException::class.java) { FileManagerSearchForm(modifiedDays = -1, modifiedFrom = "2026-02-30").options("a") }
        assertThrows(IllegalArgumentException::class.java) { FileManagerSearchForm(nameMode = FileSearchNameMode.REGEX).options("[") }
    }
    @Test fun `relative time uses captured now and presets do not overlap`() {
        val after = FileManagerSearchForm(modifiedDays = 7).options("a", now = 900000000L)
        assertEquals(900000000L - 7 * 86400000L, after.modifiedAfter)
        val small = FileManagerSearchForm(sizePreset = FileSearchSizePreset.SMALL).options("")
        val medium = FileManagerSearchForm(sizePreset = FileSearchSizePreset.MEDIUM).options("")
        assertEquals(small.maximumBytes!! + 1, medium.minimumBytes)
    }
}
