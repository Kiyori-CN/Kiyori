package com.ai.assistance.operit.ui.features.websession.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.KiyoriUiShapes
import com.kiyori.design.theme.resolveColors
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionIncognitoAvailability
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchRecord
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_MENU_LABEL_SIZE_SP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_ACTION_SIZE_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_GAP_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_HORIZONTAL_PADDING_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_SEARCH_HEIGHT_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_TOP_VERTICAL_PADDING_DP

internal const val WEB_SESSION_SEARCH_SCREEN_INPUT_MAX_LINES = 3
internal const val WEB_SESSION_SEARCH_SCREEN_INPUT_LINE_HEIGHT_SP = 18
internal const val WEB_SESSION_SEARCH_SCREEN_CLEAR_ACTION_SIZE_DP = 32
internal const val WEB_SESSION_SEARCH_SCREEN_SUBMIT_ICON_SIZE_DP = 21
internal const val WEB_SESSION_SEARCH_SCREEN_ENGINE_ICON_SIZE_DP = 18
internal const val WEB_SESSION_SEARCH_SCREEN_ENGINE_CARD_HEIGHT_DP = 42
internal const val WEB_SESSION_SEARCH_SCREEN_ENGINE_CARD_ICON_SIZE_DP = 19
internal const val WEB_SESSION_SEARCH_SCREEN_CURRENT_ACTION_WIDTH_DP = 46
internal const val WEB_SESSION_SEARCH_SCREEN_CURRENT_ACTION_ICON_SIZE_DP = 14
internal const val WEB_SESSION_SEARCH_SCREEN_CURRENT_ACTION_LABEL_SIZE_SP = 10
internal const val WEB_SESSION_SEARCH_SCREEN_CURRENT_OUTER_VERTICAL_PADDING_DP = 4
internal const val WEB_SESSION_SEARCH_SCREEN_CURRENT_INFO_VERTICAL_PADDING_DP = 4
internal const val WEB_SESSION_SEARCH_SCREEN_CURRENT_TITLE_SIZE_SP = 12
internal const val WEB_SESSION_SEARCH_SCREEN_CURRENT_URL_SIZE_SP = 10
internal const val WEB_SESSION_SEARCH_SCREEN_HISTORY_TITLE_SIZE_SP = 18
internal const val WEB_SESSION_SEARCH_SCREEN_HISTORY_EMPTY_SIZE_SP = 14
internal const val WEB_SESSION_SEARCH_SCREEN_HISTORY_ACTION_SIZE_SP = 14
internal const val WEB_SESSION_SEARCH_SCREEN_HISTORY_HEADER_HEIGHT_DP = 34
internal const val WEB_SESSION_SEARCH_SCREEN_HISTORY_PROFILE_ACTION_SIZE_DP = 34
internal const val WEB_SESSION_SEARCH_SCREEN_HISTORY_PROFILE_GAP_DP = 4
internal const val WEB_SESSION_SEARCH_SCREEN_HISTORY_DELETE_ICON_SIZE_DP = 24
internal const val WEB_SESSION_SEARCH_SCREEN_TAG_MAX_WIDTH_DP = 250
internal const val WEB_SESSION_SEARCH_ENGINE_SWITCH_BAR_CHIP_HEIGHT_DP = 28
internal const val WEB_SESSION_SEARCH_ENGINE_SWITCH_BAR_ICON_SIZE_DP = 12
internal const val WEB_SESSION_SEARCH_ENGINE_SWITCH_BAR_CLOSE_SIZE_DP = 22

