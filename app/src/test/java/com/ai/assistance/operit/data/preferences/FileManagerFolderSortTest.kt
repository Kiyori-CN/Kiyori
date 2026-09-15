package com.ai.assistance.operit.data.preferences

import org.junit.Assert.*
import org.junit.Test

class FileManagerFolderSortTest {

    @Test fun `folder override wins over the global default only for its own path`() {
        val settings = FileManagerSettings(sortMode = FileManagerSortMode.NAME, sortDescending = false)
            .withSort("/storage/docs", null, FileManagerSortMode.SIZE, true, folderOnly = true)

        assertEquals(FileManagerSortMode.SIZE, settings.folderSort("/storage/docs", null)?.mode)
        assertTrue(settings.folderSort("/storage/docs", null)!!.descending)
        assertNull(settings.folderSort("/storage/other", null))
        // 全局默认不能被局部覆盖改写，否则其他目录会跟着变。
        assertEquals(FileManagerSortMode.NAME, settings.sortMode)
        assertFalse(settings.sortDescending)
    }

    @Test fun `path and environment are compared by whole segments`() {
        val settings = FileManagerSettings().withSort("/storage/docs/", null, FileManagerSortMode.MODIFIED, true, folderOnly = true)

        assertNotNull(settings.folderSort("/storage/docs", null))
        assertNotNull(settings.folderSort("/storage/docs", "android"))
        assertNull(settings.folderSort("/storage/docs-backup", null))
        assertNull(settings.folderSort("/storage/docs/sub", null))
        assertNull(settings.folderSort("/storage/docs", "linux"))
    }

    @Test fun `writing the global default clears only this path's override`() {
        val settings = FileManagerSettings()
            .withSort("/a", null, FileManagerSortMode.SIZE, true, folderOnly = true)
            .withSort("/b", null, FileManagerSortMode.MODIFIED, false, folderOnly = true)
            .withSort("/a", null, FileManagerSortMode.FORMAT, true, folderOnly = false)

        assertEquals(FileManagerSortMode.FORMAT, settings.sortMode)
        assertTrue(settings.sortDescending)
        assertNull(settings.folderSort("/a", null))
        assertEquals(FileManagerSortMode.MODIFIED, settings.folderSort("/b", null)?.mode)
    }

    @Test fun `re-sorting the same folder replaces rather than stacks its override`() {
        val settings = FileManagerSettings()
            .withSort("/a", null, FileManagerSortMode.SIZE, true, folderOnly = true)
            .withSort("/a", null, FileManagerSortMode.NAME, false, folderOnly = true)

        assertEquals(1, settings.folderSorts.size)
        assertEquals(FileManagerSortMode.NAME, settings.folderSort("/a", null)?.mode)
    }

    @Test fun `overrides are capped so the preference file cannot grow without bound`() {
        val settings = (1..MAX_FOLDER_SORT_OVERRIDES + 20).fold(FileManagerSettings()) { acc, index ->
            acc.withSort("/folder$index", null, FileManagerSortMode.SIZE, true, folderOnly = true)
        }

        assertEquals(MAX_FOLDER_SORT_OVERRIDES, settings.folderSorts.size)
        // 保留最近调整过的路径，最早的被丢弃。
        assertNull(settings.folderSort("/folder1", null))
        assertNotNull(settings.folderSort("/folder${MAX_FOLDER_SORT_OVERRIDES + 20}", null))
    }

    @Test fun `renaming a folder carries its own and its descendants' overrides`() {
        val settings = FileManagerSettings()
            .withSort("/work/project", null, FileManagerSortMode.SIZE, true, folderOnly = true)
            .withSort("/work/project/src", null, FileManagerSortMode.MODIFIED, false, folderOnly = true)
            .withSort("/work/project-backup", null, FileManagerSortMode.FORMAT, true, folderOnly = true)
            .moveFolderSorts("/work/project", "/work/renamed", null)

        assertEquals(FileManagerSortMode.SIZE, settings.folderSort("/work/renamed", null)?.mode)
        assertEquals(FileManagerSortMode.MODIFIED, settings.folderSort("/work/renamed/src", null)?.mode)
        assertNull(settings.folderSort("/work/project", null))
        assertNull(settings.folderSort("/work/project/src", null))
        // 同名前缀的兄弟目录不能被一起迁走。
        assertEquals(FileManagerSortMode.FORMAT, settings.folderSort("/work/project-backup", null)?.mode)
    }

    @Test fun `renaming onto an existing path replaces that path's override instead of duplicating it`() {
        val settings = FileManagerSettings()
            .withSort("/a", null, FileManagerSortMode.SIZE, true, folderOnly = true)
            .withSort("/b", null, FileManagerSortMode.FORMAT, false, folderOnly = true)
            .moveFolderSorts("/a", "/b", null)

        assertEquals(1, settings.folderSorts.size)
        assertEquals(FileManagerSortMode.SIZE, settings.folderSort("/b", null)?.mode)
    }

    @Test fun `resetting browsing preferences drops every folder override`() {
        val settings = FileManagerSettings()
            .withSort("/a", null, FileManagerSortMode.SIZE, true, folderOnly = true)
            .resetBrowsing()

        assertTrue(settings.folderSorts.isEmpty())
    }
}
