package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import org.junit.Assert.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils.formatFileSize
import org.junit.Test

class FileManagerBrowserPolicyTest {
    @Test fun `natural names handle numeric runs leading zeroes and arbitrarily large integers`() {
        val names = listOf("file10", "file002", "file2", "file1", "file999999999999999999999999999999")
        assertEquals(listOf("file1", "file2", "file002", "file10", "file999999999999999999999999999999"),
            names.sortedWith(::compareFileManagerNames))
    }

    @Test fun `comparator is antisymmetric and transitive for mixed names`() {
        val names = listOf("a", "A", "a0", "a00", "a2", "a10", "a02", "a02b", "a2z", "目录10", "目录2", "📄")
        names.forEach { a -> names.forEach { b ->
            assertEquals(Integer.signum(compareFileManagerNames(a, b)), -Integer.signum(compareFileManagerNames(b, a)))
            names.forEach { c -> if (compareFileManagerNames(a, b) <= 0 && compareFileManagerNames(b, c) <= 0) {
                assertTrue(compareFileManagerNames(a, c) <= 0)
            } }
        } }
    }

    @Test fun `directories remain first in either direction and numeric fields never overflow`() {
        val entries = listOf(FileItem("file10", false, Long.MAX_VALUE), FileItem("file2", false, 2), FileItem("folder", true))
        assertEquals(listOf("folder", "file2", "file10"), entries.sortedWith(fileManagerComparator(FileManagerSortMode.SIZE, false)).map { it.name })
        assertEquals(listOf("folder", "file10", "file2"), entries.sortedWith(fileManagerComparator(FileManagerSortMode.SIZE, true)).map { it.name })
    }

    @Test fun `filter excludes backend dot entries and preserves legitimate whitespace`() {
        val entries = listOf(".", "..", ".hidden", "Report.TXT", " report ").map { FileItem(it, false) }
        assertEquals(listOf("Report.TXT", " report "), fileManagerVisibleEntries(entries, "report", false, false).map { it.name })
        assertEquals(listOf("..", " report "), fileManagerVisibleEntries(entries, " report ", true, true).map { it.name })
    }

    @Test fun `initial storage and filesystem roots never offer an inoperative parent`() {
        assertFalse(fileManagerCanNavigateUp(FileManagerLocation("/storage/test", null), "/storage/test"))
        assertFalse(fileManagerCanNavigateUp(FileManagerLocation("/", "linux"), "/storage/test"))
        assertTrue(fileManagerCanNavigateUp(FileManagerLocation("/storage/test/sub", null), "/storage/test"))
    }

    @Test fun `compact sizes retain two decimal places and scale through units`() {
        assertEquals("0.00B", formatFileSize(0))
        assertEquals("1.00K", formatFileSize(1024))
        assertEquals("4.47M", formatFileSize(4_687_135))
        assertEquals("1.00G", formatFileSize(1_073_741_824))
        assertEquals("1.00T", formatFileSize(1_099_511_627_776))
        assertEquals("8.00E", formatFileSize(Long.MAX_VALUE))
    }
}
