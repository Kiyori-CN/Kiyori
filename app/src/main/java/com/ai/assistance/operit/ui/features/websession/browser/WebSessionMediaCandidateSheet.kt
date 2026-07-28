package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserMediaCandidateDiscoverySource
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserMediaCandidateUrlEvidence
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserMediaCandidate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun WebSessionMediaCandidateSheet(
    candidates: List<WebSessionBrowserMediaCandidate>,
    onPlay: (String) -> Boolean,
    onDownload: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "视频资源",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "当前网页 · ${candidates.size} 个候选",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
        }

        if (candidates.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("当前网页暂未发现视频资源", fontWeight = FontWeight.Medium)
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(candidates, key = WebSessionBrowserMediaCandidate::id) { candidate ->
                MediaCandidateCard(candidate, onPlay, onDownload)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MediaCandidateCard(
    candidate: WebSessionBrowserMediaCandidate,
    onPlay: (String) -> Boolean,
    onDownload: (String) -> Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = mediaCandidateKind(candidate),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                candidate.mimeType?.let { mime ->
                    Text(
                        text = mime,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } ?: Spacer(Modifier.weight(1f))
                Text(
                    text = formatMediaCandidateTime(candidate.lastDiscoveredAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Text(
                text = candidate.url,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
            if (!candidate.directPlaybackReady) {
                Text(
                    text = if (candidate.isBlob) "blob/MSE 仅作网页线索，不能直接播放或下载" else "当前仅有 MIME/请求证据，未将接口地址冒充视频直链",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                OutlinedButton(
                    onClick = { onDownload(candidate.id) },
                    enabled = candidate.downloadReady,
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("下载")
                }
                Button(
                    onClick = { onPlay(candidate.id) },
                    enabled = candidate.directPlaybackReady,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("播放")
                }
            }
        }
    }
}

private fun mediaCandidateKind(candidate: WebSessionBrowserMediaCandidate): String =
    when {
        candidate.isBlob -> "网页线索"
        candidate.urlEvidence == BrowserMediaCandidateUrlEvidence.HLS_MANIFEST -> "HLS"
        candidate.urlEvidence == BrowserMediaCandidateUrlEvidence.DASH_MANIFEST -> "DASH"
        candidate.urlEvidence == BrowserMediaCandidateUrlEvidence.VIDEO_FILE -> "视频文件"
        candidate.discoverySources.any { source ->
            source == BrowserMediaCandidateDiscoverySource.DOM_CURRENT_SRC ||
                source == BrowserMediaCandidateDiscoverySource.DOM_SRC ||
                source == BrowserMediaCandidateDiscoverySource.DOM_SOURCE ||
                source == BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT
        } -> "网页播放器"
        else -> "请求线索"
    }

private fun formatMediaCandidateTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
