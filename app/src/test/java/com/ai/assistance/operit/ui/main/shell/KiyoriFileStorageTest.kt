package com.ai.assistance.operit.ui.main.shell

import com.ai.assistance.operit.data.preferences.FileManagerSettings
import com.kiyori.app.shell.KiyoriShellState
import org.junit.Assert.*
import org.junit.Test

class KiyoriFileStorageTest {
    private val rows = kiyoriFileStorageRowItems("/storage/emulated/0", "/custom/workspace")
    private val linux = rows.single { it.kind == KiyoriFileStorageKind.LINUX }
    private val workspace = rows.single { it.kind == KiyoriFileStorageKind.WORKSPACE }

    @Test
    fun `all four targets carry the exact path and environment including internal storage`() {
        assertEquals(listOf("/storage/emulated/0", "/", "/custom/workspace", "/回收站"), rows.map { it.path })
        assertEquals(listOf(null, "linux", null, "recycle"), rows.map { it.environment })
        assertEquals("default-workspace", workspace.storageId)
    }

    @Test
    fun `explicit storage targets replace a retained Linux request without closing the shared session`() {
        val retained = KiyoriShellState().openFileManager("/previous", "linux").minimizeFileManager()
        rows.forEach { row ->
            val opened = retained.openFileManager(row.path, row.environment)
            assertEquals(row.path, opened.fileManagerPendingPath)
            assertEquals(row.environment, opened.fileManagerPendingEnvironment)
            assertTrue(opened.fileManagerSessionOpen)
            assertFalse(opened.fileManagerMinimized)
        }
    }

    @Test
    fun `removed locations and hidden workspace group project the actual visible state`() {
        val settings = FileManagerSettings(drawerRemoved = setOf(linux.storageId), showWorkspaces = false)
        assertEquals(rows.filter { it.fixed }, kiyoriVisibleFileStorageRowItems(rows, settings))
        assertFalse(kiyoriFileStorageRowVisible(linux, settings))
        assertFalse(kiyoriFileStorageRowVisible(workspace, settings))
    }

    @Test
    fun `reenabling workspace clears all blocking flags while preserving unrelated preferences`() {
        val original = FileManagerSettings(
            drawerHidden = setOf(workspace.storageId, linux.storageId, "other"),
            drawerRemoved = setOf(workspace.storageId, "other"),
            showWorkspaces = false,
            defaultWorkspacePath = "/custom/workspace",
            drawerNames = mapOf(workspace.storageId to "项目"),
            leftStartPath = "/custom/left",
        )
        val updated = original.withKiyoriStorageVisibility(workspace, true)
        assertEquals(original.copy(
            drawerHidden = setOf(linux.storageId, "other"),
            drawerRemoved = setOf("other"),
            showWorkspaces = true,
        ), updated)
        assertTrue(kiyoriFileStorageRowVisible(workspace, updated))
        assertFalse(kiyoriFileStorageRowVisible(linux, updated))
        assertEquals(updated, updated.withKiyoriStorageVisibility(workspace, true))
        val hiddenAgain = updated.withKiyoriStorageVisibility(workspace, false)
        assertFalse(kiyoriFileStorageRowVisible(workspace, hiddenAgain))
        assertTrue(hiddenAgain.showWorkspaces)
        assertEquals(updated, hiddenAgain.withKiyoriStorageVisibility(workspace, true))
    }

    @Test
    fun `reenabling Linux does not expose the workspace group`() {
        val settings = FileManagerSettings(drawerRemoved = setOf(linux.storageId), showWorkspaces = false)
        val updated = settings.withKiyoriStorageVisibility(linux, true)
        assertTrue(kiyoriFileStorageRowVisible(linux, updated))
        assertFalse(updated.showWorkspaces)
    }

    @Test
    fun `fixed entries cannot be hidden even with stale persisted flags`() {
        val settings = FileManagerSettings(
            drawerHidden = rows.map { it.storageId }.toSet(),
            drawerRemoved = rows.map { it.storageId }.toSet(),
            showWorkspaces = false,
        )
        rows.filter { it.fixed }.forEach { row ->
            assertTrue(kiyoriFileStorageRowVisible(row, settings))
            assertEquals(settings, settings.withKiyoriStorageVisibility(row, false))
        }
    }

    @Test
    fun `capacity remains bounded for empty full and very large storage`() {
        assertEquals(0f, KiyoriDeviceStorageCapacity(100, 100).usedFraction)
        assertEquals(1f, KiyoriDeviceStorageCapacity(100, 0).usedFraction)
        val large = KiyoriDeviceStorageCapacity(Long.MAX_VALUE, Long.MAX_VALUE / 2)
        assertEquals(0.5f, large.usedFraction, 0.0001f)
        assertEquals(Long.MAX_VALUE - Long.MAX_VALUE / 2, large.usedBytes)
    }

    @Test
    fun `invalid capacity fails instead of showing fabricated free space`() {
        listOf(0L to 0L, -1L to 0L, 100L to -1L, 100L to 101L).forEach { (total, available) ->
            assertThrows(IllegalArgumentException::class.java) { KiyoriDeviceStorageCapacity(total, available) }
        }
    }
}
