package com.ai.assistance.operit.ui.features.chat.details

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.audit.ConversationAuditExportFormat
import com.ai.assistance.operit.data.audit.ConversationAuditLoadedPayload
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ConversationAuditEntity
import com.ai.assistance.operit.data.model.ConversationAuditEventEntity
import com.kiyori.design.theme.KiyoriSettingsTheme
import com.kiyori.design.theme.LocalKiyoriSettingsColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class ConversationDetailsTab {
    TIMELINE,
    CONVERSATION,
    RAW,
}

private data class RawAuditEntry(
    val event: ConversationAuditEventEntity,
    val json: String,
)

@Composable
fun ConversationDetailsScreen(
    audit: ConversationAuditEntity?,
    events: List<ConversationAuditEventEntity>,
    messages: List<ChatMessage>,
    storedPayloadBytes: Long,
    hasOlderEvents: Boolean,
    isLoadingOlderEvents: Boolean,
    isGenerating: Boolean,
    onEditMessage: (message: ChatMessage) -> Unit,
    onLoadPayloads: suspend (eventId: String) -> List<ConversationAuditLoadedPayload>,
    onLoadOlderEvents: () -> Unit,
    onExport: (ConversationAuditExportFormat) -> Unit,
    onAddAnnotation: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAnnotationDialog by rememberSaveable { mutableStateOf(false) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var annotationText by rememberSaveable { mutableStateOf("") }
    val tabs = remember { ConversationDetailsTab.entries }
    val providerModel =
        remember(messages) {
            messages
                .asReversed()
                .firstOrNull { message ->
                    message.sender == "ai" &&
                        (message.provider.isNotBlank() || message.modelName.isNotBlank())
                }
                ?.let { message ->
                    listOf(message.provider, message.modelName)
                        .filter(String::isNotBlank)
                        .joinToString(" / ")
                }
        }

    BackHandler {
        when {
            showExportDialog -> showExportDialog = false
            showAnnotationDialog -> {
                showAnnotationDialog = false
                annotationText = ""
            }
            else -> onClose()
        }
    }

    KiyoriSettingsTheme {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                AuditHeader(
                    audit = audit,
                    eventCount = events.size,
                    messageCount = messages.size,
                    storedPayloadBytes = storedPayloadBytes,
                    providerModel = providerModel,
                    onExport = { showExportDialog = true },
                    onAddAnnotation = { showAnnotationDialog = true },
                )
                AuditTabRow(
                    selectedTab = tabs[selectedTab],
                    onSelect = { selectedTab = tabs.indexOf(it) },
                )
                when (tabs[selectedTab]) {
                    ConversationDetailsTab.TIMELINE ->
                        AuditTimeline(
                            events = events,
                            hasOlderEvents = hasOlderEvents,
                            isLoadingOlderEvents = isLoadingOlderEvents,
                            onLoadOlderEvents = onLoadOlderEvents,
                            onLoadPayloads = onLoadPayloads,
                            modifier = Modifier.weight(1f),
                        )
                    ConversationDetailsTab.CONVERSATION ->
                        AuditConversation(
                            messages = messages,
                            isGenerating = isGenerating,
                            onEditMessage = onEditMessage,
                            modifier = Modifier.weight(1f),
                        )
                    ConversationDetailsTab.RAW ->
                        AuditRaw(
                            events = events,
                            hasOlderEvents = hasOlderEvents,
                            isLoadingOlderEvents = isLoadingOlderEvents,
                            onLoadOlderEvents = onLoadOlderEvents,
                            modifier = Modifier.weight(1f),
                        )
                }
            }
        }
    }

    if (showExportDialog) {
        KiyoriSettingsTheme {
            ExportConversationDialog(
                onDismiss = { showExportDialog = false },
                onExport = { format ->
                    showExportDialog = false
                    onExport(format)
                },
            )
        }
    }

    if (showAnnotationDialog) {
        KiyoriSettingsTheme {
            AlertDialog(
                onDismissRequest = {
                    showAnnotationDialog = false
                    annotationText = ""
                },
                title = { Text(stringResource(R.string.conversation_audit_annotation_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.conversation_audit_annotation_description),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        AuditInputField(
                            value = annotationText,
                            onValueChange = { annotationText = it },
                            placeholder =
                                stringResource(R.string.conversation_audit_annotation_content),
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = annotationText.isNotBlank(),
                        onClick = {
                            onAddAnnotation(annotationText.trim())
                            showAnnotationDialog = false
                            annotationText = ""
                        },
                    ) {
                        Text(stringResource(R.string.conversation_audit_annotation_append))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAnnotationDialog = false
                            annotationText = ""
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun AuditHeader(
    audit: ConversationAuditEntity?,
    eventCount: Int,
    messageCount: Int,
    storedPayloadBytes: Long,
    providerModel: String?,
    onExport: () -> Unit,
    onAddAnnotation: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    val context = LocalContext.current
    val status = audit?.completenessStatus ?: "BASIC"
    val statusColor =
        when (status) {
            "COMPLETE" -> Color(0xFF2E7D32)
            "IN_PROGRESS" -> colors.accent
            "PARTIAL", "BASIC" -> Color(0xFFB26A00)
            else -> MaterialTheme.colorScheme.error
        }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.conversation_details_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.conversation_audit_header_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onExport) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription =
                        stringResource(R.string.conversation_audit_export_content_description),
                    tint = colors.accent,
                )
            }
            IconButton(onClick = onAddAnnotation) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                    contentDescription =
                        stringResource(R.string.conversation_audit_annotation_content_description),
                    tint = colors.mutedIcon,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = statusColor.copy(alpha = 0.12f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector =
                            if (status == "COMPLETE") {
                                Icons.Default.CheckCircle
                            } else if (status == "IN_PROGRESS") {
                                Icons.Default.Storage
                            } else {
                                Icons.Default.WarningAmber
                            },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = completenessLabel(status),
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                    )
                }
            }
            Text(
                text =
                    stringResource(
                        R.string.conversation_audit_header_counts,
                        eventCount,
                        messageCount,
                        Formatter.formatFileSize(context, storedPayloadBytes),
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text =
                stringResource(
                    R.string.conversation_audit_header_runtime_compact,
                    providerModel
                        ?: stringResource(R.string.conversation_audit_provider_not_recorded),
                    audit?.chainHeadSha256?.take(12)
                        ?: stringResource(R.string.conversation_audit_chain_pending),
                ),
            style = MaterialTheme.typography.labelSmall,
            color = colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace,
        )
    }
    HorizontalDivider(color = colors.divider)
}

@Composable
private fun AuditTabRow(
    selectedTab: ConversationDetailsTab,
    onSelect: (ConversationDetailsTab) -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.cardBackground),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        ConversationDetailsTab.entries.forEach { tab ->
            Column(
                modifier = Modifier.weight(1f).clickable { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = tabLabel(tab),
                    modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color =
                        if (tab == selectedTab) colors.accent else colors.secondaryText,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(
                                if (tab == selectedTab) colors.accent else Color.Transparent,
                                RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                            )
                )
            }
        }
    }
    HorizontalDivider(color = colors.divider)
}

