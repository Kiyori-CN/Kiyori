package com.ai.assistance.operit.ui.features.websession.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserMediaCandidateVideoFormat
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserMediaCandidate
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
import java.util.Locale

private const val ALL_VIDEO_FORMATS = "ALL"

@Composable
internal fun WebSessionMediaCandidateSheet(
    candidates: List<WebSessionBrowserMediaCandidate>,
    onPlay: (String) -> Boolean,
    onDownload: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selectedFormat by rememberSaveable { mutableStateOf(ALL_VIDEO_FORMATS) }
    var orderedFormats by remember {
        mutableStateOf<List<BrowserMediaCandidateVideoFormat>>(emptyList())
    }
    var actionCandidate by remember { mutableStateOf<WebSessionBrowserMediaCandidate?>(null) }
    var linkCandidate by remember { mutableStateOf<WebSessionBrowserMediaCandidate?>(null) }
    val availableFormats =
        remember(candidates) {
            candidates.map(WebSessionBrowserMediaCandidate::videoFormat).distinct()
        }
    LaunchedEffect(availableFormats) {
        orderedFormats =
            orderedFormats.filter { it in availableFormats } +
                availableFormats.filterNot { it in orderedFormats }
        if (
            selectedFormat != ALL_VIDEO_FORMATS &&
                availableFormats.none { it.name == selectedFormat }
        ) {
            selectedFormat = ALL_VIDEO_FORMATS
        }
    }
    val visibleCandidates =
        remember(candidates, selectedFormat) {
            if (selectedFormat == ALL_VIDEO_FORMATS) {
                candidates
            } else {
                candidates.filter { it.videoFormat.name == selectedFormat }
            }
        }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        BrowserMediaCandidateHeader(
            candidateCount = candidates.size,
            onDismiss = onDismiss,
        )
        BrowserMediaFormatFilters(
            candidates = candidates,
            orderedFormats = orderedFormats,
            selectedFormat = selectedFormat,
            onSelect = { selectedFormat = it },
        )

        if (candidates.isEmpty()) {
            BrowserMediaCandidateEmptyState()
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentPadding =
                    PaddingValues(
                        start = 10.dp,
                        top = 8.dp,
                        end = 10.dp,
                        bottom = 18.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleCandidates, key = WebSessionBrowserMediaCandidate::id) { candidate ->
                    BrowserMediaCandidateCard(
                        candidate = candidate,
                        onOpenActions = { actionCandidate = candidate },
                        onPlay = { onPlay(candidate.id) },
                        onDownload = { onDownload(candidate.id) },
                    )
                }
            }
        }
    }

    actionCandidate?.let { candidate ->
        BrowserMediaCandidateActionDialog(
            onDismiss = { actionCandidate = null },
            onPlay = {
                onPlay(candidate.id)
                actionCandidate = null
            },
            onDownload = {
                onDownload(candidate.id)
                actionCandidate = null
            },
            onCopy = {
                copyBrowserMediaCandidateUrl(context, candidate.url)
                actionCandidate = null
            },
            onViewLink = {
                linkCandidate = candidate
                actionCandidate = null
            },
        )
    }

    linkCandidate?.let { candidate ->
        BrowserMediaCandidateLinkDialog(
            candidate = candidate,
            onDismiss = { linkCandidate = null },
            onCopy = { copyBrowserMediaCandidateUrl(context, candidate.url) },
        )
    }
}

