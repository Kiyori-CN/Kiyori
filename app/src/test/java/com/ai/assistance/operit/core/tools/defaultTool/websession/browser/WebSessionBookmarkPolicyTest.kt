package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionBookmarkPolicyTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun legacyBookmarkJsonDecodesWithRootFolderDefaults() {
        val bookmark =
            json.decodeFromString<WebSessionBookmark>(
                """{"url":"https://example.com/","title":"Example","createdAt":123,"updatedAt":456}""",
            )

        assertEquals(123L, bookmark.id)
        assertEquals("", bookmark.iconUrl)
        assertNull(bookmark.folderId)
        assertEquals(123L, bookmark.order)
        assertFalse(bookmark.secret)
    }

    @Test
    fun folderTreeCountsDescendantsAndRejectsCyclicMoves() {
        val collection =
            WebSessionBookmarkCollection(
                folders =
                    listOf(
                        folder(id = 1L, title = "小说"),
                        folder(id = 2L, title = "日本轻小说", parentId = 1L),
                        folder(id = 3L, title = "已完结", parentId = 2L),
                    ),
                bookmarks =
                    listOf(
                        bookmark(id = 11L, folderId = 1L),
                        bookmark(id = 12L, folderId = 2L),
                        bookmark(id = 13L, folderId = 3L),
                    ),
            )

        assertEquals(setOf(2L, 3L), collection.descendantFolderIds(1L))
        assertEquals(3, collection.bookmarkCount(1L, secret = false))
        assertFalse(collection.canMoveFolder(1L, 3L))
        assertFalse(collection.canMoveFolder(2L, 2L))
        assertTrue(collection.canMoveFolder(3L, null))
    }

    @Test
    fun saveRejectsUnknownFolderInsteadOfMovingBookmarkToRoot() {
        val original = WebSessionBookmarkCollection(emptyList(), emptyList())
        val updated =
            applyWebSessionBookmarkMutation(
                collection = original,
                mutation =
                    WebSessionBookmarkMutation.SaveBookmark(
                        WebSessionBookmarkDraft(
                            title = "Example",
                            url = "https://example.com",
                            iconUrl = "https://example.com/favicon.ico",
                            folderId = 99L,
                        ),
                        secret = false,
                    ),
                now = 100L,
            )

        assertEquals(original, updated)
    }

    @Test
    fun manualOrderMutationOnlyReordersSelectedSiblings() {
        val collection =
            WebSessionBookmarkCollection(
                folders = listOf(folder(1L, "A", order = 0L), folder(2L, "B", order = 1L)),
                bookmarks =
                    listOf(
                        bookmark(11L, folderId = 1L, order = 0L),
                        bookmark(12L, folderId = 1L, order = 1L),
                        bookmark(13L, folderId = 2L, order = 7L),
                    ),
            )
        val reordered =
            applyWebSessionBookmarkMutation(
                collection,
                WebSessionBookmarkMutation.SetBookmarkOrder(1L, false, listOf(12L, 11L)),
                now = 500L,
            )

        assertEquals(listOf(12L, 11L), reordered.bookmarks.filter { it.folderId == 1L }.sortedBy { it.order }.map { it.id })
        assertEquals(7L, reordered.bookmarks.single { it.id == 13L }.order)
    }

    @Test
    fun folderPickerTreeUsesManualSiblingOrderAndDepthFirstHierarchy() {
        val folders =
            listOf(
                folder(1L, "第二个一级", order = 2L),
                folder(2L, "第一个一级", order = 1L),
                folder(3L, "第二个二级", parentId = 2L, order = 3L),
                folder(4L, "第一个二级", parentId = 2L, order = 0L),
                folder(5L, "三级", parentId = 4L, order = 0L),
                folder(6L, "秘密文件夹", order = 0L).copy(secret = true),
            )

        assertEquals(
            listOf(
                WebSessionBookmarkFolderTreeEntry(2L, "第一个一级", 0),
                WebSessionBookmarkFolderTreeEntry(4L, "第一个二级", 1),
                WebSessionBookmarkFolderTreeEntry(5L, "三级", 2),
                WebSessionBookmarkFolderTreeEntry(3L, "第二个二级", 1),
                WebSessionBookmarkFolderTreeEntry(1L, "第二个一级", 0),
            ),
            buildWebSessionBookmarkFolderTree(folders, secret = false),
        )
        assertEquals(
            listOf(WebSessionBookmarkFolderTreeEntry(6L, "秘密文件夹", 0)),
            buildWebSessionBookmarkFolderTree(folders, secret = true),
        )
    }

    @Test
    fun subtreeArchiveRoundTripPreservesHierarchyContentAndRelativeOrder() {
        val source =
            WebSessionBookmarkCollection(
                folders =
                    listOf(
                        folder(1L, "外层"),
                        folder(2L, "小说", parentId = 1L),
                        folder(3L, "日本轻小说", parentId = 2L, order = 2L),
                        folder(4L, "已完结", parentId = 2L, order = 1L),
                    ),
                bookmarks =
                    listOf(
                        bookmark(11L, "后保存", folderId = 3L, order = 5L),
                        bookmark(12L, "先保存", folderId = 3L, order = 1L),
                        bookmark(13L, "完结书", folderId = 4L, order = 0L),
                    ),
            )
        val archive = source.buildBookmarkArchive(folderId = 2L, secret = false)
        val decoded = json.decodeFromString<WebSessionBookmarkArchive>(json.encodeToString(archive))

        assertTrue(isValidWebSessionBookmarkArchive(decoded))
        assertEquals(null, decoded.folders.single { it.id == 2L }.parentId)
        val imported =
            applyWebSessionBookmarkMutation(
                WebSessionBookmarkCollection(emptyList(), emptyList()),
                WebSessionBookmarkMutation.ImportArchive(decoded, secret = false),
                now = 1_000L,
            )
        val importedRoot = imported.folders.single { it.title == "小说" }
        assertNull(importedRoot.parentId)
        val children = imported.folders.filter { it.parentId == importedRoot.id }.sortedBy { it.order }
        assertEquals(listOf("已完结", "日本轻小说"), children.map { it.title })
        val lightNovelFolder = children.single { it.title == "日本轻小说" }
        val lightNovels = imported.bookmarks.filter { it.folderId == lightNovelFolder.id }.sortedBy { it.order }
        assertEquals(listOf("先保存", "后保存"), lightNovels.map { it.title })
        assertEquals("https://example.com/favicon.ico", lightNovels.first().iconUrl)
    }

    @Test
    fun archiveValidationRejectsMissingParentsAndCycles() {
        val missingParent = WebSessionBookmarkArchive(folders = listOf(folder(1L, "A", parentId = 9L)), bookmarks = emptyList())
        val cycle =
            WebSessionBookmarkArchive(
                folders = listOf(folder(1L, "A", parentId = 2L), folder(2L, "B", parentId = 1L)),
                bookmarks = emptyList(),
            )

        assertFalse(isValidWebSessionBookmarkArchive(missingParent))
        assertFalse(isValidWebSessionBookmarkArchive(cycle))
    }

    private fun folder(
        id: Long,
        title: String,
        parentId: Long? = null,
        order: Long = id,
    ): WebSessionBookmarkFolder =
        WebSessionBookmarkFolder(
            id = id,
            title = title,
            parentId = parentId,
            createdAt = id,
            order = order,
        )

    private fun bookmark(
        id: Long,
        title: String = "Bookmark $id",
        folderId: Long?,
        order: Long = id,
    ): WebSessionBookmark =
        WebSessionBookmark(
            id = id,
            title = title,
            url = "https://example.com/$id",
            iconUrl = "https://example.com/favicon.ico",
            folderId = folderId,
            createdAt = id,
            updatedAt = id,
            order = order,
        )
}
