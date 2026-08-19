package com.ai.assistance.operit.ui.features.websession.browser

import android.graphics.Color as AndroidColor
import android.view.ViewConfiguration
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
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
import androidx.core.graphics.drawable.toDrawable
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadSettingsStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserNetworkLogEntryKind
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserNetworkRequestCategory
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserNetworkImageViewerSnapshot
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BROWSER_NETWORK_IMAGE_VIEWER_MIN_SCALE
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserNetworkEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserNetworkImageViewerBackgroundAlpha
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.buildBrowserNetworkImageViewerSnapshot
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserNetworkHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserNetworkUrlExtension
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.clampBrowserNetworkImageViewerScale
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.compactBrowserNetworkLogUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.filterBrowserNetworkLogEntries
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.isThirdPartyBrowserNetworkRequest
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.normalizeBrowserResourceIdentityUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.resolveManualBrowserDownloadFileName
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.shouldDismissBrowserNetworkImageViewer
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
import com.ai.assistance.operit.util.AppLogger
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import okhttp3.Headers

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
    var imageViewerSnapshot by remember { mutableStateOf<BrowserNetworkImageViewerSnapshot?>(null) }
    var pendingImageSaveEntry by remember { mutableStateOf<WebSessionBrowserNetworkEntry?>(null) }
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
    val startDownload: (WebSessionBrowserNetworkEntry) -> Unit = { entry ->
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
    }
    LaunchedEffect(pendingImageSaveEntry) {
        pendingImageSaveEntry?.let { entry ->
            startDownload(entry)
            pendingImageSaveEntry = null
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
        BrowserNetworkImageViewer(
            snapshot = snapshot,
            onDismiss = { imageViewerSnapshot = null },
            onSave = { entry ->
                pendingImageSaveEntry = entry
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
    val cyanColors = WebSessionBrowserMenuTone.NETWORK_LOG.resolveColors()
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
                entry = entry,
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

@Composable
private fun BrowserNetworkImageViewer(
    snapshot: BrowserNetworkImageViewerSnapshot,
    onDismiss: () -> Unit,
    onSave: (WebSessionBrowserNetworkEntry) -> Unit,
) {
    val context = LocalContext.current
    val gestureScope = rememberCoroutineScope()
    val viewConfiguration = remember(context) { ViewConfiguration.get(context) }
    val pagerState =
        rememberPagerState(
            initialPage = snapshot.initialPage,
            pageCount = { snapshot.entries.size },
        )
    var verticalOffsetPx by remember { mutableStateOf(0f) }
    var viewportHeightPx by remember { mutableStateOf(0f) }
    var saveActionVisible by remember { mutableStateOf(false) }
    var imageScale by remember(snapshot.entries) {
        mutableStateOf(BROWSER_NETWORK_IMAGE_VIEWER_MIN_SCALE)
    }

    LaunchedEffect(pagerState.currentPage) {
        imageScale = BROWSER_NETWORK_IMAGE_VIEWER_MIN_SCALE
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        val dialogWindow = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect {
            // 图片查看层必须与底层浏览器共享真实透明度；保留 Dialog 默认 dim 会让
            // 黑色背景即使变透明也只能露出一层暗化后的网页。
            dialogWindow.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialogWindow.setBackgroundDrawable(AndroidColor.TRANSPARENT.toDrawable())
            val insetsController =
                WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
        BackHandler {
            if (saveActionVisible) {
                saveActionVisible = false
            } else {
                onDismiss()
            }
        }

        val backgroundAlpha =
            browserNetworkImageViewerBackgroundAlpha(
                verticalOffsetPx = verticalOffsetPx,
                viewportHeightPx = viewportHeightPx,
            )
        val currentEntry = snapshot.entries[pagerState.currentPage]
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backgroundAlpha)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { size -> viewportHeightPx = size.height.toFloat() }
                        .clipToBounds()
                        .graphicsLayer { translationY = verticalOffsetPx }
                        .pointerInput(snapshot.entries, saveActionVisible) {
                            if (saveActionVisible) {
                                return@pointerInput
                            }
                            awaitEachGesture {
                                val down =
                                    awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial,
                                    )
                                var horizontalGesture = false
                                var verticalGesture = false
                                var movedBeyondSlop = false
                                var longPressTriggered = false
                                var pointerIsDown = true
                                var completedWithUp = false
                                var pinchGesture = false
                                var previousPinchDistancePx = 0f
                                val longPressJob =
                                    gestureScope.launch {
                                        kotlinx.coroutines.delay(ViewConfiguration.getLongPressTimeout().toLong())
                                        if (pointerIsDown && !movedBeyondSlop) {
                                            longPressTriggered = true
                                            saveActionVisible = true
                                        }
                                    }
                                try {
                                    while (true) {
                                        val event =
                                            awaitPointerEvent(
                                                androidx.compose.ui.input.pointer.PointerEventPass.Initial,
                                            )
                                        val pressedPointers =
                                            event.changes.filter { change -> change.pressed }
                                        if (pressedPointers.size >= 2) {
                                            movedBeyondSlop = true
                                            pinchGesture = true
                                            longPressJob.cancel()
                                            val firstPointer = pressedPointers[0]
                                            val secondPointer = pressedPointers[1]
                                            val pinchDistancePx =
                                                (firstPointer.position - secondPointer.position)
                                                    .getDistance()
                                            if (pinchDistancePx > 0f) {
                                                if (previousPinchDistancePx > 0f) {
                                                    imageScale =
                                                        clampBrowserNetworkImageViewerScale(
                                                            imageScale *
                                                                (pinchDistancePx / previousPinchDistancePx),
                                                        )
                                                }
                                                previousPinchDistancePx = pinchDistancePx
                                            }
                                            pressedPointers.forEach { change -> change.consume() }
                                            continue
                                        }
                                        if (pinchGesture) {
                                            event.changes.forEach { change -> change.consume() }
                                            if (pressedPointers.isEmpty()) {
                                                completedWithUp = true
                                                break
                                            }
                                            continue
                                        }
                                        val pointer =
                                            event.changes.firstOrNull { change ->
                                                change.id == down.id
                                            } ?: break
                                        val totalDelta = pointer.position - down.position
                                        if (
                                            !movedBeyondSlop &&
                                                totalDelta.getDistance() > viewConfiguration.scaledTouchSlop
                                        ) {
                                            movedBeyondSlop = true
                                            longPressJob.cancel()
                                            if (kotlin.math.abs(totalDelta.x) >= kotlin.math.abs(totalDelta.y)) {
                                                horizontalGesture = true
                                            } else {
                                                verticalGesture = true
                                            }
                                        }
                                        if (horizontalGesture) {
                                            break
                                        }
                                        if (verticalGesture) {
                                            verticalOffsetPx = totalDelta.y
                                            pointer.consume()
                                        }
                                        if (!pointer.pressed) {
                                            completedWithUp = true
                                            break
                                        }
                                    }
                                } finally {
                                    pointerIsDown = false
                                    longPressJob.cancel()
                                }
                                if (verticalGesture && completedWithUp && !longPressTriggered) {
                                    if (
                                        shouldDismissBrowserNetworkImageViewer(
                                            verticalOffsetPx = verticalOffsetPx,
                                            touchSlopPx = viewConfiguration.scaledTouchSlop.toFloat(),
                                        )
                                    ) {
                                        onDismiss()
                                    } else {
                                        verticalOffsetPx = 0f
                                    }
                                } else if (
                                    completedWithUp &&
                                        !movedBeyondSlop &&
                                        !longPressTriggered
                                ) {
                                    onDismiss()
                                } else if (verticalGesture) {
                                    verticalOffsetPx = 0f
                                }
                            }
                        },
            ) { page ->
                val entry = snapshot.entries[page]
                val request =
                    remember(entry.resourceIdentity, entry.requestHeaders) {
                        buildBrowserResourceImageRequest(
                            context = context,
                            entry = entry,
                            thumbnail = false,
                        )
                    }
                SubcomposeAsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = imageScale
                                scaleY = imageScale
                            },
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "正在加载图片",
                                color = Color.White.copy(alpha = 0.76f),
                                fontSize = 14.sp,
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "图片加载失败",
                                color = Color.White,
                                fontSize = 14.sp,
                            )
                        }
                    },
                )
            }

            if (!saveActionVisible) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .graphicsLayer { alpha = backgroundAlpha },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${snapshot.entries.size}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "保存",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier =
                            Modifier
                                .clickable(role = Role.Button) { onSave(currentEntry) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.22f))
                            .clickable { saveActionVisible = false },
                ) {
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(20.dp)
                                .clickable(role = Role.Button) { onSave(currentEntry) },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                    ) {
                        Text(
                            text = "保存原图",
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun buildBrowserResourceImageRequest(
    context: Context,
    entry: WebSessionBrowserNetworkEntry,
    thumbnail: Boolean,
): ImageRequest {
    val headers = Headers.Builder()
    entry.requestHeaders.forEach { (name, value) ->
        if (
            BrowserImageRequestHeaderNames.any { allowed ->
                allowed.equals(name, ignoreCase = true)
            } &&
                value.isNotBlank() &&
                '\r' !in value &&
                '\n' !in value
        ) {
            headers.set(name, value)
        }
    }
    return ImageRequest.Builder(context)
        .data(entry.url)
        .headers(headers.build())
        .apply {
            if (thumbnail) {
                size(160, 160)
            }
        }
        .crossfade(false)
        .build()
}

private val BrowserImageRequestHeaderNames =
    setOf(
        "Accept",
        "Cookie",
        "Origin",
        "Referer",
        "User-Agent",
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