@Composable
private fun AuditSectionToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    matchingCount: Int,
    totalCount: Int,
    expanded: Boolean,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AuditSearchBar(
            value = query,
            onValueChange = onQueryChange,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.conversation_audit_match_count,
                        matchingCount,
                        totalCount,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondaryText,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = if (expanded) onCollapseAll else onExpandAll,
                contentPadding =
                    androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    stringResource(
                        if (expanded) {
                            R.string.conversation_audit_collapse_all
                        } else {
                            R.string.conversation_audit_expand_all
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun AuditSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKiyoriSettingsColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = colors.cardBackground,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = colors.mutedIcon,
                modifier = Modifier.size(20.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.primaryText),
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                },
            )
            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear),
                        tint = colors.mutedIcon,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditTimeline(
    events: List<ConversationAuditEventEntity>,
    hasOlderEvents: Boolean,
    isLoadingOlderEvents: Boolean,
    onLoadOlderEvents: () -> Unit,
    onLoadPayloads: suspend (eventId: String) -> List<ConversationAuditLoadedPayload>,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered =
        remember(events, query) {
            val normalized = query.trim()
            if (normalized.isEmpty()) {
                events
            } else {
                events.filter { event ->
                    event.eventType.contains(normalized, ignoreCase = true) ||
                        event.category.contains(normalized, ignoreCase = true) ||
                        event.actor.contains(normalized, ignoreCase = true) ||
                        event.summary.contains(normalized, ignoreCase = true) ||
                        event.eventId.contains(normalized, ignoreCase = true)
                }
            }
        }
    val listState = rememberLazyListState()
    val loadedPayloads = remember { mutableStateMapOf<String, List<ConversationAuditLoadedPayload>>() }
    val loadingPayloads = remember { mutableStateMapOf<String, Boolean>() }
    val payloadErrors = remember { mutableStateMapOf<String, String>() }
    var expandedEventIds by remember { mutableStateOf(emptySet<String>()) }
    var previousLastSequence by remember { mutableLongStateOf(0L) }
    var unreadEventCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val olderItemCount = if (hasOlderEvents || isLoadingOlderEvents) 1 else 0

    fun requestPayloads(eventId: String) {
        if (loadedPayloads[eventId] != null || loadingPayloads[eventId] == true) return
        loadingPayloads[eventId] = true
        payloadErrors.remove(eventId)
        scope.launch {
            try {
                loadedPayloads[eventId] = onLoadPayloads(eventId)
            } catch (error: Exception) {
                payloadErrors[eventId] = error.stackTraceToString()
            } finally {
                loadingPayloads[eventId] = false
            }
        }
    }

    BackHandler(enabled = expandedEventIds.isNotEmpty() || query.isNotBlank()) {
        if (expandedEventIds.isNotEmpty()) {
            expandedEventIds = emptySet()
        } else {
            query = ""
        }
    }

    LaunchedEffect(events.lastOrNull()?.sequenceNumber, filtered.size) {
        val currentLastSequence = events.lastOrNull()?.sequenceNumber ?: 0L
        val newCount =
            if (previousLastSequence == 0L) {
                events.size
            } else {
                events.count { event -> event.sequenceNumber > previousLastSequence }
            }
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        val lastContentIndex = (filtered.lastIndex + olderItemCount).coerceAtLeast(0)
        val wasAtBottom =
            previousLastSequence == 0L || lastVisible == null || lastVisible >= lastContentIndex - 1
        if (filtered.isNotEmpty() && (previousLastSequence == 0L || newCount > 0 && wasAtBottom)) {
            listState.scrollToItem(lastContentIndex)
            unreadEventCount = 0
        } else if (newCount > 0) {
            unreadEventCount += newCount
        }
        previousLastSequence = currentLastSequence
    }

    Column(modifier = modifier.fillMaxSize()) {
        AuditSectionToolbar(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.conversation_audit_search_timeline),
            matchingCount = filtered.size,
            totalCount = events.size,
            expanded = filtered.isNotEmpty() && filtered.all { it.eventId in expandedEventIds },
            onExpandAll = { expandedEventIds = expandedEventIds + filtered.map { it.eventId } },
            onCollapseAll = { expandedEventIds = expandedEventIds - filtered.map { it.eventId }.toSet() },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (filtered.isEmpty()) {
                EmptyAuditState(
                    text =
                        if (events.isEmpty()) {
                            stringResource(R.string.conversation_audit_empty)
                        } else {
                            stringResource(R.string.conversation_audit_no_matches)
                        }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(bottom = 18.dp),
                ) {
                    if (hasOlderEvents || isLoadingOlderEvents) {
                        item(key = "load-older-events") {
                            TextButton(
                                enabled = !isLoadingOlderEvents,
                                onClick = onLoadOlderEvents,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            ) {
                                if (isLoadingOlderEvents) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.size(8.dp))
                                }
                                Text(
                                    stringResource(
                                        if (isLoadingOlderEvents) {
                                            R.string.conversation_audit_loading_older
                                        } else {
                                            R.string.conversation_audit_load_older
                                        }
                                    )
                                )
                            }
                        }
                    }
                    items(
                        items = filtered,
                        key = { event -> event.eventId },
                    ) { event ->
                        AuditEventRow(
                            event = event,
                            expanded = event.eventId in expandedEventIds,
                            loading = loadingPayloads[event.eventId] == true,
                            payloads = loadedPayloads[event.eventId],
                            payloadError = payloadErrors[event.eventId],
                            onRequestPayloads = { requestPayloads(event.eventId) },
                            onClick = {
                                expandedEventIds =
                                    if (event.eventId in expandedEventIds) {
                                        expandedEventIds - event.eventId
                                    } else {
                                        expandedEventIds + event.eventId
                                    }
                            },
                        )
                    }
                }
            }
            if (unreadEventCount > 0) {
                val colors = LocalKiyoriSettingsColors.current
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = colors.accent,
                    shadowElevation = 4.dp,
                    onClick = {
                        scope.launch {
                            if (filtered.isNotEmpty()) {
                                listState.animateScrollToItem(filtered.lastIndex + olderItemCount)
                            }
                            unreadEventCount = 0
                        }
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = colors.accentContent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text =
                                pluralStringResource(
                                    R.plurals.conversation_audit_new_events,
                                    unreadEventCount,
                                    unreadEventCount,
                                ),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accentContent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditEventRow(
    event: ConversationAuditEventEntity,
    expanded: Boolean,
    loading: Boolean,
    payloads: List<ConversationAuditLoadedPayload>?,
    payloadError: String?,
    onRequestPayloads: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    LaunchedEffect(expanded, event.eventId) {
        if (expanded) onRequestPayloads()
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = event.sequenceNumber.toString().padStart(5, '0'),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = colors.accent,
                )
                Text(
                    text = "${event.category} / ${event.eventType}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.primaryText,
                )
                Text(
                    text = formatTime(event.occurredAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.mutedIcon,
                    modifier = Modifier.size(18.dp),
                )
            }
            SelectionContainer {
                Text(
                    text = event.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primaryText,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (event.visibility == "TOMBSTONE" || event.terminalState == "FAILED") {
                Text(
                    text =
                        listOfNotNull(
                                event.visibility.takeIf { value -> value == "TOMBSTONE" },
                                event.terminalState,
                            )
                            .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (expanded) {
                HorizontalDivider(color = colors.divider)
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text =
                                "eventId=${event.eventId}\n" +
                                    "actor=${event.actor}\n" +
                                    "eventSha256=${event.eventSha256}\n" +
                                    "previousEventSha256=${event.previousEventSha256}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = colors.secondaryText,
                        )
                        when {
                            loading ->
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            payloadError != null ->
                                Text(
                                    text =
                                        stringResource(
                                            R.string.conversation_audit_payload_load_failed,
                                            payloadError,
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontFamily = FontFamily.Monospace,
                                )
                            payloads.isNullOrEmpty() ->
                                Text(
                                    stringResource(R.string.conversation_audit_no_payload),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText,
                                )
                            else ->
                                payloads.forEach { payload ->
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.conversation_audit_payload_metadata,
                                                payload.ordinal,
                                                payload.label,
                                                payload.mediaType,
                                                payload.bytes.size,
                                            ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.primaryText,
                                    )
                                    Text(
                                        text =
                                            if (payload.encoding == "utf-8") {
                                                payload.bytes.toString(Charsets.UTF_8)
                                            } else {
                                                stringResource(R.string.conversation_audit_binary_payload)
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = colors.secondaryText,
                                    )
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditConversation(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    onEditMessage: (message: ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expandedMessageKeys by remember { mutableStateOf(emptySet<String>()) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingText by remember { mutableStateOf("") }
    val visibleMessages =
        remember(messages) {
            messages.filter { message -> message.sender == "user" || message.sender == "ai" }
        }
    val filtered =
        remember(visibleMessages, query) {
            val normalized = query.trim()
            if (normalized.isEmpty()) {
                visibleMessages
            } else {
                visibleMessages.filter { message ->
                    message.sender.contains(normalized, ignoreCase = true) ||
                        message.roleName.contains(normalized, ignoreCase = true) ||
                        message.provider.contains(normalized, ignoreCase = true) ||
                        message.modelName.contains(normalized, ignoreCase = true) ||
                        message.content.contains(normalized, ignoreCase = true) ||
                        message.timestamp.toString().contains(normalized)
                }
            }
        }
    val keyOf: (ChatMessage) -> String = { message ->
        "${message.timestamp}:${message.selectedVariantIndex}:${message.sender}"
    }

    BackHandler(
        enabled = editingMessage != null || expandedMessageKeys.isNotEmpty() || query.isNotBlank()
    ) {
        when {
            editingMessage != null -> {
                editingMessage = null
                editingText = ""
            }
            expandedMessageKeys.isNotEmpty() -> expandedMessageKeys = emptySet()
            else -> query = ""
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AuditSectionToolbar(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.conversation_audit_search_conversation),
            matchingCount = filtered.size,
            totalCount = visibleMessages.size,
            expanded = filtered.isNotEmpty() && filtered.all { keyOf(it) in expandedMessageKeys },
            onExpandAll = { expandedMessageKeys = expandedMessageKeys + filtered.map(keyOf) },
            onCollapseAll = { expandedMessageKeys = expandedMessageKeys - filtered.map(keyOf).toSet() },
        )
        if (filtered.isEmpty()) {
            EmptyAuditState(
                text =
                    if (visibleMessages.isEmpty()) {
                        stringResource(R.string.conversation_audit_conversation_empty)
                    } else {
                        stringResource(R.string.conversation_audit_no_matches)
                    }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 18.dp),
            ) {
                items(
                    items = filtered,
                    key = keyOf,
                ) { message ->
                    val messageKey = keyOf(message)
                    AuditMessageRow(
                        message = message,
                        expanded = messageKey in expandedMessageKeys,
                        isGenerating = isGenerating,
                        onEdit = {
                            editingMessage = message
                            editingText = message.content
                        },
                        onClick = {
                            expandedMessageKeys =
                                if (messageKey in expandedMessageKeys) {
                                    expandedMessageKeys - messageKey
                                } else {
                                    expandedMessageKeys + messageKey
                                }
                        },
                    )
                }
            }
        }
    }

    editingMessage?.let { original ->
        AlertDialog(
            onDismissRequest = {
                editingMessage = null
                editingText = ""
            },
            title = { Text(stringResource(R.string.edit_message)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.conversation_audit_edit_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    AuditInputField(
                        value = editingText,
                        onValueChange = { editingText = it },
                        placeholder = stringResource(R.string.conversation_audit_current_version),
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editingText != original.content,
                    onClick = {
                        onEditMessage(original.copy(content = editingText))
                        editingMessage = null
                        editingText = ""
                    },
                ) {
                    Text(stringResource(R.string.conversation_audit_save_revision))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingMessage = null
                        editingText = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AuditMessageRow(
    message: ChatMessage,
    expanded: Boolean,
    isGenerating: Boolean,
    onEdit: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            if (message.sender == "user") {
                                stringResource(R.string.conversation_audit_speaker_user)
                            } else {
                                stringResource(
                                    R.string.conversation_audit_speaker_ai_variant,
                                    message.selectedVariantIndex,
                                )
                            },
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.primaryText,
                    )
                    Text(
                        text =
                            stringResource(
                                R.string.conversation_audit_message_index,
                                message.timestamp,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryText,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (expanded) {
                    IconButton(enabled = !isGenerating, onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_message),
                            tint = colors.accent,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.mutedIcon,
                    modifier = Modifier.size(18.dp),
                )
            }
            SelectionContainer {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = colors.primaryText,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (expanded) {
                HorizontalDivider(color = colors.divider)
                Text(
                    text =
                        stringResource(
                            R.string.conversation_audit_message_token_metadata,
                            message.timestamp,
                            message.inputTokens,
                            message.outputTokens,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText,
                )
                if (message.provider.isNotBlank() || message.modelName.isNotBlank()) {
                    Text(
                        text =
                            listOf(message.provider, message.modelName)
                                .filter(String::isNotBlank)
                                .joinToString(" / "),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryText,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditRaw(
    events: List<ConversationAuditEventEntity>,
    hasOlderEvents: Boolean,
    isLoadingOlderEvents: Boolean,
    onLoadOlderEvents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expandedEventIds by remember { mutableStateOf(emptySet<String>()) }
    val entries =
        remember(events) {
            events.map { event -> RawAuditEntry(event = event, json = eventToJson(event)) }
        }
    val filtered =
        remember(entries, query) {
            val normalized = query.trim()
            if (normalized.isEmpty()) {
                entries
            } else {
                entries.filter { entry ->
                    entry.json.contains(normalized, ignoreCase = true) ||
                        entry.event.eventType.contains(normalized, ignoreCase = true) ||
                        entry.event.summary.contains(normalized, ignoreCase = true)
                }
            }
        }

    BackHandler(enabled = expandedEventIds.isNotEmpty() || query.isNotBlank()) {
        if (expandedEventIds.isNotEmpty()) {
            expandedEventIds = emptySet()
        } else {
            query = ""
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AuditSectionToolbar(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.conversation_audit_search_raw),
            matchingCount = filtered.size,
            totalCount = entries.size,
            expanded = filtered.isNotEmpty() && filtered.all { it.event.eventId in expandedEventIds },
            onExpandAll = {
                expandedEventIds = expandedEventIds + filtered.map { it.event.eventId }
            },
            onCollapseAll = {
                expandedEventIds = expandedEventIds - filtered.map { it.event.eventId }.toSet()
            },
        )
        if (filtered.isEmpty()) {
            EmptyAuditState(
                text =
                    if (entries.isEmpty()) {
                        stringResource(R.string.conversation_audit_empty)
                    } else {
                        stringResource(R.string.conversation_audit_no_matches)
                    }
            )
        } else {
            SelectionContainer {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 18.dp),
                ) {
                    if (hasOlderEvents || isLoadingOlderEvents) {
                        item(key = "load-older-raw-events") {
                            TextButton(
                                enabled = !isLoadingOlderEvents,
                                onClick = onLoadOlderEvents,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            ) {
                                if (isLoadingOlderEvents) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.size(8.dp))
                                }
                                Text(
                                    stringResource(
                                        if (isLoadingOlderEvents) {
                                            R.string.conversation_audit_loading_older
                                        } else {
                                            R.string.conversation_audit_load_older
                                        }
                                    )
                                )
                            }
                        }
                    }
                    itemsIndexed(
                        items = filtered,
                        key = { _, entry -> entry.event.eventId },
                    ) { _, entry ->
                        val expanded = entry.event.eventId in expandedEventIds
                        val colors = LocalKiyoriSettingsColors.current
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                expandedEventIds =
                                    if (expanded) {
                                        expandedEventIds - entry.event.eventId
                                    } else {
                                        expandedEventIds + entry.event.eventId
                                    }
                            },
                            color =
                                if (expanded) colors.cardBackground else colors.pageBackground,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = entry.event.sequenceNumber.toString().padStart(5, ' '),
                                    color = colors.secondaryText,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(end = 10.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text =
                                            "${entry.event.category} / ${entry.event.eventType}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.accent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (expanded) prettyJson(entry.json) else entry.json,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.primaryText,
                                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(
                                    imageVector =
                                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = colors.mutedIcon,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportConversationDialog(
    onDismiss: () -> Unit,
    onExport: (ConversationAuditExportFormat) -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conversation_audit_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.conversation_audit_export_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                )
                ExportOption(
                    title = stringResource(R.string.conversation_audit_export_ai_title),
                    description = stringResource(R.string.conversation_audit_export_ai_description),
                    onClick = {
                        onExport(ConversationAuditExportFormat.AI_DIAGNOSTICS_MARKDOWN)
                    },
                )
                ExportOption(
                    title = stringResource(R.string.conversation_audit_export_package_title),
                    description =
                        stringResource(R.string.conversation_audit_export_package_description),
                    onClick = {
                        onExport(ConversationAuditExportFormat.COMPLETE_AUDIT_PACKAGE)
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ExportOption(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = colors.pageBackground,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.primaryText)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
        }
    }
}

@Composable
private fun AuditInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKiyoriSettingsColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = colors.pageBackground,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize().padding(14.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.primaryText),
            decorationBox = { innerTextField ->
                if (value.isBlank()) {
                    Text(placeholder, color = colors.secondaryText)
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun EmptyAuditState(text: String) {
    val colors = LocalKiyoriSettingsColors.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.padding(28.dp),
            color = colors.secondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun tabLabel(tab: ConversationDetailsTab): String =
    when (tab) {
        ConversationDetailsTab.TIMELINE -> stringResource(R.string.conversation_audit_tab_timeline)
        ConversationDetailsTab.CONVERSATION ->
            stringResource(R.string.conversation_audit_tab_conversation)
        ConversationDetailsTab.RAW -> stringResource(R.string.conversation_audit_tab_raw)
    }

@Composable
private fun completenessLabel(status: String): String =
    when (status) {
        "COMPLETE" -> stringResource(R.string.conversation_audit_completeness_complete)
        "IN_PROGRESS" -> stringResource(R.string.conversation_audit_completeness_in_progress)
        "PARTIAL" -> stringResource(R.string.conversation_audit_completeness_partial)
        "BASIC" -> stringResource(R.string.conversation_audit_completeness_basic)
        "RECORDING_INTERRUPTED" ->
            stringResource(R.string.conversation_audit_completeness_interrupted)
        "CONTENT_CORRUPTED" ->
            stringResource(R.string.conversation_audit_completeness_corrupted)
        "SOURCE_UNVERIFIED" ->
            stringResource(R.string.conversation_audit_completeness_source_unverified)
        "KEY_UNAVAILABLE" ->
            stringResource(R.string.conversation_audit_completeness_key_unavailable)
        else -> status
    }

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

private fun eventToJson(event: ConversationAuditEventEntity): String =
    JSONObject()
        .put("eventId", event.eventId)
        .put("chatId", event.chatId)
        .put("sequenceNumber", event.sequenceNumber)
        .put("occurredAt", event.occurredAt)
        .put("recordedAt", event.recordedAt)
        .put("category", event.category)
        .put("eventType", event.eventType)
        .put("actor", event.actor)
        .put("summary", event.summary)
        .put("messageTimestamp", event.messageTimestamp ?: JSONObject.NULL)
        .put("variantIndex", event.variantIndex ?: JSONObject.NULL)
        .put("localExecutionId", event.localExecutionId ?: JSONObject.NULL)
        .put("providerCallId", event.providerCallId ?: JSONObject.NULL)
        .put("parentEventId", event.parentEventId ?: JSONObject.NULL)
        .put("sourceChatId", event.sourceChatId ?: JSONObject.NULL)
        .put("sourceEventId", event.sourceEventId ?: JSONObject.NULL)
        .put("previousEventSha256", event.previousEventSha256)
        .put("eventSha256", event.eventSha256)
        .put("visibility", event.visibility)
        .put("terminalState", event.terminalState ?: JSONObject.NULL)
        .toString()

private fun prettyJson(json: String): String = JSONObject(json).toString(2)
