package com.ai.assistance.operit.ui.features.websession.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSettingsStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserNetworkRequestCategory
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserNetworkEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserNetworkHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserNetworkUrlExtension
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.compactBrowserNetworkLogUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.filterBrowserNetworkLogEntries
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.isThirdPartyBrowserNetworkRequest
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.resolveManualBrowserDownloadFileName
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
import com.ai.assistance.operit.util.AppLogger
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val NETWORK_LOG_TAG = "WebSessionNetworkLog"

private enum class BrowserNetworkLogFilter(
    val category: BrowserNetworkRequestCategory?,
) {
    ALL(null),
    VIDEO(BrowserNetworkRequestCategory.VIDEO),
    AUDIO(BrowserNetworkRequestCategory.AUDIO),
    IMAGE(BrowserNetworkRequestCategory.IMAGE),
    WEB(BrowserNetworkRequestCategory.WEB),
    OTHER(BrowserNetworkRequestCategory.OTHER),
}

@Composable
internal fun WebSessionBrowserNetworkLog(
    entries: List<WebSessionBrowserNetworkEntry>,
    currentPageUrl: String,
    onClear: () -> Unit,
    onStartDownload: (String, String, String, BrowserDownloadEngine) -> Boolean,
    onPlayMediaCandidate: (String) -> Boolean,
    onDownloadMediaCandidate: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val downloadSettingsStore = remember(context) { BrowserDownloadSettingsStore.getInstance(context) }
    var selectedFilter by rememberSaveable { mutableStateOf(BrowserNetworkLogFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var actionEntry by remember { mutableStateOf<WebSessionBrowserNetworkEntry?>(null) }
    var detailEntry by remember { mutableStateOf<WebSessionBrowserNetworkEntry?>(null) }
    val filteredEntries =
        remember(entries, selectedFilter, query) {
            filterBrowserNetworkLogEntries(entries, selectedFilter.category, query)
        }
    val copiedMessage = stringResource(R.string.web_session_network_log_copied)
    val externalOpenFailedMessage = stringResource(R.string.web_session_network_log_external_open_failed)

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KiyoriSemanticIconBadge(
                imageVector = Icons.Filled.Info,
                tone = KiyoriSemanticTone.CYAN,
                contentDescription = null,
                containerSize = 34.dp,
                iconSize = 18.dp,
                shape = RoundedCornerShape(10.dp),
            )
            Text(
                text = stringResource(R.string.web_session_network_log),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.web_session_network_log_count, entries.size),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.web_session_network_log_clear),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color =
                    if (entries.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                    } else {
                        KiyoriSemanticTone.RED.resolveColors().icon
                    },
                modifier =
                    Modifier
                        .height(44.dp)
                        .clickable(enabled = entries.isNotEmpty(), role = Role.Button, onClick = onClear)
                        .padding(horizontal = 10.dp, vertical = 13.dp),
            )
        }

        BrowserNetworkLogSearchField(
            value = query,
            onValueChange = { query = it },
            onClear = { query = "" },
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrowserNetworkLogFilter.entries.forEach { filter ->
                BrowserNetworkLogFilterChip(
                    label = browserNetworkLogFilterLabel(filter),
                    count =
                        if (filter.category == null) {
                            entries.size
                        } else {
                            entries.count { entry -> entry.category == filter.category }
                        },
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                )
            }
        }

        Text(
            text = stringResource(R.string.web_session_network_log_third_party_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
        )

        if (filteredEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        if (entries.isEmpty()) {
                            stringResource(R.string.web_session_network_log_empty)
                        } else {
                            stringResource(R.string.web_session_network_log_empty_filter)
                        },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 10.dp,
                    top = 8.dp,
                    end = 10.dp,
                    bottom = 18.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                itemsIndexed(
                    items = filteredEntries,
                    key = { index, entry -> "${entry.timestamp}_${entry.method}_${entry.url}_$index" },
                ) { _, entry ->
                    BrowserNetworkLogRow(
                        entry = entry,
                        currentPageUrl = currentPageUrl,
                        onClick = { actionEntry = entry },
                    )
                }
            }
        }
    }

    actionEntry?.let { entry ->
        BrowserNetworkLogActionDialog(
            entry = entry,
            onDismiss = { actionEntry = null },
            onCopy = {
                copyNetworkLogUrl(context, entry.url)
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                actionEntry = null
            },
            onDownload = {
                val fileName = resolveManualBrowserDownloadFileName("", entry.url, "")
                val accepted =
                    entry.mediaCandidateId?.let(onDownloadMediaCandidate)
                        ?: onStartDownload(
                            fileName,
                            entry.url,
                            "",
                            downloadSettingsStore.current.defaultEngine,
                        )
                if (accepted) {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.download_started, fileName),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                actionEntry = null
            },
            onPlay = {
                entry.mediaCandidateId?.let(onPlayMediaCandidate)
                actionEntry = null
            },
            onOpenExternal = {
                openNetworkLogUrlExternally(context, entry.url, externalOpenFailedMessage)
                actionEntry = null
            },
            onViewDetails = {
                detailEntry = entry
                actionEntry = null
            },
        )
    }

    detailEntry?.let { entry ->
        BrowserNetworkLogDetailsDialog(
            entry = entry,
            onDismiss = { detailEntry = null },
            onCopy = {
                copyNetworkLogUrl(context, entry.url)
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun BrowserNetworkLogSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cyanColors = KiyoriSemanticTone.CYAN.resolveColors()
    Surface(
        modifier = modifier.fillMaxWidth().height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
                cursorBrush = SolidColor(cyanColors.icon),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                text = stringResource(R.string.web_session_network_log_search_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (value.isNotBlank()) {
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.clear),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserNetworkLogFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cyanColors = KiyoriSemanticTone.CYAN.resolveColors()
    Surface(
        modifier = Modifier.height(36.dp).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color =
            if (selected) {
                cyanColors.container
            } else {
                MaterialTheme.colorScheme.surface
            },
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selected) cyanColors.icon else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "$label $count",
                color = if (selected) cyanColors.icon else MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun BrowserNetworkLogRow(
    entry: WebSessionBrowserNetworkEntry,
    currentPageUrl: String,
    onClick: () -> Unit,
) {
    val thirdParty = isThirdPartyBrowserNetworkRequest(currentPageUrl, entry.url)
    val thirdPartyColors = KiyoriSemanticTone.ORANGE.resolveColors()
    val host = browserNetworkHost(entry.url).ifBlank { entry.url.substringBefore(':') }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrowserNetworkTypeBadge(entry)
                Text(
                    text = entry.method,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 7.dp),
                )
                Text(
                    text = host,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 7.dp),
                )
                if (thirdParty) {
                    Text(
                        text = stringResource(R.string.web_session_network_log_third_party),
                        color = thirdPartyColors.icon,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        modifier =
                            Modifier
                                .background(thirdPartyColors.container, RoundedCornerShape(5.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = formatNetworkLogTime(entry.timestamp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                )
            }
            Text(
                text = compactBrowserNetworkLogUrl(entry.url),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun BrowserNetworkTypeBadge(entry: WebSessionBrowserNetworkEntry) {
    val colors = browserNetworkTone(entry.category).resolveColors()
    val extension = browserNetworkUrlExtension(entry.url)
    val label =
        extension.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
            ?: when (entry.category) {
                BrowserNetworkRequestCategory.VIDEO -> "VIDEO"
                BrowserNetworkRequestCategory.AUDIO -> "AUDIO"
                BrowserNetworkRequestCategory.IMAGE -> "IMG"
                BrowserNetworkRequestCategory.WEB -> "WEB"
                BrowserNetworkRequestCategory.OTHER -> "REQ"
            }
    Text(
        text = label,
        color = colors.icon,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        modifier =
            Modifier
                .widthIn(min = 34.dp, max = 54.dp)
                .background(colors.container, RoundedCornerShape(5.dp))
                .padding(horizontal = 5.dp, vertical = 3.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun BrowserNetworkLogActionDialog(
    entry: WebSessionBrowserNetworkEntry,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onOpenExternal: () -> Unit,
    onViewDetails: () -> Unit,
) {
    val networkUrl = entry.url.startsWith("http://", true) || entry.url.startsWith("https://", true)
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
            shape = WebSessionBrowserPopupShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BrowserNetworkDialogTitle(
                    title = stringResource(R.string.web_session_network_log_action_title),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
                BrowserNetworkLogActionRow(
                    icon = Icons.Filled.ContentCopy,
                    title = stringResource(R.string.web_session_network_log_copy_link),
                    tone = KiyoriSemanticTone.PURPLE,
                    onClick = onCopy,
                )
                if (entry.mediaCandidateId != null) {
                    BrowserNetworkLogActionRow(
                        icon = Icons.Filled.PlayArrow,
                        title = "在线播放",
                        tone = KiyoriSemanticTone.BLUE,
                        onClick = onPlay,
                    )
                }
                if (networkUrl) {
                    BrowserNetworkLogActionRow(
                        icon = Icons.Filled.Download,
                        title = stringResource(R.string.web_session_network_log_download_resource),
                        tone = KiyoriSemanticTone.GREEN,
                        onClick = onDownload,
                    )
                    BrowserNetworkLogActionRow(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        title = stringResource(R.string.web_session_network_log_open_external),
                        tone = KiyoriSemanticTone.BLUE,
                        onClick = onOpenExternal,
                    )
                }
                BrowserNetworkLogActionRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.web_session_network_log_view_details),
                    tone = KiyoriSemanticTone.CYAN,
                    onClick = onViewDetails,
                    drawDivider = false,
                )
            }
        }
    }
}

@Composable
private fun BrowserNetworkLogActionRow(
    icon: ImageVector,
    title: String,
    tone: KiyoriSemanticTone,
    onClick: () -> Unit,
    drawDivider: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KiyoriSemanticIconBadge(
                imageVector = icon,
                tone = tone,
                contentDescription = null,
                containerSize = 30.dp,
                iconSize = 16.dp,
                shape = RoundedCornerShape(9.dp),
            )
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (drawDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
        }
    }
}

@Composable
private fun BrowserNetworkLogDetailsDialog(
    entry: WebSessionBrowserNetworkEntry,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).heightIn(max = 560.dp),
            shape = WebSessionBrowserPopupShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BrowserNetworkDialogTitle(
                    title = stringResource(R.string.web_session_network_log_details_title),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
                Column(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BrowserNetworkLogDetailRow(
                        stringResource(R.string.web_session_network_log_detail_method),
                        entry.method,
                    )
                    BrowserNetworkLogDetailRow(
                        stringResource(R.string.web_session_network_log_detail_type),
                        browserNetworkCategoryLabel(entry.category),
                    )
                    BrowserNetworkLogDetailRow(
                        stringResource(R.string.web_session_network_log_detail_frame),
                        stringResource(
                            if (entry.isMainFrame) {
                                R.string.web_session_network_log_main_frame
                            } else {
                                R.string.web_session_network_log_subresource
                            },
                        ),
                    )
                    BrowserNetworkLogDetailRow(
                        stringResource(R.string.web_session_network_log_detail_time),
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                            .format(Date(entry.timestamp)),
                    )
                    BrowserNetworkLogDetailRow(
                        stringResource(R.string.web_session_network_log_detail_url),
                        entry.url,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable(role = Role.Button, onClick = onDismiss)
                                .padding(top = 14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.web_session_network_log_copy_link),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable(role = Role.Button, onClick = onCopy)
                                .padding(top = 14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserNetworkDialogTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KiyoriSemanticIconBadge(
            imageVector = Icons.Filled.Info,
            tone = KiyoriSemanticTone.CYAN,
            contentDescription = null,
            containerSize = 32.dp,
            iconSize = 18.dp,
            shape = RoundedCornerShape(9.dp),
        )
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun browserNetworkTone(category: BrowserNetworkRequestCategory): KiyoriSemanticTone =
    when (category) {
        BrowserNetworkRequestCategory.VIDEO -> KiyoriSemanticTone.CYAN
        BrowserNetworkRequestCategory.AUDIO -> KiyoriSemanticTone.PURPLE
        BrowserNetworkRequestCategory.IMAGE -> KiyoriSemanticTone.PINK
        BrowserNetworkRequestCategory.WEB -> KiyoriSemanticTone.BLUE
        BrowserNetworkRequestCategory.OTHER -> KiyoriSemanticTone.ORANGE
    }

@Composable
private fun BrowserNetworkLogDetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun browserNetworkLogFilterLabel(filter: BrowserNetworkLogFilter): String =
    stringResource(
        when (filter) {
            BrowserNetworkLogFilter.ALL -> R.string.web_session_network_log_filter_all
            BrowserNetworkLogFilter.VIDEO -> R.string.web_session_network_log_filter_video
            BrowserNetworkLogFilter.AUDIO -> R.string.web_session_network_log_filter_audio
            BrowserNetworkLogFilter.IMAGE -> R.string.web_session_network_log_filter_image
            BrowserNetworkLogFilter.WEB -> R.string.web_session_network_log_filter_web
            BrowserNetworkLogFilter.OTHER -> R.string.web_session_network_log_filter_other
        },
    )

@Composable
private fun browserNetworkCategoryLabel(category: BrowserNetworkRequestCategory): String =
    stringResource(
        when (category) {
            BrowserNetworkRequestCategory.VIDEO -> R.string.web_session_network_log_filter_video
            BrowserNetworkRequestCategory.AUDIO -> R.string.web_session_network_log_filter_audio
            BrowserNetworkRequestCategory.IMAGE -> R.string.web_session_network_log_filter_image
            BrowserNetworkRequestCategory.WEB -> R.string.web_session_network_log_filter_web
            BrowserNetworkRequestCategory.OTHER -> R.string.web_session_network_log_filter_other
        },
    )

private fun formatNetworkLogTime(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

private fun copyNetworkLogUrl(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Kiyori network request", url))
}

private fun openNetworkLogUrlExternally(
    context: Context,
    url: String,
    failureMessage: String,
) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { error ->
        AppLogger.e(NETWORK_LOG_TAG, "Failed to open recorded request URL externally", error)
        Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
    }
}
