package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
internal data class WebSessionBookmarkDraft(
    val title: String,
    val url: String,
    val iconUrl: String,
    val folderId: Long?,
    val newFolderTitle: String = "",
)

@Serializable
internal data class WebSessionBookmarkArchive(
    val version: Int = WEB_SESSION_BOOKMARK_ARCHIVE_VERSION,
    val folders: List<WebSessionBookmarkFolder>,
    val bookmarks: List<WebSessionBookmark>,
)

internal data class WebSessionBookmarkCollection(
    val folders: List<WebSessionBookmarkFolder>,
    val bookmarks: List<WebSessionBookmark>,
)

internal data class WebSessionBookmarkFolderTreeEntry(
    val id: Long,
    val title: String,
    val depth: Int,
)

internal sealed interface WebSessionBookmarkMutation {
    data class CreateFolder(val title: String, val parentId: Long?, val secret: Boolean) :
        WebSessionBookmarkMutation

    data class RenameFolder(val folderId: Long, val title: String) : WebSessionBookmarkMutation

    data class MoveFolder(val folderId: Long, val parentId: Long?) : WebSessionBookmarkMutation

    data class DeleteFolder(val folderId: Long, val secret: Boolean) : WebSessionBookmarkMutation

    data class SaveBookmark(val draft: WebSessionBookmarkDraft, val secret: Boolean) :
        WebSessionBookmarkMutation

    data class UpdateBookmark(val bookmarkId: Long, val draft: WebSessionBookmarkDraft) :
        WebSessionBookmarkMutation

    data class MoveBookmark(val bookmarkId: Long, val folderId: Long?) : WebSessionBookmarkMutation

    data class SetBookmarkSecret(val bookmarkId: Long, val secret: Boolean) :
        WebSessionBookmarkMutation

    data class DeleteBookmark(val bookmarkId: Long) : WebSessionBookmarkMutation

    data class DeleteBookmarksInFolder(val folderId: Long?, val secret: Boolean) :
        WebSessionBookmarkMutation

    data class SetFolderOrder(
        val parentId: Long?,
        val secret: Boolean,
        val orderedIds: List<Long>,
    ) : WebSessionBookmarkMutation

    data class SetBookmarkOrder(
        val folderId: Long?,
        val secret: Boolean,
        val orderedIds: List<Long>,
    ) : WebSessionBookmarkMutation

    data class AddBookmarkToHome(val bookmarkId: Long) : WebSessionBookmarkMutation

    data class AddFolderToHome(val folderId: Long) : WebSessionBookmarkMutation

    data class ImportArchive(val archive: WebSessionBookmarkArchive, val secret: Boolean) :
        WebSessionBookmarkMutation
}

internal fun applyWebSessionBookmarkMutation(
    collection: WebSessionBookmarkCollection,
    mutation: WebSessionBookmarkMutation,
    now: Long,
): WebSessionBookmarkCollection =
    when (mutation) {
        is WebSessionBookmarkMutation.CreateFolder ->
            collection.createFolder(mutation.title, mutation.parentId, mutation.secret, now).first
        is WebSessionBookmarkMutation.RenameFolder -> collection.renameFolder(mutation.folderId, mutation.title)
        is WebSessionBookmarkMutation.MoveFolder -> collection.moveFolder(mutation.folderId, mutation.parentId)
        is WebSessionBookmarkMutation.DeleteFolder -> collection.deleteFolder(mutation.folderId, mutation.secret)
        is WebSessionBookmarkMutation.SaveBookmark -> collection.saveBookmark(mutation.draft, mutation.secret, now)
        is WebSessionBookmarkMutation.UpdateBookmark -> collection.updateBookmark(mutation.bookmarkId, mutation.draft, now)
        is WebSessionBookmarkMutation.MoveBookmark -> collection.moveBookmark(mutation.bookmarkId, mutation.folderId)
        is WebSessionBookmarkMutation.SetBookmarkSecret ->
            collection.setBookmarkSecret(mutation.bookmarkId, mutation.secret)
        is WebSessionBookmarkMutation.DeleteBookmark ->
            collection.copy(bookmarks = collection.bookmarks.filterNot { it.id == mutation.bookmarkId })
        is WebSessionBookmarkMutation.DeleteBookmarksInFolder ->
            collection.deleteBookmarksInFolder(mutation.folderId, mutation.secret)
        is WebSessionBookmarkMutation.SetFolderOrder ->
            collection.setFolderOrder(mutation.parentId, mutation.secret, mutation.orderedIds)
        is WebSessionBookmarkMutation.SetBookmarkOrder ->
            collection.setBookmarkOrder(mutation.folderId, mutation.secret, mutation.orderedIds)
        is WebSessionBookmarkMutation.AddBookmarkToHome -> collection.addBookmarkToHome(mutation.bookmarkId, now)
        is WebSessionBookmarkMutation.AddFolderToHome -> collection.addFolderToHome(mutation.folderId, now)
        is WebSessionBookmarkMutation.ImportArchive -> collection.importArchive(mutation.archive, mutation.secret, now)
    }

