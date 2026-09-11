package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo

import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptEditableMetadata
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptMetadataEditorPolicy
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.UserscriptEditorUiState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.CodeEditor
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.NativeCodeEditor
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_ACTION_SIZE_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_BOTTOM_PADDING_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_HORIZONTAL_PADDING_DP
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
import kotlinx.coroutines.delay

@Composable
internal fun WebSessionUserscriptEditorPage(
    draftId: String,
    userscriptId: Long?,
    userscriptState: WebSessionUserscriptUiState,
    onRequestBack: () -> Unit,
    onBufferChanged: (String, String) -> Unit,
    onPersistDraft: (String, (() -> Unit)?) -> Unit,
    onValidateDraft: (String) -> Unit,
    onFormatDraft: (String) -> Unit,
    onApplyDraft: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editor = userscriptState.editors[draftId]
    val script =
        userscriptId?.let { scriptId ->
            userscriptState.installedScripts.firstOrNull { item -> item.id == scriptId }
        }
    var nativeEditor by remember { mutableStateOf<NativeCodeEditor?>(null) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var lastMatchStart by remember { mutableIntStateOf(-1) }
    var metadataDialogVisible by remember { mutableStateOf(false) }
    var reviewDialogVisible by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(editor?.buffer, editor?.persistedSourceHash) {
        val current = editor ?: return@LaunchedEffect
        if (!current.hasUnpersistedChanges || current.isPersisting) {
            return@LaunchedEffect
        }
        delay(800)
        onPersistDraft(draftId, null)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            UserscriptEditorHeader(
                title =
                    script?.name
                        ?: stringResource(R.string.web_session_userscript_new),
                changed = editor?.hasUnappliedChanges == true,
                isFormatting = editor?.isFormatting == true,
                editingEnabled = editor?.let { !it.isFormatting && !it.isApplying } == true,
                onBack = onRequestBack,
                onSearch = { searchVisible = !searchVisible },
                onUndo = { nativeEditor?.undo() },
                onRedo = { nativeEditor?.redo() },
                onFormat = { onFormatDraft(draftId) },
                overflowExpanded = overflowExpanded,
                onOverflowExpandedChange = { overflowExpanded = it },
                onMetadata = { metadataDialogVisible = true },
                onReview = {
                    reviewDialogVisible = true
                    onValidateDraft(draftId)
                },
            )

            if (searchVisible && editor != null) {
                UserscriptEditorSearchBar(
                    query = searchQuery,
                    replacement = replacement,
                    onQueryChanged = {
                        searchQuery = it
                        lastMatchStart = -1
                    },
                    onReplacementChanged = { replacement = it },
                    onClose = {
                        searchVisible = false
                        lastMatchStart = -1
                    },
                    onFindNext = {
                        lastMatchStart =
                            selectNextMatch(
                                editor = nativeEditor,
                                source = editor.buffer,
                                query = searchQuery,
                                previousStart = lastMatchStart,
                            )
                    },
                    onReplaceNext = {
                        val start =
                            findNextMatch(
                                source = editor.buffer,
                                query = searchQuery,
                                previousStart = lastMatchStart,
                            )
                        if (start >= 0) {
                            val updated =
                                editor.buffer.replaceRange(
                                    start,
                                    start + searchQuery.length,
                                    replacement,
                                )
                            onBufferChanged(draftId, updated)
                            nativeEditor?.selectRange(start, start + replacement.length)
                            lastMatchStart = start
                        }
                    },
                    onReplaceAll = {
                        if (searchQuery.isNotEmpty()) {
                            onBufferChanged(
                                draftId,
                                editor.buffer.replace(searchQuery, replacement),
                            )
                            lastMatchStart = -1
                        }
                    },
                    replacementEnabled = !editor.isFormatting && !editor.isApplying,
                )
            }

            when {
                editor == null || editor.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = WebSessionBrowserMenuTone.PLUGINS.resolveColors().icon,
                        )
                    }
                }
                else -> {
                    editor.error?.let { error ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ) {
                            Text(
                                text = error,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    CodeEditor(
                        code = editor.buffer,
                        language = "javascript",
                        onCodeChange = { source ->
                            onBufferChanged(draftId, source)
                        },
                        readOnly = editor.isFormatting || editor.isApplying,
                        showSymbolBar = imeVisible,
                        symbolBarHeight = 36.dp,
                        editorRef = { value -> nativeEditor = value },
                        modifier = Modifier.weight(1f),
                    )
                    if (!imeVisible) {
                        UserscriptEditorFooter(
                            editor = editor,
                            onValidate = { onValidateDraft(draftId) },
                            onReview = {
                                reviewDialogVisible = true
                                onValidateDraft(draftId)
                            },
                            onApply = { onApplyDraft(draftId) },
                        )
                    }
                }
            }
        }
    }

    if (
        metadataDialogVisible &&
            editor != null &&
            !editor.isFormatting &&
            !editor.isApplying
    ) {
        UserscriptMetadataDialog(
            source = editor.buffer,
            onDismiss = { metadataDialogVisible = false },
            onApply = { metadata ->
                runCatching {
                    UserscriptMetadataEditorPolicy.apply(editor.buffer, metadata)
                }.fold(
                    onSuccess = { updated ->
                        onBufferChanged(draftId, updated)
                        metadataDialogVisible = false
                        null
                    },
                    onFailure = { error ->
                        error.message ?: "Unable to update userscript metadata"
                    },
                )
            },
        )
    }

    if (reviewDialogVisible && editor != null) {
        UserscriptEditorReviewDialog(
            editor = editor,
            onDismiss = { reviewDialogVisible = false },
        )
    }
}