@Composable
internal fun WebSessionBrowserTopBar(
    currentUrl: String,
    pageTitle: String,
    detectedVideoCount: Int,
    showDetectedVideoBadge: Boolean,
    searchEngine: WebSessionSearchEngine,
    lastSearchQuery: String,
    isSearchEngineQuickSwitchBarVisible: Boolean,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onShowDetectedVideos: () -> Unit,
    onRefresh: () -> Unit,
    onSelectQuickSearchEngine: (WebSessionSearchEngine) -> Unit,
    onDismissQuickSearchEngineBar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().statusBarsPadding(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                    shape = KiyoriUiShapes.field,
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                        if (showDetectedVideoBadge && detectedVideoCount > 0) {
                            val detectedVideoColors = WebSessionDetectedMediaBadgeTone.resolveColors()
                            val badgeText = if (detectedVideoCount > 99) "99+" else detectedVideoCount.toString()
                            Box(
                                modifier =
                                    Modifier
                                        .size(28.dp)
                                        .background(detectedVideoColors.container, CircleShape)
                                        .clickable(role = Role.Button, onClick = onShowDetectedVideos),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = badgeText,
                                    color = detectedVideoColors.icon,
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
                    icon = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.web_session_refresh),
                    onClick = onRefresh,
                )
            }

            if (isSearchEngineQuickSwitchBarVisible && lastSearchQuery.isNotBlank()) {
                WebSessionSearchEngineQuickSwitchBar(
                    currentEngine = searchEngine,
                    onSelectEngine = onSelectQuickSearchEngine,
                    onClose = onDismissQuickSearchEngineBar,
                )
            }
        }
    }
}