internal fun WebSessionBookmarkCollection.canMoveFolder(folderId: Long, parentId: Long?): Boolean {
    if (folderId == parentId) return false
    val folder = folders.firstOrNull { it.id == folderId } ?: return false
    val invalidIds = descendantFolderIds(folderId) + folderId
    if (parentId != null && parentId in invalidIds) return false
    val parent = parentId?.let { id -> folders.firstOrNull { it.id == id } ?: return false }
    return parent == null || parent.secret == folder.secret
}

internal fun WebSessionBookmarkCollection.descendantFolderIds(folderId: Long): Set<Long> {
    val descendants = mutableSetOf<Long>()
    fun visit(parentId: Long) {
        folders.filter { it.parentId == parentId }.forEach { child ->
            if (descendants.add(child.id)) visit(child.id)
        }
    }
    visit(folderId)
    return descendants
}

internal fun WebSessionBookmarkCollection.bookmarkCount(folderId: Long, secret: Boolean): Int {
    val includedIds = descendantFolderIds(folderId) + folderId
    return bookmarks.count { it.secret == secret && it.folderId in includedIds }
}

internal fun buildWebSessionBookmarkFolderTree(
    folders: List<WebSessionBookmarkFolder>,
    secret: Boolean,
): List<WebSessionBookmarkFolderTreeEntry> {
    val foldersByParent =
        folders
            .filter { folder -> folder.secret == secret }
            .groupBy { folder -> folder.parentId }
    val result = mutableListOf<WebSessionBookmarkFolderTreeEntry>()
    val visited = mutableSetOf<Long>()

    fun appendChildren(parentId: Long?, depth: Int) {
        foldersByParent[parentId]
            .orEmpty()
            .sortedWith(
                compareBy<WebSessionBookmarkFolder> { folder -> folder.order }
                    .thenBy { folder -> folder.createdAt }
                    .thenBy { folder -> folder.id },
            ).forEach { folder ->
                if (visited.add(folder.id)) {
                    result += WebSessionBookmarkFolderTreeEntry(folder.id, folder.title, depth)
                    appendChildren(folder.id, depth + 1)
                }
            }
    }

    appendChildren(parentId = null, depth = 0)
    return result
}

internal fun WebSessionBookmarkCollection.buildBookmarkArchive(
    folderId: Long?,
    secret: Boolean,
): WebSessionBookmarkArchive {
    require(folderId == null || folders.any { it.id == folderId && it.secret == secret }) {
        "Bookmark folder does not exist in the selected space: $folderId"
    }
    val includedFolderIds =
        if (folderId == null) {
            folders.filter { it.secret == secret }.mapTo(mutableSetOf()) { it.id }
        } else {
            (descendantFolderIds(folderId) + folderId).toMutableSet()
        }
    return WebSessionBookmarkArchive(
        folders =
            folders
                .filter { it.secret == secret && it.id in includedFolderIds }
                .map { folder ->
                    if (folder.id == folderId) folder.copy(parentId = null) else folder
                },
        bookmarks =
            bookmarks.filter { bookmark ->
                bookmark.secret == secret &&
                    if (folderId == null) true else bookmark.folderId in includedFolderIds
            },
    )
}

