package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import com.ai.assistance.operit.data.preferences.FileManagerSettings
import com.ai.assistance.operit.data.preferences.ApiPreferences.FileBookmark
import com.ai.assistance.operit.data.preferences.fileBookmarkIdentity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.*
import org.junit.Assert.*
import org.junit.Test

class FileManagerStorageManagementTest {
    @Test fun `legacy bookmark identity survives target edits and serialized recreation`() {
        val legacy = Json.decodeFromString<FileBookmark>("""{"name":"资料","path":"/old"}""")
        val id = fileBookmarkIdentity(legacy, workspace = false)
        val edited = legacy.copy(path = "/new", entryId = id)
        val restored = Json.decodeFromString<FileBookmark>(Json.encodeToString(edited))
        assertEquals(id, fileBookmarkIdentity(restored, workspace = false))
        val entry = FileManagerStorageEntry(restored.name, restored.path, fileBookmark = restored, category = "书签")
        val other = FileManagerStorageEntry("其他", "/other", category = "书签")
        assertEquals(listOf(entry, other), projectFileManagerStorageEntries(listOf(other, entry), FileManagerSettings(drawerOrder = listOf(id))))
        val newlyAdded = legacy.copy(entryId = "new-record")
        assertNotEquals(id, fileBookmarkIdentity(newlyAdded, workspace = false))
    }

    private val root = FileManagerStorageEntry("根目录", "/")
    private val internal = FileManagerStorageEntry("内部存储", "/storage/emulated/0")
    private val linux = FileManagerStorageEntry("Linux", "/", environment = "linux")
    private val saf = FileManagerStorageEntry("provider", "/", environment = "repo:provider", bookmarkUri = "content://test/tree/a")

    @Test fun `fixed roots remain visible and first even with stale hidden and ordering metadata`() {
        val entries = listOf(root, internal, linux, saf)
        val settings = FileManagerSettings(drawerHidden = setOf(root.storageId), drawerRemoved = setOf(internal.storageId),
            drawerOrder = listOf(saf.storageId, linux.storageId, internal.storageId, root.storageId))
        assertEquals(listOf(root, internal, saf, linux), projectFileManagerStorageEntries(entries, settings))
        assertTrue(root.isFixedLocal)
        assertTrue(internal.isFixedLocal)
        assertFalse(linux.isFixedLocal)
        assertFalse(saf.isFixedLocal)
    }

    @Test fun `renaming a SAF display entry preserves its backend identity and URI`() {
        val renamed = projectFileManagerStorageEntries(listOf(saf), FileManagerSettings(drawerNames = mapOf(saf.storageId to "资料"))).single()
        assertEquals("资料", renamed.title)
        assertEquals(saf.storageId, renamed.storageId)
        assertEquals(saf.environment, renamed.environment)
        assertEquals(saf.bookmarkUri, renamed.bookmarkUri)
        assertEquals(saf.path, renamed.path)
    }

    @Test fun `hidden entries are reversible and default workspace identity survives path edits`() {
        val workspace = FileManagerStorageEntry("默认工作区", "/original", category = "工作区")
        val settings = FileManagerSettings(drawerHidden = setOf(linux.storageId), defaultWorkspacePath = "/custom")
        assertEquals(listOf(saf), projectFileManagerStorageEntries(listOf(linux, saf), settings))
        assertEquals(listOf(linux, saf), projectFileManagerStorageEntries(listOf(linux, saf), settings.copy(drawerHidden = emptySet())))
        val edited = projectFileManagerStorageEntries(listOf(workspace), settings).single()
        assertEquals("/custom", edited.path)
        assertEquals(workspace.storageId, edited.storageId)
    }

    @Test fun `identities distinguish environment and category while insertion retains existing order`() {
        val bookmark = FileManagerStorageEntry("书签", "/", category = "书签")
        val workspace = bookmark.copy(category = "工作区", fileBookmark = com.ai.assistance.operit.data.preferences.ApiPreferences.FileBookmark("工作区", "/"))
        assertNotEquals(bookmark.storageId, workspace.storageId)
        assertNotEquals(root.storageId, linux.storageId)
        val ordered = projectFileManagerStorageEntries(listOf(root, internal, linux, saf), FileManagerSettings(drawerOrder = listOf(saf.storageId)))
        assertEquals(listOf(root, internal, saf, linux), ordered)
    }
}
