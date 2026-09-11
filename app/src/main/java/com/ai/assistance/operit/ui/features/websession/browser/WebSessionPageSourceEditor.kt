package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo

import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BROWSER_PAGE_SOURCE_FORMAT_MAX_CHARS
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPageSourceDiff
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserPageSourceLayoutStats
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionPageSourceState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.analyzeBrowserPageSourceLayout
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserPageSourceLineStartOffset
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.buildBrowserPageSourceDiff
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.CodeEditor
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.CodeFormatter
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.EditorInteractionState
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.NativeCodeEditor
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
import com.kiyori.design.theme.KiyoriUiShapes

@Composable
internal fun WebSessionPageSourceEditor(
    state: WebSessionPageSourceState,
    onRequestBack: () -> Unit,
    onOpenAiDialogue: () -> Unit,
    onBufferChanged: (String) -> Unit,
    onCopy: () -> Unit,
    onReload: () -> Unit,
    onApply: () -> Unit,
    onKeepAndClose: () -> Unit,
    onDiscardAndClose: () -> Unit,
    onDismissExitPrompt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nativeEditor by remember { mutableStateOf<NativeCodeEditor?>(null) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var lastMatchStart by remember { mutableIntStateOf(-1) }
    var lastMatchEnd by remember { mutableIntStateOf(-1) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var reviewVisible by remember { mutableStateOf(false) }
    var applyConfirmationVisible by remember { mutableStateOf(false) }
    var reloadConfirmationVisible by remember { mutableStateOf(false) }
    var jumpToLineVisible by remember { mutableStateOf(false) }
    var jumpToLineInput by remember { mutableStateOf("") }
    var softWrap by rememberSaveable(state.sessionId, state.documentToken) {
        mutableStateOf(false)
    }
    // The warning is informational and dismissible for the current captured document; keeping
    // it local avoids writing UI-only state into the page-source or Browser Runtime owner.
    var longLineWarningVisible by
        remember(state.sessionId, state.documentToken) { mutableStateOf(true) }
    var editorInteractionState by remember { mutableStateOf(EditorInteractionState()) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val content = state.content
    val layoutStats =
        remember(content) {
            content?.let(::analyzeBrowserPageSourceLayout)
                ?: BrowserPageSourceLayoutStats(
                    lineCount = 0,
                    longestLineCharacters = 0,
                    longLineCount = 0,
                )
        }

    LaunchedEffect(state.sessionId) {
        searchVisible = false
        searchQuery = ""
        replacement = ""
        lastMatchStart = -1
        lastMatchEnd = -1
        reviewVisible = false
        applyConfirmationVisible = false
        reloadConfirmationVisible = false
        jumpToLineVisible = false
        jumpToLineInput = ""
        editorInteractionState = EditorInteractionState()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            PageSourceEditorHeader(
                title = state.pageTitle.ifBlank { stringResource(R.string.web_session_page_source) },
                changed = state.hasChanges,
                editingEnabled =
                    content != null &&
                        nativeEditor != null &&
                        !state.isLoading &&
                        !state.isApplying,
                overflowExpanded = overflowExpanded,
                canFormat = content != null && content.length <= BROWSER_PAGE_SOURCE_FORMAT_MAX_CHARS,
                reloadSupported = state.applySupported,
                onBack = onRequestBack,
                onSearch = { searchVisible = !searchVisible },
                onOverflowExpandedChange = { overflowExpanded = it },
                onCopy = onCopy,
                onJumpToLine = {
                    jumpToLineInput = editorInteractionState.cursorLine.toString()
                    jumpToLineVisible = true
                },
                onFormat = {
                    content?.let { source ->
                        val formatted = CodeFormatter.format(source, "html")
                        if (formatted != source) {
                            nativeEditor?.replaceAllText(formatted)
                        }
                    }
                },
                onReload = {
                    if (state.hasChanges) {
                        reloadConfirmationVisible = true
                    } else {
                        onReload()
                    }
                },
            )
            PageSourceEditorMetadata(
                state = state,
                layoutStats = layoutStats,
                softWrap = softWrap,
                onDismissLongLineWarning = { longLineWarningVisible = false },
                longLineWarningVisible = longLineWarningVisible,
            )
            if (content != null) {
                PageSourceEditorCommandBar(
                    softWrap = softWrap,
                    interactionState = editorInteractionState,
                    editingEnabled = !state.isApplying && nativeEditor != null,
                    canFormat =
                        !state.isApplying &&
                            content.length <= BROWSER_PAGE_SOURCE_FORMAT_MAX_CHARS,
                    onSoftWrapChanged = { softWrap = it },
                    onUndo = { nativeEditor?.undo() },
                    onRedo = { nativeEditor?.redo() },
                    onFormat = {
                        val formatted = CodeFormatter.format(content, "html")
                        if (formatted != content) {
                            nativeEditor?.replaceAllText(formatted)
                        }
                    },
                    onCopy = onCopy,
                    onJumpToLine = {
                        jumpToLineInput = editorInteractionState.cursorLine.toString()
                        jumpToLineVisible = true
                    },
                )
            }

            if (searchVisible && content != null) {
                PageSourceEditorSearchBar(
                    query = searchQuery,
                    replacement = replacement,
                    onQueryChanged = {
                        searchQuery = it
                        lastMatchStart = -1
                        lastMatchEnd = -1
                    },
                    onReplacementChanged = { replacement = it },
                    onClose = {
                        searchVisible = false
                        lastMatchStart = -1
                        lastMatchEnd = -1
                    },
                    onFindNext = {
                        val match =
                            selectNextPageSourceMatch(
                                editor = nativeEditor,
                                source = content,
                                query = searchQuery,
                                previousEnd = lastMatchEnd,
                            )
                        lastMatchStart = match?.start ?: -1
                        lastMatchEnd = match?.end ?: -1
                    },
                    onFindPrevious = {
                        val match =
                            selectPreviousPageSourceMatch(
                                editor = nativeEditor,
                                source = content,
                                query = searchQuery,
                                currentStart = lastMatchStart,
                            )
                        lastMatchStart = match?.start ?: -1
                        lastMatchEnd = match?.end ?: -1
                    },
                    onReplaceNext = {
                        val match =
                            findNextPageSourceMatch(
                                source = content,
                                query = searchQuery,
                                previousEnd = lastMatchEnd,
                            )
                        if (match != null) {
                            nativeEditor?.replaceRange(
                                start = match.start,
                                end = match.end,
                                replacement = replacement,
                            )
                            nativeEditor?.selectRange(match.start, match.start + replacement.length)
                            lastMatchStart = match.start
                            lastMatchEnd = match.start + replacement.length
                        }
                    },
                    onReplaceAll = {
                        if (searchQuery.isNotEmpty()) {
                            val replaced = content.replace(searchQuery, replacement)
                            if (replaced != content) {
                                nativeEditor?.replaceAllText(replaced)
                            }
                            lastMatchStart = -1
                            lastMatchEnd = -1
                        }
                    },
                    replacementEnabled = !state.isApplying,
                )
            }

            state.statusMessage?.takeIf(String::isNotBlank)?.let { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = KiyoriSemanticTone.GREEN.resolveColors().container,
                    contentColor = KiyoriSemanticTone.GREEN.resolveColors().icon,
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            state.error?.takeIf(String::isNotBlank)?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            when {
                state.isLoading || content == null && state.error == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = WebSessionBrowserMenuTone.PAGE_SOURCE.resolveColors().icon,
                        )
                    }
                }
                content != null -> {
                    CodeEditor(
                        code = content,
                        language = "html",
                        onCodeChange = { updated ->
                            // Manual edits invalidate the previous match range; replacement actions
                            // restore their new range immediately after the editor mutation.
                            lastMatchStart = -1
                            lastMatchEnd = -1
                            onBufferChanged(updated)
                        },
                        readOnly = state.isApplying,
                        showLineNumbers = true,
                        softWrap = softWrap,
                        enableCompletion = true,
                        showSymbolBar = imeVisible,
                        symbolBarHeight = 36.dp,
                        editorRef = { editor -> nativeEditor = editor },
                        onInteractionStateChanged = { updated ->
                            editorInteractionState = updated
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (!imeVisible && state.applySupported) {
                        PageSourceEditorFooter(
                            canReview = state.baselineContent != null,
                            canApply =
                                state.applySupported &&
                                    state.hasChanges &&
                                    !state.isApplying,
                            showApply = state.applySupported,
                            isApplying = state.isApplying,
                            onOpenAiDialogue = onOpenAiDialogue,
                            onReview = { reviewVisible = true },
                            onApply = { applyConfirmationVisible = true },
                        )
                    }
                }
            }
        }
    }

    if (reviewVisible) {
        val baseline = state.baselineContent.orEmpty()
        val revised = state.content.orEmpty()
        val diff = remember(baseline, revised) {
            buildBrowserPageSourceDiff(baseline, revised)
        }
        PageSourceDiffDialog(
            diff = diff,
            onDismiss = { reviewVisible = false },
        )
    }

    if (applyConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { applyConfirmationVisible = false },
            title = { Text(stringResource(R.string.web_session_source_apply_title)) },
            text = { Text(stringResource(R.string.web_session_source_apply_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        applyConfirmationVisible = false
                        onApply()
                    },
                ) {
                    Text(stringResource(R.string.web_session_source_apply_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { applyConfirmationVisible = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (reloadConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { reloadConfirmationVisible = false },
            title = { Text(stringResource(R.string.web_session_source_reload_title)) },
            text = { Text(stringResource(R.string.web_session_source_reload_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        reloadConfirmationVisible = false
                        onReload()
                    },
                ) {
                    Text(stringResource(R.string.web_session_source_reload_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { reloadConfirmationVisible = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (jumpToLineVisible && content != null) {
        val targetLine = jumpToLineInput.toIntOrNull()
        val targetLineValid = targetLine != null && targetLine in 1..layoutStats.lineCount
        AlertDialog(
            onDismissRequest = { jumpToLineVisible = false },
            title = { Text(stringResource(R.string.web_session_source_jump_to_line)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = jumpToLineInput,
                        onValueChange = { value ->
                            jumpToLineInput = value.filter(Char::isDigit)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.web_session_source_line_number)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    R.string.web_session_source_total_lines,
                                    layoutStats.lineCount,
                                ),
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = jumpToLineInput.isNotBlank() && !targetLineValid,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val line = requireNotNull(targetLine)
                        val offset = browserPageSourceLineStartOffset(content, line)
                        nativeEditor?.selectRange(offset, offset)
                        jumpToLineVisible = false
                    },
                    enabled = targetLineValid,
                ) {
                    Text(stringResource(R.string.web_session_source_jump))
                }
            },
            dismissButton = {
                TextButton(onClick = { jumpToLineVisible = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (state.exitPromptVisible) {
        AlertDialog(
            onDismissRequest = onDismissExitPrompt,
            title = { Text(stringResource(R.string.web_session_source_leave_title)) },
            text = { Text(stringResource(R.string.web_session_source_leave_message)) },
            confirmButton = {
                TextButton(onClick = onKeepAndClose) {
                    Text(stringResource(R.string.web_session_source_keep_edit))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onDiscardAndClose) {
                        Text(
                            text = stringResource(R.string.web_session_source_discard_edit),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = onDismissExitPrompt) {
                        Text(stringResource(R.string.web_session_source_continue_edit))
                    }
                }
            },
        )
    }
}

@Composable
private fun PageSourceEditorHeader(
    title: String,
    changed: Boolean,
    editingEnabled: Boolean,
    overflowExpanded: Boolean,
    canFormat: Boolean,
    reloadSupported: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onOverflowExpandedChange: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onJumpToLine: () -> Unit,
    onFormat: () -> Unit,
    onReload: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (changed) {
                    Box(
                        modifier =
                            Modifier
                                .background(
                                    KiyoriSemanticTone.ORANGE.resolveColors().icon,
                                    CircleShape,
                                )
                                .padding(4.dp),
                    )
                }
            }
            IconButton(onClick = onSearch, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription =
                        stringResource(R.string.web_session_userscript_editor_find),
                )
            }
            Box {
                IconButton(
                    onClick = { onOverflowExpandedChange(true) },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.more),
                    )
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { onOverflowExpandedChange(false) },
                    shape = WebSessionBrowserPopupShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    shadowElevation = WebSessionBrowserPopupElevation,
                ) {
                    WebSessionBrowserDropdownItem(
                        title = stringResource(R.string.web_session_copy_source),
                        onClick = {
                            onOverflowExpandedChange(false)
                            onCopy()
                        },
                        enabled = editingEnabled,
                    )
                    WebSessionBrowserDropdownItem(
                        title = stringResource(R.string.web_session_userscript_editor_format),
                        onClick = {
                            onOverflowExpandedChange(false)
                            onFormat()
                        },
                        enabled = editingEnabled && canFormat,
                    )
                    WebSessionBrowserDropdownItem(
                        title = stringResource(R.string.web_session_source_jump_to_line),
                        onClick = {
                            onOverflowExpandedChange(false)
                            onJumpToLine()
                        },
                        enabled = editingEnabled,
                    )
                    if (reloadSupported) {
                        WebSessionBrowserDropdownItem(
                            title = stringResource(R.string.web_session_source_reload),
                            onClick = {
                                onOverflowExpandedChange(false)
                                onReload()
                            },
                            enabled = editingEnabled,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageSourceEditorMetadata(
    state: WebSessionPageSourceState,
    layoutStats: BrowserPageSourceLayoutStats,
    softWrap: Boolean,
    longLineWarningVisible: Boolean,
    onDismissLongLineWarning: () -> Unit,
) {
    val content = state.content
    val host =
        remember(state.pageUrl) {
            runCatching { state.pageUrl.toUri().host }
                .getOrNull()
                .orEmpty()
                .ifBlank { state.pageUrl }
        }
    val stateLabel =
        when {
            state.hasChanges -> stringResource(R.string.web_session_source_status_edited)
            state.isRetainedEdit -> stringResource(R.string.web_session_source_status_retained)
            else -> stringResource(R.string.web_session_source_status_live_dom)
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Text(
                text =
                    stringResource(
                        R.string.web_session_source_metadata,
                        stateLabel,
                        host,
                        content?.length ?: 0,
                        layoutStats.lineCount,
                    ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (layoutStats.longLineCount > 0 && longLineWarningVisible) {
                val warningColors = KiyoriSemanticTone.ORANGE.resolveColors()
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = warningColors.container,
                    contentColor = warningColors.icon,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                stringResource(
                                    if (softWrap) {
                                        R.string.web_session_source_long_lines_wrapped
                                    } else {
                                        R.string.web_session_source_long_lines_horizontal
                                    },
                                    layoutStats.longLineCount,
                                    layoutStats.longestLineCharacters,
                                ),
                            modifier = Modifier.weight(1f).padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        IconButton(
                            onClick = onDismissLongLineWarning,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.close),
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageSourceEditorCommandBar(
    softWrap: Boolean,
    interactionState: EditorInteractionState,
    editingEnabled: Boolean,
    canFormat: Boolean,
    onSoftWrapChanged: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFormat: () -> Unit,
    onCopy: () -> Unit,
    onJumpToLine: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageSourceCommandChip(
                label = stringResource(R.string.web_session_source_horizontal_browse),
                selected = !softWrap,
                enabled = true,
                onClick = { onSoftWrapChanged(false) },
            )
            PageSourceCommandChip(
                label = stringResource(R.string.web_session_source_soft_wrap),
                selected = softWrap,
                enabled = true,
                onClick = { onSoftWrapChanged(true) },
            )
            PageSourceCommandChip(
                label = stringResource(R.string.web_session_userscript_editor_undo),
                icon = Icons.AutoMirrored.Filled.Undo,
                enabled = editingEnabled && interactionState.canUndo,
                onClick = onUndo,
            )
            PageSourceCommandChip(
                label = stringResource(R.string.web_session_userscript_editor_redo),
                icon = Icons.AutoMirrored.Filled.Redo,
                enabled = editingEnabled && interactionState.canRedo,
                onClick = onRedo,
            )
            PageSourceCommandChip(
                label = stringResource(R.string.web_session_userscript_editor_format),
                enabled = editingEnabled && canFormat,
                onClick = onFormat,
            )
            PageSourceCommandChip(
                label = stringResource(R.string.web_session_copy_source),
                enabled = true,
                onClick = onCopy,
            )
            PageSourceCommandChip(
                label = stringResource(R.string.web_session_source_jump_to_line),
                enabled = true,
                onClick = onJumpToLine,
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text =
                        if (interactionState.selectionCharacters > 0) {
                            stringResource(
                                R.string.web_session_source_selected_characters,
                                interactionState.selectionCharacters,
                            )
                        } else {
                            stringResource(
                                R.string.web_session_source_cursor_position,
                                interactionState.cursorLine,
                                interactionState.cursorColumn,
                            )
                        },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PageSourceCommandChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    selected: Boolean = false,
    icon: ImageVector? = null,
) {
    val selectedColors = WebSessionBrowserMenuTone.PAGE_SOURCE.resolveColors()
    Surface(
        modifier =
            Modifier
                .height(36.dp)
                .alpha(if (enabled) 1f else 0.38f)
                .clickable(enabled = enabled, onClick = onClick),
        shape = KiyoriUiShapes.control,
        color =
            if (selected) {
                selectedColors.container
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        contentColor =
            if (selected) {
                selectedColors.icon
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { imageVector ->
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PageSourceEditorFooter(
    canReview: Boolean,
    canApply: Boolean,
    showApply: Boolean,
    isApplying: Boolean,
    onOpenAiDialogue: () -> Unit,
    onReview: () -> Unit,
    onApply: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PageSourceFooterAction(
                label = stringResource(R.string.web_session_ai_dialogue),
                tone = KiyoriSemanticTone.BLUE,
                enabled = true,
                onClick = onOpenAiDialogue,
                modifier = Modifier.weight(1f),
            )
            PageSourceFooterAction(
                label = stringResource(R.string.web_session_userscript_editor_view_diff),
                tone = KiyoriSemanticTone.ORANGE,
                enabled = canReview,
                onClick = onReview,
                modifier = Modifier.weight(1f),
            )
            if (showApply) {
                PageSourceFooterAction(
                    label =
                        if (isApplying) {
                            stringResource(R.string.web_session_source_applying)
                        } else {
                            stringResource(R.string.web_session_source_apply)
                        },
                    tone = KiyoriSemanticTone.GREEN,
                    enabled = canApply,
                    onClick = onApply,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PageSourceFooterAction(
    label: String,
    tone: KiyoriSemanticTone,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = tone.resolveColors()
    Surface(
        modifier =
            modifier
                .height(44.dp)
                .alpha(if (enabled) 1f else 0.38f)
                .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = colors.container,
        contentColor = colors.icon,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PageSourceEditorSearchBar(
    query: String,
    replacement: String,
    onQueryChanged: (String) -> Unit,
    onReplacementChanged: (String) -> Unit,
    onClose: () -> Unit,
    onFindPrevious: () -> Unit,
    onFindNext: () -> Unit,
    onReplaceNext: () -> Unit,
    onReplaceAll: () -> Unit,
    replacementEnabled: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageSourceCompactField(
                value = query,
                onValueChange = onQueryChanged,
                placeholder = stringResource(R.string.web_session_userscript_editor_find),
                modifier = Modifier.weight(1f),
            )
            PageSourceSearchAction(
                label = stringResource(R.string.web_session_userscript_editor_previous),
                icon = Icons.Filled.KeyboardArrowUp,
                onClick = onFindPrevious,
                enabled = query.isNotEmpty(),
            )
            PageSourceSearchAction(
                label = stringResource(R.string.web_session_userscript_editor_next),
                icon = Icons.Filled.KeyboardArrowDown,
                onClick = onFindNext,
                enabled = query.isNotEmpty(),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.close),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageSourceCompactField(
                value = replacement,
                onValueChange = onReplacementChanged,
                placeholder = stringResource(R.string.web_session_userscript_editor_replace),
                modifier = Modifier.weight(1f),
            )
            PageSourceSearchAction(
                label = stringResource(R.string.web_session_userscript_editor_replace_next),
                onClick = onReplaceNext,
                enabled = query.isNotEmpty() && replacementEnabled,
            )
            PageSourceSearchAction(
                label = stringResource(R.string.web_session_userscript_editor_replace_all),
                onClick = onReplaceAll,
                enabled = query.isNotEmpty() && replacementEnabled,
            )
        }
    }
}

@Composable
private fun PageSourceCompactField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            singleLine = true,
            textStyle =
                TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                ),
            cursorBrush = SolidColor(WebSessionBrowserMenuTone.PAGE_SOURCE.resolveColors().icon),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun PageSourceSearchAction(
    label: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Box(
        modifier =
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .alpha(if (enabled) 1f else 0.38f)
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { imageVector ->
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PageSourceDiffDialog(
    diff: BrowserPageSourceDiff,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.web_session_source_diff_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.web_session_source_diff_summary,
                            diff.originalCharacters,
                            diff.revisedCharacters,
                            diff.originalLines,
                            diff.revisedLines,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                when {
                    diff.additions == 0 && diff.deletions == 0 ->
                        Text(stringResource(R.string.web_session_source_diff_unchanged))
                    diff.hasDetailedDiff -> {
                        Text(
                            text =
                                stringResource(
                                    R.string.web_session_source_diff_line_changes,
                                    diff.additions ?: 0,
                                    diff.deletions ?: 0,
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = diff.unifiedDiff,
                                modifier = Modifier.padding(10.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    else ->
                        Text(
                            text = stringResource(R.string.web_session_source_diff_large),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                        )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

internal data class PageSourceMatch(
    val start: Int,
    val end: Int,
)

private fun selectNextPageSourceMatch(
    editor: NativeCodeEditor?,
    source: String,
    query: String,
    previousEnd: Int,
): PageSourceMatch? {
    val match = findNextPageSourceMatch(source, query, previousEnd)
    if (match != null) {
        editor?.selectRange(match.start, match.end)
    }
    return match
}

private fun selectPreviousPageSourceMatch(
    editor: NativeCodeEditor?,
    source: String,
    query: String,
    currentStart: Int,
): PageSourceMatch? {
    val match = findPreviousPageSourceMatch(source, query, currentStart)
    if (match != null) {
        editor?.selectRange(match.start, match.end)
    }
    return match
}

internal fun findNextPageSourceMatch(
    source: String,
    query: String,
    previousEnd: Int,
): PageSourceMatch? {
    if (query.isEmpty()) {
        return null
    }
    val searchFrom = previousEnd.coerceAtLeast(0)
    val next = source.indexOf(query, startIndex = searchFrom)
    val start = if (next >= 0) next else source.indexOf(query)
    return if (start >= 0) PageSourceMatch(start, start + query.length) else null
}

internal fun findPreviousPageSourceMatch(
    source: String,
    query: String,
    currentStart: Int,
): PageSourceMatch? {
    if (query.isEmpty()) {
        return null
    }
    val searchBefore =
        if (currentStart >= 0) {
            currentStart - 1
        } else {
            source.length
        }
    val previous = source.lastIndexOf(query, startIndex = searchBefore)
    val start = if (previous >= 0) previous else source.lastIndexOf(query)
    return if (start >= 0) PageSourceMatch(start, start + query.length) else null
}