@Composable
private fun WebSessionSearchEngineQuickSwitchBar(
    currentEngine: WebSessionSearchEngine,
    onSelectEngine: (WebSessionSearchEngine) -> Unit,
    onClose: () -> Unit,
) {
    val closeDescription = stringResource(R.string.close)

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WebSessionSearchEngine.entries.forEach { engine ->
                WebSessionQuickSearchEngineChip(
                    engine = engine,
                    selected = engine == currentEngine,
                    onClick = {
                        if (engine != currentEngine) {
                            onSelectEngine(engine)
                        }
                    },
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier =
                Modifier
                    .size(WEB_SESSION_SEARCH_ENGINE_SWITCH_BAR_CLOSE_SIZE_DP.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClose)
                    .semantics {
                        contentDescription = closeDescription
                        role = Role.Button
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun WebSessionQuickSearchEngineChip(
    engine: WebSessionSearchEngine,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .height(WEB_SESSION_SEARCH_ENGINE_SWITCH_BAR_CHIP_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                )
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = engine.displayName
                    role = Role.Button
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Image(
            painter = painterResource(engine.iconResId),
            contentDescription = null,
            modifier =
                Modifier
                    .size(WEB_SESSION_SEARCH_ENGINE_SWITCH_BAR_ICON_SIZE_DP.dp)
                    .clip(RoundedCornerShape(4.dp)),
        )
        Text(
            text = engine.displayName,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
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
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(actionSizeDp.dp)
            .clip(KiyoriUiShapes.control)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                role = Role.Button,
                onClick = onClick,
            )
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
    systemBackEnabled: Boolean,
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
    val density = LocalDensity.current
    val panelDismissInteractionSource = remember { MutableInteractionSource() }
    var isHistoryEditing by remember { mutableStateOf(false) }
    var showClearHistoryConfirmation by remember { mutableStateOf(false) }
    var pendingDeletionIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var inputLineCount by remember { mutableStateOf(1) }
    val inputLineHeightDp = with(density) {
        WEB_SESSION_SEARCH_SCREEN_INPUT_LINE_HEIGHT_SP.sp.toDp()
    }
    val inputMinHeight =
        WEB_SESSION_BROWSER_TOP_SEARCH_HEIGHT_DP.dp +
            inputLineHeightDp * (inputLineCount - 1)
    var searchHeaderHeight by
        remember {
            mutableStateOf(
                (
                    WEB_SESSION_BROWSER_TOP_SEARCH_HEIGHT_DP +
                        WEB_SESSION_BROWSER_TOP_VERTICAL_PADDING_DP * 2
                ).dp,
            )
        }
    val visibleSearchHistory =
        searchHistory.filterNot { record -> record.id in pendingDeletionIds }

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

    BackHandler(
        enabled = systemBackEnabled,
        onBack = ::closeSearch,
    )

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
                            .onSizeChanged { size ->
                                searchHeaderHeight = with(density) { size.height.toDp() }
                            }
                            .padding(
                                horizontal = WEB_SESSION_BROWSER_TOP_HORIZONTAL_PADDING_DP.dp,
                                vertical = WEB_SESSION_BROWSER_TOP_VERTICAL_PADDING_DP.dp,
                            ),
                    // 两侧动作沿用浏览器顶栏槽位，否则多行输入会把它们随行高一起向下推。
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement =
                        Arrangement.spacedBy(WEB_SESSION_BROWSER_TOP_GAP_DP.dp),
                ) {
                    BrowserChromeIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.web_session_back),
                        onClick = ::closeSearch,
                        modifier = Modifier.offset(y = 1.dp),
                    )
                    Surface(
                        modifier =
                            Modifier
                                .weight(1f)
                                // 顶边保持不动，新增文本行只推动底边和下方内容。
                                .animateContentSize(
                                    animationSpec = tween(durationMillis = 150),
                                    alignment = Alignment.TopCenter,
                                )
                                .heightIn(
                                    min = inputMinHeight,
                                ),
                        shape = KiyoriUiShapes.field,
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = inputMinHeight)
                                    .padding(
                                        start = 8.dp,
                                        end = 0.dp,
                                    ),
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
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester),
                                singleLine = false,
                                minLines = 1,
                                maxLines = WEB_SESSION_SEARCH_SCREEN_INPUT_MAX_LINES,
                                textStyle =
                                    MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight =
                                            WEB_SESSION_SEARCH_SCREEN_INPUT_LINE_HEIGHT_SP.sp,
                                    ),
                                onTextLayout = { layoutResult ->
                                    val measuredLineCount =
                                        layoutResult.lineCount.coerceIn(
                                            1,
                                            WEB_SESSION_SEARCH_SCREEN_INPUT_MAX_LINES,
                                        )
                                    if (inputLineCount != measuredLineCount) {
                                        inputLineCount = measuredLineCount
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (draft.isBlank()) {
                                            Text(
                                                text =
                                                    stringResource(
                                                        R.string.web_session_search_placeholder,
                                                    ),
                                                style =
                                                    MaterialTheme.typography.bodySmall.copy(
                                                        lineHeight =
                                                            WEB_SESSION_SEARCH_SCREEN_INPUT_LINE_HEIGHT_SP.sp,
                                                    ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                            if (draft.isNotBlank()) {
                                BrowserChromeIconButton(
                                    icon = Icons.Filled.Close,
                                    contentDescription =
                                        stringResource(R.string.web_session_clear_search),
                                    onClick = { onDraftChange("") },
                                    actionSizeDp = WEB_SESSION_SEARCH_SCREEN_CLEAR_ACTION_SIZE_DP,
                                    iconSizeDp = 15,
                                )
                            }
                        }
                    }
                    BrowserChromeIconButton(
                        icon = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.web_session_search_submit),
                        onClick = ::submitSearch,
                        iconSizeDp = WEB_SESSION_SEARCH_SCREEN_SUBMIT_ICON_SIZE_DP,
                        modifier = Modifier.offset(y = 1.dp),
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
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(
                                        WEB_SESSION_SEARCH_SCREEN_HISTORY_HEADER_HEIGHT_DP.dp,
                                    ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.web_session_search_history),
                                fontSize = WEB_SESSION_SEARCH_SCREEN_HISTORY_TITLE_SIZE_SP.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(
                                modifier =
                                    Modifier.width(
                                        WEB_SESSION_SEARCH_SCREEN_HISTORY_PROFILE_GAP_DP.dp,
                                    ),
                            )
                            WebSessionSearchProfileAction(
                                selectedProfile = selectedProfile,
                                incognitoAvailability = incognitoAvailability,
                                onToggle = onToggleProfile,
                                actionSizeDp =
                                    WEB_SESSION_SEARCH_SCREEN_HISTORY_PROFILE_ACTION_SIZE_DP,
                            )
                            Spacer(modifier = Modifier.weight(1f))
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
                                        color = KiyoriSemanticTone.BLUE.resolveColors().icon,
                                        onClick = ::finishHistoryEditing,
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            pendingDeletionIds = emptySet()
                                            isHistoryEditing = true
                                        },
                                        modifier = Modifier.size(40.dp),
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
                                lineHeight = 18.sp,
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

            profileFeedback?.let { message ->
                WebSessionBrowserProfileFeedback(message)
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

@Composable
internal fun BoxScope.WebSessionBrowserProfileFeedback(message: String) {
    Surface(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        shape = KiyoriUiShapes.control,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 4.dp,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
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
    actionSizeDp: Int = WEB_SESSION_BROWSER_TOP_ACTION_SIZE_DP,
    modifier: Modifier = Modifier,
) {
    val enabled = incognitoAvailability.isAvailable
    val incognitoSelected = selectedProfile == WebSessionProfile.INCOGNITO
    val contentDescription =
        when {
            !enabled -> stringResource(R.string.web_session_incognito_unavailable)
            incognitoSelected -> stringResource(R.string.web_session_incognito_mode)
            else -> stringResource(R.string.web_session_normal_mode)
        }
    BrowserChromeIconButton(
        icon =
            if (incognitoSelected) {
                Icons.Filled.VisibilityOff
            } else {
                Icons.Filled.Visibility
            },
        contentDescription = contentDescription,
        onClick = onToggle,
        enabled = enabled,
        actionSizeDp = actionSizeDp,
        modifier = modifier,
    )
}

@Composable
private fun SearchEnginePanel(
    currentEngine: WebSessionSearchEngine,
    onSelect: (WebSessionSearchEngine) -> Unit,
) {
    val blueColors = KiyoriSemanticTone.BLUE.resolveColors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = KiyoriUiShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                color = MaterialTheme.colorScheme.onSurface,
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
                            shape = KiyoriUiShapes.field,
                            color =
                                if (selected) {
                                    blueColors.container
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (selected) {
                                        blueColors.icon.copy(alpha = 0.28f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
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
                                    color = MaterialTheme.colorScheme.onSurface,
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // 网址信息与动作直接使用页面背景，避免独立灰色卡片割裂全屏搜索页。
                .padding(
                    horizontal = 8.dp,
                    vertical =
                        WEB_SESSION_SEARCH_SCREEN_CURRENT_OUTER_VERTICAL_PADDING_DP.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .clip(KiyoriUiShapes.control)
                    .clickable(role = Role.Button, onClick = onOpen)
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = url,
                fontSize = WEB_SESSION_SEARCH_SCREEN_CURRENT_URL_SIZE_SP.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

@Composable
private fun UrlActionButton(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    val colors = KiyoriSemanticTone.BLUE.resolveColors()
    Column(
        modifier =
            Modifier
                .width(WEB_SESSION_SEARCH_SCREEN_CURRENT_ACTION_WIDTH_DP.dp)
                .clip(KiyoriUiShapes.control)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier =
                Modifier
                    // 图标向文字靠近，避免纵向动作看起来上下分离。
                    .offset(y = 1.dp)
                    .size(
                        WEB_SESSION_SEARCH_SCREEN_CURRENT_ACTION_ICON_SIZE_DP.dp,
                    ),
            tint = colors.icon,
        )
        Text(
            text = title,
            fontSize = WEB_SESSION_SEARCH_SCREEN_CURRENT_ACTION_LABEL_SIZE_SP.sp,
            color = MaterialTheme.colorScheme.onSurface,
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
        lineHeight = 19.sp,
        fontWeight = FontWeight.Medium,
        modifier =
            Modifier
                .clip(KiyoriUiShapes.control)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun SearchHistoryClearConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val destructiveColors = KiyoriSemanticTone.RED.resolveColors()

    WebSessionBrowserModalDialog(
        onDismissRequest = onDismiss,
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            shape = KiyoriUiShapes.card,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
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
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clickable(role = Role.Button, onClick = onConfirm),
                    shape = KiyoriUiShapes.control,
                    color = destructiveColors.container,
                    border = BorderStroke(1.5.dp, destructiveColors.icon),
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
                            color = destructiveColors.icon,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(KiyoriUiShapes.control)
                            .clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.cancel_action),
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = destructiveColors.icon,
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
    val deleteColors = KiyoriSemanticTone.RED.resolveColors()
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
                    .clip(KiyoriUiShapes.field)
                    .clickable(
                        enabled = !isEditing,
                        role = Role.Button,
                        onClick = onOpen,
                    ),
            shape = KiyoriUiShapes.field,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
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
                        .background(deleteColors.container)
                        .clickable(role = Role.Button, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription =
                        stringResource(R.string.web_session_delete_search_history_item),
                    tint = deleteColors.icon,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