@Composable
private fun BrowserMediaCandidateHeader(
    candidateCount: Int,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KiyoriSemanticIconBadge(
            imageVector = Icons.Filled.VideoLibrary,
            tone = KiyoriSemanticTone.CYAN,
            contentDescription = null,
            containerSize = 34.dp,
            iconSize = 19.dp,
            shape = RoundedCornerShape(10.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = "视频资源",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "当前网页 · $candidateCount 个视频",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭",
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun BrowserMediaFormatFilters(
    candidates: List<WebSessionBrowserMediaCandidate>,
    orderedFormats: List<BrowserMediaCandidateVideoFormat>,
    selectedFormat: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        BrowserMediaFormatChip(
            label = "全部",
            count = candidates.size,
            selected = selectedFormat == ALL_VIDEO_FORMATS,
            onClick = { onSelect(ALL_VIDEO_FORMATS) },
        )
        orderedFormats.forEach { format ->
            BrowserMediaFormatChip(
                label = format.displayName,
                count = candidates.count { it.videoFormat == format },
                selected = selectedFormat == format.name,
                onClick = { onSelect(format.name) },
            )
        }
    }
}

@Composable
private fun BrowserMediaFormatChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cyanColors = KiyoriSemanticTone.CYAN.resolveColors()
    Surface(
        modifier = Modifier.height(32.dp).clickable(role = Role.Button, onClick = onClick),
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
                if (selected) {
                    cyanColors.icon
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
    ) {
        Box(modifier = Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "$label $count",
                color =
                    if (selected) {
                        cyanColors.icon
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserMediaCandidateCard(
    candidate: WebSessionBrowserMediaCandidate,
    onOpenActions: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    val cyanColors = KiyoriSemanticTone.CYAN.resolveColors()
    val greenColors = KiyoriSemanticTone.GREEN.resolveColors()
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    role = Role.Button,
                    onClick = onOpenActions,
                    onLongClick = onOpenActions,
                ),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrowserMediaLabel(
                    text = candidate.videoFormat.displayName,
                    foreground = cyanColors.icon,
                    background = cyanColors.container,
                )
                if (candidate.isRecommended) {
                    Spacer(modifier = Modifier.width(6.dp))
                    BrowserMediaLabel(
                        text = "推荐",
                        foreground = greenColors.icon,
                        background = greenColors.container,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = browserMediaDurationLabel(candidate),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = candidate.url,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            val rankingDetails =
                listOfNotNull(
                    candidate.qualityLabel,
                    candidate.rankingSummary.takeIf(String::isNotBlank),
                ).joinToString(" · ")
            if (rankingDetails.isNotBlank()) {
                Text(
                    text = rankingDetails,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(
                    onClick = onDownload,
                    enabled = candidate.downloadReady,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 11.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(text = "下载", fontSize = 12.sp)
                }
                Button(
                    onClick = onPlay,
                    enabled = candidate.directPlaybackReady,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(text = "播放", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BrowserMediaLabel(
    text: String,
    foreground: Color,
    background: Color,
) {
    Text(
        text = text,
        color = foreground,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        modifier =
            Modifier
                .background(background, RoundedCornerShape(5.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun BrowserMediaCandidateEmptyState() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = 10.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KiyoriSemanticIconBadge(
                imageVector = Icons.Filled.VideoLibrary,
                tone = KiyoriSemanticTone.CYAN,
                contentDescription = null,
                containerSize = 50.dp,
                iconSize = 28.dp,
            )
            Text(
                text = "当前网页暂未发现可播放视频",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun BrowserMediaCandidateActionDialog(
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCopy: () -> Unit,
    onViewLink: () -> Unit,
) {
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
            shape = WebSessionBrowserPopupShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BrowserMediaDialogTitle(
                    title = "资源操作",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
                BrowserMediaActionRow(Icons.Filled.PlayArrow, "播放资源", KiyoriSemanticTone.BLUE, onPlay)
                BrowserMediaActionRow(Icons.Filled.Download, "下载资源", KiyoriSemanticTone.GREEN, onDownload)
                BrowserMediaActionRow(Icons.Filled.ContentCopy, "复制链接", KiyoriSemanticTone.PURPLE, onCopy)
                BrowserMediaActionRow(
                    icon = Icons.Filled.Info,
                    title = "查看链接",
                    tone = KiyoriSemanticTone.CYAN,
                    onClick = onViewLink,
                    drawDivider = false,
                )
            }
        }
    }
}

@Composable
private fun BrowserMediaActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (drawDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
        }
    }
}

@Composable
private fun BrowserMediaCandidateLinkDialog(
    candidate: WebSessionBrowserMediaCandidate,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).heightIn(max = 520.dp),
            shape = WebSessionBrowserPopupShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BrowserMediaDialogTitle(
                    title = "视频链接",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
                SelectionContainer(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = candidate.url,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) {
                        Text(text = "关闭", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onCopy,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(text = "复制", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserMediaDialogTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KiyoriSemanticIconBadge(
            imageVector = Icons.Filled.VideoLibrary,
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
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun browserMediaDurationLabel(candidate: WebSessionBrowserMediaCandidate): String =
    when {
        candidate.isLive -> "时长 直播"
        candidate.durationMillis != null -> "时长 ${formatBrowserMediaDuration(candidate.durationMillis)}"
        else -> "时长 未知"
    }

private fun formatBrowserMediaDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(1L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}

private fun copyBrowserMediaCandidateUrl(
    context: Context,
    url: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("browser_video_url", url))
    Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
}
