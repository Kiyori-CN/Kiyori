package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.Context
import com.kiyori.capability.browser.presentation.KiyoriBrowserSearchSource
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.webSessionHistoryDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "web_session_browser_store")

internal class WebSessionHistoryStore private constructor(private val context: Context) {

    companion object {
        private val KEY_BOOKMARKS = stringPreferencesKey("bookmarks_json")
        private val KEY_BOOKMARK_FOLDERS = stringPreferencesKey("bookmark_folders_json")
        private val KEY_HISTORY = stringPreferencesKey("history_json")
        private val KEY_SEARCH_ENGINE = stringPreferencesKey("search_engine")
        private val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history_json")
        private const val MAX_HISTORY_ENTRIES = 500
        private const val MAX_SEARCH_HISTORY_ENTRIES = 12

        @Volatile private var instance: WebSessionHistoryStore? = null

        fun getInstance(context: Context): WebSessionHistoryStore {
            return instance ?: synchronized(this) {
                instance ?: WebSessionHistoryStore(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val bookmarksFlow: Flow<List<WebSessionBookmark>> =
        context.webSessionHistoryDataStore.data.map { preferences ->
            decodeBookmarks(preferences[KEY_BOOKMARKS])
                .sortedWith(compareBy<WebSessionBookmark> { it.order }.thenByDescending { it.updatedAt })
        }

    val bookmarkFoldersFlow: Flow<List<WebSessionBookmarkFolder>> =
        context.webSessionHistoryDataStore.data.map { preferences ->
            decodeBookmarkFolders(preferences[KEY_BOOKMARK_FOLDERS])
                .sortedWith(compareBy<WebSessionBookmarkFolder> { it.order }.thenBy { it.createdAt })
        }

    val historyFlow: Flow<List<WebSessionHistoryEntry>> =
        context.webSessionHistoryDataStore.data.map { preferences ->
            decodeHistory(preferences[KEY_HISTORY])
                .sortedByDescending { it.visitedAt }
        }

    val searchEngineFlow: Flow<WebSessionSearchEngine> =
        context.webSessionHistoryDataStore.data.map { preferences ->
            WebSessionSearchEngine.fromId(preferences[KEY_SEARCH_ENGINE])
        }

    val searchHistoryFlow: Flow<List<WebSessionSearchRecord>> =
        context.webSessionHistoryDataStore.data.map { preferences ->
            decodeSearchHistory(preferences[KEY_SEARCH_HISTORY])
                .sortedByDescending { it.createdAt }
        }

    suspend fun recordVisit(url: String, title: String, isReload: Boolean) {
        val normalizedUrl = normalizeUrl(url) ?: return
        if (isReload) {
            updateTitle(normalizedUrl, title)
            return
        }

        val now = System.currentTimeMillis()
        context.webSessionHistoryDataStore.edit { preferences ->
            val current = decodeHistory(preferences[KEY_HISTORY])
            val normalizedTitle = normalizeTitle(title, normalizedUrl)
            val updated = buildList {
                add(
                    WebSessionHistoryEntry(
                        url = normalizedUrl,
                        title = normalizedTitle,
                        visitedAt = now,
                        category = WebSessionHistoryCategory.WEB,
                    )
                )
                addAll(
                    current.filterNot { entry ->
                        entry.category == WebSessionHistoryCategory.WEB &&
                            entry.url == normalizedUrl
                    }
                )
            }
            preferences[KEY_HISTORY] = json.encodeToString(updated.take(MAX_HISTORY_ENTRIES))
        }
    }

    suspend fun recordMediaPlayback(
        uri: String,
        title: String,
        sourcePageUrl: String,
    ) {
        val normalizedUri = normalizeHistoryTarget(uri) ?: return
        val normalizedSourcePageUrl =
            normalizeUrl(sourcePageUrl).orEmpty()
        val now = System.currentTimeMillis()
        context.webSessionHistoryDataStore.edit { preferences ->
            val current = decodeHistory(preferences[KEY_HISTORY])
            val mediaOrigin = resolveWebSessionHistoryMediaOrigin(normalizedUri)
            val updated = buildList {
                add(
                    WebSessionHistoryEntry(
                        url = normalizedUri,
                        title = normalizeTitle(title, normalizedUri),
                        visitedAt = now,
                        category = WebSessionHistoryCategory.VIDEO,
                        mediaOrigin = mediaOrigin,
                        sourcePageUrl = normalizedSourcePageUrl,
                    )
                )
                addAll(
                    current.filterNot { entry ->
                        entry.url == normalizedUri &&
                            (
                                entry.category == WebSessionHistoryCategory.WEB ||
                                    entry.category == WebSessionHistoryCategory.VIDEO
                            )
                    }
                )
            }
            preferences[KEY_HISTORY] = json.encodeToString(updated.take(MAX_HISTORY_ENTRIES))
        }
    }

    suspend fun updateTitle(url: String, title: String) {
        val normalizedUrl = normalizeUrl(url) ?: return
        val normalizedTitle = normalizeTitle(title, normalizedUrl)

        context.webSessionHistoryDataStore.edit { preferences ->
            val history =
                decodeHistory(preferences[KEY_HISTORY]).map { entry ->
                    if (
                        entry.category == WebSessionHistoryCategory.WEB &&
                            entry.url == normalizedUrl &&
                            normalizedTitle.isNotBlank()
                    ) {
                        entry.copy(title = normalizedTitle)
                    } else {
                        entry
                    }
                }

            preferences[KEY_HISTORY] = json.encodeToString(history.take(MAX_HISTORY_ENTRIES))
        }
    }

    suspend fun removeBookmark(url: String, secret: Boolean) {
        val normalizedUrl = normalizeUrl(url) ?: return
        context.webSessionHistoryDataStore.edit { preferences ->
            val updated =
                decodeBookmarks(preferences[KEY_BOOKMARKS]).filterNot { bookmark ->
                    bookmark.url == normalizedUrl && bookmark.secret == secret
                }
            preferences[KEY_BOOKMARKS] = json.encodeToString(updated)
        }
    }

    suspend fun applyBookmarkMutation(mutation: WebSessionBookmarkMutation) {
        context.webSessionHistoryDataStore.edit { preferences ->
            val current =
                WebSessionBookmarkCollection(
                    folders = decodeBookmarkFolders(preferences[KEY_BOOKMARK_FOLDERS]),
                    bookmarks = decodeBookmarks(preferences[KEY_BOOKMARKS]),
                )
            val updated =
                applyWebSessionBookmarkMutation(
                    collection = current,
                    mutation = mutation,
                    now = System.currentTimeMillis(),
                )
            preferences[KEY_BOOKMARK_FOLDERS] = json.encodeToString(updated.folders)
            preferences[KEY_BOOKMARKS] = json.encodeToString(updated.bookmarks)
        }
    }

    suspend fun deleteHistory(
        category: WebSessionHistoryCategory?,
        cutoffTimeMillis: Long?,
    ) {
        context.webSessionHistoryDataStore.edit { preferences ->
            val updated =
                decodeHistory(preferences[KEY_HISTORY]).filterNot { entry ->
                    shouldDeleteWebSessionHistoryEntry(
                        entry = entry,
                        category = category,
                        cutoffTimeMillis = cutoffTimeMillis,
                    )
                }
            preferences[KEY_HISTORY] = json.encodeToString(updated)
        }
    }

    suspend fun deleteHistoryEntries(entryKeys: Set<WebSessionHistoryEntryKey>) {
        if (entryKeys.isEmpty()) return
        context.webSessionHistoryDataStore.edit { preferences ->
            // Titles may be refreshed after a visit. URL, category, and visit time are the stable
            // record identity, so exact deletion cannot accidentally remove a newer visit.
            val updated =
                removeWebSessionHistoryEntries(
                    entries = decodeHistory(preferences[KEY_HISTORY]),
                    entryKeys = entryKeys,
                )
            preferences[KEY_HISTORY] = json.encodeToString(updated)
        }
    }

    suspend fun setSearchEngine(engine: WebSessionSearchEngine) {
        context.webSessionHistoryDataStore.edit { preferences ->
            preferences[KEY_SEARCH_ENGINE] = engine.id
        }
    }

    suspend fun addSearchHistory(
        query: String,
        targetUrl: String,
        engineId: String,
        source: KiyoriBrowserSearchSource,
    ) {
        val normalizedQuery = query.trim()
        val normalizedTargetUrl = targetUrl.trim()
        val normalizedEngineId = engineId.trim()
        if (
            normalizedQuery.isBlank() ||
                normalizedTargetUrl.isBlank() ||
                normalizedEngineId.isBlank()
        ) {
            return
        }

        val now = System.currentTimeMillis()
        context.webSessionHistoryDataStore.edit { preferences ->
            val current = decodeSearchHistory(preferences[KEY_SEARCH_HISTORY])
            val updated = buildList {
                add(
                    WebSessionSearchRecord(
                        id = now,
                        query = normalizedQuery,
                        targetUrl = normalizedTargetUrl,
                        createdAt = now,
                        engineId = normalizedEngineId,
                        source = source,
                    )
                )
                addAll(
                    current.filterNot {
                        it.query == normalizedQuery || it.targetUrl == normalizedTargetUrl
                    }
                )
            }
            preferences[KEY_SEARCH_HISTORY] = json.encodeToString(
                updated.take(MAX_SEARCH_HISTORY_ENTRIES)
            )
        }
    }

    suspend fun deleteSearchHistory(id: Long) {
        context.webSessionHistoryDataStore.edit { preferences ->
            val updated = decodeSearchHistory(preferences[KEY_SEARCH_HISTORY])
                .filterNot { it.id == id }
            preferences[KEY_SEARCH_HISTORY] = json.encodeToString(updated)
        }
    }

    suspend fun clearSearchHistory() {
        context.webSessionHistoryDataStore.edit { preferences ->
            preferences[KEY_SEARCH_HISTORY] = json.encodeToString(emptyList<WebSessionSearchRecord>())
        }
    }

    private fun decodeBookmarks(raw: String?): List<WebSessionBookmark> {
        return if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<WebSessionBookmark>>(raw) }.getOrElse { emptyList() }
        }
    }

    private fun decodeHistory(raw: String?): List<WebSessionHistoryEntry> {
        return if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<WebSessionHistoryEntry>>(raw) }.getOrElse { emptyList() }
        }
    }