@Composable
private fun UserscriptEditorHeader(
    title: String,
    changed: Boolean,
    isFormatting: Boolean,
    editingEnabled: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFormat: () -> Unit,
    overflowExpanded: Boolean,
    onOverflowExpandedChange: (Boolean) -> Unit,
    onMetadata: () -> Unit,
    onReview: () -> Unit,
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
                                .padding(top = 2.dp)
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
                    contentDescription = stringResource(R.string.web_session_userscript_editor_find),
                )
            }
            IconButton(
                onClick = onUndo,
                enabled = editingEnabled,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.web_session_userscript_editor_undo),
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = editingEnabled,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = stringResource(R.string.web_session_userscript_editor_redo),
                )
            }
            Box {
                IconButton(
                    onClick = { onOverflowExpandedChange(true) },
                    enabled = editingEnabled,
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
                        title =
                            if (isFormatting) {
                                stringResource(R.string.web_session_userscript_editor_formatting)
                            } else {
                                stringResource(R.string.web_session_userscript_editor_format)
                            },
                        onClick = {
                            onOverflowExpandedChange(false)
                            onFormat()
                        },
                    )
                    WebSessionBrowserDropdownItem(
                        title = stringResource(R.string.web_session_userscript_editor_metadata),
                        onClick = {
                            onOverflowExpandedChange(false)
                            onMetadata()
                        },
                    )
                    WebSessionBrowserDropdownItem(
                        title = stringResource(R.string.web_session_userscript_editor_view_diff),
                        onClick = {
                            onOverflowExpandedChange(false)
                            onReview()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserscriptEditorSearchBar(
    query: String,
    replacement: String,
    onQueryChanged: (String) -> Unit,
    onReplacementChanged: (String) -> Unit,
    onClose: () -> Unit,
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
            UserscriptEditorCompactField(
                value = query,
                onValueChange = onQueryChanged,
                placeholder = stringResource(R.string.web_session_userscript_editor_find),
                modifier = Modifier.weight(1f),
            )
            UserscriptEditorSearchAction(
                label = stringResource(R.string.web_session_userscript_editor_next),
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
            UserscriptEditorCompactField(
                value = replacement,
                onValueChange = onReplacementChanged,
                placeholder = stringResource(R.string.web_session_userscript_editor_replace),
                modifier = Modifier.weight(1f),
            )
            UserscriptEditorSearchAction(
                label = stringResource(R.string.web_session_userscript_editor_replace_next),
                onClick = onReplaceNext,
                enabled = query.isNotEmpty() && replacementEnabled,
            )
            UserscriptEditorSearchAction(
                label = stringResource(R.string.web_session_userscript_editor_replace_all),
                onClick = onReplaceAll,
                enabled = query.isNotEmpty() && replacementEnabled,
            )
        }
    }
}

@Composable
private fun UserscriptEditorCompactField(
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
            cursorBrush = SolidColor(WebSessionBrowserMenuTone.PLUGINS.resolveColors().icon),
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
private fun UserscriptEditorSearchAction(
    label: String,
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
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = WebSessionBrowserMenuTone.PLUGINS.resolveColors().icon,
            maxLines = 1,
        )
    }
}

@Composable
private fun UserscriptEditorFooter(
    editor: UserscriptEditorUiState,
    onValidate: () -> Unit,
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
                    .padding(
                        start = WEB_SESSION_BROWSER_BOTTOM_HORIZONTAL_PADDING_DP.dp,
                        end = WEB_SESSION_BROWSER_BOTTOM_HORIZONTAL_PADDING_DP.dp,
                        bottom = WEB_SESSION_BROWSER_BOTTOM_BOTTOM_PADDING_DP.dp,
                    ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            UserscriptEditorFooterAction(
                label =
                    if (editor.isValidating) {
                        stringResource(R.string.web_session_userscript_editor_validating)
                    } else {
                        stringResource(R.string.web_session_userscript_editor_validate)
                    },
                onClick = onValidate,
                enabled = !editor.isValidating && !editor.isApplying,
                modifier = Modifier.weight(1f),
            )
            UserscriptEditorFooterAction(
                label = stringResource(R.string.web_session_userscript_editor_view_diff),
                onClick = onReview,
                enabled = !editor.isValidating && !editor.isApplying,
                modifier = Modifier.weight(1f),
            )
            UserscriptEditorFooterAction(
                label =
                    if (editor.isApplying) {
                        stringResource(R.string.web_session_userscript_updating)
                    } else {
                        stringResource(R.string.web_session_userscript_editor_apply)
                    },
                onClick = onApply,
                enabled =
                    !editor.isApplying &&
                        editor.review?.canApply == true &&
                        editor.hasUnappliedChanges,
                modifier = Modifier.weight(1f),
                primary = true,
            )
        }
    }
}

