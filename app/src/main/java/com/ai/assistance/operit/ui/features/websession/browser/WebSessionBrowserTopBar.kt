package com.ai.assistance.operit.ui.features.websession.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionIncognitoAvailability
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchRecord
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_MENU_LABEL_SIZE_SP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_SEARCH_BORDER_COLOR
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_ACTION_SIZE_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_GAP_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_HORIZONTAL_PADDING_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_SEARCH_HEIGHT_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_VERTICAL_PADDING_DP

internal const val WEB_SESSION_SEARCH_SCREEN_HEADER_VERTICAL_PADDING_DP = 5
internal const val WEB_SESSION_SEARCH_SCREEN_INPUT_HEIGHT_DP = 38
internal const val WEB_SESSION_SEARCH_SCREEN_ACTION_SIZE_DP = 34
internal const val WEB_SESSION_SEARCH_SCREEN_ENGINE_ICON_SIZE_DP = 18
internal const val WEB_SESSION_SEARCH_SCREEN_ENGINE_CARD_HEIGHT_DP = 42
internal const val WEB_SESSION_SEARCH_SCREEN_ENGINE_CARD_ICON_SIZE_DP = 19
internal const val WEB_SESSION_SEARCH_SCREEN_CURRENT_ACTION_WIDTH_DP = 46
internal const val WEB_SESSION_SEARCH_SCREEN_CURRENT_ACTION_ICON_SIZE_DP = 19
internal const val WEB_SESSION_SEARCH_SCREEN_CURRENT_OUTER_VERTICAL_PADDING_DP = 4
internal const val WEB_SESSION_SEARCH_SCREEN_CURRENT_INFO_VERTICAL_PADDING_DP = 4
internal const val WEB_SESSION_SEARCH_SCREEN_CURRENT_TITLE_SIZE_SP = 12
internal const val WEB_SESSION_SEARCH_SCREEN_CURRENT_URL_SIZE_SP = 10
internal const val WEB_SESSION_SEARCH_SCREEN_HISTORY_TITLE_SIZE_SP = 13
internal const val WEB_SESSION_SEARCH_SCREEN_HISTORY_EMPTY_SIZE_SP = 11
internal const val WEB_SESSION_SEARCH_SCREEN_HISTORY_ACTION_SIZE_SP = 11
internal const val WEB_SESSION_SEARCH_SCREEN_HISTORY_DELETE_ICON_SIZE_DP = 23
internal const val WEB_SESSION_SEARCH_SCREEN_TAG_MAX_WIDTH_DP = 250