    private fun decodeBookmarkFolders(raw: String?): List<WebSessionBookmarkFolder> {
        return if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            json.decodeFromString<List<WebSessionBookmarkFolder>>(raw)
        }
    }

    private fun decodeSearchHistory(raw: String?): List<WebSessionSearchRecord> {
        return if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<WebSessionSearchRecord>>(raw) }
                .getOrElse { emptyList() }
        }
    }

    private fun normalizeUrl(raw: String): String? = normalizeWebSessionBookmarkUrl(raw)

    private fun normalizeHistoryTarget(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val normalizedWebUrl = normalizeUrl(trimmed)
        if (normalizedWebUrl != null) return normalizedWebUrl
        val scheme = trimmed.substringBefore(':').lowercase(Locale.ROOT)
        return trimmed.takeIf { scheme == "content" || scheme == "file" }
    }

    private fun normalizeTitle(title: String, fallbackUrl: String): String {
        val trimmed = title.trim()
        return if (trimmed.isBlank()) fallbackUrl else trimmed
    }
}

internal fun normalizeWebSessionBookmarkUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    val lower = trimmed.lowercase(Locale.ROOT)
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null

    val uri =
        try {
            URI(trimmed)
        } catch (error: URISyntaxException) {
            return null
        }
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
    val host = uri.host?.lowercase(Locale.ROOT) ?: return null
    val portPart =
        when {
            uri.port < 0 -> ""
            scheme == "http" && uri.port == 80 -> ""
            scheme == "https" && uri.port == 443 -> ""
            else -> ":${uri.port}"
        }
    val path = uri.rawPath?.ifBlank { "/" } ?: "/"
    return buildString {
        append(scheme)
        append("://")
        append(host)
        append(portPart)
        append(path)
        uri.rawQuery?.takeIf { it.isNotBlank() }?.let {
            append('?')
            append(it)
        }
    }
}