@Composable
private fun UserscriptEditorFooterAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val tone = WebSessionBrowserMenuTone.PLUGINS.resolveColors()
    Box(
        modifier =
            modifier
                .height(WEB_SESSION_BROWSER_BOTTOM_ACTION_SIZE_DP.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (primary) {
                        tone.container
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                )
                .clickable(enabled = enabled, onClick = onClick)
                .alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color =
                if (primary) {
                    tone.icon
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            maxLines = 1,
        )
    }
}

@Composable
private fun UserscriptMetadataDialog(
    source: String,
    onDismiss: () -> Unit,
    onApply: (UserscriptEditableMetadata) -> String?,
) {
    val initial = remember(source) { runCatching { UserscriptMetadataEditorPolicy.read(source) } }
    val initialValue = initial.getOrNull()
    var name by remember(initialValue) { mutableStateOf(initialValue?.name.orEmpty()) }
    var namespace by remember(initialValue) { mutableStateOf(initialValue?.namespace.orEmpty()) }
    var version by remember(initialValue) { mutableStateOf(initialValue?.version.orEmpty()) }
    var description by remember(initialValue) { mutableStateOf(initialValue?.description.orEmpty()) }
    var matches by remember(initialValue) { mutableStateOf(initialValue?.matches.orEmpty()) }
    var includes by remember(initialValue) { mutableStateOf(initialValue?.includes.orEmpty()) }
    var excludes by remember(initialValue) { mutableStateOf(initialValue?.excludes.orEmpty()) }
    var excludeMatches by remember(initialValue) {
        mutableStateOf(initialValue?.excludeMatches.orEmpty())
    }
    var grants by remember(initialValue) { mutableStateOf(initialValue?.grants.orEmpty()) }
    var connects by remember(initialValue) { mutableStateOf(initialValue?.connects.orEmpty()) }
    var runAt by remember(initialValue) { mutableStateOf(initialValue?.runAt.orEmpty()) }
    var injectInto by remember(initialValue) { mutableStateOf(initialValue?.injectInto.orEmpty()) }
    var noFrames by remember(initialValue) { mutableStateOf(initialValue?.noFrames == true) }
    var applyError by remember(source) { mutableStateOf<String?>(null) }

    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Filled.Description,
            tone = WebSessionBrowserMenuTone.PLUGINS,
            title = stringResource(R.string.web_session_userscript_editor_metadata),
            modifier = Modifier.widthIn(min = 300.dp, max = 520.dp).heightIn(max = 720.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                initial.exceptionOrNull()?.message?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                applyError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(name, { name = it }, label = { Text("@name") })
                OutlinedTextField(namespace, { namespace = it }, label = { Text("@namespace") })
                OutlinedTextField(version, { version = it }, label = { Text("@version") })
                OutlinedTextField(description, { description = it }, label = { Text("@description") })
                OutlinedTextField(
                    matches,
                    { matches = it },
                    label = { Text("@match") },
                    minLines = 3,
                )
                OutlinedTextField(
                    includes,
                    { includes = it },
                    label = { Text("@include") },
                    minLines = 2,
                )
                OutlinedTextField(
                    excludes,
                    { excludes = it },
                    label = { Text("@exclude") },
                    minLines = 2,
                )
                OutlinedTextField(
                    excludeMatches,
                    { excludeMatches = it },
                    label = { Text("@exclude-match") },
                    minLines = 2,
                )
                OutlinedTextField(
                    runAt,
                    { runAt = it },
                    label = { Text("@run-at") },
                    singleLine = true,
                )
                OutlinedTextField(
                    injectInto,
                    { injectInto = it },
                    label = { Text("@inject-into") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("@noframes")
                    Switch(
                        checked = noFrames,
                        onCheckedChange = { noFrames = it },
                    )
                }
                OutlinedTextField(
                    grants,
                    { grants = it },
                    label = { Text("@grant") },
                    minLines = 3,
                )
                OutlinedTextField(
                    connects,
                    { connects = it },
                    label = { Text("@connect") },
                    minLines = 3,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            applyError =
                                onApply(
                                    UserscriptEditableMetadata(
                                        name = name,
                                        namespace = namespace,
                                        version = version,
                                        description = description,
                                        matches = matches,
                                        includes = includes,
                                        excludes = excludes,
                                        excludeMatches = excludeMatches,
                                        grants = grants,
                                        connects = connects,
                                        runAt = runAt,
                                        injectInto = injectInto,
                                        noFrames = noFrames,
                                    ),
                                )
                        },
                        enabled = initialValue != null && name.isNotBlank() && version.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.web_session_userscript_editor_apply_to_source))
                    }
                }
            }
        }
    }
}