internal fun isValidWebSessionBookmarkArchive(archive: WebSessionBookmarkArchive): Boolean {
    if (archive.version != WEB_SESSION_BOOKMARK_ARCHIVE_VERSION) return false
    if (archive.folders.any { it.title.isBlank() }) return false
    if (archive.bookmarks.any { normalizeWebSessionBookmarkUrl(it.url) == null }) return false

    val allIds = archive.folders.map { it.id } + archive.bookmarks.map { it.id }
    if (allIds.distinct().size != allIds.size) return false
    val folderById = archive.folders.associateBy { it.id }
    if (archive.folders.any { it.parentId != null && it.parentId !in folderById }) return false
    if (archive.bookmarks.any { it.folderId != null && it.folderId !in folderById }) return false

    archive.folders.forEach { folder ->
        val visited = mutableSetOf<Long>()
        var current: Long? = folder.id
        while (current != null) {
            if (!visited.add(current)) return false
            current = folderById[current]?.parentId
        }
    }
    return true
}

private fun WebSessionBookmarkCollection.createFolder(
    rawTitle: String,
    parentId: Long?,
    secret: Boolean,
    now: Long,
): Pair<WebSessionBookmarkCollection, Long?> {
    val title = rawTitle.trim()
    if (title.isBlank()) return this to null
    if (parentId != null && folders.none { it.id == parentId && it.secret == secret }) return this to null
    val targetParentId = parentId
    val existing = folders.firstOrNull {
        it.parentId == targetParentId && it.secret == secret && it.title.equals(title, ignoreCase = true)
    }
    if (existing != null) return this to existing.id
    val id = nextBookmarkStorageId(now)
    val folder =
        WebSessionBookmarkFolder(
            id = id,
            title = title,
            parentId = targetParentId,
            createdAt = now,
            order = nextFolderOrder(targetParentId, secret),
            secret = secret,
        )
    return copy(folders = folders + folder) to id
}

private fun WebSessionBookmarkCollection.renameFolder(
    folderId: Long,
    rawTitle: String,
): WebSessionBookmarkCollection {
    val title = rawTitle.trim()
    if (title.isBlank()) return this
    return copy(folders = folders.map { if (it.id == folderId) it.copy(title = title) else it })
}

private fun WebSessionBookmarkCollection.moveFolder(
    folderId: Long,
    parentId: Long?,
): WebSessionBookmarkCollection {
    if (!canMoveFolder(folderId, parentId)) return this
    val folder = folders.first { it.id == folderId }
    if (folder.parentId == parentId) return this
    val order = nextFolderOrder(parentId, folder.secret)
    return copy(
        folders = folders.map { if (it.id == folderId) it.copy(parentId = parentId, order = order) else it },
    )
}

private fun WebSessionBookmarkCollection.deleteFolder(
    folderId: Long,
    secret: Boolean,
): WebSessionBookmarkCollection {
    val deletedIds = descendantFolderIds(folderId) + folderId
    return copy(
        folders = folders.filterNot { it.secret == secret && it.id in deletedIds },
        bookmarks = bookmarks.filterNot { it.secret == secret && it.folderId in deletedIds },
    )
}

private fun WebSessionBookmarkCollection.saveBookmark(
    draft: WebSessionBookmarkDraft,
    secret: Boolean,
    now: Long,
): WebSessionBookmarkCollection {
    val normalizedUrl = normalizeWebSessionBookmarkUrl(draft.url) ?: return this
    val withFolder =
        if (draft.newFolderTitle.isBlank()) {
            if (draft.folderId != null && folders.none { it.id == draft.folderId && it.secret == secret }) return this
            this to draft.folderId
        } else {
            createFolder(draft.newFolderTitle, parentId = null, secret = secret, now = now)
        }
    val target = withFolder.first
    val folderId = withFolder.second
    val title = normalizedBookmarkTitle(draft.title, normalizedUrl)
    val existing = target.bookmarks.firstOrNull {
        it.url == normalizedUrl && it.folderId == folderId && it.secret == secret
    }
    val saved =
        existing?.copy(
            title = title,
            iconUrl = draft.iconUrl.trim(),
            updatedAt = now,
        ) ?: WebSessionBookmark(
            id = target.nextBookmarkStorageId(now),
            url = normalizedUrl,
            title = title,
            iconUrl = draft.iconUrl.trim(),
            folderId = folderId,
            createdAt = now,
            updatedAt = now,
            order = target.nextBookmarkOrder(folderId, secret),
            secret = secret,
        )
    return target.copy(bookmarks = target.bookmarks.filterNot { it.id == saved.id } + saved)
}

