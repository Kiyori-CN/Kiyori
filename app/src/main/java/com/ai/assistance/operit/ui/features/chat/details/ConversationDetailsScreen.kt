package com.ai.assistance.operit.ui.features.chat.details

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.ai.assistance.operit.data.audit.ConversationAuditLoadedPayload
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ConversationAuditEntity
import com.ai.assistance.operit.data.model.ConversationAuditEventEntity
import com.ai.assistance.operit.R
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
    onExport: () -> Unit,
    onAddAnnotation: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAnnotationDialog by rememberSaveable { mutableStateOf(false) }
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
                        .filter { value -> value.isNotBlank() }
                        .joinToString(" / ")
                }
        }

    BackHandler {
        if (showAnnotationDialog) {
            showAnnotationDialog = false
            annotationText = ""
        } else {
            onClose()
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            AuditHeader(
                audit = audit,
                loadedEventCount = events.size,
                storedPayloadBytes = storedPayloadBytes,
                providerModel = providerModel,
                onExport = onExport,
                onAddAnnotation = { showAnnotationDialog = true },
            )
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text =
                                    when (tab) {
                                        ConversationDetailsTab.TIMELINE ->
                                            stringResource(
                                                R.string.conversation_audit_tab_timeline
                                            )
                                        ConversationDetailsTab.CONVERSATION ->
                                            stringResource(
                                                R.string.conversation_audit_tab_conversation
                                            )
                                        ConversationDetailsTab.RAW ->
                                            stringResource(R.string.conversation_audit_tab_raw)
                                    }
                            )
                        },
                    )
                }
            }

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

    if (showAnnotationDialog) {
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
                    OutlinedTextField(
                        value = annotationText,
                        onValueChange = { annotationText = it },
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        label = {
                            Text(stringResource(R.string.conversation_audit_annotation_content))
                        },
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

@Composable
private fun AuditHeader(
    audit: ConversationAuditEntity?,
    loadedEventCount: Int,
    storedPayloadBytes: Long,
    providerModel: String?,
    onExport: () -> Unit,
    onAddAnnotation: () -> Unit,
) {
    val context = LocalContext.current
    val status = audit?.completenessStatus ?: "BASIC"
    val statusColor =
        when (status) {
            "COMPLETE" -> Color(0xFF2E7D32)
            "IN_PROGRESS" -> Color(0xFF1565C0)
            "PARTIAL", "BASIC" -> Color(0xFFEF6C00)
            else -> MaterialTheme.colorScheme.error
        }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.conversation_details_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onExport) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription =
                        stringResource(R.string.conversation_audit_export_content_description),
                )
            }
            IconButton(onClick = onAddAnnotation) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                    contentDescription =
                        stringResource(R.string.conversation_audit_annotation_content_description),
                )
            }
            AssistChip(
                onClick = {},
                label = { Text(completenessLabel(status)) },
                leadingIcon = {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .padding(0.dp),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = statusColor,
                        ) {}
                    }
                },
            )
        }
        Text(
            text =
                stringResource(
                    R.string.conversation_audit_header_summary,
                    audit?.eventCount ?: 0L,
                    loadedEventCount,
                    audit
                        ?.chainHeadSha256
                        ?.take(12)
                        ?.ifBlank {
                            stringResource(R.string.conversation_audit_chain_empty)
                        }
                        ?: stringResource(R.string.conversation_audit_chain_pending),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text =
                stringResource(
                    R.string.conversation_audit_header_runtime,
                    providerModel
                        ?: stringResource(R.string.conversation_audit_provider_not_recorded),
                    Formatter.formatFileSize(context, storedPayloadBytes),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
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
                        event.summary.contains(normalized, ignoreCase = true) ||
                        event.eventId.contains(normalized, ignoreCase = true)
                }
            }
        }
    val listState = rememberLazyListState()
    val loadedPayloads = remember { mutableStateMapOf<String, List<ConversationAuditLoadedPayload>>() }
    val loadingPayloads = remember { mutableStateMapOf<String, Boolean>() }
    val payloadErrors = remember { mutableStateMapOf<String, String>() }
    var expandedEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var previousLastSequence by remember { mutableStateOf(0L) }
    var unreadEventCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = expandedEventId != null || query.isNotBlank()) {
        if (expandedEventId != null) {
            expandedEventId = null
        } else {
            query = ""
        }
    }

    LaunchedEffect(events.lastOrNull()?.sequenceNumber, query) {
        val currentLastSequence = events.lastOrNull()?.sequenceNumber ?: 0L
        val newCount =
            if (previousLastSequence == 0L) {
                events.size
            } else {
                events.count { event -> event.sequenceNumber > previousLastSequence }
            }
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        val wasAtBottom =
            previousLastSequence == 0L ||
                lastVisible == null ||
                lastVisible >= (filtered.lastIndex - 1).coerceAtLeast(0)
        if (filtered.isNotEmpty() && (previousLastSequence == 0L || newCount > 0 && wasAtBottom)) {
            listState.scrollToItem(filtered.lastIndex)
            unreadEventCount = 0
        } else if (newCount > 0) {
            unreadEventCount += newCount
        }
        previousLastSequence = currentLastSequence
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = {
                Text(stringResource(R.string.conversation_audit_search_loaded_events))
            },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (filtered.isEmpty()) {
                Text(
                    text =
                        if (events.isEmpty()) {
                            stringResource(R.string.conversation_audit_empty)
                        } else {
                            stringResource(R.string.conversation_audit_no_matches)
                        },
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (hasOlderEvents || isLoadingOlderEvents) {
                        item(key = "load-older-events") {
                            TextButton(
                                enabled = !isLoadingOlderEvents,
                                onClick = onLoadOlderEvents,
                                modifier = Modifier.fillMaxWidth(),
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
                        key = { _, event -> event.eventId },
                    ) { _, event ->
                        AuditEventRow(
                            event = event,
                            expanded = expandedEventId == event.eventId,
                            loading = loadingPayloads[event.eventId] == true,
                            payloads = loadedPayloads[event.eventId],
                            payloadError = payloadErrors[event.eventId],
                            onClick = {
                                if (expandedEventId == event.eventId) {
                                    expandedEventId = null
                                } else {
                                    expandedEventId = event.eventId
                                    if (
                                        loadedPayloads[event.eventId] == null &&
                                            loadingPayloads[event.eventId] != true
                                    ) {
                                        loadingPayloads[event.eventId] = true
                                        payloadErrors.remove(event.eventId)
                                        scope.launch {
                                            try {
                                                loadedPayloads[event.eventId] =
                                                    onLoadPayloads(event.eventId)
                                            } catch (error: Exception) {
                                                payloadErrors[event.eventId] =
                                                    error.stackTraceToString()
                                            } finally {
                                                loadingPayloads[event.eventId] = false
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
            if (unreadEventCount > 0) {
                AssistChip(
                    onClick = {
                        scope.launch {
                            if (filtered.isNotEmpty()) {
                                listState.animateScrollToItem(filtered.lastIndex)
                            }
                            unreadEventCount = 0
                        }
                    },
                    label = {
                        Text(
                            pluralStringResource(
                                R.plurals.conversation_audit_new_events,
                                unreadEventCount,
                                unreadEventCount,
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
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
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${event.category} / ${event.eventType}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatTime(event.occurredAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SelectionContainer {
                Text(
                    text = event.summary,
                    style = MaterialTheme.typography.bodyMedium,
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text =
                                "eventId=${event.eventId}\n" +
                                    "eventSha256=${event.eventSha256}\n" +
                                    "previousEventSha256=${event.previousEventSha256}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        when {
                            loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    )
                                    Text(
                                        text =
                                            if (payload.encoding == "utf-8") {
                                                payload.bytes.toString(Charsets.UTF_8)
                                            } else {
                                                stringResource(
                                                    R.string.conversation_audit_binary_payload
                                                )
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
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
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf("") }
    val visibleMessages =
        remember(messages) {
            messages.withIndex().filter { indexed ->
                indexed.value.sender == "user" || indexed.value.sender == "ai"
            }
        }

    BackHandler(enabled = editingIndex != null) {
        editingIndex = null
        editingText = ""
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(
            items = visibleMessages,
            key = { _, indexed -> "${indexed.value.timestamp}:${indexed.value.selectedVariantIndex}" },
        ) { _, indexed ->
            val message = indexed.value
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            enabled = !isGenerating,
                            onClick = {
                                editingIndex = indexed.index
                                editingText = message.content
                            },
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.edit_message),
                            )
                        }
                    }
                    SelectionContainer {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Text(
                        text =
                            stringResource(
                                R.string.conversation_audit_message_token_metadata,
                                message.timestamp,
                                message.inputTokens,
                                message.outputTokens,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    val index = editingIndex
    if (index != null) {
        val original = messages.getOrNull(index)
        if (original != null) {
            AlertDialog(
                onDismissRequest = { editingIndex = null },
                title = { Text(stringResource(R.string.edit_message)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.conversation_audit_edit_description),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = editingText,
                            onValueChange = { editingText = it },
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            label = {
                                Text(stringResource(R.string.conversation_audit_current_version))
                            },
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = editingText != original.content,
                        onClick = {
                            onEditMessage(original.copy(content = editingText))
                            editingIndex = null
                        },
                    ) {
                        Text(stringResource(R.string.conversation_audit_save_revision))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingIndex = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
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
    val lines =
        remember(events) {
            events.map { event -> eventToJson(event) }
        }
    val filtered =
        remember(lines, query) {
            if (query.isBlank()) {
                lines.withIndex().toList()
            } else {
                lines.withIndex().filter { indexed ->
                    indexed.value.contains(query, ignoreCase = true)
                }
            }
        }

    BackHandler(enabled = query.isNotBlank()) {
        query = ""
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text(stringResource(R.string.conversation_audit_search_raw)) },
        )
        SelectionContainer {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (hasOlderEvents || isLoadingOlderEvents) {
                    item(key = "load-older-raw-events") {
                        TextButton(
                            enabled = !isLoadingOlderEvents,
                            onClick = onLoadOlderEvents,
                            modifier = Modifier.fillMaxWidth(),
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
                itemsIndexed(filtered) { _, indexed ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = (indexed.index + 1).toString().padStart(5, ' ') + "  ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = indexed.value,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
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
