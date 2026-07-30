package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
internal enum class WebSessionHistoryCategory {
    WEB,
    VIDEO,
    MUSIC,
    NOVEL,
    OTHER,
}

@Serializable
internal enum class WebSessionHistoryMediaOrigin {
    ONLINE,
    LOCAL,
}

internal enum class WebSessionHistoryFilter(
    val category: WebSessionHistoryCategory?,
) {
    ALL(null),
    WEB(WebSessionHistoryCategory.WEB),
    VIDEO(WebSessionHistoryCategory.VIDEO),
    MUSIC(WebSessionHistoryCategory.MUSIC),
    NOVEL(WebSessionHistoryCategory.NOVEL),
    OTHER(WebSessionHistoryCategory.OTHER),
}

internal enum class WebSessionHistoryDeleteRange(
    private val durationMillis: Long?,
) {
    PAST_HOUR(60L * 60L * 1_000L),
    PAST_DAY(24L * 60L * 60L * 1_000L),
    PAST_WEEK(7L * 24L * 60L * 60L * 1_000L),
    ALL_TIME(null),
    ;

    fun cutoffTimeMillis(nowMillis: Long): Long? =
        durationMillis?.let { duration -> nowMillis - duration }
}

internal fun filterWebSessionHistoryEntries(
    entries: List<WebSessionHistoryEntry>,
    filter: WebSessionHistoryFilter,
    query: String,
): List<WebSessionHistoryEntry> {
    val normalizedQuery = query.trim()
    return entries
        .asSequence()
        .filter { entry -> filter.category == null || entry.category == filter.category }
        .filter { entry ->
            normalizedQuery.isBlank() ||
                entry.title.contains(normalizedQuery, ignoreCase = true) ||
                entry.url.contains(normalizedQuery, ignoreCase = true) ||
                entry.sourcePageUrl.contains(normalizedQuery, ignoreCase = true)
        }
        .sortedByDescending(WebSessionHistoryEntry::visitedAt)
        .toList()
}

internal fun shouldDeleteWebSessionHistoryEntry(
    entry: WebSessionHistoryEntry,
    category: WebSessionHistoryCategory?,
    cutoffTimeMillis: Long?,
): Boolean =
    (category == null || entry.category == category) &&
        (cutoffTimeMillis == null || entry.visitedAt >= cutoffTimeMillis)

internal fun resolveWebSessionHistoryMediaOrigin(uri: String): WebSessionHistoryMediaOrigin {
    val scheme = uri.trim().substringBefore(':').lowercase(Locale.ROOT)
    require(scheme.isNotBlank()) { "History media URI scheme is blank" }
    return when (scheme) {
        "content", "file" -> WebSessionHistoryMediaOrigin.LOCAL
        else -> WebSessionHistoryMediaOrigin.ONLINE
    }
}