@Composable
internal fun WebSessionBrowserTopBar(
    currentUrl: String,
    pageTitle: String,
    isLoading: Boolean,
    detectedVideoCount: Int,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onShowDetectedVideos: () -> Unit,
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
                    if (detectedVideoCount > 0) {
                        val badgeText = if (detectedVideoCount > 99) "99+" else detectedVideoCount.toString()
                        Box(
                            modifier =
                                Modifier
                                    .size(28.dp)
                                    .background(Color(0xFFFF8A00), CircleShape)
                                    .clickable(role = Role.Button, onClick = onShowDetectedVideos),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = badgeText,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(R.string.web_session_search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
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
    actionSizeDp: Int = WEB_SESSION_BROWSER_TOP_ACTION_SIZE_DP,
    iconSizeDp: Int = 21,
) {
    Box(
        modifier = Modifier
            .size(actionSizeDp.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSizeDp.dp),
        )
    }
}

@Composable
internal fun WebSessionBrowserSearchScreen(
    currentUrl: String,
    currentTitle: String,
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
    onEditCurrentUrl: () -> Unit,
    selectedProfile: WebSessionProfile,
    incognitoAvailability: WebSessionIncognitoAvailability,
    onToggleProfile: () -> Unit,
    profileFeedback: String?,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val panelDismissInteractionSource = remember { MutableInteractionSource() }
    var isHistoryEditing by remember { mutableStateOf(false) }
    var showClearHistoryConfirmation by remember { mutableStateOf(false) }
    var pendingDeletionIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val visibleSearchHistory =
        searchHistory.filterNot { record -> record.id in pendingDeletionIds }
    val searchHeaderHeight =
        (
            WEB_SESSION_SEARCH_SCREEN_INPUT_HEIGHT_DP +
                WEB_SESSION_SEARCH_SCREEN_HEADER_VERTICAL_PADDING_DP * 2
        ).dp

    fun submitSearch() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onSubmit()
    }

    fun closeSearch() {
        if (showClearHistoryConfirmation) {
            showClearHistoryConfirmation = false
            return
        }
        if (isEnginePanelVisible) {
            onEnginePanelVisibleChange(false)
            return
        }
        if (isHistoryEditing) {
            pendingDeletionIds = emptySet()
            isHistoryEditing = false
            return
        }
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onBack()
    }

    fun finishHistoryEditing() {
        val commit =
            resolveSearchHistoryEditCommit(
                searchHistory = searchHistory,
                pendingDeletionIds = pendingDeletionIds,
            )
        if (commit.clearAll) {
            onClearSearchHistory()
        } else {
            commit.recordIds.forEach(onDeleteSearchRecord)
        }
        if (!commit.clearAll && commit.recordIds.isEmpty()) {
            pendingDeletionIds = emptySet()
        }
        isHistoryEditing = false
    }

    BackHandler(onBack = ::closeSearch)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(searchHistory) {
        val currentIds = searchHistory.mapTo(mutableSetOf()) { record -> record.id }
        pendingDeletionIds = pendingDeletionIds.intersect(currentIds)
        if (searchHistory.isEmpty()) {
            isHistoryEditing = false
            showClearHistoryConfirmation = false
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = WEB_SESSION_BROWSER_TOP_HORIZONTAL_PADDING_DP.dp,
                                vertical = WEB_SESSION_SEARCH_SCREEN_HEADER_VERTICAL_PADDING_DP.dp,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BrowserChromeIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.web_session_back),
                        onClick = ::closeSearch,
                        actionSizeDp = WEB_SESSION_SEARCH_SCREEN_ACTION_SIZE_DP,
                        iconSizeDp = 19,
                    )
                    Surface(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(WEB_SESSION_SEARCH_SCREEN_INPUT_HEIGHT_DP.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, WEB_SESSION_BROWSER_SEARCH_BORDER_COLOR),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(
                                            role = Role.Button,
                                            onClick = {
                                                onEnginePanelVisibleChange(!isEnginePanelVisible)
                                            },
                                        )
                                        .padding(horizontal = 1.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                Image(
                                    painter = painterResource(searchEngine.iconResId),
                                    contentDescription = searchEngine.displayName,
                                    modifier =
                                        Modifier
                                            .size(WEB_SESSION_SEARCH_SCREEN_ENGINE_ICON_SIZE_DP.dp)
                                            .clip(RoundedCornerShape(5.dp)),
                                )
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.web_session_search_engine),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                            BasicTextField(
                                value = draft,
                                onValueChange = onDraftChange,
                                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                                singleLine = true,
                                textStyle =
                                    MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(
                                                    min =
                                                        WEB_SESSION_SEARCH_SCREEN_INPUT_HEIGHT_DP.dp,
                                                ),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (draft.isBlank()) {
                                            Text(
                                                text =
                                                    stringResource(
                                                        R.string.web_session_search_placeholder,
                                                    ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                            if (draft.isNotBlank()) {
                                IconButton(
                                    onClick = { onDraftChange("") },
                                    modifier = Modifier.size(26.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription =
                                            stringResource(R.string.web_session_clear_search),
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                            IconButton(onClick = ::submitSearch, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription =
                                        stringResource(R.string.web_session_search_submit),
                                    modifier = Modifier.size(17.dp),
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

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (currentUrl.isNotBlank() && currentUrl != "about:blank") {
                        CurrentUrlActions(
                            title = currentTitle,
                            url = currentUrl,
                            onCopy = onCopyCurrentUrl,
                            onOpen = onOpenCurrentUrl,
                            onEdit = onEditCurrentUrl,
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.web_session_search_history),
                                fontSize = WEB_SESSION_SEARCH_SCREEN_HISTORY_TITLE_SIZE_SP.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (searchHistory.isNotEmpty()) {
                                if (isHistoryEditing) {
                                    CompactHistoryAction(
                                        text = stringResource(R.string.clear_action),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        onClick = { showClearHistoryConfirmation = true },
                                    )
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(1.dp)
                                                .height(12.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.outlineVariant,
                                                ),
                                    )
                                    CompactHistoryAction(
                                        text = stringResource(R.string.done),
                                        color = Color(0xFF4F6FEA),
                                        onClick = ::finishHistoryEditing,
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            pendingDeletionIds = emptySet()
                                            isHistoryEditing = true
                                        },
                                        modifier = Modifier.size(34.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription =
                                                stringResource(
                                                    R.string.web_session_edit_search_history,
                                                ),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier =
                                                Modifier.size(
                                                    WEB_SESSION_SEARCH_SCREEN_HISTORY_DELETE_ICON_SIZE_DP.dp,
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                        if (searchHistory.isEmpty()) {
                            Text(
                                text = stringResource(R.string.web_session_no_search_history),
                                fontSize = WEB_SESSION_SEARCH_SCREEN_HISTORY_EMPTY_SIZE_SP.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                visibleSearchHistory.forEach { record ->
                                    SearchHistoryTag(
                                        text = record.query,
                                        isEditing = isHistoryEditing,
                                        onOpen = { onOpenSearchRecord(record) },
                                        onDelete = {
                                            pendingDeletionIds =
                                                pendingDeletionIds + record.id
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isEnginePanelVisible) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(top = searchHeaderHeight)
                            .zIndex(1f)
                            .clickable(
                                interactionSource = panelDismissInteractionSource,
                                indication = null,
                                onClick = { onEnginePanelVisibleChange(false) },
                            ),
                )
            }

            AnimatedVisibility(
                visible = isEnginePanelVisible,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 10.dp,
                            top = searchHeaderHeight,
                            end = 10.dp,
                        )
                        .zIndex(2f),
                enter =
                    fadeIn(animationSpec = tween(130)) +
                        slideInVertically(
                            animationSpec = tween(150),
                            initialOffsetY = { height -> -height / 10 },
                        ),
                exit =
                    fadeOut(animationSpec = tween(100)) +
                        slideOutVertically(
                            animationSpec = tween(120),
                            targetOffsetY = { height -> -height / 12 },
                        ),
            ) {
                SearchEnginePanel(
                    currentEngine = searchEngine,
                    onSelect = {
                        onEnginePanelVisibleChange(false)
                        onSelectEngine(it)
                    },
                )
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

    if (showClearHistoryConfirmation) {
        SearchHistoryClearConfirmationDialog(
            onDismiss = { showClearHistoryConfirmation = false },
            onConfirm = {
                showClearHistoryConfirmation = false
                pendingDeletionIds = emptySet()
                isHistoryEditing = false
                onClearSearchHistory()
            },
        )
    }
}

internal data class SearchHistoryEditCommit(
    val clearAll: Boolean,
    val recordIds: Set<Long>,
)

internal fun resolveSearchHistoryEditCommit(
    searchHistory: List<WebSessionSearchRecord>,
    pendingDeletionIds: Set<Long>,
): SearchHistoryEditCommit {
    val activeIds = searchHistory.mapTo(linkedSetOf()) { record -> record.id }
    val recordIds = pendingDeletionIds.intersect(activeIds)
    return SearchHistoryEditCommit(
        clearAll = activeIds.isNotEmpty() && recordIds.size == activeIds.size,
        recordIds = recordIds,
    )
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
        modifier = Modifier.size(WEB_SESSION_SEARCH_SCREEN_ACTION_SIZE_DP.dp),
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
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SearchEnginePanel(
    currentEngine: WebSessionSearchEngine,
    onSelect: (WebSessionSearchEngine) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFEFF5FF),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = stringResource(R.string.web_session_choose_search_engine),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827),
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            WebSessionSearchEngine.entries.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    row.forEach { engine ->
                        val selected = engine == currentEngine
                        Surface(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(WEB_SESSION_SEARCH_SCREEN_ENGINE_CARD_HEIGHT_DP.dp)
                                    .clickable(
                                        role = Role.Button,
                                        onClick = { onSelect(engine) },
                                    ),
                            shape = RoundedCornerShape(14.dp),
                            color =
                                if (selected) {
                                    Color(0xFFE7EEFF)
                                } else {
                                    Color.White
                                },
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (selected) {
                                        Color(0xFFAFC2F3)
                                    } else {
                                        Color(0xFFE1E7F0)
                                    },
                                ),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Image(
                                    painter = painterResource(engine.iconResId),
                                    contentDescription = null,
                                    modifier =
                                        Modifier
                                            .size(
                                                WEB_SESSION_SEARCH_SCREEN_ENGINE_CARD_ICON_SIZE_DP.dp,
                                            )
                                            .clip(RoundedCornerShape(5.dp)),
                                )
                                Text(
                                    text = engine.displayName,
                                    fontSize = WEB_SESSION_BROWSER_MENU_LABEL_SIZE_SP.sp,
                                    fontWeight =
                                        if (selected) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Medium
                                        },
                                    color = Color(0xFF111827),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
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
    title: String,
    url: String,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF3F7FD),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 8.dp,
                        vertical =
                            WEB_SESSION_SEARCH_SCREEN_CURRENT_OUTER_VERTICAL_PADDING_DP.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Button, onClick = onOpen),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 10.dp,
                                vertical =
                                    WEB_SESSION_SEARCH_SCREEN_CURRENT_INFO_VERTICAL_PADDING_DP.dp,
                            ),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            fontSize = WEB_SESSION_SEARCH_SCREEN_CURRENT_TITLE_SIZE_SP.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF111827),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = url,
                        fontSize = WEB_SESSION_SEARCH_SCREEN_CURRENT_URL_SIZE_SP.sp,
                        lineHeight = 13.sp,
                        color = Color(0xFF6B7280),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            UrlActionButton(
                icon = Icons.Filled.ContentCopy,
                title = stringResource(R.string.web_session_copy_current_url),
                onClick = onCopy,
            )
            UrlActionButton(
                icon = Icons.Filled.Edit,
                title = stringResource(R.string.web_session_edit_current_url),
                onClick = onEdit,
            )
        }
    }
}

@Composable
private fun UrlActionButton(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(WEB_SESSION_SEARCH_SCREEN_CURRENT_ACTION_WIDTH_DP.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier =
                Modifier.size(
                    WEB_SESSION_SEARCH_SCREEN_CURRENT_ACTION_ICON_SIZE_DP.dp,
                ),
            tint = Color(0xFF4F6FEA),
        )
        Text(
            text = title,
            fontSize = WEB_SESSION_BROWSER_MENU_LABEL_SIZE_SP.sp,
            color = Color(0xFF1F2937),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompactHistoryAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = color,
        fontSize = WEB_SESSION_SEARCH_SCREEN_HISTORY_ACTION_SIZE_SP.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun SearchHistoryClearConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val destructiveColor = Color(0xFFEF4F4F)

    WebSessionBrowserModalDialog(
        onDismissRequest = onDismiss,
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            contentColor = Color(0xFF111827),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.web_session_clear_search_history_confirmation,
                        ),
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clickable(role = Role.Button, onClick = onConfirm),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.5.dp, destructiveColor),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.web_session_clear_search_history_confirm,
                                ),
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = destructiveColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.cancel_action),
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF111827),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHistoryTag(
    text: String,
    isEditing: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier =
            Modifier.padding(
                top = if (isEditing) 4.dp else 0.dp,
                end = if (isEditing) 4.dp else 0.dp,
            ),
    ) {
        Surface(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        enabled = !isEditing,
                        role = Role.Button,
                        onClick = onOpen,
                    ),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFF5F6F8),
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF171717),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .widthIn(max = WEB_SESSION_SEARCH_SCREEN_TAG_MAX_WIDTH_DP.dp)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        if (isEditing) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE2E4E8))
                        .clickable(role = Role.Button, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription =
                        stringResource(R.string.web_session_delete_search_history_item),
                    tint = Color(0xFF737780),
                    modifier = Modifier.size(12.dp),
                )
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
