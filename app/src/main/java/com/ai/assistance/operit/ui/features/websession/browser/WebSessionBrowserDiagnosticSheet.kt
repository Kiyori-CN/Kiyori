package com.ai.assistance.operit.ui.features.websession.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDiagnosticCategory
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDiagnosticEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDiagnosticLevel
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDiagnosticScope
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.filterBrowserDiagnosticEntries
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.formatBrowserDiagnosticReport
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.design.theme.KiyoriUiShapes
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun WebSessionBrowserDiagnosticSheet(
    entries: List<BrowserDiagnosticEntry>,
    activeSessionId: String?,
    onClear: (BrowserDiagnosticScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exportSuccessMessage = stringResource(R.string.export_success)
    val exportFailedMessage = stringResource(R.string.export_failed)
    val copiedToClipboardMessage = stringResource(R.string.copied_to_clipboard)
    var scope by rememberSaveable { mutableStateOf(BrowserDiagnosticScope.CURRENT_SESSION) }
    var level by rememberSaveable { mutableStateOf<BrowserDiagnosticLevel?>(null) }
    var category by rememberSaveable { mutableStateOf<BrowserDiagnosticCategory?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val filteredEntries =
        remember(entries, scope, activeSessionId, level, category, query) {
            filterBrowserDiagnosticEntries(
                entries = entries,
                scope = scope,
                activeSessionId = activeSessionId,
                level = level,
                category = category,
                query = query,
            )
        }
    val scopeEntries =
        remember(entries, scope, activeSessionId) {
            filterBrowserDiagnosticEntries(
                entries = entries,
                scope = scope,
                activeSessionId = activeSessionId,
                level = null,
                category = null,
                query = "",
            )
        }
    val report = remember(filteredEntries) { formatBrowserDiagnosticReport(filteredEntries) }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt").use { output ->
                        requireNotNull(output) { "Unable to open the selected diagnostic export document" }
                        output.writer(Charsets.UTF_8).use { writer -> writer.write(report) }
                    }
                }.onSuccess {
                    Toast.makeText(context, exportSuccessMessage, Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    AppLogger.e(DIAGNOSTIC_SHEET_TAG, "Failed to export browser diagnostics", error)
                    Toast.makeText(context, exportFailedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        WebSessionDrawerHeader(
            title = stringResource(R.string.web_session_diagnostics),
            leadingIcon = Icons.Filled.Info,
            tone = WebSessionBrowserMenuTone.DIAGNOSTICS,
            countText = scopeEntries.size.toString(),
            titleTakesRemainingSpace = true,
            actions = {
                IconButton(
                    enabled = filteredEntries.isNotEmpty(),
                    modifier = Modifier.size(40.dp),
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Kiyori browser diagnostics", report),
                        )
                        Toast.makeText(
                            context,
                            copiedToClipboardMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                    )
                }
                IconButton(
                    enabled = filteredEntries.isNotEmpty(),
                    modifier = Modifier.size(40.dp),
                    onClick = { exportLauncher.launch(browserDiagnosticExportFileName()) },
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileDownload,
                        contentDescription = stringResource(R.string.export),
                    )
                }
                IconButton(
                    enabled = scopeEntries.isNotEmpty(),
                    modifier = Modifier.size(40.dp),
                    onClick = { onClear(scope) },
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = stringResource(R.string.web_session_diagnostics_clear),
                    )
                }
            },
        )

        DiagnosticSelectorRow(
            labels =
                listOf(
                    stringResource(R.string.web_session_diagnostics_scope_current),
                    stringResource(R.string.web_session_diagnostics_scope_all),
                ),
            selectedIndex = scope.ordinal,
            onSelect = { index -> scope = BrowserDiagnosticScope.entries[index] },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        DiagnosticSelectorRow(
            labels =
                listOf(
                    stringResource(R.string.web_session_diagnostics_filter_all),
                    stringResource(R.string.web_session_diagnostics_filter_warning),
                    stringResource(R.string.web_session_diagnostics_filter_error),
                ),
            selectedIndex =
                when (level) {
                    null -> 0
                    BrowserDiagnosticLevel.WARNING -> 1
                    BrowserDiagnosticLevel.ERROR -> 2
                    BrowserDiagnosticLevel.INFO -> 0
                },
            onSelect = { index ->
                level =
                    when (index) {
                        1 -> BrowserDiagnosticLevel.WARNING
                        2 -> BrowserDiagnosticLevel.ERROR
                        else -> null
                    }
            },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        DiagnosticCategoryRow(
            selected = category,
            onSelect = { category = it },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        DiagnosticSearchField(
            value = query,
            onValueChange = { query = it },
            onClear = { query = "" },
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        if (filteredEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        if (scopeEntries.isEmpty()) {
                            stringResource(R.string.web_session_diagnostics_empty)
                        } else {
                            stringResource(R.string.web_session_diagnostics_empty_filter)
                        },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentPadding = PaddingValues(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(
                    items = filteredEntries,
                    key = { entry -> entry.sequence },
                ) { entry ->
                    DiagnosticRow(entry)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticSelectorRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = selectedIndex == index
            Surface(
                modifier = Modifier.weight(1f).heightIn(min = 40.dp).clickable { onSelect(index) },
                shape = KiyoriUiShapes.control,
                color =
                    if (selected) {
                        WebSessionBrowserMenuTone.DIAGNOSTICS.resolveColors().container
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        color =
                            if (selected) {
                                WebSessionBrowserMenuTone.DIAGNOSTICS.resolveColors().icon
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticCategoryRow(
    selected: BrowserDiagnosticCategory?,
    onSelect: (BrowserDiagnosticCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DiagnosticCategoryChip(
            label = stringResource(R.string.web_session_diagnostics_filter_all),
            selected = selected == null,
        ) { onSelect(null) }
        BrowserDiagnosticCategory.entries.forEach { category ->
            DiagnosticCategoryChip(
                label = category.name,
                selected = selected == category,
                onClick = { onSelect(category) },
            )
        }
    }
}

@Composable
private fun DiagnosticCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.heightIn(min = 40.dp).clickable(onClick = onClick),
        shape = KiyoriUiShapes.control,
        color =
            if (selected) {
                WebSessionBrowserMenuTone.DIAGNOSTICS.resolveColors().container
            } else {
                MaterialTheme.colorScheme.surface
            },
    ) {
        Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color =
                    if (selected) {
                        WebSessionBrowserMenuTone.DIAGNOSTICS.resolveColors().icon
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun DiagnosticSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(40.dp),
        shape = KiyoriUiShapes.field,
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
                cursorBrush = androidx.compose.ui.graphics.SolidColor(WebSessionBrowserMenuTone.DIAGNOSTICS.resolveColors().icon),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                text = stringResource(R.string.web_session_diagnostics_search_hint),
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
private fun DiagnosticRow(entry: BrowserDiagnosticEntry) {
    val locale = LocalConfiguration.current.locales[0]
    val levelColor =
        when (entry.level) {
            BrowserDiagnosticLevel.INFO -> Color(0xFF2563EB)
            BrowserDiagnosticLevel.WARNING -> Color(0xFFB45309)
            BrowserDiagnosticLevel.ERROR -> Color(0xFFDC2626)
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.level.name,
                    color = levelColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(levelColor.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
                Text(
                    text = entry.category.name,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 7.dp),
                )
                Text(
                    text = entry.event,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 7.dp),
                )
                Text(
                    text = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(entry.timestamp)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
                if (entry.repeatCount > 1) {
                    Text(
                        text = "x${entry.repeatCount}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
            if (entry.message.isNotBlank()) {
                Text(
                    text = entry.message,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Row(
                modifier =
                    Modifier
                        .padding(top = 5.dp)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                entry.host.takeIf(String::isNotBlank)?.let { host ->
                    DiagnosticMetadata(
                        label = "${stringResource(R.string.web_session_diagnostics_detail_host)}: $host",
                    )
                }
                entry.profile?.let { profile ->
                    DiagnosticMetadata(
                        label = "${stringResource(R.string.web_session_diagnostics_detail_profile)}: ${profile.wireName}",
                    )
                }
                entry.sessionId?.let { sessionId ->
                    DiagnosticMetadata(
                        label = "${stringResource(R.string.web_session_diagnostics_detail_session)}: ${sessionId.take(8)}",
                    )
                }
                entry.documentToken?.let { documentToken ->
                    DiagnosticMetadata(
                        label = "${stringResource(R.string.web_session_diagnostics_detail_document)}: ${documentToken.take(8)}",
                    )
                }
            }
            entry.details.forEach { (key, value) ->
                Text(
                    text = "$key: $value",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun DiagnosticMetadata(label: String) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun browserDiagnosticExportFileName(now: Date = Date()): String =
    "kiyori-browser-diagnostics-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)}.txt"

private const val DIAGNOSTIC_SHEET_TAG = "BrowserDiagnosticSheet"
