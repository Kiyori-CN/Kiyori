package com.ai.assistance.operit.ui.features.websession.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionIncognitoAvailability
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchRecord
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_ACTION_SIZE_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_GAP_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_HORIZONTAL_PADDING_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_SEARCH_HEIGHT_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_VERTICAL_PADDING_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_SEARCH_BORDER_COLOR

@Composable
internal fun WebSessionBrowserTopBar(
    currentUrl: String,
    pageTitle: String,
    isLoading: Boolean,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onRefreshOrStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().statusBarsPadding(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = WEB_SESSION_BROWSER_TOP_HORIZONTAL_PADDING_DP.dp,
                        vertical = WEB_SESSION_BROWSER_TOP_VERTICAL_PADDING_DP.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WEB_SESSION_BROWSER_TOP_GAP_DP.dp),
        ) {
            BrowserChromeIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.web_session_back),
                onClick = onBack,
            )
            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(WEB_SESSION_BROWSER_TOP_SEARCH_HEIGHT_DP.dp)
                        .clickable(role = Role.Button, onClick = onOpenSearch),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, WEB_SESSION_BROWSER_SEARCH_BORDER_COLOR),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (currentUrl.startsWith("https://")) Icons.Filled.Language else Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = pageTitle.ifBlank { currentUrl.ifBlank { "about:blank" } },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.web_session_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            BrowserChromeIconButton(
                icon = if (isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                contentDescription = stringResource(if (isLoading) R.string.web_session_stop else R.string.web_session_refresh),
                onClick = onRefreshOrStop,
            )
        }
    }
}

@Composable
private fun BrowserChromeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(WEB_SESSION_BROWSER_TOP_ACTION_SIZE_DP.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(21.dp))
    }
}

