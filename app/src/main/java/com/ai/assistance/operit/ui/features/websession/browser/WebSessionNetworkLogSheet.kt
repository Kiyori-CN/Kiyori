package com.ai.assistance.operit.ui.features.websession.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.SubcomposeAsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSettingsStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserNetworkLogEntryKind
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserNetworkRequestCategory
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserImageViewerItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserImageViewerSnapshot
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserNetworkEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.buildBrowserNetworkImageViewerSnapshot
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserNetworkHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserNetworkUrlExtension
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.compactBrowserNetworkLogUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.filterBrowserNetworkLogEntries
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.isThirdPartyBrowserNetworkRequest
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.normalizeBrowserResourceIdentityUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.resolveManualBrowserDownloadFileName
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
import com.kiyori.design.theme.KiyoriUiShapes
import com.ai.assistance.operit.util.AppLogger
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val NETWORK_LOG_TAG = "WebSessionNetworkLog"

private enum class BrowserNetworkLogFilter(
    val category: BrowserNetworkRequestCategory?,
    val blockedOnly: Boolean = false,
) {
    ALL(null),
    VIDEO(BrowserNetworkRequestCategory.VIDEO),
    AUDIO(BrowserNetworkRequestCategory.AUDIO),
    IMAGE(BrowserNetworkRequestCategory.IMAGE),
    WEB(BrowserNetworkRequestCategory.WEB),
    SCRIPT(BrowserNetworkRequestCategory.SCRIPT),
    STYLE(BrowserNetworkRequestCategory.STYLE),
    DATA(BrowserNetworkRequestCategory.DATA),
    FONT(BrowserNetworkRequestCategory.FONT),
    OTHER(BrowserNetworkRequestCategory.OTHER),
    BLOCKED(null, blockedOnly = true),
}