private fun WebSessionBookmarkCollection.updateBookmark(
    bookmarkId: Long,
    draft: WebSessionBookmarkDraft,
    now: Long,
): WebSessionBookmarkCollection {
    val original = bookmarks.firstOrNull { it.id == bookmarkId } ?: return this
    val normalizedUrl = normalizeWebSessionBookmarkUrl(draft.url) ?: return this
    val withFolder =
        if (draft.newFolderTitle.isBlank()) {
            if (draft.folderId != null && folders.none { it.id == draft.folderId && it.secret == original.secret }) return this
            this to draft.folderId
        } else {
            createFolder(draft.newFolderTitle, parentId = null, secret = original.secret, now = now)
        }
    val target = withFolder.first
    val folderId = withFolder.second
    val updated =
        original.copy(
            title = normalizedBookmarkTitle(draft.title, normalizedUrl),
            url = normalizedUrl,
            iconUrl = draft.iconUrl.trim(),
            folderId = folderId,
            updatedAt = now,
        )
    return target.copy(
        bookmarks =
            target.bookmarks
                .filterNot {
                    it.id != bookmarkId &&
                        it.url == normalizedUrl &&
                        it.folderId == folderId &&
                        it.secret == original.secret
                }.map { if (it.id == bookmarkId) updated else it },
    )
}

private fun WebSessionBookmarkCollection.moveBookmark(
    bookmarkId: Long,
    folderId: Long?,
): WebSessionBookmarkCollection {
    val bookmark = bookmarks.firstOrNull { it.id == bookmarkId } ?: return this
    if (folderId != null && folders.none { it.id == folderId && it.secret == bookmark.secret }) return this
    if (bookmark.folderId == folderId) return this
    val order = nextBookmarkOrder(folderId, bookmark.secret)
    return copy(
        bookmarks = bookmarks.map { if (it.id == bookmarkId) it.copy(folderId = folderId, order = order) else it },
    )
}

private fun WebSessionBookmarkCollection.setBookmarkSecret(
    bookmarkId: Long,
    secret: Boolean,
): WebSessionBookmarkCollection {
    val bookmark = bookmarks.firstOrNull { it.id == bookmarkId } ?: return this
    if (bookmark.secret == secret) return this
    return copy(
        bookmarks =
            bookmarks.map {
                if (it.id == bookmarkId) {
                    it.copy(folderId = null, secret = secret, order = nextBookmarkOrder(null, secret))
                } else {
                    it
                }
            },
    )
}

private fun WebSessionBookmarkCollection.deleteBookmarksInFolder(
    folderId: Long?,
    secret: Boolean,
): WebSessionBookmarkCollection {
    val includedIds = folderId?.let { descendantFolderIds(it) + it }
    return copy(
        bookmarks =
            bookmarks.filterNot {
                it.secret == secret &&
                    if (includedIds == null) it.folderId == null else it.folderId in includedIds
            },
    )
}

private fun WebSessionBookmarkCollection.setFolderOrder(
    parentId: Long?,
    secret: Boolean,
    orderedIds: List<Long>,
): WebSessionBookmarkCollection {
    val orderById = orderedIds.withIndex().associate { (index, id) -> id to index.toLong() }
    return copy(
        folders =
            folders.map { folder ->
                if (folder.parentId == parentId && folder.secret == secret) {
                    orderById[folder.id]?.let { folder.copy(order = it) } ?: folder
                } else {
                    folder
                }
            },
    )
}

private fun WebSessionBookmarkCollection.setBookmarkOrder(
    folderId: Long?,
    secret: Boolean,
    orderedIds: List<Long>,
): WebSessionBookmarkCollection {
    val orderById = orderedIds.withIndex().associate { (index, id) -> id to index.toLong() }
    return copy(
        bookmarks =
            bookmarks.map { bookmark ->
                if (bookmark.folderId == folderId && bookmark.secret == secret) {
                    orderById[bookmark.id]?.let { bookmark.copy(order = it) } ?: bookmark
                } else {
                    bookmark
                }
            },
    )
}

