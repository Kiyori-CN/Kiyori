package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmark
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserTab
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryCategory
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import java.net.URI
import java.util.Locale

internal enum class BrowserHomeDashboardEntryKind {
    CONFIGURED_HOME,
    BOOKMARK,
    HISTORY,
}

@Immutable
internal data class BrowserHomeDashboardEntry(
    val id: String,
    val title: String,
    val url: String,
    val host: String,
    val kind: BrowserHomeDashboardEntryKind,
)

internal fun buildBrowserHomeDashboardEntries(
    bookmarks: List<WebSessionBookmark>,
    history: List<WebSessionHistoryEntry>,
    configuredHomeUrl: String,
): List<BrowserHomeDashboardEntry> {
    val entries = mutableListOf<BrowserHomeDashboardEntry>()
    val seenUrls = linkedSetOf<String>()

    fun addEntry(
        id: String,
        title: String,
        url: String,
        kind: BrowserHomeDashboardEntryKind,
    ) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank() || !seenUrls.add(normalizedUrl)) {
            return
        }
        entries +=
            BrowserHomeDashboardEntry(
                id = id,
                title = title.trim(),
                url = normalizedUrl,
                host = browserHomeDashboardHost(normalizedUrl),
                kind = kind,
            )
    }

    addEntry(
        id = "configured-home",
        title = "GoTab 官网",
        url = configuredHomeUrl,
        kind = BrowserHomeDashboardEntryKind.CONFIGURED_HOME,
    )
    bookmarks
        .asSequence()
        .filterNot(WebSessionBookmark::secret)
        .take(8)
        .forEach { bookmark ->
            addEntry(
                id = "bookmark-${bookmark.id}",
                title = bookmark.title,
                url = bookmark.url,
                kind = BrowserHomeDashboardEntryKind.BOOKMARK,
            )
        }
    history
        .asSequence()
        .filter { entry -> entry.category == WebSessionHistoryCategory.WEB }
        .take(8)
        .forEachIndexed { index, entry ->
            addEntry(
                id = "history-$index-${entry.visitedAt}",
                title = entry.title,
                url = entry.url,
                kind = BrowserHomeDashboardEntryKind.HISTORY,
            )
        }
    return entries
}

internal fun browserHomeDashboardHost(url: String): String {
    val normalized = url.trim()
    val parsed = runCatching { URI(normalized) }.getOrNull()
    return parsed?.host?.lowercase(Locale.ROOT).orEmpty()
}

@Composable
internal fun BrowserHomeDashboard(
    bookmarks: List<WebSessionBookmark>,
    history: List<WebSessionHistoryEntry>,
    tabs: List<WebSessionBrowserTab>,
    configuredHomeUrl: String,
    searchEngine: WebSessionSearchEngine,
    onOpenSearch: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenTabs: () -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries =
        buildBrowserHomeDashboardEntries(
            bookmarks = bookmarks,
            history = history,
            configuredHomeUrl = configuredHomeUrl,
        )
    val shortcutEntries = entries.take(9)
    val recentEntries =
        entries
            .asSequence()
            .filter { entry -> entry.kind == BrowserHomeDashboardEntryKind.HISTORY }
            .take(6)
            .toList()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.web_session_native_home_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.web_session_native_home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clickable(role = Role.Button, onClick = onOpenSearch)
                        .semantics { role = Role.Button },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.web_session_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.web_session_native_home_search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = searchEngine.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrowserHomeDashboardAction(
                    icon = Icons.Filled.Language,
                    label = stringResource(R.string.web_session_native_home_verify_gotab),
                    supporting = stringResource(R.string.web_session_native_home_verify_gotab_supporting),
                    onClick = { onOpenUrl(configuredHomeUrl) },
                    modifier = Modifier.weight(1f),
                )
                BrowserHomeDashboardAction(
                    icon = Icons.Filled.Tab,
                    label = stringResource(R.string.web_session_native_home_tabs),
                    supporting = stringResource(R.string.web_session_native_home_tab_count, tabs.size),
                    onClick = onOpenTabs,
                    modifier = Modifier.weight(1f),
                )
                BrowserHomeDashboardAction(
                    icon = Icons.Filled.Add,
                    label = stringResource(R.string.web_session_native_home_new_tab),
                    supporting = stringResource(R.string.web_session_native_home_new_tab_supporting),
                    onClick = onNewTab,
                    modifier = Modifier.weight(1f),
                )
            }

            BrowserHomeDashboardSectionTitle(
                title = stringResource(R.string.web_session_native_home_shortcuts),
                supporting = stringResource(R.string.web_session_native_home_shortcuts_supporting),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shortcutEntries.forEach { entry ->
                    BrowserHomeDashboardEntryCard(
                        entry = entry,
                        onClick = { onOpenUrl(entry.url) },
                    )
                }
            }

            BrowserHomeDashboardSectionTitle(
                title = stringResource(R.string.web_session_native_home_recent),
                supporting = stringResource(R.string.web_session_native_home_recent_supporting),
            )
            if (recentEntries.isEmpty()) {
                Text(
                    text = stringResource(R.string.web_session_native_home_recent_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recentEntries.forEach { entry ->
                        BrowserHomeDashboardRecentRow(
                            entry = entry,
                            onClick = { onOpenUrl(entry.url) },
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.web_session_native_home_data_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 540.dp),
            )
        }
    }
}

@Composable
private fun BrowserHomeDashboardAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    supporting: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .height(82.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { role = Role.Button },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BrowserHomeDashboardSectionTitle(
    title: String,
    supporting: String,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = supporting,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BrowserHomeDashboardEntryCard(
    entry: BrowserHomeDashboardEntry,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .width(164.dp)
                .height(90.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { role = Role.Button },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.host,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text =
                        when (entry.kind) {
                            BrowserHomeDashboardEntryKind.CONFIGURED_HOME ->
                                stringResource(R.string.web_session_native_home_verify_gotab)
                            BrowserHomeDashboardEntryKind.BOOKMARK ->
                                stringResource(R.string.web_session_native_home_bookmark)
                            BrowserHomeDashboardEntryKind.HISTORY ->
                                stringResource(R.string.web_session_native_home_history)
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun BrowserHomeDashboardRecentRow(
    entry: BrowserHomeDashboardEntry,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { role = Role.Button },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = stringResource(R.string.web_session_native_home_history),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.host,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