@Composable
internal fun WebSessionBrowserSearchScreen(
    currentUrl: String,
    searchEngine: WebSessionSearchEngine,
    searchHistory: List<WebSessionSearchRecord>,
    draft: String,
    isEnginePanelVisible: Boolean,
    onDraftChange: (String) -> Unit,
    onEnginePanelVisibleChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onSelectEngine: (WebSessionSearchEngine) -> Unit,
    onOpenSearchRecord: (WebSessionSearchRecord) -> Unit,
    onDeleteSearchRecord: (Long) -> Unit,
    onClearSearchHistory: () -> Unit,
    onCopyCurrentUrl: () -> Unit,
    onOpenCurrentUrl: () -> Unit,
    onUseCurrentUrl: () -> Unit,
    selectedProfile: WebSessionProfile,
    incognitoAvailability: WebSessionIncognitoAvailability,
    onToggleProfile: () -> Unit,
    profileFeedback: String?,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun submitSearch() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onSubmit()
    }

    fun closeSearch() {
        if (isEnginePanelVisible) {
            onEnginePanelVisibleChange(false)
            return
        }
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onBack()
    }

    BackHandler(onBack = ::closeSearch)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = WEB_SESSION_BROWSER_TOP_HORIZONTAL_PADDING_DP.dp,
                            vertical = WEB_SESSION_BROWSER_TOP_VERTICAL_PADDING_DP.dp,
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WEB_SESSION_BROWSER_TOP_GAP_DP.dp),
            ) {
                BrowserChromeIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.web_session_back),
                    onClick = ::closeSearch,
                )
                Surface(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(WEB_SESSION_BROWSER_TOP_SEARCH_HEIGHT_DP.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, WEB_SESSION_BROWSER_SEARCH_BORDER_COLOR),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.clickable(role = Role.Button, onClick = { onEnginePanelVisibleChange(!isEnginePanelVisible) }),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = searchEngine.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.web_session_search_engine),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(7.dp))
                        BasicTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp), contentAlignment = Alignment.CenterStart) {
                                    if (draft.isBlank()) {
                                        Text(
                                            text = stringResource(R.string.web_session_search_placeholder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        if (draft.isNotBlank()) {
                            IconButton(onClick = { onDraftChange("") }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.web_session_clear_search),
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                        IconButton(onClick = ::submitSearch, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.web_session_search_submit),
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
                WebSessionSearchProfileAction(
                    selectedProfile = selectedProfile,
                    incognitoAvailability = incognitoAvailability,
                    onToggle = onToggleProfile,
                )
            }

            if (isEnginePanelVisible) {
                SearchEnginePanel(
                    currentEngine = searchEngine,
                    onSelect = {
                        onEnginePanelVisibleChange(false)
                        onSelectEngine(it)
                    },
                )
            }

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (currentUrl.isNotBlank() && currentUrl != "about:blank") {
                    CurrentUrlActions(
                        url = currentUrl,
                        onCopy = onCopyCurrentUrl,
                        onOpen = onOpenCurrentUrl,
                        onUse = onUseCurrentUrl,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.web_session_search_history),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (searchHistory.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.web_session_clear_search_history),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable(role = Role.Button, onClick = onClearSearchHistory).padding(8.dp),
                        )
                    }
                }
                if (searchHistory.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            text = stringResource(R.string.web_session_no_search_history),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    searchHistory.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            row.forEach { record ->
                                SearchRecordCard(
                                    record = record,
                                    onOpen = { onOpenSearchRecord(record) },
                                    onDelete = { onDeleteSearchRecord(record.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
            }

            if (profileFeedback != null) {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 32.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = profileFeedback,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WebSessionSearchProfileAction(
    selectedProfile: WebSessionProfile,
    incognitoAvailability: WebSessionIncognitoAvailability,
    onToggle: () -> Unit,
) {
    val enabled = incognitoAvailability.isAvailable
    val incognitoSelected = selectedProfile == WebSessionProfile.INCOGNITO
    val contentDescription =
        when {
            !enabled -> stringResource(R.string.web_session_incognito_unavailable)
            incognitoSelected -> stringResource(R.string.web_session_incognito_mode)
            else -> stringResource(R.string.web_session_normal_mode)
        }
    IconButton(
        onClick = onToggle,
        enabled = enabled,
        modifier = Modifier.size(WEB_SESSION_BROWSER_TOP_ACTION_SIZE_DP.dp),
    ) {
        Icon(
            imageVector =
                if (incognitoSelected) {
                    Icons.Filled.VisibilityOff
                } else {
                    Icons.Filled.Visibility
                },
            contentDescription = contentDescription,
            tint =
                if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SearchEnginePanel(
    currentEngine: WebSessionSearchEngine,
    onSelect: (WebSessionSearchEngine) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.web_session_search_engine),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            WebSessionSearchEngine.entries.chunked(3).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { engine ->
                        Surface(
                            modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = { onSelect(engine) }),
                            shape = RoundedCornerShape(13.dp),
                            color = if (engine == currentEngine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        ) {
                            Text(
                                text = engine.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (engine == currentEngine) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CurrentUrlActions(
    url: String,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onUse: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.web_session_current_page), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(text = url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchActionButton(Icons.Filled.Language, stringResource(R.string.web_session_open_current_url), onOpen)
                SearchActionButton(Icons.Filled.ContentCopy, stringResource(R.string.web_session_copy_current_url), onCopy)
                SearchActionButton(Icons.Filled.Search, stringResource(R.string.web_session_use_current_url), onUse)
            }
        }
    }
}

@Composable
private fun RowScope.SearchActionButton(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onClick).padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchRecordCard(
    record: WebSessionSearchRecord,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f)),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 13.dp, top = 11.dp, end = 5.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = record.query, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = record.targetUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
internal fun WebSessionBrowserPageSource(
    isLoading: Boolean,
    content: String?,
    error: String?,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BrowserInfoPage(title = stringResource(R.string.web_session_page_source), onBack = onDismiss, modifier = modifier) {
        when {
            isLoading -> Text(text = stringResource(R.string.web_session_source_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            !error.isNullOrBlank() -> Text(text = error, color = MaterialTheme.colorScheme.error)
            !content.isNullOrBlank() -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(text = stringResource(R.string.web_session_copy_source), color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(role = Role.Button, onClick = onCopy).padding(8.dp))
                }
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFF111827)) {
                    Text(text = content, style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun BrowserInfoPage(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BrowserChromeIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.web_session_back), onBack)
                Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            }
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}