@Composable
private fun UserscriptEditorReviewDialog(
    editor: UserscriptEditorUiState,
    onDismiss: () -> Unit,
) {
    val review = editor.review
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Filled.Description,
            tone = WebSessionBrowserMenuTone.PLUGINS,
            title = stringResource(R.string.web_session_userscript_editor_review_title),
            modifier = Modifier.widthIn(min = 300.dp, max = 560.dp).heightIn(max = 720.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 600.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            when {
                editor.isValidating -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            color = WebSessionBrowserMenuTone.PLUGINS.resolveColors().icon,
                        )
                    }
                }
                review == null -> {
                    Text(
                        text =
                            editor.error
                                ?: stringResource(R.string.web_session_userscript_editor_review_waiting),
                    )
                }
                else -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ReviewStatusLine(
                            label = stringResource(R.string.web_session_userscript_editor_metadata),
                            passed = review.preview != null,
                            detail = review.preview?.metadata?.name ?: review.syntaxError,
                        )
                        ReviewStatusLine(
                            label = stringResource(R.string.web_session_userscript_editor_syntax),
                            passed = review.syntaxError == null,
                            detail = review.syntaxError,
                        )
                        ReviewStatusLine(
                            label = stringResource(R.string.web_session_userscript_detail_permissions),
                            passed =
                                review.preview?.blockedReasons.isNullOrEmpty() &&
                                    review.preview?.unknownGrants.isNullOrEmpty(),
                            detail =
                                (
                                    review.preview?.unknownGrants.orEmpty() +
                                        review.preview?.blockedReasons.orEmpty()
                                    ).joinToString().ifBlank { null },
                        )
                        Text(
                            text =
                                pluralStringResource(
                                    R.plurals.web_session_userscript_editor_diff_summary,
                                    review.sourceDiff.additions,
                                    review.sourceDiff.additions,
                                    review.sourceDiff.deletions,
                                ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        review.updateDiff?.let { diff ->
                            val grantsLabel =
                                stringResource(R.string.web_session_userscript_grants)
                            val connectsLabel =
                                stringResource(R.string.web_session_userscript_connects)
                            val matchesLabel =
                                stringResource(R.string.web_session_userscript_matches)
                            val excludesLabel =
                                stringResource(R.string.web_session_userscript_excludes)
                            val permissionChanges =
                                buildList {
                                    addAll(diff.addedGrants.map { "$grantsLabel + $it" })
                                    addAll(diff.removedGrants.map { "$grantsLabel − $it" })
                                    addAll(diff.addedConnects.map { "$connectsLabel + $it" })
                                    addAll(diff.removedConnects.map { "$connectsLabel − $it" })
                                    addAll(diff.addedPageRules.map { "$matchesLabel + $it" })
                                    addAll(diff.removedPageRules.map { "$matchesLabel − $it" })
                                    addAll(diff.removedExclusions.map { "$excludesLabel − $it" })
                                    if (diff.sourceIdentityChanged) {
                                        add(
                                            stringResource(
                                                R.string.web_session_userscript_update_source_changed,
                                            ),
                                        )
                                    }
                                }
                            if (permissionChanges.isNotEmpty()) {
                                Text(
                                    text = permissionChanges.joinToString("\n"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                        if (review.sourceDiff.unifiedDiff.isNotBlank()) {
                            Text(
                                text = review.sourceDiff.unifiedDiff,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                        .padding(10.dp),
                            )
                        }
                    }
                }
            }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewStatusLine(
    label: String,
    passed: Boolean,
    detail: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text =
                "$label · " +
                    if (passed) {
                        stringResource(R.string.web_session_userscript_editor_check_passed)
                    } else {
                        stringResource(R.string.web_session_userscript_editor_check_failed)
                    },
            style = MaterialTheme.typography.labelLarge,
            color =
                if (passed) {
                    KiyoriSemanticTone.GREEN.resolveColors().icon
                } else {
                    MaterialTheme.colorScheme.error
                },
        )
        detail?.takeIf(String::isNotBlank)?.let { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun selectNextMatch(
    editor: NativeCodeEditor?,
    source: String,
    query: String,
    previousStart: Int,
): Int {
    val start = findNextMatch(source, query, previousStart)
    if (start >= 0) {
        editor?.selectRange(start, start + query.length)
    }
    return start
}

private fun findNextMatch(
    source: String,
    query: String,
    previousStart: Int,
): Int {
    if (query.isEmpty()) {
        return -1
    }
    val searchFrom = (previousStart + query.length).coerceAtLeast(0)
    val next = source.indexOf(query, startIndex = searchFrom)
    return if (next >= 0) next else source.indexOf(query)
}