@Composable
internal fun WebSessionBrowserNetworkLog(
    entries: List<WebSessionBrowserNetworkEntry>,
    currentPageUrl: String,
    onClear: () -> Unit,
    onBlockUrl: (String) -> Unit,
    onStartDownload: (String, String, String, BrowserDownloadEngine) -> Boolean,
    onPlayMediaCandidate: (String) -> Boolean,
    onDownloadMediaCandidate: (String) -> Boolean,
    onOpenPageSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val downloadSettingsStore = remember(context) { BrowserDownloadSettingsStore.getInstance(context) }
    var selectedFilter by rememberSaveable { mutableStateOf(BrowserNetworkLogFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var actionEntry by remember { mutableStateOf<WebSessionBrowserNetworkEntry?>(null) }
    var detailEntry by remember { mutableStateOf<WebSessionBrowserNetworkEntry?>(null) }
    var imageViewerSnapshot by remember { mutableStateOf<BrowserImageViewerSnapshot?>(null) }
    var pendingImageSaveItem by remember { mutableStateOf<BrowserImageViewerItem?>(null) }
    val filteredEntries =
        remember(entries, selectedFilter, query) {
            filterBrowserNetworkLogEntries(
                entries = entries,
                category = selectedFilter.category,
                query = query,
                blockedOnly = selectedFilter.blockedOnly,
            )
        }
    val copiedMessage = stringResource(R.string.web_session_network_log_copied)
    val externalOpenFailedMessage = stringResource(R.string.web_session_network_log_external_open_failed)
    val startDownloadUrl: (String, String?) -> Unit = { url, mediaCandidateId ->
        val fileName = resolveManualBrowserDownloadFileName("", url, "")
        val accepted =
            mediaCandidateId?.let(onDownloadMediaCandidate)
                ?: onStartDownload(
                    fileName,
                    url,
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
    }
    val startDownload: (WebSessionBrowserNetworkEntry) -> Unit = { entry ->
        startDownloadUrl(entry.url, entry.mediaCandidateId)
    }
    LaunchedEffect(pendingImageSaveItem) {
        pendingImageSaveItem?.let { item ->
            startDownloadUrl(item.url, item.mediaCandidateId)
            pendingImageSaveItem = null
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        WebSessionDrawerHeader(
            title = stringResource(R.string.web_session_network_log),
            leadingIcon = Icons.Filled.Info,
            tone = WebSessionBrowserMenuTone.NETWORK_LOG,
            countText =
                pluralStringResource(
                    R.plurals.web_session_network_log_count,
                    entries.size,
                    entries.size,
                ),
            actions = {
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
            },
        )

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
                        if (filter.blockedOnly) {
                            entries.count(WebSessionBrowserNetworkEntry::blocked)
                        } else if (filter.category == null) {
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
                    key = { _, entry ->
                        "${entry.documentToken}_${entry.kind}_${entry.resourceIdentity}_${entry.elementSelector}"
                    },
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
        val canViewCurrentPageSource =
            entry.kind == BrowserNetworkLogEntryKind.REQUEST &&
                entry.isMainFrame &&
                entry.category == BrowserNetworkRequestCategory.WEB &&
                normalizeBrowserResourceIdentityUrl(entry.url) ==
                normalizeBrowserResourceIdentityUrl(currentPageUrl)
        BrowserNetworkLogActionDialog(
            entry = entry,
            canViewCurrentPageSource = canViewCurrentPageSource,
            onDismiss = { actionEntry = null },
            onCopy = {
                copyNetworkLogUrl(context, entry.url)
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                actionEntry = null
            },
            onDownload = {
                startDownload(entry)
                actionEntry = null
            },
            onPlay = {
                entry.mediaCandidateId?.let(onPlayMediaCandidate)
                actionEntry = null
            },
            onViewImage = {
                imageViewerSnapshot =
                    buildBrowserNetworkImageViewerSnapshot(
                        entries = filteredEntries,
                        selectedResourceIdentity = entry.resourceIdentity,
                    )
                actionEntry = null
            },
            onViewPageSource = {
                actionEntry = null
                onOpenPageSource()
            },
            onOpenExternal = {
                openNetworkLogUrlExternally(context, entry.url, externalOpenFailedMessage)
                actionEntry = null
            },
            onBlock = {
                onBlockUrl(entry.url)
                Toast.makeText(
                    context,
                    R.string.web_session_network_log_block_rule_saved,
                    Toast.LENGTH_SHORT,
                ).show()
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

    imageViewerSnapshot?.let { snapshot ->
        WebSessionBrowserImageViewer(
            snapshot = snapshot,
            onDismiss = { imageViewerSnapshot = null },
            onSave = { item ->
                pendingImageSaveItem = item
                imageViewerSnapshot = null
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
    val cyanColors = WebSessionBrowserMenuTone.NETWORK_LOG.resolveColors()
    Surface(
        modifier = modifier.fillMaxWidth().height(40.dp),
        shape = KiyoriUiShapes.control,
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
                IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
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
    val cyanColors = WebSessionBrowserMenuTone.NETWORK_LOG.resolveColors()
    Surface(
        modifier = Modifier.heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onClick),
        shape = KiyoriUiShapes.control,
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
    val isElementEntry = entry.kind == BrowserNetworkLogEntryKind.ELEMENT
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.category == BrowserNetworkRequestCategory.IMAGE && !isElementEntry) {
                BrowserNetworkImageThumbnail(entry)
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
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
                if (entry.blocked) {
                    val blockedColors = KiyoriSemanticTone.RED.resolveColors()
                    Text(
                        text = stringResource(R.string.web_session_network_log_filter_blocked),
                        color = blockedColors.icon,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier =
                            Modifier
                                .background(blockedColors.container, RoundedCornerShape(5.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (entry.requestCount > 1) {
                    Text(
                        text = "×${entry.requestCount}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Text(
                text = compactBrowserNetworkLogUrl(entry.url),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (isElementEntry) {
                val selector = checkNotNull(entry.elementSelector) {
                    "Element network-log entry is missing its interception rule"
                }
                Text(
                    text = "网页元素 · 拦截规则",
                    color = KiyoriSemanticTone.RED.resolveColors().icon,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = selector,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (entry.blocked) {
                Text(
                    text =
                        entry.blockingSourceName
                            ?.takeIf(String::isNotBlank)
                            ?: stringResource(R.string.web_session_network_log_blocked_by_custom),
                    color = KiyoriSemanticTone.RED.resolveColors().icon,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            }
        }
    }
}

@Composable
private fun BrowserNetworkImageThumbnail(
    entry: WebSessionBrowserNetworkEntry,
) {
    val context = LocalContext.current
    val request =
        remember(entry.resourceIdentity, entry.requestHeaders) {
            buildBrowserResourceImageRequest(
                context = context,
                url = entry.url,
                requestHeaders = entry.requestHeaders,
                thumbnail = true,
            )
        }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        modifier =
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        contentScale = ContentScale.Crop,
        loading = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "IMG",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        error = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = browserNetworkUrlExtension(entry.url).uppercase(Locale.ROOT).ifBlank { "IMG" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
    )
}

@Composable
private fun BrowserNetworkTypeBadge(entry: WebSessionBrowserNetworkEntry) {
    val colors =
        if (entry.blocked) {
            KiyoriSemanticTone.RED.resolveColors()
        } else {
            browserNetworkTone(entry.category).resolveColors()
    }
    val extension = browserNetworkUrlExtension(entry.url)
    val label =
        if (entry.kind == BrowserNetworkLogEntryKind.ELEMENT) {
            "DOM"
        } else if (entry.blocked) {
            "BLOCK"
        } else {
            extension.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
            ?: when (entry.category) {
                BrowserNetworkRequestCategory.VIDEO -> "VIDEO"
                BrowserNetworkRequestCategory.AUDIO -> "AUDIO"
                BrowserNetworkRequestCategory.IMAGE -> "IMG"
                BrowserNetworkRequestCategory.WEB -> "WEB"
                BrowserNetworkRequestCategory.SCRIPT -> "JS"
                BrowserNetworkRequestCategory.STYLE -> "CSS"
                BrowserNetworkRequestCategory.DATA -> "DATA"
                BrowserNetworkRequestCategory.FONT -> "FONT"
                BrowserNetworkRequestCategory.OTHER -> "REQ"
            }
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
    canViewCurrentPageSource: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onPlay: () -> Unit,
    onViewImage: () -> Unit,
    onViewPageSource: () -> Unit,
    onDownload: () -> Unit,
    onOpenExternal: () -> Unit,
    onBlock: () -> Unit,
    onViewDetails: () -> Unit,
) {
    val isElementEntry = entry.kind == BrowserNetworkLogEntryKind.ELEMENT
    val networkUrl = entry.url.startsWith("http://", true) || entry.url.startsWith("https://", true)
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Filled.Info,
            tone = WebSessionBrowserMenuTone.NETWORK_LOG,
            title = stringResource(R.string.web_session_network_log_action_title),
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
        ) {
            BrowserNetworkLogActionRow(
                icon = Icons.Filled.ContentCopy,
                title = stringResource(R.string.web_session_network_log_copy_link),
                tone = KiyoriSemanticTone.PURPLE,
                onClick = onCopy,
            )
            if (
                entry.mediaCandidateId != null &&
                    entry.category == BrowserNetworkRequestCategory.VIDEO
            ) {
                BrowserNetworkLogActionRow(
                    icon = Icons.Filled.PlayArrow,
                    title = "播放视频",
                    tone = KiyoriSemanticTone.BLUE,
                    onClick = onPlay,
                )
            } else if (entry.category == BrowserNetworkRequestCategory.AUDIO) {
                BrowserNetworkUnavailableActionRow(
                    title = "播放音乐",
                    description = "内置音乐播放器尚未实现",
                )
            }
            if (entry.category == BrowserNetworkRequestCategory.IMAGE && networkUrl) {
                BrowserNetworkLogActionRow(
                    icon = Icons.Filled.Info,
                    title = "查看图片",
                    tone = KiyoriSemanticTone.PINK,
                    onClick = onViewImage,
                )
            }
            if (canViewCurrentPageSource) {
                BrowserNetworkLogActionRow(
                    icon = Icons.Filled.Code,
                    title = stringResource(R.string.web_session_network_log_view_page_source),
                    tone = KiyoriSemanticTone.CYAN,
                    onClick = onViewPageSource,
                )
            }
            if (!isElementEntry && networkUrl) {
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
                if (!entry.blocked) {
                    BrowserNetworkLogActionRow(
                        icon = Icons.Filled.Block,
                        title = stringResource(R.string.web_session_network_log_block_url),
                        tone = KiyoriSemanticTone.RED,
                        onClick = onBlock,
                    )
                }
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

@Composable
private fun BrowserNetworkUnavailableActionRow(
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            fontSize = 11.sp,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
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
        WebSessionBrowserDialogSurface(
            icon = Icons.Filled.Info,
            tone = WebSessionBrowserMenuTone.NETWORK_LOG,
            title = stringResource(R.string.web_session_network_log_details_title),
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).heightIn(max = 560.dp),
        ) {
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
                    stringResource(R.string.web_session_network_log_detail_first_seen),
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                        .format(Date(entry.firstSeenAt)),
                )
                BrowserNetworkLogDetailRow(
                    stringResource(R.string.web_session_network_log_detail_last_seen),
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                        .format(Date(entry.lastSeenAt)),
                )
                BrowserNetworkLogDetailRow(
                    stringResource(R.string.web_session_network_log_detail_request_count),
                    entry.requestCount.toString(),
                )
                BrowserNetworkLogDetailRow(
                    stringResource(R.string.web_session_network_log_detail_url),
                    entry.url,
                )
                if (entry.kind == BrowserNetworkLogEntryKind.ELEMENT) {
                    BrowserNetworkLogDetailRow(
                        "网页元素",
                        "DOM",
                    )
                    BrowserNetworkLogDetailRow(
                        "拦截规则",
                        checkNotNull(entry.elementSelector) {
                            "Element network-log entry is missing its interception rule"
                        },
                    )
                }
                BrowserNetworkLogDetailRow(
                    stringResource(R.string.web_session_network_log_detail_result),
                    stringResource(
                        if (entry.blocked) {
                            R.string.web_session_network_log_result_blocked
                        } else {
                            R.string.web_session_network_log_result_allowed
                        },
                    ),
                )
                entry.blockingSourceName?.takeIf(String::isNotBlank)?.let { sourceName ->
                    BrowserNetworkLogDetailRow(
                        stringResource(R.string.web_session_network_log_detail_block_source),
                        sourceName,
                    )
                }
                entry.blockingRule?.takeIf(String::isNotBlank)?.let { rule ->
                    BrowserNetworkLogDetailRow(
                        stringResource(R.string.web_session_network_log_detail_block_rule),
                        rule,
                    )
                }
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

private fun browserNetworkTone(category: BrowserNetworkRequestCategory): KiyoriSemanticTone =
    when (category) {
        BrowserNetworkRequestCategory.VIDEO -> KiyoriSemanticTone.CYAN
        BrowserNetworkRequestCategory.AUDIO -> KiyoriSemanticTone.PURPLE
        BrowserNetworkRequestCategory.IMAGE -> KiyoriSemanticTone.PINK
        BrowserNetworkRequestCategory.WEB -> KiyoriSemanticTone.BLUE
        BrowserNetworkRequestCategory.SCRIPT -> KiyoriSemanticTone.PURPLE
        BrowserNetworkRequestCategory.STYLE -> KiyoriSemanticTone.CYAN
        BrowserNetworkRequestCategory.DATA -> KiyoriSemanticTone.GREEN
        BrowserNetworkRequestCategory.FONT -> KiyoriSemanticTone.ORANGE
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
            BrowserNetworkLogFilter.SCRIPT -> R.string.web_session_network_log_filter_script
            BrowserNetworkLogFilter.STYLE -> R.string.web_session_network_log_filter_style
            BrowserNetworkLogFilter.DATA -> R.string.web_session_network_log_filter_data
            BrowserNetworkLogFilter.FONT -> R.string.web_session_network_log_filter_font
            BrowserNetworkLogFilter.OTHER -> R.string.web_session_network_log_filter_other
            BrowserNetworkLogFilter.BLOCKED -> R.string.web_session_network_log_filter_blocked
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
            BrowserNetworkRequestCategory.SCRIPT -> R.string.web_session_network_log_filter_script
            BrowserNetworkRequestCategory.STYLE -> R.string.web_session_network_log_filter_style
            BrowserNetworkRequestCategory.DATA -> R.string.web_session_network_log_filter_data
            BrowserNetworkRequestCategory.FONT -> R.string.web_session_network_log_filter_font
            BrowserNetworkRequestCategory.OTHER -> R.string.web_session_network_log_filter_other
        },
    )

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