private fun WebSessionBookmarkCollection.addBookmarkToHome(
    bookmarkId: Long,
    now: Long,
): WebSessionBookmarkCollection {
    val bookmark = bookmarks.firstOrNull { it.id == bookmarkId && !it.secret } ?: return this
    val withHome = createFolder(HOME_NAVIGATION_FOLDER_TITLE, null, false, now)
    return withHome.first.saveBookmark(
        WebSessionBookmarkDraft(
            title = bookmark.title,
            url = bookmark.url,
            iconUrl = bookmark.iconUrl,
            folderId = withHome.second,
        ),
        secret = false,
        now = now,
    )
}

private fun WebSessionBookmarkCollection.addFolderToHome(
    folderId: Long,
    now: Long,
): WebSessionBookmarkCollection {
    val includedIds = descendantFolderIds(folderId) + folderId
    val source = bookmarks.filter { !it.secret && it.folderId in includedIds }
    if (source.isEmpty()) return this
    val withHome = createFolder(HOME_NAVIGATION_FOLDER_TITLE, null, false, now)
    var target = withHome.first
    source.forEachIndexed { index, bookmark ->
        target =
            target.saveBookmark(
                WebSessionBookmarkDraft(
                    title = bookmark.title,
                    url = bookmark.url,
                    iconUrl = bookmark.iconUrl,
                    folderId = withHome.second,
                ),
                secret = false,
                now = now + index,
            )
    }
    return target
}

private fun WebSessionBookmarkCollection.importArchive(
    archive: WebSessionBookmarkArchive,
    secret: Boolean,
    now: Long,
): WebSessionBookmarkCollection {
    require(isValidWebSessionBookmarkArchive(archive)) { "Invalid bookmark archive" }
    var target = this
    val newIdByOldId = mutableMapOf<Long, Long?>()
    val pendingFolders = archive.folders.toMutableList()
    var nextTimestamp = now
    while (pendingFolders.isNotEmpty()) {
        val ready =
            pendingFolders
                .filter { it.parentId == null || newIdByOldId.containsKey(it.parentId) }
                .sortedBy { it.order }
        require(ready.isNotEmpty()) { "Bookmark archive contains a cyclic folder graph" }
        ready.forEach { folder ->
            val parentId = folder.parentId?.let { newIdByOldId.getValue(it) }
            val created = target.createFolder(folder.title, parentId, secret, nextTimestamp++)
            target = created.first
            newIdByOldId[folder.id] = requireNotNull(created.second)
            pendingFolders.remove(folder)
        }
    }
    archive.bookmarks.sortedBy { it.order }.forEach { bookmark ->
        val folderId = bookmark.folderId?.let { newIdByOldId.getValue(it) }
        target =
            target.saveBookmark(
                WebSessionBookmarkDraft(
                    title = bookmark.title,
                    url = bookmark.url,
                    iconUrl = bookmark.iconUrl,
                    folderId = folderId,
                ),
                secret = secret,
                now = nextTimestamp++,
            )
    }
    return target
}

private fun WebSessionBookmarkCollection.nextBookmarkStorageId(now: Long): Long {
    val used = (folders.map { it.id } + bookmarks.map { it.id }).toHashSet()
    var candidate = now
    while (candidate in used) candidate++
    return candidate
}

private fun WebSessionBookmarkCollection.nextFolderOrder(parentId: Long?, secret: Boolean): Long =
    (folders.filter { it.parentId == parentId && it.secret == secret }.maxOfOrNull { it.order } ?: -1L) + 1L

private fun WebSessionBookmarkCollection.nextBookmarkOrder(folderId: Long?, secret: Boolean): Long =
    (bookmarks.filter { it.folderId == folderId && it.secret == secret }.maxOfOrNull { it.order } ?: -1L) + 1L

private fun normalizedBookmarkTitle(rawTitle: String, url: String): String =
    rawTitle.trim().ifBlank { url.substringAfter("://").substringBefore('/').ifBlank { url } }

internal const val WEB_SESSION_BOOKMARK_ARCHIVE_VERSION = 1
internal const val HOME_NAVIGATION_FOLDER_TITLE = "主页导航"
